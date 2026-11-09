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
     * Sanitizes raw log text, query strings, headers, and form parameters.
     * Centralized redaction ensures credentials, tokens, session cookies, and student IDs
     * never leak to disk or log outputs.
     */
    fun sanitizeText(raw: String?): String {
        if (raw.isNullOrBlank()) return raw ?: ""

        val base = raw
            // Mask Passwords
            .replace(Regex("(txtPassword|txtPasswordPlain|password|pass)=([^&\\s]*)", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            // Mask Usernames (National ID / Student ID)
            .replace(Regex("(txtUsername|txtUsernamePlain|username|studentNumber|nationalId)=([^&\\s]*)", RegexOption.IGNORE_CASE), "$1=[REDACTED_USER]")
            // Mask Captcha values
            .replace(Regex("(txtCaptcha|captcha|captchaInput)=([^&\\s]*)", RegexOption.IGNORE_CASE), "$1=[REDACTED_CAPTCHA]")
            // Mask ASP.NET Session Cookie
            .replace(Regex("ASP\\.NET_SessionId=([^;,\\s&]+)", RegexOption.IGNORE_CASE), "ASP.NET_SessionId=***")
            // Mask Anti-CSRF Token
            .replace(Regex("__AntiXsrfToken=([^;,\\s&]+)", RegexOption.IGNORE_CASE), "__AntiXsrfToken=***")
            // Mask keyId Cookie
            .replace(Regex("keyId=([^;,\\s&]+)", RegexOption.IGNORE_CASE), "keyId=***")
            // Mask accordianActiveIndex Cookie
            .replace(Regex("accordianActiveIndex=([^;,\\s&]+)", RegexOption.IGNORE_CASE), "accordianActiveIndex=***")
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
            http = entry.http?.copy(
                url = sanitizeText(entry.http.url),
                finalUrl = entry.http.finalUrl?.let { sanitizeText(it) }
            ),
            aspnet = entry.aspnet?.copy(
                eventTarget = entry.aspnet.eventTarget?.let { sanitizeText(it) }
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
