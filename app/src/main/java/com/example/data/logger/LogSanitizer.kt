package com.example.data.logger

import java.security.MessageDigest

object LogSanitizer {

    /**
     * Computes a safe 16-hexadecimal character (64-bit truncated SHA-256) fingerprint.
     * Intended use: Diagnostic state comparison ONLY (e.g. verifying whether ViewState changed
     * between postbacks or confirming session cookie continuity). This is NOT a cryptographic
     * proof of authenticity or collision-resistant security token.
     * Raw sensitive values are never logged or exposed.
     */
    fun computeFingerprint(value: String): String {
        if (value.isBlank()) return "EMPTY"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(value.toByteArray(Charsets.UTF_8))
            // Take 8 bytes = 16 hex characters
            digest.take(8).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "FP_ERR"
        }
    }

    /**
     * Sanitizes raw log text, query strings, headers, JSON fragments, and form parameters.
     * Centralized redaction ensures credentials, tokens, session cookies, and student IDs
     * never leak to disk or shareable log outputs.
     */
    fun sanitizeText(raw: String?): String {
        if (raw.isNullOrBlank()) return raw ?: ""

        val base = raw
            // Mask Passwords (=, %3D, :, JSON key-value)
            .replace(Regex("(txtPassword|txtPasswordPlain|savedPassword|password|pass)(?:=|%3D|:\\s*)\"?([^&\\s\",}]+)\"?", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            .replace(Regex("\"(txtPassword|txtPasswordPlain|savedPassword|password|pass)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE), "\"$1\":\"[REDACTED]\"")
            // Mask Usernames & Profile PII keys (National ID / Student ID / Mobile / Email / Card / Bank) across =, %3D, :, and JSON formats
            .replace(Regex("(txtUsername|txtUsernamePlain|savedUsername|username|studentNumber|studentId|nationalId|nationalCode|idNumber|mobile|email|bankAccount|cardNumber|barcodeNumber|fullName|studentName)(?:=|%3D|:\\s*)\"?([^&\\s\",}]+)\"?", RegexOption.IGNORE_CASE), "$1=[REDACTED_USER]")
            .replace(Regex("\"(txtUsername|txtUsernamePlain|savedUsername|username|studentNumber|studentId|nationalId|nationalCode|idNumber|mobile|email|bankAccount|cardNumber|barcodeNumber|fullName|studentName)\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE), "\"$1\":\"[REDACTED_USER]\"")
            // Mask free-text user login lines like "User logged in: 4000..." or "User 9912345"
            .replace(Regex("(User\\s+logged\\s+in:\\s*)(\\S+)", RegexOption.IGNORE_CASE), "$1[REDACTED_USER]")
            // Mask raw Iranian mobile numbers (09xxxxxxxxx) and email addresses
            .replace(Regex("\\b09\\d{9}\\b"), "[REDACTED_MOBILE]")
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[REDACTED_EMAIL]")
            // Mask Captcha values
            .replace(Regex("(txtCaptcha|captcha|captchaInput)(?:=|%3D|:\\s*)\"?([^&\\s\",}]+)\"?", RegexOption.IGNORE_CASE), "$1=[REDACTED_CAPTCHA]")
            // Mask ASP.NET Session Cookie
            .replace(Regex("ASP\\.NET_SessionId(?:=|%3D)([^;,\\s&]+)", RegexOption.IGNORE_CASE), "ASP.NET_SessionId=***")
            // Mask Anti-CSRF Token
            .replace(Regex("__AntiXsrfToken(?:=|%3D)([^;,\\s&]+)", RegexOption.IGNORE_CASE), "__AntiXsrfToken=***")
            // Mask keyId Cookie
            .replace(Regex("keyId(?:=|%3D)([^;,\\s&]+)", RegexOption.IGNORE_CASE), "keyId=***")
            // Mask accordianActiveIndex Cookie
            .replace(Regex("accordianActiveIndex(?:=|%3D)([^;,\\s&]+)", RegexOption.IGNORE_CASE), "accordianActiveIndex=***")
            // Mask Security & Auth Tokens / Tickets in query strings (negative lookbehind ensures ASP.NET_SessionId is untouched)
            .replace(Regex("(?<![a-zA-Z0-9_.])(token|ticket|auth|code|secret|sessionId)(?:=|%3D)([^&;\\s]*)", RegexOption.IGNORE_CASE), "$1=[REDACTED_TOKEN]")
            // Mask Bearer & Basic auth tokens
            .replace(Regex("Authorization[:=]\\s*(Bearer|Basic)\\s+[^\\s]+", RegexOption.IGNORE_CASE), "Authorization=[REDACTED_AUTH]")
            // Truncate Public Key PEM in form body
            .replace(Regex("(publicKeytxt=)([^&]*)", RegexOption.IGNORE_CASE), "$1[PEM_KEY_TRUNCATED]")

        // Truncate huge ViewState & EventValidation blobs to keep logs clean while preserving size metric
        val withViewState = Regex("(__VIEWSTATE\\d*=)([^&]{40,})").replace(base) { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            "${key}[VIEWSTATE_LEN_${value.length}_FP_${computeFingerprint(value)}]"
        }

        return Regex("(__EVENTVALIDATION=)([^&]{40,})").replace(withViewState) { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            "${key}[EVENTVALIDATION_LEN_${value.length}_FP_${computeFingerprint(value)}]"
        }
    }

    /**
     * Deep-sanitizes every field of a LogEntry to guarantee zero sensitive credential exposure.
     */
    fun sanitizeEntry(entry: LogEntry): LogEntry {
        return entry.copy(
            message = sanitizeText(entry.message),
            operation = sanitizeText(entry.operation),
            component = sanitizeText(entry.component),
            errorCategory = entry.errorCategory?.let { sanitizeText(it) },
            exceptionMessage = entry.exceptionMessage?.let { sanitizeText(it) },
            stackTrace = entry.stackTrace?.let { sanitizeText(it) },
            http = entry.http?.copy(
                url = sanitizeText(entry.http.url),
                finalUrl = entry.http.finalUrl?.let { sanitizeText(it) },
                redirectLocation = entry.http.redirectLocation?.let { sanitizeText(it) }
            ),
            aspnet = entry.aspnet?.copy(
                eventTarget = entry.aspnet.eventTarget?.let { sanitizeText(it) }
            ),
            notice = entry.notice?.copy(
                actionUrl = entry.notice.actionUrl?.let { sanitizeText(it) },
                noticeType = entry.notice.noticeType?.let { sanitizeText(it) }
            )
        )
    }

    fun sanitizeHeaders(headersMap: Map<String, String>): Map<String, String> {
        return headersMap.mapValues { (key, value) ->
            if (key.equals("Cookie", ignoreCase = true) || key.equals("Set-Cookie", ignoreCase = true)) {
                value
                    .replace(Regex("ASP\\.NET_SessionId=([^;,\\s]+)", RegexOption.IGNORE_CASE), "ASP.NET_SessionId=***")
                    .replace(Regex("__AntiXsrfToken=([^;,\\s]+)", RegexOption.IGNORE_CASE), "__AntiXsrfToken=***")
                    .replace(Regex("keyId=([^;,\\s]+)", RegexOption.IGNORE_CASE), "keyId=***")
            } else if (key.equals("Authorization", ignoreCase = true)) {
                "[REDACTED_AUTH]"
            } else {
                value
            }
        }
    }
}
