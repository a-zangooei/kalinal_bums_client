package com.example

import com.example.data.model.WeekNavigationDirection
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.DeltaParser
import com.example.data.parser.ReservationHtmlParser
import com.example.data.repository.MenuRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

class WeekNavigationUnitTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: MenuRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        CullinanHttpClient.setCustomBaseUrl(mockWebServer.url("/").toString().trimEnd('/'))
        repository = MenuRepository()
    }

    @After
    fun tearDown() {
        CullinanHttpClient.resetCustomBaseUrl()
        mockWebServer.shutdown()
    }

    private fun loadHarEntry0(): String {
        val candidates = listOf(
            File("docs/05-samples-and-fixtures/har_entry0_resp.txt"),
            File("../docs/05-samples-and-fixtures/har_entry0_resp.txt"),
            File("app/src/test/resources/har_entry0_resp.txt"),
            File("src/test/resources/har_entry0_resp.txt")
        )
        val file = candidates.find { it.exists() }
        if (file != null) return file.readText()
        val stream = javaClass.classLoader?.getResourceAsStream("har_entry0_resp.txt")
        if (stream != null) return stream.bufferedReader().readText()
        throw IllegalStateException("har_entry0_resp.txt not found")
    }

    @Test
    fun testBuildNavigateWeekBodyIncludesAllFormControlsAndState() {
        val delta0 = DeltaParser.parse(loadHarEntry0())
        val baseMenu = ReservationHtmlParser.parse(
            html = delta0.updatePanels["UpdatePanel1"]!!,
            existingViewState = delta0.hiddenFields["__VIEWSTATE"]!!,
            existingGenerator = delta0.hiddenFields["__VIEWSTATEGENERATOR"]!!,
            existingEventValidation = "EV_INITIAL_WEEK"
        )

        val nextBody = repository.buildNavigateWeekBody(
            direction = WeekNavigationDirection.NEXT,
            currentMenu = baseMenu,
            selectedTabId = "css3-tabstrip-0-1"
        )
        val nextParams = (0 until nextBody.size).associate {
            nextBody.name(it) to nextBody.value(it)
        }

        assertEquals("ctl00\$UpdatePanel1|ctl00\$cphMain\$imgbtnNextWeek", nextParams["ctl00\$ScriptManager"])
        assertEquals("css3-tabstrip-0-1", nextParams["ctl00\$cphMain\$hdnSelectedTab"])
        assertEquals("on", nextParams["css3-tabstrip-0"])
        assertEquals("", nextParams["__LASTFOCUS"])
        assertEquals("EV_INITIAL_WEEK", nextParams["__EVENTVALIDATION"])
        assertEquals("true", nextParams["__ASYNCPOST"])
        assertEquals("8", nextParams["ctl00\$cphMain\$imgbtnNextWeek.x"])
        assertEquals("29", nextParams["ctl00\$cphMain\$imgbtnNextWeek.y"])
        assertTrue(
            "Must include existing GridView controls in navigation postback",
            nextParams.keys.any { it.contains("grdReservationLunch") && it.contains("drpFood") }
        )

        val prevBody = repository.buildNavigateWeekBody(
            direction = WeekNavigationDirection.PREVIOUS,
            currentMenu = baseMenu
        )
        val prevParams = (0 until prevBody.size).associate {
            prevBody.name(it) to prevBody.value(it)
        }
        assertEquals("ctl00\$UpdatePanel1|ctl00\$cphMain\$imgbtnLastWeek", prevParams["ctl00\$ScriptManager"])
        assertEquals("8", prevParams["ctl00\$cphMain\$imgbtnLastWeek.x"])

        val currentBody = repository.buildNavigateWeekBody(
            direction = WeekNavigationDirection.CURRENT,
            currentMenu = baseMenu
        )
        val currentParams = (0 until currentBody.size).associate {
            currentBody.name(it) to currentBody.value(it)
        }
        assertEquals("ctl00\$UpdatePanel1|ctl00\$cphMain\$imgbtnCurrentWeek", currentParams["ctl00\$ScriptManager"])
    }

    @Test
    fun testNavigateWeekChainsViewStateAndEventValidationSequentially() = runBlocking {
        val delta0 = DeltaParser.parse(loadHarEntry0())
        val initialMenu = ReservationHtmlParser.parse(
            html = delta0.updatePanels["UpdatePanel1"]!!,
            existingViewState = "VS_WEEK_0",
            existingGenerator = "3ED98A75",
            existingEventValidation = "EV_WEEK_0"
        )

        val week1Html = """
            <div>
                <label id="cphMain_lblWeekDate">1405/08/23 الی 1405/08/29</label>
                <label id="cphMain_lblCreditValue">اعتبار شما: 590,000 ریال</label>
            </div>
        """.trimIndent()
        val week1Delta = "1|#||4|${week1Html.length}|updatePanel|ctl00\$UpdatePanel1|$week1Html|9|hiddenField|__VIEWSTATE|VS_WEEK_1|9|hiddenField|__EVENTVALIDATION|EV_WEEK_1|"

        val week0BackHtml = """
            <div>
                <label id="cphMain_lblWeekDate">1405/08/16 الی 1405/08/22</label>
                <div id="cphMain_alertBox" class="alertBox" style="display:block;"><label id="cphMain_alertBoxValue">بازگشت به هفته جاری</label></div>
            </div>
        """.trimIndent()
        val week0Delta = "1|#||4|${week0BackHtml.length}|updatePanel|UpdatePanel1|$week0BackHtml|9|hiddenField|__VIEWSTATE|VS_WEEK_2|9|hiddenField|__EVENTVALIDATION|EV_WEEK_2|"

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(week1Delta))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(week0Delta))

        val res1 = repository.navigateWeek(WeekNavigationDirection.NEXT, initialMenu, 0)
        assertTrue(res1.isSuccess)
        val (menu1, offset1) = res1.getOrThrow()
        assertEquals(1, offset1)
        assertEquals("1405/08/23 الی 1405/08/29", menu1.weekDateRange)
        assertEquals("VS_WEEK_1", menu1.viewState)
        assertEquals("EV_WEEK_1", menu1.eventValidation)

        val res2 = repository.navigateWeek(WeekNavigationDirection.PREVIOUS, menu1, offset1)
        assertTrue(res2.isSuccess)
        val (menu2, offset2) = res2.getOrThrow()
        assertEquals(0, offset2)
        assertEquals("1405/08/16 الی 1405/08/22", menu2.weekDateRange)
        assertEquals("بازگشت به هفته جاری", menu2.alertMessage)
        assertEquals("VS_WEEK_2", menu2.viewState)
        assertEquals("EV_WEEK_2", menu2.eventValidation)

        val req1Body = URLDecoder.decode(mockWebServer.takeRequest().body.readUtf8(), StandardCharsets.UTF_8.name())
        assertTrue(req1Body.contains("__VIEWSTATE=VS_WEEK_0"))
        assertTrue(req1Body.contains("__EVENTVALIDATION=EV_WEEK_0"))

        val req2Body = URLDecoder.decode(mockWebServer.takeRequest().body.readUtf8(), StandardCharsets.UTF_8.name())
        assertTrue(req2Body.contains("__VIEWSTATE=VS_WEEK_1"))
        assertTrue(req2Body.contains("__EVENTVALIDATION=EV_WEEK_1"))
    }

    @Test
    fun testDeltaParserAndNavigateWeekSurfaceServerDeltaError() = runBlocking {
        val errorText = "خطای اعتبارسنجی درخواست در سرور"
        val errorDelta = "1|#||4|${errorText.length}|error|500|$errorText|"
        val parsed = DeltaParser.parse(errorDelta)
        assertEquals("500", parsed.errorStatusCode)
        assertEquals(errorText, parsed.errorMessage)

        val delta0 = DeltaParser.parse(loadHarEntry0())
        val initialMenu = ReservationHtmlParser.parse(
            html = delta0.updatePanels["UpdatePanel1"]!!,
            existingViewState = "VS_INIT",
            existingGenerator = "3ED98A75"
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(errorDelta))

        val result = repository.navigateWeek(WeekNavigationDirection.NEXT, initialMenu, 0)
        assertTrue("Navigate week must fail on delta error token", result.isFailure)
        val msg = result.exceptionOrNull()?.message
        assertNotNull(msg)
        assertFalse(msg.isNullOrBlank())
        assertTrue(msg!!.contains(errorText))
    }

    @Test
    fun testNavigateWeekSendsBrowserUserAgentOnStandardAndFallbackClients() = runBlocking {
        val delta0 = DeltaParser.parse(loadHarEntry0())
        val initialMenu = ReservationHtmlParser.parse(
            html = delta0.updatePanels["UpdatePanel1"]!!,
            existingViewState = "VS_UA_TEST",
            existingGenerator = "3ED98A75"
        )
        val weekHtml = """<div><label id="cphMain_lblWeekDate">1405/08/23 الی 1405/08/29</label></div>"""
        val okDelta = "1|#||4|${weekHtml.length}|updatePanel|UpdatePanel1|$weekHtml|8|hiddenField|__VIEWSTATE|VS_UA_2|"

        val initialCompat = CullinanHttpClient.isCompatibilityModeActive.value
        try {
            CullinanHttpClient.setCompatibilityMode(false)
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(okDelta))
            val resStd = repository.navigateWeek(WeekNavigationDirection.NEXT, initialMenu, 0)
            assertTrue(resStd.isSuccess)
            val stdReq = mockWebServer.takeRequest()
            val stdUa = stdReq.getHeader("User-Agent") ?: ""
            assertEquals(CullinanHttpClient.DEFAULT_USER_AGENT, stdUa)
            assertFalse(stdUa.startsWith("okhttp/"))

            CullinanHttpClient.setCompatibilityMode(true)
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(okDelta))
            val resFb = repository.navigateWeek(WeekNavigationDirection.PREVIOUS, initialMenu, 1)
            assertTrue(resFb.isSuccess)
            val fbReq = mockWebServer.takeRequest()
            val fbUa = fbReq.getHeader("User-Agent") ?: ""
            assertEquals(CullinanHttpClient.DEFAULT_USER_AGENT, fbUa)
        } finally {
            CullinanHttpClient.setCompatibilityMode(initialCompat)
        }
    }

    @Test
    fun testFullPageHtmlIgnoresScriptManagerHiddenFieldAndUpdateProgress() {
        val fullPageHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <form id="MainForm" action="./Reservation.aspx">
                    <input type="hidden" name="ctl00_ScriptManager_HiddenField" id="ctl00_ScriptManager_HiddenField" value="" />
                    <input type="hidden" name="__VIEWSTATE" value="VS_FULL_PAGE" />
                    <input type="hidden" name="__VIEWSTATEGENERATOR" value="3ED98A75" />
                    <input type="hidden" name="__EVENTVALIDATION" value="EV_FULL_PAGE" />
                    <div id="ctl00_UpdateProgress1">در حال بارگذاری...</div>
                    <div id="UpdatePanel1">
                        <label id="cphMain_lblWeekDate">1405/08/16 الی 1405/08/22</label>
                    </div>
                </form>
            </body>
            </html>
        """.trimIndent()

        val parsed = ReservationHtmlParser.parse(fullPageHtml)
        assertEquals("ctl00\$ScriptManager", parsed.scriptManagerName)
        assertEquals("ctl00\$UpdatePanel1", parsed.updatePanelName)

        val navBody = repository.buildNavigateWeekBody(WeekNavigationDirection.NEXT, parsed)
        val params = (0 until navBody.size).associate { navBody.name(it) to navBody.value(it) }
        assertEquals(
            "ctl00\$UpdatePanel1|ctl00\$cphMain\$imgbtnNextWeek",
            params["ctl00\$ScriptManager"]
        )
        assertFalse(params.containsKey("ctl00_ScriptManager_HiddenField"))
    }
}
