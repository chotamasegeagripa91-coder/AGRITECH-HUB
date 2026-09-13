package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.repository.AgritechRepository
import com.example.ui.utils.ExcelMaterialParser
import com.example.ui.utils.ExcelParseResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExcelMaterialImportTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AgritechRepository
    private lateinit var preferences: AppPreferences

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = AppPreferences(context)
        repository = AgritechRepository(
            materialDao = database.materialDao(),
            customerDao = database.customerDao(),
            quoteDao = database.quoteDao(),
            preferences = preferences
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createMockXlsx(
        headers: List<String>,
        rows: List<List<String>>
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val zip = ZipOutputStream(baos)

        val allStrings = mutableListOf<String>()
        allStrings.addAll(headers)
        for (row in rows) {
            for (i in 0 until minOf(3, row.size)) {
                allStrings.add(row[i])
            }
        }
        val stringToIndex = allStrings.distinct().mapIndexed { idx, s -> s to idx }.toMap()

        val sstXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${stringToIndex.size}" uniqueCount="${stringToIndex.size}">""")
            for (entry in stringToIndex.entries.sortedBy { it.value }) {
                append("<si><t>${entry.key}</t></si>")
            }
            append("</sst>")
        }
        zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
        zip.write(sstXml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            append("""<row r="1">""")
            for ((colIdx, h) in headers.withIndex()) {
                val colLetter = ('A'.code + colIdx).toChar()
                val sstIdx = stringToIndex[h] ?: 0
                append("""<c r="${colLetter}1" t="s"><v>$sstIdx</v></c>""")
            }
            append("</row>")

            for ((rowIdx, r) in rows.withIndex()) {
                val rowNum = rowIdx + 2
                append("""<row r="$rowNum">""")
                for ((colIdx, cellVal) in r.withIndex()) {
                    val colLetter = ('A'.code + colIdx).toChar()
                    if (colIdx == 3) {
                        append("""<c r="$colLetter$rowNum"><v>$cellVal</v></c>""")
                    } else {
                        val sstIdx = stringToIndex[cellVal] ?: 0
                        append("""<c r="$colLetter$rowNum" t="s"><v>$sstIdx</v></c>""")
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zip.write(sheetXml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.close()
        return baos.toByteArray()
    }

    @Test
    fun testParseAndImportStandardXlsx() = runBlocking {
        val headers = listOf("Material Name", "Category", "Unit", "Price")
        val dataRows = listOf(
            listOf("PVC Cable 1.5mm", "Electrical", "Meter", "1500"),
            listOf("PVC Cable 2.5mm", "Electrical", "Meter", "2500"),
            listOf("13A Socket", "Electrical", "Piece", "5000"),
            listOf("PVC Pipe 1/2 inch", "Plumbing", "Piece", "3500"),
            listOf("PVC Elbow 1/2 inch", "Plumbing", "Piece", "1000")
        )

        val xlsxBytes = createMockXlsx(headers, dataRows)

        // 1. Test ExcelMaterialParser
        val parseResult = ExcelMaterialParser.parse(ByteArrayInputStream(xlsxBytes))
        assertTrue("Parsing should succeed", parseResult is ExcelParseResult.Success)
        val success = parseResult as ExcelParseResult.Success
        assertEquals(5, success.materials.size)
        assertEquals(0, success.failedRows)

        assertEquals("PVC Cable 1.5mm", success.materials[0].name)
        assertEquals("Electrical", success.materials[0].category)
        assertEquals("Meter", success.materials[0].unit)
        assertEquals(1500.0, success.materials[0].price, 0.001)

        // 2. Test initial repository import
        val report = repository.importMaterialsFromExcel(ByteArrayInputStream(xlsxBytes), "test_user_123")
        assertTrue("Import report should succeed", report.success)
        assertEquals(5, report.addedCount)
        assertEquals(0, report.updatedCount)
        assertEquals(0, report.failedCount)

        val stored = database.materialDao().getAllMaterialsList()
        assertEquals(5, stored.size)

        val socket = stored.find { it.name == "13A Socket" }
        assertNotNull("13A Socket must exist in database", socket)
        assertEquals(5000.0, socket!!.price, 0.001)
        assertEquals("Electrical", socket.category)
        assertEquals("Piece", socket.unit)
        assertEquals("test_user_123", socket.userId)
        assertFalse(socket.isDemo)
    }

    @Test
    fun testReimportUpdatesPricesWithoutDuplicates() = runBlocking {
        val headers = listOf("Material Name", "Category", "Unit", "Price")
        val initialRows = listOf(
            listOf("PVC Cable 1.5mm", "Electrical", "Meter", "1500"),
            listOf("13A Socket", "Electrical", "Piece", "5000"),
            listOf("PVC Pipe 1/2 inch", "Plumbing", "Piece", "3500")
        )
        val initialXlsx = createMockXlsx(headers, initialRows)
        val report1 = repository.importMaterialsFromExcel(ByteArrayInputStream(initialXlsx), "user1")
        assertEquals(3, report1.addedCount)
        assertEquals(0, report1.updatedCount)
        assertEquals(3, database.materialDao().getAllMaterialsList().size)

        // Now import again with updated prices: 13A Socket is 5800, PVC Pipe is 4000, and 1 new material
        val updatedRows = listOf(
            listOf("PVC Cable 1.5mm", "Electrical", "Meter", "1500"),
            listOf("13A Socket", "Electrical", "Piece", "5800"),
            listOf("PVC Pipe 1/2 inch", "Plumbing", "Piece", "4000"),
            listOf("Cement Simba 32.5R", "Construction", "Bag", "18500")
        )
        val updatedXlsx = createMockXlsx(headers, updatedRows)
        val report2 = repository.importMaterialsFromExcel(ByteArrayInputStream(updatedXlsx), "user1")

        assertEquals(1, report2.addedCount)
        assertEquals(3, report2.updatedCount)
        assertEquals(0, report2.failedCount)

        val stored = database.materialDao().getAllMaterialsList()
        assertEquals("Total items must be 4, no duplicates created", 4, stored.size)

        val updatedSocket = stored.find { it.name == "13A Socket" }
        assertEquals(5800.0, updatedSocket!!.price, 0.001)

        val updatedPipe = stored.find { it.name == "PVC Pipe 1/2 inch" }
        assertEquals(4000.0, updatedPipe!!.price, 0.001)

        val cement = stored.find { it.name == "Cement Simba 32.5R" }
        assertNotNull(cement)
        assertEquals("Construction", cement!!.category)
        assertEquals(18500.0, cement.price, 0.001)
    }

    @Test
    fun testMissingRequiredColumnsFailsGracefully() {
        // Missing "Price" column
        val headers = listOf("Material Name", "Category", "Unit")
        val rows = listOf(
            listOf("PVC Cable 1.5mm", "Electrical", "Meter")
        )
        val badXlsx = createMockXlsx(headers, rows)
        val result = ExcelMaterialParser.parse(ByteArrayInputStream(badXlsx))

        assertTrue("Parse should fail due to missing Price column", result is ExcelParseResult.Error)
        val err = (result as ExcelParseResult.Error).message
        assertTrue("Error message should mention missing Price column", err.contains("Price"))
    }

    @Test
    fun testCsvFallbackImport() = runBlocking {
        val csv = """
            Material Name,Category,Unit,Price
            PVC Cable 1.5mm,Electrical,Meter,1500
            PVC Elbow 1/2 inch,Plumbing,Piece,1000
        """.trimIndent()

        val report = repository.importMaterialsFromExcel(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)), "user_csv")
        assertTrue(report.success)
        assertEquals(2, report.addedCount)
        assertEquals(0, report.updatedCount)

        val items = database.materialDao().getAllMaterialsList()
        assertEquals(2, items.size)
    }
}
