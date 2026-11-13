package com.example.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.model.UserSession
import com.example.data.security.BiometricHelper
import com.example.ui.components.PersianRtlLayout
import com.example.ui.screens.login.EncryptionUnavailableBanner
import com.example.ui.screens.login.LoginBrandingHeader
import com.example.ui.screens.login.LoginCredentialsCard
import com.example.ui.screens.login.LoginErrorBanner
import com.example.ui.screens.login.LoginSupportNoticeCard
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
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val captchaInput by viewModel.captchaInput.collectAsState()
    val rememberMe by viewModel.rememberMe.collectAsState()
    val isEncryptionAvailable by viewModel.isEncryptionAvailable.collectAsState()
    val requiresBiometricUnlock by viewModel.requiresBiometricUnlock.collectAsState()
    val pageState by viewModel.pageState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCaptchaRefreshing by viewModel.isCaptchaRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isCompatibilityModeActive by viewModel.isCompatibilityModeActive.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.syncCredentialState(initialLoad = false)
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
        captchaInput = captchaInput,
        captchaBitmap = captchaBitmap,
        rememberMe = rememberMe,
        isEncryptionAvailable = isEncryptionAvailable,
        requiresBiometricUnlock = requiresBiometricUnlock,
        isLoading = isLoading,
        isCaptchaRefreshing = isCaptchaRefreshing,
        errorMessage = errorMessage,
        isCompatibilityModeActive = isCompatibilityModeActive,
        onOpenSettings = onOpenSettings,
        onRetryConnection = { viewModel.loadLoginPage() },
        onBiometricUnlockClick = {
            val activity = context.findFragmentActivity()
            if (activity == null) {
                viewModel.onBiometricAuthError("پنجره احراز هویت بیومتریک در این بافت قابل نمایش نیست.")
            } else {
                BiometricHelper.authenticate(
                    activity = activity,
                    onSuccess = { viewModel.unlockSavedCredentialsAfterBiometric() },
                    onError = { errMsg -> viewModel.onBiometricAuthError(errMsg) }
                )
            }
        },
        onUsernameChange = { viewModel.onUsernameChange(it) },
        onPasswordChange = { viewModel.onPasswordChange(it) },
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
    captchaInput: String,
    captchaBitmap: Bitmap?,
    rememberMe: Boolean,
    isEncryptionAvailable: Boolean,
    requiresBiometricUnlock: Boolean,
    isLoading: Boolean,
    isCaptchaRefreshing: Boolean,
    errorMessage: String?,
    isCompatibilityModeActive: Boolean,
    onOpenSettings: (() -> Unit)?,
    onRetryConnection: () -> Unit,
    onBiometricUnlockClick: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
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

                LoginErrorBanner(
                    errorMessage = errorMessage,
                    onRetryConnection = onRetryConnection
                )

                LoginCredentialsCard(
                    username = username,
                    password = password,
                    captchaInput = captchaInput,
                    captchaBitmap = captchaBitmap,
                    rememberMe = rememberMe,
                    isEncryptionAvailable = isEncryptionAvailable,
                    requiresBiometricUnlock = requiresBiometricUnlock,
                    isLoading = isLoading,
                    isCaptchaRefreshing = isCaptchaRefreshing,
                    onBiometricUnlockClick = onBiometricUnlockClick,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange,
                    onCaptchaChange = onCaptchaChange,
                    onRefreshCaptcha = onRefreshCaptcha,
                    onToggleRememberMe = onToggleRememberMe,
                    onSubmitLogin = onSubmitLogin
                )

                Spacer(modifier = Modifier.height(20.dp))

                LoginSupportNoticeCard()
            }
        }
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
            captchaInput = "",
            captchaBitmap = null,
            rememberMe = true,
            isEncryptionAvailable = true,
            requiresBiometricUnlock = true,
            isLoading = false,
            isCaptchaRefreshing = false,
            errorMessage = null,
            isCompatibilityModeActive = false,
            onOpenSettings = {},
            onRetryConnection = {},
            onBiometricUnlockClick = {},
            onUsernameChange = {},
            onPasswordChange = {},
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
            captchaInput = "",
            captchaBitmap = null,
            rememberMe = false,
            isEncryptionAvailable = false,
            requiresBiometricUnlock = false,
            isLoading = false,
            isCaptchaRefreshing = true,
            errorMessage = "ارتباط با سرور دانشگاه برقرار نشد.",
            isCompatibilityModeActive = true,
            onOpenSettings = {},
            onRetryConnection = {},
            onBiometricUnlockClick = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onCaptchaChange = {},
            onRefreshCaptcha = {},
            onToggleRememberMe = {},
            onSubmitLogin = {}
        )
    }
}
