package com.example.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

object SecurityUtils {

    private val secureRandom = SecureRandom()

    /**
     * Generates a 16-byte random cryptographic salt as a hex string.
     */
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Hashes a password with salt using SHA-256.
     * Never stores plain-text passwords.
     */
    fun hashPassword(password: String, salt: String): String {
        val combined = "$salt:$password"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies if input password matches the stored salted hash.
     */
    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        if (salt.isBlank() || expectedHash.isBlank()) return false
        val computed = hashPassword(password, salt)
        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Generates a secure 6-digit OTP for SMS/Email verification.
     */
    fun generateOtp(): String {
        val otpNum = 100000 + secureRandom.nextInt(900000)
        return otpNum.toString()
    }

    /**
     * Generates an opaque session token.
     */
    fun generateSessionToken(): String {
        return UUID.randomUUID().toString().replace("-", "").lowercase(Locale.ROOT)
    }
}
