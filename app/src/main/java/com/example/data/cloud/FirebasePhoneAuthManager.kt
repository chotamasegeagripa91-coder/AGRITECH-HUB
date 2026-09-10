package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * End-to-end Firebase Phone Authentication and Verification Manager for AGRITECH HUB.
 *
 * Handles:
 * 1. Firebase Initialization and project metadata verification (google-services.json detection).
 * 2. E.164 standard international phone number formatting (e.g. +255...).
 * 3. Real SMS OTP dispatch through Firebase PhoneAuthProvider.
 * 4. Resending SMS OTP using ForceResendingToken.
 * 5. Credential verification and Firebase Auth sign-in.
 */
object FirebasePhoneAuthManager {

    private const val TAG = "FirebasePhoneAuth"

    // App signing fingerprints for Firebase Console registration
    const val DEBUG_SHA1 = "65:37:9D:52:0A:F3:51:AC:35:3B:C9:86:37:A1:1B:BB:1C:4E:0F:24"
    const val DEBUG_SHA256 = "41:33:0A:24:4E:4A:49:F2:73:69:2A:E8:2C:3E:BF:08:A6:54:A3:03:EF:9D:D3:4C:36:FE:FB:44:69:5E:C0:27"
    const val PACKAGE_NAME = "com.aistudio.agritechhub.epzn"

    data class FirebaseDiagnostics(
        val isConnected: Boolean,
        val isGoogleServicesPresent: Boolean,
        val projectId: String?,
        val applicationId: String,
        val sha1: String,
        val sha256: String,
        val statusMessage: String
    )

    /**
     * Checks if Firebase is initialized and connected with valid project credentials.
     */
    fun getDiagnostics(context: Context): FirebaseDiagnostics {
        val isInitialized = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }

        val app = if (isInitialized) {
            try {
                FirebaseApp.getInstance()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val projectId = app?.options?.projectId
        val hasProjectId = !projectId.isNullOrBlank() && projectId != "test-project"

        val statusMessage = if (hasProjectId) {
            "Firebase imeunganishwa kikamilifu na Mradi wa: $projectId"
        } else {
            "Faili la 'google-services.json' halipatikani kwenye /app. Ili kutuma SMS za OTP kwa namba halisi, weka faili hilo kutoka Firebase Console na uwashe Phone Auth."
        }

        return FirebaseDiagnostics(
            isConnected = hasProjectId,
            isGoogleServicesPresent = hasProjectId,
            projectId = projectId,
            applicationId = PACKAGE_NAME,
            sha1 = DEBUG_SHA1,
            sha256 = DEBUG_SHA256,
            statusMessage = statusMessage
        )
    }

    /**
     * Formats input phone number to standard E.164 international format (+255...).
     * Supports Tanzanian numbers (07xx, 06xx, 255xx) and any other international format.
     */
    fun formatToE164(phone: String): String {
        var clean = phone.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        if (clean.startsWith("+")) {
            return clean
        }
        if (clean.startsWith("00")) {
            return "+" + clean.substring(2)
        }
        if (clean.startsWith("255")) {
            return "+$clean"
        }
        if (clean.startsWith("0")) {
            return "+255" + clean.substring(1)
        }
        // If 9 digits starting with 7 or 6
        if (clean.length == 9 && (clean.startsWith("7") || clean.startsWith("6"))) {
            return "+255$clean"
        }
        return "+$clean"
    }

    /**
     * Dispatches real SMS OTP through Firebase PhoneAuthProvider.
     */
    fun sendVerificationCode(
        activity: Activity,
        rawPhoneNumber: String,
        forceResendingToken: PhoneAuthProvider.ForceResendingToken? = null,
        onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
        onVerificationCompleted: (credential: PhoneAuthCredential) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val diagnostics = getDiagnostics(activity)
        if (!diagnostics.isConnected) {
            Log.e(TAG, "Cannot send SMS: Firebase is not connected (missing google-services.json)")
            onError(
                "Firebase haijaunganishwa: Faili la 'google-services.json' halipo kwenye mradi huu. " +
                "Ili kupokea SMS ya OTP kwenye simu, weka google-services.json yenye kifurushi $PACKAGE_NAME na uwashe Phone Auth kwenye Firebase Console."
            )
            return
        }

        val formattedPhone = formatToE164(rawPhoneNumber)
        Log.i(TAG, "Dispatching Firebase Phone Auth SMS to: $formattedPhone")

        val auth = FirebaseAuth.getInstance()
        auth.setLanguageCode("sw") // Default Swahili for SMS notifications, falls back automatically

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.i(TAG, "Phone verification automatically completed / instant verification.")
                onVerificationCompleted(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "Firebase Phone verification failed: ${e.message}", e)
                val friendlyMessage = when {
                    e is FirebaseAuthInvalidCredentialsException ->
                        "Namba ya simu ($formattedPhone) si sahihi au imekataliwa na mtandao."
                    e.message?.contains("quota", ignoreCase = true) == true ->
                        "Kikomo cha SMS cha Firebase kimefikiwa (SMS Quota Exceeded). Tafadhali jaribu tena baadae."
                    e.message?.contains("app-not-authorized", ignoreCase = true) == true ||
                    e.message?.contains("fingerprint", ignoreCase = true) == true ->
                        "Programu haijaidhinishwa kwenye Firebase Console: Hakikisha umeweka SHA-1 ($DEBUG_SHA1) na kuwasha Phone Auth."
                    else ->
                        "Hitilafu ya Firebase: ${e.localizedMessage ?: e.message ?: "Haikuweza kutuma SMS."}"
                }
                onError(friendlyMessage)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.i(TAG, "Firebase SMS OTP dispatched successfully. VerificationId: $verificationId")
                onCodeSent(verificationId, token)
            }
        }

        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (forceResendingToken != null) {
            optionsBuilder.setForceResendingToken(forceResendingToken)
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    /**
     * Verifies the SMS OTP entered by user against the Firebase verificationId.
     */
    fun verifySmsCode(
        verificationId: String,
        otpCode: String,
        onSuccess: (credential: PhoneAuthCredential) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val cleanOtp = otpCode.trim()
        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            onError("Msimbo wa OTP lazima uwe na tarakimu 6 (6-digit number).")
            return
        }

        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, cleanOtp)
            val auth = FirebaseAuth.getInstance()
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    Log.i(TAG, "Firebase Auth sign-in successful for user: ${it.user?.uid}")
                    onSuccess(credential)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to verify credential: ${e.message}", e)
                    val msg = if (e is FirebaseAuthInvalidCredentialsException) {
                        "Msimbo wa OTP ulioingiza si sahihi au umekwisha muda wake."
                    } else {
                        "Uthibitisho umeshindwa: ${e.localizedMessage ?: e.message}"
                    }
                    onError(msg)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating credential: ${e.message}", e)
            onError("Hitilafu ya uthibitisho: ${e.message}")
        }
    }
}
