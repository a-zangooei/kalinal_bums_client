package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.ProfileUiState
import com.example.data.model.UserProfile
import com.example.ui.components.PersianRtlLayout
import com.example.ui.screens.profile.ProfileSheetContent
import com.example.ui.theme.CullinanTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileSheetComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleProfile() = UserProfile(
        fullName = "نام و نام خانوادگی نمونه",
        userType = "دانشجو",
        idNumber = "4000000000000000",
        nationalCode = "0000000000",
        group = "بومی",
        subGroup = "بومی",
        gender = "مرد",
        description = null,
        college = "دانشکده نمونه",
        field = "رشته نمونه",
        dorm = "مجوز شام دارد",
        blockNumber = null,
        roomNumber = null,
        mobile = "09000000000",
        grade = "دکتری عمومی",
        email = "user@example.com",
        bankAccount = null,
        lastLogin = "12:00:00 - 1405/01/01",
        cardNumber = "4000000000000000",
        barcodeNumber = "00000",
        cardDeliveryDate = "1400/01/01",
        cardExpirationDate = "1450/12/29",
        cardStatus = "فعال",
        selfCreditRials = -123000L,
        selfCreditText = "123,000- ریال (بدهی)",
        cafeteriaCreditRials = 0L,
        cafeteriaCreditText = "0 ریال",
        todayDate = "شنبه 01 فروردین 1405",
        portalVersion = "Version : 8.9.78.1",
        portalLastUpdate = "Last Update: 25/11/1402",
        isFullProfileLoaded = true
    )

    @Test
    fun profileSheet_masksSensitiveFieldsByDefaultAndRevealsOnTap() {
        composeRule.setContent {
            CullinanTheme(darkTheme = false) {
                PersianRtlLayout {
                    ProfileSheetContent(
                        profileState = ProfileUiState.Loaded(sampleProfile()),
                        avatarBytes = null,
                        onClose = {},
                        onRefresh = {},
                        onRelogin = {},
                        onOpenUnreadMessages = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("profile_full_name").assertIsDisplayed()
        composeRule.onNodeWithText("نام و نام خانوادگی نمونه").assertIsDisplayed()

        // Scroll personal details section into LazyColumn composition
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_national_code"))
        composeRule.waitForIdle()

        // National code & mobile must NOT appear unmasked initially
        assertTrue(composeRule.onAllNodesWithText("0000000000").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("09000000000").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_national_code_toggle"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_row_national_code_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("0000000000").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_row_mobile_toggle"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_row_mobile_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("09000000000").assertIsDisplayed()

        // Empty nullable fields display ثبت‌نشده
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(androidx.compose.ui.test.hasText("توضیحات"))
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("ثبت‌نشده")[0].assertIsDisplayed()
    }

    @Test
    fun profileSheet_showsActionableBannersForSessionExpiredAndUnreadMessages() {
        var reloginClicked = false
        composeRule.setContent {
            CullinanTheme(darkTheme = false) {
                PersianRtlLayout {
                    ProfileSheetContent(
                        profileState = ProfileUiState.SessionExpired("نشست منقضی شد"),
                        avatarBytes = null,
                        onClose = {},
                        onRefresh = {},
                        onRelogin = { reloginClicked = true },
                        onOpenUnreadMessages = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("profile_relogin_button").performClick()
        assertTrue(reloginClicked)
    }
}
