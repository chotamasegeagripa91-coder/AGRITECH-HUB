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
                prefs.setDemoDataInitialized(true)
                prefs.setDemoQuotesRemoved(true)
                prefs.setDemoCustomersRemoved(true)
                prefs.setDemoMaterialsRemoved(true)

                instance
            }
        }

        suspend fun seedDefaultDataIfEmpty(
            materialDao: MaterialDao,
            customerDao: CustomerDao,
            quoteDao: QuoteDao,
            preferences: AppPreferences? = null
        ) {
            preferences?.setDemoDataInitialized(true)
            preferences?.setDemoQuotesRemoved(true)
            preferences?.setDemoCustomersRemoved(true)
            preferences?.setDemoMaterialsRemoved(true)
        }

        suspend fun seedDefaultCustomersAndQuotesIfEmpty(
            customerDao: CustomerDao,
            quoteDao: QuoteDao
        ) {
            // No sample data seeded
        }

        suspend fun populateDefaultData(
            materialDao: MaterialDao,
            customerDao: CustomerDao,
            quoteDao: QuoteDao
        ) {
            // No sample data seeded
        }

        fun getAllDefaultMaterials(): List<MaterialEntity> {
            return emptyList()
        }

        fun isDefaultCatalogItem(name: String, category: String = ""): Boolean {
            return false
        }
    }
}
