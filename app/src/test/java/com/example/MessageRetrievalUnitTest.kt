package com.example

import com.example.data.model.MessageError
import com.example.data.model.MessageItem
import com.example.data.model.MessageOpenResult
import com.example.data.model.MessagesPageState
import com.example.data.model.WebFormsState
import com.example.data.parser.DeltaParser
import com.example.data.repository.MessageRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MessageRetrievalUnitTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: MessageRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        com.example.data.network.CullinanHttpClient.setCustomBaseUrl(mockWebServer.url("/").toString().trimEnd('/'))
        repository = MessageRepository()
    }

    @After
    fun tearDown() {
        com.example.data.network.CullinanHttpClient.resetCustomBaseUrl()
        mockWebServer.shutdown()
    }

    /**
     * Scenario 1: Exact ScriptManager field value and UpdatePanel identifier.
     */
    @Test
    fun testExactScriptManagerValueAndUpdatePanelIdentifier() = runBlocking {
        val deltaResponse = "1|#||4|250|updatePanel|ctl00${'$'}UpdatePanel1|<div><p id=\"cphMain_pMessage\">متن پیام تستی</p></div>|20|hiddenField|__VIEWSTATE|NEW_VS|"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(deltaResponse))

        val buttonId = "ctl00\$cphMain\$grdContent\$ctl02\$imgbtnMessageText"
        val state = WebFormsState(viewState = "INITIAL_VS", viewStateGenerator = "87AF9C2D", eventValidation = "INITIAL_EV")

        val result = repository.openMessage(
            buttonInputName = buttonId,
            state = state,
            scriptManagerName = "ctl00\$ScriptManager",
            updatePanelName = "ctl00\$UpdatePanel1"
        )

        assertTrue(result is MessageOpenResult.Success)
        val recordedRequest = mockWebServer.takeRequest()
        val body = URLDecoder.decode(recordedRequest.body.readUtf8(), StandardCharsets.UTF_8.name())

        assertTrue(body.contains("ctl00\$ScriptManager=ctl00\$UpdatePanel1|$buttonId"))
        assertTrue(body.contains("__ASYNCPOST=true"))
    }

    /**
     * Scenario 2: Correct image-button POST parameters (.x and .y coordinates).
     */
    @Test
    fun testCorrectImageButtonPostParameters() = runBlocking {
        val deltaResponse = "1|#||4|250|updatePanel|ctl00${'$'}UpdatePanel1|<div><p id=\"cphMain_pMessage\">تست پارامتر مختصات</p></div>|20|hiddenField|__VIEWSTATE|NEW_VS|"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(deltaResponse))

        val buttonId = "ctl00\$cphMain\$grdContent\$ctl03\$imgbtnMessageText"
        repository.openMessage(buttonInputName = buttonId, state = WebFormsState("VS_TEST"))

        val recordedRequest = mockWebServer.takeRequest()
        val body = URLDecoder.decode(recordedRequest.body.readUtf8(), StandardCharsets.UTF_8.name())

        assertTrue("Must include .x coordinate for image button", body.contains("$buttonId.x=10"))
        assertTrue("Must include .y coordinate for image button", body.contains("$buttonId.y=10"))
    }

    /**
     * Scenario 3: Extraction of message text from a representative captured Delta response.
     */
    @Test
    fun testExtractionOfMessageTextFromDeltaResponse() = runBlocking {
        val expectedMessage = "دانشجوی گرامی، به دلیل تعمیرات سلف سرویس مرکزی، وعده شام در سلف الزهرا توزیع می‌شود."
        val deltaResponse = "1|#||4|350|updatePanel|ctl00${'$'}UpdatePanel1|<table class=\"tableStyle\"><tr><td><p id=\"cphMain_pMessage\">$expectedMessage</p></td></tr></table>|30|hiddenField|__VIEWSTATE|UPDATED_VS_987|30|hiddenField|__EVENTVALIDATION|UPDATED_EV_654|"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(deltaResponse))

        val result = repository.openMessage(
            buttonInputName = "ctl00\$cphMain\$grdContent\$ctl02\$imgbtnMessageText",
            state = WebFormsState("VS_1")
        )

        assertTrue(result is MessageOpenResult.Success)
        val success = result as MessageOpenResult.Success
        assertEquals(expectedMessage, success.messageText)
        assertEquals("UPDATED_VS_987", success.updatedState.viewState)
        assertEquals("UPDATED_EV_654", success.updatedState.eventValidation)
    }

    /**
     * Scenario 4: Extraction and chaining of all updated hidden fields (ViewState, Generator, EventValidation).
     */
    @Test
    fun testExtractionAndChainingOfAllUpdatedHiddenFields() {
        val deltaPayload = "1|#||4|200|updatePanel|UpdatePanel1|<div><p id=\"cphMain_pMessage\">پیام</p></div>|25|hiddenField|__VIEWSTATE|VS_CHAIN_123|20|hiddenField|__VIEWSTATEGENERATOR|ABCD1234|30|hiddenField|__EVENTVALIDATION|EV_CHAIN_456|"
        val parsed = DeltaParser.parse(deltaPayload)

        assertEquals("VS_CHAIN_123", parsed.hiddenFields["__VIEWSTATE"])
        assertEquals("ABCD1234", parsed.hiddenFields["__VIEWSTATEGENERATOR"])
        assertEquals("EV_CHAIN_456", parsed.hiddenFields["__EVENTVALIDATION"])

        val prevState = WebFormsState("VS_OLD", "GEN_OLD", "EV_OLD")
        val chained = DeltaParser.extractWebFormsState(parsed, prevState)

        assertEquals("VS_CHAIN_123", chained.viewState)
        assertEquals("ABCD1234", chained.viewStateGenerator)
        assertEquals("EV_CHAIN_456", chained.eventValidation)
    }

    /**
     * Scenario 5: Missing UpdatePanel error handling.
     */
    @Test
    fun testMissingUpdatePanelReturnsExplicitError() = runBlocking {
        // Delta response with only a hiddenField and no updatePanel
        val deltaWithoutPanel = "1|#||4|25|hiddenField|__VIEWSTATE|NEW_VS|"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(deltaWithoutPanel))

        val result = repository.openMessage(
            buttonInputName = "ctl00\$cphMain\$grdContent\$ctl02\$imgbtnMessageText",
            state = WebFormsState("VS_1")
        )

        assertTrue("Result must be failure when no UpdatePanel is returned", result is MessageOpenResult.Failure)
        val failure = result as MessageOpenResult.Failure
        assertEquals(MessageError.MissingUpdatePanel, failure.error)
    }

    /**
     * Scenario 6: Missing or empty message element error handling.
     */
    @Test
    fun testMissingOrEmptyMessageElementReturnsExplicitError() = runBlocking {
        // UpdatePanel exists but p#cphMain_pMessage is empty or absent
        val deltaWithEmptyPanel = "1|#||4|100|updatePanel|ctl00${'$'}UpdatePanel1|<div><p id=\"cphMain_pMessage\">   </p></div>|20|hiddenField|__VIEWSTATE|NEW_VS|"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(deltaWithEmptyPanel))

        val result = repository.openMessage(
            buttonInputName = "ctl00\$cphMain\$grdContent\$ctl02\$imgbtnMessageText",
            state = WebFormsState("VS_1")
        )

        assertTrue("Result must be failure when message element is empty", result is MessageOpenResult.Failure)
        val failure = result as MessageOpenResult.Failure
        assertEquals(MessageError.EmptyMessageContent, failure.error)
    }

    /**
     * Scenario 7: Full HTML response instead of Delta response.
     */
    @Test
    fun testFullHtmlResponseInsteadOfDeltaReturnsInvalidDeltaError() = runBlocking {
        val serverErrorHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>500 Internal Server Error</title></head>
            <body><h1>Server Error in '/MyCullinan' Application.</h1></body>
            </html>
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(serverErrorHtml))

        val result = repository.openMessage(
            buttonInputName = "ctl00\$cphMain\$grdContent\$ctl02\$imgbtnMessageText",
            state = WebFormsState("VS_1")
        )

        assertTrue(result is MessageOpenResult.Failure)
        val failure = result as MessageOpenResult.Failure
        assertEquals(MessageError.InvalidDeltaResponse, failure.error)
    }

    /**
     * Scenario 8: Session expiration response detection.
     */
    @Test
    fun testSessionExpirationDetection() = runBlocking {
        val redirectDelta = "1|#||4|50|pageRedirect||/MyCullinan/Login.aspx?expired=true|"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(redirectDelta))

        val result = repository.openMessage(
            buttonInputName = "ctl00\$cphMain\$grdContent\$ctl02\$imgbtnMessageText",
            state = WebFormsState("VS_1")
        )

        assertTrue(result is MessageOpenResult.Failure)
        val failure = result as MessageOpenResult.Failure
        assertEquals(MessageError.SessionExpired, failure.error)
    }

    /**
     * Scenario 9: Sequential opening of multiple messages with chained state.
     */
    @Test
    fun testSequentialOpeningOfMultipleMessagesWithChainedState() = runBlocking {
        val delta1 = "1|#||4|250|updatePanel|ctl00${'$'}UpdatePanel1|<div><p id=\"cphMain_pMessage\">متن پیام اول</p></div>|20|hiddenField|__VIEWSTATE|VS_STEP_1|20|hiddenField|__EVENTVALIDATION|EV_STEP_1|"
        val delta2 = "1|#||4|250|updatePanel|ctl00${'$'}UpdatePanel1|<div><p id=\"cphMain_pMessage\">متن پیام دوم</p></div>|20|hiddenField|__VIEWSTATE|VS_STEP_2|20|hiddenField|__EVENTVALIDATION|EV_STEP_2|"

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(delta1))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(delta2))

        val initialState = WebFormsState("VS_INIT", "87AF9C2D", "EV_INIT")

        val res1 = repository.openMessage("btn1", initialState)
        assertTrue(res1 is MessageOpenResult.Success)
        val stateAfter1 = (res1 as MessageOpenResult.Success).updatedState
        assertEquals("VS_STEP_1", stateAfter1.viewState)
        assertEquals("EV_STEP_1", stateAfter1.eventValidation)

        val res2 = repository.openMessage("btn2", stateAfter1)
        assertTrue(res2 is MessageOpenResult.Success)
        val stateAfter2 = (res2 as MessageOpenResult.Success).updatedState
        assertEquals("VS_STEP_2", stateAfter2.viewState)
        assertEquals("EV_STEP_2", stateAfter2.eventValidation)

        // Inspect second request from server perspective: must contain VS_STEP_1 and EV_STEP_1
        mockWebServer.takeRequest() // skip first request
        val req2 = mockWebServer.takeRequest()
        val req2Body = URLDecoder.decode(req2.body.readUtf8(), StandardCharsets.UTF_8.name())
        assertTrue("Request 2 must use ViewState from Response 1", req2Body.contains("__VIEWSTATE=VS_STEP_1"))
        assertTrue("Request 2 must use EventValidation from Response 1", req2Body.contains("__EVENTVALIDATION=EV_STEP_1"))
    }

    /**
     * Scenario 10: Prevention of concurrent requests using the same stale state.
     */
    @Test
    fun testSerializedProcessProtectsAgainstConcurrentStaleState() = runBlocking {
        val initialMessages = listOf(
            MessageItem(1, "پیام یک", "btn1", "1405/08/18", "10:00", false, "", ""),
            MessageItem(2, "پیام دو", "btn2", "1405/08/18", "10:05", false, "", "")
        )
        val initialPageState = MessagesPageState(
            viewState = "VS_SERIAL_INIT",
            eventValidation = "EV_SERIAL_INIT",
            messages = initialMessages,
            hasUnreadMessages = true,
            unreadCount = 2
        )

        // Enqueue responses for serialized processing:
        // 1. openMessage(btn1)
        val delta1 = "1|#||4|250|updatePanel|ctl00${'$'}UpdatePanel1|<div><p id=\"cphMain_pMessage\">متن پیام اول</p></div>|20|hiddenField|__VIEWSTATE|VS_POST_1|20|hiddenField|__EVENTVALIDATION|EV_POST_1|"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(delta1))

        // 2. openMessage(btn2)
        val delta2 = "1|#||4|250|updatePanel|ctl00${'$'}UpdatePanel1|<div><p id=\"cphMain_pMessage\">متن پیام دوم</p></div>|20|hiddenField|__VIEWSTATE|VS_POST_2|20|hiddenField|__EVENTVALIDATION|EV_POST_2|"
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(delta2))

        // 3. Final verification GET
        val verifyHtml = """
            <html><body>
            <a href="../Reservation/Reservation.aspx">رزرو غذا</a>
            <input type="hidden" name="__VIEWSTATE" value="FINAL_VS" />
            <table id="cphMain_grdContent"></table>
            </body></html>
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(verifyHtml))

        val result = repository.markAllUnreadAsReadSerialized(initialPageState) { _, _, _, _ -> }
        assertTrue(result.isSuccess)

        val req1 = mockWebServer.takeRequest()
        val body1 = URLDecoder.decode(req1.body.readUtf8(), StandardCharsets.UTF_8.name())
        assertTrue(body1.contains("__VIEWSTATE=VS_SERIAL_INIT"))

        val req2 = mockWebServer.takeRequest()
        val body2 = URLDecoder.decode(req2.body.readUtf8(), StandardCharsets.UTF_8.name())
        assertTrue("Message 2 request must strictly use updated ViewState from message 1", body2.contains("__VIEWSTATE=VS_POST_1"))
        assertTrue("Message 2 request must strictly use updated EventValidation from message 1", body2.contains("__EVENTVALIDATION=EV_POST_1"))
    }
}
