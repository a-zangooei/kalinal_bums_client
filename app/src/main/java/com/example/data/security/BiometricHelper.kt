package com.example.data.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.logger.AppLogger

fun interface BiometricCapabilityChecker {
    fun canAuthenticate(context: Context): Boolean
}

fun interface BiometricAuthenticator {
    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    )
}

object BiometricHelper {

    fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun canAuthenticate(context: Context): Boolean {
        return try {
            val manager = BiometricManager.from(context)
            manager.canAuthenticate(allowedAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            AppLogger.logWarn("BiometricHelper", "CanAuthenticateError", "Biometric capability check failed: ${e.message}", e)
            false
        }
    }

    fun forActivity(activity: FragmentActivity): BiometricAuthenticator {
        return BiometricAuthenticator { title, subtitle, onSuccess, onError, onCancelled ->
            authenticate(
                activity = activity,
                title = title,
                subtitle = subtitle,
                onSuccess = onSuccess,
                onError = onError,
                onCancelled = onCancelled
            )
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "تایید هویت امنیتی",
        subtitle: String = "برای دسترسی به اعتبارنامه ذخیره‌شده از اثر انگشت یا رمز دستگاه استفاده کنید",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    AppLogger.logInfo("BiometricHelper", "AuthSucceeded", "Device biometric/credential authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    val isCancel = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    AppLogger.logWarn(
                        component = "BiometricHelper",
                        operation = if (isCancel) "AuthCancelled" else "AuthError",
                        message = "Biometric prompt ended (code=$errorCode)"
                    )
                    if (isCancel) {
                        onCancelled()
                    } else {
                        onError(errString.toString().ifBlank { "احراز هویت بیومتریک ناموفق بود" })
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    AppLogger.logDebug("BiometricHelper", "AuthAttemptFailed", "Biometric recognition attempt did not match")
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(allowedAuthenticators())
                .build()

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            AppLogger.logError("BiometricHelper", "Failed to display BiometricPrompt: ${e.message}", e)
            onError("امکان نمایش پنجره احراز هویت بیومتریک در این دستگاه وجود ندارد")
        }
    }
}
