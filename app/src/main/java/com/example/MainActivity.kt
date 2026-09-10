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

class MainActivity : ComponentActivity() {
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

            // Dialog states
            var editingMaterial by remember { mutableStateOf<MaterialEntity?>(null) }
            var isAddingMaterial by remember { mutableStateOf(false) }

            var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
            var isAddingCustomer by remember { mutableStateOf(false) }

            var selectedQuoteForDetails by remember { mutableStateOf<QuoteEntity?>(null) }
            var selectedQuoteForPrint by remember { mutableStateOf<QuoteEntity?>(null) }

            var showActivationDialog by remember { mutableStateOf(false) }

            var preselectedCustomerForNewQuote by remember { mutableStateOf<CustomerEntity?>(null) }

            AgritechHubTheme(darkTheme = isDarkMode) {
                if (!isUserRegistered) {
                    AuthScreen(
                        language = language,
                        onToggleLanguage = { viewModel.toggleLanguage() },
                        onRegisterDirectly = { bName, oName, ph, em, pwd, uid ->
                            viewModel.registerUserDirectly(bName, oName, ph, em, pwd, uid)
                        },
                        onRegister = { bName, oName, ph, em, pwd ->
                            viewModel.registerUser(bName, oName, ph, em, pwd)
                        },
                        onLogin = { emOrPhone, pwd, onResult ->
                            viewModel.loginUser(emOrPhone, pwd, autoRestoreCloudData = true, onComplete = onResult)
                        },
                        onLoginWithFirebase = { em, pwd, uid, onResult ->
                            viewModel.loginUser(em, pwd, autoRestoreCloudData = true, firebaseUid = uid, onComplete = onResult)
                        },
                        onLoginWithGoogle = { em, dName, uid, onResult ->
                            viewModel.loginWithGoogle(
                                firebaseUid = uid,
                                email = em,
                                displayName = dName,
                                autoRestoreCloudData = true,
                                onComplete = onResult
                            )
                        },
                        onVerifyOtp = { emOrPhone, otp ->
                            viewModel.verifyRegistrationOtp(emOrPhone, otp)
                        },
                        onCompleteFirebaseVerifiedRegistration = { emOrPhone, uid ->
                            viewModel.completeFirebaseVerifiedRegistration(emOrPhone, uid)
                        },
                        onRequestForgotOtp = { id ->
                            viewModel.requestForgotPasswordOtp(id)
                        },
                        onResetPasswordOtp = { id, otp, newPwd ->
                            viewModel.resetPasswordWithOtp(id, otp, newPwd)
                        },
                        onResendOtp = { id ->
                            viewModel.resendOtp(id)
                        }
                    )
                } else if (isLocked) {
                    LockScreen(
                        businessName = businessSettings.name,
                        language = language,
                        isPasswordConfigured = isPasswordConfigured,
                        onToggleLanguage = { viewModel.toggleLanguage() },
                        onCreatePassword = { password -> viewModel.createInitialPassword(password) },
                        onUnlock = { password -> viewModel.unlockApp(password) },
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
                                        onAddMaterial = {
                                            editingMaterial = null
                                            isAddingMaterial = true
                                        },
                                        onEditMaterial = { mat ->
                                            editingMaterial = mat
                                            isAddingMaterial = false
                                        },
                                        onDeleteMaterial = { mat -> viewModel.deleteMaterial(mat) }
                                    )
                                }

                                AppScreen.CUSTOMERS -> {
                                    CustomersScreen(
                                        customers = customers,
                                        quotes = quotes,
                                        language = language,
                                        onAddCustomer = {
                                            editingCustomer = null
                                            isAddingCustomer = true
                                        },
                                        onEditCustomer = { cust ->
                                            editingCustomer = cust
                                            isAddingCustomer = false
                                        },
                                        onDeleteCustomer = { cust -> viewModel.deleteCustomer(cust) },
                                        onNewQuoteForCustomer = { cust ->
                                            preselectedCustomerForNewQuote = cust
                                            viewModel.navigateTo(AppScreen.NEW_QUOTE)
                                        }
                                    )
                                }

                                AppScreen.NEW_QUOTE -> {
                                    NewQuoteScreen(
                                        customers = customers,
                                        materials = materials,
                                        preselectedCustomer = preselectedCustomerForNewQuote,
                                        initialQuoteNumber = viewModel.getNextQuoteNumber(),
                                        language = language,
                                        onSaveQuote = { newQuote ->
                                            viewModel.insertQuote(newQuote)
                                            preselectedCustomerForNewQuote = null
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
                                        onNewQuote = {
                                            preselectedCustomerForNewQuote = null
                                            viewModel.navigateTo(AppScreen.NEW_QUOTE)
                                        }
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
                                        onSaveBusinessSettings = { settings -> viewModel.updateBusinessSettings(settings) },
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
                                        onLogout = { viewModel.logoutUser() }
                                    )
                                }
                            }
                        }
                    }

                    // Material Edit / Add Dialog
                    if (isAddingMaterial || editingMaterial != null) {
                        MaterialEditDialog(
                            material = editingMaterial,
                            language = language,
                            onDismiss = {
                                isAddingMaterial = false
                                editingMaterial = null
                            },
                            onSave = { mat ->
                                if (mat.id == 0) viewModel.insertMaterial(mat)
                                else viewModel.updateMaterial(mat)
                                isAddingMaterial = false
                                editingMaterial = null
                            }
                        )
                    }

                    // Customer Edit / Add Dialog
                    if (isAddingCustomer || editingCustomer != null) {
                        CustomerEditDialog(
                            customer = editingCustomer,
                            language = language,
                            onDismiss = {
                                isAddingCustomer = false
                                editingCustomer = null
                            },
                            onSave = { cust ->
                                if (cust.id == 0) viewModel.insertCustomer(cust)
                                else viewModel.updateCustomer(cust)
                                isAddingCustomer = false
                                editingCustomer = null
                            }
                        )
                    }

                    // Quote Details Dialog
                    if (selectedQuoteForDetails != null) {
                        val currentQ = quotes.find { it.id == selectedQuoteForDetails!!.id } ?: selectedQuoteForDetails!!
                        QuoteDetailsDialog(
                            quote = currentQ,
                            businessSettings = businessSettings,
                            language = language,
                            onDismiss = { selectedQuoteForDetails = null },
                            onConvertToInvoice = { id, _ -> viewModel.convertToInvoice(id, viewModel.getNextInvoiceNumber()) },
                            onSetPaidStatus = { id, paid -> viewModel.setQuotePaidStatus(id, paid) },
                            onOpenPrintPreview = { quote ->
                                selectedQuoteForDetails = null
                                selectedQuoteForPrint = quote
                            },
                            onDelete = { quote ->
                                viewModel.deleteQuote(quote)
                                selectedQuoteForDetails = null
                            }
                        )
                    }

                    // Print / PDF Preview Dialog
                    if (selectedQuoteForPrint != null) {
                        QuotePrintPreviewDialog(
                            quote = selectedQuoteForPrint!!,
                            businessSettings = businessSettings,
                            language = language,
                            onDismiss = { selectedQuoteForPrint = null }
                        )
                    }

                    // License Activation Dialog
                    if (showActivationDialog) {
                        ActivationDialog(
                            licenseInfo = licenseInfo,
                            installationId = installationId,
                            language = language,
                            onDismiss = { showActivationDialog = false },
                            onActivate = { key, customerName -> viewModel.activateLicense(key, customerName) }
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
