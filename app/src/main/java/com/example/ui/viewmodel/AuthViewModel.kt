package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferences
import com.example.data.logger.AppLogger
import com.example.data.model.LoginPageState
import com.example.data.model.UserSession
import com.example.data.repository.AuthRepository
import com.example.data.security.BiometricHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val userPrefs: UserPreferences
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, AuthRepository(), UserPreferences(application))

    constructor(application: Application, authRepository: AuthRepository) : this(
        application,
        authRepository,
        UserPreferences(application)
    )

    private val _isEncryptionAvailable = MutableStateFlow(userPrefs.isEncryptionAvailable)
    val isEncryptionAvailable: StateFlow<Boolean> = _isEncryptionAvailable.asStateFlow()

    private val _rememberMe = MutableStateFlow(userPrefs.isEncryptionAvailable && userPrefs.rememberMe)
    val rememberMe: StateFlow<Boolean> = _rememberMe.asStateFlow()

    private val _requiresBiometricUnlock = MutableStateFlow(false)
    val requiresBiometricUnlock: StateFlow<Boolean> = _requiresBiometricUnlock.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

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

    fun toggleCompatibilityMode() {
        val current = com.example.data.network.CullinanHttpClient.isCompatibilityModeActive.value
        val next = !current
        com.example.data.network.CullinanHttpClient.setCompatibilityMode(next)
        AppLogger.logAction("Login SSL Compatibility toggled", "enabled=$next")
        loadLoginPage()
    }

    private var loadPageJob: Job? = null

    init {
        syncCredentialState(initialLoad = true)
        AppLogger.logDebug(
            "AuthViewModel",
            "Init",
            "AuthViewModel initialized (rememberMe=${_rememberMe.value}, encryptionAvailable=${_isEncryptionAvailable.value}, biometricRequired=${_requiresBiometricUnlock.value})"
        )
        loadLoginPage()
    }

    fun syncCredentialState(initialLoad: Boolean = false) {
        val encryptionOk = userPrefs.isEncryptionAvailable
        _isEncryptionAvailable.value = encryptionOk
        if (!encryptionOk) {
            _rememberMe.value = false
            _requiresBiometricUnlock.value = false
            if (initialLoad) {
                _username.value = ""
                _password.value = ""
            }
            return
        }

        val rem = userPrefs.rememberMe
        _rememberMe.value = rem

        if (!rem || !userPrefs.hasSavedCredentials()) {
            _requiresBiometricUnlock.value = false
            if (initialLoad) {
                _username.value = ""
                _password.value = ""
            }
            return
        }

        val biometricGateActive =
            userPrefs.isBiometricLockEnabled && BiometricHelper.canAuthenticate(getApplication())

        if (biometricGateActive) {
            if (_password.value.isEmpty() || initialLoad) {
                _username.value = ""
                _password.value = ""
                _requiresBiometricUnlock.value = true
            }
        } else {
            _requiresBiometricUnlock.value = false
            if (initialLoad || (_username.value.isBlank() && _password.value.isBlank())) {
                _username.value = userPrefs.savedUsername
                _password.value = userPrefs.savedPassword
            }
        }
    }

    fun unlockSavedCredentialsAfterBiometric() {
        if (!userPrefs.isEncryptionAvailable || !userPrefs.hasSavedCredentials()) {
            _requiresBiometricUnlock.value = false
            return
        }
        _username.value = userPrefs.savedUsername
        _password.value = userPrefs.savedPassword
        _requiresBiometricUnlock.value = false
        _errorMessage.value = null
        AppLogger.logInfo("AuthViewModel", "BiometricUnlockSuccess", "Populated saved credentials after biometric verification")
    }

    fun onBiometricAuthError(message: String) {
        _errorMessage.value = message
    }

    fun onUsernameChange(newVal: String) {
        _username.value = newVal
        _errorMessage.value = null
    }

    fun onPasswordChange(newVal: String) {
        _password.value = newVal
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
            _errorMessage.value = "رمزنگاری امن سخت‌افزاری روی این دستگاه در دسترس نیست؛ گزینه «مرا به خاطر بسپار» غیرفعال است."
            return
        }
        val next = !_rememberMe.value
        _rememberMe.value = next
        userPrefs.rememberMe = next
        AppLogger.logInfo("AuthViewModel", "RememberMeToggled", "rememberMe=$next")
        if (!next) {
            userPrefs.clearCredentials()
            _requiresBiometricUnlock.value = false
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
                val msg = error.message ?: "خطا در ارتباط با سرور سامانه کالینان"
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
            _errorMessage.value = "لطفاً نام کاربری (شماره دانشجویی) را وارد نمایید"
            return
        }
        if (pass.isEmpty()) {
            AppLogger.logWarn("AuthViewModel", "LoginValidationFailed", "Password empty", errorCategory = "VALIDATION_ERROR")
            _errorMessage.value = "لطفاً گذرواژه (کد ملی) را وارد نمایید"
            return
        }
        if (captcha.isEmpty()) {
            AppLogger.logWarn("AuthViewModel", "LoginValidationFailed", "Captcha empty", errorCategory = "VALIDATION_ERROR")
            _errorMessage.value = "لطفاً کد امنیتی تصویر را وارد نمایید"
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
                    _password.value = ""
                }
                _captchaInput.value = ""
                _requiresBiometricUnlock.value = false
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
        if (!_rememberMe.value || !userPrefs.isEncryptionAvailable) {
            userPrefs.clearCredentials()
            _username.value = ""
            _password.value = ""
            _requiresBiometricUnlock.value = false
        } else if (userPrefs.isBiometricLockEnabled && BiometricHelper.canAuthenticate(getApplication())) {
            // Re-lock saved credentials so biometric/PIN prompt is required on next login
            _username.value = ""
            _password.value = ""
            _requiresBiometricUnlock.value = userPrefs.hasSavedCredentials()
        }
        loadLoginPage()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
