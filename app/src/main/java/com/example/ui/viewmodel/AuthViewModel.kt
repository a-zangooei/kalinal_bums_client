package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferences
import com.example.data.logger.AppLogger
import com.example.data.model.LoginPageState
import com.example.data.model.UserSession
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, AuthRepository())

    private val userPrefs = UserPreferences(application)

    private val _username = MutableStateFlow(userPrefs.savedUsername)
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow(userPrefs.savedPassword)
    val password: StateFlow<String> = _password.asStateFlow()

    private val _captchaInput = MutableStateFlow("")
    val captchaInput: StateFlow<String> = _captchaInput.asStateFlow()

    private val _rememberMe = MutableStateFlow(userPrefs.rememberMe)
    val rememberMe: StateFlow<Boolean> = _rememberMe.asStateFlow()

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
        AppLogger.logDebug("AuthViewModel", "Init", "AuthViewModel initialized (rememberMe=${_rememberMe.value})")
        loadLoginPage()
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
        val next = !_rememberMe.value
        _rememberMe.value = next
        userPrefs.rememberMe = next
        AppLogger.logInfo("AuthViewModel", "RememberMeToggled", "rememberMe=$next")
        if (!next) {
            userPrefs.clearCredentials()
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
                if (_rememberMe.value) {
                    userPrefs.savedUsername = user
                    userPrefs.savedPassword = pass
                } else {
                    userPrefs.clearCredentials()
                    _password.value = ""
                }
                _captchaInput.value = ""
                AppLogger.logInfo("AuthViewModel", "LoginSuccess", "Login succeeded (hasUnreadMessages=${session.hasUnreadMessages})")
                onSuccess(session)
            }.onFailure { error ->
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

    fun clearError() {
        _errorMessage.value = null
    }
}
