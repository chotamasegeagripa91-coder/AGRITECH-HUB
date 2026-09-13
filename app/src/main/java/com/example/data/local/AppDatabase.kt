package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.models.CustomerEntity
import com.example.data.models.MaterialEntity
import com.example.data.models.QuoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [MaterialEntity::class, CustomerEntity::class, QuoteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun customerDao(): CustomerDao
    abstract fun quoteDao(): QuoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE materials ADD COLUMN internalCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE materials ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE materials ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_materials_internalCode ON materials(internalCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_materials_category_name_unit ON materials(category, name, unit)")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agritech_hub_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance

                val prefs = AppPreferences(context.applicationContext)
                if (!prefs.isDemoDataInitialized()) {
                    scope.launch(Dispatchers.IO) {
                        seedDefaultDataIfEmpty(
                            instance.materialDao(),
                            instance.customerDao(),
                            instance.quoteDao(),
                            prefs
                        )
                    }
                }

                instance
            }
        }

        suspend fun seedDefaultDataIfEmpty(
            materialDao: MaterialDao,
            customerDao: CustomerDao,
            quoteDao: QuoteDao,
            preferences: AppPreferences? = null
        ) {
            if (preferences != null && (preferences.isDemoDataInitialized() || preferences.areDemoQuotesRemoved())) {
                return
            }

            if (quoteDao.getDemoCount() == 0 && quoteDao.getAllQuotesList().isEmpty()) {
                populateSampleDocuments(quoteDao)
            }
            preferences?.setDemoDataInitialized(true)
            preferences?.setDemoDataSeeded(true)
        }

        suspend fun seedDefaultCustomersAndQuotesIfEmpty(
            customerDao: CustomerDao,
            quoteDao: QuoteDao
        ) {
            if (quoteDao.getDemoCount() == 0 && quoteDao.getAllQuotesList().isEmpty()) {
                populateSampleDocuments(quoteDao)
            }
        }

        suspend fun populateDefaultData(
            materialDao: MaterialDao,
            customerDao: CustomerDao,
            quoteDao: QuoteDao
        ) {
            seedDefaultDataIfEmpty(materialDao, customerDao, quoteDao)
        }

        fun getAllDefaultMaterials(): List<MaterialEntity> {
            return emptyList()
        }

        fun isDefaultCatalogItem(name: String, category: String = ""): Boolean {
            return false
        }

        private suspend fun populateSampleDocuments(quoteDao: QuoteDao) {
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFmt.format(Date())
            val validUntilStr = dateFmt.format(Date(System.currentTimeMillis() + 14L * 86400000L))
            val pastDateStr = dateFmt.format(Date(System.currentTimeMillis() - 7L * 86400000L))

            val sampleDocuments = listOf(
                // 1 Sample Quotation
                QuoteEntity(
                    number = "SAMPLE-QUO-001",
                    date = todayStr,
                    validUntil = validUntilStr,
                    customerName = "[SAMPLE] Mhandisi Juma Rashid",
                    customerPhone = "+255 712 345 678",
                    customerLocation = "Mikocheni, Dar es Salaam",
                    description = "[SAMPLE] Makadirio ya Mfano - Ufungaji wa Mfumo wa Umeme (Sample Quotation)",
                    itemsJson = """[{"id":"1","name":"Cable 1.5mm Twin & Earth (Flat)","unit":"Roll","price":180000.0,"quantity":2.0,"total":360000.0},{"id":"2","name":"Cable 2.5mm Twin & Earth (Flat)","unit":"Roll","price":280000.0,"quantity":3.0,"total":840000.0},{"id":"3","name":"Consumer Unit DB 12-Way Flush","unit":"Pcs","price":65000.0,"quantity":1.0,"total":65000.0},{"id":"4","name":"Circuit Breaker MCB 10A (Lighting)","unit":"Pcs","price":8500.0,"quantity":6.0,"total":51000.0},{"id":"5","name":"Socket 13A Twin Double Switch Socket","unit":"Pcs","price":12000.0,"quantity":8.0,"total":96000.0},{"id":"6","name":"LED Ceiling Panel 18W Round Warm/White","unit":"Pcs","price":15000.0,"quantity":10.0,"total":150000.0}]""",
                    materialsTotal = 1562000.0,
                    labour = 350000.0,
                    grandTotal = 1912000.0,
                    status = "quotation",
                    paid = false,
                    createdAt = System.currentTimeMillis()
                ),
                // 1 Sample Invoice
                QuoteEntity(
                    number = "SAMPLE-INV-001",
                    date = pastDateStr,
                    validUntil = "",
                    customerName = "[SAMPLE] Kampuni ya Ujenzi Bora Ltd",
                    customerPhone = "+255 754 987 654",
                    customerLocation = "Kijitonyama, Dar es Salaam",
                    description = "[SAMPLE] Ankara ya Mfano - Malipo ya Awamu ya Ufungaji Taa na Swichi (Sample Invoice)",
                    itemsJson = """[{"id":"1","name":"LED Ceiling Panel 18W Round Warm/White","unit":"Pcs","price":15000.0,"quantity":12.0,"total":180000.0},{"id":"2","name":"2 Gang 1 Way Switch","unit":"Pcs","price":6500.0,"quantity":6.0,"total":39000.0},{"id":"3","name":"Socket 13A Twin Double Switch Socket","unit":"Pcs","price":12000.0,"quantity":8.0,"total":96000.0},{"id":"4","name":"Conduit Pipe 20mm PVC Heavy Duty","unit":"Pcs","price":4500.0,"quantity":15.0,"total":67500.0}]""",
                    materialsTotal = 382500.0,
                    labour = 120000.0,
                    grandTotal = 502500.0,
                    status = "invoice",
                    paid = true,
                    createdAt = System.currentTimeMillis() - 7L * 86400000L
                )
            )
            quoteDao.insertAll(sampleDocuments)
        }
    }
}
