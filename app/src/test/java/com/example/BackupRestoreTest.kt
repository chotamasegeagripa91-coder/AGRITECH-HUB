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
}
