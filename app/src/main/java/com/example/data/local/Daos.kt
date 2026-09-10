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

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: Int): MaterialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: MaterialEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(materials: List<MaterialEntity>)

    @Update
    suspend fun updateMaterial(material: MaterialEntity)

    @Delete
    suspend fun deleteMaterial(material: MaterialEntity)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM materials")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM materials")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM materials WHERE name LIKE '%[DEMO / SAMPLE]%' OR category LIKE '%demo%'")
    suspend fun getDemoCount(): Int

    @Query("SELECT COUNT(*) FROM materials WHERE category = :category")
    suspend fun getCountByCategory(category: String): Int

    @Query("UPDATE materials SET category = 'Electrical' WHERE category != 'Plumbing' AND category != 'Construction'")
    suspend fun normalizeLegacyCategories()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

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

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM customers WHERE name LIKE '%[DEMO / SAMPLE]%' OR notes LIKE '%[DEMO / SAMPLE]%'")
    suspend fun getDemoCount(): Int
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    fun getAllQuotes(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun getQuoteById(id: Int): QuoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: QuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quotes: List<QuoteEntity>)

    @Update
    suspend fun updateQuote(quote: QuoteEntity)

    @Delete
    suspend fun deleteQuote(quote: QuoteEntity)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM quotes")
    suspend fun deleteAll()

    @Query("UPDATE quotes SET status = 'invoice', number = :newNumber WHERE id = :id")
    suspend fun convertToInvoice(id: Int, newNumber: String)

    @Query("UPDATE quotes SET paid = :paid WHERE id = :id")
    suspend fun setPaidStatus(id: Int, paid: Boolean)

    @Query("SELECT COUNT(*) FROM quotes")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM quotes WHERE number LIKE 'DEMO-%'")
    suspend fun getDemoCount(): Int
}
