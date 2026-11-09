package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.UserPreferences
import com.example.data.logger.AppLogger
import com.example.data.model.UserSession
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import com.example.ui.components.PersianRtlLayout
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.repository.AuthRepository
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.MenuViewModel
import com.example.ui.viewmodel.UpdateViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(applicationContext)
        AppLogger.logAction("Application Started")

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val userPrefs = remember { UserPreferences(context) }
            var currentThemeMode by remember { mutableStateOf(userPrefs.themeMode) }

            CullinanTheme(themeMode = currentThemeMode) {
                KalinanApp(
                    currentThemeMode = currentThemeMode,
                    onThemeChanged = { newTheme ->
                        currentThemeMode = newTheme
                        userPrefs.themeMode = newTheme
                    }
                )
            }
        }
    }
}

@Composable
fun KalinanApp(
    currentThemeMode: com.example.data.model.AppThemeMode,
    onThemeChanged: (com.example.data.model.AppThemeMode) -> Unit
) {
    var activeSession by remember { mutableStateOf<UserSession?>(null) }
    var isPreLoginSettingsOpen by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableIntStateOf(0) } // 0: Reservation, 1: Settings

    val authViewModel: AuthViewModel = viewModel()
    val menuViewModel: MenuViewModel = viewModel()
    val updateViewModel: UpdateViewModel = viewModel()

    val updateInfo by updateViewModel.updateInfo.collectAsState()
    val userMessage by updateViewModel.userMessage.collectAsState()

    val session = activeSession
    if (session == null) {
        if (isPreLoginSettingsOpen) {
            BackHandler {
                isPreLoginSettingsOpen = false
            }

            SettingsScreen(
                currentThemeMode = currentThemeMode,
                onThemeChanged = onThemeChanged,
                onNavigateBack = { isPreLoginSettingsOpen = false },
                onCheckForUpdates = { updateViewModel.checkForUpdates(isManual = true) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = { userSession ->
                    activeSession = userSession
                    selectedBottomTab = 0
                    AppLogger.logAction("User logged in: ${userSession.username}")
                },
                onOpenSettings = {
                    isPreLoginSettingsOpen = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        var isLoggingOut by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val authRepo = remember { AuthRepository() }

        val handleLogout: () -> Unit = {
            if (!isLoggingOut) {
                isLoggingOut = true
                AppLogger.logAction("User initiated real server logout")
                val currentMenu = menuViewModel.weeklyMenu.value

                coroutineScope.launch {
                    try {
                        val serverResult = authRepo.logoutFromServer(currentMenu)
                        if (serverResult.isSuccess) {
                            Toast.makeText(context, "خروج موفق از سامانه کالینان انجام شد", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "خروج سمت سرور با خطا مواجه شد، اما نشست محلی دستگاه پاک‌سازی شد", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        AppLogger.logError("Logout", "Logout request failed", e)
                        Toast.makeText(context, "خروج سمت سرور با خطا مواجه شد، اما نشست محلی دستگاه پاک‌سازی شد", Toast.LENGTH_LONG).show()
                    } finally {
                        // 1. Purge active local session state
                        activeSession = null
                        selectedBottomTab = 0
                        // 2. Clear local cookies AFTER server request completed
                        com.example.data.network.CullinanHttpClient.cookieJar.clear()
                        // 3. Fetch a fresh Login.aspx page (fresh ViewState, fresh RSA Pem key, and fresh Captcha)
                        authViewModel.loadLoginPage()
                        isLoggingOut = false
                    }
                }
            }
        }

        BackHandler {
            if (selectedBottomTab != 0) {
                selectedBottomTab = 0
            } else {
                handleLogout()
            }
        }

        PersianRtlLayout {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        NavigationBarItem(
                            selected = selectedBottomTab == 0,
                            onClick = {
                                selectedBottomTab = 0
                                AppLogger.logAction("Switched to Reservation tab")
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.RestaurantMenu,
                                    contentDescription = "برنامه غذایی و رزرو"
                                )
                            },
                            label = {
                                Text(
                                    text = "رزرو غذا",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedBottomTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealPrimary,
                                selectedTextColor = TealPrimary,
                                indicatorColor = TealPrimary.copy(alpha = 0.12f)
                            )
                        )

                        NavigationBarItem(
                            selected = selectedBottomTab == 1,
                            onClick = {
                                selectedBottomTab = 1
                                AppLogger.logAction("Switched to Settings tab")
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "تنظیمات برنامه"
                                )
                            },
                            label = {
                                Text(
                                    text = "تنظیمات",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedBottomTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealPrimary,
                                selectedTextColor = TealPrimary,
                                indicatorColor = TealPrimary.copy(alpha = 0.12f)
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { paddingValues ->
                when (selectedBottomTab) {
                    0 -> MenuScreen(
                        userSession = session,
                        viewModel = menuViewModel,
                        onLogout = handleLogout,
                        isLoggingOut = isLoggingOut,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                    1 -> SettingsScreen(
                        currentThemeMode = currentThemeMode,
                        onThemeChanged = onThemeChanged,
                        onNavigateBack = { selectedBottomTab = 0 },
                        onCheckForUpdates = { updateViewModel.checkForUpdates(isManual = true) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }
    }

    // Render In-App Update Dialog when a newer release is detected
    updateInfo?.let { info ->
        UpdateDialog(
            updateInfo = info,
            onDismiss = { updateViewModel.dismissUpdateDialog() }
        )
    }

    // Render User Notification for Manual Check (e.g. app is up to date)
    userMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { updateViewModel.dismissUserMessage() },
            title = {
                Text(
                    text = "اطلاعیه به‌روزرسانی",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { updateViewModel.dismissUserMessage() }) {
                    Text("متوجه شدم", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
