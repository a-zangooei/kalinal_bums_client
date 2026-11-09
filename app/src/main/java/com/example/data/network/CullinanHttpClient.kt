package com.example.data.network

import android.util.Log
import com.example.data.logger.AppLogger
import com.example.data.logger.AspNetDiagnostics
import com.example.data.logger.HttpDiagnostics
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
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
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val method = req.method
                val reqId = AppLogger.generateRequestId()

                var bodyString: String? = null
                val body = req.body
                if (body != null) {
                    try {
                        val buffer = okio.Buffer()
                        body.writeTo(buffer)
                        bodyString = buffer.readUtf8()
                    } catch (_: Exception) {
                    }
                }

                // Extract ASP.NET WebForms field diagnostics from request body
                val aspNetDiag = extractAspNetDiagnosticsFromBody(bodyString)

                AppLogger.log(
                    LogEntry(
                        timestamp = "",
                        level = LogLevel.DEBUG,
                        component = "HTTP",
                        operation = "Request",
                        requestId = reqId,
                        http = HttpDiagnostics(
                            method = method,
                            url = url,
                            contentType = req.body?.contentType()?.toString(),
                            contentLength = req.body?.contentLength(),
                            isCompatibilitySsl = _isCompatibilityModeActive.value
                        ),
                        aspnet = aspNetDiag,
                        message = "HTTP $method $url"
                    )
                )

                val start = System.currentTimeMillis()
                val response: Response
                try {
                    response = chain.proceed(req)
                } catch (e: Exception) {
                    val cat = classifyNetworkingFailure(e)
                    AppLogger.log(
                        LogEntry(
                            timestamp = "",
                            level = LogLevel.ERROR,
                            component = "HTTP",
                            operation = "Failure",
                            requestId = reqId,
                            durationMs = System.currentTimeMillis() - start,
                            http = HttpDiagnostics(
                                method = method,
                                url = url,
                                isCompatibilitySsl = _isCompatibilityModeActive.value
                            ),
                            errorCategory = cat,
                            exceptionType = e.javaClass.simpleName,
                            exceptionMessage = e.message,
                            message = "HTTP $method $url failed with $cat: ${e.message}"
                        )
                    )
                    throw e
                }
                val duration = System.currentTimeMillis() - start

                var preview: String? = null
                try {
                    preview = response.peekBody(1024).string()
                } catch (_: Exception) {
                }

                val respLogLevel = if (response.isSuccessful) LogLevel.DEBUG else LogLevel.WARN

                AppLogger.log(
                    LogEntry(
                        timestamp = "",
                        level = respLogLevel,
                        component = "HTTP",
                        operation = "Response",
                        requestId = reqId,
                        durationMs = duration,
                        http = HttpDiagnostics(
                            method = method,
                            url = url,
                            statusCode = response.code,
                            contentType = response.body?.contentType()?.toString(),
                            contentLength = response.body?.contentLength(),
                            finalUrl = response.request.url.toString(),
                            isCompatibilitySsl = _isCompatibilityModeActive.value
                        ),
                        message = "HTTP ${response.code} (${duration}ms) - $url"
                    )
                )
                response
            }
    }

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

    fun executeCallNoRedirect(request: Request): Response {
        val baseClient = if (_isCompatibilityModeActive.value) fallbackClient else standardClient
        val noRedirectClient = baseClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        return try {
            noRedirectClient.newCall(request).execute()
        } catch (e: Exception) {
            if (!_isCompatibilityModeActive.value && isSslHandshakeException(e)) {
                _isCompatibilityModeActive.value = true
                val fbClientNoRedirect = fallbackClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(6, TimeUnit.SECONDS)
                    .build()
                fbClientNoRedirect.newCall(request).execute()
            } else {
                throw e
            }
        }
    }

    private fun extractAspNetDiagnosticsFromBody(bodyString: String?): AspNetDiagnostics? {
        if (bodyString.isNullOrBlank()) return null
        var vsLen: Int? = null
        var vsFp: String? = null
        var vsGen: String? = null
        var hasEv = false
        var evLen: Int? = null
        var target: String? = null

        val pairs = bodyString.split("&")
        for (pair in pairs) {
            val parts = pair.split("=")
            if (parts.size == 2) {
                val key = java.net.URLDecoder.decode(parts[0], "UTF-8")
                val value = java.net.URLDecoder.decode(parts[1], "UTF-8")

                when (key) {
                    "__VIEWSTATE" -> {
                        vsLen = value.length
                        vsFp = LogSanitizer.computeFingerprint(value)
                    }
                    "__VIEWSTATEGENERATOR" -> vsGen = value
                    "__EVENTVALIDATION" -> {
                        hasEv = true
                        evLen = value.length
                    }
                    "__EVENTTARGET" -> if (value.isNotBlank()) target = value
                }
            }
        }

        if (vsLen == null && !hasEv && target == null) return null

        return AspNetDiagnostics(
            viewStateLength = vsLen,
            viewStateFingerprint = vsFp,
            viewStateGenerator = vsGen,
            hasEventValidation = hasEv,
            eventValidationLength = evLen,
            eventTarget = target,
            formFieldCount = pairs.size
        )
    }

    private fun classifyNetworkingFailure(e: Throwable): String {
        var curr: Throwable? = e
        while (curr != null) {
            val name = curr.javaClass.name
            val msg = curr.message ?: ""

            if (curr is java.net.UnknownHostException) return "DNS_RESOLUTION_FAILED"
            if (curr is java.net.ConnectException) return "CONNECTION_FAILED"
            if (curr is java.net.SocketTimeoutException) return "SOCKET_TIMEOUT"
            if (isSslHandshakeException(curr)) return "TLS_HANDSHAKE_FAILED"
            if (name.contains("SocketException")) return "CONNECTION_RESET"
            curr = curr.cause
        }
        return "NETWORK_ERROR"
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
