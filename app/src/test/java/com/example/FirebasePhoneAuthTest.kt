package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.FirebasePhoneAuthManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebasePhoneAuthTest {

    @Test
    fun testPhoneFormattingToE164() {
        // Standard Tanzanian local format
        assertEquals("+255712345678", FirebasePhoneAuthManager.formatToE164("0712345678"))
        assertEquals("+255688123456", FirebasePhoneAuthManager.formatToE164("0688123456"))
        
        // Already formatted with country code
        assertEquals("+255712345678", FirebasePhoneAuthManager.formatToE164("255712345678"))
        assertEquals("+255712345678", FirebasePhoneAuthManager.formatToE164("+255712345678"))
        
        // Formatted with spaces or dashes
        assertEquals("+255712345678", FirebasePhoneAuthManager.formatToE164("0712 345 678"))
        assertEquals("+255712345678", FirebasePhoneAuthManager.formatToE164("0712-345-678"))
        
        // Other international formats
        assertEquals("+254712345678", FirebasePhoneAuthManager.formatToE164("+254712345678"))
    }

    @Test
    fun testDiagnosticsReportsStatusCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diag = FirebasePhoneAuthManager.getDiagnostics(context)

        // Verifies package name and SHA fingerprints are strictly matching
        assertEquals("com.aistudio.agritechhub.epzn", diag.applicationId)
        assertEquals(FirebasePhoneAuthManager.DEBUG_SHA1, diag.sha1)
        assertEquals(FirebasePhoneAuthManager.DEBUG_SHA256, diag.sha256)
        assertNotNull(diag.statusMessage)
    }

    @Test
    fun testOtpValidationRejectsInvalidLength() {
        var failed = false
        var errorMsg = ""
        FirebasePhoneAuthManager.verifySmsCode(
            verificationId = "fake-id",
            otpCode = "123", // only 3 digits
            onSuccess = {},
            onError = { msg ->
                failed = true
                errorMsg = msg
            }
        )
        assertTrue(failed)
        assertTrue(errorMsg.contains("6"))
    }
}
