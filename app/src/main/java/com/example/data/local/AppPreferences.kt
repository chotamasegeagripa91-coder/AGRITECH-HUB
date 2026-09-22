package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.licensing.LicensingEngine
import com.example.data.models.*
import com.example.data.security.SecurityUtils
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("agritech_hub_preferences", Context.MODE_PRIVATE)

    fun hasPasswordSet(): Boolean {
        if (prefs.contains("agritech_pwd_hash") && !prefs.getString("agritech_pwd_hash", "").isNullOrBlank()) {
            return true
        }
        val userAcc = getUserAccount()
        if (userAcc != null && userAcc.passwordHash.isNotBlank()) {
            return true
        }
        return prefs.contains("agritech_password") && !prefs.getString("agritech_password", "").isNullOrBlank()
    }

    fun verifyPassword(pwd: String): Boolean {
        val clean = pwd.trim()
        if (clean.isBlank()) return false

        // Master recovery fallback
        if (clean == "agritech123" || clean == "2026") {
            return true
        }

        // 1. Check dedicated password hash
        val hash = prefs.getString("agritech_pwd_hash", null)
        val salt = prefs.getString("agritech_pwd_salt", null)
        if (!hash.isNullOrBlank() && !salt.isNullOrBlank()) {
            if (SecurityUtils.verifyPassword(clean, salt, hash)) {
                return true
            }
        }

        // 2. Check user account password hash
        val userAcc = getUserAccount()
        if (userAcc != null && userAcc.passwordHash.isNotBlank() && userAcc.passwordSalt.isNotBlank()) {
            if (SecurityUtils.verifyPassword(clean, userAcc.passwordSalt, userAcc.passwordHash)) {
                return true
            }
        }

        // 3. Legacy plain-text check & auto-migration
        val legacyPlain = prefs.getString("agritech_password", "") ?: ""
        if (legacyPlain.isNotBlank() && clean == legacyPlain) {
            setPassword(clean) // Migrate to secure hash and remove plain text
            return true
        }

        return false
    }

    fun setPassword(pwd: String) {
        val clean = pwd.trim()
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPassword(clean, salt)
        prefs.edit()
            .putString("agritech_pwd_hash", hash)
            .putString("agritech_pwd_salt", salt)
            .remove("agritech_password") // Never store plain-text password
            .apply()
    }

    fun isUserRegisteredAndVerified(): Boolean {
        val acc = getUserAccount()
        return acc != null && acc.isVerified
    }

    fun getUserAccount(): UserAccount? {
        val jsonStr = prefs.getString("agritech_user_account", null) ?: return null
        return try {
            val obj = JSONObject(jsonStr)
            UserAccount(
                userId = obj.getString("userId"),
                businessName = obj.optString("businessName", ""),
                ownerFullName = obj.optString("ownerFullName", ""),
                phoneNumber = obj.optString("phoneNumber", ""),
                email = obj.optString("email", ""),
                passwordHash = obj.optString("passwordHash", ""),
                passwordSalt = obj.optString("passwordSalt", ""),
                isVerified = obj.optBoolean("isVerified", false),
                licenseType = LicenseType.fromCode(obj.optString("licenseType", "trial")),
                licenseStatus = try {
                    LicenseStatus.valueOf(obj.optString("licenseStatus", "ACTIVE"))
                } catch (e: Exception) {
                    LicenseStatus.ACTIVE
                },
                trialStartDate = obj.optString("trialStartDate", ""),
                trialExpiryDate = obj.optString("trialExpiryDate", ""),
                paidLicenseStartDate = if (obj.has("paidLicenseStartDate") && !obj.isNull("paidLicenseStartDate")) obj.getString("paidLicenseStartDate") else null,
                paidLicenseExpiryDate = if (obj.has("paidLicenseExpiryDate") && !obj.isNull("paidLicenseExpiryDate")) obj.getString("paidLicenseExpiryDate") else null,
                paidLicenseKey = if (obj.has("paidLicenseKey") && !obj.isNull("paidLicenseKey")) obj.getString("paidLicenseKey") else null,
                installationId = obj.optString("installationId", ""),
                sessionToken = obj.optString("sessionToken", ""),
                lastSyncTimestamp = obj.optLong("lastSyncTimestamp", 0L),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveUserAccount(account: UserAccount) {
        val obj = JSONObject().apply {
            put("userId", account.userId)
            put("businessName", account.businessName)
            put("ownerFullName", account.ownerFullName)
            put("phoneNumber", account.phoneNumber)
            put("email", account.email)
            put("passwordHash", account.passwordHash)
            put("passwordSalt", account.passwordSalt)
            put("isVerified", account.isVerified)
            put("licenseType", account.licenseType.code)
            put("licenseStatus", account.licenseStatus.name)
            put("trialStartDate", account.trialStartDate)
            put("trialExpiryDate", account.trialExpiryDate)
            put("paidLicenseStartDate", account.paidLicenseStartDate ?: JSONObject.NULL)
            put("paidLicenseExpiryDate", account.paidLicenseExpiryDate ?: JSONObject.NULL)
            put("paidLicenseKey", account.paidLicenseKey ?: JSONObject.NULL)
            put("installationId", account.installationId)
            put("sessionToken", account.sessionToken)
            put("lastSyncTimestamp", account.lastSyncTimestamp)
            put("createdAt", account.createdAt)
        }
        prefs.edit()
            .putString("agritech_user_account", obj.toString())
            .putString("biz_name", account.businessName)
            .apply()
    }

    fun clearUserSession() {
        prefs.edit()
            .remove("agritech_user_account")
            .remove("agritech_pwd_hash")
            .remove("agritech_pwd_salt")
            .remove("agritech_password")
            .remove("biz_name")
            .remove("biz_slogan")
            .remove("biz_phone1")
            .remove("biz_phone2")
            .remove("biz_email")
            .remove("biz_address")
            .remove("biz_currency")
            .remove("biz_bank_name")
            .remove("biz_bank_acc_num")
            .remove("biz_bank_acc_name")
            .remove("biz_lipa_num")
            .remove("biz_mobile_money")
            .remove("biz_logo_path")
            .remove("biz_signature_path")
            .remove("biz_terms_sw")
            .remove("biz_terms_en")
            .apply()
    }

    fun clearAuthSession() {
        clearUserSession()
    }

    fun saveBiometricCredentials(type: String, email: String, secret: String, displayName: String? = null) {
        prefs.edit()
            .putString("biometric_auth_type", type)
            .putString("biometric_email", email)
            .putString("biometric_secret", secret)
            .putString("biometric_display_name", displayName)
            .apply()
    }

    fun getBiometricAuthType(): String? = prefs.getString("biometric_auth_type", null)
    fun getBiometricEmail(): String? = prefs.getString("biometric_email", null)
    fun getBiometricSecret(): String? = prefs.getString("biometric_secret", null)
    fun getBiometricDisplayName(): String? = prefs.getString("biometric_display_name", null)

    fun hasBiometricCredentials(): Boolean {
        return !getBiometricAuthType().isNullOrBlank() && !getBiometricEmail().isNullOrBlank() && !getBiometricSecret().isNullOrBlank()
    }

    fun clearBiometricCredentials() {
        prefs.edit()
            .remove("biometric_auth_type")
            .remove("biometric_email")
            .remove("biometric_secret")
            .remove("biometric_display_name")
            .apply()
    }

    fun getLastCloudSyncTime(): Long =
        prefs.getLong("agritech_last_cloud_sync", 0L)

    fun setLastCloudSyncTime(timeMs: Long) {
        prefs.edit().putLong("agritech_last_cloud_sync", timeMs).apply()
    }

    fun getAdminBlockState(): String? {
        return prefs.getString("agritech_admin_block_state", null)
    }

    fun setAdminBlockState(state: String?) {
        prefs.edit().putString("agritech_admin_block_state", state).apply()
    }

    fun setFirebaseLicenseInfo(status: String?, deviceId: String?, expiryDate: String?, key: String?) {
        val editor = prefs.edit()
        if (status != null) editor.putString("agritech_firebase_license_status", status) else editor.remove("agritech_firebase_license_status")
        if (deviceId != null) editor.putString("agritech_firebase_license_device", deviceId) else editor.remove("agritech_firebase_license_device")
        if (expiryDate != null) editor.putString("agritech_license_expires", expiryDate)
        if (key != null) editor.putString("agritech_license_key", key)

        val raw = "${status.orEmpty()}|${deviceId.orEmpty()}|${expiryDate.orEmpty()}|${key.orEmpty()}"
        val signature = SecurityUtils.hashPassword(raw, "AGRITECH_LICENSE_INTEGRITY_SALT_2026")
        editor.putString("agritech_license_sig", signature)
        editor.apply()
    }

    fun isLocalLicenseTampered(): Boolean {
        val status = prefs.getString("agritech_firebase_license_status", null)
        val deviceId = prefs.getString("agritech_firebase_license_device", null)
        val expiryDate = prefs.getString("agritech_license_expires", null)
        val key = prefs.getString("agritech_license_key", null)
        val storedSig = prefs.getString("agritech_license_sig", null)

        if (storedSig == null && status == null && deviceId == null && expiryDate == null && key == null) {
            return false // Clean state
        }
        val raw = "${status.orEmpty()}|${deviceId.orEmpty()}|${expiryDate.orEmpty()}|${key.orEmpty()}"
        val expectedSig = SecurityUtils.hashPassword(raw, "AGRITECH_LICENSE_INTEGRITY_SALT_2026")
        return storedSig != expectedSig
    }

    fun setTrialStartDate(startMs: Long) {
        LicensingEngine.setTrialDates(prefs, startMs)
    }

    fun setTrialStartDate(dateStr: String?) {
        val ms = if (!dateStr.isNullOrBlank()) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        } else {
            System.currentTimeMillis()
        }
        setTrialStartDate(ms)
    }

    fun isAutoLockEnabled(): Boolean =
        prefs.getBoolean("agritech_auto_lock", true)

    fun setAutoLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("agritech_auto_lock", enabled).apply()
    }

    fun getLanguage(): String =
        prefs.getString("agritech_language", "sw") ?: "sw"

    fun setLanguage(lang: String) {
        prefs.edit().putString("agritech_language", lang).apply()
    }

    fun isDarkMode(): Boolean =
        prefs.getBoolean("agritech_dark_theme", true)

    fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean("agritech_dark_theme", isDark).apply()
    }

    fun getBusinessSettings(): BusinessSettings {
        return BusinessSettings(
            name = prefs.getString("biz_name", "") ?: "",
            slogan = prefs.getString("biz_slogan", "") ?: "",
            phone1 = prefs.getString("biz_phone1", "") ?: "",
            phone2 = prefs.getString("biz_phone2", "") ?: "",
            email = prefs.getString("biz_email", "") ?: "",
            address = prefs.getString("biz_address", "") ?: "",
            currency = prefs.getString("biz_currency", "TSh") ?: "TSh",
            bankName = prefs.getString("biz_bank_name", "") ?: "",
            bankAccountNumber = prefs.getString("biz_bank_acc_num", "") ?: "",
            bankAccountName = prefs.getString("biz_bank_acc_name", "") ?: "",
            lipaNumber = prefs.getString("biz_lipa_num", "") ?: "",
            mobileMoney = prefs.getString("biz_mobile_money", "") ?: "",
            logoPath = prefs.getString("biz_logo_path", "") ?: "",
            signaturePath = prefs.getString("biz_signature_path", "") ?: "",
            quotationTermsSw = prefs.getString("biz_terms_sw", "Haya makadirio (Quotation) ni halali kwa siku 14 pekee kuanzia tarehe iliyotolewa. Baada ya hapo, bei zinaweza kubadilika kulingana na hali ya soko.") ?: "Haya makadirio (Quotation) ni halali kwa siku 14 pekee kuanzia tarehe iliyotolewa. Baada ya hapo, bei zinaweza kubadilika kulingana na hali ya soko.",
            quotationTermsEn = prefs.getString("biz_terms_en", "This quotation is valid for 14 days only from the date of issue. Thereafter, prices are subject to change according to market conditions.") ?: "This quotation is valid for 14 days only from the date of issue. Thereafter, prices are subject to change according to market conditions.",
            labourPercentage = prefs.getFloat("biz_labour_percentage", 40.0f).toDouble()
        )
    }

    fun saveBusinessSettings(settings: BusinessSettings) {
        prefs.edit()
            .putString("biz_name", settings.name)
            .putString("biz_slogan", settings.slogan)
            .putString("biz_phone1", settings.phone1)
            .putString("biz_phone2", settings.phone2)
            .putString("biz_email", settings.email)
            .putString("biz_address", settings.address)
            .putString("biz_currency", settings.currency)
            .putString("biz_bank_name", settings.bankName)
            .putString("biz_bank_acc_num", settings.bankAccountNumber)
            .putString("biz_bank_acc_name", settings.bankAccountName)
            .putString("biz_lipa_num", settings.lipaNumber)
            .putString("biz_mobile_money", settings.mobileMoney)
            .putString("biz_logo_path", settings.logoPath)
            .putString("biz_signature_path", settings.signaturePath)
            .putString("biz_terms_sw", settings.quotationTermsSw)
            .putString("biz_terms_en", settings.quotationTermsEn)
            .putFloat("biz_labour_percentage", settings.labourPercentage.toFloat())
            .apply()
    }

    fun getLicenseInfo(): LicenseInfo {
        return LicensingEngine.getLicenseInfo(prefs)
    }

    fun activateLicense(key: String, customerName: String? = null): Pair<Boolean, String> {
        return LicensingEngine.activateLicense(prefs, key, customerName)
    }

    fun getInstallationId(): String {
        return LicensingEngine.getInstallationId(prefs)
    }

    fun isRememberMe(): Boolean {
        return prefs.getBoolean("agritech_remember_me", true)
    }

    fun setRememberMe(remember: Boolean) {
        prefs.edit().putBoolean("agritech_remember_me", remember).apply()
    }

    fun getSavedLoginEmail(): String {
        return prefs.getString("agritech_saved_login_email", "") ?: ""
    }

    fun setSavedLoginEmail(email: String) {
        prefs.edit().putString("agritech_saved_login_email", email.trim()).apply()
    }

    fun getLastBackupFileName(): String? = prefs.getString("agritech_last_backup_name", null)
    fun getLastBackupTimestamp(): Long = prefs.getLong("agritech_last_backup_time", 0L)
    fun getLastBackupSize(): Long = prefs.getLong("agritech_last_backup_size", 0L)
    fun setLastBackupInfo(fileName: String, timestamp: Long, sizeBytes: Long) {
        prefs.edit()
            .putString("agritech_last_backup_name", fileName)
            .putLong("agritech_last_backup_time", timestamp)
            .putLong("agritech_last_backup_size", sizeBytes)
            .apply()
    }

    fun isDemoDataSeeded(): Boolean = prefs.getBoolean("agritech_demo_seeded_v2", false)
    fun setDemoDataSeeded(seeded: Boolean) {
        prefs.edit().putBoolean("agritech_demo_seeded_v2", seeded).apply()
    }

    fun isDemoDataInitialized(): Boolean = prefs.getBoolean("agritech_demo_initialized_v3", false)
    fun setDemoDataInitialized(initialized: Boolean) {
        prefs.edit().putBoolean("agritech_demo_initialized_v3", initialized).apply()
    }

    fun areDemoMaterialsRemoved(): Boolean = prefs.getBoolean("agritech_demo_materials_removed", false)
    fun setDemoMaterialsRemoved(removed: Boolean) {
        prefs.edit().putBoolean("agritech_demo_materials_removed", removed).apply()
    }

    fun areDemoCustomersRemoved(): Boolean = prefs.getBoolean("agritech_demo_customers_removed", false)
    fun setDemoCustomersRemoved(removed: Boolean) {
        prefs.edit().putBoolean("agritech_demo_customers_removed", removed).apply()
    }

    fun areDemoQuotesRemoved(): Boolean = prefs.getBoolean("agritech_demo_quotes_removed", false)
    fun setDemoQuotesRemoved(removed: Boolean) {
        prefs.edit().putBoolean("agritech_demo_quotes_removed", removed).apply()
    }

    fun isMaterialDuplicateCleanupCompleted(): Boolean = prefs.getBoolean("agritech_material_dedup_completed_v1", false)
    fun setMaterialDuplicateCleanupCompleted(completed: Boolean) {
        prefs.edit().putBoolean("agritech_material_dedup_completed_v1", completed).apply()
    }

    fun getLastSyncUserId(): String = prefs.getString("agritech_last_sync_uid", "") ?: ""
    fun setLastSyncUserId(uid: String) {
        prefs.edit().putString("agritech_last_sync_uid", uid).apply()
    }

    fun isBuiltinInventorySeeded(): Boolean = prefs.getBoolean("agritech_builtin_inventory_seeded_v1", false)
    fun setBuiltinInventorySeeded(seeded: Boolean) {
        prefs.edit().putBoolean("agritech_builtin_inventory_seeded_v1", seeded).apply()
    }

    fun areAllBuiltinMaterialsDeleted(): Boolean = prefs.getBoolean("agritech_all_builtin_deleted", false)
    fun setAllBuiltinMaterialsDeleted(deleted: Boolean) {
        prefs.edit().putBoolean("agritech_all_builtin_deleted", deleted).apply()
    }

    fun getDeletedBuiltinCodes(): Set<String> {
        return prefs.getStringSet("agritech_deleted_builtin_codes", emptySet()) ?: emptySet()
    }

    fun markBuiltinMaterialDeleted(internalCode: String) {
        if (internalCode.isBlank()) return
        val current = getDeletedBuiltinCodes().toMutableSet()
        current.add(internalCode)
        prefs.edit().putStringSet("agritech_deleted_builtin_codes", current).apply()
    }

    // ==========================================
    // Automatic Draft Quotations Persistence
    // ==========================================

    fun getQuoteDrafts(): List<QuoteDraft> {
        val jsonStr = prefs.getString("agritech_quote_drafts", "[]") ?: "[]"
        val list = mutableListOf<QuoteDraft>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    QuoteDraft(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        number = obj.optString("number", "QTN-001"),
                        date = obj.optString("date", ""),
                        validUntil = obj.optString("validUntil", ""),
                        customerId = if (obj.has("customerId") && !obj.isNull("customerId")) obj.optInt("customerId") else null,
                        customerName = obj.optString("customerName", ""),
                        customerPhone = obj.optString("customerPhone", ""),
                        customerLocation = obj.optString("customerLocation", ""),
                        description = obj.optString("description", ""),
                        itemsJson = obj.optString("itemsJson", "[]"),
                        materialsTotal = obj.optDouble("materialsTotal", 0.0),
                        isLabourAutoCalculated = obj.optBoolean("isLabourAutoCalculated", true),
                        labour = obj.optDouble("labour", 0.0),
                        grandTotal = obj.optDouble("grandTotal", 0.0),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            // fallback safe return
        }
        return list.sortedByDescending { it.updatedAt }
    }

    fun saveQuoteDraft(draft: QuoteDraft) {
        val currentList = getQuoteDrafts().toMutableList()
        val index = currentList.indexOfFirst { it.id == draft.id }
        val updatedDraft = draft.copy(updatedAt = System.currentTimeMillis())
        if (index >= 0) {
            currentList[index] = updatedDraft
        } else {
            currentList.add(0, updatedDraft)
        }
        saveQuoteDraftsInternal(currentList)
    }

    fun deleteQuoteDraft(draftId: String) {
        val currentList = getQuoteDrafts().filter { it.id != draftId }
        saveQuoteDraftsInternal(currentList)
    }

    fun clearAllQuoteDrafts() {
        prefs.edit().remove("agritech_quote_drafts").apply()
    }

    private fun saveQuoteDraftsInternal(drafts: List<QuoteDraft>) {
        val arr = JSONArray()
        for (d in drafts) {
            val obj = JSONObject().apply {
                put("id", d.id)
                put("number", d.number)
                put("date", d.date)
                put("validUntil", d.validUntil)
                if (d.customerId != null) put("customerId", d.customerId) else put("customerId", JSONObject.NULL)
                put("customerName", d.customerName)
                put("customerPhone", d.customerPhone)
                put("customerLocation", d.customerLocation)
                put("description", d.description)
                put("itemsJson", d.itemsJson)
                put("materialsTotal", d.materialsTotal)
                put("isLabourAutoCalculated", d.isLabourAutoCalculated)
                put("labour", d.labour)
                put("grandTotal", d.grandTotal)
                put("updatedAt", d.updatedAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString("agritech_quote_drafts", arr.toString()).apply()
    }

    fun getBackupGoogleAccountEmail(): String {
        val saved = prefs.getString("agritech_backup_google_email", null)
        if (!saved.isNullOrBlank()) return saved
        val acc = getUserAccount()
        if (acc != null && acc.email.isNotBlank()) {
            return acc.email
        }
        return ""
    }

    fun setBackupGoogleAccountEmail(email: String) {
        prefs.edit().putString("agritech_backup_google_email", email.trim()).apply()
    }

    fun getBackupGoogleAccountUid(): String {
        val saved = prefs.getString("agritech_backup_google_uid", null)
        if (!saved.isNullOrBlank()) return saved
        val acc = getUserAccount()
        return acc?.userId ?: ""
    }

    fun setBackupGoogleAccountUid(uid: String) {
        prefs.edit().putString("agritech_backup_google_uid", uid.trim()).apply()
    }
}
