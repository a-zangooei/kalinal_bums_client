package com.example.data.security

import android.content.Context
import com.example.data.local.UserPreferences
import com.example.data.logger.AppLogger

sealed class BiometricToggleOutcome {
    data class Updated(
        val isEnabled: Boolean,
        val clearedCredentials: Boolean = false
    ) : BiometricToggleOutcome()

    data object Cancelled : BiometricToggleOutcome()

    data class Failed(
        val message: String,
        val requiresRecoveryWipe: Boolean = false
    ) : BiometricToggleOutcome()
}

/**
 * Coordinates security-sensitive changes to the biometric lock setting so that BOTH
 * enabling and disabling the lock require successful user authentication, and fail-closed
 * recovery removes stored credentials if device authentication becomes unavailable.
 */
class BiometricLockCoordinator(
    private val context: Context,
    private val userPrefs: UserPreferences,
    private val capabilityChecker: BiometricCapabilityChecker = BiometricCapabilityChecker {
        BiometricHelper.canAuthenticate(it)
    }
) {

    fun requestLockChange(
        targetEnabled: Boolean,
        authenticator: BiometricAuthenticator?,
        onResult: (BiometricToggleOutcome) -> Unit
    ) {
        if (!userPrefs.isEncryptionAvailable) {
            AppLogger.logWarn(
                component = "BiometricLockCoordinator",
                operation = "LockChangeBlockedEncryption",
                message = "Rejected lock change (target=$targetEnabled) because encrypted storage is unavailable"
            )
            onResult(BiometricToggleOutcome.Failed("رمزنگاری سخت‌افزاری دستگاه در دسترس نیست."))
            return
        }

        if (!capabilityChecker.canAuthenticate(context)) {
            if (targetEnabled) {
                AppLogger.logWarn(
                    component = "BiometricLockCoordinator",
                    operation = "LockEnableBlockedNoAuth",
                    message = "Cannot enable biometric lock: device authentication unavailable"
                )
                onResult(BiometricToggleOutcome.Failed("ابتدا اثر انگشت یا قفل صفحه را در تنظیمات امنیتی دستگاه فعال نمایید."))
            } else {
                AppLogger.logWarn(
                    component = "BiometricLockCoordinator",
                    operation = "LockDisableBlockedFailClosed",
                    message = "Blocked unauthenticated biometric lock disable while device auth unavailable"
                )
                onResult(
                    BiometricToggleOutcome.Failed(
                        message = "امکان تأیید هویت برای غیرفعال‌سازی قفل وجود ندارد؛ جهت حفظ امنیت، لازم است اطلاعات ورود ذخیره‌شده پاک‌سازی گردد.",
                        requiresRecoveryWipe = true
                    )
                )
            }
            return
        }

        if (authenticator == null) {
            onResult(BiometricToggleOutcome.Failed("پنجره احراز هویت بیومتریک در دسترس نیست."))
            return
        }

        val title = if (targetEnabled) "فعال‌سازی قفل امنیتی" else "غیرفعال‌سازی قفل امنیتی"
        val subtitle = if (targetEnabled) {
            "جهت فعال‌سازی محافظت از رمز عبور ذخیره‌شده، هویت خود را تأیید نمایید."
        } else {
            "جهت غیرفعال‌سازی قفل امنیتی، هویت خود را تأیید نمایید."
        }

        authenticator.authenticate(
            title = title,
            subtitle = subtitle,
            onSuccess = {
                userPrefs.isBiometricLockEnabled = targetEnabled
                AppLogger.logInfo(
                    component = "BiometricLockCoordinator",
                    operation = "LockStateChanged",
                    message = "Biometric lock authenticated change applied: enabled=$targetEnabled"
                )
                onResult(BiometricToggleOutcome.Updated(isEnabled = targetEnabled))
            },
            onError = { err ->
                AppLogger.logWarn(
                    component = "BiometricLockCoordinator",
                    operation = "LockChangeAuthError",
                    message = "Biometric lock change rejected (target=$targetEnabled): $err"
                )
                onResult(BiometricToggleOutcome.Failed(err))
            },
            onCancelled = {
                AppLogger.logInfo(
                    component = "BiometricLockCoordinator",
                    operation = "LockChangeCancelled",
                    message = "Biometric lock change cancelled by user (target=$targetEnabled); state preserved"
                )
                onResult(BiometricToggleOutcome.Cancelled)
            }
        )
    }

    fun clearCredentialsAndDisableLock(): BiometricToggleOutcome.Updated {
        userPrefs.clearCredentials()
        userPrefs.isBiometricLockEnabled = false
        AppLogger.logInfo(
            component = "BiometricLockCoordinator",
            operation = "FailClosedRecoveryWipe",
            message = "Cleared saved credentials and disabled biometric lock via explicit recovery"
        )
        return BiometricToggleOutcome.Updated(isEnabled = false, clearedCredentials = true)
    }
}
