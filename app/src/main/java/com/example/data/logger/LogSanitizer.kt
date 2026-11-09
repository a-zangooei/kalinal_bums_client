package com.example.data.logger

import java.security.MessageDigest

object LogSanitizer {

    /**
     * Sanitizes raw log text, query strings, and form parameters.
     * Centralized redaction ensures secrets never leak to disk or logs.
     */
    fun sanitizeText(raw: String): String {
        if (raw.isBlank()) return raw

        val base = raw
            // Mask Passwords
            .replace(Regex("(txtPassword|txtPasswordPlain|password)=([^&\\s]*)", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            // Mask Usernames (National ID / Student ID)
            .replace(Regex("(txtUsername|txtUsernamePlain|username)=([^&\\s]*)", RegexOption.IGNORE_CASE), "$1=[REDACTED_USER]")
            // Mask ASP.NET Session Cookie
            .replace(Regex("ASP\\.NET_SessionId=([^;,\\s&]+)", RegexOption.IGNORE_CASE), "ASP.NET_SessionId=***")
            // Truncate Public Key PEM in form body
            .replace(Regex("(publicKeytxt=)([^&]*)", RegexOption.IGNORE_CASE), "$1[PEM_KEY_TRUNCATED]")

        // Truncate huge ViewState blobs to keep logs clean while preserving size metric
        return Regex("(__VIEWSTATE=)([^&]{40,})").replace(base) { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            "${key}[VIEWSTATE_LEN_${value.length}_FP_${computeFingerprint(value)}]"
        }
    }

    /**
     * Computes a safe 8-character cryptographic SHA-256 fingerprint
     * for state equality checking without exposing raw values.
     */
    fun computeFingerprint(value: String): String {
        if (value.isBlank()) return "EMPTY"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(value.toByteArray(Charsets.UTF_8))
            digest.take(4).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "FP_ERR"
        }
    }

    fun sanitizeHeaders(headersMap: Map<String, String>): Map<String, String> {
        return headersMap.mapValues { (key, value) ->
            if (key.equals("Cookie", ignoreCase = true) || key.equals("Set-Cookie", ignoreCase = true)) {
                value.replace(Regex("ASP\\.NET_SessionId=([^;,\\s]+)", RegexOption.IGNORE_CASE), "ASP.NET_SessionId=***")
            } else {
                value
            }
        }
    }
}
