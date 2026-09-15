package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import android.view.autofill.AutofillManager
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase Authentication Manager for AGRITECH HUB.
 * Implements modern, secure Email & Password authentication, Google Sign-In
 * via Android Credential Manager, and Password Reset via Firebase.
 *
 * Strictly avoids paid SMS/Phone Auth APIs and handles all errors with friendly,
 * user-facing messages. Technical or billing errors are never displayed to users.
 */
object FirebaseAuthManager {

    private const val TAG = "FirebaseAuthManager"
    private const val WEB_CLIENT_ID_FALLBACK = "357548839727-r8srt8cp5v35o3nbdpq19t708eb03l7e.apps.googleusercontent.com"

    data class AuthResult(
        val success: Boolean,
        val uid: String? = null,
        val email: String? = null,
        val errorMessage: String? = null
    )

    data class GoogleAuthResult(
        val success: Boolean,
        val uid: String? = null,
        val email: String? = null,
        val displayName: String? = null,
        val photoUrl: String? = null,
        val isCancelled: Boolean = false,
        val errorMessage: String? = null
    )

    data class ResetResult(
        val success: Boolean,
        val message: String
    )

    data class PasswordCredentialResult(
        val success: Boolean,
        val username: String? = null,
        val password: String? = null,
        val isCancelled: Boolean = false,
        val errorMessage: String? = null
    )

    /**
     * Resolves the Web Client ID configured in Firebase (from google-services.json).
     */
    fun getServerClientId(context: Context): String {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val value = context.getString(resId)
                if (value.isNotBlank()) value else WEB_CLIENT_ID_FALLBACK
            } else {
                WEB_CLIENT_ID_FALLBACK
            }
        } catch (e: Exception) {
            WEB_CLIENT_ID_FALLBACK
        }
    }

    /**
     * Unwraps Context to resolve an Activity if wrapped in ContextWrapper.
     */
    fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    /**
     * Checks if Firebase is initialized in this runtime environment.
     */
    fun isFirebaseAvailable(context: Context): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Registers a new user account with Email and Password using Firebase Authentication.
     */
    fun createUserWithEmail(
        email: String,
        password: String,
        language: String = "en",
        onResult: (AuthResult) -> Unit
    ) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            onResult(
                AuthResult(
                    success = false,
                    errorMessage = if (language == "sw") "Tafadhali weka barua pepe halali." else "Please enter a valid email address."
                )
            )
            return
        }

        if (cleanPassword.length < 6) {
            onResult(
                AuthResult(
                    success = false,
                    errorMessage = if (language == "sw") "Nenosiri lazima liwe na angalau herufi 6." else "Password must be at least 6 characters."
                )
            )
            return
        }

        try {
            val auth = FirebaseAuth.getInstance()
            auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        Log.d(TAG, "Firebase user created successfully: ${user?.uid}")
                        onResult(
                            AuthResult(
                                success = true,
                                uid = user?.uid,
                                email = user?.email ?: cleanEmail
                            )
                        )
                    } else {
                        val exception = task.exception
                        Log.w(TAG, "Firebase user creation failed", exception)
                        val friendlyMessage = formatFriendlyError(exception, language)
                        onResult(
                            AuthResult(
                                success = false,
                                errorMessage = friendlyMessage
                            )
                        )
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Firebase createUser", e)
            val friendly = formatFriendlyError(e, language)
            onResult(
                AuthResult(
                    success = false,
                    errorMessage = friendly
                )
            )
        }
    }

    /**
     * Signs in an existing user with Email and Password using Firebase Authentication.
     */
    fun signInWithEmail(
        email: String,
        password: String,
        language: String = "en",
        onResult: (AuthResult) -> Unit
    ) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        if (cleanEmail.isBlank()) {
            onResult(
                AuthResult(
                    success = false,
                    errorMessage = if (language == "sw") "Tafadhali weka barua pepe yako." else "Please enter your email address."
                )
            )
            return
        }

        if (cleanPassword.isBlank()) {
            onResult(
                AuthResult(
                    success = false,
                    errorMessage = if (language == "sw") "Tafadhali weka nenosiri lako." else "Please enter your password."
                )
            )
            return
        }

        try {
            val auth = FirebaseAuth.getInstance()
            auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        Log.d(TAG, "Firebase user logged in: ${user?.uid}")
                        onResult(
                            AuthResult(
                                success = true,
                                uid = user?.uid,
                                email = user?.email ?: cleanEmail
                            )
                        )
                    } else {
                        val exception = task.exception
                        Log.w(TAG, "Firebase login failed", exception)
                        val friendlyMessage = formatFriendlyError(exception, language)
                        onResult(
                            AuthResult(
                                success = false,
                                errorMessage = friendlyMessage
                            )
                        )
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Firebase signIn", e)
            val friendly = formatFriendlyError(e, language)
            onResult(
                AuthResult(
                    success = false,
                    errorMessage = friendly
                )
            )
        }
    }

    /**
     * Dispatches a password reset email to the user's registered email address.
     * Does NOT require the old password or SMS OTP.
     */
    fun sendPasswordReset(
        email: String,
        language: String = "en",
        onResult: (ResetResult) -> Unit
    ) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            onResult(
                ResetResult(
                    success = false,
                    message = if (language == "sw") "Tafadhali weka barua pepe halali." else "Please enter a valid email address."
                )
            )
            return
        }

        try {
            val auth = FirebaseAuth.getInstance()
            auth.sendPasswordResetEmail(cleanEmail)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Password reset email sent to $cleanEmail")
                        val successMessage = if (language == "sw")
                            "Kiungo cha kuweka upya nenosiri kimetumwa kwenye barua pepe: $cleanEmail. Tafadhali fungua kikasha chako."
                        else
                            "A password reset link has been sent to $cleanEmail. Please check your inbox to reset your password."
                        onResult(ResetResult(success = true, message = successMessage))
                    } else {
                        val exception = task.exception
                        Log.w(TAG, "Failed to send password reset email", exception)
                        val friendly = formatFriendlyError(exception, language)
                        onResult(ResetResult(success = false, message = friendly))
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending password reset", e)
            val friendly = formatFriendlyError(e, language)
            onResult(ResetResult(success = false, message = friendly))
        }
    }

    /**
     * Changes the password for the currently signed-in Firebase user directly without a reset link.
     */
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        language: String = "sw",
        context: Context? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanCurrent = currentPassword.trim()
        val cleanNew = newPassword.trim()

        if (cleanNew.length < 6) {
            return@withContext AuthResult(
                success = false,
                errorMessage = if (language == "sw") "Nenosiri jipya lazima liwe na angalau herufi 6." else "New password must be at least 6 characters."
            )
        }

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            return@withContext AuthResult(
                success = false,
                errorMessage = if (language == "sw") "Hakuna akaunti iliyoingia kwenye mfumo." else "No user account currently signed in."
            )
        }

        try {
            val email = currentUser.email
            if (!email.isNullOrBlank() && cleanCurrent.isNotBlank()) {
                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, cleanCurrent)
                com.google.android.gms.tasks.Tasks.await(currentUser.reauthenticate(credential), 10, java.util.concurrent.TimeUnit.SECONDS)
            }

            com.google.android.gms.tasks.Tasks.await(currentUser.updatePassword(cleanNew), 10, java.util.concurrent.TimeUnit.SECONDS)
            Log.d(TAG, "Password changed successfully for user: ${currentUser.uid}")

            if (context != null && !email.isNullOrBlank()) {
                try {
                    savePasswordCredential(context, email, cleanNew, forceUpdate = true)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update saved password in Credential Manager: ${e.message}")
                }
            }

            AuthResult(success = true, uid = currentUser.uid, email = currentUser.email)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to change password", e)
            val friendly = formatFriendlyError(e, language)
            AuthResult(success = false, errorMessage = friendly)
        }
    }

    /**
     * Signs in with Google using modern Android Credential Manager and Firebase Authentication.
     * Launches the official system account chooser, obtains Google ID token, converts to
     * Firebase Google credential via GoogleAuthProvider, and signs into Firebase.
     *
     * Automatically creates the Firebase account if it does not yet exist.
     * Requires ZERO SMS/phone verification and ZERO Firebase billing.
     */
    suspend fun signInWithGoogle(
        context: Context,
        language: String = "en"
    ): GoogleAuthResult {
        val activityContext = findActivity(context) ?: context
        val credentialManager = CredentialManager.create(activityContext)
        val serverClientId = getServerClientId(activityContext)

        val googleIdOption = try {
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build GetGoogleIdOption", e)
            val friendly = formatFriendlyError(e, language)
            return GoogleAuthResult(success = false, errorMessage = friendly)
        }

        val request = try {
            GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build GetCredentialRequest", e)
            val friendly = formatFriendlyError(e, language)
            return GoogleAuthResult(success = false, errorMessage = friendly)
        }

        val response = try {
            credentialManager.getCredential(
                request = request,
                context = activityContext
            )
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google account selection was cancelled by user")
            return GoogleAuthResult(success = false, isCancelled = true)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager error during Google Sign-In: ${e.message}", e)
            val friendly = formatFriendlyError(e, language)
            return GoogleAuthResult(success = false, errorMessage = friendly)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during Google Sign-In Credential Manager call", e)
            val friendly = formatFriendlyError(e, language)
            return GoogleAuthResult(success = false, errorMessage = friendly)
        }

        val credential = response.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing GoogleIdTokenCredential", e)
                return GoogleAuthResult(
                    success = false,
                    errorMessage = if (language == "sw") "Hitilafu ya kusoma taarifa za akaunti ya Google." else "Failed to parse Google credentials."
                )
            }

            val idToken = googleIdTokenCredential.idToken
            val googleEmail = googleIdTokenCredential.id
            val displayName = googleIdTokenCredential.displayName
            val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()

            if (idToken.isBlank()) {
                return GoogleAuthResult(
                    success = false,
                    errorMessage = if (language == "sw") "Haikuweza kupata tokeni ya Google." else "Could not obtain Google ID token."
                )
            }

            return try {
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val user = suspendCancellableCoroutine<FirebaseUser> { continuation ->
                    FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                        .addOnSuccessListener { authResult ->
                            val firebaseUser = authResult.user
                            if (firebaseUser != null) {
                                continuation.resume(firebaseUser)
                            } else {
                                continuation.resumeWithException(IllegalStateException("FirebaseUser is null"))
                            }
                        }
                        .addOnFailureListener { exception ->
                            continuation.resumeWithException(exception)
                        }
                }

                Log.d(TAG, "Firebase Google sign-in successful: uid=${user.uid}, email=${user.email}")
                GoogleAuthResult(
                    success = true,
                    uid = user.uid,
                    email = user.email ?: googleEmail,
                    displayName = user.displayName ?: displayName,
                    photoUrl = user.photoUrl?.toString() ?: photoUrl
                )
            } catch (e: Exception) {
                Log.e(TAG, "Firebase signInWithCredential failed", e)
                val friendly = formatFriendlyError(e, language)
                GoogleAuthResult(
                    success = false,
                    errorMessage = friendly
                )
            }
        } else {
            Log.w(TAG, "Received unexpected credential type: ${credential::class.java.name}, type: ${credential.type}")
            return GoogleAuthResult(
                success = false,
                errorMessage = if (language == "sw") "Akaunti ya Google haikuchaguliwa." else "Google account was not selected."
            )
        }
    }

    private var lastSavedUsername: String? = null
    private var lastSavedTime: Long = 0L

    /**
     * Prompts the Android Credential Manager / Google Password Manager to save or update
     * the user's password credentials securely.
     * Never logs or stores the raw password in app storage.
     */
    suspend fun savePasswordCredential(
        context: Context,
        username: String,
        password: String,
        forceUpdate: Boolean = false
    ): Boolean {
        val cleanUser = username.trim().lowercase()
        val cleanPass = password.trim()
        if (cleanUser.isBlank() || cleanPass.isBlank()) return false

        // Prevent duplicate prompts within 10 seconds for the same user account unless forced
        if (!forceUpdate && cleanUser.equals(lastSavedUsername, ignoreCase = true) &&
            (System.currentTimeMillis() - lastSavedTime) < 10_000L
        ) {
            Log.d(TAG, "Skipping duplicate credential save request within debounce window")
            return true
        }

        return withContext(Dispatchers.Main) {
            val activity = findActivity(context) ?: (context as? Activity)
            if (activity == null) {
                Log.w(TAG, "Cannot save credentials: Context is not an Activity")
                return@withContext false
            }

            try {
                Log.d(TAG, "Requesting Credential Manager to save password credentials for user: $cleanUser")
                val credentialManager = CredentialManager.create(activity)
                val request = CreatePasswordRequest(
                    id = cleanUser,
                    password = cleanPass
                )
                credentialManager.createCredential(
                    context = activity,
                    request = request
                )
                lastSavedUsername = cleanUser
                lastSavedTime = System.currentTimeMillis()
                Log.d(TAG, "Password credentials saved/updated successfully in system Credential Manager")

                // Android Credential Manager is the modern and preferred way to save credentials.
                // We intentionally do NOT call legacy AutofillManager.commit() here, as calling both
                // causes Google Password Manager to create duplicate credential entries.

                true
            } catch (e: CreateCredentialCancellationException) {
                Log.d(TAG, "User declined saving password credentials")
                false
            } catch (e: Exception) {
                Log.w(TAG, "Could not save credentials to system Credential Manager: ${e.javaClass.simpleName} - ${e.message}")
                false
            }
        }
    }

    /**
     * Retrieves saved password credentials from the device Credential Manager (including Google Password Manager).
     * If user selects a saved account, returns the username and password so the app can auto-fill.
     * If no credentials exist or the prompt is cancelled/dismissed, handles it silently without user-facing errors.
     */
    suspend fun getSavedPasswordCredential(
        context: Context,
        language: String = "en"
    ): PasswordCredentialResult {
        return withContext(Dispatchers.Main) {
            val activity = findActivity(context) ?: (context as? Activity)
            if (activity == null) {
                Log.w(TAG, "Cannot get saved credentials: Context is not an Activity")
                return@withContext PasswordCredentialResult(success = false)
            }

            try {
                Log.d(TAG, "Requesting saved credentials from system Credential Manager")
                val credentialManager = CredentialManager.create(activity)
                val passwordOption = GetPasswordOption()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(passwordOption)
                    .build()

                val response = credentialManager.getCredential(
                    context = activity,
                    request = request
                )
                val credential = response.credential
                if (credential is PasswordCredential) {
                    Log.d(TAG, "Successfully retrieved PasswordCredential for ${credential.id}")
                    PasswordCredentialResult(
                        success = true,
                        username = credential.id,
                        password = credential.password
                    )
                } else {
                    Log.d(TAG, "Credential received was not a PasswordCredential: ${credential::class.java.name}")
                    PasswordCredentialResult(success = false)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.d(TAG, "Password credential retrieval was cancelled by user")
                PasswordCredentialResult(success = false, isCancelled = true)
            } catch (e: Exception) {
                Log.d(TAG, "No saved password credentials available or error: ${e.javaClass.simpleName}")
                PasswordCredentialResult(success = false)
            }
        }
    }

    /**
     * Converts technical Firebase and Credential Manager errors into clean, friendly human messages.
     * Accurately recognizes email collision, incorrect/expired credentials, weak passwords,
     * reCAPTCHA validation failures, and network timeouts.
     */
    fun formatFriendlyError(e: Throwable?, language: String): String {
        if (e == null) {
            return if (language == "sw") "Hitilafu imetokea. Tafadhali jaribu tena." else "An error occurred. Please try again."
        }

        val name = e.javaClass.simpleName
        val msg = buildString {
            append(e.message ?: "")
            append(" ")
            append(e.cause?.message ?: "")
            if (e is com.google.firebase.auth.FirebaseAuthException) {
                append(" ")
                append(e.errorCode)
            }
        }

        return when {
            // 1. Email Already Exists (User Collision)
            e is FirebaseAuthUserCollisionException ||
                    name.contains("UserCollision", ignoreCase = true) ||
                    msg.contains("already in use", ignoreCase = true) ||
                    msg.contains("already exists", ignoreCase = true) ||
                    msg.contains("EMAIL_EXISTS", ignoreCase = true) ||
                    msg.contains("EMAIL_ALREADY_IN_USE", ignoreCase = true) ||
                    msg.contains("ERROR_EMAIL_ALREADY_IN_USE", ignoreCase = true) -> {
                if (language == "sw")
                    "Akaunti yenye barua pepe hii tayari ipo. Tafadhali chagua Kuingia (Login) au weka upya nenosiri kama umelisahau."
                else
                    "An account with this email address already exists. Please choose Login or reset your password."
            }

            // 2. Incorrect Credentials / Wrong Password / Expired
            e is FirebaseAuthInvalidCredentialsException ||
                    name.contains("InvalidCredentials", ignoreCase = true) ||
                    msg.contains("supplied auth credential is incorrect", ignoreCase = true) ||
                    msg.contains("incorrect, malformed or has expired", ignoreCase = true) ||
                    msg.contains("malformed or has expired", ignoreCase = true) ||
                    msg.contains("credential is incorrect", ignoreCase = true) ||
                    msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                    msg.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
                    msg.contains("WRONG_PASSWORD", ignoreCase = true) ||
                    msg.contains("wrong password", ignoreCase = true) ||
                    msg.contains("ERROR_WRONG_PASSWORD", ignoreCase = true) ||
                    msg.contains("ERROR_INVALID_CREDENTIAL", ignoreCase = true) -> {
                if (language == "sw")
                    "Barua pepe au nenosiri si sahihi (au akaunti ilisajiliwa kwa Google). Bonyeza 'Umesahau nenosiri?' kuweka jipya, au tumia 'Endelea na Google'."
                else
                    "Incorrect email or password (or account was registered with Google). Tap 'Forgot password?' to reset, or use 'Continue with Google'."
            }

            // 3. User Not Found
            e is FirebaseAuthInvalidUserException ||
                    name.contains("InvalidUser", ignoreCase = true) ||
                    msg.contains("USER_NOT_FOUND", ignoreCase = true) ||
                    msg.contains("user-not-found", ignoreCase = true) ||
                    msg.contains("no user record", ignoreCase = true) ||
                    msg.contains("ERROR_USER_NOT_FOUND", ignoreCase = true) -> {
                if (language == "sw")
                    "Hakuna akaunti yenye barua pepe hii. Tafadhali fungua akaunti mpya kwa kubonyeza 'Jisajili'."
                else
                    "No account found with this email. Please create an account by tapping 'Sign Up'."
            }

            // 4. Weak Password
            e is FirebaseAuthWeakPasswordException ||
                    name.contains("WeakPassword", ignoreCase = true) ||
                    msg.contains("WEAK_PASSWORD", ignoreCase = true) ||
                    msg.contains("Password should be at least", ignoreCase = true) ||
                    msg.contains("ERROR_WEAK_PASSWORD", ignoreCase = true) -> {
                if (language == "sw")
                    "Nenosiri ni dhaifu mno. Tafadhali tumia nenosiri lenye angalau herufi au namba 6."
                else
                    "Password is too weak. Please use at least 6 characters."
            }

            // 5. Rate Limiting / Too Many Attempts
            msg.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) ||
                    msg.contains("too many requests", ignoreCase = true) ||
                    msg.contains("temporarily blocked", ignoreCase = true) ||
                    msg.contains("blocked all requests", ignoreCase = true) ||
                    msg.contains("unusual activity", ignoreCase = true) -> {
                if (language == "sw")
                    "Majaribio mengi mno yasiyo sahihi. Akaunti imezuiwa kwa muda mfupi kwa usalama. Tafadhali subiri dakika chache au weka upya nenosiri."
                else
                    "Too many failed attempts. Access temporarily disabled for security. Please wait a few minutes or reset your password."
            }

            // 6. Security / Recaptcha Verification
            name.contains("Recaptcha", ignoreCase = true) ||
                    msg.contains("Recaptcha", ignoreCase = true) ||
                    msg.contains("safety", ignoreCase = true) ||
                    msg.contains("appcheck", ignoreCase = true) -> {
                if (language == "sw")
                    "Hitilafu ya uthibitishaji wa usalama. Tafadhali hakiki muunganisho wa intaneti na ujaribu tena."
                else
                    "Security verification check failed. Please check your internet connection and try again."
            }

            // 7. Google Credential Manager
            name.contains("NoCredential", ignoreCase = true) ||
                    msg.contains("No credential available", ignoreCase = true) -> {
                if (language == "sw")
                    "Hakuna akaunti ya Google iliyopatikana kwenye kifaa hiki. Tafadhali weka akaunti ya Google kwenye simu yako."
                else
                    "No Google account found on this device. Please ensure a Google account is added to your device settings."
            }

            name.contains("Cancellation", ignoreCase = true) -> {
                if (language == "sw")
                    "Uchaguzi wa akaunti umesitishwa."
                else
                    "Account selection was cancelled."
            }

            name.contains("ProviderConfiguration", ignoreCase = true) ||
                    name.contains("Unsupported", ignoreCase = true) -> {
                if (language == "sw")
                    "Huduma za Google Play hazijakamilika kwenye kifaa hiki."
                else
                    "Google Play services is not properly configured on this device."
            }

            // 8. Network Connection
            name.contains("Network", ignoreCase = true) ||
                    msg.contains("network", ignoreCase = true) ||
                    msg.contains("connection", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("unreachable", ignoreCase = true) -> {
                if (language == "sw")
                    "Hitilafu ya mtandao. Tafadhali hakiki muunganisho wako wa intaneti na ujaribu tena."
                else
                    "Network connection problem. Please check your internet connection and try again."
            }

            // 9. Filter out billing or raw internal errors
            msg.contains("BILLING", ignoreCase = true) ||
                    msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
                    msg.contains("not allowed", ignoreCase = true) ||
                    msg.contains("SMS", ignoreCase = true) -> {
                if (language == "sw")
                    "Hitilafu ya huduma ya uthibitishaji. Tafadhali jaribu tena baada ya muda mfupi au tumia Google."
                else
                    "Authentication service error. Please try again in a moment or use Google Sign-In."
            }

            else -> {
                if (language == "sw")
                    "Imeshindikana kukamilisha ombi. Tafadhali hakiki taarifa zako na ujaribu tena."
                else
                    "Unable to complete request. Please verify your details and try again."
            }
        }
    }

    /**
     * Signs out the current Firebase user.
     */
    fun signOut() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error during signOut", e)
        }
    }
}
