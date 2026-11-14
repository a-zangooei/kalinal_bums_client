package com.example

import com.example.data.logger.AppLogger
import com.example.data.logger.LogSanitizer
import com.example.data.model.ProfileFetchOutcome
import com.example.data.model.ProfileUiState
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.PageClassifier
import com.example.data.parser.ProfileParser
import com.example.data.parser.ReservationHtmlParser
import com.example.data.repository.ProfilePhotoSessionExpiredException
import com.example.data.repository.ProfileRepository
import com.example.ui.screens.profile.ProfilePrivacyFormatter
import com.example.ui.screens.profile.SensitiveFieldKind
import com.example.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileFeatureUnitTest {

    private lateinit var mockWebServer: MockWebServer
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockWebServer = MockWebServer()
        mockWebServer.start()
        CullinanHttpClient.setCustomBaseUrl(mockWebServer.url("/").toString())
        CullinanHttpClient.cookieJar.clear()
        AppLogger.clearLogs()
    }

    @After
    fun tearDown() {
        CullinanHttpClient.resetCustomBaseUrl()
        CullinanHttpClient.cookieJar.clear()
        mockWebServer.shutdown()
        Dispatchers.resetMain()
    }

    private fun loadSanitizedProfileHtml(): String {
        val candidates = listOf(
            File("docs/05-samples-and-fixtures/MyProfile_page_sanitized.html"),
            File("../docs/05-samples-and-fixtures/MyProfile_page_sanitized.html")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate MyProfile_page_sanitized.html from ${File(".").absolutePath}")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun pageClassifier_identifiesMyProfileFixtureAsTypeProfile() {
        val html = loadSanitizedProfileHtml()
        val classification = PageClassifier.classify(
            html = html,
            httpStatusCode = 200,
            finalUrl = "https://jeton.bums.ac.ir/MyCullinan/MyProfile.aspx"
        )
        assertEquals(PageClassifier.TYPE_PROFILE, classification.pageType)
    }

    @Test
    fun profileParser_extractsAllFieldsFromSanitizedFixtureAndNormalizesNullsAndCredits() {
        val html = loadSanitizedProfileHtml()
        val outcome = ProfileParser.parse(
            html = html,
            httpStatusCode = 200,
            finalUrl = "https://jeton.bums.ac.ir/MyCullinan/MyProfile.aspx"
        )

        assertTrue("Expected Success outcome, got $outcome", outcome is ProfileFetchOutcome.Success)
        val profile = (outcome as ProfileFetchOutcome.Success).profile

        assertTrue(profile.isFullProfileLoaded)
        assertEquals("نام و نام خانوادگی نمونه", profile.fullName)
        assertEquals("دانشجو", profile.userType)
        assertEquals("4000000000000000", profile.idNumber)
        assertEquals("0000000000", profile.nationalCode)
        assertEquals("بومی", profile.group)
        assertEquals("بومی", profile.subGroup)
        assertEquals("مرد", profile.gender)
        assertNull("Placeholder '...' must become null", profile.description)
        assertEquals("دانشکده نمونه", profile.college)
        assertEquals("رشته نمونه", profile.field)
        assertEquals("مجوز شام دارد", profile.dorm)
        assertNull("Placeholder '...' must become null", profile.blockNumber)
        assertNull("Placeholder '...' must become null", profile.roomNumber)
        assertEquals("09000000000", profile.mobile)
        assertEquals("دکتری عمومی", profile.grade)
        assertEquals("user@example.com", profile.email)
        assertNull("Placeholder '...' must become null", profile.bankAccount)
        assertEquals("12:00:00 - 1405/01/01", profile.lastLogin)

        assertEquals("4000000000000000", profile.cardNumber)
        assertEquals("00000", profile.barcodeNumber)
        assertEquals("1400/01/01", profile.cardDeliveryDate)
        assertEquals("1450/12/29", profile.cardExpirationDate)
        assertEquals("فعال", profile.cardStatus)

        assertEquals(-123000L, profile.selfCreditRials)
        assertEquals("123,000- ریال (بدهی)", profile.selfCreditText)
        assertEquals(0L, profile.cafeteriaCreditRials)
        assertEquals("0 ریال", profile.cafeteriaCreditText)
        assertEquals("شنبه 01 فروردین 1405", profile.todayDate)
        assertEquals("Version : 8.9.78.1", profile.portalVersion)
        assertEquals("Last Update: 25/11/1402", profile.portalLastUpdate)
        assertNull("Hidden alertBox must not emit alertMessage", profile.alertMessage)

        val logsJoined = AppLogger.recentLogs.value.joinToString("\n")
        assertFalse(logsJoined.contains("09000000000"))
        assertFalse(logsJoined.contains("user@example.com"))
        assertFalse(logsJoined.contains("4000000000000000"))
    }

    @Test
    fun profileParser_failsClosedWhenRequiredAnchorsMissing() {
        val malformedHtml = "<html><head><title>کالینان</title></head><body><form id='MainForm' action='./MyProfile.aspx'><div>Empty</div></form></body></html>"
        val outcome = ProfileParser.parse(malformedHtml, 200, "https://jeton.bums.ac.ir/MyCullinan/MyProfile.aspx")
        assertTrue(outcome is ProfileFetchOutcome.Failure)
    }

    @Test
    fun profileParser_extractsVisibleAlertBoxAndPersianDigits() {
        val html = loadSanitizedProfileHtml()
            .replace("style=\"display:none;\"", "style=\"display:block;\"")
            .replace("<label id=\"cphMain_alertBoxValue\">\n                        </label>", "<label id=\"cphMain_alertBoxValue\">کارت موقتاً مسدود است</label>")
            .replace("123,000- ريال ", "۴۵۰,۰۰۰- ريال ")
        val outcome = ProfileParser.parse(html, 200) as ProfileFetchOutcome.Success
        assertEquals("کارت موقتاً مسدود است", outcome.profile.alertMessage)
        assertEquals(-450000L, outcome.profile.selfCreditRials)
    }

    @Test
    fun reservationHtmlParser_mapsDivUsernameToNameAndLblIdNumberToStudentNumber() {
        val sidebarOnlyHtml = """
            <html><body>
                <form id="MainForm" action="./Reservation.aspx">
                    <div id="divUsername" class="profileTitle">فاطمه احمدی</div>
                    <label id="lblIdNumber">4031009988</label>
                    <label id="cphMain_lblCreditValue">۱۲۰,۰۰۰- ريال </label>
                </form>
            </body></html>
        """.trimIndent()

        val menu = ReservationHtmlParser.parse(sidebarOnlyHtml)
        assertEquals("فاطمه احمدی", menu.studentName)
        assertEquals("4031009988", menu.studentNumber)
        assertEquals(-120000L, menu.creditValueRials)
    }

    @Test
    fun profileRepository_fetchProfile_neverSavesHtmlSnapshotAndHandlesRedirectsAndErrors() = runTest(testDispatcher) {
        val repo = ProfileRepository(testDispatcher)
        val sampleHtml = loadSanitizedProfileHtml()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(sampleHtml)
        )

        val successOutcome = repo.fetchProfile()
        assertTrue(successOutcome is ProfileFetchOutcome.Success)
        assertEquals(
            "MyProfile.aspx must NEVER be stored in AppLogger pageSnapshots",
            0,
            AppLogger.pageSnapshots.value.count { it.url.contains("MyProfile.aspx", ignoreCase = true) }
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/Login.aspx?ReturnUrl=%2fMyCullinan%2fMyProfile.aspx")
        )
        assertTrue(repo.fetchProfile() is ProfileFetchOutcome.SessionExpired)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/MyCullinan/MyMessage.aspx")
        )
        assertTrue(repo.fetchProfile() is ProfileFetchOutcome.UnreadMessagesRequired)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )
        assertTrue(repo.fetchProfile() is ProfileFetchOutcome.Failure)
    }

    @Test
    fun profileRepository_fetchProfilePhoto_handlesNonStandardImageJpgAndRejectsHtmlSpoofing() = runTest(testDispatcher) {
        val repo = ProfileRepository(testDispatcher)
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/jpg")
                .setBody(Buffer().write(fakeJpeg))
        )
        val okResult = repo.fetchProfilePhoto()
        assertTrue(okResult.isSuccess)
        assertArrayEquals(fakeJpeg, okResult.getOrNull())

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body>Login Required</body></html>")
        )
        assertTrue(repo.fetchProfilePhoto().isFailure)

        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        val notFoundResult = repo.fetchProfilePhoto()
        assertTrue(notFoundResult.isSuccess)
        assertNull(notFoundResult.getOrNull())

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/Login.aspx")
        )
        val expiredResult = repo.fetchProfilePhoto()
        assertTrue(expiredResult.isFailure)
        assertTrue(expiredResult.exceptionOrNull() is ProfilePhotoSessionExpiredException)
    }

    @Test
    fun profileViewModel_lazyFetchesProfileOnlyOnOpenAndWipesAllStateOnClearSession() = runTest(testDispatcher) {
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val sampleHtml = loadSanitizedProfileHtml()

        val vm = ProfileViewModel(ProfileRepository(testDispatcher))
        val session = UserSession(
            isLoggedIn = true,
            username = "4000000000000000",
            studentName = "کاربر موقت",
            studentNumber = "4000000000000000",
            creditFormatted = "50,000 ریال"
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/jpg")
                .setBody(Buffer().write(fakeJpeg))
        )
        vm.ensureAvatarLoaded()
        advanceUntilIdle()

        assertArrayEquals(fakeJpeg, vm.avatarBytes.value)
        assertEquals(ProfileUiState.Idle, vm.profileState.value)
        assertEquals("Only UserImage.ashx should be fetched before user taps Profile", 1, mockWebServer.requestCount)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(sampleHtml)
        )
        vm.openProfile(session, null)
        assertTrue(vm.isProfileSheetOpen.value)
        advanceUntilIdle()

        val loaded = vm.profileState.value as? ProfileUiState.Loaded
        assertNotNull(loaded)
        assertEquals("0000000000", loaded?.profile?.nationalCode)
        assertEquals(2, mockWebServer.requestCount)

        vm.openProfile(session, null)
        advanceUntilIdle()
        assertEquals("Re-opening already loaded profile should reuse in-memory cache", 2, mockWebServer.requestCount)

        vm.clearSession()
        assertNull(vm.avatarBytes.value)
        assertEquals(ProfileUiState.Idle, vm.profileState.value)
        assertFalse(vm.isProfileSheetOpen.value)
    }

    @Test
    fun profilePrivacyFormatter_and_logSanitizer_maskSensitiveValues() {
        assertEquals("000••••000", ProfilePrivacyFormatter.mask("0000000000", SensitiveFieldKind.NATIONAL_CODE))
        assertEquals("0900••••000", ProfilePrivacyFormatter.mask("09000000000", SensitiveFieldKind.MOBILE))
        assertEquals("u•••@example.com", ProfilePrivacyFormatter.mask("user@example.com", SensitiveFieldKind.EMAIL))
        assertEquals("4000••••••••0000", ProfilePrivacyFormatter.mask("4000000000000000", SensitiveFieldKind.ID_OR_CARD))
        assertEquals("ثبت‌نشده", ProfilePrivacyFormatter.mask(null, SensitiveFieldKind.EMAIL))

        val sanitized = LogSanitizer.sanitizeText(
            "User logged in: 4031001234 nationalCode=1234567890 mobile=09123456789 email=person@uni.ac.ir cardNumber=6037991122334455"
        )
        assertFalse(sanitized.contains("4031001234"))
        assertFalse(sanitized.contains("1234567890"))
        assertFalse(sanitized.contains("09123456789"))
        assertFalse(sanitized.contains("person@uni.ac.ir"))
        assertFalse(sanitized.contains("6037991122334455"))
    }
}
