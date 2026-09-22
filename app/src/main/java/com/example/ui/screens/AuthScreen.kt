package com.example.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import com.example.R
import com.example.data.cloud.AgritechCloudService
import com.example.ui.utils.BiometricHelper
import androidx.fragment.app.FragmentActivity
import com.example.data.cloud.FirebaseAuthManager
import com.example.data.local.AppPreferences
import com.example.data.models.UserAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Brand Palette
private val AmberPrimary = Color(0xFFD97706)
private val AmberDark = Color(0xFFB45309)
private val AmberLight = Color(0xFFFDE68A)
private val EmeraldSuccess = Color(0xFF059669)
private val RoseError = Color(0xFFDC2626)

enum class AuthTab {
    LOGIN,
    REGISTER
}

/**
 * Modern Authentication Screen for AGRITECH HUB.
 *
 * Implements Firebase Email & Password authentication for Login and Registration,
 * with real Google Sign-In via Android Credential Manager, Android Credential Manager
 * and Autofill password management, and email-based password reset.
 * All Phone/SMS OTP logic and technical billing errors have been completely removed.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AuthScreen(
    language: String,
    onToggleLanguage: () -> Unit,
    onRegisterDirectly: ((bName: String, oName: String, ph: String, em: String, pwd: String, firebaseUid: String?) -> AgritechCloudService.ServerResponse<UserAccount>)? = null,
    onRegister: (bName: String, oName: String, ph: String, em: String, pwd: String) -> AgritechCloudService.ServerResponse<UserAccount>,
    onLogin: (emOrPhone: String, pwd: String, onResult: (AgritechCloudService.ServerResponse<UserAccount>, Boolean) -> Unit) -> Unit,
    onLoginWithFirebase: ((em: String, pwd: String, firebaseUid: String, onResult: (AgritechCloudService.ServerResponse<UserAccount>, Boolean) -> Unit) -> Unit)? = null,
    onLoginWithGoogle: ((email: String, displayName: String?, uid: String, (AgritechCloudService.ServerResponse<UserAccount>, Boolean) -> Unit) -> Unit)? = null,
    // Backwards-compatible legacy callbacks (unused in new flow, preserved for API stability)
    onVerifyOtp: ((String, String) -> AgritechCloudService.ServerResponse<UserAccount>)? = null,
    onCompleteFirebaseVerifiedRegistration: ((String, String?) -> AgritechCloudService.ServerResponse<UserAccount>)? = null,
    onRequestForgotOtp: ((String) -> AgritechCloudService.ServerResponse<String>)? = null,
    onResetPasswordOtp: ((String, String, String) -> AgritechCloudService.ServerResponse<UserAccount>)? = null,
    onResendOtp: ((String) -> AgritechCloudService.ServerResponse<String>)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }
    val businessLogoPath = remember { prefs.getBusinessSettings().logoPath }
    val logoBitmap = remember(businessLogoPath) {
        if (businessLogoPath.isNotBlank()) {
            com.example.ui.utils.ImageUtils.loadLogoBitmap(businessLogoPath, 160)
        } else {
            null
        }
    }

    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    var currentTab by remember { mutableStateOf(AuthTab.LOGIN) }
    var isProcessing by remember { mutableStateOf(false) }
    var isGoogleProcessing by remember { mutableStateOf(false) }

    // Login Form State
    var loginEmail by remember { mutableStateOf(prefs.getSavedLoginEmail()) }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(prefs.isRememberMe()) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var loginSuccess by remember { mutableStateOf<String?>(null) }

    // Autofill Nodes for Login
    val emailAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
            onFill = { filledText ->
                loginEmail = filledText
                loginError = null
            }
        )
    }

    val passwordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { filledText ->
                loginPassword = filledText
                loginError = null
            }
        )
    }

    // Register Form State
    var regBusinessName by remember { mutableStateOf("") }
    var regOwnerFullName by remember { mutableStateOf("") }
    var regPhoneNumber by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }
    var regConfirmPasswordVisible by remember { mutableStateOf(false) }
    var registerError by remember { mutableStateOf<String?>(null) }

    // Autofill Nodes for Registration
    val regEmailAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
            onFill = { filledText ->
                regEmail = filledText
                registerError = null
            }
        )
    }

    val regPasswordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.NewPassword),
            onFill = { filledText ->
                regPassword = filledText
                registerError = null
            }
        )
    }

    val regConfirmPasswordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.NewPassword),
            onFill = { filledText ->
                regConfirmPassword = filledText
                registerError = null
            }
        )
    }

    LaunchedEffect(currentTab) {
        if (currentTab == AuthTab.LOGIN) {
            autofillTree += emailAutofillNode
            autofillTree += passwordAutofillNode
        } else {
            autofillTree += regEmailAutofillNode
            autofillTree += regPasswordAutofillNode
            autofillTree += regConfirmPasswordAutofillNode
        }
    }

    // Interaction sources for tap detection on fields
    val emailInteractionSource = remember { MutableInteractionSource() }
    val passwordInteractionSource = remember { MutableInteractionSource() }

    var hasPromptedCredentialManager by remember { mutableStateOf(false) }

    // Secure retrieval of saved credentials via system Credential Manager (including Google Password Manager)
    fun requestCredentialManagerFill(force: Boolean = false) {
        if (hasPromptedCredentialManager && !force) return
        hasPromptedCredentialManager = true
        coroutineScope.launch {
            try {
                val credResult = FirebaseAuthManager.getSavedPasswordCredential(context, language)
                if (credResult.success && !credResult.username.isNullOrBlank()) {
                    loginEmail = credResult.username
                    if (!credResult.password.isNullOrBlank()) {
                        loginPassword = credResult.password
                    }
                    loginError = null
                }
            } catch (e: Exception) {
                // Graceful silent fallback
            }
        }
    }

    LaunchedEffect(Unit) {
        if (loginEmail.isBlank() && loginPassword.isBlank()) {
            delay(300L)
            requestCredentialManagerFill(force = false)
        }
    }

    LaunchedEffect(emailInteractionSource) {
        emailInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                autofill?.requestAutofillForNode(emailAutofillNode)
                if (loginPassword.isBlank()) {
                    requestCredentialManagerFill(force = true)
                }
            }
        }
    }

    LaunchedEffect(passwordInteractionSource) {
        passwordInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                autofill?.requestAutofillForNode(passwordAutofillNode)
                if (loginPassword.isBlank()) {
                    requestCredentialManagerFill(force = true)
                }
            }
        }
    }

    // Dialog States
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotError by remember { mutableStateOf<String?>(null) }
    var forgotSuccess by remember { mutableStateOf<String?>(null) }
    var isForgotProcessing by remember { mutableStateOf(false) }

    var showRestoreNotice by remember { mutableStateOf(false) }

    fun clearErrors() {
        loginError = null
        loginSuccess = null
        registerError = null
    }

    // Google Sign-In with real Android Credential Manager & Firebase Auth
    fun performGoogleSignIn() {
        clearErrors()
        isGoogleProcessing = true
        coroutineScope.launch {
            try {
                val result = FirebaseAuthManager.signInWithGoogle(
                    context = context,
                    language = language
                )
                if (result.isCancelled) {
                    isGoogleProcessing = false
                    return@launch
                }
                if (result.success && !result.uid.isNullOrBlank()) {
                    val email = result.email ?: ""
                    val displayName = result.displayName
                    val uid = result.uid
                    val handler = onLoginWithGoogle
                    if (handler != null) {
                        handler(email, displayName, uid) { res, restored ->
                            isGoogleProcessing = false
                            if (!res.success) {
                                loginError = res.message
                                registerError = res.message
                            } else if (restored) {
                                showRestoreNotice = true
                            }
                        }
                    } else {
                        isGoogleProcessing = false
                    }
                } else {
                    isGoogleProcessing = false
                    val err = result.errorMessage ?: if (language == "sw") "Kuingia kwa Google kumeshindikana." else "Google Sign-In failed."
                    loginError = err
                    registerError = err
                }
            } catch (e: Exception) {
                isGoogleProcessing = false
                val friendly = FirebaseAuthManager.formatFriendlyError(e, language)
                loginError = friendly
                registerError = friendly
            }
        }
    }

    // Primary Login Execution
    fun performLogin() {
        val cleanEmail = loginEmail.trim().lowercase()
        val cleanPassword = loginPassword.trim()

        if (cleanEmail.isBlank()) {
            loginError = if (language == "sw") "Tafadhali weka barua pepe yako." else "Please enter your email address."
            return
        }
        if (cleanPassword.isBlank()) {
            loginError = if (language == "sw") "Tafadhali weka nenosiri lako." else "Please enter your password."
            return
        }

        isProcessing = true
        loginError = null
        loginSuccess = null

        // Save or clear Remember Me preference
        prefs.setRememberMe(rememberMe)
        if (rememberMe) {
            prefs.setSavedLoginEmail(cleanEmail)
        } else {
            prefs.setSavedLoginEmail("")
        }

        // Firebase Email & Password Authentication
        FirebaseAuthManager.signInWithEmail(
            email = cleanEmail,
            password = cleanPassword,
            language = language
        ) { authResult ->
            if (authResult.success) {
                val uid = authResult.uid ?: ""
                coroutineScope.launch {
                    // Prompt system Credential Manager / Google Password Manager to save credentials
                    try {
                        withTimeoutOrNull(15_000L) {
                            FirebaseAuthManager.savePasswordCredential(
                                context = context,
                                username = cleanEmail,
                                password = cleanPassword
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("AuthScreen", "Credential save prompt error: ${e.message}")
                    }

                    val firebaseLoginHandler = onLoginWithFirebase
                    if (firebaseLoginHandler != null) {
                        firebaseLoginHandler(cleanEmail, cleanPassword, uid) { res, restored ->
                            isProcessing = false
                            if (!res.success) {
                                loginError = res.message
                            } else if (restored) {
                                showRestoreNotice = true
                            }
                        }
                    } else {
                        onLogin(cleanEmail, cleanPassword) { res, restored ->
                            isProcessing = false
                            if (!res.success) {
                                loginError = res.message
                            } else if (restored) {
                                showRestoreNotice = true
                            }
                        }
                    }
                }
            } else {
                // If Firebase fails with credentials, try local fallback for offline accounts
                onLogin(cleanEmail, cleanPassword) { localRes, restored ->
                    isProcessing = false
                    if (localRes.success) {
                        if (restored) {
                            showRestoreNotice = true
                        }
                        coroutineScope.launch {
                            FirebaseAuthManager.savePasswordCredential(
                                context = context,
                                username = cleanEmail,
                                password = cleanPassword
                            )
                        }
                    } else {
                        loginError = authResult.errorMessage ?: localRes.message
                    }
                }
            }
        }
    }

    // Primary Register Execution
    fun performRegister() {
        val cleanBiz = regBusinessName.trim()
        val cleanOwner = regOwnerFullName.trim()
        val cleanPhone = regPhoneNumber.trim()
        val cleanEmail = regEmail.trim().lowercase()
        val cleanPwd = regPassword.trim()
        val cleanConfirm = regConfirmPassword.trim()

        if (cleanBiz.isBlank()) {
            registerError = if (language == "sw") "Jina la biashara linahitajika." else "Business Name is required."
            return
        }
        val nameParts = cleanOwner.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (nameParts.size < 2) {
            registerError = if (language == "sw") "Jina la mmiliki linahitaji angalau majina mawili." else "Owner's Full Name requires at least two names."
            return
        }
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            registerError = if (language == "sw") "Tafadhali weka barua pepe halali." else "Please enter a valid email address."
            return
        }
        if (cleanPwd.length < 6) {
            registerError = if (language == "sw") "Nenosiri lazima liwe na angalau herufi 6." else "Password must be at least 6 characters."
            return
        }
        if (cleanPwd != cleanConfirm) {
            registerError = if (language == "sw") "Nenosiri halilingani na uthibitisho." else "Passwords do not match."
            return
        }

        isProcessing = true
        registerError = null

        // Create user with Firebase Email & Password Authentication
        FirebaseAuthManager.createUserWithEmail(
            email = cleanEmail,
            password = cleanPwd,
            language = language
        ) { authResult ->
            if (authResult.success) {
                val uid = authResult.uid
                coroutineScope.launch {
                    // Prompt Android / Google Password Manager to save the new credentials
                    try {
                        withTimeoutOrNull(15_000L) {
                            FirebaseAuthManager.savePasswordCredential(
                                context = context,
                                username = cleanEmail,
                                password = cleanPwd
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("AuthScreen", "Registration credential save warning: ${e.message}")
                    }

                    // Complete user registration in the application session
                    val res = onRegisterDirectly?.invoke(cleanBiz, cleanOwner, cleanPhone, cleanEmail, cleanPwd, uid)
                        ?: onRegister(cleanBiz, cleanOwner, cleanPhone, cleanEmail, cleanPwd)
                    isProcessing = false
                    if (!res.success) {
                        registerError = res.message
                    }
                }
            } else {
                isProcessing = false
                val err = authResult.errorMessage ?: if (language == "sw")
                    "Intaneti inahitajika ili kuunda akaunti mpya na kuunganisha taarifa zako kwenye wingu (Firebase). Tafadhali washa data au Wi-Fi kisha ujaribu tena."
                else
                    "An active internet connection is required to create a new account and link your data to Firebase. Please connect to internet and try again."
                registerError = err
                if (err.contains("tayari ipo", ignoreCase = true) ||
                    err.contains("already exists", ignoreCase = true) ||
                    err.contains("already in use", ignoreCase = true)
                ) {
                    loginEmail = cleanEmail
                    if (cleanPwd.isNotBlank()) {
                        loginPassword = cleanPwd
                    }
                }
            }
        }
    }

    // Forgot Password Execution
    fun performForgotPassword() {
        val cleanEmail = forgotEmail.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            forgotError = if (language == "sw") "Tafadhali weka barua pepe halali." else "Please enter a valid email address."
            return
        }

        isForgotProcessing = true
        forgotError = null
        forgotSuccess = null

        FirebaseAuthManager.sendPasswordReset(cleanEmail, language) { res ->
            isForgotProcessing = false
            if (res.success) {
                forgotSuccess = res.message
            } else {
                forgotError = res.message
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Language Selector (Pill Style, Top-Right - Login Screen Only)
            if (currentTab == AuthTab.LOGIN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onToggleLanguage,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("auth_lang_toggle_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                tint = AmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (language == "sw") "Kiswahili" else "English",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Agritech Hub Logo & Emblem
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = "Agritech Hub Logo",
                    modifier = Modifier
                        .height(84.dp)
                        .wrapContentWidth(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AmberLight, AmberPrimary, AmberDark)
                            )
                        )
                        .border(2.dp, AmberPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Agritech Hub",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Title & Tagline
            Text(
                text = "AGRITECH HUB",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (currentTab == AuthTab.LOGIN) {
                    if (language == "sw") "Mfumo Mahiri wa Usimamizi wa Biashara" else "Smart Business Management Suite"
                } else {
                    if (language == "sw") "Sajili akaunti ya Agritech Hub kusimamia biashara yako" else "Create your Agritech Hub account to manage your business"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Main Authentication Card
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentTab == AuthTab.LOGIN) {
                        // ==========================================
                        // LOGIN SCREEN
                        // ==========================================

                        // Error Banner
                        AnimatedVisibility(visible = loginError != null) {
                            Surface(
                                color = RoseError.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = RoseError,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = loginError ?: "",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = RoseError,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    val isCredentialOrPasswordIssue = loginError?.let { err ->
                                        err.contains("nenosiri", ignoreCase = true) ||
                                                err.contains("password", ignoreCase = true) ||
                                                err.contains("credential", ignoreCase = true) ||
                                                err.contains("Google", ignoreCase = true)
                                    } == true

                                    if (isCredentialOrPasswordIssue) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Quick Reset Password button
                                            Button(
                                                onClick = {
                                                    forgotEmail = loginEmail.trim()
                                                    forgotError = null
                                                    forgotSuccess = null
                                                    showForgotDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = AmberPrimary,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LockReset,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (language == "sw") "Weka Nenosiri" else "Reset Password",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }

                                            // Quick Continue with Google button
                                            OutlinedButton(
                                                onClick = { performGoogleSignIn() },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                                    contentDescription = null,
                                                    tint = Color.Unspecified,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Google",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Success Banner
                        AnimatedVisibility(visible = loginSuccess != null) {
                            Surface(
                                color = EmeraldSuccess.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = loginSuccess ?: "",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = EmeraldSuccess
                                    )
                                }
                            }
                        }

                        // Sehemu ya Kuchagua Lugha (Login Screen Only)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .testTag("login_language_selector_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Language",
                                        tint = AmberPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (language == "sw") "Lugha:" else "Language:",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Kiswahili
                                    Surface(
                                        onClick = {
                                            if (language != "sw") onToggleLanguage()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (language == "sw") AmberPrimary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (language == "sw") AmberPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.testTag("login_lang_sw_btn")
                                    ) {
                                        Text(
                                            text = "Kiswahili",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (language == "sw") FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (language == "sw") Color.White else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                    // English
                                    Surface(
                                        onClick = {
                                            if (language != "en") onToggleLanguage()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (language == "en") AmberPrimary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (language == "en") AmberPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.testTag("login_lang_en_btn")
                                    ) {
                                        Text(
                                            text = "English",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (language == "en") FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (language == "en") Color.White else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 1. Email Address Field
                        OutlinedTextField(
                            value = loginEmail,
                            onValueChange = {
                                loginEmail = it
                                loginError = null
                            },
                            label = { Text(if (language == "sw") "Barua Pepe" else "Email Address") },
                            placeholder = { Text("name@example.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = AmberPrimary
                                )
                            },
                            singleLine = true,
                            interactionSource = emailInteractionSource,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    emailAutofillNode.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        autofill?.requestAutofillForNode(emailAutofillNode)
                                        if (loginPassword.isBlank()) {
                                            requestCredentialManagerFill()
                                        }
                                    } else {
                                        autofill?.cancelAutofillForNode(emailAutofillNode)
                                    }
                                }
                                .testTag("login_identity_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. Password Field with Show/Hide Toggle
                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = {
                                loginPassword = it
                                loginError = null
                            },
                            label = { Text(if (language == "sw") "Nenosiri" else "Password") },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Password",
                                    tint = AmberPrimary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                                    Icon(
                                        imageVector = if (loginPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (loginPasswordVisible) "Hide Password" else "Show Password",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            singleLine = true,
                            interactionSource = passwordInteractionSource,
                            visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    performLogin()
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    passwordAutofillNode.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        autofill?.requestAutofillForNode(passwordAutofillNode)
                                        if (loginPassword.isBlank()) {
                                            requestCredentialManagerFill()
                                        }
                                    } else {
                                        autofill?.cancelAutofillForNode(passwordAutofillNode)
                                    }
                                }
                                .testTag("login_password_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. Options Row: Remember Me & Forgot Password
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { rememberMe = !rememberMe }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { rememberMe = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AmberPrimary,
                                        checkmarkColor = Color.Black
                                    ),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (language == "sw") "Nikumbuke" else "Remember me",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TextButton(
                                onClick = {
                                    forgotEmail = loginEmail.trim()
                                    forgotError = null
                                    forgotSuccess = null
                                    showForgotDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.testTag("forgot_pwd_btn")
                            ) {
                                Text(
                                    text = if (language == "sw") "Umesahau nenosiri?" else "Forgot password?",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = AmberPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 4. Main LOGIN Button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                performLogin()
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_btn")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = if (language == "sw") "INGIA" else "LOGIN",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }

                        // 5. Divider with "OR"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (language == "sw") "AU" else "OR",
                                modifier = Modifier.padding(horizontal = 14.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            )
                        }

                        // 6. "Continue with Google" Button
                        OutlinedButton(
                            onClick = { performGoogleSignIn() },
                            enabled = !isProcessing && !isGoogleProcessing,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_google_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            if (isGoogleProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (language == "sw") "Inaunganisha na Google..." else "Connecting to Google...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = "Google",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (language == "sw") "Endelea na Google" else "Continue with Google",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }

                        // 6.5. Small Biometric Login Option (Only if hardware is supported)
                        val isBiometricHardwareSupported = remember { BiometricHelper.isHardwareSupported(context) }
                        val isBiometricEnrolled = remember { BiometricHelper.hasBiometricsEnrolled(context) }
                        val hasBiometricSetup = remember { prefs.hasBiometricCredentials() }

                        if (isBiometricHardwareSupported) {
                            Spacer(modifier = Modifier.height(14.dp))
                            TextButton(
                                onClick = {
                                    val biometricActivity = context as? FragmentActivity
                                    if (biometricActivity != null) {
                                        if (!isBiometricEnrolled) {
                                            loginError = if (language == "sw") {
                                                "Tafadhali weka alama ya kidole kwenye mipangilio ya simu yako kwanza."
                                            } else {
                                                "Please set up fingerprint/biometric authentication in your phone's system settings first."
                                            }
                                            return@TextButton
                                        }
                                        
                                        if (!hasBiometricSetup) {
                                            loginError = if (language == "sw") {
                                                "Hakuna akaunti iliyohifadhiwa. Tafadhali ingia mara moja kwanza kwa barua pepe au Google ili kuwezesha alama ya kidole."
                                            } else {
                                                "No saved account found. Please login normally once with Email/Password or Google to enable biometric login."
                                            }
                                            return@TextButton
                                        }

                                        val title = if (language == "sw") "Ingia kwa Alama ya Kidole" else "Biometric Login"
                                        val subtitle = if (language == "sw") "Gusa sensor ya alama ya kidole kuingia kwenye akaunti yako" else "Touch the fingerprint sensor to access your account"
                                        val negativeBtnText = if (language == "sw") "Ghairi" else "Cancel"
                                        
                                        BiometricHelper.showBiometricPrompt(
                                            activity = biometricActivity,
                                            title = title,
                                            subtitle = subtitle,
                                            negativeButtonText = negativeBtnText,
                                            onSuccess = {
                                                val bType = prefs.getBiometricAuthType()
                                                val bEmail = prefs.getBiometricEmail() ?: ""
                                                val bSecret = prefs.getBiometricSecret() ?: ""
                                                val bDisplayName = prefs.getBiometricDisplayName()
                                                
                                                isProcessing = true
                                                loginError = null
                                                loginSuccess = null
                                                
                                                if (bType == "google") {
                                                    isGoogleProcessing = true
                                                    val googleLoginHandler = onLoginWithGoogle
                                                    if (googleLoginHandler != null) {
                                                        googleLoginHandler(bEmail, bDisplayName, bSecret) { res, restored ->
                                                            isProcessing = false
                                                            isGoogleProcessing = false
                                                            if (!res.success) {
                                                                loginError = res.message
                                                            } else if (restored) {
                                                                showRestoreNotice = true
                                                            }
                                                        }
                                                    } else {
                                                        isProcessing = false
                                                        isGoogleProcessing = false
                                                        loginError = "Google Sign-In is not supported."
                                                    }
                                                } else {
                                                    FirebaseAuthManager.signInWithEmail(
                                                        email = bEmail,
                                                        password = bSecret,
                                                        language = language
                                                    ) { authResult ->
                                                        if (authResult.success) {
                                                            val uid = authResult.uid ?: ""
                                                            val firebaseLoginHandler = onLoginWithFirebase
                                                            if (firebaseLoginHandler != null) {
                                                                firebaseLoginHandler(bEmail, bSecret, uid) { res, restored ->
                                                                    isProcessing = false
                                                                    if (!res.success) {
                                                                        loginError = res.message
                                                                    } else if (restored) {
                                                                        showRestoreNotice = true
                                                                    }
                                                                }
                                                            } else {
                                                                onLogin(bEmail, bSecret) { res, restored ->
                                                                    isProcessing = false
                                                                    if (!res.success) {
                                                                        loginError = res.message
                                                                    } else if (restored) {
                                                                        showRestoreNotice = true
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            onLogin(bEmail, bSecret) { localRes, restored ->
                                                                isProcessing = false
                                                                if (localRes.success) {
                                                                    if (restored) {
                                                                        showRestoreNotice = true
                                                                    }
                                                                } else {
                                                                    loginError = authResult.errorMessage ?: localRes.message
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            onError = { err ->
                                                loginError = err
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .testTag("biometric_login_btn"),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = AmberPrimary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Fingerprint",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (language == "sw") "Ingia kwa Alama ya Kidole" else "Login with Fingerprint",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 7. Bottom Navigation Text: Switch to Sign Up
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == "sw") "Huna akaunti? " else "Don't have an account? ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (language == "sw") "Jisajili" else "Sign Up",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = AmberPrimary,
                                modifier = Modifier
                                    .clickable {
                                        currentTab = AuthTab.REGISTER
                                        clearErrors()
                                    }
                                    .testTag("switch_to_signup_btn")
                            )
                        }

                    } else {
                        // ==========================================
                        // CREATE ACCOUNT / SIGN UP SCREEN
                        // ==========================================

                        Text(
                            text = if (language == "sw") "Fungua Akaunti Mpya" else "Create an Account",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Error Banner
                        AnimatedVisibility(visible = registerError != null) {
                            Surface(
                                color = RoseError.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = RoseError,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = registerError ?: "",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = RoseError,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    val isAlreadyExists = registerError?.let { err ->
                                        err.contains("tayari ipo", ignoreCase = true) ||
                                                err.contains("already exists", ignoreCase = true) ||
                                                err.contains("already in use", ignoreCase = true) ||
                                                err.contains("chagua Kuingia", ignoreCase = true) ||
                                                err.contains("choose Login", ignoreCase = true)
                                    } == true

                                    if (isAlreadyExists) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Direct switch to Login with email transferred
                                            Button(
                                                onClick = {
                                                    loginEmail = regEmail.trim()
                                                    if (regPassword.isNotBlank()) {
                                                        loginPassword = regPassword.trim()
                                                    }
                                                    currentTab = AuthTab.LOGIN
                                                    clearErrors()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = AmberPrimary,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Login,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (language == "sw") "Ingia Sasa" else "Log In Now",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }

                                            // Quick Reset Password action
                                            OutlinedButton(
                                                onClick = {
                                                    forgotEmail = regEmail.trim()
                                                    forgotError = null
                                                    forgotSuccess = null
                                                    showForgotDialog = true
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LockReset,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (language == "sw") "Weka Nenosiri" else "Reset Password",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Business Name Field
                        OutlinedTextField(
                            value = regBusinessName,
                            onValueChange = {
                                regBusinessName = it
                                registerError = null
                            },
                            label = { Text(if (language == "sw") "Jina la Biashara *" else "Business Name *") },
                            placeholder = { Text(if (language == "sw") "mf. Agritech Electrical" else "e.g. Agritech Electrical") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = "Business Name",
                                    tint = AmberPrimary
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_business_name_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Owner's Full Name Field
                        OutlinedTextField(
                            value = regOwnerFullName,
                            onValueChange = {
                                regOwnerFullName = it
                                registerError = null
                            },
                            label = { Text(if (language == "sw") "Jina la Mmiliki (Majina 2+) *" else "Owner's Full Name (2+ names) *") },
                            placeholder = { Text(if (language == "sw") "mf. Juma Hamisi" else "e.g. John Doe") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Owner Name",
                                    tint = AmberPrimary
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_owner_name_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Phone Number Field (Retained for business quotations, NO SMS OTP)
                        OutlinedTextField(
                            value = regPhoneNumber,
                            onValueChange = {
                                regPhoneNumber = it
                                registerError = null
                            },
                            label = { Text(if (language == "sw") "Namba ya Simu ya Biashara" else "Business Phone Number") },
                            placeholder = { Text("07XXXXXXXX") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone",
                                    tint = AmberPrimary
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_phone_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 4. Email Address Field (Required for Firebase Email Auth)
                        OutlinedTextField(
                            value = regEmail,
                            onValueChange = {
                                regEmail = it
                                registerError = null
                            },
                            label = { Text(if (language == "sw") "Barua Pepe (Email) *" else "Email Address *") },
                            placeholder = { Text("name@example.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = AmberPrimary
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    regEmailAutofillNode.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        autofill?.requestAutofillForNode(regEmailAutofillNode)
                                    } else {
                                        autofill?.cancelAutofillForNode(regEmailAutofillNode)
                                    }
                                }
                                .testTag("reg_email_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 5. Password Field
                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = {
                                regPassword = it
                                registerError = null
                            },
                            label = { Text(if (language == "sw") "Nenosiri (herufi 6+) *" else "Password (6+ chars) *") },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Password",
                                    tint = AmberPrimary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { regPasswordVisible = !regPasswordVisible }) {
                                    Icon(
                                        imageVector = if (regPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (regPasswordVisible) "Hide" else "Show",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    regPasswordAutofillNode.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        autofill?.requestAutofillForNode(regPasswordAutofillNode)
                                    } else {
                                        autofill?.cancelAutofillForNode(regPasswordAutofillNode)
                                    }
                                }
                                .testTag("reg_password_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 6. Confirm Password Field
                        OutlinedTextField(
                            value = regConfirmPassword,
                            onValueChange = {
                                regConfirmPassword = it
                                registerError = null
                            },
                            label = { Text(if (language == "sw") "Thibitisha Nenosiri *" else "Confirm Password *") },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Confirm Password",
                                    tint = AmberPrimary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { regConfirmPasswordVisible = !regConfirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (regConfirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (regConfirmPasswordVisible) "Hide" else "Show",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (regConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    performRegister()
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_confirm_password_input")
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 7. Main CREATE ACCOUNT Button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                performRegister()
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("register_submit_btn")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = if (language == "sw") "FUNGUA AKAUNTI" else "CREATE ACCOUNT",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }

                        // 8. Divider with "OR"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (language == "sw") "AU" else "OR",
                                modifier = Modifier.padding(horizontal = 14.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            )
                        }

                        // 9. "Continue with Google" Button
                        OutlinedButton(
                            onClick = { performGoogleSignIn() },
                            enabled = !isProcessing && !isGoogleProcessing,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("register_google_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            if (isGoogleProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (language == "sw") "Inaunganisha na Google..." else "Connecting to Google...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = "Google",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (language == "sw") "Endelea na Google" else "Continue with Google",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 10. Bottom Navigation Text: Switch to Login
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == "sw") "Tayari una akaunti? " else "Already have an account? ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (language == "sw") "Ingia" else "Login",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = AmberPrimary,
                                modifier = Modifier
                                    .clickable {
                                        currentTab = AuthTab.LOGIN
                                        clearErrors()
                                    }
                                    .testTag("switch_to_login_btn")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Text: Exactly "Powered by Agritech"
            Text(
                text = "Powered by Agritech",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("auth_footer_text")
            )
        }
    }

    // ==========================================
    // FORGOT PASSWORD DIALOG (Firebase Email Reset)
    // ==========================================
    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isForgotProcessing) {
                    showForgotDialog = false
                }
            },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (language == "sw") "Weka Upya Nenosiri" else "Forgot Password",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (language == "sw")
                            "Weka barua pepe yako uliyojisajili nayo ili kupokea kiungo cha kuweka upya nenosiri:"
                        else
                            "Enter your registered email address to receive a password reset link:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = {
                            forgotEmail = it
                            forgotError = null
                        },
                        label = { Text(if (language == "sw") "Barua Pepe" else "Email Address") },
                        placeholder = { Text("name@example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_identity_input")
                    )

                    AnimatedVisibility(visible = forgotError != null) {
                        Surface(
                            color = RoseError.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = forgotError ?: "",
                                color = RoseError,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = forgotSuccess != null) {
                        Surface(
                            color = EmeraldSuccess.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = forgotSuccess ?: "",
                                    color = EmeraldSuccess,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        performForgotPassword()
                    },
                    enabled = !isForgotProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("forgot_submit_btn")
                ) {
                    if (isForgotProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (language == "sw") "Tuma Kiungo" else "Send Reset Link",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotDialog = false },
                    enabled = !isForgotProcessing,
                    modifier = Modifier.testTag("forgot_cancel_btn")
                ) {
                    Text(if (language == "sw") "Funga" else "Close")
                }
            }
        )
    }



    // ==========================================
    // RESTORE NOTICE DIALOG
    // ==========================================
    if (showRestoreNotice) {
        AlertDialog(
            onDismissRequest = { showRestoreNotice = false },
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (language == "sw") "Data Zilizohifadhiwa Zimerejeshwa!" else "Cloud Backup Restored!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = if (language == "sw")
                        "Taarifa zote za biashara, wateja, makadirio na bei zilizokuwa kwenye wingu zimerejeshwa kikamilifu kwenye kifaa hiki. Unaweza kuendelea kutumia mfumo huu hata BILA INTERNET (Offline)!"
                    else
                        "All business profile, customer, quotation, and material data backed up on the cloud have been successfully restored to this device. You can now use the app OFFLINE!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { showRestoreNotice = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (language == "sw") "Fungua Mfumo" else "Open App", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
