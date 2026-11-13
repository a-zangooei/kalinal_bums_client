package com.example

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.UserPreferences
import com.example.data.model.AppThemeMode
import com.example.data.network.CullinanHttpClient
import com.example.data.repository.AuthRepository
import com.example.data.security.BiometricAuthenticator
import com.example.data.security.BiometricCapabilityChecker
import com.example.data.security.BiometricLockCoordinator
import com.example.data.security.BiometricToggleOutcome
import com.example.ui.screens.LoginScreenContent
import com.example.ui.screens.SettingsScreenContent
import com.example.ui.theme.CullinanTheme
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiometricSecurityHardeningTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private val testDispatcher = UnconfinedTestDispatcher()

    private val loginHtml = """
        <html><body>
        <input type="hidden" name="__VIEWSTATE" value="VS_TOKEN" />
        <input type="hidden" name="__VIEWSTATEGENERATOR" value="87AF9C2D" />
        <input type="hidden" name="publicKeytxt" value="FAKE_PEM_KEY" />
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cullinan_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cullinan_ui_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("test_secure_hardening_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        server = MockWebServer()
        server.start()
        CullinanHttpClient.setCustomBaseUrl(server.url("/").toString().trimEnd('/'))
        repeat(6) {
            server.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
        }
    }

    @After
    fun tearDown() {
        CullinanHttpClient.resetCustomBaseUrl()
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun fakeSecureFactory(): UserPreferences.SecurePrefsFactory =
        UserPreferences.SecurePrefsFactory { ctx ->
            ctx.getSharedPreferences("test_secure_hardening_prefs", Context.MODE_PRIVATE)
        }

    private fun createPrefs(
        rememberMe: Boolean = false,
        biometricEnabled: Boolean = false,
        username: String = "",
        password: String = ""
    ): UserPreferences {
        val prefs = UserPreferences(context, fakeSecureFactory())
        prefs.rememberMe = rememberMe
        prefs.isBiometricLockEnabled = biometricEnabled
        if (rememberMe && username.isNotEmpty() && password.isNotEmpty()) {
            prefs.saveCredentials(username, password)
        }
        return prefs
    }

    private fun successAuthenticator() = BiometricAuthenticator { _, _, onSuccess, _, _ ->
        onSuccess()
    }

    private fun cancelAuthenticator() = BiometricAuthenticator { _, _, _, _, onCancelled ->
        onCancelled()
    }

    private fun errorAuthenticator(message: String = "اثر انگشت تایید نشد") =
        BiometricAuthenticator { _, _, _, onError, _ ->
            onError(message)
        }

    // 1. Enabling biometric lock requires successful authentication
    @Test
    fun scenario01_enablingBiometricLockRequiresAuthentication() {
        val prefs = createPrefs(rememberMe = true, biometricEnabled = false, username = "4031001", password = "Secret123")
        val coordinator = BiometricLockCoordinator(
            context = context,
            userPrefs = prefs,
            capabilityChecker = BiometricCapabilityChecker { true }
        )

        var lastOutcome: BiometricToggleOutcome? = null

        // Cancelled auth -> lock stays OFF
        coordinator.requestLockChange(targetEnabled = true, authenticator = cancelAuthenticator()) {
            lastOutcome = it
        }
        assertTrue(lastOutcome is BiometricToggleOutcome.Cancelled)
        assertFalse(prefs.isBiometricLockEnabled)

        // Failed auth -> lock stays OFF
        coordinator.requestLockChange(targetEnabled = true, authenticator = errorAuthenticator("خطا")) {
            lastOutcome = it
        }
        assertTrue(lastOutcome is BiometricToggleOutcome.Failed)
        assertFalse(prefs.isBiometricLockEnabled)

        // Null authenticator -> lock stays OFF
        coordinator.requestLockChange(targetEnabled = true, authenticator = null) {
            lastOutcome = it
        }
        assertTrue(lastOutcome is BiometricToggleOutcome.Failed)
        assertFalse(prefs.isBiometricLockEnabled)

        // Unavailable device security -> lock stays OFF
        val unavailableCoordinator = BiometricLockCoordinator(
            context = context,
            userPrefs = prefs,
            capabilityChecker = BiometricCapabilityChecker { false }
        )
        unavailableCoordinator.requestLockChange(targetEnabled = true, authenticator = successAuthenticator()) {
            lastOutcome = it
        }
        assertTrue(lastOutcome is BiometricToggleOutcome.Failed)
        assertFalse(prefs.isBiometricLockEnabled)

        // Successful authentication -> lock becomes ON
        coordinator.requestLockChange(targetEnabled = true, authenticator = successAuthenticator()) {
            lastOutcome = it
        }
        assertEquals(BiometricToggleOutcome.Updated(isEnabled = true), lastOutcome)
        assertTrue(prefs.isBiometricLockEnabled)
    }

    // 2. Disabling biometric lock ALSO requires successful authentication
    @Test
    fun scenario02_disablingBiometricLockRequiresAuthentication() {
        val prefs = createPrefs(rememberMe = true, biometricEnabled = true, username = "4031002", password = "Secret456")
        val coordinator = BiometricLockCoordinator(
            context = context,
            userPrefs = prefs,
            capabilityChecker = BiometricCapabilityChecker { true }
        )

        var lastOutcome: BiometricToggleOutcome? = null

        // Cancel -> must stay ON
        coordinator.requestLockChange(targetEnabled = false, authenticator = cancelAuthenticator()) {
            lastOutcome = it
        }
        assertTrue(lastOutcome is BiometricToggleOutcome.Cancelled)
        assertTrue(prefs.isBiometricLockEnabled)

        // Error -> must stay ON
        coordinator.requestLockChange(targetEnabled = false, authenticator = errorAuthenticator("نامعتبر")) {
            lastOutcome = it
        }
        assertTrue(lastOutcome is BiometricToggleOutcome.Failed)
        assertTrue(prefs.isBiometricLockEnabled)

        // Null authenticator -> must stay ON
        coordinator.requestLockChange(targetEnabled = false, authenticator = null) {
            lastOutcome = it
        }
        assertTrue(lastOutcome is BiometricToggleOutcome.Failed)
        assertTrue(prefs.isBiometricLockEnabled)

        // Device auth unavailable while locked -> Fail-closed: cannot disable without recovery wipe
        val unavailableCoordinator = BiometricLockCoordinator(
            context = context,
            userPrefs = prefs,
            capabilityChecker = BiometricCapabilityChecker { false }
        )
        unavailableCoordinator.requestLockChange(targetEnabled = false, authenticator = successAuthenticator()) {
            lastOutcome = it
        }
        val unavailableFailure = lastOutcome as BiometricToggleOutcome.Failed
        assertTrue(unavailableFailure.requiresRecoveryWipe)
        assertTrue(prefs.isBiometricLockEnabled)
        assertTrue(prefs.hasSavedCredentials())

        // Authenticated disable -> succeeds
        coordinator.requestLockChange(targetEnabled = false, authenticator = successAuthenticator()) {
            lastOutcome = it
        }
        assertEquals(BiometricToggleOutcome.Updated(isEnabled = false), lastOutcome)
        assertFalse(prefs.isBiometricLockEnabled)
    }

    // 3. Cancelling biometric prompt keeps credentials empty, masked, and lock active
    @Test
    fun scenario03_cancellingBiometricPromptKeepsPasswordEmptyAndLocked() = runTest {
        val prefs = createPrefs(rememberMe = true, biometricEnabled = true, username = "4031003", password = "PassCancel!1")
        val vm = AuthViewModel(context, AuthRepository(), prefs, BiometricCapabilityChecker { true })

        assertTrue(vm.requiresBiometricUnlock.value)
        assertEquals("", vm.username.value)
        assertEquals("", vm.password.value)
        assertFalse(vm.isPasswordVisible.value)

        // User flips eye icon before cancelling or tries unlock -> cancel
        vm.setPasswordVisible(true)
        vm.unlockWithBiometrics(cancelAuthenticator())

        assertEquals("", vm.username.value)
        assertEquals("", vm.password.value)
        assertFalse(vm.isPasswordVisible.value)
        assertTrue(vm.requiresBiometricUnlock.value)
        assertNull(vm.errorMessage.value)
    }

    // 4. Failed / repeated failed auth attempts never populate savedPassword and keep lock active
    @Test
    fun scenario04_failedAndRepeatedBiometricAttemptsNeverPopulatePassword() = runTest {
        val prefs = createPrefs(rememberMe = true, biometricEnabled = true, username = "4031004", password = "PassError!2")
        val vm = AuthViewModel(context, AuthRepository(), prefs, BiometricCapabilityChecker { true })

        repeat(4) { attempt ->
            vm.unlockWithBiometrics(errorAuthenticator("خطای تایید #$attempt"))
            assertEquals("", vm.username.value)
            assertEquals("", vm.password.value)
            assertFalse(vm.isPasswordVisible.value)
            assertTrue(vm.requiresBiometricUnlock.value)
            assertEquals("خطای تایید #$attempt", vm.errorMessage.value)
        }
    }

    // 5. Device sensor/PIN unavailable fails closed: never autofills, blocks unlock, provides recovery wipe
    @Test
    fun scenario05_biometricUnavailableWhileLockedFailsClosedAndOffersRecoveryWipe() = runTest {
        val prefs = createPrefs(rememberMe = true, biometricEnabled = true, username = "4031005", password = "PassClosed!3")
        val vm = AuthViewModel(context, AuthRepository(), prefs, BiometricCapabilityChecker { false })

        // Fail-closed on init
        assertEquals("", vm.username.value)
        assertEquals("", vm.password.value)
        assertFalse(vm.isPasswordVisible.value)
        assertFalse(vm.requiresBiometricUnlock.value)
        assertTrue(vm.biometricUnavailableWhileLocked.value)

        // Calling unlock methods must NOT leak saved credentials
        vm.unlockSavedCredentialsAfterBiometric()
        assertEquals("", vm.username.value)
        assertEquals("", vm.password.value)
        assertTrue(vm.biometricUnavailableWhileLocked.value)

        vm.unlockWithBiometrics(successAuthenticator())
        assertEquals("", vm.username.value)
        assertEquals("", vm.password.value)
        assertTrue(vm.biometricUnavailableWhileLocked.value)

        // Verify recovery wipe clears everything safely
        vm.clearSavedCredentialsForRecovery()
        assertFalse(prefs.hasSavedCredentials())
        assertFalse(prefs.isBiometricLockEnabled)
        assertFalse(prefs.rememberMe)
        assertFalse(vm.biometricUnavailableWhileLocked.value)
        assertEquals("", vm.username.value)
        assertEquals("", vm.password.value)
        assertFalse(vm.isPasswordVisible.value)
    }

    @Test
    fun scenario05b_composeUiShowsFailClosedBannerAndSettingsRecoveryControls() = runTest {
        val prefs = createPrefs(rememberMe = true, biometricEnabled = true, username = "4031005ui", password = "SecretUi!5")
        val vm = AuthViewModel(context, AuthRepository(), prefs, BiometricCapabilityChecker { false })

        composeRule.setContent {
            val username by vm.username.collectAsState()
            val password by vm.password.collectAsState()
            val passwordVisible by vm.isPasswordVisible.collectAsState()
            val rememberMe by vm.rememberMe.collectAsState()
            val unavailableWhileLocked by vm.biometricUnavailableWhileLocked.collectAsState()

            CullinanTheme(darkTheme = false) {
                LoginScreenContent(
                    username = username,
                    password = password,
                    passwordVisible = passwordVisible,
                    captchaInput = "",
                    captchaBitmap = null,
                    rememberMe = rememberMe,
                    isEncryptionAvailable = true,
                    requiresBiometricUnlock = false,
                    biometricUnavailableWhileLocked = unavailableWhileLocked,
                    canQuickFillSavedCredentials = false,
                    isLoading = false,
                    isCaptchaRefreshing = false,
                    errorMessage = null,
                    isCompatibilityModeActive = false,
                    onOpenSettings = {},
                    onOpenDeviceSecuritySettings = {},
                    onClearLockedSavedCredentials = { vm.clearSavedCredentialsForRecovery() },
                    onRetryConnection = {},
                    onBiometricUnlockClick = {},
                    onQuickFillSavedCredentials = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onTogglePasswordVisibility = { vm.togglePasswordVisibility() },
                    onCaptchaChange = {},
                    onRefreshCaptcha = {},
                    onToggleRememberMe = {},
                    onSubmitLogin = {}
                )
            }
        }

        composeRule.onNodeWithTag("biometric_unavailable_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("open_device_security_settings_button").assertIsDisplayed()
        composeRule.onNodeWithTag("wipe_locked_credentials_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertFalse(prefs.hasSavedCredentials())
        assertFalse(prefs.isBiometricLockEnabled)
        assertFalse(vm.biometricUnavailableWhileLocked.value)
    }

    @Test
    fun scenario05c_settingsSwitchReflectsOnAndDisabledWithRecoveryWhenUnavailable() {
        var recoveryClicked = false
        composeRule.setContent {
            CullinanTheme(darkTheme = false) {
                SettingsScreenContent(
                    currentThemeMode = AppThemeMode.LIGHT,
                    isEncryptionAvailable = true,
                    canUseBiometric = false,
                    isBiometricEnabled = true,
                    baseUrl = "https://jeton.bums.ac.ir",
                    isCompatibilityModeActive = false,
                    isDevUnlocked = false,
                    versionName = "1.0.0",
                    versionCode = 66,
                    onNavigateBack = {},
                    onSelectTheme = {},
                    onToggleBiometric = {},
                    onOpenDeviceSecuritySettings = {},
                    onRecoveryWipeAndDisableLock = { recoveryClicked = true },
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

        // Switch must still show checked (ON) and not be toggleable when locked & device auth is unavailable
        composeRule.onNodeWithTag("biometric_lock_switch").performScrollTo().assertIsOn().assertIsNotEnabled()
        composeRule.onNodeWithTag("settings_open_security_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_wipe_biometric_recovery_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(recoveryClicked)
    }

    // 6. onLogout() clears transient ViewModel state across all 3 persistence modes
    @Test
    fun scenario06_logoutClearsTransientCredentialsAndVisibilityAcrossAllThreeModes() = runTest {
        // Mode A: Remember Me = OFF
        val prefsA = createPrefs(rememberMe = false, biometricEnabled = false)
        val vmA = AuthViewModel(context, AuthRepository(), prefsA, BiometricCapabilityChecker { true })
        vmA.onUsernameChange("4031006a")
        vmA.onPasswordChange("TempPassA")
        vmA.togglePasswordVisibility()
        assertTrue(vmA.isPasswordVisible.value)

        vmA.onLogout()
        assertEquals("", vmA.username.value)
        assertEquals("", vmA.password.value)
        assertFalse(vmA.isPasswordVisible.value)
        assertFalse(prefsA.hasSavedCredentials())

        // Mode B: Remember Me = ON + Biometric Lock = ON
        val prefsB = createPrefs(rememberMe = true, biometricEnabled = true, username = "4031006b", password = "SecretLockB")
        val vmB = AuthViewModel(context, AuthRepository(), prefsB, BiometricCapabilityChecker { true })
        vmB.unlockWithBiometrics(successAuthenticator())
        vmB.togglePasswordVisibility()
        assertEquals("SecretLockB", vmB.password.value)
        assertTrue(vmB.isPasswordVisible.value)

        vmB.onLogout()
        assertEquals("", vmB.username.value)
        assertEquals("", vmB.password.value)
        assertFalse(vmB.isPasswordVisible.value)
        assertTrue(vmB.requiresBiometricUnlock.value)
        // Encrypted store preserved for next biometric unlock
        assertTrue(prefsB.hasSavedCredentials())
        // Re-syncing login screen must not auto-populate
        vmB.syncCredentialState(initialLoad = false)
        assertEquals("", vmB.password.value)

        // Mode C: Remember Me = ON + Biometric Lock = OFF
        val prefsC = createPrefs(rememberMe = true, biometricEnabled = false, username = "4031006c", password = "SecretOpenC")
        val vmC = AuthViewModel(context, AuthRepository(), prefsC, BiometricCapabilityChecker { true })
        assertEquals("4031006c", vmC.username.value)
        assertEquals("SecretOpenC", vmC.password.value)
        vmC.togglePasswordVisibility()
        assertTrue(vmC.isPasswordVisible.value)

        vmC.onLogout()
        assertEquals("", vmC.username.value)
        assertEquals("", vmC.password.value)
        assertFalse(vmC.isPasswordVisible.value)
        assertTrue(vmC.canQuickFillSavedCredentials.value)
        // Syncing LoginScreen right after logout suppresses immediate autofill
        vmC.syncCredentialState(initialLoad = false)
        assertEquals("", vmC.username.value)
        assertEquals("", vmC.password.value)
        // Explicit quick-fill repopulates with password masked
        vmC.refillSavedCredentialsAfterLogout()
        assertEquals("4031006c", vmC.username.value)
        assertEquals("SecretOpenC", vmC.password.value)
        assertFalse(vmC.isPasswordVisible.value)
        assertFalse(vmC.canQuickFillSavedCredentials.value)
    }

    // 7. Re-lock resets passwordVisible state (both ViewModel state & Compose PasswordVisualTransformation)
    @Test
    fun scenario07_relockAndUnlockResetsPasswordVisibilityToMasked() = runTest {
        val prefs = createPrefs(rememberMe = true, biometricEnabled = true, username = "4031007", password = "VisibleResetPass")
        val vm = AuthViewModel(context, AuthRepository(), prefs, BiometricCapabilityChecker { true })

        composeRule.setContent {
            val username by vm.username.collectAsState()
            val password by vm.password.collectAsState()
            val passwordVisible by vm.isPasswordVisible.collectAsState()
            val requiresBio by vm.requiresBiometricUnlock.collectAsState()

            CullinanTheme(darkTheme = false) {
                LoginScreenContent(
                    username = username,
                    password = password,
                    passwordVisible = passwordVisible,
                    captchaInput = "",
                    captchaBitmap = null,
                    rememberMe = true,
                    isEncryptionAvailable = true,
                    requiresBiometricUnlock = requiresBio,
                    biometricUnavailableWhileLocked = false,
                    canQuickFillSavedCredentials = false,
                    isLoading = false,
                    isCaptchaRefreshing = false,
                    errorMessage = null,
                    isCompatibilityModeActive = false,
                    onOpenSettings = null,
                    onOpenDeviceSecuritySettings = {},
                    onClearLockedSavedCredentials = {},
                    onRetryConnection = {},
                    onBiometricUnlockClick = { vm.unlockWithBiometrics(successAuthenticator()) },
                    onQuickFillSavedCredentials = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onTogglePasswordVisibility = { vm.togglePasswordVisibility() },
                    onCaptchaChange = {},
                    onRefreshCaptcha = {},
                    onToggleRememberMe = {},
                    onSubmitLogin = {}
                )
            }
        }

        // 1. Unlock -> password populated, must start masked (PasswordVisualTransformation)
        vm.unlockWithBiometrics(successAuthenticator())
        composeRule.waitForIdle()
        assertFalse(vm.isPasswordVisible.value)
        val maskedSemantics = composeRule.onNodeWithTag("password_input").fetchSemanticsNode().config
        assertTrue(maskedSemantics.contains(SemanticsProperties.Password))

        // 2. User taps eye toggle -> password becomes visible
        composeRule.onNodeWithTag("password_visibility_toggle").performClick()
        composeRule.waitForIdle()
        assertTrue(vm.isPasswordVisible.value)
        val visibleSemantics = composeRule.onNodeWithTag("password_input").fetchSemanticsNode().config
        assertFalse(visibleSemantics.contains(SemanticsProperties.Password))

        // 3. Logout / re-lock resets visibility; next unlock MUST stay masked
        vm.onLogout()
        composeRule.waitForIdle()
        assertFalse(vm.isPasswordVisible.value)
        assertEquals("", vm.password.value)

        vm.unlockWithBiometrics(successAuthenticator())
        composeRule.waitForIdle()
        assertEquals("VisibleResetPass", vm.password.value)
        assertFalse(vm.isPasswordVisible.value)
        val relockedUnlockSemantics = composeRule.onNodeWithTag("password_input").fetchSemanticsNode().config
        assertTrue(relockedUnlockSemantics.contains(SemanticsProperties.Password))

        // 4. Also verify initialLoad=true re-lock resets visibility when eye icon was open
        composeRule.onNodeWithTag("password_visibility_toggle").performClick()
        composeRule.waitForIdle()
        assertTrue(vm.isPasswordVisible.value)
        vm.syncCredentialState(initialLoad = true)
        composeRule.waitForIdle()
        assertFalse(vm.isPasswordVisible.value)
        assertEquals("", vm.password.value)
        vm.unlockWithBiometrics(successAuthenticator())
        composeRule.waitForIdle()
        assertFalse(vm.isPasswordVisible.value)
    }

    // 8. Sync between Settings lock toggle and Login / AuthViewModel state
    @Test
    fun scenario08_settingsLockToggleImmediatelySyncsWithAuthViewModel() = runTest {
        val prefs = createPrefs(rememberMe = true, biometricEnabled = false, username = "4031008", password = "SyncSecret8")
        val vm = AuthViewModel(context, AuthRepository(), prefs, BiometricCapabilityChecker { true })

        // Initially unlocked -> auto-filled and password masked
        assertEquals("4031008", vm.username.value)
        assertEquals("SyncSecret8", vm.password.value)
        assertFalse(vm.requiresBiometricUnlock.value)
        vm.togglePasswordVisibility()
        assertTrue(vm.isPasswordVisible.value)

        // Enable lock in Settings via coordinator -> syncCallback notifies AuthViewModel
        val coordinator = BiometricLockCoordinator(
            context = context,
            userPrefs = prefs,
            capabilityChecker = BiometricCapabilityChecker { true }
        )
        coordinator.requestLockChange(targetEnabled = true, authenticator = successAuthenticator()) {
            vm.syncCredentialState(initialLoad = false)
        }

        assertTrue(prefs.isBiometricLockEnabled)
        assertEquals("", vm.username.value)
        assertEquals("", vm.password.value)
        assertFalse(vm.isPasswordVisible.value)
        assertTrue(vm.requiresBiometricUnlock.value)

        // Now disable lock in Settings with auth -> syncCallback updates AuthViewModel
        coordinator.requestLockChange(targetEnabled = false, authenticator = successAuthenticator()) {
            vm.syncCredentialState(initialLoad = false)
        }
        assertFalse(prefs.isBiometricLockEnabled)
        assertFalse(vm.requiresBiometricUnlock.value)
        assertEquals("4031008", vm.username.value)
        assertEquals("SyncSecret8", vm.password.value)
        assertFalse(vm.isPasswordVisible.value)
    }

    // 9. Disabling Remember Me clears encrypted credentials permanently across simulated app restart
    @Test
    fun scenario09_disablingRememberMeClearsStorageAcrossAppRestart() = runTest {
        val prefsBefore = createPrefs(rememberMe = true, biometricEnabled = false, username = "4031009", password = "PersistCheck9")
        val vmBefore = AuthViewModel(context, AuthRepository(), prefsBefore, BiometricCapabilityChecker { true })
        assertEquals("4031009", vmBefore.username.value)
        assertEquals("PersistCheck9", vmBefore.password.value)

        vmBefore.toggleRememberMe()
        assertFalse(vmBefore.rememberMe.value)
        assertFalse(prefsBefore.hasSavedCredentials())

        val rawPrefs: SharedPreferences =
            context.getSharedPreferences("test_secure_hardening_prefs", Context.MODE_PRIVATE)
        assertNull(rawPrefs.getString("saved_username", null))
        assertNull(rawPrefs.getString("saved_password", null))

        // Simulate cold restart with brand new UserPreferences + AuthViewModel instances
        val prefsAfterRestart = UserPreferences(context, fakeSecureFactory())
        val vmAfterRestart = AuthViewModel(context, AuthRepository(), prefsAfterRestart, BiometricCapabilityChecker { true })
        assertFalse(vmAfterRestart.rememberMe.value)
        assertEquals("", vmAfterRestart.username.value)
        assertEquals("", vmAfterRestart.password.value)
        assertFalse(vmAfterRestart.requiresBiometricUnlock.value)
    }

    // 10. Cold app restart with biometric lock ON leaves credentials empty and masked until auth succeeds
    @Test
    fun scenario10_coldRestartWithBiometricLockKeepsCredentialsEmptyUntilAuth() = runTest {
        val initialPrefs = createPrefs(rememberMe = true, biometricEnabled = true, username = "4031010", password = "ColdRestartPass10")
        assertTrue(initialPrefs.hasSavedCredentials())

        // Recreate UserPreferences and AuthViewModel as a cold app launch
        val restartedPrefs = UserPreferences(context, fakeSecureFactory())
        val restartedVm = AuthViewModel(context, AuthRepository(), restartedPrefs, BiometricCapabilityChecker { true })

        assertTrue(restartedVm.rememberMe.value)
        assertTrue(restartedVm.requiresBiometricUnlock.value)
        assertFalse(restartedVm.biometricUnavailableWhileLocked.value)
        assertEquals("", restartedVm.username.value)
        assertEquals("", restartedVm.password.value)
        assertFalse(restartedVm.isPasswordVisible.value)

        // Only after biometric authentication succeeds are credentials populated (still masked)
        restartedVm.unlockWithBiometrics(successAuthenticator())
        assertFalse(restartedVm.requiresBiometricUnlock.value)
        assertEquals("4031010", restartedVm.username.value)
        assertEquals("ColdRestartPass10", restartedVm.password.value)
        assertFalse(restartedVm.isPasswordVisible.value)
    }
}
