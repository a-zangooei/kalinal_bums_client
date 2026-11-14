package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.graphics.Color
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.profile.ProfileModalSheet
import com.example.ui.viewmodel.MessagesViewModel
import com.example.ui.viewmodel.ProfileViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(applicationContext)
        AppLogger.installCrashHandler(applicationContext)
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
    var selectedBottomTab by remember { mutableIntStateOf(0) } // 0: Reservation, 1: Messages, 2: Settings

    val authViewModel: AuthViewModel = viewModel()
    val menuViewModel: MenuViewModel = viewModel()
    val messagesViewModel: MessagesViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val updateViewModel: UpdateViewModel = viewModel()

    val updateInfo by updateViewModel.updateInfo.collectAsStateWithLifecycle()
    val userMessage by updateViewModel.userMessage.collectAsStateWithLifecycle()
    val messagesState by messagesViewModel.pageState.collectAsStateWithLifecycle()
    val menuBlockedByUnread by menuViewModel.hasUnreadMessagesBlocked.collectAsStateWithLifecycle()
    val profileAvatarBytes by profileViewModel.avatarBytes.collectAsStateWithLifecycle()
    val profileState by profileViewModel.profileState.collectAsStateWithLifecycle()
    val isProfileSheetOpen by profileViewModel.isProfileSheetOpen.collectAsStateWithLifecycle()

    val isMandatoryBlocking = (activeSession?.hasUnreadMessages == true || menuBlockedByUnread || messagesState.hasUnreadMessages) && !messagesState.isReservationUnlocked

    val context = LocalContext.current
    val shouldProtectWindow = activeSession == null || selectedBottomTab == 2 || isProfileSheetOpen
    LaunchedEffect(shouldProtectWindow) {
        val window = (context as? android.app.Activity)?.window
        if (shouldProtectWindow) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val session = activeSession
    if (session == null) {
        if (isPreLoginSettingsOpen) {
            BackHandler {
                isPreLoginSettingsOpen = false
                authViewModel.syncCredentialState(initialLoad = false)
                AppLogger.logAction("Pre-login Settings closed via Back")
            }

            SettingsScreen(
                currentThemeMode = currentThemeMode,
                onThemeChanged = onThemeChanged,
                onNavigateBack = {
                    isPreLoginSettingsOpen = false
                    authViewModel.syncCredentialState(initialLoad = false)
                    AppLogger.logAction("Pre-login Settings closed via Back button")
                },
                onCheckForUpdates = { updateViewModel.checkForUpdates(isManual = true) },
                onSecuritySettingsChanged = {
                    authViewModel.syncCredentialState(initialLoad = false)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = { userSession ->
                    activeSession = userSession
                    messagesViewModel.loadMessages()
                    profileViewModel.ensureAvatarLoaded()
                    if (userSession.hasUnreadMessages) {
                        selectedBottomTab = 1
                    } else {
                        selectedBottomTab = 0
                    }
                    AppLogger.logAction("User logged in (hasUnreadMessages: ${userSession.hasUnreadMessages}, initialTab=$selectedBottomTab)")
                },
                onOpenSettings = {
                    isPreLoginSettingsOpen = true
                    AppLogger.logAction("Opened pre-login Settings")
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        var isLoggingOut by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val authRepo = remember { AuthRepository() }

        LaunchedEffect(session) {
            profileViewModel.ensureAvatarLoaded()
        }

        val handleLogout: () -> Unit = remember(authViewModel, menuViewModel, profileViewModel, authRepo, context) {
            {
                if (!isLoggingOut) {
                    isLoggingOut = true
                    AppLogger.logAction("User initiated real server logout")
                    val currentMenu = menuViewModel.weeklyMenu.value

                    coroutineScope.launch {
                        try {
                            val serverResult = authRepo.logoutFromServer(currentMenu)
                            if (serverResult.isSuccess) {
                                Toast.makeText(context, "خروج از حساب کاربری با موفقیت انجام شد.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "خروج در سمت سرور با خطا مواجه شد؛ نشست محلی پاک‌سازی گردید.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            AppLogger.logError("Logout", "Logout request failed", e)
                            Toast.makeText(context, "خروج در سمت سرور با خطا مواجه شد؛ نشست محلی پاک‌سازی گردید.", Toast.LENGTH_LONG).show()
                        } finally {
                            // 1. Purge active local session state & in-memory PII
                            profileViewModel.clearSession()
                            activeSession = null
                            selectedBottomTab = 0
                            menuViewModel.clearUnreadBlocked()
                            // 2. Clear local cookies AFTER server request completed
                            com.example.data.network.CullinanHttpClient.cookieJar.clear()
                            // 3. Reset credentials per Remember-Me/Biometric rules and fetch fresh Login.aspx
                            authViewModel.onLogout()
                            isLoggingOut = false
                        }
                    }
                }
            }
        }
        val onReservationTabClick = remember {
            {
                selectedBottomTab = 0
                AppLogger.logAction("Switched to Reservation tab")
            }
        }
        val onMessagesTabClick = remember(messagesViewModel) {
            {
                selectedBottomTab = 1
                messagesViewModel.loadMessages()
                AppLogger.logAction("Switched to Messages tab")
            }
        }
        val onSettingsTabClick = remember {
            {
                selectedBottomTab = 2
                AppLogger.logAction("Switched to Settings tab")
            }
        }

        BackHandler {
            AppLogger.logAction("System Back pressed (tab=$selectedBottomTab, blocking=$isMandatoryBlocking)")
            if (isMandatoryBlocking) {
                handleLogout()
            } else if (selectedBottomTab != 0) {
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
                            onClick = onReservationTabClick,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.RestaurantMenu,
                                    contentDescription = null
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
                            onClick = onMessagesTabClick,
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (messagesState.unreadCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = Color.White
                                            ) {
                                                Text(messagesState.unreadCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mail,
                                        contentDescription = null
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = "پیام‌ها",
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

                        NavigationBarItem(
                            selected = selectedBottomTab == 2,
                            onClick = onSettingsTabClick,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(
                                    text = "تنظیمات",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedBottomTab == 2) FontWeight.Bold else FontWeight.Normal
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
                    0 -> {
                        if (isMandatoryBlocking) {
                            MessagesScreen(
                                viewModel = messagesViewModel,
                                isBlockingMode = true,
                                onNavigateBack = null,
                                onUnlockedSuccess = {
                                    AppLogger.logInfo("Navigation", "MessagesUnlocked", "Reservation unlocked from blocking MessagesScreen")
                                    activeSession = activeSession?.copy(hasUnreadMessages = false)
                                    menuViewModel.clearUnreadBlocked()
                                    menuViewModel.loadInitialMenu()
                                    selectedBottomTab = 0
                                },
                                avatarBytes = profileAvatarBytes,
                                onOpenProfile = {
                                    profileViewModel.openProfile(session, menuViewModel.weeklyMenu.value)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            )
                        } else {
                            MenuScreen(
                                userSession = session,
                                viewModel = menuViewModel,
                                onLogout = handleLogout,
                                isLoggingOut = isLoggingOut,
                                profileAvatarBytes = profileAvatarBytes,
                                onOpenProfile = {
                                    profileViewModel.openProfile(session, menuViewModel.weeklyMenu.value)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            )
                        }
                    }
                    1 -> MessagesScreen(
                        viewModel = messagesViewModel,
                        isBlockingMode = isMandatoryBlocking,
                        onNavigateBack = if (!isMandatoryBlocking) { { selectedBottomTab = 0 } } else null,
                        onUnlockedSuccess = {
                            AppLogger.logInfo("Navigation", "MessagesUnlocked", "Reservation unlocked from Messages tab")
                            activeSession = activeSession?.copy(hasUnreadMessages = false)
                            menuViewModel.clearUnreadBlocked()
                            menuViewModel.loadInitialMenu()
                            selectedBottomTab = 0
                        },
                        avatarBytes = profileAvatarBytes,
                        onOpenProfile = {
                            profileViewModel.openProfile(session, menuViewModel.weeklyMenu.value)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                    2 -> SettingsScreen(
                        currentThemeMode = currentThemeMode,
                        onThemeChanged = onThemeChanged,
                        onNavigateBack = { selectedBottomTab = 0 },
                        onCheckForUpdates = { updateViewModel.checkForUpdates(isManual = true) },
                        onSecuritySettingsChanged = {
                            authViewModel.syncCredentialState(initialLoad = false)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }

        if (isProfileSheetOpen) {
            ProfileModalSheet(
                profileState = profileState,
                avatarBytes = profileAvatarBytes,
                onDismiss = { profileViewModel.closeProfile() },
                onRefresh = {
                    profileViewModel.refreshProfile(session, menuViewModel.weeklyMenu.value)
                },
                onRelogin = {
                    profileViewModel.clearSession()
                    activeSession = null
                    selectedBottomTab = 0
                    menuViewModel.clearUnreadBlocked()
                    com.example.data.network.CullinanHttpClient.cookieJar.clear()
                    authViewModel.onLogout()
                },
                onOpenUnreadMessages = {
                    profileViewModel.closeProfile()
                    selectedBottomTab = 1
                    messagesViewModel.loadMessages()
                }
            )
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
                    text = "وضعیت به‌روزرسانی برنامه",
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
                TextButton(
                    onClick = { updateViewModel.dismissUserMessage() },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("متوجه شدم", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
