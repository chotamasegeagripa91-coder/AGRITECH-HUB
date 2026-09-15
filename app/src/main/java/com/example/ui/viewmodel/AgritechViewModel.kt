package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.AgritechCloudService
import com.example.data.licensing.LicensingEngine
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.models.*
import com.example.data.repository.AgritechRepository
import com.example.data.security.SecurityUtils
import com.example.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

enum class AppScreen {
    DASHBOARD,
    MATERIALS,
    CUSTOMERS,
    NEW_QUOTE,
    QUOTES_HISTORY,
    SETTINGS
}
class AgritechViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val cloudService = AgritechCloudService(application)
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = AgritechRepository(
        materialDao = database.materialDao(),
        customerDao = database.customerDao(),
        quoteDao = database.quoteDao(),
        preferences = preferences
    )
    private val networkMonitor = NetworkMonitor(application)
    private val syncMutex = Mutex()

    val isOnline: Flow<Boolean> = networkMonitor.isOnline

    val materials: StateFlow<List<MaterialEntity>> = repository.allMaterials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quotes: StateFlow<List<QuoteEntity>> = repository.allQuotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _userAccount = MutableStateFlow(preferences.getUserAccount())
    val userAccount: StateFlow<UserAccount?> = _userAccount.asStateFlow()

    private val _isUserRegistered = MutableStateFlow(preferences.isUserRegisteredAndVerified())
    val isUserRegistered: StateFlow<Boolean> = _isUserRegistered.asStateFlow()

    private val _lastCloudSyncTime = MutableStateFlow(preferences.getLastCloudSyncTime())
    val lastCloudSyncTime: StateFlow<Long> = _lastCloudSyncTime.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _isPasswordConfigured = MutableStateFlow(preferences.hasPasswordSet())
    val isPasswordConfigured: StateFlow<Boolean> = _isPasswordConfigured.asStateFlow()

    private val _isLocked = MutableStateFlow(!preferences.isUserRegisteredAndVerified() || preferences.isAutoLockEnabled() || !preferences.hasPasswordSet())
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _autoLockEnabled = MutableStateFlow(preferences.isAutoLockEnabled())
    val autoLockEnabled: StateFlow<Boolean> = _autoLockEnabled.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _businessSettings = MutableStateFlow(preferences.getBusinessSettings())
    val businessSettings: StateFlow<BusinessSettings> = _businessSettings.asStateFlow()

    private val _licenseInfo = MutableStateFlow(preferences.getLicenseInfo())
    val licenseInfo: StateFlow<LicenseInfo> = _licenseInfo.asStateFlow()

    private val _isDarkMode = MutableStateFlow(preferences.isDarkMode())
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _language = MutableStateFlow(preferences.getLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    private var lastUserActivityTimestamp = System.currentTimeMillis()

    init {
        refreshLicenseInfo()
        viewModelScope.launch(Dispatchers.IO) {
            if (!preferences.isDemoDataInitialized()) {
                repository.ensureDemoDataSeeded()
            }
            val uid = preferences.getUserAccount()?.userId ?: ""
            repository.ensureBuiltinMaterialsSeeded(uid)
            repository.cleanupDuplicateMaterialsOnce()
        }
        // Automatically sync data with Firebase whenever an internet connection is detected
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online && _isUserRegistered.value) {
                    triggerAutoSync(silent = true, delayMs = 1200L)

                }
            }
        }
    }
    fun recordUserActivity() {
        lastUserActivityTimestamp = System.currentTimeMillis()

    }
    fun onAppForegrounded() {
        if (_autoLockEnabled.value && !_isLocked.value) {
            val elapsed = System.currentTimeMillis() - lastUserActivityTimestamp
            if (elapsed >= 120_000L) { // 2 minutes
                _isLocked.value = true
            }
        }
        recordUserActivity()

    }
    fun getInstallationId(): String {
        return preferences.getInstallationId()

    }
    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen

    }
    fun createInitialPassword(password: String): Boolean {
        if (password.isNotBlank()) {
            preferences.setPassword(password.trim())
            _isPasswordConfigured.value = true
            return true
        }
        return false

    }
    fun unlockApp(password: String): Boolean {
        if (preferences.verifyPassword(password)) {
            _isLocked.value = false
            _currentScreen.value = AppScreen.DASHBOARD
            return true
        }
        return false

    }
    fun unlockWithBiometric() {
        _isLocked.value = false
        _currentScreen.value = AppScreen.DASHBOARD
    }
    fun lockApp() {
        _isLocked.value = true

    }
    fun setAutoLockEnabled(enabled: Boolean) {
        _autoLockEnabled.value = enabled
        preferences.setAutoLockEnabled(enabled)

    }
    // ==========================================
    // CLOUD AUTHENTICATION & TRIAL REGISTRATION
    // ==========================================

    fun registerUser(
        businessName: String,
        ownerFullName: String,
        phone: String,
        email: String,
        password: String
    ): AgritechCloudService.ServerResponse<UserAccount> {
        return registerUserDirectly(businessName, ownerFullName, phone, email, password)

    }
    /**
     * Direct registration using Firebase Email and Password.
     * Activates 30-day trial immediately, no SMS OTP needed.
     */
    fun registerUserDirectly(
        businessName: String,
        ownerFullName: String,
        phone: String,
        email: String,
        password: String,
        firebaseUid: String? = null
    ): AgritechCloudService.ServerResponse<UserAccount> {
        val installationId = getInstallationId()
        val response = cloudService.registerUserDirectly(
            businessName = businessName,
            ownerFullName = ownerFullName,
            phone = phone,
            email = email,
            password = password,
            installationId = installationId,
            firebaseUid = firebaseUid
        )

        if (response.success && response.data != null) {
            val account = response.data
            runBlocking(Dispatchers.IO) { 
                repository.clearAllLocalData(preserveDemo = true) 
                repository.ensureBuiltinMaterialsSeeded(account.userId)
            }
            preferences.saveUserAccount(account)
            preferences.setPassword(password)
            preferences.saveBiometricCredentials("email", email, password)
            preferences.setTrialStartDate(account.trialStartDate)

            val currentBiz = preferences.getBusinessSettings()
            val updatedBiz = currentBiz.copy(
                name = account.businessName,
                phone1 = if (account.phoneNumber.isNotBlank()) account.phoneNumber else currentBiz.phone1,
                email = account.email
            )
            preferences.saveBusinessSettings(updatedBiz)
            _businessSettings.value = updatedBiz

            _isPasswordConfigured.value = true
            _isUserRegistered.value = true
            _userAccount.value = account
            _isLocked.value = false
            _currentScreen.value = AppScreen.DASHBOARD
            refreshLicenseInfo()

            triggerAutoSync(silent = true, delayMs = 500L)
        }
        return response

    }
    fun verifyRegistrationOtp(
        emailOrPhone: String,
        otpCode: String
    ): AgritechCloudService.ServerResponse<UserAccount> {
        val installationId = getInstallationId()
        val response = cloudService.verifyRegistrationOtp(emailOrPhone, otpCode, installationId)
        if (response.success && response.data != null) {
            val account = response.data
            preferences.saveUserAccount(account)
            // Start automatic 30-day trial locally
            preferences.setTrialStartDate(account.trialStartDate)

            // Update business identity to the registered business name
            val currentBiz = preferences.getBusinessSettings()
            val updatedBiz = currentBiz.copy(
                name = account.businessName,
                phone1 = account.phoneNumber,
                email = account.email
            )
            preferences.saveBusinessSettings(updatedBiz)
            _businessSettings.value = updatedBiz

            _isPasswordConfigured.value = true
            _isUserRegistered.value = true
            _userAccount.value = account
            _isLocked.value = false
            refreshLicenseInfo()

            viewModelScope.launch(Dispatchers.IO) {
                repository.ensureBuiltinMaterialsSeeded(account.userId)
                // Initial cloud backup synchronization
                triggerAutoSync(silent = true, delayMs = 500L)
            }
        }
        return response

    }
    fun completeFirebaseVerifiedRegistration(
        emailOrPhone: String,
        firebaseUid: String? = null
    ): AgritechCloudService.ServerResponse<UserAccount> {
        val installationId = getInstallationId()
        val response = cloudService.completeFirebaseVerifiedRegistration(emailOrPhone, installationId, firebaseUid)
        if (response.success && response.data != null) {
            val account = response.data
            preferences.saveUserAccount(account)
            preferences.setTrialStartDate(account.trialStartDate)

            val currentBiz = preferences.getBusinessSettings()
            val updatedBiz = currentBiz.copy(
                name = account.businessName,
                phone1 = account.phoneNumber,
                email = account.email
            )
            preferences.saveBusinessSettings(updatedBiz)
            _businessSettings.value = updatedBiz

            _isPasswordConfigured.value = true
            _isUserRegistered.value = true
            _userAccount.value = account
            _isLocked.value = false
            refreshLicenseInfo()

            viewModelScope.launch(Dispatchers.IO) {
                repository.ensureBuiltinMaterialsSeeded(account.userId)
                triggerAutoSync(silent = true, delayMs = 500L)
            }
        }
        return response

    }
    fun getFirebaseDiagnostics(): com.example.data.cloud.FirebasePhoneAuthManager.FirebaseDiagnostics {
        return com.example.data.cloud.FirebasePhoneAuthManager.getDiagnostics(getApplication())

    }
    fun loginUser(
        emailOrPhone: String,
        password: String,
        autoRestoreCloudData: Boolean = true,
        firebaseUid: String? = null,
        onComplete: (AgritechCloudService.ServerResponse<UserAccount>, Boolean) -> Unit
    ) {
        val installationId = getInstallationId()
        val response = if (!firebaseUid.isNullOrBlank()) {
            cloudService.loginOrProvisionFromFirebase(emailOrPhone, password, firebaseUid, installationId)
        } else {
            cloudService.login(emailOrPhone, password, installationId)
        }
        if (response.success && response.data != null) {
            val account = response.data
            runBlocking(Dispatchers.IO) { 
                repository.clearAllLocalData(preserveDemo = true) 
                if (!autoRestoreCloudData) {
                    repository.ensureBuiltinMaterialsSeeded(account.userId)
                }
            }
            preferences.saveUserAccount(account)
            preferences.setPassword(password)
            preferences.saveBiometricCredentials("email", emailOrPhone, password)

            // Update business identity to registered business name
            val currentBiz = preferences.getBusinessSettings()
            val updatedBiz = currentBiz.copy(
                name = account.businessName,
                phone1 = if (account.phoneNumber.isNotBlank()) account.phoneNumber else currentBiz.phone1,
                email = account.email
            )
            preferences.saveBusinessSettings(updatedBiz)
            _businessSettings.value = updatedBiz

            // Check if user has active paid license or trial
            if (!account.paidLicenseKey.isNullOrBlank()) {
                preferences.activateLicense(account.paidLicenseKey, account.businessName)
            } else if (account.trialStartDate != null) {
                preferences.setTrialStartDate(account.trialStartDate)

            }
            _isPasswordConfigured.value = true
            _isUserRegistered.value = true
            _userAccount.value = account
            _isLocked.value = false
            _currentScreen.value = AppScreen.DASHBOARD
            refreshLicenseInfo()

            if (autoRestoreCloudData) {
                viewModelScope.launch(Dispatchers.IO) {
                    val restoreRes = cloudService.restoreBackupFromCloud(account.userId)
                    var restoredData = false
                    if (restoreRes.success && restoreRes.data != null) {
                        repository.restoreFromPayload(restoreRes.data, account.userId)
                        repository.cleanupDuplicateMaterialsOnce()
                        _businessSettings.value = preferences.getBusinessSettings()
                        preferences.setLastCloudSyncTime(restoreRes.data.timestamp)
                        _lastCloudSyncTime.value = restoreRes.data.timestamp
                        refreshLicenseInfo()
                        restoredData = true
                    }
                    repository.ensureBuiltinMaterialsSeeded(account.userId)
                    // Trigger immediate background sync right after successful login & restore check
                    triggerAutoSync(silent = true, delayMs = 600L)
                    withContext(Dispatchers.Main) {
                        onComplete(response, restoredData)
                    }
                }
                return
            } else {
                triggerAutoSync(silent = true, delayMs = 600L)
            }
        }
        onComplete(response, false)

    }
    /**
     * Completes authentication and provisions or links an account authenticated via Google Sign-In.
     */
    fun loginWithGoogle(
        firebaseUid: String,
        email: String,
        displayName: String?,
        autoRestoreCloudData: Boolean = true,
        onComplete: (AgritechCloudService.ServerResponse<UserAccount>, Boolean) -> Unit
    ) {
        val installationId = getInstallationId()
        val response = cloudService.loginOrProvisionFromGoogle(
            firebaseUid = firebaseUid,
            email = email,
            displayName = displayName,
            currentInstallationId = installationId
        )
        if (response.success && response.data != null) {
            val account = response.data
            runBlocking(Dispatchers.IO) { 
                repository.clearAllLocalData(preserveDemo = true) 
                if (!autoRestoreCloudData) {
                    repository.ensureBuiltinMaterialsSeeded(account.userId)
                }
            }
            preferences.saveUserAccount(account)
            preferences.saveBiometricCredentials("google", email, firebaseUid, displayName)

            // Update business identity
            val currentBiz = preferences.getBusinessSettings()
            val updatedBiz = currentBiz.copy(
                name = if (account.businessName.isNotBlank() && currentBiz.name == "AGRITECH ELECTRICAL SOLUTIONS") account.businessName else currentBiz.name,
                phone1 = if (account.phoneNumber.isNotBlank()) account.phoneNumber else currentBiz.phone1,
                email = account.email
            )
            preferences.saveBusinessSettings(updatedBiz)
            _businessSettings.value = updatedBiz

            if (!account.paidLicenseKey.isNullOrBlank()) {
                preferences.activateLicense(account.paidLicenseKey, account.businessName)
            } else if (account.trialStartDate != null) {
                preferences.setTrialStartDate(account.trialStartDate)

            }
            _isPasswordConfigured.value = true
            _isUserRegistered.value = true
            _userAccount.value = account
            _isLocked.value = false
            _currentScreen.value = AppScreen.DASHBOARD
            refreshLicenseInfo()

            if (autoRestoreCloudData) {
                viewModelScope.launch(Dispatchers.IO) {
                    val restoreRes = cloudService.restoreBackupFromCloud(account.userId)
                    var restoredData = false
                    if (restoreRes.success && restoreRes.data != null) {
                        repository.restoreFromPayload(restoreRes.data, account.userId)
                        repository.cleanupDuplicateMaterialsOnce()
                        _businessSettings.value = preferences.getBusinessSettings()
                        preferences.setLastCloudSyncTime(restoreRes.data.timestamp)
                        _lastCloudSyncTime.value = restoreRes.data.timestamp
                        refreshLicenseInfo()
                        restoredData = true
                    }
                    repository.ensureBuiltinMaterialsSeeded(account.userId)
                    // Trigger immediate background sync right after successful Google login & restore check
                    triggerAutoSync(silent = true, delayMs = 600L)
                    withContext(Dispatchers.Main) {
                        onComplete(response, restoredData)
                    }
                }
                return
            } else {
                triggerAutoSync(silent = true, delayMs = 600L)
            }
        }
        onComplete(response, false)

    }
    fun isRememberMe(): Boolean = preferences.isRememberMe()
    fun setRememberMe(remember: Boolean) = preferences.setRememberMe(remember)
    fun getSavedLoginEmail(): String = preferences.getSavedLoginEmail()
    fun setSavedLoginEmail(email: String) = preferences.setSavedLoginEmail(email)

    fun requestForgotPasswordOtp(emailOrPhone: String): AgritechCloudService.ServerResponse<String> {
        return cloudService.requestForgotPasswordOtp(emailOrPhone)

    }
    fun resetPasswordWithOtp(
        emailOrPhone: String,
        otpCode: String,
        newPassword: String
    ): AgritechCloudService.ServerResponse<UserAccount> {
        val res = cloudService.resetPasswordWithOtp(emailOrPhone, otpCode, newPassword)
        if (res.success && res.data != null) {
            preferences.saveUserAccount(res.data)
            preferences.setPassword(newPassword)
            _userAccount.value = res.data
            _isPasswordConfigured.value = true
        }
        return res

    }
    fun resendOtp(emailOrPhone: String): AgritechCloudService.ServerResponse<String> {
        return cloudService.resendOtp(emailOrPhone)

    }
    /**
     * Automatically and safely synchronizes data with Firebase in the background.
     * Guaranteed not to block UI, not to duplicate requests (Mutex protected),
     * filters out demo data, and prevents duplicate records.
     */
    fun triggerAutoSync(silent: Boolean = true, delayMs: Long = 800L) {
        val user = _userAccount.value ?: preferences.getUserAccount()
        if (user == null || !user.isVerified) return

        viewModelScope.launch(Dispatchers.IO) {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            if (!networkMonitor.isConnected()) return@launch

            if (syncMutex.tryLock()) {
                try {
                    _isCloudSyncing.value = true
                    // Filter out DEMO/SAMPLE items so they are NEVER uploaded as real user data to Firestore
                    val realMaterials = materials.value.filter { !com.example.ui.utils.DemoUtils.isDemoMaterial(it) 
                    }
                    val realCustomers = customers.value.filter { !com.example.ui.utils.DemoUtils.isDemoCustomer(it) 
                    }
                    val realQuotes = quotes.value.filter { !com.example.ui.utils.DemoUtils.isDemoQuote(it) } 

                    val payload = CloudBackupPayload(
                        version = 1,
                        timestamp = System.currentTimeMillis(),
                        userId = user.userId,
                        business = preferences.getBusinessSettings(),
                        materials = realMaterials,
                        customers = realCustomers,
                        quotes = realQuotes,
                        licenseInfo = licenseInfo.value
                    )

                    val res = cloudService.syncBackupToCloud(payload)
                    if (res.success) {
                        preferences.setLastCloudSyncTime(payload.timestamp)
                        _lastCloudSyncTime.value = payload.timestamp
                    }
                } catch (e: Exception) {
                    // Silent background catch
                } finally {
                    _isCloudSyncing.value = false
                    syncMutex.unlock()

                }
            }
        }
    }
    fun syncDataToCloud(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = _userAccount.value ?: preferences.getUserAccount()
        if (user == null || !user.isVerified) {
            onComplete(false, "Hakuna akaunti iliyothibitishwa.")
            return

        }
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            try {
                // Filter out DEMO/SAMPLE items so they are NEVER uploaded as real user data to Firestore
                val realMaterials = materials.value.filter { !com.example.ui.utils.DemoUtils.isDemoMaterial(it) 
                }
                val realCustomers = customers.value.filter { !com.example.ui.utils.DemoUtils.isDemoCustomer(it) 
                }
                val realQuotes = quotes.value.filter { !com.example.ui.utils.DemoUtils.isDemoQuote(it) } 

                val payload = CloudBackupPayload(
                    version = 1,
                    timestamp = System.currentTimeMillis(),
                    userId = user.userId,
                    business = preferences.getBusinessSettings(),
                    materials = realMaterials,
                    customers = realCustomers,
                    quotes = realQuotes,
                    licenseInfo = licenseInfo.value
                )

                val res = cloudService.syncBackupToCloud(payload)
                if (res.success) {
                    preferences.setLastCloudSyncTime(payload.timestamp)
                    _lastCloudSyncTime.value = payload.timestamp
                    withContext(Dispatchers.Main) {
                        _isCloudSyncing.value = false
                        onComplete(true, res.message)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isCloudSyncing.value = false
                        onComplete(false, res.message)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    onComplete(false, "Hitilafu: ${e.message}")

                }
            }
        }
    }
    fun restoreDataFromCloud(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = _userAccount.value ?: preferences.getUserAccount()
        if (user == null || !user.isVerified) {
            onComplete(false, "Hakuna akaunti iliyothibitishwa.")
            return

        }
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            try {
                val res = cloudService.restoreBackupFromCloud(user.userId)
                if (res.success && res.data != null) {
                    repository.restoreFromPayload(res.data, user.userId)
                    repository.cleanupDuplicateMaterialsOnce()
                    _businessSettings.value = preferences.getBusinessSettings()
                    preferences.setLastCloudSyncTime(res.data.timestamp)
                    _lastCloudSyncTime.value = res.data.timestamp
                    refreshLicenseInfo()
                    withContext(Dispatchers.Main) {
                        _isCloudSyncing.value = false
                        onComplete(true, res.message)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isCloudSyncing.value = false
                        onComplete(false, res.message)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isCloudSyncing.value = false
                    onComplete(false, "Hitilafu: ${e.message}")

                }
            }
        }
    }
    fun logoutUser() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllLocalData(preserveDemo = true)
            repository.ensureBuiltinMaterialsSeeded("")
        }
        preferences.clearAuthSession()
        _userAccount.value = null
        _businessSettings.value = BusinessSettings()
        _isUserRegistered.value = false
        _isLocked.value = true
        _isPasswordConfigured.value = false

    }
    fun refreshLicenseInfo() {
        _licenseInfo.value = preferences.getLicenseInfo()

    }
    fun toggleDarkMode() {
        val next = !_isDarkMode.value
        _isDarkMode.value = next
        preferences.setDarkMode(next)

    }
    fun toggleLanguage() {
        val next = if (_language.value == "sw") "en" else "sw"
        _language.value = next
        preferences.setLanguage(next)

    }
    fun updateBusinessSettings(newSettings: BusinessSettings) {
        preferences.saveBusinessSettings(newSettings)
        _businessSettings.value = newSettings
        // Sync updated business profile to cloud
        syncDataToCloud()

    }
    fun activateLicense(key: String, customerName: String? = null): Pair<Boolean, String> {
        val result = preferences.activateLicense(key, customerName)
        refreshLicenseInfo()
        if (result.first) {
            val info = preferences.getLicenseInfo()
            val user = _userAccount.value ?: preferences.getUserAccount()
            if (user != null && info.licenseType != null) {
                cloudService.recordPaidLicenseOnServer(
                    userId = user.userId,
                    licenseType = info.licenseType,
                    licenseKey = key,
                    expiresAt = info.expiresAt
                )
            }
        }
        return result

    }
    fun getNextQuoteNumber(): String {
        val quoteList = quotes.value.filter { it.status == "quotation" 
        }
        val numbers = quoteList.mapNotNull { q ->
            val numPart = q.number.replace(Regex("[^0-9]"), "")
            numPart.toIntOrNull()
        }
        val nextNum = if (numbers.isEmpty()) 1 else (numbers.maxOrNull()!! + 1)
        return String.format("QTN-%03d", nextNum)

    }
    fun getNextInvoiceNumber(): String {
        val invoiceList = quotes.value.filter { it.status == "invoice" 
        }
        val numbers = invoiceList.mapNotNull { q ->
            val numPart = q.number.replace(Regex("[^0-9]"), "")
            numPart.toIntOrNull()
        }
        val nextNum = if (numbers.isEmpty()) 1 else (numbers.maxOrNull()!! + 1)
        return String.format("INV-%03d", nextNum)

    }
    // Material Operations
    fun insertMaterial(material: MaterialEntity) {
        val currentUid = userAccount.value?.userId ?: ""
        viewModelScope.launch(Dispatchers.IO) {
            val toInsert = material.copy(
                isDemo = false,
                userId = currentUid
            )
            repository.insertMaterial(toInsert)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    fun updateMaterial(material: MaterialEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateMaterial(material)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    fun deleteMaterial(material: MaterialEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMaterial(material)
            val currentUid = userAccount.value?.userId ?: ""
            cloudService.deleteMaterialFromCloud(material.id, currentUid)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    fun deleteAllMaterials(onComplete: () -> Unit = {}) {
        val currentUid = userAccount.value?.userId ?: ""
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllMaterials()
            preferences.setDemoMaterialsRemoved(true)
            preferences.setDemoDataInitialized(true)
            cloudService.deleteAllMaterialsFromCloud(currentUid)
            triggerAutoSync(silent = true, delayMs = 400L)
            withContext(Dispatchers.Main) {
                onComplete()

            }
        }
    }
    fun deleteMaterialsByCategory(category: String, onComplete: () -> Unit = {}) {
        val currentUid = userAccount.value?.userId ?: ""
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMaterialsByCategory(category)
            if (category.equals(com.example.ui.utils.MaterialCategoryUtils.ELECTRICAL, ignoreCase = true) ||
                category.equals(com.example.ui.utils.MaterialCategoryUtils.PLUMBING, ignoreCase = true) ||
                category.equals(com.example.ui.utils.MaterialCategoryUtils.CONSTRUCTION, ignoreCase = true)
            ) {
                preferences.setDemoMaterialsRemoved(true)
            }
            cloudService.deleteMaterialsByCategoryFromCloud(category, currentUid)
            triggerAutoSync(silent = true, delayMs = 400L)
            withContext(Dispatchers.Main) {
                onComplete()

            }
        }
    }
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val lang = _language.value
        val result = com.example.data.cloud.FirebaseAuthManager.changePassword(
            currentPassword = currentPassword,
            newPassword = newPassword,
            language = lang,
            context = getApplication()
        )
        if (result.success) {
            // Update locally stored password for offline credential continuity
            preferences.setPassword(newPassword)
            val successMsg = if (lang == "sw") "Nenosiri limebadilishwa kikamilifu!" else "Password changed successfully!"
            Pair(true, successMsg)
        } else {
            val errorMsg = result.errorMessage ?: (if (lang == "sw") "Imeshindwa kubadili nenosiri" else "Failed to change password")
            Pair(false, errorMsg)

        }
    }
    fun deleteDemoData(onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDemoData()
            preferences.setDemoMaterialsRemoved(true)
            preferences.setDemoCustomersRemoved(true)
            preferences.setDemoQuotesRemoved(true)
            preferences.setDemoDataInitialized(true)
            withContext(Dispatchers.Main) {
                onComplete()

            }
        }
    }
    suspend fun importMaterialsFromExcel(inputStream: java.io.InputStream): com.example.ui.utils.ExcelImportReport {
        val currentUid = userAccount.value?.userId ?: ""
        return withContext(Dispatchers.IO) {
            repository.importMaterialsFromExcel(inputStream, currentUid)

        }
    }
    // Customer Operations
    fun insertCustomer(customer: CustomerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertCustomer(customer)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    fun updateCustomer(customer: CustomerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCustomer(customer)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCustomer(customer)
            val currentUid = userAccount.value?.userId ?: ""
            cloudService.deleteCustomerFromCloud(customer.id, currentUid)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    // Quote Operations
    fun insertQuote(quote: QuoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertQuote(quote)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    fun updateQuote(quote: QuoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateQuote(quote)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    fun convertToInvoice(id: Int, newNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.convertToInvoice(id, newNumber)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    fun setQuotePaidStatus(id: Int, paid: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setPaidStatus(id, paid)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    fun deleteQuote(quote: QuoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteQuote(quote)
            val currentUid = userAccount.value?.userId ?: ""
            cloudService.deleteQuoteFromCloud(quote.id, currentUid)
            triggerAutoSync(silent = true, delayMs = 600L)

        }
    }
    // Backup & Restore
    fun exportBackupJson(): String {
        return runBlocking(Dispatchers.IO) {
            repository.exportAllDataAsJson(
                materials = materials.value.filter { !com.example.ui.utils.DemoUtils.isDemoMaterial(it) },
                customers = customers.value.filter { !com.example.ui.utils.DemoUtils.isDemoCustomer(it) },
                quotes = quotes.value.filter { !com.example.ui.utils.DemoUtils.isDemoQuote(it) } 
            )

        }
    }
    fun importBackupJson(json: String): Pair<Boolean, String> {
        return runBlocking(Dispatchers.IO) {
            val isSw = _language.value == "sw"
            val result = repository.importDataFromJsonDetailed(json, isSw)
            if (result.first) {
                _businessSettings.value = preferences.getBusinessSettings()
            }
            result

        }
    }
}
class AgritechViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgritechViewModel::class.java)) {
            return AgritechViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
