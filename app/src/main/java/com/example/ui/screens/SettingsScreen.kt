package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.AppPreferences
import com.example.data.models.BusinessSettings
import com.example.data.models.LicenseInfo
import com.example.data.models.LicenseStatus
import com.example.data.models.UserAccount
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.utils.AppStrings
import com.example.ui.utils.ImageUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    businessSettings: BusinessSettings,
    licenseInfo: LicenseInfo,
    installationId: String,
    isDarkMode: Boolean,
    language: String,
    autoLockEnabled: Boolean,
    onSaveBusinessSettings: (BusinessSettings) -> Unit,
    onToggleAutoLock: (Boolean) -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenLicenseDialog: () -> Unit,
    onExportBackup: () -> String,
    onImportBackup: (String) -> Pair<Boolean, String>,
    userAccount: UserAccount? = null,
    lastSyncTime: Long = 0L,
    isSyncing: Boolean = false,
    onSyncCloud: () -> Unit = {},
    onRestoreCloud: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current

    var businessName by remember(businessSettings) { mutableStateOf(businessSettings.name) }
    var slogan by remember(businessSettings) { mutableStateOf(businessSettings.slogan) }
    var phone1 by remember(businessSettings) { mutableStateOf(businessSettings.phone1) }
    var phone2 by remember(businessSettings) { mutableStateOf(businessSettings.phone2) }
    var email by remember(businessSettings) { mutableStateOf(businessSettings.email) }
    var address by remember(businessSettings) { mutableStateOf(businessSettings.address) }
    var bankName by remember(businessSettings) { mutableStateOf(businessSettings.bankName) }
    var bankAccountNumber by remember(businessSettings) { mutableStateOf(businessSettings.bankAccountNumber) }
    var bankAccountName by remember(businessSettings) { mutableStateOf(businessSettings.bankAccountName) }
    var lipaNumber by remember(businessSettings) { mutableStateOf(businessSettings.lipaNumber) }
    var mobileMoney by remember(businessSettings) { mutableStateOf(businessSettings.mobileMoney) }
    var logoPath by remember(businessSettings) { mutableStateOf(businessSettings.logoPath) }
    var signaturePath by remember(businessSettings) { mutableStateOf(businessSettings.signaturePath) }
    var quotationTermsSw by remember(businessSettings) { mutableStateOf(businessSettings.quotationTermsSw) }
    var quotationTermsEn by remember(businessSettings) { mutableStateOf(businessSettings.quotationTermsEn) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = ImageUtils.saveLogoFromUri(context, uri)
            if (saved != null) {
                logoPath = saved
                val updated = businessSettings.copy(
                    name = businessName.trim(),
                    slogan = slogan.trim(),
                    phone1 = phone1.trim(),
                    phone2 = phone2.trim(),
                    email = email.trim(),
                    address = address.trim(),
                    bankName = bankName.trim(),
                    bankAccountNumber = bankAccountNumber.trim(),
                    bankAccountName = bankAccountName.trim(),
                    lipaNumber = lipaNumber.trim(),
                    mobileMoney = mobileMoney.trim(),
                    logoPath = saved,
                    signaturePath = signaturePath.trim(),
                    quotationTermsSw = quotationTermsSw.trim(),
                    quotationTermsEn = quotationTermsEn.trim()
                )
                onSaveBusinessSettings(updated)
                Toast.makeText(context, if (language == "sw") "Logo imewekwa kikamilifu!" else "Logo uploaded successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, if (language == "sw") "Imeshindikana kusoma picha" else "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val signaturePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = ImageUtils.saveSignatureFromUri(context, uri)
            if (saved != null) {
                signaturePath = saved
                val updated = businessSettings.copy(
                    name = businessName.trim(),
                    slogan = slogan.trim(),
                    phone1 = phone1.trim(),
                    phone2 = phone2.trim(),
                    email = email.trim(),
                    address = address.trim(),
                    bankName = bankName.trim(),
                    bankAccountNumber = bankAccountNumber.trim(),
                    bankAccountName = bankAccountName.trim(),
                    lipaNumber = lipaNumber.trim(),
                    mobileMoney = mobileMoney.trim(),
                    logoPath = logoPath.trim(),
                    signaturePath = saved,
                    quotationTermsSw = quotationTermsSw.trim(),
                    quotationTermsEn = quotationTermsEn.trim()
                )
                onSaveBusinessSettings(updated)
                Toast.makeText(context, if (language == "sw") "Saini imewekwa kikamilifu!" else "Signature uploaded successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, if (language == "sw") "Imeshindikana kusoma picha" else "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val appPrefs = remember { AppPreferences(context) }

    var lastSavedBackupName by remember { mutableStateOf(appPrefs.getLastBackupFileName()) }
    var lastSavedBackupTime by remember { mutableStateOf(appPrefs.getLastBackupTimestamp()) }
    var lastSavedBackupSize by remember { mutableStateOf(appPrefs.getLastBackupSize()) }
    var lastSavedLocalFile by remember {
        mutableStateOf<File?>(
            appPrefs.getLastBackupFileName()?.let { name ->
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "backups")
                val f = File(dir, name)
                if (f.exists()) f else null
            }
        )
    }

    var isProcessingBackup by remember { mutableStateOf(false) }
    var isProcessingRestore by remember { mutableStateOf(false) }
    var restoreErrorDialogMessage by remember { mutableStateOf<String?>(null) }
    var restoreSuccessDialogMessage by remember { mutableStateOf<String?>(null) }

    fun shareBackupFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "AGRITECH HUB Data Backup")
                putExtra(
                    Intent.EXTRA_TEXT,
                    if (language == "sw") "Nakala ya Data (Backup) ya mfumo wa AGRITECH HUB - ${file.name}"
                    else "Data Backup file for AGRITECH HUB system - ${file.name}"
                )
                clipData = ClipData.newRawUri("Backup", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(
                    sendIntent,
                    if (language == "sw") "Shiriki / Tuma Faili la Backup" else "Share Backup File"
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingBackup = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val jsonContent = onExportBackup()
                    val bytes = jsonContent.toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                        os.flush()
                    }

                    var actualName: String? = null
                    try {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (idx != -1) actualName = cursor.getString(idx)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore query error
                    }
                    val fileName = actualName ?: "AgritechHub_Backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                    val now = System.currentTimeMillis()
                    val size = bytes.size.toLong()

                    val backupDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "backups")
                    if (!backupDir.exists()) backupDir.mkdirs()
                    val localFile = File(backupDir, fileName)
                    localFile.writeBytes(bytes)

                    appPrefs.setLastBackupInfo(fileName, now, size)

                    withContext(Dispatchers.Main) {
                        lastSavedBackupName = fileName
                        lastSavedBackupTime = now
                        lastSavedBackupSize = size
                        lastSavedLocalFile = localFile
                        isProcessingBackup = false
                        val msg = if (language == "sw")
                            "Faili la backup limehifadhiwa: $fileName"
                        else
                            "Backup file saved: $fileName"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessingBackup = false
                        val errMsg = if (language == "sw")
                            "Hitilafu ya kuhifadhi faili: ${e.localizedMessage ?: "Imeshindikana"}"
                        else
                            "Failed to save backup: ${e.localizedMessage ?: "Unknown error"}"
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingRestore = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        withContext(Dispatchers.Main) {
                            isProcessingRestore = false
                            restoreErrorDialogMessage = if (language == "sw")
                                "Imeshindikana kufungua faili lililochaguliwa."
                            else
                                "Could not open selected file."
                        }
                        return@launch
                    }

                    val jsonContent = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val (success, message) = onImportBackup(jsonContent)

                    withContext(Dispatchers.Main) {
                        isProcessingRestore = false
                        if (success) {
                            restoreSuccessDialogMessage = message
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        } else {
                            restoreErrorDialogMessage = message
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessingRestore = false
                        val err = if (language == "sw")
                            "Hitilafu wakati wa kusoma faili: ${e.localizedMessage ?: "Faili haliko sahihi"}"
                        else
                            "Error reading file: ${e.localizedMessage ?: "Invalid file format"}"
                        restoreErrorDialogMessage = err
                    }
                }
            }
        }
    }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label ${if (language == "sw") "Imenakiliwa!" else "Copied!"}", Toast.LENGTH_SHORT).show()
    }

    fun shareApp() {
        val shareText = if (language == "sw") {
            "⚡ *AGRITECH HUB - Electrical Solution*\n" +
            "Mfumo bora na rahisi wa kutengeneza Makadirio ya Vifaa vya Umeme (Quotations), Ankara za Malipo (Invoices), na Kusimamia Stoo bila intaneti (100% Offline).\n\n" +
            "Wasiliana nasi: +255 627 318 891 / +255 650 549 735\n" +
            "Barua Pepe: agritechelectricalsolution@gmail.com\n" +
            "Powered by Agritech"
        } else {
            "⚡ *AGRITECH HUB - Electrical Solution*\n" +
            "The premier offline electrical materials estimation, quotation, invoicing, and inventory management system (100% Offline).\n\n" +
            "Contact us: +255 627 318 891 / +255 650 549 735\n" +
            "Email: agritechelectricalsolution@gmail.com\n" +
            "Powered by Agritech"
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        try {
            context.startActivity(Intent.createChooser(sendIntent, if (language == "sw") "Shiriki AGRITECH HUB" else "Share AGRITECH HUB"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing app", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
    ) {
        // 1. Business Profile Settings
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == "sw") "Taarifa za Biashara / Kampuni" else "Business Information",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Icon(imageVector = Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text(if (language == "sw") "Jina la Kampuni / Biashara" else "Company / Business Name") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_business_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = slogan,
                        onValueChange = { slogan = it },
                        label = { Text(if (language == "sw") "Kaulimbiu (Slogan)" else "Slogan / Tagline") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phone1,
                            onValueChange = { phone1 = it },
                            label = { Text(if (language == "sw") "Simu 1" else "Phone 1") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = phone2,
                            onValueChange = { phone2 = it },
                            label = { Text(if (language == "sw") "Simu 2" else "Phone 2") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(if (language == "sw") "Barua Pepe (Email)" else "Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(if (language == "sw") "Anwani / Eneo la Ofisi" else "Office Address / Location") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Logo Selection Section
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val logoBitmap: Bitmap? = remember(logoPath) {
                                ImageUtils.loadLogoBitmap(logoPath, 160)
                            }

                            if (logoBitmap != null) {
                                Image(
                                    bitmap = logoBitmap.asImageBitmap(),
                                    contentDescription = "Business Logo",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(1.dp, AmberPrimary, RoundedCornerShape(8.dp))
                                        .padding(2.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (language == "sw") "Nembo ya Biashara (Logo)" else "Business Logo",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (logoPath.isNotBlank()) {
                                        if (language == "sw") "Inaonekana kwenye PDF" else "Appears on PDF exports"
                                    } else {
                                        if (language == "sw") "Haijawekwa bado" else "No logo selected"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = { logoPickerLauncher.launch("image/*") },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp).testTag("select_logo_btn")
                                    ) {
                                        Text(
                                            text = if (logoPath.isNotBlank()) (if (language == "sw") "Badili" else "Change") else (if (language == "sw") "Weka Logo" else "Add Logo"),
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (logoPath.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                logoPath = ""
                                                val updated = businessSettings.copy(logoPath = "")
                                                onSaveBusinessSettings(updated)
                                                Toast.makeText(context, if (language == "sw") "Logo imeondolewa" else "Logo removed", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp).testTag("remove_logo_btn")
                                        ) {
                                            Text(
                                                text = if (language == "sw") "Ondoa" else "Remove",
                                                fontSize = 12.sp,
                                                color = RoseError
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val updated = businessSettings.copy(
                                name = businessName.trim(),
                                slogan = slogan.trim(),
                                phone1 = phone1.trim(),
                                phone2 = phone2.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                bankName = bankName.trim(),
                                bankAccountNumber = bankAccountNumber.trim(),
                                bankAccountName = bankAccountName.trim(),
                                lipaNumber = lipaNumber.trim(),
                                mobileMoney = mobileMoney.trim(),
                                logoPath = logoPath.trim(),
                                signaturePath = signaturePath.trim(),
                                quotationTermsSw = quotationTermsSw.trim(),
                                quotationTermsEn = quotationTermsEn.trim()
                            )
                            onSaveBusinessSettings(updated)
                            Toast.makeText(context, if (language == "sw") "Taarifa zimehifadhiwa!" else "Business info saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("save_business_settings_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (language == "sw") "Hifadhi Taarifa za Biashara" else "Save Business Info", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Payment Accounts Settings (Akaunti za Malipo)
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (language == "sw") "Akaunti za Malipo (Payment Accounts)" else "Payment Account Details",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Text(
                        text = if (language == "sw") "Akaunti hizi za malipo zitaonekana kwenye Ankara za Wateja (Invoices) pekee kwa ajili ya kufanya malipo." else "These payment account details will appear on Client Invoices only for payments.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Company Information automatically reflected from profile
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (language == "sw") "Kampuni / Biashara (Kutoka Usajili):" else "Company / Business (From Registration):",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = businessName.ifBlank { businessSettings.name.ifBlank { if (language == "sw") "Haijawekwa" else "Not set" } },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            val contactDetails = listOf(phone1.ifBlank { businessSettings.phone1 }, email.ifBlank { businessSettings.email }).filter { it.isNotBlank() }
                            if (contactDetails.isNotEmpty()) {
                                Text(
                                    text = contactDetails.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text(if (language == "sw") "Jina la Benki" else "Bank Name") },
                        placeholder = { Text(if (language == "sw") "Mfano: NMB, CRDB, n.k." else "e.g. NMB, CRDB, etc.") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_bank_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = bankAccountNumber,
                        onValueChange = { bankAccountNumber = it },
                        label = { Text(if (language == "sw") "Namba ya Akaunti ya Benki" else "Bank Account Number") },
                        placeholder = { Text("012XXXXXXXXX") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_bank_acc_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = bankAccountName,
                        onValueChange = { bankAccountName = it },
                        label = { Text(if (language == "sw") "Jina la Usajili wa Akaunti" else "Registered Account Name") },
                        placeholder = { Text(businessName.ifBlank { businessSettings.name.ifBlank { if (language == "sw") "Jina la Kampuni au Mmiliki" else "Company or Owner Name" } }) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = lipaNumber,
                        onValueChange = { lipaNumber = it },
                        label = { Text(if (language == "sw") "Lipa Namba / Till" else "Till / Lipa Namba") },
                        placeholder = { Text("012XXXXXXXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = mobileMoney,
                        onValueChange = { mobileMoney = it },
                        label = { Text(if (language == "sw") "Mitandao ya Simu (Mobile Money)" else "Mobile Money (Tigo Pesa / Airtel / M-Pesa)") },
                        placeholder = { Text("012XXXXXXXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            val updated = businessSettings.copy(
                                name = businessName.trim(),
                                slogan = slogan.trim(),
                                phone1 = phone1.trim(),
                                phone2 = phone2.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                bankName = bankName.trim(),
                                bankAccountNumber = bankAccountNumber.trim(),
                                bankAccountName = bankAccountName.trim(),
                                lipaNumber = lipaNumber.trim(),
                                mobileMoney = mobileMoney.trim(),
                                logoPath = logoPath.trim(),
                                signaturePath = signaturePath.trim(),
                                quotationTermsSw = quotationTermsSw.trim(),
                                quotationTermsEn = quotationTermsEn.trim()
                            )
                            onSaveBusinessSettings(updated)
                            Toast.makeText(context, if (language == "sw") "Akaunti za malipo zimehifadhiwa!" else "Payment accounts saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("save_payment_accounts_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (language == "sw") "Hifadhi Akaunti za Malipo" else "Save Payment Accounts", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Business Signature Settings
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == "sw") "Saini ya Mwenye Biashara" else "Business Signature",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = if (language == "sw") "Saini hii itaonekana chini ya makadirio na ankara utakazochapisha au kutuma." else "This signature will appear at the bottom of printed or shared quotations/invoices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val sigBitmap = remember(signaturePath) {
                                if (signaturePath.isNotBlank()) ImageUtils.loadLogoBitmap(signaturePath, 120) else null
                            }
                            if (sigBitmap != null) {
                                Image(
                                    bitmap = sigBitmap.asImageBitmap(),
                                    contentDescription = "Signature",
                                    modifier = Modifier
                                        .size(60.dp, 36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp, 36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Text(
                                text = if (signaturePath.isNotBlank()) (if (language == "sw") "Saini Imewekwa" else "Signature Added") else (if (language == "sw") "Hujaweka Saini" else "No Signature"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Button(
                            onClick = { signaturePickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (signaturePath.isNotBlank()) (if (language == "sw") "Badili Saini" else "Change") else (if (language == "sw") "Weka Saini" else "Add Signature"))
                        }
                    }
                }
            }
        }

        // 2. License & Activation Settings
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (language == "sw") "Hali ya Leseni & Utambulisho" else "License & Identity",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    // Device ID Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Device ID:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = installationId, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { copyToClipboard(installationId, "Device ID") }) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Status
                    val statusText = when (licenseInfo.status) {
                        LicenseStatus.ACTIVE -> {
                            val durationInfo = if (licenseInfo.daysRemaining != null) {
                                if (language == "sw") "Zimebaki Siku ${licenseInfo.daysRemaining}" else "${licenseInfo.daysRemaining} days left"
                            } else {
                                if (language == "sw") "Bila Kikomo" else "Lifetime"
                            }
                            val expiryDate = if (licenseInfo.expiresAt != null) " - ${licenseInfo.expiresAt}" else ""
                            if (language == "sw") "Leseni Iko Hai (${licenseInfo.licenseType?.titleSw ?: "PRO"} - $durationInfo$expiryDate)"
                            else "Active License (${licenseInfo.licenseType?.titleEn ?: "PRO"} - $durationInfo$expiryDate)"
                        }
                        LicenseStatus.TRIAL -> if (language == "sw") "Jaribio la Bure: Siku ${licenseInfo.trialDaysRemaining} zimebaki" else "Free Trial: ${licenseInfo.trialDaysRemaining} days left"
                        LicenseStatus.TRIAL_EXPIRED -> if (language == "sw") "Muda wa Jaribio Umekwisha" else "Trial Expired"
                        LicenseStatus.LICENSE_EXPIRED -> if (language == "sw") "Muda wa Leseni Umekwisha" else "License Expired"
                        LicenseStatus.TAMPERED -> if (language == "sw") "Tahadhari: Saa Imebadilishwa" else "Clock Tampered"
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (licenseInfo.status == LicenseStatus.ACTIVE) Icons.Default.Verified else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (licenseInfo.status == LicenseStatus.ACTIVE) EmeraldSuccess else AmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = statusText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }

                    Button(
                        onClick = onOpenLicenseDialog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_activate_license_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == "sw") "Washa Leseni / Ingiza Code" else "Activate License / Enter Code",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Security & App Preferences
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (language == "sw") "Usalama & Muonekano" else "Security & Appearance",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    // Auto-lock Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = if (language == "sw") "Kufunga App Kiotomatiki (Lock)" else "Auto-Lock App", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(text = if (language == "sw") "Inahitaji nenosiri wakati wa kufungua" else "Requires password when opening app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoLockEnabled, onCheckedChange = onToggleAutoLock)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Dark Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = if (isDarkMode) (if (language == "sw") "Muonekano wa Usiku (Dark Mode)" else "Dark Mode") else (if (language == "sw") "Muonekano wa Mchana (Light Mode)" else "Light Mode"), style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = isDarkMode, onCheckedChange = { onToggleDarkMode() })
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Language Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleLanguage() }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = if (language == "sw") "Lugha ya Mfumo" else "System Language", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = if (language == "sw") "Kiswahili (SW)" else "English (EN)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 4. Share App Feature
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (language == "sw") "Shiriki App na Mafundi / Wateja" else "Share App with Electricians / Clients",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (language == "sw") "Sambaza mfumo huu wa kisasa kwa wataalamu wengine wa umeme na wakandarasi." else "Share this modern offline system with other electrical experts and contractors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { shareApp() },
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("settings_share_app_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (language == "sw") "Shiriki App ya AGRITECH HUB" else "Share AGRITECH HUB App", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Data Backup & Restore
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == "sw") "Hifadhi ya Nakala (Backup & Restore)" else "Data Backup & Restore",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = AmberPrimary
                        )
                    }

                    Text(
                        text = if (language == "sw")
                            "Hifadhi data zako zote (vifaa, wateja, makadirio na ankara) kama faili halisi kwenye simu yako (.json), au rejesha data kwa kuchagua faili la backup kutoka kwenye simu."
                        else
                            "Save all your data (materials, customers, quotes, and invoices) as a real file on your device (.json), or restore data by selecting a backup file from your phone storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val ownerOrBiz = (userAccount?.ownerFullName?.ifBlank { null }
                                    ?: businessSettings.name.ifBlank { null }
                                    ?: "AGRITECH_User").replace(Regex("[^a-zA-Z0-9]"), "_")
                                val suggestedName = "${ownerOrBiz}_AGRITECH_Backup_$timeStamp.json"
                                createBackupLauncher.launch(suggestedName)
                            },
                            enabled = !isProcessingBackup && !isProcessingRestore,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("export_backup_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                        ) {
                            if (isProcessingBackup) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (language == "sw") "Backup Data" else "Backup Data",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                openBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*", "*/*"))
                            },
                            enabled = !isProcessingBackup && !isProcessingRestore,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("import_backup_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isProcessingRestore) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (language == "sw") "Restore Data" else "Restore Data",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Saved Backup File Card
                    if (lastSavedBackupName != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().testTag("saved_backup_file_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AmberPrimary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = AmberPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = lastSavedBackupName ?: "AgritechHub_Backup.json",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1
                                        )
                                        val timeStr = if (lastSavedBackupTime > 0L) {
                                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(lastSavedBackupTime))
                                        } else ""
                                        val sizeStr = if (lastSavedBackupSize > 0L) {
                                            val kb = lastSavedBackupSize / 1024.0
                                            String.format(Locale.getDefault(), "%.1f KB", kb)
                                        } else "JSON"
                                        Text(
                                            text = listOf(sizeStr, timeStr, if (language == "sw") "Hifadhi ya Simu" else "Device Storage")
                                                .filter { it.isNotBlank() }
                                                .joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            var targetFile = lastSavedLocalFile
                                            if (targetFile == null || !targetFile.exists()) {
                                                val backupDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "backups")
                                                if (!backupDir.exists()) backupDir.mkdirs()
                                                val f = File(backupDir, lastSavedBackupName ?: "AgritechHub_Backup.json")
                                                f.writeText(onExportBackup(), Charsets.UTF_8)
                                                targetFile = f
                                                lastSavedLocalFile = f
                                            }
                                            shareBackupFile(targetFile)
                                        },
                                        modifier = Modifier.testTag("share_backup_file_btn")
                                    ) {
                                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (language == "sw") "Shiriki / Nakili Faili" else "Share / Copy File",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5b. Cloud Synchronization & Account Recovery
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == "sw") "Akaunti & Hifadhi ya Wingu (Cloud)" else "Cloud Account & Recovery",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = AmberPrimary
                        )
                    }

                    if (userAccount != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = userAccount.businessName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = AmberPrimary
                                )
                                Text(
                                    text = "Mmiliki: ${userAccount.ownerFullName} • ${userAccount.phoneNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Email: ${userAccount.email}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = if (lastSyncTime > 0) {
                            val timeStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTime))
                            if (language == "sw") "Wingu lilisawazishwa: $timeStr" else "Last cloud sync: $timeStr"
                        } else {
                            if (language == "sw") "Mfumo unafanya kazi bila mtandao (Offline-First)." else "App works 100% offline-first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSyncCloud,
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f).testTag("cloud_sync_now_btn"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == "sw") "Hifadhi Wingu" else "Cloud Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onRestoreCloud,
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f).testTag("cloud_restore_now_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == "sw") "Rejesha Wingu" else "Cloud Restore", fontSize = 12.sp)
                        }
                    }

                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("settings_logout_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp), tint = RoseError)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == "sw") "Toka / Badili Akaunti (Logout)" else "Logout / Switch Account",
                            color = RoseError,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // 6. App & Support Footer with Powered by Agritech
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "AGRITECH HUB v2.5 (Offline Electrical System)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (language == "sw") "Msaada & Huduma: +255 627 318 891 / +255 650 549 735" else "Support & Enquiries: +255 627 318 891 / +255 650 549 735",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
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

    // Restore Error / Invalid File Dialog
    if (restoreErrorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { restoreErrorDialogMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (language == "sw") "Hitilafu ya Kurejesha Data" else "Restore Failed",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = restoreErrorDialogMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { restoreErrorDialogMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Restore Success Dialog
    if (restoreSuccessDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { restoreSuccessDialogMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (language == "sw") "Urejeshaji Umekamilika" else "Restore Complete",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = restoreSuccessDialogMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { restoreSuccessDialogMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
