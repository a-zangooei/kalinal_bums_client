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

        synchronized(currentCookies) {
            for (newCookie in cookies) {
                currentCookies.removeAll { it.name == newCookie.name && it.path == newCookie.path }
                currentCookies.add(newCookie)
                if (newCookie.name == "ASP.NET_SessionId") {
                    sessionFp = LogSanitizer.computeFingerprint(newCookie.value)
                }
            }
        }

        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.DEBUG,
                component = "CookieJar",
                operation = "SaveCookies",
                cookies = CookieDiagnostics(
                    action = "SAVE_RESPONSE",
                    cookieCount = currentCookies.size,
                    sessionFingerprint = sessionFp ?: getSessionFingerprint(host)
                ),
                message = "Saved ${cookies.size} cookies for host $host"
            )
        )
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val validCookies: List<Cookie>

        synchronized(cookies) {
            cookies.removeAll { it.expiresAt < now }
            validCookies = cookies.filter { it.matches(url) }
        }

        return validCookies
    }

    fun clear() {
        val countBefore = cookieStore.values.sumOf { it.size }
        cookieStore.clear()
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "CookieJar",
                operation = "ClearCookies",
                cookies = CookieDiagnostics(
                    action = "CLEAR_ALL",
                    cookieCount = 0,
                    sessionFingerprint = "PURGED"
                ),
                message = "Purged all cookies (cleared $countBefore cookies)"
            )
        )
    }

    fun getCookieValue(host: String, cookieName: String): String? {
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
