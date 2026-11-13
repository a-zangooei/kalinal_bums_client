package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.local.UserPreferences
import com.example.data.logger.AppLogger
import com.example.data.model.AppThemeMode
import com.example.data.network.CullinanHttpClient
import com.example.data.security.BiometricHelper
import com.example.data.security.BiometricLockCoordinator
import com.example.data.security.BiometricToggleOutcome
import com.example.ui.components.DeveloperLogsDialog
import com.example.ui.components.LogSharePreviewDialog
import com.example.ui.components.PersianRtlLayout
import com.example.ui.components.shareDiagnosticZip
import com.example.ui.screens.settings.AboutVersionSection
import com.example.ui.screens.settings.DeveloperToolsSection
import com.example.ui.screens.settings.NetworkStatusSection
import com.example.ui.screens.settings.SecurityBiometricSection
import com.example.ui.screens.settings.ThemeSelectionSection
import com.example.ui.theme.CullinanTheme

@Composable
fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    onThemeChanged: (AppThemeMode) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onCheckForUpdates: (() -> Unit)? = null,
    onSecuritySettingsChanged: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val userPrefs = remember { UserPreferences(context) }
    val lockCoordinator = remember(context, userPrefs) { BiometricLockCoordinator(context, userPrefs) }

    var isDevUnlocked by remember { mutableStateOf(userPrefs.isDeveloperModeUnlocked) }
    var isEncryptionAvailable by remember { mutableStateOf(userPrefs.isEncryptionAvailable) }
    var isBiometricEnabled by remember { mutableStateOf(userPrefs.isBiometricLockEnabled) }
    var canUseBiometric by remember { mutableStateOf(BiometricHelper.canAuthenticate(context)) }
    var tapCounter by remember { mutableIntStateOf(0) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showSharePreviewDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context, userPrefs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEncryptionAvailable = userPrefs.isEncryptionAvailable
                isBiometricEnabled = userPrefs.isBiometricLockEnabled
                canUseBiometric = BiometricHelper.canAuthenticate(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isCompatibilityModeActive by CullinanHttpClient.isCompatibilityModeActive.collectAsState()

    SettingsScreenContent(
        currentThemeMode = currentThemeMode,
        isEncryptionAvailable = isEncryptionAvailable,
        canUseBiometric = canUseBiometric,
        isBiometricEnabled = isBiometricEnabled,
        baseUrl = CullinanHttpClient.BASE_URL,
        isCompatibilityModeActive = isCompatibilityModeActive,
        isDevUnlocked = isDevUnlocked,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        onNavigateBack = onNavigateBack,
        onSelectTheme = { mode ->
            userPrefs.themeMode = mode
            onThemeChanged(mode)
            AppLogger.logAction("Theme changed to ${mode.name}")
        },
        onToggleBiometric = { enable ->
            val authenticator = context.findFragmentActivity()?.let { BiometricHelper.forActivity(it) }
            lockCoordinator.requestLockChange(
                targetEnabled = enable,
                authenticator = authenticator
            ) { outcome ->
                when (outcome) {
                    is BiometricToggleOutcome.Updated -> {
                        isBiometricEnabled = outcome.isEnabled
                        onSecuritySettingsChanged?.invoke()
                        val msg = if (outcome.isEnabled) {
                            "قفل بیومتریک فعال شد"
                        } else {
                            "قفل بیومتریک غیرفعال شد"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    BiometricToggleOutcome.Cancelled -> {
                        Toast.makeText(context, "احراز هویت لغو شد؛ وضعیت قفل تغییری نکرد", Toast.LENGTH_SHORT).show()
                    }
                    is BiometricToggleOutcome.Failed -> {
                        Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        },
        onOpenDeviceSecuritySettings = {
            try {
                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "تنظیمات امنیتی گوشی از این صفحه قابل باز شدن نیست", Toast.LENGTH_SHORT).show()
            }
        },
        onRecoveryWipeAndDisableLock = {
            lockCoordinator.clearCredentialsAndDisableLock()
            isBiometricEnabled = false
            onSecuritySettingsChanged?.invoke()
            Toast.makeText(context, "رمزهای ذخیره‌شده پاک شد و قفل بیومتریک غیرفعال گردید", Toast.LENGTH_LONG).show()
        },
        onToggleCompatibilityMode = { enable ->
            CullinanHttpClient.setCompatibilityMode(enable)
            AppLogger.logAction("SSL Compatibility Mode toggled: $enable")
        },
        onOpenLogs = { showLogsDialog = true },
        onExportZip = { showSharePreviewDialog = true },
        onClearLogs = {
            AppLogger.clearLogs()
            Toast.makeText(context, "تاریخچه لاگ‌ها با موفقیت پاکسازی شد", Toast.LENGTH_SHORT).show()
        },
        onExitDeveloperMode = {
            isDevUnlocked = false
            userPrefs.isDeveloperModeUnlocked = false
            tapCounter = 0
            Toast.makeText(context, "حالت توسعه‌دهنده غیرفعال شد", Toast.LENGTH_SHORT).show()
        },
        onVersionClick = {
            if (!isDevUnlocked) {
                tapCounter++
                if (tapCounter >= 7) {
                    isDevUnlocked = true
                    userPrefs.isDeveloperModeUnlocked = true
                    Toast.makeText(context, "🎉 تبریک! حالت توسعه‌دهنده فعال شد.", Toast.LENGTH_LONG).show()
                    AppLogger.logAction("Developer Mode unlocked via 7-tap Easter Egg")
                } else if (tapCounter in 3..6) {
                    val remaining = 7 - tapCounter
                    Toast.makeText(context, "تنها $remaining ضربه دیگر تا فعال‌سازی حالت توسعه‌دهنده...", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "حالت توسعه‌دهنده از قبل فعال است", Toast.LENGTH_SHORT).show()
            }
        },
        onCheckForUpdates = onCheckForUpdates,
        modifier = modifier
    )

    // Logs Dialog
    if (showLogsDialog) {
        DeveloperLogsDialog(onDismiss = { showLogsDialog = false })
    }

    // Sanitized Log Preview & Security Warning before ACTION_SEND
    if (showSharePreviewDialog) {
        LogSharePreviewDialog(
            onConfirmShare = {
                showSharePreviewDialog = false
                shareDiagnosticZip(
                    context = context,
                    sslCompatibilityActive = isCompatibilityModeActive
                )
            },
            onDismiss = { showSharePreviewDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreenContent(
    currentThemeMode: AppThemeMode,
    isEncryptionAvailable: Boolean,
    canUseBiometric: Boolean,
    isBiometricEnabled: Boolean,
    baseUrl: String,
    isCompatibilityModeActive: Boolean,
    isDevUnlocked: Boolean,
    versionName: String,
    versionCode: Int,
    onNavigateBack: (() -> Unit)?,
    onSelectTheme: (AppThemeMode) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onOpenDeviceSecuritySettings: () -> Unit,
    onRecoveryWipeAndDisableLock: () -> Unit,
    onToggleCompatibilityMode: (Boolean) -> Unit,
    onOpenLogs: () -> Unit,
    onExportZip: () -> Unit,
    onClearLogs: () -> Unit,
    onExitDeveloperMode: () -> Unit,
    onVersionClick: () -> Unit,
    onCheckForUpdates: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    PersianRtlLayout {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "تنظیمات برنامه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "بازگشت"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            modifier = modifier
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                ThemeSelectionSection(
                    currentThemeMode = currentThemeMode,
                    onSelectTheme = onSelectTheme
                )

                Spacer(modifier = Modifier.height(20.dp))

                SecurityBiometricSection(
                    isEncryptionAvailable = isEncryptionAvailable,
                    canUseBiometric = canUseBiometric,
                    isBiometricEnabled = isBiometricEnabled,
                    onToggleBiometric = onToggleBiometric,
                    onOpenDeviceSecuritySettings = onOpenDeviceSecuritySettings,
                    onRecoveryWipeAndDisableLock = onRecoveryWipeAndDisableLock
                )

                Spacer(modifier = Modifier.height(20.dp))

                NetworkStatusSection(
                    baseUrl = baseUrl,
                    isCompatibilityModeActive = isCompatibilityModeActive,
                    onToggleCompatibilityMode = onToggleCompatibilityMode
                )

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedVisibility(visible = isDevUnlocked) {
                    Column {
                        DeveloperToolsSection(
                            onOpenLogs = onOpenLogs,
                            onExportZip = onExportZip,
                            onClearLogs = onClearLogs,
                            onExitDeveloperMode = onExitDeveloperMode
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                AboutVersionSection(
                    versionName = versionName,
                    versionCode = versionCode,
                    onVersionClick = onVersionClick,
                    onCheckForUpdates = onCheckForUpdates
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Preview(showBackground = true, name = "SettingsScreen - Light RTL")
@Composable
private fun SettingsScreenLightPreview() {
    CullinanTheme(darkTheme = false) {
        SettingsScreenContent(
            currentThemeMode = AppThemeMode.SYSTEM,
            isEncryptionAvailable = true,
            canUseBiometric = true,
            isBiometricEnabled = false,
            baseUrl = "https://jeton.bums.ac.ir",
            isCompatibilityModeActive = false,
            isDevUnlocked = true,
            versionName = "1.0.0",
            versionCode = 66,
            onNavigateBack = {},
            onSelectTheme = {},
            onToggleBiometric = {},
            onOpenDeviceSecuritySettings = {},
            onRecoveryWipeAndDisableLock = {},
            onToggleCompatibilityMode = {},
            onOpenLogs = {},
            onExportZip = {},
            onClearLogs = {},
            onExitDeveloperMode = {},
            onVersionClick = {},
            onCheckForUpdates = {}
        )
    }
}

@Preview(showBackground = true, name = "SettingsScreen - Dark RTL")
@Composable
private fun SettingsScreenDarkPreview() {
    CullinanTheme(darkTheme = true) {
        SettingsScreenContent(
            currentThemeMode = AppThemeMode.DARK,
            isEncryptionAvailable = true,
            canUseBiometric = false,
            isBiometricEnabled = true,
            baseUrl = "https://jeton.bums.ac.ir",
            isCompatibilityModeActive = true,
            isDevUnlocked = true,
            versionName = "1.0.0",
            versionCode = 66,
            onNavigateBack = {},
            onSelectTheme = {},
            onToggleBiometric = {},
            onOpenDeviceSecuritySettings = {},
            onRecoveryWipeAndDisableLock = {},
            onToggleCompatibilityMode = {},
            onOpenLogs = {},
            onExportZip = {},
            onClearLogs = {},
            onExitDeveloperMode = {},
            onVersionClick = {},
            onCheckForUpdates = {}
        )
    }
}
