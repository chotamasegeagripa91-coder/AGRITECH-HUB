package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.LicenseStatus
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun LicenseBlockScreen(
    licenseStatus: LicenseStatus,
    installationId: String,
    language: String,
    onContactSupport: (String) -> Unit,
    onActivateNewLicense: (String) -> Pair<Boolean, String>
) {
    var inputKey by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showActivateInput by remember { mutableStateOf(false) }

    val (titleText, descSw, descEn, iconVector, primaryColor) = when (licenseStatus) {
        LicenseStatus.INVALID_DEVICE -> Tuple5(
            "LICENSE NOT VALID FOR THIS DEVICE",
            "Code hii ya leseni si halali kwa kifaa hiki. Leseni moja inafanya kazi kwenye kifaa kimoja tu.",
            "This license key is not valid for this device. Each license is bound to one device ID only.",
            Icons.Default.PhoneAndroid,
            RoseError
        )
        LicenseStatus.DISABLED -> Tuple5(
            "LICENSE DISABLED",
            "Leseni yako imezimwa na Msimamizi wa Mfumo. Tafadhali wasiliana na msaada ili kurejesha huduma.",
            "Your license has been disabled by System Administrator. Please contact support to reactivate your license.",
            Icons.Default.Block,
            RoseError
        )
        LicenseStatus.REVOKED -> Tuple5(
            "LICENSE REVOKED",
            "Leseni yako imefutwa/imesitishwa. Wasiliana na Admin kwa namba +255 627 318 891 au +255 650 549 735 ili akufungulie akaunti yako na kukupatia leseni mpya.",
            "Your license has been revoked. Contact Admin at +255 627 318 891 or +255 650 549 735 to unlock your account and receive a new activation license.",
            Icons.Default.Security,
            RoseError
        )
        LicenseStatus.EXPIRED, LicenseStatus.LICENSE_EXPIRED, LicenseStatus.TRIAL_EXPIRED -> Tuple5(
            "LICENSE EXPIRED",
            "Muda wa leseni au jaribio lako umekwisha. Tafadhali lipia na uweke code mpya ya leseni kuendelea.",
            "Your license or trial has expired. Please renew your subscription to receive a new activation code.",
            Icons.Default.Key,
            AmberPrimary
        )
        LicenseStatus.TAMPERED -> Tuple5(
            "CLOCK TAMPERING DETECTED",
            "Tarehe au saa ya simu imebadilishwa. Weka tarehe sahihi ya mtandao kuendelea.",
            "System clock tampering detected. Please align your device date/time with network time to proceed.",
            Icons.Default.Lock,
            RoseError
        )
        else -> Tuple5(
            "LICENSE INVALID",
            "Leseni yako si halali. Wasiliana na msimamizi kupata msaada.",
            "Your license status is invalid. Please contact administrator for assistance.",
            Icons.Default.Block,
            RoseError
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F12),
                        Color(0xFF18181E),
                        Color(0xFF0D0D10)
                    )
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.5.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .testTag("license_block_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C24))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.15f))
                        .border(1.dp, primaryColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = "License Status Icon",
                        tint = primaryColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AGRITECH HUB Branding
                Text(
                    text = "AGRITECH HUB",
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status Message
                Text(
                    text = titleText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Explanatory Description
                Text(
                    text = if (language == "sw") descSw else descEn,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider(color = Color(0xFF2E2E38))

                Spacer(modifier = Modifier.height(16.dp))

                // Device ID Display
                Surface(
                    color = Color(0xFF121218),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C38)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (language == "sw") "Kifaa Hiki (Device ID):" else "This Device ID:",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = installationId,
                            color = AmberPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Admin Phone Contact Numbers Card
                Surface(
                    color = Color(0xFF161622),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (language == "sw") "Namba za Simu za Admin (Kufungua Akaunti):" else "Admin Phone Numbers (Account Unlock):",
                            color = AmberPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Phone",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+255 627 318 891  |  +255 650 549 735",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "(HALOPESA / MIXX BY YAS - AGRIPA EDWARD)",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Support Action Buttons
                Button(
                    onClick = {
                        val message = "Hujambo Admin, nahitaji msaada wa Leseni ya AGRITECH HUB.\nStatus: $titleText\nDevice ID: $installationId"
                        onContactSupport(message)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("contact_support_whatsapp_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call/WhatsApp",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == "sw") "Wasiliana na Admin (WhatsApp)" else "Contact Admin on WhatsApp",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button to toggle/show license input
                OutlinedButton(
                    onClick = {
                        showActivateInput = !showActivateInput
                        errorMessage = null
                        successMessage = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("activate_new_license_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Activate Key",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == "sw") "Ingiza Code Mpya ya Leseni" else "Enter New License Code",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Inline License Activation Section
                AnimatedVisibility(visible = showActivateInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = inputKey,
                            onValueChange = {
                                inputKey = it
                                errorMessage = null
                                successMessage = null
                            },
                            label = { Text(if (language == "sw") "Code ya Leseni" else "License Code", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberPrimary,
                                unfocusedBorderColor = Color(0xFF3E3E4D),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("license_key_input_field")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (inputKey.isBlank()) {
                                    errorMessage = if (language == "sw") "Tafadhali ingiza code ya leseni." else "Please enter a license code."
                                    return@Button
                                }
                                val result = onActivateNewLicense(inputKey)
                                if (result.first) {
                                    successMessage = result.second
                                    errorMessage = null
                                } else {
                                    errorMessage = result.second
                                    successMessage = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("submit_activation_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Activate",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (language == "sw") "Thibitisha Leseni" else "Verify License",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = RoseError,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!successMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = successMessage!!,
                                color = EmeraldSuccess,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
