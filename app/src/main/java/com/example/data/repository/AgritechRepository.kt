package com.example.data.repository

import com.example.data.local.AppPreferences
import com.example.data.local.CustomerDao
import com.example.data.local.MaterialDao
import com.example.data.local.QuoteDao
import com.example.data.models.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class AgritechRepository(
    private val materialDao: MaterialDao,
    private val customerDao: CustomerDao,
    private val quoteDao: QuoteDao,
    val preferences: AppPreferences
) {
    val allMaterials: Flow<List<MaterialEntity>> = materialDao.getAllMaterials()
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val allQuotes: Flow<List<QuoteEntity>> = quoteDao.getAllQuotes()

    // Materials
    suspend fun insertMaterial(material: MaterialEntity) = materialDao.insertMaterial(material)
    suspend fun updateMaterial(material: MaterialEntity) = materialDao.updateMaterial(material)
    suspend fun deleteMaterial(material: MaterialEntity) = materialDao.deleteMaterial(material)
    suspend fun deleteMaterialById(id: Int) = materialDao.deleteById(id)

    // Customers
    suspend fun insertCustomer(customer: CustomerEntity) = customerDao.insertCustomer(customer)
    suspend fun updateCustomer(customer: CustomerEntity) = customerDao.updateCustomer(customer)
    suspend fun deleteCustomer(customer: CustomerEntity) = customerDao.deleteCustomer(customer)
    suspend fun deleteCustomerById(id: Int) = customerDao.deleteById(id)

    // Quotes & Invoices
    suspend fun insertQuote(quote: QuoteEntity) = quoteDao.insertQuote(quote)
    suspend fun updateQuote(quote: QuoteEntity) = quoteDao.updateQuote(quote)
    suspend fun deleteQuote(quote: QuoteEntity) = quoteDao.deleteQuote(quote)
    suspend fun deleteQuoteById(id: Int) = quoteDao.deleteById(id)
    suspend fun convertToInvoice(id: Int, newNumber: String) = quoteDao.convertToInvoice(id, newNumber)
    suspend fun setPaidStatus(id: Int, paid: Boolean) = quoteDao.setPaidStatus(id, paid)

    // Clear all local database tables and ensure demo data remains available
    suspend fun clearAllLocalData() {
        materialDao.deleteAll()
        customerDao.deleteAll()
        quoteDao.deleteAll()
        ensureDemoDataSeeded()
    }

    suspend fun ensureDemoDataSeeded() {
        com.example.data.local.AppDatabase.seedDefaultDataIfEmpty(
            materialDao,
            customerDao,
            quoteDao
        )
    }

    // JSON Backup Export
    suspend fun exportAllDataAsJson(
        materials: List<MaterialEntity>,
        customers: List<CustomerEntity>,
        quotes: List<QuoteEntity>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("appName", "AGRITECH HUB")

        val user = preferences.getUserAccount()
        if (user != null) {
            root.put("userId", user.userId)
            root.put("ownerFullName", user.ownerFullName)
            root.put("businessName", user.businessName)
        }

        // Business
        val biz = preferences.getBusinessSettings()
        val bizObj = JSONObject().apply {
            put("name", biz.name)
            put("slogan", biz.slogan)
            put("phone1", biz.phone1)
            put("phone2", biz.phone2)
            put("email", biz.email)
            put("address", biz.address)
            put("currency", biz.currency)
            put("bankName", biz.bankName)
            put("bankAccountNumber", biz.bankAccountNumber)
            put("bankAccountName", biz.bankAccountName)
            put("lipaNumber", biz.lipaNumber)
            put("mobileMoney", biz.mobileMoney)
            put("logoPath", biz.logoPath)
            put("signaturePath", biz.signaturePath)
            put("termsSw", biz.quotationTermsSw)
            put("termsEn", biz.quotationTermsEn)
        }
        root.put("business", bizObj)

        // Materials
        val matArray = JSONArray()
        for (m in materials) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("unit", m.unit)
                put("price", m.price)
                put("category", m.category)
            }
            matArray.put(obj)
        }
        root.put("materials", matArray)

        // Customers
        val custArray = JSONArray()
        for (c in customers) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("location", c.location)
                put("notes", c.notes)
            }
            custArray.put(obj)
        }
        root.put("customers", custArray)

        // Quotes
        val quoteArray = JSONArray()
        for (q in quotes) {
            val obj = JSONObject().apply {
                put("id", q.id)
                put("number", q.number)
                put("date", q.date)
                put("validUntil", q.validUntil)
                put("customerId", q.customerId)
                put("customerName", q.customerName)
                put("customerPhone", q.customerPhone)
                put("customerLocation", q.customerLocation)
                put("description", q.description)
                put("itemsJson", q.itemsJson)
                put("materialsTotal", q.materialsTotal)
                put("labour", q.labour)
                put("grandTotal", q.grandTotal)
                put("status", q.status)
                put("paid", q.paid)
                put("createdAt", q.createdAt)
            }
            quoteArray.put(obj)
        }
        root.put("quotes", quoteArray)

        return root.toString(2)
    }

    // JSON Backup Restore
    suspend fun importDataFromJsonDetailed(jsonStr: String, isSwahili: Boolean = true): Pair<Boolean, String> {
        if (jsonStr.isBlank()) {
            return Pair(
                false,
                if (isSwahili) "Faili halina maudhui yoyote (Faili tupu)."
                else "The file is completely empty."
            )
        }

        val root: JSONObject = try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            return Pair(
                false,
                if (isSwahili) "Faili lililochaguliwa si faili sahihi la JSON au limeharibika."
                else "The selected file is not a valid JSON file or is corrupted."
            )
        }

        // Validate Agritech Hub signature / structure
        val appName = root.optString("appName", "")
        if (root.has("appName") && !appName.equals("AGRITECH HUB", ignoreCase = true)) {
            return Pair(
                false,
                if (isSwahili) "Faili hili linatoka kwenye mfumo mwingine ($appName) na si nakala halali ya AGRITECH HUB."
                else "This file is from another application ($appName) and is not a valid AGRITECH HUB backup."
            )
        }

        // Validate backup belonging by Firebase User ID
        val backupUserId = root.optString("userId", "")
        val currentUserId = preferences.getUserAccount()?.userId ?: ""
        if (backupUserId.isNotBlank() && currentUserId.isNotBlank() && !backupUserId.equals(currentUserId, ignoreCase = true)) {
            return Pair(
                false,
                if (isSwahili) "Hifadhi hii ni ya akaunti nyingine na haiwezi kurejeshwa kwenye akaunti hii."
                else "This backup belongs to another account and cannot be restored to this account."
            )
        }

        val hasBusiness = root.has("business")
        val hasMaterials = root.has("materials")
        val hasCustomers = root.has("customers")
        val hasQuotes = root.has("quotes")

        if (!hasBusiness && !hasMaterials && !hasCustomers && !hasQuotes) {
            return Pair(
                false,
                if (isSwahili) "Faili hili halina data zozote zinazotambulika za AGRITECH HUB (Vifaa, Wateja, Ankara au Taarifa za Biashara)."
                else "This file contains no recognizable AGRITECH HUB data (Materials, Customers, Quotes, or Business Settings)."
            )
        }

        return try {
            var materialsCount = 0
            var customersCount = 0
            var quotesCount = 0
            var businessRestored = false

            if (hasBusiness) {
                val b = root.optJSONObject("business")
                if (b != null) {
                    preferences.saveBusinessSettings(
                        BusinessSettings(
                            name = b.optString("name", ""),
                            slogan = b.optString("slogan", ""),
                            phone1 = b.optString("phone1", ""),
                            phone2 = b.optString("phone2", ""),
                            email = b.optString("email", ""),
                            address = b.optString("address", ""),
                            currency = b.optString("currency", "TSh"),
                            bankName = b.optString("bankName", ""),
                            bankAccountNumber = b.optString("bankAccountNumber", ""),
                            bankAccountName = b.optString("bankAccountName", ""),
                            lipaNumber = b.optString("lipaNumber", ""),
                            mobileMoney = b.optString("mobileMoney", ""),
                            logoPath = b.optString("logoPath", "")
                        )
                    )
                    businessRestored = true
                }
            }

            if (hasMaterials) {
                val arr = root.optJSONArray("materials")
                if (arr != null) {
                    val list = mutableListOf<MaterialEntity>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val name = o.optString("name", "")
                        if (name.isNotBlank()) {
                            list.add(
                                MaterialEntity(
                                    id = o.optInt("id", 0),
                                    name = name,
                                    unit = o.optString("unit", "Pcs"),
                                    price = o.optDouble("price", 0.0),
                                    category = o.optString("category", "Jumla")
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        materialDao.insertAll(list)
                        materialsCount = list.size
                    }
                }
            }

            if (hasCustomers) {
                val arr = root.optJSONArray("customers")
                if (arr != null) {
                    val list = mutableListOf<CustomerEntity>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val name = o.optString("name", "")
                        if (name.isNotBlank()) {
                            list.add(
                                CustomerEntity(
                                    id = o.optInt("id", 0),
                                    name = name,
                                    phone = o.optString("phone", ""),
                                    location = o.optString("location", ""),
                                    notes = o.optString("notes", "")
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        customerDao.insertAll(list)
                        customersCount = list.size
                    }
                }
            }

            if (hasQuotes) {
                val arr = root.optJSONArray("quotes")
                if (arr != null) {
                    val list = mutableListOf<QuoteEntity>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val number = o.optString("number", "")
                        val customerName = o.optString("customerName", "")
                        if (number.isNotBlank() || customerName.isNotBlank()) {
                            list.add(
                                QuoteEntity(
                                    id = o.optInt("id", 0),
                                    number = if (number.isNotBlank()) number else "QTN-001",
                                    date = o.optString("date", ""),
                                    validUntil = o.optString("validUntil", ""),
                                    customerId = o.optInt("customerId", 0),
                                    customerName = customerName,
                                    customerPhone = o.optString("customerPhone", ""),
                                    customerLocation = o.optString("customerLocation", ""),
                                    description = o.optString("description", ""),
                                    itemsJson = o.optString("itemsJson", "[]"),
                                    materialsTotal = o.optDouble("materialsTotal", 0.0),
                                    labour = o.optDouble("labour", 0.0),
                                    grandTotal = o.optDouble("grandTotal", 0.0),
                                    status = o.optString("status", "quotation"),
                                    paid = o.optBoolean("paid", false),
                                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        quoteDao.insertAll(list)
                        quotesCount = list.size
                    }
                }
            }

            if (materialsCount == 0 && customersCount == 0 && quotesCount == 0 && !businessRestored) {
                Pair(
                    false,
                    if (isSwahili) "Faili halina taarifa zozote halali zinazoweza kurejeshwa."
                    else "The file contains no valid records that could be restored."
                )
            } else {
                val details = if (isSwahili) {
                    "Data zimerejeshwa kikamilifu! Vifaa: $materialsCount, Wateja: $customersCount, Makadirio/Ankara: $quotesCount"
                } else {
                    "Data restored successfully! Materials: $materialsCount, Customers: $customersCount, Quotes/Invoices: $quotesCount"
                }
                Pair(true, details)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(
                false,
                if (isSwahili) "Hitilafu wakati wa kuweka data: ${e.localizedMessage ?: "Data si sahihi"}"
                else "Error importing records: ${e.localizedMessage ?: "Invalid data"}"
            )
        }
    }

    suspend fun importDataFromJson(jsonStr: String): Boolean {
        return importDataFromJsonDetailed(jsonStr).first
    }

    suspend fun restoreFromPayload(payload: CloudBackupPayload): Boolean {
        return try {
            preferences.saveBusinessSettings(payload.business)
            if (payload.materials.isNotEmpty()) {
                materialDao.insertAll(payload.materials)
            }
            if (payload.customers.isNotEmpty()) {
                customerDao.insertAll(payload.customers)
            }
            if (payload.quotes.isNotEmpty()) {
                quoteDao.insertAll(payload.quotes)
            }
            ensureDemoDataSeeded()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
