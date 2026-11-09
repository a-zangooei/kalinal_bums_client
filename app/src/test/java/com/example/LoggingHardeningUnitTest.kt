package com.example

import com.example.data.logger.AppLogger
import com.example.data.logger.AspNetDiagnostics
import com.example.data.logger.CookieDiagnostics
import com.example.data.logger.HttpDiagnostics
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
import com.example.data.logger.NoticeDiagnostics
import com.example.data.logger.ParserDiagnostics
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
class LoggingHardeningUnitTest {

    @Test
    fun testUtcTimestampFormatAndPrecision() {
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "Test",
                operation = "TimestampCheck",
                message = "Checking UTC"
            )
        )
        val logs = AppLogger.recentLogs.value
        val lastLog = logs.lastOrNull()
        assertNotNull(lastLog)
        assertTrue("Log timestamp must match ISO-8601 UTC with Z suffix", lastLog!!.contains("Z]"))
    }

    @Test
    fun testDeepSanitizationZeroSecretLeakage() {
        val sensitiveMsg = "User 9912345 login with txtPassword=SuperSecret123&txtCaptcha=4821 and ASP.NET_SessionId=abcdef123456"
        val sanitized = LogSanitizer.sanitizeText(sensitiveMsg)

        assertFalse("Password must be masked", sanitized.contains("SuperSecret123"))
        assertFalse("Captcha must be masked", sanitized.contains("4821"))
        assertFalse("Session ID must be masked", sanitized.contains("abcdef123456"))
        assertTrue("Password redacted marker present", sanitized.contains("[REDACTED]"))
        assertTrue("Captcha redacted marker present", sanitized.contains("[REDACTED_CAPTCHA]"))
        assertTrue("Session masked marker present", sanitized.contains("ASP.NET_SessionId=***"))
    }

    @Test
    fun testDeepSanitizationEntryAllFields() {
        val entry = LogEntry(
            timestamp = "2026-11-09T08:30:00.000Z",
            level = LogLevel.ERROR,
            component = "AuthService",
            operation = "LoginAttempt",
            message = "Attempt for studentNumber=9912345",
            errorCategory = "AuthFailed_pass=TopSecret999",
            exceptionMessage = "Database error for username=admin99 pass=SecretP@ss token=secret_token_abc",
            http = HttpDiagnostics(
                method = "POST",
                url = "https://jeton.bums.ac.ir/Login.aspx?txtUsername=9912345&txtPassword=MyPass&ticket=ticket_secret_xyz",
                finalUrl = "https://jeton.bums.ac.ir/Login.aspx?token=123"
            ),
            notice = NoticeDiagnostics(
                isNoticeDetected = true,
                noticeType = "NOTICE_TYPE",
                actionUrl = "https://jeton.bums.ac.ir/Accept.aspx?sessionId=session_12345&studentNumber=9912345"
            )
        )

        val sanitized = LogSanitizer.sanitizeEntry(entry)
        assertFalse("Message username must be masked", sanitized.message.contains("9912345"))
        assertFalse("ErrorCategory pass must be masked", sanitized.errorCategory!!.contains("TopSecret999"))
        assertFalse("Exception pass must be masked", sanitized.exceptionMessage!!.contains("SecretP@ss"))
        assertFalse("Exception token must be masked", sanitized.exceptionMessage!!.contains("secret_token_abc"))
        assertFalse("HTTP URL pass must be masked", sanitized.http!!.url.contains("MyPass"))
        assertFalse("HTTP URL ticket must be masked", sanitized.http!!.url.contains("ticket_secret_xyz"))
        assertFalse("Notice actionUrl sessionId must be masked", sanitized.notice!!.actionUrl!!.contains("session_12345"))
        assertFalse("Notice actionUrl studentNumber must be masked", sanitized.notice!!.actionUrl!!.contains("9912345"))
        assertTrue("Notice actionUrl redacted token marker", sanitized.notice!!.actionUrl!!.contains("[REDACTED_TOKEN]"))
        assertTrue("Notice actionUrl redacted user marker", sanitized.notice!!.actionUrl!!.contains("[REDACTED_USER]"))
    }

    @Test
    fun testJsonlSerializationAndControlCharacters() {
        val trickyMessage = "Line 1\nLine 2\tTabbed \"Quoted text\" \\Backslash\\ and \r Carriage and فارسی"
        val entry = LogEntry(
            timestamp = "2026-11-09T08:00:00.000Z",
            level = LogLevel.WARN,
            component = "TestComp",
            operation = "JsonlControlChars",
            flowId = "flow_12345",
            requestId = "req_67890",
            durationMs = 245,
            errorCategory = "PARSER_FAILURE",
            http = HttpDiagnostics(
                method = "POST",
                url = "https://jeton.bums.ac.ir/Reservation.aspx",
                statusCode = 200,
                isCompatibilitySsl = true
            ),
            aspnet = AspNetDiagnostics(
                viewStateLength = 1500,
                viewStateFingerprint = "abcd1234ef567890",
                hasEventValidation = true,
                eventTarget = "ctl00\$cphMain\$btnSave"
            ),
            cookies = CookieDiagnostics(
                action = "SAVE_RESPONSE",
                cookieCount = 3,
                hasSessionCookie = true,
                sessionFingerprint = "1234567890abcdef"
            ),
            parser = ParserDiagnostics(
                pageType = "RESERVATION",
                htmlLength = 45000,
                tablesFound = 3,
                parsedMealsCount = 14
            ),
            notice = NoticeDiagnostics(
                isNoticeDetected = false,
                isAcknowledged = false
            ),
            message = trickyMessage
        )

        val jsonl = AppLogger.buildJsonlLine(entry)
        assertFalse("Must be a single line without raw unescaped newlines", jsonl.contains("\r\n"))

        // Real JSONObject parser validation
        val json = JSONObject(jsonl)
        assertEquals("TestComp", json.getString("comp"))
        assertEquals("JsonlControlChars", json.getString("op"))
        assertEquals("flow_12345", json.getString("flowId"))
        assertEquals("req_67890", json.getString("reqId"))
        assertEquals(245, json.getInt("durMs"))
        assertEquals(trickyMessage, json.getString("msg"))

        // Verify nested HTTP object
        val httpObj = json.getJSONObject("http")
        assertEquals("POST", httpObj.getString("method"))
        assertEquals("https://jeton.bums.ac.ir/Reservation.aspx", httpObj.getString("url"))
        assertEquals(200, httpObj.getInt("status"))
        assertTrue(httpObj.getBoolean("sslFallback"))

        // Verify nested ASP.NET object
        val aspObj = json.getJSONObject("aspnet")
        assertEquals(1500, aspObj.getInt("vsLen"))
        assertEquals("abcd1234ef567890", aspObj.getString("vsFp"))
        assertTrue(aspObj.getBoolean("hasEv"))
        assertEquals("ctl00\$cphMain\$btnSave", aspObj.getString("target"))

        // Verify nested cookies object
        val cookiesObj = json.getJSONObject("cookies")
        assertEquals("SAVE_RESPONSE", cookiesObj.getString("act"))
        assertEquals(3, cookiesObj.getInt("cnt"))
        assertTrue(cookiesObj.getBoolean("hasSession"))
        assertEquals("1234567890abcdef", cookiesObj.getString("sessionFp"))

        // Verify nested parser object
        val parserObj = json.getJSONObject("parser")
        assertEquals("RESERVATION", parserObj.getString("pageType"))
        assertEquals(45000, parserObj.getInt("htmlLen"))
        assertEquals(14, parserObj.getInt("meals"))

        // Verify nested notice object
        val noticeObj = json.getJSONObject("notice")
        assertFalse(noticeObj.getBoolean("detected"))
        assertFalse(noticeObj.getBoolean("ack"))
    }

    @Test
    fun testDiagnosticFingerprintProperties() {
        val value1 = "ViewStatePayloadContentAlpha"
        val value2 = "ViewStatePayloadContentBeta"

        val fp1 = LogSanitizer.computeFingerprint(value1)
        val fp2 = LogSanitizer.computeFingerprint(value2)

        // 16 hex characters = 8 bytes = 64-bit diagnostic representation
        assertEquals(16, fp1.length)
        assertEquals(16, fp2.length)
        assertFalse("Distinct payloads must produce distinct diagnostic fingerprints", fp1 == fp2)

        // Identical payload produces stable identical diagnostic fingerprint
        val fp1Again = LogSanitizer.computeFingerprint(value1)
        assertEquals(fp1, fp1Again)
    }

    @Test
    fun testNoticeDiagnosticsAndPageClassifier() {
        val htmlNotice = """
            <html>
                <body>
                    <div class="noticeBox">
                        <span id="cphMain_lblNotice">دانشجوی گرامی، لطفاً نسبت به تسویه بدهی خوابگاه اقدام فرمایید.</span>
                        <input type="submit" id="btnAcceptNotice" name="btnAcceptNotice" value="تایید اطلاعیه" />
                    </div>
                </body>
            </html>
        """.trimIndent()

        val classification = com.example.data.parser.PageClassifier.classify(htmlNotice)
        assertEquals(com.example.data.parser.PageClassifier.TYPE_NOTICE, classification.pageType)
        assertTrue(classification.evidence.contains("NOTICE_SELECTORS_FOUND"))
        assertFalse("Notice page must not be flagged as containing reservation grid", classification.hasReservationGrid)
    }

    @Test
    fun testLoginPageClassifierDetection() {
        val htmlLogin = """
            <html>
                <body>
                    <input type="text" name="txtUsernamePlain" />
                    <input type="password" name="txtPasswordPlain" />
                    <input type="submit" name="btnLogin" value="ورود" />
                </body>
            </html>
        """.trimIndent()

        val classification = com.example.data.parser.PageClassifier.classify(htmlLogin)
        assertEquals(com.example.data.parser.PageClassifier.TYPE_LOGIN, classification.pageType)
        assertTrue(classification.isSessionExpired)
    }
}
