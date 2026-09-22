package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.models.CustomerEntity
import com.example.data.models.MaterialEntity
import com.example.data.repository.AgritechRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreTest {

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

    @Test
    fun testRejectBlankOrEmptyFile() = runBlocking {
        val (success, message) = repository.importDataFromJsonDetailed("", isSwahili = true)
        assertFalse(success)
        assertTrue(message.contains("tupu") || message.contains("empty"))

        val (enSuccess, enMessage) = repository.importDataFromJsonDetailed("   ", isSwahili = false)
        assertFalse(enSuccess)
        assertTrue(enMessage.contains("empty"))
    }

    @Test
    fun testRejectInvalidNonJsonContent() = runBlocking {
        val invalidContent = "This is a random text file, not a JSON backup."
        val (success, message) = repository.importDataFromJsonDetailed(invalidContent, isSwahili = true)
        assertFalse(success)
        assertTrue(message.contains("sahihi") || message.contains("limeharibika"))

        val (enSuccess, enMessage) = repository.importDataFromJsonDetailed(invalidContent, isSwahili = false)
        assertFalse(enSuccess)
        assertTrue(enMessage.contains("valid JSON") || enMessage.contains("corrupted"))
    }

    @Test
    fun testRejectJsonWithoutAgritechSignature() = runBlocking {
        val alienJson = """
            {
                "appName": "RandomApp",
                "data": [1, 2, 3]
            }
        """.trimIndent()
        val (success, message) = repository.importDataFromJsonDetailed(alienJson, isSwahili = true)
        assertFalse(success)
        assertTrue(message.contains("AGRITECH HUB"))

        val (enSuccess, enMessage) = repository.importDataFromJsonDetailed(alienJson, isSwahili = false)
        assertFalse(enSuccess)
        assertTrue(enMessage.contains("AGRITECH HUB"))
    }

    @Test
    fun testValidBackupExportAndRestore() = runBlocking {
        // Insert sample material and customer
        val sampleMaterial = MaterialEntity(
            name = "Copper Cable 2.5mm",
            category = "Cables",
            unit = "Roll",
            price = 85000.0
        )
        repository.insertMaterial(sampleMaterial)

        val sampleCustomer = CustomerEntity(
            name = "John Mdee",
            phone = "0712345678",
            location = "Kinondoni, DSM"
        )
        repository.insertCustomer(sampleCustomer)

        // Export JSON
        val exportedJson = repository.exportAllDataAsJson(
            materials = listOf(sampleMaterial),
            customers = listOf(sampleCustomer),
            quotes = emptyList()
        )
        assertTrue(exportedJson.contains("AGRITECH HUB"))
        assertTrue(exportedJson.contains("Copper Cable 2.5mm"))
        assertTrue(exportedJson.contains("John Mdee"))

        // Clear database
        database.clearAllTables()

        // Restore JSON
        val (restoreSuccess, restoreMsg) = repository.importDataFromJsonDetailed(exportedJson, isSwahili = true)
        assertTrue(restoreSuccess)
        assertTrue(restoreMsg.contains("kikamilifu"))
    }

    @Test
    fun testAppPreferencesBackupMetadata() {
        val fileName = "AgritechHub_Backup_20260909_120000.json"
        val timestamp = 1757424000000L
        val size = 15360L

        preferences.setLastBackupInfo(fileName, timestamp, size)

        assertEquals(fileName, preferences.getLastBackupFileName())
        assertEquals(timestamp, preferences.getLastBackupTimestamp())
        assertEquals(size, preferences.getLastBackupSize())
    }

    @Test
    fun testGoogleAccountBackupAndRestoreLifecycle() = runBlocking {
        val testUid = "user_google_12345"
        val testEmail = "fundi.agritech@gmail.com"
        preferences.setBackupGoogleAccountEmail(testEmail)
        preferences.setBackupGoogleAccountUid(testUid)

        assertEquals(testEmail, preferences.getBackupGoogleAccountEmail())

        // 1. Create realistic user data
        val material1 = MaterialEntity(
            id = 101,
            name = "Schneider MCB 16A Single Pole",
            category = "Switchgear & Distribution",
            unit = "Pcs",
            price = 14500.0,
            userId = testUid
        )
        val material2 = MaterialEntity(
            id = 102,
            name = "PVC Conduit Pipe 20mm 3m",
            category = "Conduits & Containment",
            unit = "Pcs",
            price = 5500.0,
            userId = testUid
        )
        repository.insertMaterial(material1)
        repository.insertMaterial(material2)

        val customer = CustomerEntity(
            id = 201,
            name = "Baraka Electrical Enterprises",
            phone = "+255754123456",
            location = "Mwenge, Dar es Salaam",
            notes = "Customer since 2024"
        )
        repository.insertCustomer(customer)

        val quote = com.example.data.models.QuoteEntity(
            id = 301,
            number = "QTN-2026-001",
            customerId = 201,
            customerName = "Baraka Electrical Enterprises",
            customerPhone = "+255754123456",
            customerLocation = "Mwenge, Dar es Salaam",
            date = "2026-09-20",
            validUntil = "2026-10-04",
            description = "Wiring for new warehouse building",
            status = "invoice",
            paid = false,
            itemsJson = """[{"id":"1","name":"Schneider MCB 16A Single Pole","quantity":10.0,"price":14500.0,"total":145000.0}]""",
            materialsTotal = 145000.0,
            labour = 40000.0,
            grandTotal = 185000.0
        )
        repository.insertQuote(quote)

        // Create CloudBackupPayload
        val payload = com.example.data.models.CloudBackupPayload(
            version = 1,
            timestamp = System.currentTimeMillis(),
            userId = testUid,
            business = com.example.data.models.BusinessSettings(
                name = "AGRITECH PRO INSTALLATIONS",
                phone1 = "+255754123456",
                email = testEmail
            ),
            materials = listOf(material1, material2),
            customers = listOf(customer),
            quotes = listOf(quote)
        )

        // 2. Simulate complete uninstall / new phone / database wipe
        database.clearAllTables()
        assertEquals(0, database.materialDao().getAllMaterialsList().size)
        assertEquals(0, database.customerDao().getAllCustomersList().size)
        assertEquals(0, database.quoteDao().getAllQuotesList().size)

        // 3. User reinstalls and signs into same Google account -> restoreFromPayload
        val restoreSuccess = repository.restoreFromPayload(payload, testUid)
        assertTrue(restoreSuccess)

        // 4. Assert all items returned into Room database
        val restoredMaterials = database.materialDao().getAllMaterialsList()
        val restoredCustomers = database.customerDao().getAllCustomersList()
        val restoredQuotes = database.quoteDao().getAllQuotesList()

        assertTrue(restoredMaterials.isNotEmpty())
        assertTrue(restoredMaterials.any { it.name == "Schneider MCB 16A Single Pole" })
        assertTrue(restoredMaterials.any { it.name == "PVC Conduit Pipe 20mm 3m" })

        assertEquals(1, restoredCustomers.size)
        assertEquals("Baraka Electrical Enterprises", restoredCustomers[0].name)
        assertEquals("+255754123456", restoredCustomers[0].phone)

        assertEquals(1, restoredQuotes.size)
        assertEquals("QTN-2026-001", restoredQuotes[0].number)
        assertEquals("invoice", restoredQuotes[0].status)
        assertEquals(185000.0, restoredQuotes[0].grandTotal, 0.01)

        // 5. Verify no duplicate IDs on repeated restore
        repository.restoreFromPayload(payload, testUid)
        val secondPassCustomers = database.customerDao().getAllCustomersList()
        val secondPassQuotes = database.quoteDao().getAllQuotesList()
        assertEquals(1, secondPassCustomers.size)
        assertEquals(1, secondPassQuotes.size)
    }
}
