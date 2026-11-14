package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserSession
import com.example.data.security.BiometricHelper
import com.example.ui.components.PersianRtlLayout
import com.example.ui.screens.login.BiometricUnavailableLockedBanner
import com.example.ui.screens.login.EncryptionUnavailableBanner
import com.example.ui.screens.login.LoginBrandingHeader
import com.example.ui.screens.login.LoginCredentialsCard
import com.example.ui.screens.login.LoginErrorBanner
import com.example.ui.screens.login.LoginTopSettingsAction
import com.example.ui.screens.login.ServerConnectionBadge
import com.example.ui.theme.CullinanTheme
import com.example.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: (UserSession) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val passwordVisible by viewModel.isPasswordVisible.collectAsStateWithLifecycle()
    val captchaInput by viewModel.captchaInput.collectAsStateWithLifecycle()
    val rememberMe by viewModel.rememberMe.collectAsStateWithLifecycle()
    val isEncryptionAvailable by viewModel.isEncryptionAvailable.collectAsStateWithLifecycle()
    val requiresBiometricUnlock by viewModel.requiresBiometricUnlock.collectAsStateWithLifecycle()
    val biometricUnavailableWhileLocked by viewModel.biometricUnavailableWhileLocked.collectAsStateWithLifecycle()
    val canQuickFillSavedCredentials by viewModel.canQuickFillSavedCredentials.collectAsStateWithLifecycle()
    val pageState by viewModel.pageState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isCaptchaRefreshing by viewModel.isCaptchaRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isCompatibilityModeActive by viewModel.isCompatibilityModeActive.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.syncCredentialState(initialLoad = false)
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncCredentialState(initialLoad = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val captchaBitmap = remember(pageState.captchaBytes) {
        pageState.captchaBytes?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
    }

    LoginScreenContent(
        username = username,
        password = password,
        passwordVisible = passwordVisible,
        captchaInput = captchaInput,
        captchaBitmap = captchaBitmap,
        rememberMe = rememberMe,
        isEncryptionAvailable = isEncryptionAvailable,
        requiresBiometricUnlock = requiresBiometricUnlock,
        biometricUnavailableWhileLocked = biometricUnavailableWhileLocked,
        canQuickFillSavedCredentials = canQuickFillSavedCredentials,
        isLoading = isLoading,
        isCaptchaRefreshing = isCaptchaRefreshing,
        errorMessage = errorMessage,
        isCompatibilityModeActive = isCompatibilityModeActive,
        onOpenSettings = onOpenSettings,
        onOpenDeviceSecuritySettings = {
            openDeviceSecuritySettings(context)
        },
        onClearLockedSavedCredentials = {
            viewModel.clearSavedCredentialsForRecovery()
        },
        onRetryConnection = { viewModel.loadLoginPage() },
        onBiometricUnlockClick = {
            val authenticator = context.findFragmentActivity()?.let { BiometricHelper.forActivity(it) }
            viewModel.unlockWithBiometrics(authenticator)
        },
        onQuickFillSavedCredentials = { viewModel.refillSavedCredentialsAfterLogout() },
        onUsernameChange = { viewModel.onUsernameChange(it) },
        onPasswordChange = { viewModel.onPasswordChange(it) },
        onTogglePasswordVisibility = { viewModel.togglePasswordVisibility() },
        onCaptchaChange = { viewModel.onCaptchaChange(it) },
        onRefreshCaptcha = { viewModel.refreshCaptcha() },
        onToggleRememberMe = { viewModel.toggleRememberMe() },
        onSubmitLogin = { viewModel.login(onLoginSuccess) },
        modifier = modifier
    )
}

@Composable
internal fun LoginScreenContent(
    username: String,
    password: String,
    passwordVisible: Boolean,
    captchaInput: String,
    captchaBitmap: Bitmap?,
    rememberMe: Boolean,
    isEncryptionAvailable: Boolean,
    requiresBiometricUnlock: Boolean,
    biometricUnavailableWhileLocked: Boolean,
    canQuickFillSavedCredentials: Boolean,
    isLoading: Boolean,
    isCaptchaRefreshing: Boolean,
    errorMessage: String?,
    isCompatibilityModeActive: Boolean,
    onOpenSettings: (() -> Unit)?,
    onOpenDeviceSecuritySettings: () -> Unit,
    onClearLockedSavedCredentials: () -> Unit,
    onRetryConnection: () -> Unit,
    onBiometricUnlockClick: () -> Unit,
    onQuickFillSavedCredentials: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onCaptchaChange: (String) -> Unit,
    onRefreshCaptcha: () -> Unit,
    onToggleRememberMe: () -> Unit,
    onSubmitLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    PersianRtlLayout {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoginTopSettingsAction(onOpenSettings = onOpenSettings)

                LoginBrandingHeader()

                Spacer(modifier = Modifier.height(20.dp))

                ServerConnectionBadge(isCompatibilityModeActive = isCompatibilityModeActive)

                Spacer(modifier = Modifier.height(12.dp))

                EncryptionUnavailableBanner(visible = !isEncryptionAvailable)

                BiometricUnavailableLockedBanner(
                    visible = biometricUnavailableWhileLocked && isEncryptionAvailable,
                    onOpenSecuritySettings = onOpenDeviceSecuritySettings,
                    onClearSavedCredentials = onClearLockedSavedCredentials
                )

                LoginErrorBanner(
                    errorMessage = errorMessage,
                    onRetryConnection = onRetryConnection
                )

                LoginCredentialsCard(
                    username = username,
                    password = password,
                    passwordVisible = passwordVisible,
                    captchaInput = captchaInput,
                    captchaBitmap = captchaBitmap,
                    rememberMe = rememberMe,
                    isEncryptionAvailable = isEncryptionAvailable,
                    requiresBiometricUnlock = requiresBiometricUnlock,
                    canQuickFillSavedCredentials = canQuickFillSavedCredentials,
                    isLoading = isLoading,
                    isCaptchaRefreshing = isCaptchaRefreshing,
                    onBiometricUnlockClick = onBiometricUnlockClick,
                    onQuickFillSavedCredentials = onQuickFillSavedCredentials,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange,
                    onTogglePasswordVisibility = onTogglePasswordVisibility,
                    onCaptchaChange = onCaptchaChange,
                    onRefreshCaptcha = onRefreshCaptcha,
                    onToggleRememberMe = onToggleRememberMe,
                    onSubmitLogin = onSubmitLogin
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun openDeviceSecuritySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "تنظیمات امنیتی دستگاه از این صفحه قابل بازگشایی نیست.", Toast.LENGTH_SHORT).show()
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Preview(showBackground = true, name = "LoginScreen - Light RTL")
@Composable
private fun LoginScreenLightPreview() {
    CullinanTheme(darkTheme = false) {
        LoginScreenContent(
            username = "4031001234",
            password = "",
            passwordVisible = false,
            captchaInput = "",
            captchaBitmap = null,
            rememberMe = true,
            isEncryptionAvailable = true,
            requiresBiometricUnlock = true,
            biometricUnavailableWhileLocked = false,
            canQuickFillSavedCredentials = false,
            isLoading = false,
            isCaptchaRefreshing = false,
            errorMessage = null,
            isCompatibilityModeActive = false,
            onOpenSettings = {},
            onOpenDeviceSecuritySettings = {},
            onClearLockedSavedCredentials = {},
            onRetryConnection = {},
            onBiometricUnlockClick = {},
            onQuickFillSavedCredentials = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onCaptchaChange = {},
            onRefreshCaptcha = {},
            onToggleRememberMe = {},
            onSubmitLogin = {}
        )
    }
}

@Preview(showBackground = true, name = "LoginScreen - Dark RTL")
@Composable
private fun LoginScreenDarkPreview() {
    CullinanTheme(darkTheme = true) {
        LoginScreenContent(
            username = "",
            password = "",
            passwordVisible = false,
            captchaInput = "",
            captchaBitmap = null,
            rememberMe = true,
            isEncryptionAvailable = true,
            requiresBiometricUnlock = false,
            biometricUnavailableWhileLocked = true,
            canQuickFillSavedCredentials = false,
            isLoading = false,
            isCaptchaRefreshing = true,
            errorMessage = "ارتباط با سرور دانشگاه برقرار نشد.",
            isCompatibilityModeActive = true,
            onOpenSettings = {},
            onOpenDeviceSecuritySettings = {},
            onClearLockedSavedCredentials = {},
            onRetryConnection = {},
            onBiometricUnlockClick = {},
            onQuickFillSavedCredentials = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onCaptchaChange = {},
            onRefreshCaptcha = {},
            onToggleRememberMe = {},
            onSubmitLogin = {}
        )
    }
}
