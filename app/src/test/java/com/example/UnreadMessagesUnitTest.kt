package com.example

import com.example.data.model.UnreadMessagesException
import com.example.data.model.UserSession
import com.example.data.parser.DeltaParser
import com.example.data.parser.PageClassifier
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnreadMessagesUnitTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private val sampleMessagesHtmlWithUnread = """
        <html>
        <head><title>صندوق پیام ها</title></head>
        <body>
        <form id="MainForm" action="./MyMessage.aspx">
            <input type="hidden" name="__VIEWSTATE" id="__VIEWSTATE" value="INITIAL_VS_MESSAGES" />
            <input type="hidden" name="__VIEWSTATEGENERATOR" id="__VIEWSTATEGENERATOR" value="87AF9C2D" />
            
            <table id="cphMain_grdContent" class="gridStyle">
                <tr class="HeaderStyle">
                    <th>ردیف</th><th>عنوان پیام</th><th>متن پیام</th><th>تاریخ ارسال</th><th>زمان ارسال</th><th>وضعیت</th><th>تاریخ خواندن</th><th>زمان خواندن</th>
                </tr>
                <tr>
                    <td>1</td>
                    <td>همراه داشتن کارت دانشجویی</td>
                    <td><input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl02${'$'}imgbtnMessageText" src="../Images/Icon/view.png" /></td>
                    <td>1405/08/18</td>
                    <td>21:30:15</td>
                    <td><img src="../Images/Icon/MsgClose.png" title="خوانده نشده" /></td>
                    <td>-</td>
                    <td>-</td>
                </tr>
                <tr>
                    <td>2</td>
                    <td>اطلاعیه توزیع غذای روز دانشجو</td>
                    <td><input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl03${'$'}imgbtnMessageText" src="../Images/Icon/view.png" /></td>
                    <td>1405/08/15</td>
                    <td>10:00:00</td>
                    <td><img src="../Images/Icon/MsgOpen.png" title="خوانده شده" /></td>
                    <td>1405/08/16</td>
                    <td>12:30:45</td>
                </tr>
                <tr>
                    <td>3</td>
                    <td>تغییر برنامه سلف مرکزی</td>
                    <td><input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl04${'$'}imgbtnMessageText" src="../Images/Icon/view.png" /></td>
                    <td>1405/08/10</td>
                    <td>08:15:00</td>
                    <td><img src="../Images/Icon/MsgOpen.png" title="خوانده شده" /></td>
                    <td>1405/08/10</td>
                    <td>09:00:00</td>
                </tr>
            </table>
        </form>
        </body>
        </html>
    """.trimIndent()

    private val sampleMessagesHtmlAllReadWithUnlockedMenu = """
        <html>
        <head><title>صندوق پیام ها</title></head>
        <body>
        <div id="navigationMenu">
            <a href="../Reservation/Reservation.aspx">رزرو غذا</a>
            <a href="../Reservation/DailyReservation.aspx">رزرو روزفروش</a>
        </div>
        <table id="cphMain_grdContent" class="gridStyle">
            <tr class="HeaderStyle">
                <th>ردیف</th><th>عنوان پیام</th><th>متن پیام</th><th>تاریخ ارسال</th><th>زمان ارسال</th><th>وضعیت</th><th>تاریخ خواندن</th><th>زمان خواندن</th>
            </tr>
            <tr>
                <td>1</td>
                <td>همراه داشتن کارت دانشجویی</td>
                <td><input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl02${'$'}imgbtnMessageText" src="../Images/Icon/view.png" /></td>
                <td>1405/08/18</td>
                <td>21:30:15</td>
                <td><img src="../Images/Icon/MsgOpen.png" title="خوانده شده" /></td>
                <td>1405/08/18</td>
                <td>22:44:50</td>
            </tr>
        </table>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun testPageClassifierIdentifiesUnreadMessagesPage() {
        val result = PageClassifier.classify(
            html = sampleMessagesHtmlWithUnread,
            httpStatusCode = 200,
            finalUrl = "https://jeton.bums.ac.ir/MyCullinan/MyMessage.aspx"
        )

        assertEquals(PageClassifier.TYPE_MESSAGES, result.pageType)
        assertTrue("Must detect unread messages", result.hasUnreadMessages)
        assertEquals("Should have exactly 1 unread message", 1, result.unreadMessageCount)
        assertFalse("Should not have pagination in sample", result.hasPaginationControls)
    }

    @Test
    fun testPageClassifierIdentifiesAllReadMessagesPage() {
        val result = PageClassifier.classify(
            html = sampleMessagesHtmlAllReadWithUnlockedMenu,
            httpStatusCode = 200,
            finalUrl = "https://jeton.bums.ac.ir/MyCullinan/MyMessage.aspx"
        )

        assertEquals(PageClassifier.TYPE_MESSAGES, result.pageType)
        assertFalse("All messages are read", result.hasUnreadMessages)
        assertEquals(0, result.unreadMessageCount)
    }

    @Test
    fun testPageClassifierDetectsPaginationControls() {
        val paginatedHtml = """
            <table id="cphMain_grdContent">
                <tr class="HeaderStyle"><th>ردیف</th></tr>
                <tr><td>1</td></tr>
                <tr class="PagerStyle"><td><span>1</span><a href="#">2</a></td></tr>
            </table>
        """.trimIndent()

        val result = PageClassifier.classify(paginatedHtml, 200, "https://jeton.bums.ac.ir/MyCullinan/MyMessage.aspx")
        assertEquals(PageClassifier.TYPE_MESSAGES, result.pageType)
        assertTrue("Must detect pagination controls", result.hasPaginationControls)
    }

    @Test
    fun testDeltaParserExtractsMessageContentAndChainedViewState() {
        val deltaBody = "1|#||4|114|updatePanel|UpdatePanel1|<div><p id=\"cphMain_pMessage\">همراه داشتن کارت دانشجویی در سلف الزامی است.</p></div>|26|hiddenField|__VIEWSTATE|CHAINED_VS_AFTER_MSG_1|8|hiddenField|__VIEWSTATEGENERATOR|87AF9C2D|"
        val deltaResponse = DeltaParser.parse(deltaBody)

        assertEquals("CHAINED_VS_AFTER_MSG_1", deltaResponse.hiddenFields["__VIEWSTATE"])
        assertEquals("87AF9C2D", deltaResponse.hiddenFields["__VIEWSTATEGENERATOR"])

        val panelHtml = deltaResponse.updatePanels["UpdatePanel1"]
        assertNotNull(panelHtml)

        val doc = Jsoup.parse(panelHtml ?: "")
        val messageText = doc.select("#cphMain_pMessage").text().trim()
        assertEquals("همراه داشتن کارت دانشجویی در سلف الزامی است.", messageText)
    }

    @Test
    fun testSerializedViewStateChainingSimulation() {
        var currentViewState = "VS_INITIAL"

        // Simulation of message 1 opening
        val delta1 = "1|#||4|80|updatePanel|UpdatePanel1|<p id=\"cphMain_pMessage\">پیام ۱</p>|20|hiddenField|__VIEWSTATE|VS_CHAIN_1|"
        val parsedDelta1 = DeltaParser.parse(delta1)
        val vs1 = parsedDelta1.hiddenFields["__VIEWSTATE"] ?: currentViewState
        currentViewState = vs1
        assertEquals("VS_CHAIN_1", currentViewState)

        // Simulation of message 2 opening with chained VS
        val delta2 = "1|#||4|80|updatePanel|UpdatePanel1|<p id=\"cphMain_pMessage\">پیام ۲</p>|20|hiddenField|__VIEWSTATE|VS_CHAIN_2|"
        val parsedDelta2 = DeltaParser.parse(delta2)
        val vs2 = parsedDelta2.hiddenFields["__VIEWSTATE"] ?: currentViewState
        currentViewState = vs2
        assertEquals("VS_CHAIN_2", currentViewState)

        // Simulation of message 3 opening with chained VS
        val delta3 = "1|#||4|80|updatePanel|UpdatePanel1|<p id=\"cphMain_pMessage\">پیام ۳</p>|20|hiddenField|__VIEWSTATE|VS_CHAIN_3|"
        val parsedDelta3 = DeltaParser.parse(delta3)
        val vs3 = parsedDelta3.hiddenFields["__VIEWSTATE"] ?: currentViewState
        currentViewState = vs3
        assertEquals("VS_CHAIN_3", currentViewState)
    }

    @Test
    fun testLoginRedirectInspectionLogic() {
        // Test 1: Location to MyMessage.aspx -> Unread messages present
        val locationMyMessage = "/MyCullinan/MyMessage.aspx"
        val isMessagesRedirect1 = locationMyMessage.contains("MyMessage.aspx", ignoreCase = true)
        assertTrue(isMessagesRedirect1)
        val session1 = UserSession(isLoggedIn = true, username = "test", hasUnreadMessages = isMessagesRedirect1)
        assertTrue(session1.hasUnreadMessages)

        // Test 2: Location to Default.aspx -> Normal login, no unread messages
        val locationDefault = "/Master/Default.aspx"
        val isMessagesRedirect2 = locationDefault.contains("MyMessage.aspx", ignoreCase = true)
        assertFalse(isMessagesRedirect2)
        val session2 = UserSession(isLoggedIn = true, username = "test", hasUnreadMessages = isMessagesRedirect2)
        assertFalse(session2.hasUnreadMessages)
    }

    @Test
    fun testUnreadMessagesExceptionClassification() {
        val exception = UnreadMessagesException(unreadCount = 3)
        assertEquals(3, exception.unreadCount)
        assertTrue(exception.message?.contains("پیام خوانده‌نشده") == true)
    }

    @Test
    fun testMessagesTableWithPagerRowDoesNotCreateFakeUnreadMessage() {
        val htmlWithPager = """
            <!DOCTYPE html>
            <html lang="fa-ir">
            <body>
            <div menuidx="1">رزرو و خرید غذا</div>
            <a href="https://jeton.bums.ac.ir/Reservation/Reservation.aspx"><div class="subMenu">رزرو و خرید هفتگی غذا</div></a>
            <form method="post" action="https://jeton.bums.ac.ir/MyCullinan/MyMessage.aspx" id="MainForm">
                <input type="hidden" name="__VIEWSTATE" id="__VIEWSTATE" value="TEST_VS_PAGER" />
                <input type="hidden" name="__VIEWSTATEGENERATOR" id="__VIEWSTATEGENERATOR" value="87AF9C2D" />
                <table id="cphMain_grdContent" class="GridviewStyle">
                    <tbody>
                        <tr class="HeaderStyle">
                            <th>ردیف</th><th>عنوان پیام</th><th>متن پیام</th><th>تاریخ ارسال</th><th>زمان ارسال</th><th>وضعیت</th><th>تاریخ خواندن</th><th>زمان خواندن</th>
                        </tr>
                        <tr>
                            <td>1</td>
                            <td>همراه داشتن کارت دانشجویی</td>
                            <td><input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl02${'$'}imgbtnMessageText" src="../Images/Icon/view.png"></td>
                            <td>1405/08/18</td><td>10:18:58</td>
                            <td><img src="https://jeton.bums.ac.ir/Images/Icon/MsgOpen.png"></td>
                            <td>1405/08/18</td><td>22:44:50</td>
                        </tr>
                        <tr>
                            <td>2</td>
                            <td>اطلاعیه مهم</td>
                            <td><input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl03${'$'}imgbtnMessageText" src="../Images/Icon/view.png"></td>
                            <td>1405/02/05</td><td>08:13:07</td>
                            <td><img src="https://jeton.bums.ac.ir/Images/Icon/MsgOpen.png"></td>
                            <td>1405/03/07</td><td>22:21:37</td>
                        </tr>
                        <tr>
                            <td>3</td>
                            <td>خدمات سامانه تغذیه در پیام رسان ایتا</td>
                            <td><input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl04${'$'}imgbtnMessageText" src="../Images/Icon/view.png"></td>
                            <td>1405/01/07</td><td>19:36:33</td>
                            <td><img src="https://jeton.bums.ac.ir/Images/Icon/MsgOpen.png"></td>
                            <td>1405/03/07</td><td>22:21:33</td>
                        </tr>
                        <tr>
                            <td>4</td>
                            <td>ساعت کاری سلف سرویس دانشگاه در ماه مبارک رمضان</td>
                            <td><input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl05${'$'}imgbtnMessageText" src="../Images/Icon/view.png"></td>
                            <td>1404/11/30</td><td>10:38:36</td>
                            <td><img src="https://jeton.bums.ac.ir/Images/Icon/MsgOpen.png"></td>
                            <td>1405/03/07</td><td>22:21:18</td>
                        </tr>
                        <tr>
                            <td>5</td>
                            <td>اطلاعیه</td>
                            <td><input type="image" name="ctl00${'$'}cphMain${'$'}grdContent${'$'}ctl06${'$'}imgbtnMessageText" src="../Images/Icon/view.png"></td>
                            <td>1404/11/21</td><td>13:22:43</td>
                            <td><img src="https://jeton.bums.ac.ir/Images/Icon/MsgOpen.png"></td>
                            <td>1404/11/22</td><td>10:20:29</td>
                        </tr>
                        <tr class="PagerStyle">
                            <td colspan="8">
                                <table>
                                    <tbody>
                                        <tr>
                                            <td><span>1</span></td>
                                            <td><a href="javascript:__doPostBack('ctl00${'$'}cphMain${'$'}grdContent','Page${'$'}2')">2</a></td>
                                            <td><a href="javascript:__doPostBack('ctl00${'$'}cphMain${'$'}grdContent','Page${'$'}3')">3</a></td>
                                            <td><a href="javascript:__doPostBack('ctl00${'$'}cphMain${'$'}grdContent','Page${'$'}4')">4</a></td>
                                            <td><a href="javascript:__doPostBack('ctl00${'$'}cphMain${'$'}grdContent','Page${'$'}5')">5</a></td>
                                            <td><a href="javascript:__doPostBack('ctl00${'$'}cphMain${'$'}grdContent','Page${'$'}6')">6</a></td>
                                        </tr>
                                    </tbody>
                                </table>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </form>
            </body>
            </html>
        """.trimIndent()

        // 1. Check PageClassifier
        val classified = PageClassifier.classify(htmlWithPager, 200, "https://jeton.bums.ac.ir/MyCullinan/MyMessage.aspx")
        assertEquals(PageClassifier.TYPE_MESSAGES, classified.pageType)
        assertFalse("Must have NO unread messages when all 5 are read", classified.hasUnreadMessages)
        assertEquals("Unread message count must be 0", 0, classified.unreadMessageCount)
        assertTrue("Must detect pagination controls", classified.hasPaginationControls)

        // 2. Check row filtering directly (as done in MessageRepository)
        val doc = Jsoup.parse(htmlWithPager)
        val rows = doc.select("#cphMain_grdContent tr").filter {
            it.select("input[name*='imgbtnMessageText']").isNotEmpty()
        }
        assertEquals("Must parse exactly 5 message rows, excluding header and pager rows", 5, rows.size)

        for (i in 0 until 5) {
            val r = rows[i]
            val btn = r.select("input[name*='imgbtnMessageText']").first()
            assertNotNull(btn)
            assertTrue("Button name must match expected ctl0${i + 2}", btn?.attr("name")?.contains("ctl0${i + 2}") == true)
        }
    }

    @Test
    fun testFallbackMessageContentExtractionFromRawDelta() {
        val rawDelta = "1|#||4|250|updatePanel|ctl00${'$'}UpdatePanel1|<div><p id=\"cphMain_pMessage\">متن تستی استخراج پیام خارج از فرمت عادی</p></div>|20|hiddenField|__VIEWSTATE|NEW_VS|"
        val deltaResponse = DeltaParser.parse(rawDelta)
        val vs = deltaResponse.hiddenFields["__VIEWSTATE"]
        assertEquals("NEW_VS", vs)

        val upHtml = deltaResponse.updatePanels["UpdatePanel1"] ?: deltaResponse.updatePanels["ctl00${'$'}UpdatePanel1"] ?: ""
        val text = Jsoup.parse(upHtml).select("#cphMain_pMessage").text().trim()
        assertEquals("متن تستی استخراج پیام خارج از فرمت عادی", text)
    }
}

