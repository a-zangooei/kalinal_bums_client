package com.example.data.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object CullinanHttpClient {

    private const val TAG = "CullinanHttpClient"

    const val BASE_URL = "https://jeton.bums.ac.ir"
    const val LOGIN_URL = "$BASE_URL/Login.aspx"
    const val CAPTCHA_URL = "$BASE_URL/UserControls/Captcha.ashx"
    const val RESERVATION_URL = "$BASE_URL/Reservation/Reservation.aspx"

    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    val cookieJar = CullinanCookieJar()

    private val _isCompatibilityModeActive = MutableStateFlow(false)
    val isCompatibilityModeActive: StateFlow<Boolean> = _isCompatibilityModeActive.asStateFlow()

    // 1. Standard Client using System & User CA trust anchors
    val standardClient: OkHttpClient by lazy {
        buildBaseClientBuilder().build()
    }

    // 2. Adaptive Fallback Client for Intranet/University Certificate Chains
    val fallbackClient: OkHttpClient by lazy {
        try {
            val fallbackTrustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(fallbackTrustManager), SecureRandom())
            }

            val fallbackHostnameVerifier = HostnameVerifier { hostname, session ->
                hostname == "jeton.bums.ac.ir" ||
                        hostname.endsWith(".bums.ac.ir") ||
                        OkHttpClient().hostnameVerifier.verify(hostname, session)
            }

            buildBaseClientBuilder()
                .sslSocketFactory(sslContext.socketFactory, fallbackTrustManager)
                .hostnameVerifier(fallbackHostnameVerifier)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize fallback SSL client, falling back to standard client", e)
            standardClient
        }
    }

    /**
     * Dynamically points to the active client based on whether compatibility mode was triggered.
     */
    val client: OkHttpClient
        get() = if (_isCompatibilityModeActive.value) fallbackClient else standardClient

    private fun buildBaseClientBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()
                chain.proceed(request)
            }
    }

    /**
     * Executes an HTTP request with automatic, seamless fallback to University Intranet SSL Compatibility Mode
     * if the platform TrustManager fails due to incomplete certificate chain (Trust anchor not found).
     */
    fun executeCall(request: Request): Response {
        if (_isCompatibilityModeActive.value) {
            return fallbackClient.newCall(request).execute()
        }

        return try {
            standardClient.newCall(request).execute()
        } catch (e: Exception) {
            if (isSslHandshakeException(e)) {
                Log.w(TAG, "SSL Trust Anchor validation failed (${e.message}). Switching automatically to University SSL Compatibility Mode.")
                _isCompatibilityModeActive.value = true
                fallbackClient.newCall(request).execute()
            } else {
                throw e
            }
        }
    }

    fun setCompatibilityMode(enabled: Boolean) {
        _isCompatibilityModeActive.value = enabled
    }

    private fun isSslHandshakeException(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            val name = current.javaClass.name
            if (current is javax.net.ssl.SSLHandshakeException ||
                current is javax.net.ssl.SSLException ||
                current is java.security.cert.CertificateException ||
                name.contains("CertPathValidatorException") ||
                name.contains("SSLPeerUnverifiedException") ||
                name.contains("TrustAnchor")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
