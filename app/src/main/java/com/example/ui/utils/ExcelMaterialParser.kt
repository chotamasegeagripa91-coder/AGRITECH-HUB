package com.example.ui.utils

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.TreeMap
import java.util.zip.ZipInputStream

sealed class ExcelParseResult {
    data class Success(
        val materials: List<ParsedExcelMaterial>,
        val failedRows: Int
    ) : ExcelParseResult()

    data class Error(val message: String) : ExcelParseResult()
}

data class ParsedExcelMaterial(
    val name: String,
    val category: String,
    val unit: String,
    val price: Double
)

data class ExcelImportReport(
    val success: Boolean,
    val addedCount: Int = 0,
    val updatedCount: Int = 0,
    val failedCount: Int = 0,
    val totalProcessed: Int = 0,
    val errorMessage: String? = null
)

object ExcelMaterialParser {

    /**
     * Parses an input stream from an Excel (.xlsx) file or fallback CSV.
     * Validates that the 4 required columns (Material Name, Category, Unit, Price) exist.
     */
    fun parse(inputStream: InputStream): ExcelParseResult {
        return try {
            val buffered = if (inputStream.markSupported()) inputStream else BufferedInputStream(inputStream)
            buffered.mark(8)
            val magic = ByteArray(4)
            val readCount = buffered.read(magic)
            buffered.reset()

            val isZip = (readCount >= 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte())

            if (isZip) {
                parseXlsx(buffered)
            } else {
                parseCsv(buffered)
            }
        } catch (e: Exception) {
            ExcelParseResult.Error(
                "Failed to parse file: ${e.localizedMessage ?: "Unknown error"}. Please ensure it is a valid .xlsx file."
            )
        }
    }

    private fun parseXlsx(inputStream: InputStream): ExcelParseResult {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val normalizedName = entry.name.replace('\\', '/')
                    entries[normalizedName] = zipStream.readBytes()
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        if (entries.isEmpty()) {
            return ExcelParseResult.Error("The uploaded Excel file is empty or corrupted.")
        }

        // 1. Parse Shared Strings (if any)
        val sharedStringsKey = entries.keys.firstOrNull {
            it.equals("xl/sharedStrings.xml", ignoreCase = true)
        }
        val sharedStrings = ArrayList<String>()
        if (sharedStringsKey != null) {
            val sstBytes = entries[sharedStringsKey] ?: ByteArray(0)
            parseSharedStrings(sstBytes, sharedStrings)
        }

        // 2. Locate the first worksheet
        val sheetKey = entries.keys.firstOrNull {
            it.equals("xl/worksheets/sheet1.xml", ignoreCase = true)
        } ?: entries.keys.filter {
            it.startsWith("xl/worksheets/", ignoreCase = true) && it.endsWith(".xml", ignoreCase = true)
        }.minOrNull()

        if (sheetKey == null) {
            return ExcelParseResult.Error("No worksheet found in the Excel file.")
        }

        val sheetBytes = entries[sheetKey] ?: ByteArray(0)
        val sheetData = parseWorksheet(sheetBytes, sharedStrings)

        return processSheetGrid(sheetData)
    }

    private fun parseSharedStrings(xmlBytes: ByteArray, outList: ArrayList<String>) {
        if (xmlBytes.isEmpty()) return
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var event = parser.eventType
        var inSi = false
        var inT = false
        val currentSiText = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name.equals("si", ignoreCase = true)) {
                        inSi = true
                        currentSiText.setLength(0)
                    } else if (inSi && name.equals("t", ignoreCase = true)) {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inSi && inT) {
                        currentSiText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (inSi && name.equals("t", ignoreCase = true)) {
                        inT = false
                    } else if (name.equals("si", ignoreCase = true)) {
                        outList.add(currentSiText.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun parseWorksheet(
        xmlBytes: ByteArray,
        sharedStrings: List<String>
    ): TreeMap<Int, MutableMap<Int, String>> {
        val sheetData = TreeMap<Int, MutableMap<Int, String>>()
        if (xmlBytes.isEmpty()) return sheetData

        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var event = parser.eventType
        var currentRowNum = 0
        var currentCellCol = -1
        var currentCellType = ""
        var currentCellText = StringBuilder()
        var inV = false
        var inT = false
        var lastColInRow = -1

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    when {
                        tag.equals("row", ignoreCase = true) -> {
                            val rAttr = parser.getAttributeValue(null, "r")
                            currentRowNum = rAttr?.toIntOrNull() ?: (currentRowNum + 1)
                            if (!sheetData.containsKey(currentRowNum)) {
                                sheetData[currentRowNum] = mutableMapOf()
                            }
                            lastColInRow = -1
                        }
                        tag.equals("c", ignoreCase = true) -> {
                            val rAttr = parser.getAttributeValue(null, "r") ?: ""
                            val colFromRef = extractColIndex(rAttr)
                            currentCellCol = if (colFromRef >= 0) colFromRef else (lastColInRow + 1)
                            lastColInRow = currentCellCol
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentCellText.setLength(0)
                        }
                        tag.equals("v", ignoreCase = true) -> inV = true
                        tag.equals("t", ignoreCase = true) -> inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV || inT) {
                        currentCellText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    when {
                        tag.equals("v", ignoreCase = true) -> inV = false
                        tag.equals("t", ignoreCase = true) -> inT = false
                        tag.equals("c", ignoreCase = true) -> {
                            if (currentRowNum > 0 && currentCellCol >= 0) {
                                val raw = currentCellText.toString().trim()
                                val finalVal = when (currentCellType) {
                                    "s" -> {
                                        val idx = raw.toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) {
                                            sharedStrings[idx]
                                        } else {
                                            raw
                                        }
                                    }
                                    else -> raw
                                }
                                sheetData[currentRowNum]?.put(currentCellCol, finalVal)
                            }
                            currentCellCol = -1
                            currentCellType = ""
                            currentCellText.setLength(0)
                        }
                    }
                }
            }
            event = parser.next()
        }

        return sheetData
    }

    private fun extractColIndex(cellRef: String): Int {
        if (cellRef.isBlank()) return -1
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return -1
        var col = 0
        for (ch in letters) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            }
        }
        return col - 1
    }

    private fun parseCsv(inputStream: InputStream): ExcelParseResult {
        val lines = inputStream.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ExcelParseResult.Error("The file is empty.")
        }

        // Determine delimiter (, or ;)
        val firstLine = lines.first()
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','

        val sheetData = TreeMap<Int, MutableMap<Int, String>>()
        for ((idx, line) in lines.withIndex()) {
            val rowNum = idx + 1
            val cols = parseCsvLine(line, delimiter)
            val rowMap = mutableMapOf<Int, String>()
            for ((colIdx, cell) in cols.withIndex()) {
                rowMap[colIdx] = cell
            }
            sheetData[rowNum] = rowMap
        }

        return processSheetGrid(sheetData)
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                result.add(current.toString().trim())
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun processSheetGrid(sheetData: TreeMap<Int, MutableMap<Int, String>>): ExcelParseResult {
        if (sheetData.isEmpty()) {
            return ExcelParseResult.Error("No data found in the spreadsheet.")
        }

        // Find the header row (first row with at least 3 non-empty values)
        val headerRowEntry = sheetData.entries.firstOrNull { (_, cells) ->
            cells.values.count { it.isNotBlank() } >= 3
        } ?: return ExcelParseResult.Error("No valid header row found. Expected columns: Material Name, Category, Unit, Price.")

        val headerRowNum = headerRowEntry.key
        val headerCells = headerRowEntry.value

        var colName = -1
        var colCategory = -1
        var colUnit = -1
        var colPrice = -1

        // Primary match: exact / canonical matching
        for ((colIdx, text) in headerCells) {
            val norm = text.trim().lowercase().replace("_", " ").replace("-", " ")
            when {
                colName == -1 && (norm == "material name" || norm == "material" || norm == "materialname" || norm == "item name" || norm == "item" || norm == "jina la kifaa" || norm == "jina") -> {
                    colName = colIdx
                }
                colCategory == -1 && (norm == "category" || norm == "kundi" || norm == "aina" || norm == "aina ya kifaa") -> {
                    colCategory = colIdx
                }
                colUnit == -1 && (norm == "unit" || norm == "units" || norm == "kipimo" || norm == "vipimo") -> {
                    colUnit = colIdx
                }
                colPrice == -1 && (norm == "price" || norm == "unit price" || norm == "unitprice" || norm == "bei" || norm == "bei ya kifaa" || norm == "cost" || norm == "unit cost" || norm == "amount") -> {
                    colPrice = colIdx
                }
            }
        }

        // Secondary fallback match: partial substring matching
        if (colName == -1 || colCategory == -1 || colUnit == -1 || colPrice == -1) {
            for ((colIdx, text) in headerCells) {
                val norm = text.trim().lowercase()
                if (colName == -1 && (norm.contains("material") || (norm.contains("name") && !norm.contains("category")))) {
                    colName = colIdx
                } else if (colCategory == -1 && (norm.contains("categor") || norm.contains("kundi") || norm.contains("aina"))) {
                    colCategory = colIdx
                } else if (colUnit == -1 && (norm.contains("unit") || norm.contains("kipimo"))) {
                    colUnit = colIdx
                } else if (colPrice == -1 && (norm.contains("price") || norm.contains("bei") || norm.contains("cost"))) {
                    colPrice = colIdx
                }
            }
        }

        // Validate that all 4 required columns were matched
        val missingColumns = mutableListOf<String>()
        if (colName == -1) missingColumns.add("Material Name")
        if (colCategory == -1) missingColumns.add("Category")
        if (colUnit == -1) missingColumns.add("Unit")
        if (colPrice == -1) missingColumns.add("Price")

        if (missingColumns.isNotEmpty()) {
            return ExcelParseResult.Error(
                "Invalid Excel format. Missing required column(s): ${missingColumns.joinToString(", ")}.\n" +
                "The file must contain: Material Name, Category, Unit, Price."
            )
        }

        val materials = mutableListOf<ParsedExcelMaterial>()
        var failedRows = 0

        for ((rowNum, cells) in sheetData) {
            if (rowNum <= headerRowNum) continue

            // Skip completely empty lines
            if (cells.values.all { it.isBlank() }) continue

            val rawName = cells[colName]?.trim() ?: ""
            val rawCategory = cells[colCategory]?.trim() ?: ""
            val rawUnit = cells[colUnit]?.trim() ?: "Pcs"
            val rawPrice = cells[colPrice]?.trim() ?: ""

            // Validate Material Name
            if (rawName.isBlank()) {
                failedRows++
                continue
            }

            // Validate Price
            val cleanPrice = rawPrice.replace(",", "").replace(" ", "").replace("TZS", "", ignoreCase = true).replace("/=", "")
            val price = cleanPrice.toDoubleOrNull()
            if (price == null || price < 0.0) {
                failedRows++
                continue
            }

            // Canonicalize / preserve Category (supports Electrical, Plumbing, Construction & custom categories)
            val finalCategory = MaterialCategoryUtils.getCanonicalCategory(rawCategory, rawName)

            val finalUnit = if (rawUnit.isNotBlank()) rawUnit else "Pcs"

            materials.add(
                ParsedExcelMaterial(
                    name = rawName,
                    category = finalCategory,
                    unit = finalUnit,
                    price = price
                )
            )
        }

        if (materials.isEmpty() && failedRows == 0) {
            return ExcelParseResult.Error("The Excel file has no data rows under the header.")
        }

        return ExcelParseResult.Success(
            materials = materials,
            failedRows = failedRows
        )
    }
}
