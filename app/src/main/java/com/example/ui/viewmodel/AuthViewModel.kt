package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferences
import com.example.data.logger.AppLogger
import com.example.data.model.LoginPageState
import com.example.data.model.UserSession
import com.example.data.repository.AuthRepository
import com.example.data.security.BiometricAuthenticator
import com.example.data.security.BiometricCapabilityChecker
import com.example.data.security.BiometricHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val userPrefs: UserPreferences,
    private val biometricCapabilityChecker: BiometricCapabilityChecker
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        AuthRepository(),
        UserPreferences(application),
        BiometricCapabilityChecker { BiometricHelper.canAuthenticate(it) }
    )

    constructor(application: Application, authRepository: AuthRepository) : this(
        application,
        authRepository,
        UserPreferences(application),
        BiometricCapabilityChecker { BiometricHelper.canAuthenticate(it) }
    )

    constructor(
        application: Application,
        authRepository: AuthRepository,
        userPrefs: UserPreferences
    ) : this(
        application,
        authRepository,
        userPrefs,
        BiometricCapabilityChecker { BiometricHelper.canAuthenticate(it) }
    )

    private val _isEncryptionAvailable = MutableStateFlow(userPrefs.isEncryptionAvailable)
    val isEncryptionAvailable: StateFlow<Boolean> = _isEncryptionAvailable.asStateFlow()

    private val _rememberMe = MutableStateFlow(userPrefs.isEncryptionAvailable && userPrefs.rememberMe)
    val rememberMe: StateFlow<Boolean> = _rememberMe.asStateFlow()

    private val _requiresBiometricUnlock = MutableStateFlow(false)
    val requiresBiometricUnlock: StateFlow<Boolean> = _requiresBiometricUnlock.asStateFlow()

    private val _biometricUnavailableWhileLocked = MutableStateFlow(false)
    val biometricUnavailableWhileLocked: StateFlow<Boolean> = _biometricUnavailableWhileLocked.asStateFlow()

    private val _canQuickFillSavedCredentials = MutableStateFlow(false)
    val canQuickFillSavedCredentials: StateFlow<Boolean> = _canQuickFillSavedCredentials.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isPasswordVisible = MutableStateFlow(false)
    val isPasswordVisible: StateFlow<Boolean> = _isPasswordVisible.asStateFlow()

    private val _captchaInput = MutableStateFlow("")
    val captchaInput: StateFlow<String> = _captchaInput.asStateFlow()

    private val _pageState = MutableStateFlow(LoginPageState())
    val pageState: StateFlow<LoginPageState> = _pageState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isCaptchaRefreshing = MutableStateFlow(false)
    val isCaptchaRefreshing: StateFlow<Boolean> = _isCaptchaRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isCompatibilityModeActive: StateFlow<Boolean> = com.example.data.network.CullinanHttpClient.isCompatibilityModeActive

    private var loadPageJob: Job? = null
    private var isBiometricUnlockedForCurrentForm: Boolean = false
    private var suppressAutoFillAfterLogout: Boolean = false

    init {
        syncCredentialState(initialLoad = true)
        AppLogger.logDebug(
            "AuthViewModel",
            "Init",
            "AuthViewModel initialized (rememberMe=${_rememberMe.value}, encryptionAvailable=${_isEncryptionAvailable.value}, biometricRequired=${_requiresBiometricUnlock.value}, failClosedUnavailable=${_biometricUnavailableWhileLocked.value})"
        )
        loadLoginPage()
    }

    fun toggleCompatibilityMode() {
        val current = com.example.data.network.CullinanHttpClient.isCompatibilityModeActive.value
        val next = !current
        com.example.data.network.CullinanHttpClient.setCompatibilityMode(next)
        AppLogger.logAction("Login SSL Compatibility toggled", "enabled=$next")
        loadLoginPage()
    }

    fun syncCredentialState(initialLoad: Boolean = false) {
        val encryptionOk = userPrefs.isEncryptionAvailable
        _isEncryptionAvailable.value = encryptionOk
        if (!encryptionOk) {
            _rememberMe.value = false
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = false
            _canQuickFillSavedCredentials.value = false
            isBiometricUnlockedForCurrentForm = false
            suppressAutoFillAfterLogout = false
            if (initialLoad) {
                _username.value = ""
                _password.value = ""
                _isPasswordVisible.value = false
            }
            return
        }

        val rem = userPrefs.rememberMe
        _rememberMe.value = rem

        if (!rem || !userPrefs.hasSavedCredentials()) {
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = false
            _canQuickFillSavedCredentials.value = false
            isBiometricUnlockedForCurrentForm = false
            suppressAutoFillAfterLogout = false
            if (initialLoad) {
                _username.value = ""
                _password.value = ""
                _isPasswordVisible.value = false
            }
            return
        }

        val lockEnabled = userPrefs.isBiometricLockEnabled
        val canAuth = biometricCapabilityChecker.canAuthenticate(getApplication())

        if (lockEnabled) {
            _canQuickFillSavedCredentials.value = false
            if (!canAuth) {
                // Fail-closed: lock is ON, device auth unavailable -> NEVER auto-fill credentials
                _username.value = ""
                _password.value = ""
                _isPasswordVisible.value = false
                isBiometricUnlockedForCurrentForm = false
                _requiresBiometricUnlock.value = false
                _biometricUnavailableWhileLocked.value = true
            } else {
                _biometricUnavailableWhileLocked.value = false
                if (initialLoad || !isBiometricUnlockedForCurrentForm || _password.value.isEmpty()) {
                    _username.value = ""
                    _password.value = ""
                    _isPasswordVisible.value = false
                    isBiometricUnlockedForCurrentForm = false
                    _requiresBiometricUnlock.value = true
                }
            }
        } else {
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = false
            if (suppressAutoFillAfterLogout) {
                _canQuickFillSavedCredentials.value = _password.value.isEmpty()
            } else {
                _canQuickFillSavedCredentials.value = false
                if (initialLoad || (_username.value.isBlank() && _password.value.isBlank())) {
                    _username.value = userPrefs.savedUsername
                    _password.value = userPrefs.savedPassword
                    _isPasswordVisible.value = false
                }
            }
        }
    }

    fun unlockWithBiometrics(authenticator: BiometricAuthenticator?) {
        if (!userPrefs.isEncryptionAvailable || !userPrefs.rememberMe || !userPrefs.hasSavedCredentials()) {
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = false
            _isPasswordVisible.value = false
            isBiometricUnlockedForCurrentForm = false
            return
        }

        if (userPrefs.isBiometricLockEnabled && !biometricCapabilityChecker.canAuthenticate(getApplication())) {
            _username.value = ""
            _password.value = ""
            _isPasswordVisible.value = false
            isBiometricUnlockedForCurrentForm = false
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = true
            _errorMessage.value = "احراز هویت دستگاه در دسترس نیست؛ بازیابی رمز ذخیره‌شده مسدود شد."
            return
        }

        if (authenticator == null) {
            onBiometricAuthError("امکان نمایش پنجره احراز هویت بیومتریک در این صفحه وجود ندارد.")
            return
        }

        authenticator.authenticate(
            title = "تأیید هویت امنیتی",
            subtitle = "برای استفاده از اطلاعات ورود ذخیره‌شده، هویت خود را با اثر انگشت یا رمز دستگاه تأیید کنید.",
            onSuccess = { unlockSavedCredentialsAfterBiometric() },
            onError = { errMsg -> onBiometricAuthError(errMsg) },
            onCancelled = { onBiometricAuthCancelled() }
        )
    }

    fun unlockSavedCredentialsAfterBiometric() {
        if (!userPrefs.isEncryptionAvailable || !userPrefs.rememberMe || !userPrefs.hasSavedCredentials()) {
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = false
            _isPasswordVisible.value = false
            isBiometricUnlockedForCurrentForm = false
            return
        }
        if (userPrefs.isBiometricLockEnabled && !biometricCapabilityChecker.canAuthenticate(getApplication())) {
            _username.value = ""
            _password.value = ""
            _isPasswordVisible.value = false
            isBiometricUnlockedForCurrentForm = false
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = true
            _errorMessage.value = "احراز هویت دستگاه در دسترس نیست؛ بازیابی رمز ذخیره‌شده مسدود شد."
            return
        }
        _username.value = userPrefs.savedUsername
        _password.value = userPrefs.savedPassword
        // Password remains masked after unlock until user explicitly toggles visibility
        _isPasswordVisible.value = false
        isBiometricUnlockedForCurrentForm = true
        suppressAutoFillAfterLogout = false
        _requiresBiometricUnlock.value = false
        _biometricUnavailableWhileLocked.value = false
        _canQuickFillSavedCredentials.value = false
        _errorMessage.value = null
        AppLogger.logInfo("AuthViewModel", "BiometricUnlockSuccess", "Populated saved credentials after biometric verification")
    }

    fun onBiometricAuthCancelled() {
        _isPasswordVisible.value = false
        if (userPrefs.isBiometricLockEnabled && !isBiometricUnlockedForCurrentForm) {
            _username.value = ""
            _password.value = ""
            val canAuth = biometricCapabilityChecker.canAuthenticate(getApplication())
            val hasSaved = userPrefs.isEncryptionAvailable && userPrefs.rememberMe && userPrefs.hasSavedCredentials()
            _requiresBiometricUnlock.value = hasSaved && canAuth
            _biometricUnavailableWhileLocked.value = hasSaved && !canAuth
        }
        AppLogger.logInfo("AuthViewModel", "BiometricUnlockCancelled", "Biometric unlock cancelled by user; credentials remain locked")
    }

    fun onBiometricAuthError(message: String) {
        _isPasswordVisible.value = false
        if (userPrefs.isBiometricLockEnabled && !isBiometricUnlockedForCurrentForm) {
            _username.value = ""
            _password.value = ""
        }
        _errorMessage.value = message
    }

    fun clearSavedCredentialsForRecovery() {
        userPrefs.clearCredentials()
        userPrefs.isBiometricLockEnabled = false
        _rememberMe.value = false
        _username.value = ""
        _password.value = ""
        _isPasswordVisible.value = false
        isBiometricUnlockedForCurrentForm = false
        suppressAutoFillAfterLogout = false
        _requiresBiometricUnlock.value = false
        _biometricUnavailableWhileLocked.value = false
        _canQuickFillSavedCredentials.value = false
        _errorMessage.value = null
        AppLogger.logInfo("AuthViewModel", "BiometricRecoveryWipe", "Cleared saved credentials and reset biometric lock for recovery")
    }

    fun refillSavedCredentialsAfterLogout() {
        if (!userPrefs.isEncryptionAvailable || !userPrefs.rememberMe || !userPrefs.hasSavedCredentials()) {
            _canQuickFillSavedCredentials.value = false
            return
        }
        if (userPrefs.isBiometricLockEnabled) {
            // Never bypass biometric lock
            syncCredentialState(initialLoad = false)
            return
        }
        _username.value = userPrefs.savedUsername
        _password.value = userPrefs.savedPassword
        _isPasswordVisible.value = false
        suppressAutoFillAfterLogout = false
        _canQuickFillSavedCredentials.value = false
        _errorMessage.value = null
    }

    fun togglePasswordVisibility() {
        _isPasswordVisible.value = !_isPasswordVisible.value
    }

    fun setPasswordVisible(visible: Boolean) {
        _isPasswordVisible.value = visible
    }

    fun onUsernameChange(newVal: String) {
        _username.value = newVal
        suppressAutoFillAfterLogout = false
        _canQuickFillSavedCredentials.value = false
        _errorMessage.value = null
    }

    fun onPasswordChange(newVal: String) {
        _password.value = newVal
        suppressAutoFillAfterLogout = false
        _canQuickFillSavedCredentials.value = false
        _errorMessage.value = null
    }

    fun onCaptchaChange(newVal: String) {
        // Enforce max 4 digits
        val digitsOnly = newVal.filter { it.isDigit() }.take(4)
        _captchaInput.value = digitsOnly
        _errorMessage.value = null
    }

    fun toggleRememberMe() {
        if (!userPrefs.isEncryptionAvailable) {
            _rememberMe.value = false
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = false
            _canQuickFillSavedCredentials.value = false
            _isPasswordVisible.value = false
            _errorMessage.value = "رمزنگاری امن سخت‌افزاری روی این دستگاه در دسترس نیست؛ گزینه «مرا به خاطر بسپار» غیرفعال است."
            return
        }
        val next = !_rememberMe.value
        _rememberMe.value = next
        userPrefs.rememberMe = next
        AppLogger.logInfo("AuthViewModel", "RememberMeToggled", "rememberMe=$next")
        if (!next) {
            userPrefs.clearCredentials()
            isBiometricUnlockedForCurrentForm = false
            suppressAutoFillAfterLogout = false
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = false
            _canQuickFillSavedCredentials.value = false
            _isPasswordVisible.value = false
        }
    }

    fun loadLoginPage() {
        loadPageJob?.cancel()
        AppLogger.logInfo("AuthViewModel", "LoadLoginPage", "Loading initial Login.aspx state and captcha")
        loadPageJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.fetchLoginPage()
            result.onSuccess { state ->
                _pageState.value = state
                AppLogger.logInfo(
                    "AuthViewModel",
                    "LoadLoginPageSuccess",
                    "Login page ready (hasCaptcha=${state.captchaBytes != null}, pemLen=${state.publicKeyPem.length})"
                )
            }.onFailure { error ->
                val msg = error.message ?: "خطا در برقراری ارتباط با سرور سامانه تغذیه"
                _errorMessage.value = msg
                AppLogger.logError("AuthViewModel", "LoadLoginPage failed: $msg", error, operation = "LoadLoginPageFailure", errorCategory = "LOGIN_PAGE_FETCH_ERROR")
            }
            _isLoading.value = false
        }
    }

    fun refreshCaptcha() {
        AppLogger.logInfo("AuthViewModel", "RefreshCaptcha", "Refreshing captcha image")
        viewModelScope.launch {
            _isCaptchaRefreshing.value = true
            val result = authRepository.refreshCaptcha()
            result.onSuccess { bytes ->
                _pageState.value = _pageState.value.copy(captchaBytes = bytes)
                _captchaInput.value = ""
                AppLogger.logDebug("AuthViewModel", "RefreshCaptchaSuccess", "Captcha refreshed (${bytes.size} bytes)")
            }.onFailure { error ->
                _errorMessage.value = error.message
                AppLogger.logWarn(
                    component = "AuthViewModel",
                    operation = "RefreshCaptchaFailed",
                    message = "Captcha refresh failed: ${error.message}",
                    throwable = error,
                    errorCategory = "CAPTCHA_REFRESH_FAILED"
                )
            }
            _isCaptchaRefreshing.value = false
        }
    }

    fun login(onSuccess: (UserSession) -> Unit) {
        val user = _username.value.trim()
        val pass = _password.value.trim()
        val captcha = _captchaInput.value.trim()

        if (user.isEmpty()) {
            AppLogger.logWarn("AuthViewModel", "LoginValidationFailed", "Username empty", errorCategory = "VALIDATION_ERROR")
            _errorMessage.value = "لطفاً نام کاربری را وارد نمایید."
            return
        }
        if (pass.isEmpty()) {
            AppLogger.logWarn("AuthViewModel", "LoginValidationFailed", "Password empty", errorCategory = "VALIDATION_ERROR")
            _errorMessage.value = "لطفاً رمز عبور را وارد نمایید."
            return
        }
        if (captcha.isEmpty()) {
            AppLogger.logWarn("AuthViewModel", "LoginValidationFailed", "Captcha empty", errorCategory = "VALIDATION_ERROR")
            _errorMessage.value = "لطفاً کد امنیتی را وارد نمایید."
            return
        }

        AppLogger.logInfo("AuthViewModel", "LoginSubmitted", "Submitting login form (rememberMe=${_rememberMe.value}, captchaLen=${captcha.length})")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = authRepository.login(
                usernamePlain = user,
                passwordPlain = pass,
                captchaInput = captcha,
                pageState = _pageState.value
            )

            result.onSuccess { session ->
                if (_rememberMe.value && userPrefs.isEncryptionAvailable) {
                    userPrefs.rememberMe = true
                    userPrefs.saveCredentials(user, pass)
                } else {
                    userPrefs.clearCredentials()
                }
                _username.value = ""
                _password.value = ""
                _isPasswordVisible.value = false
                _captchaInput.value = ""
                isBiometricUnlockedForCurrentForm = false
                suppressAutoFillAfterLogout = false
                _requiresBiometricUnlock.value = false
                _biometricUnavailableWhileLocked.value = false
                _canQuickFillSavedCredentials.value = false
                AppLogger.logInfo("AuthViewModel", "LoginSuccess", "Login succeeded (hasUnreadMessages=${session.hasUnreadMessages})")
                onSuccess(session)
            }.onFailure { error ->
                if (!_rememberMe.value || !userPrefs.isEncryptionAvailable) {
                    userPrefs.clearCredentials()
                }
                val msg = error.message ?: "ورود به سامانه با خطا مواجه شد"
                _errorMessage.value = msg
                AppLogger.logWarn(
                    component = "AuthViewModel",
                    operation = "LoginFailure",
                    message = "Login rejected or failed: $msg; auto-refreshing captcha",
                    throwable = error,
                    errorCategory = "LOGIN_FAILED"
                )
                // Refresh captcha on failure as ASP.NET invalidates the previous captcha
                refreshCaptcha()
            }

            _isLoading.value = false
        }
    }

    fun onLogout() {
        _captchaInput.value = ""
        _errorMessage.value = null
        // Always clear transient in-memory credentials and password visibility on logout
        _username.value = ""
        _password.value = ""
        _isPasswordVisible.value = false
        isBiometricUnlockedForCurrentForm = false

        if (!_rememberMe.value || !userPrefs.isEncryptionAvailable) {
            userPrefs.clearCredentials()
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = false
            _canQuickFillSavedCredentials.value = false
            suppressAutoFillAfterLogout = false
        } else if (userPrefs.isBiometricLockEnabled) {
            val canAuth = biometricCapabilityChecker.canAuthenticate(getApplication())
            val hasSaved = userPrefs.hasSavedCredentials()
            _requiresBiometricUnlock.value = hasSaved && canAuth
            _biometricUnavailableWhileLocked.value = hasSaved && !canAuth
            _canQuickFillSavedCredentials.value = false
            suppressAutoFillAfterLogout = false
        } else {
            val hasSaved = userPrefs.hasSavedCredentials()
            _requiresBiometricUnlock.value = false
            _biometricUnavailableWhileLocked.value = false
            suppressAutoFillAfterLogout = hasSaved
            _canQuickFillSavedCredentials.value = hasSaved
        }
        loadLoginPage()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
