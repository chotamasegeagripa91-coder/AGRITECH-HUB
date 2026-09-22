package com.example.data.local

import androidx.room.*
import com.example.data.models.CustomerEntity
import com.example.data.models.MaterialEntity
import com.example.data.models.QuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials ORDER BY name ASC")
    fun getAllMaterials(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials ORDER BY name ASC")
    suspend fun getAllMaterialsList(): List<MaterialEntity>

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: Int): MaterialEntity?

    @Query("SELECT * FROM materials WHERE internalCode = :code LIMIT 1")
    suspend fun getMaterialByInternalCode(code: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE isDemo = 1")
    suspend fun getDemoMaterials(): List<MaterialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: MaterialEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(materials: List<MaterialEntity>): List<Long>

    @Update
    suspend fun updateMaterial(material: MaterialEntity)

    @Update
    suspend fun updateAll(materials: List<MaterialEntity>)

    @Delete
    suspend fun deleteMaterial(material: MaterialEntity)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM materials WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query("DELETE FROM materials")
    suspend fun deleteAll()

    @Query("DELETE FROM materials WHERE isDemo = 1 OR internalCode LIKE 'DEMO-%'")
    suspend fun deleteDemoMaterials()

    @Query("DELETE FROM materials WHERE isDemo = 0")
    suspend fun deleteUserMaterials()

    @Query("SELECT COUNT(*) FROM materials")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM materials WHERE isDemo = 1 OR internalCode LIKE 'DEMO-%'")
    suspend fun getDemoCount(): Int

    @Query("SELECT COUNT(*) FROM materials WHERE category = :category")
    suspend fun getCountByCategory(category: String): Int

    @Query("DELETE FROM materials WHERE category = :category COLLATE NOCASE")
    suspend fun deleteByCategory(category: String)

    @Query("SELECT DISTINCT category FROM materials WHERE category IS NOT NULL AND category != ''")
    suspend fun getAllCategoriesList(): List<String>
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun getAllCustomersList(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Int): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()

    @Query("DELETE FROM customers WHERE name LIKE '%[DEMO%' OR name LIKE '%[SAMPLE%' OR notes LIKE '%[DEMO%' OR notes LIKE '%[SAMPLE%' OR name LIKE '%Demo%' OR name LIKE '%Sample%' OR notes LIKE '%Demo%' OR notes LIKE '%Sample%' OR name LIKE '%Mfano%' OR notes LIKE '%Mfano%'")
    suspend fun deleteDemoCustomers()

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM customers WHERE name LIKE '%[DEMO%' OR name LIKE '%[SAMPLE%' OR name LIKE '%Demo%' OR name LIKE '%Sample%'")
    suspend fun getDemoCount(): Int
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    fun getAllQuotes(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    suspend fun getAllQuotesList(): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun getQuoteById(id: Int): QuoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: QuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quotes: List<QuoteEntity>)

    @Update
    suspend fun updateQuote(quote: QuoteEntity)

    @Update
    suspend fun updateAll(quotes: List<QuoteEntity>)

    @Delete
    suspend fun deleteQuote(quote: QuoteEntity)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM quotes")
    suspend fun deleteAll()

    @Query("DELETE FROM quotes WHERE number LIKE 'DEMO-%' OR number LIKE 'SAMPLE-%' OR description LIKE '%Demo%' OR description LIKE '%Sample%' OR description LIKE '%Mfano%' OR customerName LIKE '%Demo%' OR customerName LIKE '%Sample%' OR customerName LIKE '%Mfano%'")
    suspend fun deleteDemoQuotes()

    @Query("UPDATE quotes SET status = 'invoice', number = :newNumber WHERE id = :id")
    suspend fun convertToInvoice(id: Int, newNumber: String)

    @Query("UPDATE quotes SET paid = :paid WHERE id = :id")
    suspend fun setPaidStatus(id: Int, paid: Boolean)

    @Query("SELECT COUNT(*) FROM quotes")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM quotes WHERE number LIKE 'DEMO-%'")
    suspend fun getDemoCount(): Int
}
