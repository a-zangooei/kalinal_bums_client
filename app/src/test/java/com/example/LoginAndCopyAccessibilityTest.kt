package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import com.example.ui.screens.LoginScreenContent
import com.example.ui.screens.menu.StudentProfileDialog
import com.example.ui.theme.CullinanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginAndCopyAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreen_showsFormalLabels_omitsStudentAndNationalIdAndNoticeCard() {
        composeRule.setContent {
            CullinanTheme(darkTheme = false) {
                LoginScreenContent(
                    username = "",
                    password = "",
                    passwordVisible = false,
                    captchaInput = "",
                    captchaBitmap = null,
                    rememberMe = false,
                    isEncryptionAvailable = true,
                    requiresBiometricUnlock = false,
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

        composeRule.onNodeWithTag("username_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("نام کاربری").assertIsDisplayed()
        composeRule.onNodeWithTag("password_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("رمز عبور").assertIsDisplayed()
        composeRule.onNodeWithText("کد ملی", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("شماره دانشجویی", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("راهنمای دانشجویان", substring = true).assertDoesNotExist()

        val passwordSemantics = composeRule.onNodeWithTag("password_input").fetchSemanticsNode().config
        assertTrue(passwordSemantics.contains(SemanticsProperties.Password))

        val toggleSemantics = composeRule.onNodeWithTag("password_visibility_toggle").fetchSemanticsNode().config
        assertTrue(toggleSemantics.contains(SemanticsProperties.StateDescription))
    }

    @Test
    fun loginScreen_enterAndImeNext_traversesFieldsAndSubmitsOnCaptcha() {
        var submitCount = 0
        var username by mutableStateOf("4031001")
        var password by mutableStateOf("secret")
        var captcha by mutableStateOf("1234")

        composeRule.setContent {
            CullinanTheme(darkTheme = false) {
                LoginScreenContent(
                    username = username,
                    password = password,
                    passwordVisible = false,
                    captchaInput = captcha,
                    captchaBitmap = null,
                    rememberMe = false,
                    isEncryptionAvailable = true,
                    requiresBiometricUnlock = false,
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
                    onUsernameChange = { username = it },
                    onPasswordChange = { password = it },
                    onTogglePasswordVisibility = {},
                    onCaptchaChange = { captcha = it },
                    onRefreshCaptcha = {},
                    onToggleRememberMe = {},
                    onSubmitLogin = { submitCount++ }
                )
            }
        }

        // Focus username -> Enter moves to password
        composeRule.onNodeWithTag("username_input").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("username_input").assertIsFocused()
        composeRule.onNodeWithTag("username_input").performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("password_input").assertIsFocused()

        // In password -> Enter moves to captcha
        composeRule.onNodeWithTag("password_input").performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("captcha_input").assertIsFocused()

        // In captcha -> Enter or IME Done submits login
        composeRule.onNodeWithTag("captcha_input").performImeAction()
        composeRule.waitForIdle()
        assertEquals(1, submitCount)
    }

    @Test
    fun profileDialog_showsUserIdLabel_insteadOfStudentNumber() {
        composeRule.setContent {
            CullinanTheme(darkTheme = false) {
                StudentProfileDialog(
                    studentName = "کاربر نمونه",
                    studentNumber = "4031001234",
                    creditFormatted = "۱۲۰,۰۰۰ ریال",
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("شناسه کاربری").assertIsDisplayed()
        composeRule.onNodeWithText("شماره دانشجویی", substring = true).assertDoesNotExist()
    }
}
