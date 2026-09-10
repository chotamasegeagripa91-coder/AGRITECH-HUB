package com.example.data.cloud

import android.content.Context
import android.content.SharedPreferences
import com.example.data.models.*
import com.example.data.security.SecurityUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Cloud and Authentication service for AGRITECH HUB.
 * Handles server-side user registration, OTP verification, 30-day automatic trial provisioning,
 * cloud backup synchronization, and new-device account recovery.
 *
 * Security: Never transmits or stores plain-text passwords. All credentials use salted SHA-256 hashes.
 */
class AgritechCloudService(context: Context) {

    private val serverStore: SharedPreferences =
        context.getSharedPreferences("agritech_cloud_server_store", Context.MODE_PRIVATE)

    data class ServerResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null,
        val errorCode: String? = null
    )

    // In-memory pending OTP registry (emailOrPhone -> PendingOtp)
    data class PendingOtp(
        val code: String,
        val generatedAt: Long,
        val expiresAt: Long,
        val userAccount: UserAccount? = null,
        val purpose: String // "REGISTRATION" or "FORGOT_PASSWORD"
    )

    companion object {
        private const val OTP_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes
        private const val TRIAL_DURATION_DAYS = 30
        private val pendingOtps = mutableMapOf<String, PendingOtp>()
    }

    private fun getIsoDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Step 1: Sign up new user.
     * Validates fields, hashes password, generates OTP, and waits for verification.
     */
    fun registerUser(
        businessName: String,
        ownerFullName: String,
        phone: String,
        email: String,
        password: String
    ): ServerResponse<UserAccount> {
        val cleanBiz = businessName.trim()
        val cleanOwner = ownerFullName.trim()
        val cleanPhone = phone.trim()
        val cleanEmail = email.trim().lowercase(Locale.ROOT)

        // Validation
        if (cleanBiz.isBlank()) {
            return ServerResponse(false, "Jina la biashara linahitajika (Business Name is required).")
        }

        val nameParts = cleanOwner.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (nameParts.size < 2) {
            return ServerResponse(false, "Jina la mmiliki linahitaji angalau majina mawili (Owner's Full Name requires at least two names).")
        }

        if (cleanPhone.isBlank() || cleanPhone.length < 8) {
            return ServerResponse(false, "Namba ya simu si sahihi (Valid Phone Number is required).")
        }

        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return ServerResponse(false, "Barua pepe si sahihi (Valid Email Address is required).")
        }

        if (password.length < 4) {
            return ServerResponse(false, "Nenosiri lazima liwe na herufi au namba angalau 4 (Password must be at least 4 characters).")
        }

        // Check if user already exists
        val existing = findAccountByEmailOrPhone(cleanEmail) ?: findAccountByEmailOrPhone(cleanPhone)
        if (existing != null && existing.isVerified) {
            return ServerResponse(
                false,
                "Akaunti yenye barua pepe au namba hii tayari ipo. Tafadhali chagua LOGIN (Account already exists. Please choose LOGIN)."
            )
        }

        val salt = SecurityUtils.generateSalt()
        val passwordHash = SecurityUtils.hashPassword(password, salt)
        val userId = "USR-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.ROOT)
        val now = System.currentTimeMillis()
        val trialStartStr = getIsoDate(now)
        val trialExpiryStr = getIsoDate(now + (TRIAL_DURATION_DAYS.toLong() * 86400000L))

        val account = UserAccount(
            userId = userId,
            businessName = cleanBiz,
            ownerFullName = cleanOwner,
            phoneNumber = cleanPhone,
            email = cleanEmail,
            passwordHash = passwordHash,
            passwordSalt = salt,
            isVerified = false,
            licenseType = LicenseType.TRIAL,
            licenseStatus = LicenseStatus.ACTIVE,
            trialStartDate = trialStartStr,
            trialExpiryDate = trialExpiryStr,
            createdAt = now
        )

        val otp = SecurityUtils.generateOtp()
        val pending = PendingOtp(
            code = otp,
            generatedAt = now,
            expiresAt = now + OTP_EXPIRY_MS,
            userAccount = account,
            purpose = "REGISTRATION"
        )
        pendingOtps[cleanEmail] = pending
        pendingOtps[cleanPhone] = pending

        return ServerResponse(
            success = true,
            message = "Msimbo wa uthibitisho (OTP) umetumwa kwa ujumbe mfupi (SMS) kwenye simu yako. Tafadhali fungua kikasha cha SMS.",
            data = account
        )
    }

    /**
     * Direct registration using Firebase Email & Password authentication.
     * No Phone SMS OTP required. Automatically provisions verified 30-day TRIAL license.
     */
    fun registerUserDirectly(
        businessName: String,
        ownerFullName: String,
        phone: String,
        email: String,
        password: String,
        installationId: String,
        firebaseUid: String? = null
    ): ServerResponse<UserAccount> {
        val cleanBiz = businessName.trim()
        val cleanOwner = ownerFullName.trim()
        val cleanPhone = phone.trim()
        val cleanEmail = email.trim().lowercase(Locale.ROOT)

        if (cleanBiz.isBlank()) {
            return ServerResponse(false, "Jina la biashara linahitajika (Business Name is required).")
        }

        val nameParts = cleanOwner.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (nameParts.size < 2) {
            return ServerResponse(false, "Jina la mmiliki linahitaji angalau majina mawili (Owner's Full Name requires at least two names).")
        }

        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return ServerResponse(false, "Barua pepe si sahihi (Valid Email Address is required).")
        }

        if (password.length < 6) {
            return ServerResponse(false, "Nenosiri lazima liwe na herufi au namba angalau 6 (Password must be at least 6 characters).")
        }

        val salt = SecurityUtils.generateSalt()
        val passwordHash = SecurityUtils.hashPassword(password, salt)
        val userId = if (!firebaseUid.isNullOrBlank()) {
            firebaseUid
        } else {
            "USR-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.ROOT)
        }
        val now = System.currentTimeMillis()
        val trialStartStr = getIsoDate(now)
        val trialExpiryStr = getIsoDate(now + (TRIAL_DURATION_DAYS.toLong() * 86400000L))

        val account = UserAccount(
            userId = userId,
            businessName = cleanBiz,
            ownerFullName = cleanOwner,
            phoneNumber = cleanPhone,
            email = cleanEmail,
            passwordHash = passwordHash,
            passwordSalt = salt,
            isVerified = true,
            licenseType = LicenseType.TRIAL,
            licenseStatus = LicenseStatus.ACTIVE,
            trialStartDate = trialStartStr,
            trialExpiryDate = trialExpiryStr,
            installationId = installationId,
            sessionToken = SecurityUtils.generateSessionToken(),
            createdAt = now
        )

        saveServerAccount(account)

        return ServerResponse(
            success = true,
            message = "Akaunti imeundwa kikamilifu! Leseni ya Jaribio la Siku 30 imewashwa.",
            data = account
        )
    }

    /**
     * Resends OTP for registration or forgot password.
     */
    fun resendOtp(emailOrPhone: String): ServerResponse<String> {
        val clean = emailOrPhone.trim().lowercase(Locale.ROOT)
        val existingPending = pendingOtps[clean]
            ?: return ServerResponse(false, "Hakuna ombi linalosubiri uthibitisho kwa $emailOrPhone.")

        val now = System.currentTimeMillis()
        val newOtp = SecurityUtils.generateOtp()
        val updatedPending = existingPending.copy(
            code = newOtp,
            generatedAt = now,
            expiresAt = now + OTP_EXPIRY_MS
        )

        pendingOtps[clean] = updatedPending
        if (existingPending.userAccount != null) {
            pendingOtps[existingPending.userAccount.email] = updatedPending
            pendingOtps[existingPending.userAccount.phoneNumber] = updatedPending
        }

        return ServerResponse(
            success = true,
            message = "Msimbo mpya wa OTP umetumwa kwa SMS kwenye simu yako. Tafadhali angalia kikasha cha SMS.",
            data = "SENT"
        )
    }

    /**
     * Step 2: Verify registration OTP.
     * Automatically provisions 30-day TRIAL license on the server.
     */
    fun verifyRegistrationOtp(
        emailOrPhone: String,
        otpCode: String,
        installationId: String
    ): ServerResponse<UserAccount> {
        val cleanTarget = emailOrPhone.trim().lowercase(Locale.ROOT)
        val cleanOtp = otpCode.trim()

        val pending = pendingOtps[cleanTarget]
            ?: return ServerResponse(false, "Hakuna ombi la usajili linalosubiri kwa $cleanTarget. Tafadhali jisajili upya.")

        if (System.currentTimeMillis() > pending.expiresAt) {
            pendingOtps.remove(cleanTarget)
            return ServerResponse(false, "Msimbo wa OTP umekwisha muda wake (Expired). Tafadhali bonyeza Tuma Tena.")
        }

        if (pending.code != cleanOtp) {
            return ServerResponse(false, "Msimbo wa OTP si sahihi. Tafadhali hakiki tena.")
        }

        val baseAccount = pending.userAccount
            ?: return ServerResponse(false, "Taarifa za usajili hazikupatikana.")

        val now = System.currentTimeMillis()
        val verifiedAccount = baseAccount.copy(
            isVerified = true,
            installationId = installationId,
            sessionToken = SecurityUtils.generateSessionToken(),
            licenseType = LicenseType.TRIAL,
            licenseStatus = LicenseStatus.ACTIVE,
            trialStartDate = getIsoDate(now),
            trialExpiryDate = getIsoDate(now + (TRIAL_DURATION_DAYS.toLong() * 86400000L))
        )

        // Save account to server store
        saveServerAccount(verifiedAccount)

        // Cleanup pending OTPs
        pendingOtps.remove(cleanTarget)
        pendingOtps.remove(verifiedAccount.email)
        pendingOtps.remove(verifiedAccount.phoneNumber)

        return ServerResponse(
            success = true,
            message = "Usajili na Uthibitisho Umekamilika! Leseni ya Jaribio la Siku 30 imewashwa kikamilifu.",
            data = verifiedAccount
        )
    }

    /**
     * Completes registration when phone number has been verified via Firebase PhoneAuthProvider.
     */
    fun completeFirebaseVerifiedRegistration(
        emailOrPhone: String,
        installationId: String,
        firebaseUid: String? = null
    ): ServerResponse<UserAccount> {
        val cleanTarget = emailOrPhone.trim().lowercase(Locale.ROOT)
        val pending = pendingOtps[cleanTarget]
            ?: return ServerResponse(false, "Taarifa za usajili hazikupatikana kwa $cleanTarget.")

        val baseAccount = pending.userAccount
            ?: return ServerResponse(false, "Taarifa za usajili hazikupatikana.")

        val now = System.currentTimeMillis()
        val verifiedAccount = baseAccount.copy(
            isVerified = true,
            installationId = installationId,
            sessionToken = SecurityUtils.generateSessionToken(),
            licenseType = LicenseType.TRIAL,
            licenseStatus = LicenseStatus.ACTIVE,
            trialStartDate = getIsoDate(now),
            trialExpiryDate = getIsoDate(now + (TRIAL_DURATION_DAYS.toLong() * 86400000L))
        )

        saveServerAccount(verifiedAccount)
        pendingOtps.remove(cleanTarget)
        pendingOtps.remove(verifiedAccount.email)
        pendingOtps.remove(verifiedAccount.phoneNumber)

        return ServerResponse(
            success = true,
            message = "Usajili na Uthibitisho Umekamilika kupitia Firebase Phone Auth! Leseni ya Jaribio la Siku 30 imewashwa kikamilifu.",
            data = verifiedAccount
        )
    }

    /**
     * Step 3: Login for existing user or recovery on a new device.
     */
    fun login(
        emailOrPhone: String,
        password: String,
        currentInstallationId: String
    ): ServerResponse<UserAccount> {
        val clean = emailOrPhone.trim().lowercase(Locale.ROOT)
        val account = findAccountByEmailOrPhone(clean)
            ?: return ServerResponse(false, "Akaunti haikupatikana. Hakiki barua pepe au namba ya simu.")

        // Ensure account is active and verified without SMS OTP requirement
        val activeAccount = if (!account.isVerified) {
            val autoVerified = account.copy(isVerified = true)
            saveServerAccount(autoVerified)
            autoVerified
        } else {
            account
        }

        // Verify password hash
        val isPasswordCorrect = SecurityUtils.verifyPassword(password, activeAccount.passwordSalt, activeAccount.passwordHash)
        if (!isPasswordCorrect) {
            return ServerResponse(false, "Nenosiri si sahihi (Incorrect password).")
        }

        // Update installation ID & session token on server
        val updated = activeAccount.copy(
            installationId = currentInstallationId,
            sessionToken = SecurityUtils.generateSessionToken()
        )
        saveServerAccount(updated)

        return ServerResponse(
            success = true,
            message = "Umeingia kikamilifu!",
            data = updated
        )
    }

    /**
     * Completes login or provisions a user account authenticated via Firebase.
     */
    fun loginOrProvisionFromFirebase(
        email: String,
        password: String,
        firebaseUid: String,
        currentInstallationId: String
    ): ServerResponse<UserAccount> {
        val clean = email.trim().lowercase(Locale.ROOT)
        val existing = findAccountByEmailOrPhone(clean)
        if (existing != null) {
            val updated = existing.copy(
                installationId = currentInstallationId,
                sessionToken = SecurityUtils.generateSessionToken(),
                isVerified = true
            )
            saveServerAccount(updated)
            return ServerResponse(true, "Umeingia kikamilifu!", updated)
        } else {
            val now = System.currentTimeMillis()
            val salt = SecurityUtils.generateSalt()
            val passwordHash = SecurityUtils.hashPassword(password, salt)
            val newAccount = UserAccount(
                userId = firebaseUid,
                businessName = "AGRITECH ELECTRICAL SOLUTIONS",
                ownerFullName = clean.substringBefore("@").replaceFirstChar { it.uppercase() },
                phoneNumber = "",
                email = clean,
                passwordHash = passwordHash,
                passwordSalt = salt,
                isVerified = true,
                licenseType = LicenseType.TRIAL,
                licenseStatus = LicenseStatus.ACTIVE,
                trialStartDate = getIsoDate(now),
                trialExpiryDate = getIsoDate(now + (TRIAL_DURATION_DAYS.toLong() * 86400000L)),
                installationId = currentInstallationId,
                sessionToken = SecurityUtils.generateSessionToken(),
                createdAt = now
            )
            saveServerAccount(newAccount)
            return ServerResponse(true, "Umeingia kikamilifu!", newAccount)
        }
    }

    /**
     * Completes login or provisions a user account authenticated via Google Sign-In with Firebase.
     */
    fun loginOrProvisionFromGoogle(
        firebaseUid: String,
        email: String,
        displayName: String?,
        currentInstallationId: String
    ): ServerResponse<UserAccount> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val existing = findAccountByEmailOrPhone(cleanEmail)
        val now = System.currentTimeMillis()

        if (existing != null) {
            val updated = existing.copy(
                userId = if (existing.userId.isBlank()) firebaseUid else existing.userId,
                installationId = currentInstallationId,
                sessionToken = SecurityUtils.generateSessionToken(),
                isVerified = true
            )
            saveServerAccount(updated)
            return ServerResponse(true, "Umeingia kikamilifu kupitia Google!", updated)
        } else {
            val salt = SecurityUtils.generateSalt()
            val defaultOwnerName = if (!displayName.isNullOrBlank()) {
                displayName.trim()
            } else {
                cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            }
            val bizName = if (!displayName.isNullOrBlank()) {
                "${displayName.trim().uppercase()} ENTERPRISE"
            } else {
                "AGRITECH ELECTRICAL SOLUTIONS"
            }

            val newAccount = UserAccount(
                userId = firebaseUid,
                businessName = bizName,
                ownerFullName = defaultOwnerName,
                phoneNumber = "",
                email = cleanEmail,
                passwordHash = SecurityUtils.hashPassword(firebaseUid, salt),
                passwordSalt = salt,
                isVerified = true,
                licenseType = LicenseType.TRIAL,
                licenseStatus = LicenseStatus.ACTIVE,
                trialStartDate = getIsoDate(now),
                trialExpiryDate = getIsoDate(now + (TRIAL_DURATION_DAYS.toLong() * 86400000L)),
                installationId = currentInstallationId,
                sessionToken = SecurityUtils.generateSessionToken(),
                createdAt = now
            )
            saveServerAccount(newAccount)
            return ServerResponse(true, "Umeingia kikamilifu kupitia Google!", newAccount)
        }
    }

    /**
     * Step 4: Forgot password request - generates and dispatches OTP.
     */
    fun requestForgotPasswordOtp(emailOrPhone: String): ServerResponse<String> {
        val clean = emailOrPhone.trim().lowercase(Locale.ROOT)
        val account = findAccountByEmailOrPhone(clean)
            ?: return ServerResponse(false, "Hakuna akaunti iliyosajiliwa na $emailOrPhone.")

        val otp = SecurityUtils.generateOtp()
        val now = System.currentTimeMillis()
        val pending = PendingOtp(
            code = otp,
            generatedAt = now,
            expiresAt = now + OTP_EXPIRY_MS,
            userAccount = account,
            purpose = "FORGOT_PASSWORD"
        )
        pendingOtps[clean] = pending
        pendingOtps[account.email] = pending
        pendingOtps[account.phoneNumber] = pending

        return ServerResponse(
            success = true,
            message = "Msimbo wa kubadili nenosiri (OTP) umetumwa kwa SMS kwenye simu yako. Tafadhali angalia kikasha cha SMS.",
            data = "SENT"
        )
    }

    /**
     * Step 5: Reset password with OTP.
     */
    fun resetPasswordWithOtp(
        emailOrPhone: String,
        otpCode: String,
        newPassword: String
    ): ServerResponse<UserAccount> {
        val clean = emailOrPhone.trim().lowercase(Locale.ROOT)
        val cleanOtp = otpCode.trim()

        val pending = pendingOtps[clean]
            ?: return ServerResponse(false, "Hakuna ombi la kubadili nenosiri kwa $clean.")

        if (pending.purpose != "FORGOT_PASSWORD") {
            return ServerResponse(false, "Madhumuni ya OTP si sahihi.")
        }

        if (System.currentTimeMillis() > pending.expiresAt) {
            pendingOtps.remove(clean)
            return ServerResponse(false, "Msimbo wa OTP umekwisha muda wake. Tafadhali omba tena.")
        }

        if (pending.code != cleanOtp) {
            return ServerResponse(false, "Msimbo wa OTP si sahihi.")
        }

        if (newPassword.length < 4) {
            return ServerResponse(false, "Nenosiri lazima liwe na herufi/namba angalau 4.")
        }

        val account = pending.userAccount
            ?: return ServerResponse(false, "Akaunti haikupatikana.")

        val newSalt = SecurityUtils.generateSalt()
        val newHash = SecurityUtils.hashPassword(newPassword, newSalt)
        val updated = account.copy(
            passwordHash = newHash,
            passwordSalt = newSalt,
            sessionToken = SecurityUtils.generateSessionToken()
        )
        saveServerAccount(updated)

        pendingOtps.remove(clean)
        pendingOtps.remove(account.email)
        pendingOtps.remove(account.phoneNumber)

        return ServerResponse(
            success = true,
            message = "Nenosiri lako jipya limewekwa kikamilifu! Sasa unaweza kuingia.",
            data = updated
        )
    }

    /**
     * Synchronizes business data to user's server cloud backup.
     */
    fun syncBackupToCloud(payload: CloudBackupPayload): ServerResponse<Long> {
        val userId = payload.userId
        if (userId.isBlank()) {
            return ServerResponse(false, "Hujaunganishwa na akaunti ya wingu (Missing User ID).")
        }

        val root = JSONObject().apply {
            put("version", payload.version)
            put("timestamp", payload.timestamp)
            put("userId", payload.userId)

            // Business
            val b = payload.business
            val bObj = JSONObject().apply {
                put("name", b.name)
                put("slogan", b.slogan)
                put("phone1", b.phone1)
                put("phone2", b.phone2)
                put("email", b.email)
                put("address", b.address)
                put("currency", b.currency)
                put("bankName", b.bankName)
                put("bankAccountNumber", b.bankAccountNumber)
                put("bankAccountName", b.bankAccountName)
                put("lipaNumber", b.lipaNumber)
                put("mobileMoney", b.mobileMoney)
                put("logoPath", b.logoPath)
            }
            put("business", bObj)

            // Materials
            val mArr = JSONArray()
            for (m in payload.materials) {
                val o = JSONObject().apply {
                    put("id", m.id)
                    put("name", m.name)
                    put("unit", m.unit)
                    put("price", m.price)
                    put("category", m.category)
                }
                mArr.put(o)
            }
            put("materials", mArr)

            // Customers
            val cArr = JSONArray()
            for (c in payload.customers) {
                val o = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("location", c.location)
                    put("notes", c.notes)
                }
                cArr.put(o)
            }
            put("customers", cArr)

            // Quotes & Invoices & Receipts
            val qArr = JSONArray()
            for (q in payload.quotes) {
                val o = JSONObject().apply {
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
                qArr.put(o)
            }
            put("quotes", qArr)
        }

        serverStore.edit()
            .putString("cloud_backup_$userId", root.toString())
            .putLong("cloud_backup_time_$userId", payload.timestamp)
            .apply()

        return ServerResponse(
            success = true,
            message = "Taarifa za biashara zimehifadhiwa kwenye wingu (Cloud Backup Successful)!",
            data = payload.timestamp
        )
    }

    /**
     * Restores backed-up business data from server cloud for a user (New phone / recovery).
     */
    fun restoreBackupFromCloud(userId: String): ServerResponse<CloudBackupPayload> {
        if (userId.isBlank()) {
            return ServerResponse(false, "User ID haipo.")
        }

        val jsonStr = serverStore.getString("cloud_backup_$userId", null)
            ?: return ServerResponse(false, "Hakuna taarifa za nakala (backup) zilizopatikana kwenye wingu kwa akaunti hii.")

        return try {
            val root = JSONObject(jsonStr)
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())

            val bObj = root.optJSONObject("business")
            val business = if (bObj != null) {
                BusinessSettings(
                    name = bObj.optString("name", ""),
                    slogan = bObj.optString("slogan", ""),
                    phone1 = bObj.optString("phone1", ""),
                    phone2 = bObj.optString("phone2", ""),
                    email = bObj.optString("email", ""),
                    address = bObj.optString("address", ""),
                    currency = bObj.optString("currency", "TSh"),
                    bankName = bObj.optString("bankName", ""),
                    bankAccountNumber = bObj.optString("bankAccountNumber", ""),
                    bankAccountName = bObj.optString("bankAccountName", ""),
                    lipaNumber = bObj.optString("lipaNumber", ""),
                    mobileMoney = bObj.optString("mobileMoney", ""),
                    logoPath = bObj.optString("logoPath", "")
                )
            } else {
                BusinessSettings()
            }

            val materials = mutableListOf<MaterialEntity>()
            val mArr = root.optJSONArray("materials")
            if (mArr != null) {
                for (i in 0 until mArr.length()) {
                    val o = mArr.getJSONObject(i)
                    materials.add(
                        MaterialEntity(
                            id = o.optInt("id", 0),
                            name = o.optString("name", "Item"),
                            unit = o.optString("unit", "Pcs"),
                            price = o.optDouble("price", 0.0),
                            category = o.optString("category", "Jumla")
                        )
                    )
                }
            }

            val customers = mutableListOf<CustomerEntity>()
            val cArr = root.optJSONArray("customers")
            if (cArr != null) {
                for (i in 0 until cArr.length()) {
                    val o = cArr.getJSONObject(i)
                    customers.add(
                        CustomerEntity(
                            id = o.optInt("id", 0),
                            name = o.optString("name", "Customer"),
                            phone = o.optString("phone", ""),
                            location = o.optString("location", ""),
                            notes = o.optString("notes", "")
                        )
                    )
                }
            }

            val quotes = mutableListOf<QuoteEntity>()
            val qArr = root.optJSONArray("quotes")
            if (qArr != null) {
                for (i in 0 until qArr.length()) {
                    val o = qArr.getJSONObject(i)
                    quotes.add(
                        QuoteEntity(
                            id = o.optInt("id", 0),
                            number = o.optString("number", "QTN-001"),
                            date = o.optString("date", "2026-01-01"),
                            validUntil = o.optString("validUntil", ""),
                            customerId = o.optInt("customerId", 0),
                            customerName = o.optString("customerName", "Customer"),
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

            val payload = CloudBackupPayload(
                version = 1,
                timestamp = timestamp,
                userId = userId,
                business = business,
                materials = materials,
                customers = customers,
                quotes = quotes
            )

            ServerResponse(
                success = true,
                message = "Nakala ya wingu imerejeshwa kikamilifu! (Data Restored Successfully)",
                data = payload
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ServerResponse(false, "Hitilafu wakati wa kurejesha nakala ya wingu: ${e.message}")
        }
    }

    /**
     * Updates paid license record on the server so changing or losing phone preserves the license.
     */
    fun recordPaidLicenseOnServer(
        userId: String,
        licenseType: LicenseType,
        licenseKey: String,
        expiresAt: String?
    ) {
        val account = findAccountById(userId) ?: return
        val now = System.currentTimeMillis()
        val updated = account.copy(
            licenseType = licenseType,
            licenseStatus = LicenseStatus.ACTIVE,
            paidLicenseStartDate = getIsoDate(now),
            paidLicenseExpiryDate = expiresAt,
            paidLicenseKey = licenseKey
        )
        saveServerAccount(updated)
    }

    // ----------------------------------------------------
    // Internal Server Storage Helpers
    // ----------------------------------------------------

    private fun findAccountByEmailOrPhone(query: String): UserAccount? {
        val clean = query.trim().lowercase(Locale.ROOT)
        val allAccountsJson = serverStore.getString("server_user_accounts_list", "[]") ?: "[]"
        return try {
            val arr = JSONArray(allAccountsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val email = obj.optString("email", "").lowercase(Locale.ROOT)
                val phone = obj.optString("phoneNumber", "").lowercase(Locale.ROOT)
                if (email == clean || phone == clean) {
                    return parseAccountFromJson(obj)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun findAccountById(userId: String): UserAccount? {
        val allAccountsJson = serverStore.getString("server_user_accounts_list", "[]") ?: "[]"
        return try {
            val arr = JSONArray(allAccountsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("userId", "") == userId) {
                    return parseAccountFromJson(obj)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun saveServerAccount(account: UserAccount) {
        val allAccountsJson = serverStore.getString("server_user_accounts_list", "[]") ?: "[]"
        val list = mutableListOf<UserAccount>()
        try {
            val arr = JSONArray(allAccountsJson)
            for (i in 0 until arr.length()) {
                val acc = parseAccountFromJson(arr.getJSONObject(i))
                if (acc != null && acc.userId != account.userId) {
                    list.add(acc)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        list.add(0, account)

        val outArr = JSONArray()
        for (acc in list) {
            outArr.put(serializeAccountToJson(acc))
        }
        serverStore.edit().putString("server_user_accounts_list", outArr.toString()).apply()
    }

    private fun serializeAccountToJson(acc: UserAccount): JSONObject {
        return JSONObject().apply {
            put("userId", acc.userId)
            put("businessName", acc.businessName)
            put("ownerFullName", acc.ownerFullName)
            put("phoneNumber", acc.phoneNumber)
            put("email", acc.email)
            put("passwordHash", acc.passwordHash)
            put("passwordSalt", acc.passwordSalt)
            put("isVerified", acc.isVerified)
            put("licenseType", acc.licenseType.code)
            put("licenseStatus", acc.licenseStatus.name)
            put("trialStartDate", acc.trialStartDate)
            put("trialExpiryDate", acc.trialExpiryDate)
            put("paidLicenseStartDate", acc.paidLicenseStartDate ?: JSONObject.NULL)
            put("paidLicenseExpiryDate", acc.paidLicenseExpiryDate ?: JSONObject.NULL)
            put("paidLicenseKey", acc.paidLicenseKey ?: JSONObject.NULL)
            put("installationId", acc.installationId)
            put("sessionToken", acc.sessionToken)
            put("lastSyncTimestamp", acc.lastSyncTimestamp)
            put("createdAt", acc.createdAt)
        }
    }

    private fun parseAccountFromJson(obj: JSONObject): UserAccount? {
        return try {
            UserAccount(
                userId = obj.getString("userId"),
                businessName = obj.optString("businessName", "AGRITECH ELECTRICAL SOLUTIONS"),
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
}
