package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.models.BusinessSettings
import com.example.data.models.CloudBackupPayload
import com.example.data.models.MaterialEntity
import com.example.data.repository.AgritechRepository
import com.example.ui.utils.DemoUtils
import com.example.ui.utils.MaterialCategoryUtils
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
class MaterialDeduplicationTest {

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
    fun testDemoMaterialsSeededAndCountStable() = runBlocking {
        // Seed default demo documents
        repository.ensureDemoDataSeeded()
        val initialCount = database.quoteDao().getAllQuotesList().size
        assertTrue("Demo documents should be seeded (2 items)", initialCount == 2)

        // Running ensureDemoDataSeeded again must NOT duplicate demo items
        repository.ensureDemoDataSeeded()
        val secondCount = database.quoteDao().getAllQuotesList().size
        assertEquals("Demo documents must remain idempotent and not duplicate", initialCount, secondCount)
    }

    @Test
    fun testCleanupDuplicateMaterials() = runBlocking {
        val matDao = database.materialDao()

        // Insert 3 duplicates with auto-generated IDs (simulating existing corrupt database)
        val m1 = MaterialEntity(id = 0, name = "Copper Cable 2.5mm", unit = "Roll", price = 85000.0, category = "Electrical")
        val m2 = MaterialEntity(id = 0, name = "Copper Cable 2.5mm", unit = "Roll", price = 0.0, category = "Electrical")
        val m3 = MaterialEntity(id = 0, name = "copper cable 2.5mm", unit = "roll", price = 85000.0, category = "Electrical")
        matDao.insertAll(listOf(m1, m2, m3))

        val countBefore = matDao.getAllMaterialsList().size
        assertEquals(3, countBefore)

        val deletedCount = repository.performDuplicateMaterialCleanup()
        assertEquals(2, deletedCount)

        val remaining = matDao.getAllMaterialsList()
        assertEquals(1, remaining.size)
        assertEquals(85000.0, remaining[0].price, 0.001)
        assertTrue(remaining[0].internalCode.isNotBlank())
    }

    @Test
    fun testValidMaterialVariantsAreNotMerged() = runBlocking {
        val matDao = database.materialDao()

        val v1 = MaterialEntity(id = 0, name = "PPR Pipe 20mm (PN20) 4m Hot/Cold", unit = "Pcs", price = 12000.0, category = "Plumbing")
        val v2 = MaterialEntity(id = 0, name = "PPR Pipe 25mm (PN20) 4m Hot/Cold", unit = "Pcs", price = 18000.0, category = "Plumbing")
        val v3 = MaterialEntity(id = 0, name = "PPR Pipe 32mm (PN20) 4m", unit = "Pcs", price = 28000.0, category = "Plumbing")
        matDao.insertAll(listOf(v1, v2, v3))

        repository.performDuplicateMaterialCleanup()

        val remaining = matDao.getAllMaterialsList()
        assertEquals("Distinct dimensional variants must NOT be merged", 3, remaining.size)
    }

    @Test
    fun testLogoutLoginCycleStabilityAtLeast5Times() = runBlocking {
        // Step 1: Initialize database with demo materials
        repository.ensureDemoDataSeeded()
        val initialDemoCount = database.materialDao().getAllMaterialsList().size

        // Step 2: Add 5 user materials
        val userMaterials = listOf(
            MaterialEntity(id = 0, name = "Custom Solar Inverter 5kW", unit = "Pcs", price = 2500000.0, category = "Electrical", userId = "user123"),
            MaterialEntity(id = 0, name = "Custom Water Pump 1.5HP", unit = "Pcs", price = 450000.0, category = "Plumbing", userId = "user123"),
            MaterialEntity(id = 0, name = "Custom Gypsum Board 9mm", unit = "Pcs", price = 24000.0, category = "Construction", userId = "user123"),
            MaterialEntity(id = 0, name = "Custom Wall Tile 30x60", unit = "Sqm", price = 28000.0, category = "Construction", userId = "user123"),
            MaterialEntity(id = 0, name = "Custom Brass Tap 3/4\"", unit = "Pcs", price = 15000.0, category = "Plumbing", userId = "user123")
        )
        repository.upsertMaterials(userMaterials, "user123")

        val totalExpected = initialDemoCount + 5
        val countAfterAdd = database.materialDao().getAllMaterialsList().size
        assertEquals(totalExpected, countAfterAdd)

        // Step 3: Simulate 6 consecutive logout/login/restore cycles!
        for (cycle in 1..6) {
            // Logout: clear local user data, preserve demo
            repository.clearAllLocalData(preserveDemo = true)

            // Demo data remains
            val countAfterLogout = database.materialDao().getAllMaterialsList().size
            assertEquals("Cycle $cycle: Demo data must remain intact on logout", initialDemoCount, countAfterLogout)

            // Login & cloud restore:
            // Cloud payload has the user materials
            val payload = CloudBackupPayload(
                version = 1,
                timestamp = System.currentTimeMillis(),
                userId = "user123",
                business = BusinessSettings(),
                materials = userMaterials,
                customers = emptyList(),
                quotes = emptyList()
            )

            repository.restoreFromPayload(payload, "user123")
            repository.cleanupDuplicateMaterialsOnce()

            val countAfterRestore = database.materialDao().getAllMaterialsList().size
            assertEquals(
                "Cycle $cycle: Total materials count MUST be stable across restore cycles and not duplicate",
                totalExpected,
                countAfterRestore
            )
        }
    }

    @Test
    fun testDemoItemsNeverMarkedAsRealUserOrUploaded() = runBlocking {
        repository.ensureDemoDataSeeded()
        val allMaterials = database.materialDao().getAllMaterialsList()

        for (m in allMaterials) {
            assertTrue("Seeded default material '${m.name}' must be recognized as demo", DemoUtils.isDemoMaterial(m))
        }

        // Filtering for cloud upload must exclude all demo items
        val forCloudUpload = allMaterials.filter { !DemoUtils.isDemoMaterial(it) }
        assertEquals("No demo material should ever be included in cloud upload payload", 0, forCloudUpload.size)
    }
}
