package com.example

import com.example.data.logger.AppLogger
import com.example.data.logger.LogContext
import com.example.data.network.CullinanCookieJar
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.PageClassifier
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
class LoggingIntegrationTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        AppLogger.currentMinLogLevel = com.example.data.logger.LogLevel.DEBUG
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testLogContextEndToEndCorrelation() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("<html><body>OK</body></html>")
        )

        val flowId = AppLogger.generateFlowId("test")
        val url = server.url("/test").toString()

        val request = Request.Builder()
            .url(url)
            .get()
            .tag(LogContext::class.java, LogContext(flowId, "TestOperation"))
            .build()

        val response = CullinanHttpClient.executeCall(request)
        assertEquals(200, response.code)

        // Verify recent memory logs capture operation with flowId
        val logs = AppLogger.recentLogs.value
        val hasCorrelatedEntry = logs.any { it.contains(flowId) && it.contains("TestOperation") }
        assertTrue("Log should capture the correlated flowId and operation name", hasCorrelatedEntry)
    }

    @Test
    fun testUntaggedRequestFallbackGraceful() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("<html><body>Fallback OK</body></html>")
        )

        val url = server.url("/fallback").toString()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = CullinanHttpClient.executeCall(request)
        assertEquals(200, response.code)

        // Untagged request must execute normally without error
        assertNotNull(response.body)
    }

    @Test
    fun testNoticePagePreClassificationPreventsEmptyMenu() {
        val noticeBody = """
            <html>
                <body>
                    <div class="noticeBox">
                        <span id="cphMain_lblNotice">اطلاعیه مهم: لطفاً پرونده تغذیه خود را تکمیل کنید</span>
                        <input type="submit" id="btnAcceptNotice" name="btnAcceptNotice" value="تایید" />
                    </div>
                </body>
            </html>
        """.trimIndent()

        val classification = PageClassifier.classify(noticeBody, 200, server.url("/Reservation.aspx").toString())
        assertEquals(PageClassifier.TYPE_NOTICE, classification.pageType)
        assertFalse(classification.hasReservationGrid)
    }

    @Test
    fun testCookieJarLoadDiagnosticsAndExpiration() {
        val cookieJar = CullinanCookieJar()
        val httpUrl = "https://jeton.bums.ac.ir/Reservation/Reservation.aspx".toHttpUrl()

        val validCookie = Cookie.Builder()
            .domain("jeton.bums.ac.ir")
            .path("/")
            .name("ASP.NET_SessionId")
            .value("session_secret_token_12345")
            .expiresAt(System.currentTimeMillis() + 60000)
            .build()

        val expiredCookie = Cookie.Builder()
            .domain("jeton.bums.ac.ir")
            .path("/")
            .name("oldCookie")
            .value("old_val")
            .expiresAt(System.currentTimeMillis() - 60000)
            .build()

        cookieJar.saveFromResponse(httpUrl, listOf(validCookie, expiredCookie))

        val loaded = cookieJar.loadForRequest(httpUrl)
        assertEquals(1, loaded.size)
        assertEquals("ASP.NET_SessionId", loaded[0].name)

        // Ensure raw secret is never present in log memory
        val logs = AppLogger.recentLogs.value
        val hasRawSecret = logs.any { it.contains("session_secret_token_12345") }
        assertFalse("Raw session secret must never appear in cookie diagnostic logs", hasRawSecret)
    }

    @Test
    fun testZeroSecretsInFinalLogExport() {
        val text = AppLogger.getExportableText()
        val jsonl = AppLogger.getExportableJsonl()

        assertFalse("Export text must not contain raw passwords", text.contains("SuperSecret123"))
        assertFalse("Export JSONL must not contain raw passwords", jsonl.contains("SuperSecret123"))
        assertFalse("Export text must not contain raw session secrets", text.contains("session_secret_token_12345"))
        assertFalse("Export JSONL must not contain raw session secrets", jsonl.contains("session_secret_token_12345"))
    }
}
