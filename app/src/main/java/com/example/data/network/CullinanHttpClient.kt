package com.example.data.network

import android.util.Log
import com.example.data.logger.AppLogger
import com.example.data.logger.AspNetDiagnostics
import com.example.data.logger.HttpDiagnostics
import com.example.data.logger.LogContext
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.net.URLDecoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object CullinanHttpClient {

    private const val TAG = "CullinanHttpClient"

    const val BASE_URL = "https://jeton.bums.ac.ir"
    const val LOGIN_URL = "$BASE_URL/Login.aspx"
    const val RESERVATION_URL = "$BASE_URL/Reservation/Reservation.aspx"
    const val MESSAGES_URL = "$BASE_URL/MyCullinan/MyMessage.aspx"
    const val CAPTCHA_URL = "$BASE_URL/UserControls/Captcha.ashx"

    val cookieJar = CullinanCookieJar()

    private val _isCompatibilityModeActive = MutableStateFlow(false)
    val isCompatibilityModeActive: StateFlow<Boolean> = _isCompatibilityModeActive.asStateFlow()

    private val standardClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    internal val fallbackClient: OkHttpClient by lazy {
        try {
            val permissiveTrustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(permissiveTrustManager), SecureRandom())
            }

            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .sslSocketFactory(sslContext.socketFactory, permissiveTrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Fallback client init error", e)
            standardClient
        }
    }

    val client: OkHttpClient
        get() = if (_isCompatibilityModeActive.value) fallbackClient else standardClient

    private val loggingInterceptor: Interceptor by lazy {
        Interceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()
            val method = req.method

            // Propagate LogContext attached to Request via request.tag(LogContext::class.java)
            val logContext = req.tag(LogContext::class.java)
            val flowId = logContext?.flowId
            val operation = logContext?.operation ?: "Http$method"
            val reqId = logContext?.parentReqId ?: AppLogger.generateRequestId()

            // Safe read-only inspection of request body without mutation or stream exhaustion
            val (bodyString, aspNetDiag) = inspectRequestBodySafely(req)

            try {
                AppLogger.log(
                    LogEntry(
                        timestamp = "",
                        level = LogLevel.DEBUG,
                        component = "HTTP",
                        operation = operation,
                        flowId = flowId,
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
            } catch (_: Exception) {}

            val start = System.currentTimeMillis()
            val response: Response
            try {
                response = chain.proceed(req)
            } catch (e: Exception) {
                val cat = classifyNetworkingFailure(e)
                try {
                    AppLogger.log(
                        LogEntry(
                            timestamp = "",
                            level = LogLevel.ERROR,
                            component = "HTTP",
                            operation = "$operation:Failure",
                            flowId = flowId,
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
                } catch (_: Exception) {}
                throw e
            }
            val duration = System.currentTimeMillis() - start

            val respLogLevel = if (response.isSuccessful || response.isRedirect) LogLevel.DEBUG else LogLevel.WARN

            try {
                AppLogger.log(
                    LogEntry(
                        timestamp = "",
                        level = respLogLevel,
                        component = "HTTP",
                        operation = "$operation:Response",
                        flowId = flowId,
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
            } catch (_: Exception) {}

            response
        }
    }

    fun executeCall(request: Request): Response {
        if (_isCompatibilityModeActive.value) {
            return fallbackClient.newCall(request).execute()
        }

        return try {
            standardClient.newCall(request).execute()
        } catch (e: IOException) {
            if (isSslHandshakeException(e)) {
                Log.w(TAG, "SSL failure on standard client. Activating compatibility mode...", e)
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
            .build()

        return try {
            noRedirectClient.newCall(request).execute()
        } catch (e: IOException) {
            if (isSslHandshakeException(e) && !_isCompatibilityModeActive.value) {
                Log.w(TAG, "SSL failure on standard client (no-redirect). Activating fallback...", e)
                _isCompatibilityModeActive.value = true
                val fallbackNoRedirect = fallbackClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                fallbackNoRedirect.newCall(request).execute()
            } else {
                throw e
            }
        }
    }

    /**
     * Safely inspects the request body read-only without consuming or altering the original network stream.
     * Accurately parses URL-encoded parameters with support for Base64 and multi-chunk ViewStates.
     */
    private fun inspectRequestBodySafely(request: Request): Pair<String?, AspNetDiagnostics?> {
        val body = request.body ?: return Pair(null, null)
        val bodyString = try {
            val buffer = Buffer()
            body.writeTo(buffer)
            // Limit preview reading to max 128KB to prevent memory pressure
            val bytesToRead = minOf(buffer.size, 128 * 1024L)
            buffer.readString(bytesToRead, Charsets.UTF_8)
        } catch (_: Exception) {
            return Pair(null, null)
        }

        var vsTotalLen = 0
        val vsChunks = mutableListOf<String>()
        var vsGen: String? = null
        var hasEv = false
        var evLen: Int? = null
        var target: String? = null
        var fieldCountParam: Int? = null

        val pairs = bodyString.split("&")
        for (pair in pairs) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx != -1) {
                val rawKey = pair.substring(0, eqIdx)
                val rawVal = pair.substring(eqIdx + 1)
                val key = try { URLDecoder.decode(rawKey, "UTF-8") } catch (_: Exception) { rawKey }
                val value = try { URLDecoder.decode(rawVal, "UTF-8") } catch (_: Exception) { rawVal }

                when {
                    key == "__VIEWSTATE" -> {
                        vsTotalLen += value.length
                        vsChunks.add(value)
                    }
                    key.startsWith("__VIEWSTATE") && key.removePrefix("__VIEWSTATE").toIntOrNull() != null -> {
                        vsTotalLen += value.length
                        vsChunks.add(value)
                    }
                    key == "__VIEWSTATEFIELDCOUNT" -> {
                        fieldCountParam = value.toIntOrNull()
                    }
                    key == "__VIEWSTATEGENERATOR" -> vsGen = value
                    key == "__EVENTVALIDATION" -> {
                        hasEv = true
                        evLen = value.length
                    }
                    key == "__EVENTTARGET" -> if (value.isNotBlank()) target = value
                }
            }
        }

        val primaryFingerprint = if (vsChunks.isNotEmpty()) LogSanitizer.computeFingerprint(vsChunks[0]) else null

        val aspNetDiag = if (vsTotalLen > 0 || hasEv || target != null) {
            AspNetDiagnostics(
                viewStateLength = if (vsTotalLen > 0) vsTotalLen else null,
                viewStateFingerprint = primaryFingerprint,
                viewStateChunksCount = if (vsChunks.size > 1) vsChunks.size else null,
                viewStateFieldCount = fieldCountParam,
                viewStateGenerator = vsGen,
                hasEventValidation = hasEv,
                eventValidationLength = evLen,
                eventTarget = target,
                formFieldCount = pairs.size,
                isCachedState = false
            )
        } else null

        return Pair(bodyString, aspNetDiag)
    }

    private fun classifyNetworkingFailure(e: Throwable): String {
        var curr: Throwable? = e
        while (curr != null) {
            val name = curr.javaClass.name
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
            if (current is SSLException) return true
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("trust anchor") || msg.contains("sslhandshake") || msg.contains("certpathvalidator")) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
