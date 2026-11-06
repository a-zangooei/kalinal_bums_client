package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferences
import com.example.data.model.LoginPageState
import com.example.data.model.UserSession
import com.example.data.repository.AuthRepository
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

    init {
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
        if (!next) {
            userPrefs.clearCredentials()
        }
    }

    fun loadLoginPage() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = authRepository.fetchLoginPage()
            result.onSuccess { state ->
                _pageState.value = state
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "خطا در ارتباط با سرور سامانه کالینان"
            }
            _isLoading.value = false
        }
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            _isCaptchaRefreshing.value = true
            val result = authRepository.refreshCaptcha()
            result.onSuccess { bytes ->
                _pageState.value = _pageState.value.copy(captchaBytes = bytes)
                _captchaInput.value = ""
            }.onFailure { error ->
                _errorMessage.value = error.message
            }
            _isCaptchaRefreshing.value = false
        }
    }

    fun login(onSuccess: (UserSession) -> Unit) {
        val user = _username.value.trim()
        val pass = _password.value.trim()
        val captcha = _captchaInput.value.trim()

        if (user.isEmpty()) {
            _errorMessage.value = "لطفاً نام کاربری (شماره دانشجویی) را وارد نمایید"
            return
        }
        if (pass.isEmpty()) {
            _errorMessage.value = "لطفاً گذرواژه (کد ملی) را وارد نمایید"
            return
        }
        if (captcha.isEmpty()) {
            _errorMessage.value = "لطفاً کد امنیتی تصویر را وارد نمایید"
            return
        }

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
                }
                onSuccess(session)
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "ورود به سامانه با خطا مواجه شد"
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
