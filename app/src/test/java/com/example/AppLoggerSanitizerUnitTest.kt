package com.example

import com.example.data.logger.AppLogger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerSanitizerUnitTest {

    @Test
    fun testPasswordSanitization() {
        val rawBody = "txtUsername=4031000000&txtPassword=SecretPassword123&txtCaptcha=1234&btnLogin=ورود"
        val sanitized = AppLogger.sanitize(rawBody)

        assertFalse("Raw password must never appear in sanitized output", sanitized.contains("SecretPassword123"))
        assertTrue("Password must be replaced with [REDACTED]", sanitized.contains("txtPassword=[REDACTED]"))
        assertTrue("Username must be masked", sanitized.contains("txtUsername=[REDACTED_USER]"))
    }

    @Test
    fun testSessionCookieSanitization() {
        val rawHeader = "ASP.NET_SessionId=abcdef123456789; path=/; HttpOnly"
        val sanitized = AppLogger.sanitize(rawHeader)

        assertFalse("Session ID must never appear in plain text", sanitized.contains("abcdef123456789"))
        assertTrue("Session ID must be masked with ***", sanitized.contains("ASP.NET_SessionId=***"))
    }

    @Test
    fun testViewStateTruncationForCleanLogs() {
        val hugeViewState = "__VIEWSTATE=" + "A".repeat(200) + "&__VIEWSTATEGENERATOR=C2EE9ABB"
        val sanitized = AppLogger.sanitize(hugeViewState)

        assertFalse("Giant viewstate blob must not bloat logs", sanitized.contains("A".repeat(50)))
        assertTrue("Must include length indicator", sanitized.contains("VIEWSTATE_LENGTH_200"))
    }
}
