package com.example

import com.example.data.network.CullinanCookieJar
import com.example.data.parser.DeltaParser
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MockWebServerSecurityUnitTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var cookieJar: CullinanCookieJar
    private lateinit var testClient: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        cookieJar = CullinanCookieJar()
        testClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        cookieJar.clear()
    }

    @Test
    fun testSessionCookieClearanceOnLogout() {
        val serverUrl = mockWebServer.url("/Login.aspx")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "ASP.NET_SessionId=secure_session_12345; path=/; HttpOnly; SameSite=Lax")
                .setBody("<html>Login Page</html>")
        )

        val request = Request.Builder().url(serverUrl).build()
        val response = testClient.newCall(request).execute()
        assertTrue(response.isSuccessful)

        val loadedCookiesBefore = cookieJar.loadForRequest(serverUrl)
        assertEquals(1, loadedCookiesBefore.size)
        assertEquals("ASP.NET_SessionId", loadedCookiesBefore[0].name)
        assertEquals("secure_session_12345", loadedCookiesBefore[0].value)

        // Clear session on logout
        cookieJar.clear()

        val loadedCookiesAfter = cookieJar.loadForRequest(serverUrl)
        assertTrue("All cookies must be purged after logout", loadedCookiesAfter.isEmpty())
    }

    @Test
    fun testDeltaParserDetectsSessionExpirationRedirect() {
        val expiredDeltaResponse = "1|#||4|35|pageRedirect||/Login.aspx?error=SessionExpired|"
        val parsed = DeltaParser.parse(expiredDeltaResponse)

        assertTrue("Should detect session expiration via pageRedirect to Login.aspx", parsed.isSessionExpired)
        assertEquals("/Login.aspx?error=SessionExpired", parsed.redirectUrl)
    }

    @Test
    fun testDeltaParserNormalDeltaNotExpired() {
        val normalDeltaResponse = "1|#||4|8|hiddenField|__VIEWSTATEGENERATOR|3ED98A75|18|formAction||./Reservation.aspx|"
        val parsed = DeltaParser.parse(normalDeltaResponse)

        assertFalse("Normal delta update panel must not be flagged as expired", parsed.isSessionExpired)
    }

    @Test
    fun testMockWebServerHandles500InternalServerErrorGracefully() {
        val serverUrl = mockWebServer.url("/Reservation/Reservation.aspx")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("<html><body>Server Error in '/' Application.</body></html>")
        )

        val request = Request.Builder().url(serverUrl).build()
        val response = testClient.newCall(request).execute()

        assertEquals(500, response.code)
        assertFalse(response.isSuccessful)
    }

    @Test
    fun testMockWebServerCaptchaFetchAndBinaryStream() {
        val sampleCaptchaBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) // PNG signature
        val serverUrl = mockWebServer.url("/UserControls/Captcha.ashx?t=12345")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(sampleCaptchaBytes))
        )

        val request = Request.Builder().url(serverUrl).build()
        val response = testClient.newCall(request).execute()

        assertTrue(response.isSuccessful)
        val receivedBytes = response.body?.bytes()
        assertTrue(receivedBytes != null && receivedBytes.contentEquals(sampleCaptchaBytes))
    }

    @Test
    fun testHttpClientCompatibilityModeToggleAndFallbackClient() {
        val initialMode = com.example.data.network.CullinanHttpClient.isCompatibilityModeActive.value
        assertFalse(initialMode)

        com.example.data.network.CullinanHttpClient.setCompatibilityMode(true)
        assertTrue(com.example.data.network.CullinanHttpClient.isCompatibilityModeActive.value)

        // Verify fallback client is initialized properly
        val fallbackClient = com.example.data.network.CullinanHttpClient.fallbackClient
        assertTrue(fallbackClient.followRedirects)
        assertTrue(fallbackClient.followSslRedirects)

        // Reset to default
        com.example.data.network.CullinanHttpClient.setCompatibilityMode(false)
        assertFalse(com.example.data.network.CullinanHttpClient.isCompatibilityModeActive.value)
    }

    @Test
    fun testServerSideLogout302ResponseHandling() {
        val serverUrl = mockWebServer.url("/Reservation/Reservation.aspx")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/Login.aspx")
                .setHeader("Set-Cookie", "ASP.NET_SessionId=; path=/; HttpOnly")
        )

        val request = Request.Builder()
            .url(serverUrl)
            .post(
                okhttp3.FormBody.Builder()
                    .add("__EVENTTARGET", "ctl00\$logout")
                    .add("__VIEWSTATE", "SAMPLE_VIEWSTATE")
                    .build()
            )
            .build()

        val noRedirectClient = testClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val response = noRedirectClient.newCall(request).execute()

        assertEquals(302, response.code)
        val location = response.header("Location") ?: ""
        assertTrue("Location header must redirect to Login.aspx", location.contains("Login.aspx"))

        val recordedRequest = mockWebServer.takeRequest()
        val recordedBody = recordedRequest.body.readUtf8()
        assertTrue(recordedBody.contains("__EVENTTARGET=ctl00%24logout"))
        assertTrue(recordedBody.contains("__VIEWSTATE=SAMPLE_VIEWSTATE"))
    }
}
