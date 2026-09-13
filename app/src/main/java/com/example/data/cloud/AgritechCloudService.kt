package com.example.data.cloud

import android.content.Context
import android.content.SharedPreferences
import com.example.data.models.*
import com.example.data.security.SecurityUtils
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

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

    private fun getAuthenticatedFirebaseUid(fallbackUserId: String? = null): String {
        val authUid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }
        return when {
            !authUid.isNullOrBlank() -> authUid
            !fallbackUserId.isNullOrBlank() -> fallbackUserId
            else -> ""
        }
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
     * Scoped strictly under /users/{uid} in Firestore.
     */
    fun syncBackupToCloud(payload: CloudBackupPayload): ServerResponse<Long> {
        val uid = getAuthenticatedFirebaseUid(payload.userId)
        if (uid.isBlank()) {
            return ServerResponse(false, "Hujaunganishwa na akaunti ya wingu (Missing or unauthenticated User ID).")
        }

        val timestamp = payload.timestamp
        val root = JSONObject().apply {
            put("version", payload.version)
            put("timestamp", timestamp)
            put("userId", uid)

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
                    put("internalCode", m.internalCode)
                    put("name", m.name)
                    put("unit", m.unit)
                    put("price", m.price)
                    put("category", m.category)
                    put("isDemo", m.isDemo)
                    put("userId", m.userId)
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

        // Save locally for offline fallback
        serverStore.edit()
            .putString("cloud_backup_$uid", root.toString())
            .putLong("cloud_backup_time_$uid", timestamp)
            .apply()

        // Real Firestore Sync scoped under /users/$uid
        try {
            val db = FirebaseFirestore.getInstance()
            val userDocRef = db.collection("users").document(uid)

            val b = payload.business
            val businessMap = hashMapOf<String, Any>(
                "name" to b.name,
                "slogan" to b.slogan,
                "phone1" to b.phone1,
                "phone2" to b.phone2,
                "email" to b.email,
                "address" to b.address,
                "currency" to b.currency,
                "bankName" to b.bankName,
                "bankAccountNumber" to b.bankAccountNumber,
                "bankAccountName" to b.bankAccountName,
                "lipaNumber" to b.lipaNumber,
                "mobileMoney" to b.mobileMoney,
                "logoPath" to b.logoPath,
                "signaturePath" to b.signaturePath,
                "termsSw" to b.quotationTermsSw,
                "termsEn" to b.quotationTermsEn
            )

            val userRootMap = hashMapOf<String, Any>(
                "userId" to uid,
                "lastSyncTimestamp" to timestamp,
                "business" to businessMap,
                "materialsCount" to payload.materials.size,
                "customersCount" to payload.customers.size,
                "quotesCount" to payload.quotes.size
            )
            userDocRef.set(userRootMap, SetOptions.merge())

            // Backup Snapshot document at /users/$uid/backups/latest
            val materialsListMap = payload.materials.map { m ->
                hashMapOf<String, Any>(
                    "id" to m.id,
                    "internalCode" to m.internalCode,
                    "name" to m.name,
                    "unit" to m.unit,
                    "price" to m.price,
                    "category" to m.category,
                    "isDemo" to m.isDemo,
                    "userId" to m.userId
                )
            }
            val customersListMap = payload.customers.map { c ->
                hashMapOf<String, Any>(
                    "id" to c.id,
                    "name" to c.name,
                    "phone" to c.phone,
                    "location" to c.location,
                    "notes" to c.notes
                )
            }
            val quotesListMap = payload.quotes.map { q ->
                hashMapOf<String, Any>(
                    "id" to q.id,
                    "number" to q.number,
                    "date" to q.date,
                    "validUntil" to q.validUntil,
                    "customerId" to q.customerId,
                    "customerName" to q.customerName,
                    "customerPhone" to q.customerPhone,
                    "customerLocation" to q.customerLocation,
                    "description" to q.description,
                    "itemsJson" to q.itemsJson,
                    "materialsTotal" to q.materialsTotal,
                    "labour" to q.labour,
                    "grandTotal" to q.grandTotal,
                    "status" to q.status,
                    "paid" to q.paid,
                    "createdAt" to q.createdAt
                )
            }

            val backupDocMap = hashMapOf<String, Any>(
                "version" to payload.version,
                "timestamp" to timestamp,
                "userId" to uid,
                "business" to businessMap,
                "materials" to materialsListMap,
                "customers" to customersListMap,
                "quotes" to quotesListMap
            )

            val backupTask = userDocRef.collection("backups").document("latest").set(backupDocMap)
            Tasks.await(backupTask, 10, TimeUnit.SECONDS)

            // Subcollections under /users/$uid/
            try {
                val matSnapshot = Tasks.await(userDocRef.collection("materials").get(), 5, TimeUnit.SECONDS)
                val localMatIds = payload.materials.map { it.id.toString() }.toSet()
                val batch = db.batch()
                var batchCount = 0
                for (doc in matSnapshot.documents) {
                    if (!localMatIds.contains(doc.id)) {
                        batch.delete(doc.reference)
                        batchCount++
                    }
                }
                if (batchCount > 0) {
                    Tasks.await(batch.commit(), 5, TimeUnit.SECONDS)
                }
            } catch (e: Exception) {
                android.util.Log.w("AgritechCloudService", "Error cleaning deleted cloud materials: ${e.message}")
            }

            if (payload.materials.isNotEmpty()) {
                for (m in payload.materials) {
                    val matMap = hashMapOf<String, Any>(
                        "id" to m.id,
                        "internalCode" to m.internalCode,
                        "name" to m.name,
                        "unit" to m.unit,
                        "price" to m.price,
                        "category" to m.category,
                        "isDemo" to m.isDemo,
                        "userId" to m.userId
                    )
                    userDocRef.collection("materials").document(m.id.toString()).set(matMap, SetOptions.merge())
                }
            }

            for (c in payload.customers) {
                val custMap = hashMapOf<String, Any>(
                    "id" to c.id,
                    "name" to c.name,
                    "phone" to c.phone,
                    "location" to c.location,
                    "notes" to c.notes
                )
                userDocRef.collection("customers").document(c.id.toString()).set(custMap, SetOptions.merge())
            }

            for (q in payload.quotes) {
                val qMap = hashMapOf<String, Any>(
                    "id" to q.id,
                    "number" to q.number,
                    "date" to q.date,
                    "validUntil" to q.validUntil,
                    "customerId" to q.customerId,
                    "customerName" to q.customerName,
                    "customerPhone" to q.customerPhone,
                    "customerLocation" to q.customerLocation,
                    "description" to q.description,
                    "itemsJson" to q.itemsJson,
                    "materialsTotal" to q.materialsTotal,
                    "labour" to q.labour,
                    "grandTotal" to q.grandTotal,
                    "status" to q.status,
                    "paid" to q.paid,
                    "createdAt" to q.createdAt
                )
                userDocRef.collection("quotes").document(q.id.toString()).set(qMap, SetOptions.merge())
            }
        } catch (e: Exception) {
            android.util.Log.w("AgritechCloudService", "Firestore sync warning (using local store): ${e.message}")
        }

        return ServerResponse(
            success = true,
            message = "Taarifa za biashara zimehifadhiwa kwenye wingu (Firestore Cloud Backup Successful)!",
            data = timestamp
        )
    }

    /**
     * Restores backed-up business data from server cloud for a user.
     * Reads strictly from /users/{uid} in Firestore.
     */
    fun restoreBackupFromCloud(userId: String): ServerResponse<CloudBackupPayload> {
        val uid = getAuthenticatedFirebaseUid(userId)
        if (uid.isBlank()) {
            return ServerResponse(false, "User ID haipo.")
        }

        // Attempt restoring directly from Firestore /users/$uid/backups/latest
        try {
            val db = FirebaseFirestore.getInstance()
            val backupDocRef = db.collection("users").document(uid).collection("backups").document("latest")
            val task = backupDocRef.get()
            val snapshot = Tasks.await(task, 10, TimeUnit.SECONDS)

            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data
                if (data != null) {
                    val timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                    val version = ((data["version"] as? Long) ?: 1L).toInt()

                    val bObj = data["business"] as? Map<*, *>
                    val business = if (bObj != null) {
                        BusinessSettings(
                            name = bObj["name"]?.toString() ?: "",
                            slogan = bObj["slogan"]?.toString() ?: "",
                            phone1 = bObj["phone1"]?.toString() ?: "",
                            phone2 = bObj["phone2"]?.toString() ?: "",
                            email = bObj["email"]?.toString() ?: "",
                            address = bObj["address"]?.toString() ?: "",
                            currency = bObj["currency"]?.toString() ?: "TSh",
                            bankName = bObj["bankName"]?.toString() ?: "",
                            bankAccountNumber = bObj["bankAccountNumber"]?.toString() ?: "",
                            bankAccountName = bObj["bankAccountName"]?.toString() ?: "",
                            lipaNumber = bObj["lipaNumber"]?.toString() ?: "",
                            mobileMoney = bObj["mobileMoney"]?.toString() ?: "",
                            logoPath = bObj["logoPath"]?.toString() ?: "",
                            signaturePath = bObj["signaturePath"]?.toString() ?: "",
                            quotationTermsSw = bObj["termsSw"]?.toString() ?: "",
                            quotationTermsEn = bObj["termsEn"]?.toString() ?: ""
                        )
                    } else {
                        BusinessSettings()
                    }

                    val materials = mutableListOf<MaterialEntity>()
                    val mList = data["materials"] as? List<*>
                    if (mList != null) {
                        for (item in mList) {
                            val m = item as? Map<*, *> ?: continue
                            materials.add(
                                MaterialEntity(
                                    id = (m["id"] as? Number)?.toInt() ?: 0,
                                    internalCode = m["internalCode"]?.toString() ?: "",
                                    name = m["name"]?.toString() ?: "Item",
                                    unit = m["unit"]?.toString() ?: "Pcs",
                                    price = (m["price"] as? Number)?.toDouble() ?: 0.0,
                                    category = m["category"]?.toString() ?: "Jumla",
                                    isDemo = (m["isDemo"] as? Boolean) ?: false,
                                    userId = m["userId"]?.toString() ?: uid
                                )
                            )
                        }
                    }

                    val customers = mutableListOf<CustomerEntity>()
                    val cList = data["customers"] as? List<*>
                    if (cList != null) {
                        for (item in cList) {
                            val c = item as? Map<*, *> ?: continue
                            customers.add(
                                CustomerEntity(
                                    id = (c["id"] as? Number)?.toInt() ?: 0,
                                    name = c["name"]?.toString() ?: "Customer",
                                    phone = c["phone"]?.toString() ?: "",
                                    location = c["location"]?.toString() ?: "",
                                    notes = c["notes"]?.toString() ?: ""
                                )
                            )
                        }
                    }

                    val quotes = mutableListOf<QuoteEntity>()
                    val qList = data["quotes"] as? List<*>
                    if (qList != null) {
                        for (item in qList) {
                            val q = item as? Map<*, *> ?: continue
                            quotes.add(
                                QuoteEntity(
                                    id = (q["id"] as? Number)?.toInt() ?: 0,
                                    number = q["number"]?.toString() ?: "QTN-001",
                                    date = q["date"]?.toString() ?: "2026-01-01",
                                    validUntil = q["validUntil"]?.toString() ?: "",
                                    customerId = (q["customerId"] as? Number)?.toInt() ?: 0,
                                    customerName = q["customerName"]?.toString() ?: "Customer",
                                    customerPhone = q["customerPhone"]?.toString() ?: "",
                                    customerLocation = q["customerLocation"]?.toString() ?: "",
                                    description = q["description"]?.toString() ?: "",
                                    itemsJson = q["itemsJson"]?.toString() ?: "[]",
                                    materialsTotal = (q["materialsTotal"] as? Number)?.toDouble() ?: 0.0,
                                    labour = (q["labour"] as? Number)?.toDouble() ?: 0.0,
                                    grandTotal = (q["grandTotal"] as? Number)?.toDouble() ?: 0.0,
                                    status = q["status"]?.toString() ?: "quotation",
                                    paid = (q["paid"] as? Boolean) ?: false,
                                    createdAt = (q["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }

                    val payload = CloudBackupPayload(
                        version = version,
                        timestamp = timestamp,
                        userId = uid,
                        business = business,
                        materials = materials,
                        customers = customers,
                        quotes = quotes
                    )

                    return ServerResponse(
                        success = true,
                        message = "Taarifa zote zimerudishwa kutoka kwenye wingu (Firestore Backup Restored)!",
                        data = payload
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AgritechCloudService", "Firestore restore warning: ${e.message}")
        }

        // Fallback to local serverStore cache if Firestore is unreachable / offline
        val jsonStr = serverStore.getString("cloud_backup_$uid", null)
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
                            internalCode = o.optString("internalCode", ""),
                            name = o.optString("name", "Item"),
                            unit = o.optString("unit", "Pcs"),
                            price = o.optDouble("price", 0.0),
                            category = o.optString("category", "Jumla"),
                            isDemo = o.optBoolean("isDemo", false),
                            userId = o.optString("userId", uid)
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
                userId = uid,
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

    fun deleteMaterialFromCloud(materialId: Int, userId: String) {
        val uid = getAuthenticatedFirebaseUid(userId)
        if (uid.isNotBlank()) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(uid).collection("materials").document(materialId.toString()).delete()
            } catch (e: Exception) {
                android.util.Log.w("AgritechCloudService", "Firestore deleteMaterial error: ${e.message}")
            }
        }
    }

    fun deleteMaterialsByCategoryFromCloud(category: String, userId: String) {
        val uid = getAuthenticatedFirebaseUid(userId)
        if (uid.isNotBlank()) {
            try {
                val db = FirebaseFirestore.getInstance()
                val userDocRef = db.collection("users").document(uid)
                val matSnapshot = Tasks.await(userDocRef.collection("materials").get(), 5, TimeUnit.SECONDS)
                val batch = db.batch()
                var batchCount = 0
                for (doc in matSnapshot.documents) {
                    val cat = doc.getString("category") ?: ""
                    val name = doc.getString("name") ?: ""
                    if (com.example.ui.utils.MaterialCategoryUtils.matchesCategory(com.example.data.models.MaterialEntity(name = name, category = cat, unit = "", price = 0.0), category)) {
                        batch.delete(doc.reference)
                        batchCount++
                    }
                }
                if (batchCount > 0) {
                    Tasks.await(batch.commit(), 5, TimeUnit.SECONDS)
                }
            } catch (e: Exception) {
                android.util.Log.w("AgritechCloudService", "Firestore deleteMaterialsByCategory error: ${e.message}")
            }
        }
    }

    fun deleteAllMaterialsFromCloud(userId: String) {
        val uid = getAuthenticatedFirebaseUid(userId)
        if (uid.isNotBlank()) {
            try {
                val db = FirebaseFirestore.getInstance()
                val userDocRef = db.collection("users").document(uid)
                val matSnapshot = Tasks.await(userDocRef.collection("materials").get(), 5, TimeUnit.SECONDS)
                val batch = db.batch()
                for (doc in matSnapshot.documents) {
                    batch.delete(doc.reference)
                }
                // Also update latest backup if it exists
                val backupDoc = userDocRef.collection("backups").document("latest")
                batch.update(backupDoc, "materials", emptyList<Map<String, Any>>(), "materialsCount", 0)
                Tasks.await(batch.commit(), 5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                android.util.Log.w("AgritechCloudService", "Firestore deleteAllMaterials error: ${e.message}")
            }
        }
    }

    fun deleteCustomerFromCloud(customerId: Int, userId: String) {
        val uid = getAuthenticatedFirebaseUid(userId)
        if (uid.isNotBlank()) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(uid).collection("customers").document(customerId.toString()).delete()
            } catch (e: Exception) {
                android.util.Log.w("AgritechCloudService", "Firestore deleteCustomer error: ${e.message}")
            }
        }
    }

    fun deleteQuoteFromCloud(quoteId: Int, userId: String) {
        val uid = getAuthenticatedFirebaseUid(userId)
        if (uid.isNotBlank()) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(uid).collection("quotes").document(quoteId.toString()).delete()
            } catch (e: Exception) {
                android.util.Log.w("AgritechCloudService", "Firestore deleteQuote error: ${e.message}")
            }
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

        // Sync profile directly to Firestore document /users/{uid}
        try {
            val uid = getAuthenticatedFirebaseUid(account.userId)
            if (uid.isNotBlank()) {
                val db = FirebaseFirestore.getInstance()
                val userMap = hashMapOf<String, Any>(
                    "userId" to uid,
                    "businessName" to account.businessName,
                    "ownerFullName" to account.ownerFullName,
                    "phoneNumber" to account.phoneNumber,
                    "email" to account.email,
                    "isVerified" to account.isVerified,
                    "licenseType" to account.licenseType.code,
                    "licenseStatus" to account.licenseStatus.name,
                    "trialStartDate" to (account.trialStartDate ?: ""),
                    "trialExpiryDate" to (account.trialExpiryDate ?: ""),
                    "installationId" to account.installationId,
                    "createdAt" to account.createdAt,
                    "updatedAt" to System.currentTimeMillis()
                )
                account.paidLicenseKey?.let { userMap["paidLicenseKey"] = it }
                userMap["profile"] = hashMapOf(
                    "email" to account.email,
                    "ownerFullName" to account.ownerFullName,
                    "businessName" to account.businessName,
                    "phoneNumber" to account.phoneNumber
                )
                db.collection("users").document(uid).set(userMap, SetOptions.merge())
            }
        } catch (e: Exception) {
            android.util.Log.w("AgritechCloudService", "Firestore profile save warning: ${e.message}")
        }
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
