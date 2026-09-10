package com.example

import com.example.data.cloud.FirebaseAuthManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseAuthManagerTest {

    @Test
    fun testBillingAndOperationErrorsAreSanitized() {
        val billingException = RuntimeException("BILLING_NOT_ENABLED: This project requires billing")
        val friendlyEn = FirebaseAuthManager.formatFriendlyError(billingException, "en")
        val friendlySw = FirebaseAuthManager.formatFriendlyError(billingException, "sw")

        // Must NEVER contain technical billing terms
        assertFalse(friendlyEn.contains("BILLING", ignoreCase = true))
        assertFalse(friendlyEn.contains("NOT_ENABLED", ignoreCase = true))
        assertFalse(friendlySw.contains("BILLING", ignoreCase = true))
        assertTrue(friendlyEn.contains("Authentication service", ignoreCase = true))

        val notAllowedException = RuntimeException("This operation is not allowed (CONFIGURATION_NOT_ALLOWED)")
        val notAllowedEn = FirebaseAuthManager.formatFriendlyError(notAllowedException, "en")
        assertFalse(notAllowedEn.contains("operation is not allowed", ignoreCase = true))
    }

    @Test
    fun testWeakPasswordAndValidationErrors() {
        val weakPassException = RuntimeException("WEAK_PASSWORD : Password should be at least 6 characters")
        val errorEn = FirebaseAuthManager.formatFriendlyError(weakPassException, "en")
        val errorSw = FirebaseAuthManager.formatFriendlyError(weakPassException, "sw")

        assertTrue(errorEn.contains("6 characters", ignoreCase = true))
        assertTrue(errorSw.contains("6", ignoreCase = true))
    }

    @Test
    fun testCreateUserRejectsInvalidInputsLocally() {
        var callbackCalled = false
        FirebaseAuthManager.createUserWithEmail(
            email = "invalid-email",
            password = "123",
            language = "en"
        ) { result ->
            callbackCalled = true
            assertFalse(result.success)
            assertNotNull(result.errorMessage)
            assertTrue(result.errorMessage!!.contains("valid email", ignoreCase = true))
        }
        assertTrue(callbackCalled)

        callbackCalled = false
        FirebaseAuthManager.createUserWithEmail(
            email = "user@example.com",
            password = "123", // less than 6 chars
            language = "en"
        ) { result ->
            callbackCalled = true
            assertFalse(result.success)
            assertTrue(result.errorMessage!!.contains("6 characters", ignoreCase = true))
        }
        assertTrue(callbackCalled)
    }

    @Test
    fun testSignInRejectsBlankInputsLocally() {
        var callbackCalled = false
        FirebaseAuthManager.signInWithEmail(
            email = "",
            password = "password123",
            language = "en"
        ) { result ->
            callbackCalled = true
            assertFalse(result.success)
            assertTrue(result.errorMessage!!.contains("email", ignoreCase = true))
        }
        assertTrue(callbackCalled)
    }

    @Test
    fun testPasswordResetRejectsInvalidEmailLocally() {
        var callbackCalled = false
        FirebaseAuthManager.sendPasswordReset(
            email = "bademail",
            language = "en"
        ) { result ->
            callbackCalled = true
            assertFalse(result.success)
            assertTrue(result.message.contains("valid email", ignoreCase = true))
        }
        assertTrue(callbackCalled)
    }
}
