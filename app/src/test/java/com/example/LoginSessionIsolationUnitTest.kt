package com.example

import android.app.Application
import com.example.data.network.CullinanHttpClient
import com.example.data.repository.AuthRepository
import com.example.data.repository.MessageRepository
import com.example.ui.viewmodel.MessagesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginSessionIsolationUnitTest {

    companion object {
        private const val TEST_PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3pAPzCnTcX4A3p9w+10PNY5ekRthWl/3mXANELfvo6mAzNlHhHLCKfjcXMuYT0XeaPyirNZjSdjWKyasou0Bb3JqVYsJBxCMzAOLv2fCMuOvYRwGWMqfmfrD25oUzFL2BQO8YTSCz766jm8h7RNeLQsmxObAIevSeSNNSF7A3q2dX6l0hj+TgSV5oewEVKTSu1MRKggXkTq02m33vdeqNhXwzQX1bmj783kUsSq6HP3ko6CzMyMAmszJNuQZ/o0/OxpIUMWRsp4Y2fcRrWtGzVbzjFw9R7RqLYUk4YX0Hl5BZXPj/lOjpVK9Mljc5yZc0nYhV78tk54W9i0fUERhWQIDAQAB
-----END PUBLIC KEY-----"""
    }

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockWebServer = MockWebServer()
        mockWebServer.start()
        CullinanHttpClient.cookieJar.clear()
        CullinanHttpClient.setCustomBaseUrl(mockWebServer.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        CullinanHttpClient.cookieJar.clear()
        CullinanHttpClient.resetCustomBaseUrl()
        mockWebServer.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun testMessagesViewModelDoesNotFetchBeforeLoginOnStartup() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Set-Cookie", "ASP.NET_SessionId=HIJACKED_BY_MESSAGES; path=/")
                .setBody("<html>Redirected to Login</html>")
        )

        val viewModel = MessagesViewModel(Application(), MessageRepository())

        assertFalse(
            "MessagesViewModel must start idle until user logs in",
            viewModel.pageState.value.isLoading
        )
        assertEquals(
            "MessagesViewModel init must NOT send any HTTP request before login",
            0,
            mockWebServer.requestCount
        )
    }

    @Test
    fun testFirstAttemptLoginPreservesSingleCleanSessionWithoutCookieOverwrite() = runBlocking {
        // Seed a stale cookie as if left from an old session
        val baseHttpUrl = mockWebServer.url("/").toString().toHttpUrl()
        CullinanHttpClient.cookieJar.saveFromResponse(
            baseHttpUrl,
            listOf(
                Cookie.Builder()
                    .name("ASP.NET_SessionId")
                    .value("STALE_PREVIOUS_SESSION")
                    .domain(baseHttpUrl.host)
                    .path("/")
                    .build()
            )
        )

        val loginHtml = """
            <html><body>
                <input type="hidden" name="__VIEWSTATE" value="VS_CLEAN_1" />
                <input type="hidden" name="__VIEWSTATEGENERATOR" value="C2EE9ABB" />
                <textarea id="publicKeytxt" name="publicKeytxt">$TEST_PUBLIC_KEY</textarea>
            </body></html>
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Set-Cookie", "ASP.NET_SessionId=CLEAN_SESSION_1; path=/; HttpOnly")
                .setBody(loginHtml)
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/Master/Default.aspx")
        )

        val authRepo = AuthRepository()
        val pageResult = authRepo.fetchLoginPage()
        assertTrue(pageResult.isSuccess)

        val pageState = pageResult.getOrThrow()
        val loginResult = authRepo.login("4031000000", "1234567890", "1234", pageState)
        assertTrue("First login attempt must succeed cleanly", loginResult.isSuccess)

        // 1. GET /Login.aspx must not carry the stale cookie and must send browser User-Agent
        val reqLoginGet = mockWebServer.takeRequest()
        assertEquals(null, reqLoginGet.getHeader("Cookie"))
        assertEquals(CullinanHttpClient.DEFAULT_USER_AGENT, reqLoginGet.getHeader("User-Agent"))

        // 2. GET /UserControls/Captcha.ashx must carry CLEAN_SESSION_1
        val reqCaptcha = mockWebServer.takeRequest()
        assertTrue(reqCaptcha.getHeader("Cookie")?.contains("ASP.NET_SessionId=CLEAN_SESSION_1") == true)

        // 3. POST /Login.aspx must still carry the exact same CLEAN_SESSION_1
        val reqLoginPost = mockWebServer.takeRequest()
        assertTrue(reqLoginPost.getHeader("Cookie")?.contains("ASP.NET_SessionId=CLEAN_SESSION_1") == true)
        assertEquals(CullinanHttpClient.DEFAULT_USER_AGENT, reqLoginPost.getHeader("User-Agent"))
    }
}
