package com.example.data.network

import com.example.data.logger.AppLogger
import com.example.data.logger.CookieDiagnostics
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class CullinanCookieJar : CookieJar {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val currentCookies = cookieStore.getOrPut(host) { mutableListOf() }
        var sessionFp: String? = null
        var hasSession = false

        synchronized(currentCookies) {
            for (newCookie in cookies) {
                currentCookies.removeAll { it.name == newCookie.name && it.path == newCookie.path }
                currentCookies.add(newCookie)
                if (newCookie.name == "ASP.NET_SessionId") {
                    hasSession = true
                    sessionFp = LogSanitizer.computeFingerprint(newCookie.value)
                }
            }
        }

        val cookieNames = synchronized(currentCookies) { currentCookies.map { it.name }.distinct() }

        try {
            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.DEBUG,
                    component = "CookieJar",
                    operation = "SaveCookies",
                    cookies = CookieDiagnostics(
                        action = "SAVE_RESPONSE",
                        cookieCount = currentCookies.size,
                        hasSessionCookie = hasSession || getSessionFingerprint(host) != "NONE",
                        sessionFingerprint = sessionFp ?: getSessionFingerprint(host),
                        cookieNames = cookieNames
                    ),
                    message = "Saved ${cookies.size} cookies for host $host (total=${currentCookies.size})"
                )
            )
        } catch (_: Exception) {
            // Failure isolation
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val validCookies: List<Cookie>
        var expiredCount = 0

        synchronized(cookies) {
            val countBefore = cookies.size
            cookies.removeAll { it.expiresAt < now }
            expiredCount = countBefore - cookies.size
            validCookies = cookies.filter { it.matches(url) }
        }

        val sessionFp = getSessionFingerprint(host)
        val hasSession = sessionFp != "NONE"

        try {
            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.DEBUG,
                    component = "CookieJar",
                    operation = "LoadCookies",
                    cookies = CookieDiagnostics(
                        action = "LOAD_REQUEST",
                        cookieCount = validCookies.size,
                        hasSessionCookie = hasSession,
                        sessionFingerprint = sessionFp,
                        expiredRemovedCount = expiredCount,
                        cookieNames = validCookies.map { it.name }.distinct()
                    ),
                    message = "Loaded ${validCookies.size} cookies for ${url.encodedPath} (expired pruned: $expiredCount)"
                )
            )
        } catch (_: Exception) {
            // Failure isolation
        }

        return validCookies
    }

    fun clear() {
        val countBefore = cookieStore.values.sumOf { it.size }
        cookieStore.clear()
        try {
            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.INFO,
                    component = "CookieJar",
                    operation = "ClearCookies",
                    cookies = CookieDiagnostics(
                        action = "CLEAR_ALL",
                        cookieCount = 0,
                        hasSessionCookie = false,
                        sessionFingerprint = "PURGED",
                        expiredRemovedCount = 0
                    ),
                    message = "Purged all cookies (cleared $countBefore cookies)"
                )
            )
        } catch (_: Exception) {
            // Failure isolation
        }
    }

    private fun getCookieValue(host: String, cookieName: String): String? {
        val cookies = cookieStore[host] ?: return null
        synchronized(cookies) {
            return cookies.firstOrNull { it.name == cookieName }?.value
        }
    }

    fun getSessionFingerprint(host: String = "jeton.bums.ac.ir"): String {
        val sessionValue = getCookieValue(host, "ASP.NET_SessionId") ?: return "NONE"
        return LogSanitizer.computeFingerprint(sessionValue)
    }
}
