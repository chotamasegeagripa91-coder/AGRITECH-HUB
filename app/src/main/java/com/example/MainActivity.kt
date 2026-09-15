package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.models.CustomerEntity
import com.example.data.models.MaterialEntity
import com.example.data.models.QuoteEntity
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.AgritechHubTheme
import com.example.ui.viewmodel.AgritechViewModel
import com.example.ui.viewmodel.AgritechViewModelFactory
import com.example.ui.viewmodel.AppScreen

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var mainViewModel: AgritechViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            // Guard against legacy custom vendor window controllers
        }

        setContent {
            val viewModel: AgritechViewModel = viewModel(
                factory = AgritechViewModelFactory(application)
            )
            LaunchedEffect(viewModel) {
                mainViewModel = viewModel
            }

            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val language by viewModel.language.collectAsStateWithLifecycle()
            val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
            val isUserRegistered by viewModel.isUserRegistered.collectAsStateWithLifecycle()
            val userAccount by viewModel.userAccount.collectAsStateWithLifecycle()
            val lastCloudSyncTime by viewModel.lastCloudSyncTime.collectAsStateWithLifecycle()
            val isCloudSyncing by viewModel.isCloudSyncing.collectAsStateWithLifecycle()
            val isPasswordConfigured by viewModel.isPasswordConfigured.collectAsStateWithLifecycle()
            val autoLockEnabled by viewModel.autoLockEnabled.collectAsStateWithLifecycle()

            val licenseInfo by viewModel.licenseInfo.collectAsStateWithLifecycle()
            val installationId = viewModel.getInstallationId()
            val businessSettings by viewModel.businessSettings.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

            val materials by viewModel.materials.collectAsStateWithLifecycle()
            val customers by viewModel.customers.collectAsStateWithLifecycle()
            val quotes by viewModel.quotes.collectAsStateWithLifecycle()

            var showActivationDialog by remember { mutableStateOf(false) }
            var editingMaterial by remember { mutableStateOf<MaterialEntity?>(null) }
            var showAddMaterialDialog by remember { mutableStateOf(false) }
            var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
            var showAddCustomerDialog by remember { mutableStateOf(false) }
            var selectedQuoteForDetails by remember { mutableStateOf<QuoteEntity?>(null) }
            var selectedQuoteForPreview by remember { mutableStateOf<QuoteEntity?>(null) }
            var preselectedCustomerForQuote by remember { mutableStateOf<CustomerEntity?>(null) }

            AgritechHubTheme(darkTheme = isDarkMode) {
                if (!isUserRegistered) {
                    AuthScreen(
                        language = language,
                        onToggleLanguage = { viewModel.toggleLanguage() },
                        onRegister = { bName, oName, ph, em, pwd ->
                            viewModel.registerUser(bName, oName, ph, em, pwd)
                        },
                        onRegisterDirectly = { bName, oName, ph, em, pwd, firebaseUid ->
                            viewModel.registerUserDirectly(bName, oName, ph, em, pwd, firebaseUid)
                        },
                        onLogin = { emOrPhone, pwd, onResult ->
                            viewModel.loginUser(emOrPhone, pwd, onComplete = onResult)
                        },
                        onLoginWithFirebase = { em, pwd, uid, onResult ->
                            viewModel.loginUser(em, pwd, firebaseUid = uid, onComplete = onResult)
                        },
                        onLoginWithGoogle = { email, displayName, uid, onResult ->
                            viewModel.loginWithGoogle(firebaseUid = uid, email = email, displayName = displayName, onComplete = onResult)
                        },
                        onRequestForgotOtp = { emailOrPhone ->
                            viewModel.requestForgotPasswordOtp(emailOrPhone)
                        },
                        onResetPasswordOtp = { emailOrPhone, otpCode, newPassword ->
                            viewModel.resetPasswordWithOtp(emailOrPhone, otpCode, newPassword)
                        },
                        onResendOtp = { emailOrPhone ->
                            viewModel.resendOtp(emailOrPhone)
                        }
                    )
                } else if (isLocked) {
                    LockScreen(
                        businessName = businessSettings.name,
                        language = language,
                        isPasswordConfigured = isPasswordConfigured,
                        onToggleLanguage = { viewModel.toggleLanguage() },
                        onCreatePassword = { password -> viewModel.createInitialPassword(password) },
                        onUnlock = { password -> 
                            if (password.isEmpty()) {
                                viewModel.unlockWithBiometric()
                                true
                            } else {
                                viewModel.unlockApp(password)
                            }
                        },
                        onSwitchAccount = { viewModel.logoutUser() }
                    )
                } else {
                    // Back handler to navigate back to dashboard first
                    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD) {
                        viewModel.navigateTo(AppScreen.DASHBOARD)
                    }

                    Scaffold(
                        topBar = {
                            AppTopBar(
                                businessName = businessSettings.name,
                                licenseInfo = licenseInfo,
                                isDarkMode = isDarkMode,
                                language = language,
                                onToggleTheme = { viewModel.toggleDarkMode() },
                                onToggleLanguage = { viewModel.toggleLanguage() },
                                onOpenLicense = { showActivationDialog = true },
                                onLockApp = { viewModel.lockApp() }
                            )
                        },
                        bottomBar = {
                            AppBottomBar(
                                currentScreen = currentScreen,
                                language = language,
                                onSelectScreen = { screen -> viewModel.navigateTo(screen) }
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentScreen) {
                                AppScreen.DASHBOARD -> {
                                    DashboardScreen(
                                        quotes = quotes,
                                        materials = materials,
                                        customers = customers,
                                        licenseInfo = licenseInfo,
                                        language = language,
                                        onNavigate = { screen -> viewModel.navigateTo(screen) },
                                        onSelectQuote = { quote -> selectedQuoteForDetails = quote },
                                        onOpenLicense = { showActivationDialog = true },
                                        lastSyncTime = lastCloudSyncTime,
                                        isSyncing = isCloudSyncing,
                                        onSyncCloud = { viewModel.syncDataToCloud() }
                                    )
                                }

                                AppScreen.MATERIALS -> {
                                    MaterialsScreen(
                                        materials = materials,
                                        language = language,
                                        onAddMaterial = { showAddMaterialDialog = true },
                                        onEditMaterial = { mat -> editingMaterial = mat },
                                        onDeleteMaterial = { mat -> viewModel.deleteMaterial(mat) },
                                        onDeleteAllMaterials = { viewModel.deleteAllMaterials() },
                                        onDeleteMaterialsByCategory = { cat -> viewModel.deleteMaterialsByCategory(cat) },
                                        onImportExcel = { inputStream -> viewModel.importMaterialsFromExcel(inputStream) }
                                    )
                                }

                                AppScreen.CUSTOMERS -> {
                                    CustomersScreen(
                                        customers = customers,
                                        quotes = quotes,
                                        language = language,
                                        onAddCustomer = { showAddCustomerDialog = true },
                                        onEditCustomer = { cust -> editingCustomer = cust },
                                        onDeleteCustomer = { cust -> viewModel.deleteCustomer(cust) },
                                        onNewQuoteForCustomer = { cust ->
                                            preselectedCustomerForQuote = cust
                                            viewModel.navigateTo(AppScreen.NEW_QUOTE)
                                        }
                                    )
                                }

                                AppScreen.NEW_QUOTE -> {
                                    NewQuoteScreen(
                                        customers = customers,
                                        materials = materials,
                                        preselectedCustomer = preselectedCustomerForQuote,
                                        initialQuoteNumber = viewModel.getNextQuoteNumber(),
                                        language = language,
                                        onSaveQuote = { quote ->
                                            viewModel.insertQuote(quote)
                                            preselectedCustomerForQuote = null
                                            viewModel.navigateTo(AppScreen.QUOTES_HISTORY)
                                        },
                                        onAddCustomer = { newCust -> viewModel.insertCustomer(newCust) }
                                    )
                                }

                                AppScreen.QUOTES_HISTORY -> {
                                    QuotesHistoryScreen(
                                        quotes = quotes,
                                        language = language,
                                        onSelectQuote = { quote -> selectedQuoteForDetails = quote },
                                        onNewQuote = { viewModel.navigateTo(AppScreen.NEW_QUOTE) }
                                    )
                                }

                                AppScreen.SETTINGS -> {
                                    SettingsScreen(
                                        businessSettings = businessSettings,
                                        licenseInfo = licenseInfo,
                                        installationId = installationId,
                                        isDarkMode = isDarkMode,
                                        language = language,
                                        autoLockEnabled = autoLockEnabled,
                                        onSaveBusinessSettings = { newSettings -> viewModel.updateBusinessSettings(newSettings) },
                                        onToggleAutoLock = { enabled -> viewModel.setAutoLockEnabled(enabled) },
                                        onToggleDarkMode = { viewModel.toggleDarkMode() },
                                        onToggleLanguage = { viewModel.toggleLanguage() },
                                        onOpenLicenseDialog = { showActivationDialog = true },
                                        onExportBackup = { viewModel.exportBackupJson() },
                                        onImportBackup = { json -> viewModel.importBackupJson(json) },
                                        userAccount = userAccount,
                                        lastSyncTime = lastCloudSyncTime,
                                        isSyncing = isCloudSyncing,
                                        onSyncCloud = { viewModel.syncDataToCloud() },
                                        onRestoreCloud = { viewModel.restoreDataFromCloud() },
                                        onDeleteDemoData = { viewModel.deleteDemoData() },
                                        onChangePassword = { old, new -> viewModel.changePassword(old, new) },
                                        onLogout = { viewModel.logoutUser() }
                                    )
                                }
                            }
                        }
                    }

                    // Dialogs
                    if (showActivationDialog) {
                        ActivationDialog(
                            licenseInfo = licenseInfo,
                            installationId = installationId,
                            language = language,
                            onDismiss = { showActivationDialog = false },
                            onActivate = { key, customerName ->
                                viewModel.activateLicense(key, customerName)
                            }
                        )
                    }

                    if (showAddMaterialDialog || editingMaterial != null) {
                        MaterialEditDialog(
                            material = editingMaterial,
                            language = language,
                            availableCategories = materials.map { it.category }.distinct(),
                            onDismiss = {
                                showAddMaterialDialog = false
                                editingMaterial = null
                            },
                            onSave = { mat ->
                                if (editingMaterial != null) {
                                    viewModel.updateMaterial(mat)
                                } else {
                                    viewModel.insertMaterial(mat)
                                }
                                showAddMaterialDialog = false
                                editingMaterial = null
                            }
                        )
                    }

                    if (showAddCustomerDialog || editingCustomer != null) {
                        CustomerEditDialog(
                            customer = editingCustomer,
                            language = language,
                            onDismiss = {
                                showAddCustomerDialog = false
                                editingCustomer = null
                            },
                            onSave = { cust ->
                                if (editingCustomer != null) {
                                    viewModel.updateCustomer(cust)
                                } else {
                                    viewModel.insertCustomer(cust)
                                }
                                showAddCustomerDialog = false
                                editingCustomer = null
                            }
                        )
                    }

                    if (selectedQuoteForDetails != null) {
                        QuoteDetailsDialog(
                            quote = selectedQuoteForDetails!!,
                            businessSettings = businessSettings,
                            language = language,
                            onDismiss = { selectedQuoteForDetails = null },
                            onConvertToInvoice = { id, newNum ->
                                viewModel.convertToInvoice(id, newNum)
                            },
                            onSetPaidStatus = { id, paid ->
                                viewModel.setQuotePaidStatus(id, paid)
                            },
                            onOpenPrintPreview = { quote ->
                                selectedQuoteForPreview = quote
                            },
                            onDelete = { quote ->
                                viewModel.deleteQuote(quote)
                                selectedQuoteForDetails = null
                            }
                        )
                    }

                    if (selectedQuoteForPreview != null) {
                        QuotePrintPreviewDialog(
                            quote = selectedQuoteForPreview!!,
                            businessSettings = businessSettings,
                            language = language,
                            onDismiss = { selectedQuoteForPreview = null }
                        )
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        mainViewModel?.recordUserActivity()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel?.onAppForegrounded()
    }
}
