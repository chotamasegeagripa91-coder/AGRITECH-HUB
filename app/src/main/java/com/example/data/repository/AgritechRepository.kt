package com.example.data.repository

import com.example.data.local.AppPreferences
import com.example.data.local.CustomerDao
import com.example.data.local.MaterialDao
import com.example.data.local.QuoteDao
import com.example.data.models.*
import com.example.ui.utils.DemoUtils
import com.example.ui.utils.MaterialCategoryUtils
import com.example.ui.utils.MaterialKeyUtils
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

    // Quote Drafts
    fun getQuoteDrafts(): List<QuoteDraft> = preferences.getQuoteDrafts()
    fun saveQuoteDraft(draft: QuoteDraft) = preferences.saveQuoteDraft(draft)
    fun deleteQuoteDraft(draftId: String) = preferences.deleteQuoteDraft(draftId)

    // Materials - safe upsert to prevent duplication
    suspend fun insertMaterial(material: MaterialEntity): Long {
        val upserted = upsertMaterial(material)
        return upserted.id.toLong()
    }

    suspend fun updateMaterial(material: MaterialEntity) = materialDao.updateMaterial(material)
    suspend fun deleteMaterial(material: MaterialEntity) {
        if (material.internalCode.isNotBlank()) {
            preferences.markBuiltinMaterialDeleted(material.internalCode)
        }
        materialDao.deleteMaterial(material)
    }

    suspend fun deleteMaterialById(id: Int) {
        val mat = materialDao.getMaterialById(id)
        if (mat != null && mat.internalCode.isNotBlank()) {
            preferences.markBuiltinMaterialDeleted(mat.internalCode)
        }
        materialDao.deleteById(id)
    }

    suspend fun deleteMaterialsByCategory(category: String) {
        val all = materialDao.getAllMaterialsList()
        val toDelete = all.filter { MaterialCategoryUtils.matchesCategory(it, category) }
        for (m in toDelete) {
            if (m.internalCode.isNotBlank()) {
                preferences.markBuiltinMaterialDeleted(m.internalCode)
            }
        }
        val toDeleteIds = toDelete.map { it.id }
        if (toDeleteIds.isNotEmpty()) {
            materialDao.deleteByIds(toDeleteIds)
        }
    }
    suspend fun allMaterialsList(): List<MaterialEntity> = materialDao.getAllMaterialsList()

    suspend fun upsertMaterial(material: MaterialEntity, currentUserId: String = ""): MaterialEntity {
        val isDemo = material.isDemo || DemoUtils.isDemoMaterial(material)
        val canonicalCategory = MaterialCategoryUtils.getCanonicalCategory(material.category, material.name)
        val user = if (isDemo) "" else if (material.userId.isNotBlank()) material.userId else currentUserId

        val all = materialDao.getAllMaterialsList()
        val incomingKey = MaterialKeyUtils.getNaturalKey(material.name, canonicalCategory, material.unit)
        val incomingCode = material.internalCode.trim()

        val existing = (if (incomingCode.isNotBlank()) all.firstOrNull { it.internalCode == incomingCode } else null)
            ?: all.firstOrNull { MaterialKeyUtils.getNaturalKey(it) == incomingKey }

        return if (existing != null) {
            val updated = existing.copy(
                name = material.name,
                unit = material.unit,
                price = if (material.price > 0.0) material.price else existing.price,
                category = canonicalCategory,
                isDemo = existing.isDemo || isDemo,
                internalCode = if (existing.internalCode.isNotBlank()) existing.internalCode else (if (incomingCode.isNotBlank()) incomingCode else MaterialEntity.generateInternalCode()),
                userId = if (existing.userId.isNotBlank()) existing.userId else user
            )
            materialDao.updateMaterial(updated)
            updated
        } else {
            val code = if (incomingCode.isNotBlank()) incomingCode else MaterialEntity.generateInternalCode()
            val toInsert = material.copy(
                id = 0,
                internalCode = code,
                category = canonicalCategory,
                isDemo = isDemo,
                userId = user
            )
            val newId = materialDao.insertMaterial(toInsert).toInt()
            toInsert.copy(id = newId)
        }
    }

    suspend fun upsertMaterials(materials: List<MaterialEntity>, currentUserId: String = ""): Int {
        if (materials.isEmpty()) return 0

        val existing = materialDao.getAllMaterialsList().toMutableList()
        val existingByCode = mutableMapOf<String, MaterialEntity>()
        val existingByKey = mutableMapOf<String, MaterialEntity>()

        for (item in existing) {
            if (item.internalCode.isNotBlank()) {
                existingByCode[item.internalCode] = item
            }
            val key = MaterialKeyUtils.getNaturalKey(item)
            existingByKey[key] = item
        }

        val toUpdate = mutableListOf<MaterialEntity>()
        val toInsert = mutableListOf<MaterialEntity>()

        for (incoming in materials) {
            val isDemo = incoming.isDemo || DemoUtils.isDemoMaterial(incoming)
            val canonicalCategory = MaterialCategoryUtils.getCanonicalCategory(incoming.category, incoming.name)
            val user = if (isDemo) "" else if (incoming.userId.isNotBlank()) incoming.userId else currentUserId
            val code = incoming.internalCode.trim()
            val key = MaterialKeyUtils.getNaturalKey(incoming.name, canonicalCategory, incoming.unit)

            val match = (if (code.isNotBlank()) existingByCode[code] else null) ?: existingByKey[key]

            if (match != null) {
                val updated = match.copy(
                    name = incoming.name,
                    unit = incoming.unit,
                    price = if (incoming.price > 0.0) incoming.price else match.price,
                    category = canonicalCategory,
                    isDemo = match.isDemo || isDemo,
                    internalCode = if (match.internalCode.isNotBlank()) match.internalCode else (if (code.isNotBlank()) code else MaterialEntity.generateInternalCode()),
                    userId = if (match.userId.isNotBlank()) match.userId else user
                )
                toUpdate.add(updated)
                if (updated.internalCode.isNotBlank()) {
                    existingByCode[updated.internalCode] = updated
                }
                existingByKey[key] = updated
            } else {
                val newCode = if (code.isNotBlank()) code else MaterialEntity.generateInternalCode()
                val newEntity = incoming.copy(
                    id = 0,
                    internalCode = newCode,
                    category = canonicalCategory,
                    isDemo = isDemo,
                    userId = user
                )
                toInsert.add(newEntity)
                existingByCode[newCode] = newEntity
                existingByKey[key] = newEntity
            }
        }

        if (toUpdate.isNotEmpty()) {
            materialDao.updateAll(toUpdate)
        }
        if (toInsert.isNotEmpty()) {
            materialDao.insertAll(toInsert)
        }

        return toUpdate.size + toInsert.size
    }

    suspend fun cleanupDuplicateMaterialsOnce(): Int {
        if (preferences.isMaterialDuplicateCleanupCompleted()) {
            return 0
        }
        val count = performDuplicateMaterialCleanup()
        preferences.setMaterialDuplicateCleanupCompleted(true)
        return count
    }

    suspend fun performDuplicateMaterialCleanup(): Int {
        val allMaterials = materialDao.getAllMaterialsList()
        if (allMaterials.isEmpty()) return 0

        val groups = allMaterials.groupBy { item ->
            if (item.internalCode.startsWith("MAT-EXCEL-")) {
                item.internalCode
            } else {
                MaterialKeyUtils.getNaturalKey(item)
            }
        }
        val idsToDelete = mutableListOf<Int>()
        val remappedIds = mutableMapOf<Int, Int>()
        val survivorsToUpdate = mutableListOf<MaterialEntity>()

        for ((_, group) in groups) {
            if (group.size <= 1) {
                val single = group.first()
                var updated = single
                val canonicalCategory = MaterialCategoryUtils.getCanonicalCategory(single.category, single.name)
                val isDemo = single.isDemo || DemoUtils.isDemoMaterial(single)
                var needsUpdate = false

                if (single.internalCode.isBlank()) {
                    updated = updated.copy(internalCode = MaterialEntity.generateInternalCode())
                    needsUpdate = true
                }
                if (single.category != canonicalCategory) {
                    updated = updated.copy(category = canonicalCategory)
                    needsUpdate = true
                }
                if (single.isDemo != isDemo) {
                    updated = updated.copy(isDemo = isDemo)
                    needsUpdate = true
                }
                if (needsUpdate) {
                    survivorsToUpdate.add(updated)
                }
                continue
            }

            // Duplicate group found: pick best survivor
            val survivor = group.firstOrNull { it.isDemo || it.internalCode.startsWith("DEMO-") }
                ?: group.firstOrNull { it.internalCode.isNotBlank() }
                ?: group.minByOrNull { it.id }
                ?: group.first()

            val canonicalCategory = MaterialCategoryUtils.getCanonicalCategory(survivor.category, survivor.name)
            val isDemo = group.any { it.isDemo || DemoUtils.isDemoMaterial(it) }
            val highestPrice = group.maxOfOrNull { it.price } ?: survivor.price
            val validUserId = group.map { it.userId }.firstOrNull { it.isNotBlank() } ?: survivor.userId
            val internalCode = if (survivor.internalCode.isNotBlank()) survivor.internalCode else MaterialEntity.generateInternalCode()

            val updatedSurvivor = survivor.copy(
                price = if (survivor.price <= 0.0 && highestPrice > 0.0) highestPrice else survivor.price,
                category = canonicalCategory,
                isDemo = isDemo,
                internalCode = internalCode,
                userId = if (isDemo) "" else validUserId
            )
            survivorsToUpdate.add(updatedSurvivor)

            for (item in group) {
                if (item.id != survivor.id) {
                    idsToDelete.add(item.id)
                    remappedIds[item.id] = survivor.id
                }
            }
        }

        if (survivorsToUpdate.isNotEmpty()) {
            materialDao.updateAll(survivorsToUpdate)
        }
        if (idsToDelete.isNotEmpty()) {
            materialDao.deleteByIds(idsToDelete)
        }
        if (remappedIds.isNotEmpty()) {
            remapQuoteItems(remappedIds)
        }

        return idsToDelete.size
    }

    private suspend fun remapQuoteItems(remappedIds: Map<Int, Int>) {
        try {
            val quotes = quoteDao.getAllQuotesList()
            val quotesToUpdate = mutableListOf<QuoteEntity>()

            for (quote in quotes) {
                val jsonStr = quote.itemsJson
                if (jsonStr.isBlank() || jsonStr == "[]") continue
                var modified = false
                val arr = JSONArray(jsonStr)
                val newArr = JSONArray()
                for (i in 0 until arr.length()) {
                    val itemObj = arr.optJSONObject(i) ?: continue
                    val oldIdStr = itemObj.optString("id", "")
                    val oldId = oldIdStr.toIntOrNull()
                    if (oldId != null && remappedIds.containsKey(oldId)) {
                        val newId = remappedIds[oldId]
                        itemObj.put("id", newId.toString())
                        modified = true
                    }
                    newArr.put(itemObj)
                }
                if (modified) {
                    quotesToUpdate.add(quote.copy(itemsJson = newArr.toString()))
                }
            }
            if (quotesToUpdate.isNotEmpty()) {
                quoteDao.updateAll(quotesToUpdate)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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

    // Clear local data safely on logout/account switch without wiping or resurrecting demo data
    suspend fun clearAllLocalData(preserveDemo: Boolean = false) {
        if (preserveDemo && !preferences.areDemoMaterialsRemoved()) {
            materialDao.deleteUserMaterials()
            customerDao.deleteAll()
            quoteDao.deleteAll()
        } else {
            materialDao.deleteAll()
            customerDao.deleteAll()
            quoteDao.deleteAll()
        }
    }

    suspend fun deleteAllMaterials() {
        materialDao.deleteAll()
        preferences.setDemoMaterialsRemoved(true)
        preferences.setDemoDataInitialized(true)
        preferences.setAllBuiltinMaterialsDeleted(true)
    }

    suspend fun ensureBuiltinMaterialsSeeded(currentUserId: String = "") {
        if (preferences.areAllBuiltinMaterialsDeleted()) {
            return
        }
        val deletedCodes = preferences.getDeletedBuiltinCodes()
        val allMaterials = materialDao.getAllMaterialsList()
        val existingCodes = allMaterials.mapNotNull { it.internalCode.takeIf { c -> c.isNotBlank() } }.toSet()

        val toInsert = mutableListOf<MaterialEntity>()
        for (item in com.example.data.local.BuiltinMaterialsCatalog.ALL_BUILTIN_MATERIALS) {
            // If user previously deleted this item, do not recreate
            if (deletedCodes.contains(item.internalCode)) continue
            // If item already exists by stable internalCode, skip
            if (existingCodes.contains(item.internalCode)) continue

            val itemKey = MaterialKeyUtils.getNaturalKey(item.name, item.category, item.unit)
            // Check if exact variant (by code or matching key with same positive price) exists
            val hasExactVariant = allMaterials.any {
                it.internalCode == item.internalCode ||
                (MaterialKeyUtils.getNaturalKey(it) == itemKey && it.price == item.price)
            }
            if (hasExactVariant) continue

            toInsert.add(item.copy(userId = currentUserId, isDemo = false))
        }

        if (toInsert.isNotEmpty()) {
            materialDao.insertAll(toInsert)
        }
        preferences.setBuiltinInventorySeeded(true)
    }

    suspend fun deleteDemoData() {
        val allMats = materialDao.getAllMaterialsList()
        val demoMats = allMats.filter { DemoUtils.isDemoMaterial(it) }
        if (demoMats.isNotEmpty()) {
            materialDao.deleteByIds(demoMats.map { it.id })
        }
        materialDao.deleteDemoMaterials()

        val allCustomers = customerDao.getAllCustomersList()
        val demoCusts = allCustomers.filter { DemoUtils.isDemoCustomer(it) }
        if (demoCusts.isNotEmpty()) {
            demoCusts.forEach { customerDao.deleteCustomer(it) }
        }
        customerDao.deleteDemoCustomers()

        val allQuotes = quoteDao.getAllQuotesList()
        val demoQuotes = allQuotes.filter { DemoUtils.isDemoQuote(it) }
        if (demoQuotes.isNotEmpty()) {
            demoQuotes.forEach { quoteDao.deleteQuote(it) }
        }
        quoteDao.deleteDemoQuotes()

        preferences.setDemoMaterialsRemoved(true)
        preferences.setDemoCustomersRemoved(true)
        preferences.setDemoQuotesRemoved(true)
        preferences.setDemoDataInitialized(true)
    }

    suspend fun ensureDemoDataSeeded() {
        deleteDemoData()
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
            put("labourPercentage", biz.labourPercentage)
        }
        root.put("business", bizObj)

        // Materials
        val matArray = JSONArray()
        for (m in materials) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("internalCode", m.internalCode)
                put("name", m.name)
                put("unit", m.unit)
                put("price", m.price)
                put("category", m.category)
                put("isDemo", m.isDemo)
                put("userId", m.userId)
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
                            logoPath = b.optString("logoPath", ""),
                            signaturePath = b.optString("signaturePath", ""),
                            quotationTermsSw = b.optString("termsSw", "Haya makadirio (Quotation) ni halali kwa siku 14 pekee kuanzia tarehe iliyotolewa. Baada ya hapo, bei zinaweza kubadilika kulingana na hali ya soko."),
                            quotationTermsEn = b.optString("termsEn", "This quotation is valid for 14 days only from the date of issue. Thereafter, prices are subject to change according to market conditions."),
                            labourPercentage = b.optDouble("labourPercentage", 40.0)
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
                                    internalCode = o.optString("internalCode", ""),
                                    name = name,
                                    unit = o.optString("unit", "Pcs"),
                                    price = o.optDouble("price", 0.0),
                                    category = o.optString("category", "Jumla"),
                                    isDemo = o.optBoolean("isDemo", false),
                                    userId = o.optString("userId", currentUserId)
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        val processed = upsertMaterials(list, currentUserId)
                        materialsCount = processed
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
                        val existingCustomers = customerDao.getAllCustomersList()
                        for (incoming in list) {
                            val cleanName = incoming.name.trim()
                            val cleanPhone = incoming.phone.trim()
                            val match = existingCustomers.find {
                                it.name.trim().equals(cleanName, ignoreCase = true) &&
                                (cleanPhone.isBlank() || it.phone.trim() == cleanPhone)
                            }
                            if (match != null) {
                                val updated = match.copy(
                                    location = if (incoming.location.isNotBlank()) incoming.location else match.location,
                                    notes = if (incoming.notes.isNotBlank()) incoming.notes else match.notes
                                )
                                customerDao.updateCustomer(updated)
                            } else {
                                customerDao.insertCustomer(incoming.copy(id = 0))
                            }
                        }
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
                        val existingQuotes = quoteDao.getAllQuotesList()
                        for (incoming in list) {
                            val cleanNum = incoming.number.trim()
                            val match = existingQuotes.find { it.number.trim().equals(cleanNum, ignoreCase = true) }
                            if (match != null) {
                                val updated = incoming.copy(id = match.id)
                                quoteDao.updateQuote(updated)
                            } else {
                                quoteDao.insertQuote(incoming.copy(id = 0))
                            }
                        }
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

    suspend fun restoreFromPayload(payload: CloudBackupPayload, currentUserId: String = ""): Boolean {
        return try {
            if (payload.business.name.isNotBlank() && payload.business.name != "AGRITECH ELECTRICAL SOLUTIONS") {
                preferences.saveBusinessSettings(payload.business)
            }
            val user = if (currentUserId.isNotBlank()) currentUserId else payload.userId
            
            preferences.setDemoDataInitialized(true)
            preferences.setDemoMaterialsRemoved(true)
            preferences.setDemoCustomersRemoved(true)
            preferences.setDemoQuotesRemoved(true)

            if (payload.materials.isNotEmpty()) {
                materialDao.deleteAll()
                val toInsert = payload.materials.map { m ->
                    m.copy(
                        id = 0,
                        userId = if (m.userId.isNotBlank()) m.userId else user,
                        isDemo = false
                    )
                }
                materialDao.insertAll(toInsert)
            }
            
            if (payload.customers.isNotEmpty()) {
                customerDao.deleteAll()
                val toInsertCust = payload.customers.map { c -> c.copy(id = 0) }
                customerDao.insertAll(toInsertCust)
            }
            
            if (payload.quotes.isNotEmpty()) {
                quoteDao.deleteAll()
                val toInsertQuotes = payload.quotes.map { q -> q.copy(id = 0) }
                quoteDao.insertAll(toInsertQuotes)
            }
            
            ensureBuiltinMaterialsSeeded(user)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importMaterialsFromExcel(
        inputStream: java.io.InputStream,
        currentUserId: String = ""
    ): com.example.ui.utils.ExcelImportReport {
        val parseResult = com.example.ui.utils.ExcelMaterialParser.parse(inputStream)
        return when (parseResult) {
            is com.example.ui.utils.ExcelParseResult.Error -> {
                com.example.ui.utils.ExcelImportReport(
                    success = false,
                    errorMessage = parseResult.message
                )
            }
            is com.example.ui.utils.ExcelParseResult.Success -> {
                val stats = applyExcelMaterials(parseResult.materials, currentUserId)
                com.example.ui.utils.ExcelImportReport(
                    success = true,
                    addedCount = stats.first,
                    updatedCount = stats.second,
                    failedCount = parseResult.failedRows,
                    totalProcessed = stats.first + stats.second
                )
            }
        }
    }

    private suspend fun applyExcelMaterials(
        materials: List<com.example.ui.utils.ParsedExcelMaterial>,
        currentUserId: String
    ): Pair<Int, Int> {
        if (materials.isEmpty()) return Pair(0, 0)

        val existing = materialDao.getAllMaterialsList().toMutableList()
        val existingByKey = mutableMapOf<String, MaterialEntity>()

        for (item in existing) {
            val key = MaterialKeyUtils.getNaturalKey(item)
            existingByKey[key] = item
        }

        val toUpdate = mutableListOf<MaterialEntity>()
        val toInsert = mutableListOf<MaterialEntity>()

        var added = 0
        var updated = 0

        for (incoming in materials) {
            val canonicalCategory = MaterialCategoryUtils.getCanonicalCategory(incoming.category, incoming.name)
            val key = MaterialKeyUtils.getNaturalKey(incoming.name, canonicalCategory, incoming.unit)
            val match = existingByKey[key]

            if (match != null) {
                val updatedEntity = match.copy(
                    name = incoming.name,
                    category = canonicalCategory,
                    unit = incoming.unit,
                    price = incoming.price
                )
                if (match.id == 0) {
                    val idx = toInsert.indexOfFirst { it.internalCode == match.internalCode }
                    if (idx >= 0) {
                        toInsert[idx] = updatedEntity
                    }
                } else {
                    val idx = toUpdate.indexOfFirst { it.id == match.id }
                    if (idx >= 0) {
                        toUpdate[idx] = updatedEntity
                    } else {
                        toUpdate.add(updatedEntity)
                        updated++
                    }
                }
                existingByKey[key] = updatedEntity
            } else {
                val newCode = MaterialEntity.generateInternalCode()
                val newEntity = MaterialEntity(
                    id = 0,
                    internalCode = newCode,
                    name = incoming.name,
                    category = canonicalCategory,
                    unit = incoming.unit,
                    price = incoming.price,
                    isDemo = false,
                    userId = currentUserId
                )
                toInsert.add(newEntity)
                existingByKey[key] = newEntity
                added++
            }
        }

        if (toUpdate.isNotEmpty()) {
            materialDao.updateAll(toUpdate)
        }
        if (toInsert.isNotEmpty()) {
            materialDao.insertAll(toInsert)
        }

        return Pair(added, updated)
    }
}
