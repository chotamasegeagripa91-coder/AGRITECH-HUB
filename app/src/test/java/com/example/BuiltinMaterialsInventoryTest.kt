package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.local.BuiltinMaterialsCatalog
import com.example.data.models.QuoteItem
import com.example.data.models.MaterialEntity
import com.example.data.repository.AgritechRepository
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
class BuiltinMaterialsInventoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: AppPreferences
    private lateinit var repository: AgritechRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = AppPreferences(context)
        preferences.setAllBuiltinMaterialsDeleted(false)
        preferences.setBuiltinInventorySeeded(false)

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
    fun testAll103MaterialsPresentInCatalogWithCorrectCategories() {
        val all = BuiltinMaterialsCatalog.ALL_BUILTIN_MATERIALS
        assertEquals("Must have exactly 103 items in built-in catalog", 103, all.size)

        val electrical = all.filter { it.category == MaterialCategoryUtils.ELECTRICAL }
        val plumbing = all.filter { it.category == MaterialCategoryUtils.PLUMBING }

        assertEquals("Must have exactly 63 Electrical materials", 63, electrical.size)
        assertEquals("Must have exactly 40 Plumbing materials", 40, plumbing.size)

        // Verify every material has non-blank name, unit, code, and price > 0
        for (item in all) {
            assertTrue("Name must not be blank: ${item.name}", item.name.isNotBlank())
            assertTrue("Category must be valid: ${item.category}", item.category == MaterialCategoryUtils.ELECTRICAL || item.category == MaterialCategoryUtils.PLUMBING)
            assertTrue("Unit must not be blank: ${item.unit}", item.unit.isNotBlank())
            assertTrue("Price must be > 0: ${item.name} had price ${item.price}", item.price > 0.0)
            assertTrue("Internal code must start with MAT-EXCEL-: ${item.internalCode}", item.internalCode.startsWith("MAT-EXCEL-"))
            assertFalse("Built-in materials must not be marked as demo", item.isDemo)
        }
    }

    @Test
    fun testSeedingIsIdempotentAndDoesNotDuplicateOnMultipleRuns() = runBlocking {
        // Run 1: initial seed
        repository.ensureBuiltinMaterialsSeeded()
        val countAfterFirst = database.materialDao().getAllMaterialsList().size
        assertEquals(103, countAfterFirst)

        // Run 2: simulate app restart
        repository.ensureBuiltinMaterialsSeeded()
        val countAfterSecond = database.materialDao().getAllMaterialsList().size
        assertEquals(103, countAfterSecond)

        // Run 3: simulate user login with userId
        repository.ensureBuiltinMaterialsSeeded("user_abc")
        val countAfterThird = database.materialDao().getAllMaterialsList().size
        assertEquals(103, countAfterThird)
    }

    @Test
    fun testCategoryFilteringAndSearchability() = runBlocking {
        repository.ensureBuiltinMaterialsSeeded()
        val all = database.materialDao().getAllMaterialsList()

        // Test filtering Electrical
        val electrical = all.filter { MaterialCategoryUtils.matchesCategory(it, MaterialCategoryUtils.ELECTRICAL) }
        assertEquals(63, electrical.size)

        // Test filtering Plumbing
        val plumbing = all.filter { MaterialCategoryUtils.matchesCategory(it, MaterialCategoryUtils.PLUMBING) }
        assertEquals(40, plumbing.size)

        // Test searching specific items from Excel
        val conduit = all.filter { it.name.contains("Conduit 20mm", ignoreCase = true) }
        assertEquals(1, conduit.size)
        assertEquals(1200.0, conduit.first().price, 0.01)

        val pedrollo = all.filter { it.name.contains("Pedrollo 0.5", ignoreCase = true) }
        assertEquals(1, pedrollo.size)
        assertEquals(105000.0, pedrollo.first().price, 0.01)
        assertEquals("Plumbing", pedrollo.first().category)

        val shinge = all.filter { it.name.contains("Shinge 0.5", ignoreCase = true) }
        assertEquals(1, shinge.size)
        assertEquals(130000.0, shinge.first().price, 0.01)

        // Verify multi-variant items are preserved with correct prices
        val earthRods = all.filter { it.name.equals("Earth rod copper", ignoreCase = true) }
        assertEquals("All 3 Earth rod copper variants must be present", 3, earthRods.size)
        val prices = earthRods.map { it.price }.sorted()
        assertEquals(listOf(15000.0, 25000.0, 50000.0), prices)
    }

    @Test
    fun testPreservesUserEditsToBuiltinMaterials() = runBlocking {
        repository.ensureBuiltinMaterialsSeeded()

        val all = database.materialDao().getAllMaterialsList()
        val itemToEdit = all.first { it.name == "Conduit 20mm" }
        assertEquals(1200.0, itemToEdit.price, 0.01)

        // Customer edits price from 1,200 to 1,800
        val edited = itemToEdit.copy(price = 1800.0)
        database.materialDao().updateMaterial(edited)

        // Re-run seeding (e.g. app restart or update)
        repository.ensureBuiltinMaterialsSeeded()

        val itemAfterReSeed = database.materialDao().getMaterialById(itemToEdit.id)
        assertNotNull(itemAfterReSeed)
        assertEquals("Customer's edited price of 1800 must be preserved", 1800.0, itemAfterReSeed!!.price, 0.01)
    }

    @Test
    fun testDeletedBuiltinMaterialIsNotRecreated() = runBlocking {
        repository.ensureBuiltinMaterialsSeeded()

        val all = database.materialDao().getAllMaterialsList()
        val itemToDelete = all.first { it.name == "Junction box" }

        // User deletes this material
        repository.deleteMaterial(itemToDelete)

        val countAfterDelete = database.materialDao().getAllMaterialsList().size
        assertEquals(102, countAfterDelete)
        assertNull(database.materialDao().getMaterialById(itemToDelete.id))

        // Re-run seeding (e.g. app restart)
        repository.ensureBuiltinMaterialsSeeded()

        // Item must NOT be resurrected
        val countAfterReSeed = database.materialDao().getAllMaterialsList().size
        assertEquals("Deleted item must not be recreated", 102, countAfterReSeed)
        val searchDeleted = database.materialDao().getAllMaterialsList().filter { it.name == "Junction box" }
        assertTrue("Junction box must not be recreated", searchDeleted.isEmpty())
    }

    @Test
    fun testDeleteAllMaterialsDoesNotRecreateOnRestart() = runBlocking {
        repository.ensureBuiltinMaterialsSeeded()
        assertEquals(103, database.materialDao().getAllMaterialsList().size)

        repository.deleteAllMaterials()
        assertEquals(0, database.materialDao().getAllMaterialsList().size)

        // App re-launches
        repository.ensureBuiltinMaterialsSeeded()
        assertEquals("Seeding must respect deleteAllMaterials and not restore anything", 0, database.materialDao().getAllMaterialsList().size)
    }

    @Test
    fun testQuoteAndInvoiceLineItemCalculationWithBuiltinMaterial() = runBlocking {
        repository.ensureBuiltinMaterialsSeeded()

        val all = database.materialDao().getAllMaterialsList()
        val mainSwitch = all.first { it.name.contains("Main switch board 4way tronics") }
        assertEquals(85000.0, mainSwitch.price, 0.01)

        // Simulate creating a line item in quotation/invoice
        val quoteItem = QuoteItem(
            id = "item_1",
            materialId = mainSwitch.id,
            name = mainSwitch.name,
            unit = mainSwitch.unit,
            price = mainSwitch.price,
            quantity = 3.0,
            total = mainSwitch.price * 3.0
        )

        assertEquals("Pcs", quoteItem.unit)
        assertEquals(85000.0, quoteItem.price, 0.01)
        assertEquals(255000.0, quoteItem.total, 0.01)
    }
}
