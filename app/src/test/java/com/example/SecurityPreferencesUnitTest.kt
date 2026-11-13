package com.example

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.UserPreferences
import com.example.data.logger.AppLogger
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
import com.example.data.model.AppThemeMode
import com.example.data.model.LoginPageState
import com.example.data.repository.AuthRepository
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityPreferencesUnitTest {

    private lateinit var context: Application
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cullinan_user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cullinan_ui_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("test_secure_prefs_store", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeSecureFactory(): UserPreferences.SecurePrefsFactory =
        UserPreferences.SecurePrefsFactory { ctx ->
            ctx.getSharedPreferences("test_secure_prefs_store", Context.MODE_PRIVATE)
        }

    private fun failingSecureFactory(): UserPreferences.SecurePrefsFactory =
        UserPreferences.SecurePrefsFactory {
            throw GeneralSecurityException("Simulated Android KeyStore failure")
        }

    @Test
    fun testLegacyPlaintextMigrationAndPurge() {
        val legacy = context.getSharedPreferences("cullinan_user_prefs", Context.MODE_PRIVATE)
        legacy.edit()
            .putString("saved_username", "403123456")
            .putString("saved_password", "0987654321")
            .putBoolean("remember_me", true)
            .putString("theme_mode", AppThemeMode.DARK.name)
            .putBoolean("dev_mode_unlocked", true)
            .commit()

        val prefs = UserPreferences(context, fakeSecureFactory())

        assertTrue(prefs.isEncryptionAvailable)
        assertTrue(prefs.rememberMe)
        assertEquals("403123456", prefs.savedUsername)
        assertEquals("0987654321", prefs.savedPassword)
        assertEquals(AppThemeMode.DARK, prefs.themeMode)
        assertTrue(prefs.isDeveloperModeUnlocked)

        // Legacy plaintext store must be completely emptied
        val legacyAfter = context.getSharedPreferences("cullinan_user_prefs", Context.MODE_PRIVATE)
        assertTrue("Legacy plaintext store must be cleared after migration", legacyAfter.all.isEmpty())
    }

    @Test
    fun testMasterKeysUpgradePreservesExistingEncryptedCredentials() {
        // Simulate data created under legacy MasterKeys.getOrCreate(AES256_GCM_SPEC)
        val existingEncrypted = context.getSharedPreferences("test_secure_prefs_store", Context.MODE_PRIVATE)
        existingEncrypted.edit()
            .putString("saved_username", "401112233")
            .putString("saved_password", "SecretPass!99")
            .putBoolean("remember_me", true)
            .putString("theme_mode", AppThemeMode.LIGHT.name)
            .commit()

        val prefs = UserPreferences(context, fakeSecureFactory())

        assertTrue(prefs.rememberMe)
        assertEquals("401112233", prefs.savedUsername)
        assertEquals("SecretPass!99", prefs.savedPassword)
        assertEquals(AppThemeMode.LIGHT, prefs.themeMode)
        assertEquals(
            UserPreferences.CURRENT_MIGRATION_VERSION,
            existingEncrypted.getInt(UserPreferences.KEY_MIGRATION_VERSION, 0)
        )
    }

    @Test
    fun testEncryptionFailureDisablesRememberMeAndNeverWritesPlaintextCredentials() {
        // Seed legacy plaintext prefs to also ensure they are purged even when encryption fails
        context.getSharedPreferences("cullinan_user_prefs", Context.MODE_PRIVATE).edit()
            .putString("saved_username", "403999888")
            .putString("saved_password", "ShouldBePurged")
            .putBoolean("remember_me", true)
            .putString("theme_mode", AppThemeMode.DARK.name)
            .commit()

        val prefs = UserPreferences(context, failingSecureFactory())

        assertFalse("Encryption must be reported unavailable", prefs.isEncryptionAvailable)
        assertFalse("RememberMe must be forced false when encryption fails", prefs.rememberMe)

        // Attempt to turn rememberMe on and save credentials
        prefs.rememberMe = true
        prefs.saveCredentials("403000111", "PlaintextSecret!")
        prefs.savedUsername = "403000111"
        prefs.savedPassword = "PlaintextSecret!"

        assertFalse("RememberMe must remain false", prefs.rememberMe)
        assertFalse("hasSavedCredentials must remain false", prefs.hasSavedCredentials())
        assertEquals("", prefs.savedUsername)
        assertEquals("", prefs.savedPassword)

        // Ensure no plaintext store holds any credentials
        val legacyAfter = context.getSharedPreferences("cullinan_user_prefs", Context.MODE_PRIVATE)
        val uiPrefs = context.getSharedPreferences("cullinan_ui_prefs", Context.MODE_PRIVATE)
        assertFalse(legacyAfter.contains("saved_username"))
        assertFalse(legacyAfter.contains("saved_password"))
        assertFalse(uiPrefs.contains("saved_username"))
        assertFalse(uiPrefs.contains("saved_password"))

        // Non-sensitive UI theme preference must still work cleanly
        assertEquals(AppThemeMode.DARK, prefs.themeMode)
        prefs.themeMode = AppThemeMode.LIGHT
        assertEquals(AppThemeMode.LIGHT, prefs.themeMode)
    }

    @Test
    fun testTurningOffRememberMeImmediatelyClearsCredentials() {
        val prefs = UserPreferences(context, fakeSecureFactory())
        prefs.rememberMe = true
        prefs.saveCredentials("402555666", "MyPassword123")

        assertTrue(prefs.hasSavedCredentials())
        assertEquals("402555666", prefs.savedUsername)
        assertEquals("MyPassword123", prefs.savedPassword)

        // Turn off rememberMe
        prefs.rememberMe = false

        assertFalse(prefs.rememberMe)
        assertFalse(prefs.hasSavedCredentials())
        assertEquals("", prefs.savedUsername)
        assertEquals("", prefs.savedPassword)

        val backing: SharedPreferences = context.getSharedPreferences("test_secure_prefs_store", Context.MODE_PRIVATE)
        assertNull(backing.getString("saved_username", null))
        assertNull(backing.getString("saved_password", null))
    }

    @Test
    fun testAuthViewModelRememberMeToggleLoginFailureAndLogoutPaths() = runTest {
        val prefs = UserPreferences(context, fakeSecureFactory())
        prefs.rememberMe = true
        prefs.saveCredentials("402888777", "PassWord!1")

        val server = MockWebServer()
        server.start()
        com.example.data.network.CullinanHttpClient.setCustomBaseUrl(server.url("/").toString().trimEnd('/'))

        try {
            // Enqueue login page GET responses
            val loginHtml = """
                <html><body>
                <input type="hidden" name="__VIEWSTATE" value="VS1" />
                <input type="hidden" name="__VIEWSTATEGENERATOR" value="87AF9C2D" />
                <input type="hidden" name="publicKeytxt" value="FAKE_PEM" />
                </body></html>
            """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
            server.enqueue(MockResponse().setResponseCode(200).setBody("CAPTCHA"))

            val vm = AuthViewModel(context, AuthRepository(), prefs)
            assertEquals("402888777", vm.username.value)
            assertEquals("PassWord!1", vm.password.value)

            // 1. Toggling Remember Me OFF immediately clears stored credentials
            vm.toggleRememberMe()
            assertFalse(vm.rememberMe.value)
            assertFalse(prefs.hasSavedCredentials())
            assertEquals("", prefs.savedUsername)
            assertEquals("", prefs.savedPassword)

            // 2. Logout ensures in-memory password & username are cleared when rememberMe is OFF
            vm.onUsernameChange("402888777")
            vm.onPasswordChange("TempPass")
            server.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
            vm.onLogout()
            assertEquals("", vm.username.value)
            assertEquals("", vm.password.value)
            assertFalse(prefs.hasSavedCredentials())
        } finally {
            com.example.data.network.CullinanHttpClient.resetCustomBaseUrl()
            server.shutdown()
        }
    }

    @Test
    fun testShareableLogSanitizationMasksAllSensitiveFieldsIncludingUsername() {
        val rawText = """
            POST login body: txtUsername=4031987654&txtPassword=SuperSecretPass&txtCaptcha=4321
            JSON payload: {"username":"4031987654","studentNumber":"4031987654","nationalId":"0912345678","savedPassword":"MySavedPass!"}
            Log detail: savedUsername=4031987654 studentId: 4031987654 txtUsername%3D4031987654
            Cookie: ASP.NET_SessionId=xyz_session_999; __AntiXsrfToken=csrf_secret_888; keyId=key_secret_777
            Authorization: Bearer secret_jwt_token_value
        """.trimIndent()

        val sanitized = AppLogger.sanitize(rawText)

        assertFalse("Student username must never appear in sanitized output", sanitized.contains("4031987654"))
        assertFalse("National ID must never appear in sanitized output", sanitized.contains("0912345678"))
        assertFalse("Password must never appear in sanitized output", sanitized.contains("SuperSecretPass"))
        assertFalse("Saved password must never appear in sanitized output", sanitized.contains("MySavedPass!"))
        assertFalse("Captcha must never appear in sanitized output", sanitized.contains("4321"))
        assertFalse("Session cookie must never appear in sanitized output", sanitized.contains("xyz_session_999"))
        assertFalse("AntiXsrf cookie must never appear in sanitized output", sanitized.contains("csrf_secret_888"))
        assertFalse("keyId cookie must never appear in sanitized output", sanitized.contains("key_secret_777"))
        assertFalse("Bearer token must never appear in sanitized output", sanitized.contains("secret_jwt_token_value"))

        val entry = LogEntry(
            timestamp = "2026-11-13T10:00:00.000Z",
            level = LogLevel.INFO,
            component = "Auth",
            operation = "Login",
            message = "User attempt username=4031987654 password=MySecret99",
            exceptionMessage = "Error for studentNumber: 4031987654"
        )
        val sanitizedEntry = LogSanitizer.sanitizeEntry(entry)
        assertFalse(sanitizedEntry.message.contains("4031987654"))
        assertFalse(sanitizedEntry.message.contains("MySecret99"))
        assertFalse(sanitizedEntry.exceptionMessage!!.contains("4031987654"))
    }
}
