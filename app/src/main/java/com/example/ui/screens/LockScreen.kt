package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings

@Composable
fun LockScreen(
    businessName: String,
    language: String,
    isPasswordConfigured: Boolean,
    onToggleLanguage: () -> Unit = {},
    onCreatePassword: (password: String) -> Boolean = { false },
    onUnlock: (password: String) -> Boolean,
    onSwitchAccount: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    // Mode: If not configured, we start in Create Password mode
    var isCreateMode by remember(isPasswordConfigured) { mutableStateOf(!isPasswordConfigured) }

    // Unlock / Login state
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resetSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Create Password state
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var createErrorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp)
    ) {
        // TOP-RIGHT CORNER: Language Selector
        Surface(
            onClick = {
                focusManager.clearFocus()
                onToggleLanguage()
            },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag("lock_language_selector")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Language",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (language == "sw") "Kiswahili" else "English",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // CENTER: Main Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .align(Alignment.Center),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCreateMode) Icons.Default.Key else Icons.Default.Bolt,
                        contentDescription = "Logo",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = businessName.ifEmpty { "AGRITECH HUB" },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center
                )

                if (isCreateMode) {
                    // ==========================================
                    // 1. CREATE PASSWORD MODE (FIRST TIME)
                    // ==========================================
                    Text(
                        text = if (language == "sw") "Tengeneza Nenosiri Lako (Create Password)" else "Create Your Password",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (language == "sw")
                            "Weka nenosiri unalopenda (maneno, namba au mchanganyiko) kulinda mfumo wako:"
                        else
                            "Set your personal password (words, numbers, or letters) to secure your app:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                    )

                    // 1. New Password Input
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = {
                            newPasswordInput = it
                            createErrorMessage = null
                        },
                        placeholder = { Text(if (language == "sw") "Andika Nenosiri Jipya..." else "Enter New Password...") },
                        label = { Text(if (language == "sw") "Nenosiri Jipya" else "New Password") },
                        singleLine = true,
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_password_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Confirm Password Input
                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = {
                            confirmPasswordInput = it
                            createErrorMessage = null
                        },
                        placeholder = { Text(if (language == "sw") "Thibitisha Nenosiri Lako..." else "Confirm Your Password...") },
                        label = { Text(if (language == "sw") "Thibitisha Nenosiri" else "Confirm Password") },
                        singleLine = true,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (newPasswordInput.isBlank()) {
                                    createErrorMessage = if (language == "sw") "Tafadhali andika nenosiri lako!" else "Please enter your password!"
                                    return@KeyboardActions
                                }
                                if (newPasswordInput != confirmPasswordInput) {
                                    createErrorMessage = if (language == "sw") "Nenosiri halilingani! Hakikisha yanafanana." else "Passwords do not match! Please check again."
                                    return@KeyboardActions
                                }
                                val success = onCreatePassword(newPasswordInput)
                                if (success) {
                                    isCreateMode = false
                                    passwordInput = ""
                                    resetSuccessMessage = if (language == "sw")
                                        "Nenosiri limetengenezwa kikamilifu! Sasa weka nenosiri lako kuingia."
                                    else
                                        "Password created successfully! Please enter your password to login."
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_confirm_password_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (createErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = createErrorMessage ?: "",
                            color = RoseError,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (newPasswordInput.isBlank()) {
                                createErrorMessage = if (language == "sw") "Tafadhali andika nenosiri lako!" else "Please enter your password!"
                                return@Button
                            }
                            if (newPasswordInput != confirmPasswordInput) {
                                createErrorMessage = if (language == "sw") "Nenosiri halilingani! Hakikisha yanafanana." else "Passwords do not match! Please check again."
                                return@Button
                            }
                            val success = onCreatePassword(newPasswordInput)
                            if (success) {
                                isCreateMode = false
                                passwordInput = ""
                                resetSuccessMessage = if (language == "sw")
                                    "Nenosiri limetengenezwa kikamilifu! Sasa weka nenosiri lako kuingia."
                                else
                                    "Password created successfully! Please enter your password to login."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_password_submit_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == "sw") "Hifadhi Nenosiri & Endelea" else "Save Password & Continue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                } else {
                    // ==========================================
                    // 2. LOGIN / UNLOCK MODE
                    // ==========================================
                    Text(
                        text = if (language == "sw") "Ulinzi wa Mfumo" else "System Security",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Text(
                        text = if (language == "sw") "Weka nenosiri lako kufungua mfumo" else "Enter your password to unlock the system",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            errorMessage = null
                            resetSuccessMessage = null
                        },
                        placeholder = { Text(if (language == "sw") "Weka Nenosiri..." else "Enter Password...") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                val success = onUnlock(passwordInput)
                                if (!success) {
                                    errorMessage = if (language == "sw") "Nenosiri si sahihi!" else "Incorrect password!"
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_password_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = RoseError,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    if (resetSuccessMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = resetSuccessMessage ?: "",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val success = onUnlock(passwordInput)
                            if (!success) {
                                errorMessage = if (language == "sw") "Nenosiri si sahihi!" else "Incorrect password!"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("unlock_submit_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.t("btn_unlock", language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            onSwitchAccount()
                        },
                        modifier = Modifier.testTag("lock_switch_account_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockReset,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == "sw") "Umesahau Nenosiri? / Badili Akaunti" else "Forgot Password? / Switch Account",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Powered by Agritech",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = AmberPrimary
                )
            }
        }
    }
}
