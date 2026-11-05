package com.example.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class CullinanCookieJar : CookieJar {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val currentCookies = cookieStore.getOrPut(host) { mutableListOf() }
        synchronized(currentCookies) {
            for (newCookie in cookies) {
                currentCookies.removeAll { it.name == newCookie.name }
                currentCookies.add(newCookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(cookies) {
            cookies.removeAll { it.expiresAt < now }
            return cookies.toList()
        }
    }

    fun clear() {
        cookieStore.clear()
    }

    fun getCookieValue(host: String, cookieName: String): String? {
        val cookies = cookieStore[host] ?: return null
        synchronized(cookies) {
            return cookies.firstOrNull { it.name == cookieName }?.value
        }
    }
}
