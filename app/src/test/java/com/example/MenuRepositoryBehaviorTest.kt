package com.example

import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.data.model.UnreadMessagesException
import com.example.data.model.WeekNavigationDirection
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.DeltaParser
import com.example.data.parser.ReservationHtmlParser
import com.example.data.repository.MenuRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MenuRepositoryBehaviorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: MenuRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        CullinanHttpClient.cookieJar.clear()
        CullinanHttpClient.setCustomBaseUrl(mockWebServer.url("/").toString().trimEnd('/'))
        repository = MenuRepository()
    }

    @After
    fun tearDown() {
        CullinanHttpClient.cookieJar.clear()
        CullinanHttpClient.resetCustomBaseUrl()
        mockWebServer.shutdown()
    }

    private fun loadHarEntry(index: Int): String {
        val candidates = listOf(
            File("docs/05-samples-and-fixtures/har_entry${index}_resp.txt"),
            File("../docs/05-samples-and-fixtures/har_entry${index}_resp.txt"),
            File("app/src/test/resources/har_entry${index}_resp.txt"),
            File("src/test/resources/har_entry${index}_resp.txt")
        )
        val file = candidates.find { it.exists() }
        if (file != null) return file.readText()

        val stream = javaClass.classLoader?.getResourceAsStream("har_entry${index}_resp.txt")
        if (stream != null) return stream.bufferedReader().readText()

        throw IllegalStateException("har_entry${index}_resp.txt not found")
    }

    private fun parseMenuFromHar(index: Int): com.example.data.model.WeeklyMenu {
        val delta = DeltaParser.parse(loadHarEntry(index))
        return ReservationHtmlParser.parse(
            html = delta.updatePanels["UpdatePanel1"]!!,
            existingViewState = delta.hiddenFields["__VIEWSTATE"] ?: "",
            existingGenerator = delta.hiddenFields["__VIEWSTATEGENERATOR"] ?: "3ED98A75",
            existingEventValidation = delta.hiddenFields["__EVENTVALIDATION"] ?: ""
        )
    }

    @Test
    fun fetchInitialMenu_parsesReservationPageAndState() = runBlocking {
        val fullHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <form id="MainForm" action="./Reservation.aspx">
                    <input type="hidden" name="__VIEWSTATE" value="VS_INIT_FULL" />
                    <input type="hidden" name="__VIEWSTATEGENERATOR" value="3ED98A75" />
                    <input type="hidden" name="__EVENTVALIDATION" value="EV_INIT_FULL" />
                    <div id="ctl00_UpdatePanel1">
                        <label id="cphMain_lblWeekDate">1405/08/16 الی 1405/08/22</label>
                        <label id="cphMain_lblCreditValue">اعتبار شما: 590,000 ریال</label>
                        <label id="cphMain_titleBoxValue">کاربر: دانشجو آزمایشی - 4031001122</label>
                        <table id="cphMain_grdReservationLunch">
                            <tr><th>انتخاب</th><th>روز</th><th>سلف</th><th>غذا</th><th>تعداد</th><th>کالری</th><th>مبلغ</th><th>وضعیت</th></tr>
                            <tr>
                                <td><input type="checkbox" name="ctl00${'$'}cphMain${'$'}grdReservationLunch${'$'}ctl02${'$'}chkReserve" /></td>
                                <td>شنبه 1405/08/16</td>
                                <td><select name="ctl00${'$'}cphMain${'$'}grdReservationLunch${'$'}ctl02${'$'}drpSelf"><option selected="selected" value="1">سلف پردیس</option></select></td>
                                <td><select name="ctl00${'$'}cphMain${'$'}grdReservationLunch${'$'}ctl02${'$'}drpFood"><option selected="selected" value="-1">انتخاب کنید</option><option value="89247">چلو جوجه کباب 180000 ریال</option></select></td>
                                <td><select name="ctl00${'$'}cphMain${'$'}grdReservationLunch${'$'}ctl02${'$'}drpFoodCount"><option selected="selected" value="0">0</option></select></td>
                                <td><span>650</span></td>
                                <td><span>180000</span></td>
                                <td><img src="../Images/Icon/uncheck24.png" title="رزرو نشده" /></td>
                            </tr>
                        </table>
                    </div>
                </form>
            </body>
            </html>
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(fullHtml))

        val result = repository.fetchInitialMenu()
        assertTrue(result.isSuccess)
        val menu = result.getOrThrow()
        assertEquals("1405/08/16 الی 1405/08/22", menu.weekDateRange)
        assertEquals(590000L, menu.creditValueRials)
        assertEquals("VS_INIT_FULL", menu.viewState)
        assertEquals("EV_INIT_FULL", menu.eventValidation)
        assertEquals("ctl00\$UpdatePanel1", menu.updatePanelName)
    }

    @Test
    fun fetchInitialMenu_failsWithUnreadMessagesExceptionWhenBlocked() = runBlocking {
        val messagesHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <form id="MainForm" action="./MyMessage.aspx">
                    <div id="cphMain_grdContent">پیام خوانده نشده</div>
                    <span id="cphMain_lblUnreadCount">2</span>
                    <input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl02${'$'}imgbtnMessageText" />
                </form>
            </body>
            </html>
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(messagesHtml)
        )

        val result = repository.fetchInitialMenu()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Expected UnreadMessagesException but got $error", error is UnreadMessagesException)
    }

    @Test
    fun fetchInitialMenu_failsOnLoginPageOrHttpError() = runBlocking {
        val loginHtml = """
            <html><body>
                <input type="text" id="txtUsernamePlain" name="txtUsernamePlain" />
                <input type="password" id="txtPasswordPlain" name="txtPasswordPlain" />
                <input type="submit" id="btnLogin" name="btnLogin" value="ورود" />
                <textarea id="publicKeytxt"></textarea>
            </body></html>
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))
        val loginBlocked = repository.fetchInitialMenu()
        assertTrue(loginBlocked.isFailure)
        assertTrue(loginBlocked.exceptionOrNull()?.message?.contains("منقضی") == true)

        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        val httpError = repository.fetchInitialMenu()
        assertTrue(httpError.isFailure)
        assertTrue(httpError.exceptionOrNull()?.message?.contains("503") == true)
    }

    @Test
    fun reserveAndCancelLunch_withHarFixtures_chainHiddenFieldsAndUpdateMenu() = runBlocking {
        val initialMenu = parseMenuFromHar(0)
        val harReserve = loadHarEntry(1)
        val harCancel = loadHarEntry(2)

        val reserveDelta = DeltaParser.parse(harReserve)
        val expectedVsAfterReserve = reserveDelta.hiddenFields["__VIEWSTATE"]!!
        val expectedEvAfterReserve = reserveDelta.hiddenFields["__EVENTVALIDATION"] ?: initialMenu.eventValidation

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(harReserve))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(harCancel))

        // 1. Reserve Wednesday lunch (dayIndex = 4)
        val reserveResult = repository.reserveLunch(
            currentMenu = initialMenu,
            targetDayIndex = 4,
            targetFoodId = "89247",
            targetSelfId = "1"
        )
        assertTrue("Reserve lunch should succeed", reserveResult.isSuccess)
        val res1 = reserveResult.getOrThrow()
        assertTrue(res1.isSuccess)
        assertEquals("success", res1.toastType.lowercase())
        assertNotNull(res1.updatedMenu)
        val menuAfterReserve = res1.updatedMenu!!
        assertEquals(410000L, menuAfterReserve.creditValueRials)
        assertEquals(
            ReservationStatus.RESERVED,
            menuAfterReserve.days[4].meals[MealType.LUNCH]?.firstOrNull()?.status
        )
        assertEquals(expectedVsAfterReserve, menuAfterReserve.viewState)

        // 2. Cancel Wednesday lunch using updatedMenu from reserve response
        val cancelResult = repository.cancelLunch(
            currentMenu = menuAfterReserve,
            targetDayIndex = 4
        )
        assertTrue("Cancel lunch should succeed", cancelResult.isSuccess)
        val res2 = cancelResult.getOrThrow()
        assertTrue(res2.isSuccess)
        val menuAfterCancel = res2.updatedMenu!!
        assertEquals(590000L, menuAfterCancel.creditValueRials)
        assertEquals(
            ReservationStatus.NOT_RESERVED,
            menuAfterCancel.days[4].meals[MealType.LUNCH]?.firstOrNull()?.status
        )

        // Verify outgoing HTTP requests & ViewState chaining
        val req1 = mockWebServer.takeRequest()
        assertEquals("Delta=true", req1.getHeader("X-MicrosoftAjax"))
        assertEquals("XMLHttpRequest", req1.getHeader("X-Requested-With"))
        val req1Body = URLDecoder.decode(req1.body.readUtf8(), StandardCharsets.UTF_8.name())
        assertTrue(req1Body.contains("ctl00\$cphMain\$grdReservationLunch\$ctl06\$chkReserve=on"))
        assertTrue(req1Body.contains("ctl00\$cphMain\$grdReservationLunch\$ctl06\$drpFood=89247"))

        val req2 = mockWebServer.takeRequest()
        val req2Body = URLDecoder.decode(req2.body.readUtf8(), StandardCharsets.UTF_8.name())
        assertFalse(
            "Cancel request must omit chkReserve for target day",
            req2Body.contains("ctl00\$cphMain\$grdReservationLunch\$ctl06\$chkReserve=")
        )
        assertTrue(req2Body.contains("__VIEWSTATE=$expectedVsAfterReserve"))
        if (expectedEvAfterReserve.isNotEmpty()) {
            assertTrue(req2Body.contains("__EVENTVALIDATION=$expectedEvAfterReserve"))
        }
    }

    @Test
    fun reserveAndCancel_handleSessionExpiredErrorAndMissingUpdatePanel() = runBlocking {
        val menu = parseMenuFromHar(0)

        // 1. Session expired pageRedirect token
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("1|#||4|26|pageRedirect||/Login.aspx|"))
        val expired = repository.reserveMeal(menu, MealType.LUNCH, 4, "89247", "1")
        assertTrue(expired.isFailure)
        assertTrue(expired.exceptionOrNull()?.message?.contains("منقضی") == true)

        // 2. Delta error token
        val errText = "اعتبار ناکافی است"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("1|#||4|${errText.length}|error|500|$errText|"))
        val serverErr = repository.cancelMeal(menu, MealType.LUNCH, 4)
        assertTrue(serverErr.isFailure)
        assertTrue(serverErr.exceptionOrNull()?.message?.contains(errText) == true)

        // 3. Missing UpdatePanel1
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("1|#||4|4|hiddenField|__VIEWSTATE|ABCD|"))
        val missingPanel = repository.reserveMeal(menu, MealType.LUNCH, 4, "89247", "1")
        assertTrue(missingPanel.isFailure)
        assertTrue(missingPanel.exceptionOrNull()?.message?.contains("ناقص") == true)

        // 4. HTTP error on cancel
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val httpErr = repository.cancelMeal(menu, MealType.LUNCH, 4)
        assertTrue(httpErr.isFailure)
        assertTrue(httpErr.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun concurrentOperations_areSerializedByRepositoryMutex() = runBlocking {
        val menu = parseMenuFromHar(0)
        val harReserve = loadHarEntry(1)
        val harCancel = loadHarEntry(2)

        val inFlight = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        mockWebServer.dispatcher = object : Dispatcher() {
            private val count = AtomicInteger(0)
            override fun dispatch(request: RecordedRequest): MockResponse {
                val active = inFlight.incrementAndGet()
                maxConcurrent.updateAndGet { prev -> maxOf(prev, active) }
                try {
                    val idx = count.getAndIncrement()
                    if (idx == 0) {
                        Thread.sleep(100)
                        return MockResponse().setResponseCode(200).setBody(harReserve)
                    }
                    return MockResponse().setResponseCode(200).setBody(harCancel)
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        }

        coroutineScope {
            val first = async { repository.reserveLunch(menu, 4, "89247", "1") }
            val second = async { repository.navigateWeek(WeekNavigationDirection.NEXT, menu, 0) }
            val r1 = first.await()
            val r2 = second.await()
            assertTrue(r1.isSuccess)
            assertTrue(r2.isSuccess)
        }

        assertEquals(
            "MenuRepository mutex must prevent concurrent HTTP execution",
            1,
            maxConcurrent.get()
        )
    }
}
