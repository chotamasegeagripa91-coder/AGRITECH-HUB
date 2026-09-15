package com.example.data.licensing

import android.content.SharedPreferences
import android.util.Base64
import com.example.data.models.LicenseInfo
import com.example.data.models.LicenseStatus
import com.example.data.models.LicenseType
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object LicensingEngine {

    const val APP_NAME = "AGRITECH HUB"
    const val COMPANY_NAME = "AGRITECH Electrical Solution"
    const val SLOGAN = "Ubora na Usalama wa Umeme Ndio Fahari Yetu"
    const val TRIAL_DAYS = 30
    const val DEFAULT_PASSWORD = "agritech123"

    // Asymmetric Public Key (RSA-2048 X.509 SubjectPublicKeyInfo in Base64)
    // The Customer APK holds ONLY this Public Key.
    // The Private Key is strictly held by Admin to prevent license forgery.
    const val RSA_PUBLIC_KEY_BASE64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAs9Y5DxmkPN6d79BcoZr6RZdYzCRfP5Mi3TQR+WYKRMC4duWs+mhaPk4aiZqS4eulKGqx+eS/Zn34Dx55sU0v7bQs0LUVU8qTbNF2GVgiGX0jPwmQByaPYc8fQSPKlD7Tp9L8UcymY5TV/RrmZhZEwGpC/6qLshhnly3Ngq3BhOq8Qw0I13VYjEoewOPswaHFrpnQZrscQAMzIewBMpgdJ9EfyOB7hp01fBZGzsM4qPF6n0BQ3zOCWoqpPGxcVzbizcOF/Xs4UcDx1ZlVkt6T9SLYRfzBWTjOLOFCWh2QUx31iqvAVN/A894LiML3Ksr8adctRZUESVk+AJ/7snaBSQIDAQAB"

    @Volatile
    private var cachedPublicKey: PublicKey? = null

    private fun getPublicKey(): PublicKey {
        cachedPublicKey?.let { return it }
        val keyBytes = Base64.decode(RSA_PUBLIC_KEY_BASE64, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val pubKey = keyFactory.generatePublic(spec)
        cachedPublicKey = pubKey
        return pubKey
    }

    val PAYMENT_INFO = mapOf(
        "halopesa" to "+255 627 318 891 (AGRIPA EDWARD)",
        "mixx" to "+255 650 549 735 (AGRIPA EDWARD)",
        "adminPhone" to "+255 627 318 891",
        "adminPhoneAlt" to "+255 650 549 735",
        "adminWhatsapp" to "255627318891",
        "adminEmail" to "agritechelectricalsolution@gmail.com",
        "instructionSw" to "Baada ya kukamilisha malipo kwa HALOPESA au MIXX BY YAS (AGRIPA EDWARD), wasiliana nasi kupokea Code yako ya uanzishaji wa leseni.",
        "instructionEn" to "After completing payment via HALOPESA or MIXX BY YAS (AGRIPA EDWARD), contact us to receive your license activation code."
    )

    /**
     * Extracts clean phone model name (e.g. Samsung A065F/DS, TECNO BG6, Tecno Spark 20, etc.).
     */
    fun getDeviceModelName(): String {
        val mfg = try {
            android.os.Build.MANUFACTURER.orEmpty().trim()
        } catch (e: Exception) { "" }
        val model = try {
            android.os.Build.MODEL.orEmpty().trim()
        } catch (e: Exception) { "" }

        val cleanMfg = when {
            mfg.isBlank() || mfg.equals("unknown", ignoreCase = true) || mfg.equals("generic", ignoreCase = true) || mfg.equals("android", ignoreCase = true) -> ""
            else -> mfg
        }
        var cleanModel = when {
            model.isBlank() || model.equals("unknown", ignoreCase = true) || model.equals("generic", ignoreCase = true) || model.equals("android", ignoreCase = true) -> ""
            else -> model
        }

        // If Samsung, strip common "SM-" prefix (e.g. SM-A065F/DS -> A065F/DS)
        if (cleanMfg.equals("samsung", ignoreCase = true) && cleanModel.startsWith("SM-", ignoreCase = true)) {
            cleanModel = cleanModel.substring(3).trim()
        }

        val formattedMfg = if (cleanMfg.all { it.isUpperCase() } && cleanMfg.length > 3) {
            cleanMfg
        } else {
            cleanMfg.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        val result = when {
            formattedMfg.isNotEmpty() && cleanModel.isNotEmpty() -> {
                if (cleanModel.startsWith(formattedMfg, ignoreCase = true)) {
                    cleanModel
                } else {
                    "$formattedMfg $cleanModel"
                }
            }
            formattedMfg.isNotEmpty() -> formattedMfg
            cleanModel.isNotEmpty() -> cleanModel
            else -> "Android Device"
        }
        return result.trim()
    }

    /**
     * Extracts device brand prefix (e.g. TECNO, SAMSUNG, REDMI, INFINIX, OPPO, VIVO).
     */
    fun getDeviceBrandPrefix(): String {
        val mfg = try {
            android.os.Build.MANUFACTURER.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
        } catch (e: Exception) { "" }

        val model = try {
            android.os.Build.MODEL.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
        } catch (e: Exception) { "" }

        val cleanPrefix = when {
            mfg.isNotBlank() && mfg != "UNKNOWN" && mfg != "GENERIC" && mfg != "ANDROID" -> mfg
            model.isNotBlank() && model != "UNKNOWN" && model != "GENERIC" && model != "ANDROID" -> model
            else -> "PHONE"
        }
        return cleanPrefix.take(8)
    }

    /**
     * Generates a unique 8-character uppercase License ID (e.g. LIC-CE9D3E20).
     */
    fun generateLicenseId(): String {
        val hex = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.ROOT)
        return "LIC-$hex"
    }

    /**
     * Cryptographically verifies the RSA-2048 (SHA256withRSA) digital signature against the Base64 payload
     * using the Customer APK's embedded RSA Public Key.
     * The private key is held strictly by Admin on the server/generator tool, ensuring no forgery is possible.
     */
    fun verifySignature(payloadBase64: String, signatureBase64: String): Boolean {
        return try {
            val pubKey = getPublicKey()
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(pubKey)
            sig.update(payloadBase64.toByteArray(Charsets.UTF_8))
            val sigBytes = Base64.decode(signatureBase64.trim(), Base64.DEFAULT)
            sig.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets or generates the installation ID matching the phone model name (e.g. Samsung A065F/DS, TECNO BG6).
     */
    fun getInstallationId(prefs: SharedPreferences): String {
        var id = prefs.getString("agritech_device_name_id", null)
        if (id.isNullOrBlank()) {
            val deviceName = getDeviceModelName()
            id = deviceName
            val oldId = prefs.getString("agritech_installation_id", null)
            if (!oldId.isNullOrBlank() && oldId != deviceName) {
                prefs.edit().putString("agritech_installation_id_legacy", oldId).apply()
            }
            prefs.edit()
                .putString("agritech_device_name_id", id)
                .putString("agritech_installation_id", id)
                .apply()
        }
        return id
    }

    fun getDeviceShortCode(fullId: String): String {
        val clean = fullId.replace(Regex("[^A-Za-z0-9]"), "").uppercase(Locale.ROOT)
        return clean.takeLast(4).ifEmpty { "UNIV" }
    }

    data class VerificationResult(
        val valid: Boolean,
        val licenseType: LicenseType? = null,
        val expiresAt: String? = null,
        val message: String = "",
        val isLifetime: Boolean = false,
        val customerName: String? = null,
        val licenseId: String? = null
    )

    /**
     * Verifies license code (cryptographic RSA digital signature and legacy format).
     */
    fun verifyActivationCode(
        code: String,
        currentInstallationId: String,
        legacyInstallationId: String? = null
    ): VerificationResult {
        val clean = code.trim().replace("\\s".toRegex(), "")
        if (clean.isBlank()) {
            return VerificationResult(valid = false, message = "Code ya leseni haipaswi kuwa wazi.")
        }

        // 1. BASE64_PAYLOAD.SIGNATURE Format (RSA-2048 SHA256withRSA)
        if (clean.contains(".")) {
            val dotIndex = clean.indexOf('.')
            if (dotIndex <= 0 || dotIndex >= clean.length - 1) {
                return VerificationResult(valid = false, message = "Muundo wa code ya leseni si sahihi (Invalid Token Structure).")
            }

            val payloadBase64 = clean.substring(0, dotIndex).trim()
            val signatureBase64 = clean.substring(dotIndex + 1).trim()

            // Cryptographic Signature Verification
            if (!verifySignature(payloadBase64, signatureBase64)) {
                return VerificationResult(
                    valid = false,
                    message = "Sahihi ya leseni si sahihi au imechezewa (Cryptographic Signature Verification Failed)."
                )
            }

            // Decode and Parse JSON Payload
            val jsonObj: JSONObject
            try {
                val decodedBytes = Base64.decode(payloadBase64, Base64.DEFAULT)
                val jsonString = String(decodedBytes, Charsets.UTF_8)
                jsonObj = JSONObject(jsonString)
            } catch (e: Exception) {
                return VerificationResult(
                    valid = false,
                    message = "Taarifa za leseni zimeharibika (Payload Decode Error)."
                )
            }

            val licenseId = jsonObj.optString("id", "")
            val customer = jsonObj.optString("customer", "")
            val typeStr = jsonObj.optString("type", "LIFETIME").uppercase(Locale.ROOT)
            val installId = jsonObj.optString("installId", "UNIVERSAL")
            val issuedAt = jsonObj.optLong("issuedAt", 0L)
            val days = jsonObj.optInt("days", 0)

            val licenseType = when (typeStr) {
                "MONTHLY" -> LicenseType.MONTHLY
                "QUARTERLY" -> LicenseType.QUARTERLY
                "SEMI_ANNUAL", "SIX_MONTHS", "6MONTHS", "HALF_YEAR", "SEMIANNUAL" -> LicenseType.SEMI_ANNUAL
                "YEARLY" -> LicenseType.YEARLY
                "LIFETIME" -> LicenseType.LIFETIME
                else -> LicenseType.fromCode(typeStr)
            }

            // Device Installation ID Matching
            val currentShortCode = getDeviceShortCode(currentInstallationId)
            val legacyShortCode = legacyInstallationId?.let { getDeviceShortCode(it) }
            val targetShortCode = getDeviceShortCode(installId)
            val isUniversal = installId.equals("UNIVERSAL", ignoreCase = true) || installId.isBlank()

            val cleanTarget = installId.replace(Regex("[^A-Za-z0-9]"), "")
            val cleanCurrent = currentInstallationId.replace(Regex("[^A-Za-z0-9]"), "")
            val cleanLegacy = legacyInstallationId?.replace(Regex("[^A-Za-z0-9]"), "")

            val isMatch = isUniversal ||
                    installId.equals(currentInstallationId, ignoreCase = true) ||
                    (cleanTarget.isNotEmpty() && cleanTarget.equals(cleanCurrent, ignoreCase = true)) ||
                    (legacyInstallationId != null && installId.equals(legacyInstallationId, ignoreCase = true)) ||
                    (cleanLegacy != null && cleanTarget.isNotEmpty() && cleanTarget.equals(cleanLegacy, ignoreCase = true)) ||
                    targetShortCode.equals(currentShortCode, ignoreCase = true) ||
                    (legacyShortCode != null && targetShortCode.equals(legacyShortCode, ignoreCase = true))

            if (!isMatch) {
                return VerificationResult(
                    valid = false,
                    message = "Code hii ya leseni imefungwa kwenye kifaa ($installId). Kifaa hiki ni: $currentInstallationId."
                )
            }

            // Expiration Verification
            val now = System.currentTimeMillis()
            var expiresAtStr: String? = null
            val isLifetime = days == 0 || licenseType == LicenseType.LIFETIME

            if (!isLifetime && days > 0) {
                val validIssuedAt = if (issuedAt > 0) issuedAt else now
                val expiryMs = validIssuedAt + (days.toLong() * 86400000L)
                expiresAtStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(expiryMs))

                if (now > expiryMs) {
                    return VerificationResult(
                        valid = false,
                        licenseType = licenseType,
                        expiresAt = expiresAtStr,
                        message = "Muda wa leseni hii ya (${licenseType.name}) umekwisha tarehe $expiresAtStr."
                    )
                }
            }

            return VerificationResult(
                valid = true,
                licenseType = licenseType,
                expiresAt = expiresAtStr,
                isLifetime = isLifetime,
                customerName = customer,
                licenseId = licenseId,
                message = "Leseni Imethibitishwa Kikamilifu! ($typeStr)"
            )
        }

        // 3. Legacy Fallback Format (AGRI-M-xxxx)
        val upperClean = clean.uppercase(Locale.ROOT)
        val parts = upperClean.split("-")
        if (parts.size >= 4 && parts[0] == "AGRI") {
            val seg1 = parts[1]
            val typeLetter = if (seg1.isNotEmpty()) seg1[0].toString() else "L"
            val devSegment = if (seg1.length > 1) seg1.substring(1) else "UNIV"
            val expiryDaysCode = parts[2]

            val licenseType = when (typeLetter) {
                "M" -> LicenseType.MONTHLY
                "Q" -> LicenseType.QUARTERLY
                "S", "H", "6" -> LicenseType.SEMI_ANNUAL
                "Y" -> LicenseType.YEARLY
                "L" -> LicenseType.LIFETIME
                else -> LicenseType.LIFETIME
            }

            val currentShortCode = getDeviceShortCode(currentInstallationId)
            val legacyShortCode = legacyInstallationId?.let { getDeviceShortCode(it) }
            val isShortMatch = devSegment == "UNIV" ||
                    devSegment.equals(currentShortCode, ignoreCase = true) ||
                    (legacyShortCode != null && devSegment.equals(legacyShortCode, ignoreCase = true))
            if (!isShortMatch) {
                return VerificationResult(
                    valid = false,
                    message = "Code hii ya leseni imefungwa kwenye kifaa ($devSegment). Kifaa chako ni: $currentShortCode ($currentInstallationId)."
                )
            }

            var expiresAtStr: String? = null
            if (typeLetter != "L" && expiryDaysCode != "9999") {
                try {
                    val daysSinceEpoch = expiryDaysCode.toLong(36)
                    val expiryMs = daysSinceEpoch * 86400000L
                    expiresAtStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(expiryMs))
                    if (System.currentTimeMillis() > expiryMs) {
                        return VerificationResult(
                            valid = false,
                            message = "Muda wa leseni hii ya (${licenseType.name}) umekwisha tarehe $expiresAtStr."
                        )
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            return VerificationResult(
                valid = true,
                licenseType = licenseType,
                expiresAt = expiresAtStr,
                isLifetime = typeLetter == "L",
                message = "Leseni Imethibitishwa Kikamilifu!"
            )
        }

        return VerificationResult(valid = false, message = "Muundo wa code ya leseni si sahihi.")
    }

    fun checkTimeTampering(prefs: SharedPreferences): Boolean {
        val now = System.currentTimeMillis()
        val lastKnown = prefs.getLong("agritech_last_known_time", 0L)
        if (lastKnown > 0 && now < (lastKnown - 3600000L)) {
            prefs.edit().putBoolean("agritech_clock_tampered", true).apply()
            return true
        }
        prefs.edit().putLong("agritech_last_known_time", now).apply()
        return prefs.getBoolean("agritech_clock_tampered", false)
    }

    fun setTrialDates(prefs: SharedPreferences, startMs: Long) {
        prefs.edit()
            .putLong("agritech_trial_start", startMs)
            .putLong("agritech_last_known_time", startMs)
            .apply()
    }

    fun getTrialStartDate(prefs: SharedPreferences): Long {
        var start = prefs.getLong("agritech_trial_start", 0L)
        if (start == 0L) {
            start = System.currentTimeMillis()
            prefs.edit().putLong("agritech_trial_start", start).apply()
            getInstallationId(prefs)
            prefs.edit().putLong("agritech_last_known_time", start).apply()
        }
        return start
    }

    fun getLicenseInfo(prefs: SharedPreferences): LicenseInfo {
        val isTampered = checkTimeTampering(prefs)
        val installationId = getInstallationId(prefs)
        val trialStartMs = getTrialStartDate(prefs)
        val trialStartStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(trialStartMs))

        val now = System.currentTimeMillis()
        val diffDays = ((now - trialStartMs) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        val trialDaysRemaining = (TRIAL_DAYS - diffDays).coerceAtLeast(0)

        val licenseKey = prefs.getString("agritech_license_key", "") ?: ""
        val savedType = prefs.getString("agritech_license_type", null)
        val expiresAt = prefs.getString("agritech_license_expires", null)
        val customerName = prefs.getString("agritech_license_customer", null)
        val activatedAt = prefs.getString("agritech_license_activated_at", null)

        if (licenseKey.isNotBlank()) {
            val legacyId = prefs.getString("agritech_installation_id_legacy", null)
            val verification = verifyActivationCode(licenseKey, installationId, legacyId)
            if (verification.valid) {
                if (!expiresAt.isNullOrBlank()) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val expiryDate = sdf.parse(expiresAt)
                        val expiryMs = expiryDate?.time ?: 0L
                        if (expiryMs > 0 && now > expiryMs) {
                            return LicenseInfo(
                                status = LicenseStatus.LICENSE_EXPIRED,
                                installationId = installationId,
                                trialStartDate = trialStartStr,
                                trialDaysUsed = diffDays,
                                trialDaysRemaining = 0,
                                isLicensed = false,
                                licenseType = verification.licenseType ?: LicenseType.fromCode(savedType),
                                licenseKey = licenseKey,
                                customerName = verification.customerName ?: customerName,
                                activatedAt = activatedAt,
                                expiresAt = expiresAt,
                                daysRemaining = 0,
                                isTampered = isTampered
                            )
                        }

                        val msLeft = expiryMs - now
                        val daysRemaining = (msLeft / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)

                        return LicenseInfo(
                            status = LicenseStatus.ACTIVE,
                            installationId = installationId,
                            trialStartDate = trialStartStr,
                            trialDaysUsed = diffDays,
                            trialDaysRemaining = 0,
                            isLicensed = true,
                            licenseType = verification.licenseType ?: LicenseType.fromCode(savedType),
                            licenseKey = licenseKey,
                            customerName = verification.customerName ?: customerName,
                            activatedAt = activatedAt,
                            expiresAt = expiresAt,
                            daysRemaining = daysRemaining,
                            isTampered = isTampered
                        )
                    } catch (e: Exception) {
                        // ignore and proceed
                    }
                }

                return LicenseInfo(
                    status = LicenseStatus.ACTIVE,
                    installationId = installationId,
                    trialStartDate = trialStartStr,
                    trialDaysUsed = diffDays,
                    trialDaysRemaining = 0,
                    isLicensed = true,
                    licenseType = LicenseType.LIFETIME,
                    licenseKey = licenseKey,
                    customerName = verification.customerName ?: customerName,
                    activatedAt = activatedAt,
                    expiresAt = null,
                    daysRemaining = null,
                    isTampered = isTampered
                )
            }
        }

        if (isTampered) {
            return LicenseInfo(
                status = LicenseStatus.TAMPERED,
                installationId = installationId,
                trialStartDate = trialStartStr,
                trialDaysUsed = diffDays,
                trialDaysRemaining = 0,
                isLicensed = false,
                isTampered = true
            )
        }

        if (trialDaysRemaining > 0) {
            val trialExpiryMs = trialStartMs + (TRIAL_DAYS.toLong() * 86400000L)
            val trialExpiryStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(trialExpiryMs))
            return LicenseInfo(
                status = LicenseStatus.ACTIVE,
                installationId = installationId,
                trialStartDate = trialStartStr,
                trialDaysUsed = diffDays,
                trialDaysRemaining = trialDaysRemaining,
                isLicensed = false,
                licenseType = LicenseType.TRIAL,
                expiresAt = trialExpiryStr,
                daysRemaining = trialDaysRemaining,
                isTampered = false
            )
        }

        return LicenseInfo(
            status = LicenseStatus.TRIAL_EXPIRED,
            installationId = installationId,
            trialStartDate = trialStartStr,
            trialDaysUsed = diffDays,
            trialDaysRemaining = 0,
            isLicensed = false,
            licenseType = LicenseType.TRIAL,
            daysRemaining = 0,
            isTampered = false
        )
    }

    fun activateLicense(
        prefs: SharedPreferences,
        key: String,
        customerName: String? = null
    ): Pair<Boolean, String> {
        val cleanKey = key.trim()
        val installationId = getInstallationId(prefs)
        val legacyId = prefs.getString("agritech_installation_id_legacy", null)
        val result = verifyActivationCode(cleanKey, installationId, legacyId)

        if (result.valid) {
            val editor = prefs.edit()
            editor.putString("agritech_license_key", cleanKey)
            if (result.licenseType != null) {
                editor.putString("agritech_license_type", result.licenseType.code)
            }
            if (!result.expiresAt.isNullOrBlank()) {
                editor.putString("agritech_license_expires", result.expiresAt)
            } else {
                editor.remove("agritech_license_expires")
            }
            val finalCustomer = result.customerName ?: customerName
            if (!finalCustomer.isNullOrBlank()) {
                editor.putString("agritech_license_customer", finalCustomer)
            }
            editor.putString(
                "agritech_license_activated_at",
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            )
            editor.remove("agritech_clock_tampered")
            editor.apply()

            return Pair(true, result.message.ifEmpty { "Leseni Imethibitishwa Kikamilifu!" })
        }

        return Pair(false, result.message.ifEmpty { "Funguo ya leseni si sahihi." })
    }
}
