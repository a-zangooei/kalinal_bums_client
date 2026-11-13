package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.logger.AppLogger
import com.example.data.model.AppUpdateInfo
import com.example.data.repository.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val updateRepository: UpdateRepository = UpdateRepository()
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        // Automatic check on launch
        checkForUpdates(isManual = false)
    }

    fun checkForUpdates(isManual: Boolean) {
        AppLogger.logInfo("UpdateViewModel", "CheckForUpdates", "Initiating update check (isManual=$isManual)")
        viewModelScope.launch {
            _isChecking.value = true
            val result = updateRepository.checkForUpdate()
            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _updateInfo.value = info
                    AppLogger.logInfo("UpdateViewModel", "UpdateAvailable", "New version ${info.latestVersionName} available (current=${info.currentVersionName})")
                } else {
                    AppLogger.logDebug("UpdateViewModel", "AppUpToDate", "App is up to date (${info.currentVersionName})")
                    if (isManual) {
                        _userMessage.value = "برنامه شما به‌روز است (نسخه ${info.currentVersionName})"
                    }
                }
            }.onFailure { error ->
                AppLogger.logWarn(
                    component = "UpdateViewModel",
                    operation = "UpdateCheckFailed",
                    message = "Update check failed (isManual=$isManual): ${error.message}",
                    throwable = error,
                    errorCategory = "UPDATE_CHECK_ERROR"
                )
                if (isManual) {
                    _userMessage.value = "خطا در بررسی به‌روزرسانی: ${error.message}"
                }
            }
            _isChecking.value = false
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }
}
