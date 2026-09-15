package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.licensing.LicensingEngine
import com.example.data.models.LicenseInfo
import com.example.data.models.LicenseStatus
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun ActivationDialog(
    licenseInfo: LicenseInfo,
    installationId: String,
    language: String,
    onDismiss: () -> Unit,
    onActivate: (key: String, customerName: String) -> Pair<Boolean, String>
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var licenseKeyInput by remember { mutableStateOf("") }
    var customerNameInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(text: String, label: String) {
        focusManager.clearFocus()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label Imenakiliwa!", Toast.LENGTH_SHORT).show()
    }

    fun openWhatsApp(phone: String, text: String) {
        focusManager.clearFocus()
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://wa.me/$phone?text=${Uri.encode(text)}")
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp haipatikani kwenye kifaa hiki.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = {
            focusManager.clearFocus()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(8.dp)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AmberPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "License",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (language == "sw") "Uanzishaji wa Leseni" else "License Activation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    IconButton(onClick = {
                        focusManager.clearFocus()
                        onDismiss()
                    }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Device ID Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (language == "sw") "Namba ya Utambulisho wa Kifaa (Device ID):" else "Device Installation ID:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = installationId,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                modifier = Modifier.weight(1f, fill = false),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = { copyToClipboard(installationId, "Device ID") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (language == "sw") "Nakili" else "Copy", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status Banner
                val statusText = when (licenseInfo.status) {
                    LicenseStatus.ACTIVE -> {
                        val durationInfo = if (licenseInfo.daysRemaining != null) {
                            if (language == "sw") "Zimebaki Siku ${licenseInfo.daysRemaining}" else "${licenseInfo.daysRemaining} Days Left"
                        } else {
                            if (language == "sw") "Bila Kikomo cha Muda (Lifetime)" else "Lifetime (No Expiry)"
                        }
                        val expiryDate = if (licenseInfo.expiresAt != null) " - ${if (language == "sw") "Mwisho" else "Expires"}: ${licenseInfo.expiresAt}" else ""
                        if (language == "sw") "Leseni Iko Hai: ${licenseInfo.licenseType?.titleSw ?: "PRO"} ($durationInfo$expiryDate)"
                        else "Active License: ${licenseInfo.licenseType?.titleEn ?: "PRO"} ($durationInfo$expiryDate)"
                    }
                    LicenseStatus.TRIAL -> if (language == "sw") "Jaribio la Awali (Kifaa Kipya): Siku ${licenseInfo.trialDaysRemaining} zimebaki kati ya 30" else "Initial Free Trial (New Device): ${licenseInfo.trialDaysRemaining} of 30 days left"
                    LicenseStatus.TRIAL_EXPIRED -> if (language == "sw") "Muda wa Jaribio la Awali Umekwisha! (Jaribio la bure ni mara moja tu mwanzoni). Weka code ya leseni kuendelea." else "Initial Free Trial Ended! (One-time trial for new install only). Please enter license code to continue."
                    LicenseStatus.LICENSE_EXPIRED -> if (language == "sw") "Muda wa Leseni Umekwisha! Tafadhali lipia na uweke code mpya ya leseni kuendelea." else "License Expired! Please renew and enter a license code to continue."
                    LicenseStatus.TAMPERED -> if (language == "sw") "Tahadhari: Tarehe ya Simu Imebadilishwa!" else "Warning: Clock Tampering Detected!"
                }
                val statusColor = if (licenseInfo.status == LicenseStatus.ACTIVE) EmeraldSuccess else if (licenseInfo.status == LicenseStatus.TRIAL) AmberPrimary else RoseError

                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (licenseInfo.status == LicenseStatus.ACTIVE) Icons.Default.Verified else Icons.Default.Info,
                            contentDescription = "Status",
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = statusColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Activation Code Input
                Text(
                    text = if (language == "sw") "Ingiza Code ya Leseni:" else "Enter License Code:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = licenseKeyInput,
                    onValueChange = { licenseKeyInput = it.trim() },
                    placeholder = { Text("Mfano: eyJpZCI6...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("activation_key_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipText.isNotBlank()) licenseKeyInput = clipText.trim()
                        }) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customerNameInput,
                    onValueChange = { customerNameInput = it },
                    label = { Text(if (language == "sw") "Jina la Mteja / Kampuni (Hiari)" else "Client / Business Name (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = RoseError,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (successMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = successMessage ?: "",
                        color = EmeraldSuccess,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        errorMessage = null
                        successMessage = null
                        if (licenseKeyInput.isBlank()) {
                            errorMessage = if (language == "sw") "Tafadhali weka code ya leseni." else "Please enter license code."
                            return@Button
                        }
                        val (success, msg) = onActivate(licenseKeyInput, customerNameInput)
                        if (success) {
                            successMessage = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        } else {
                            errorMessage = msg
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("activate_submit_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == "sw") "Thibitisha na Washa Leseni" else "Verify & Activate License",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Pricing Packages Overview
                Text(
                    text = if (language == "sw") "Vifurushi vya Leseni (Hakuna Tena Jaribio):" else "License Packages (Paid):",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                val plans = listOf(
                    Triple(if (language == "sw") "Mwezi 1 (Monthly)" else "1 Month (Monthly)", "TZS 5,000", if (language == "sw") "Siku 30" else "30 Days"),
                    Triple(if (language == "sw") "Miezi 3 (Quarterly)" else "3 Months (Quarterly)", "TZS 12,000", if (language == "sw") "Siku 90" else "90 Days"),
                    Triple(if (language == "sw") "Miezi 6 (6 Months)" else "6 Months", "TZS 24,000", if (language == "sw") "Siku 180" else "180 Days"),
                    Triple(if (language == "sw") "Mwaka 1 (Yearly)" else "1 Year (Yearly)", "TZS 40,000", if (language == "sw") "Siku 365" else "365 Days"),
                    Triple(if (language == "sw") "Maisha Yote (Lifetime)" else "Lifetime (No Expiry)", "TZS 60,000", if (language == "sw") "Bila Kikomo cha Muda" else "Lifetime (No Expiry)")
                )

                plans.forEach { (name, price, desc) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = price,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // WhatsApp Help Button for License Generation & Support
                Button(
                    onClick = {
                        val msg = "Habari AGRITECH HUB, Nahitaji code ya leseni kwa kifaa chenye ID: $installationId"
                        openWhatsApp("255627318891", msg)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.Chat, contentDescription = "WhatsApp")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (language == "sw") "Wasiliana Kupata Leseni (WhatsApp)" else "Contact via WhatsApp for License", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
