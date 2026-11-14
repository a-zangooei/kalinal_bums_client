package com.example.data.network

import android.util.Log
import com.example.data.logger.AppLogger
import com.example.data.logger.AspNetDiagnostics
import com.example.data.logger.HttpDiagnostics
import com.example.data.logger.LogContext
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
import com.example.data.logger.SnapshotDiagnostics
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

    const val DEFAULT_BASE_URL = "https://jeton.bums.ac.ir"

    @Volatile
    private var customBaseUrl: String? = null

    val BASE_URL: String get() = customBaseUrl ?: DEFAULT_BASE_URL
    val LOGIN_URL: String get() = "$BASE_URL/Login.aspx"
    val RESERVATION_URL: String get() = "$BASE_URL/Reservation/Reservation.aspx"
    val MESSAGES_URL: String get() = "$BASE_URL/MyCullinan/MyMessage.aspx"
    val PROFILE_URL: String get() = "$BASE_URL/MyCullinan/MyProfile.aspx"
    val USER_IMAGE_URL: String get() = "$BASE_URL/UserControls/UserImage.ashx"
    val CAPTCHA_URL: String get() = "$BASE_URL/UserControls/Captcha.ashx"

    fun setCustomBaseUrl(url: String) {
        customBaseUrl = url.trimEnd('/')
    }

    fun resetCustomBaseUrl() {
        customBaseUrl = null
    }

    val cookieJar = CullinanCookieJar()

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val _isCompatibilityModeActive = MutableStateFlow(false)
    val isCompatibilityModeActive: StateFlow<Boolean> = _isCompatibilityModeActive.asStateFlow()

    private val userAgentInterceptor: Interceptor by lazy {
        Interceptor { chain ->
            val original = chain.request()
            if (original.header("User-Agent") != null) {
                chain.proceed(original)
            } else {
                val updated = original.newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(updated)
            }
        }
    }

    private val standardClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
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
                .addInterceptor(userAgentInterceptor)
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
            val (formKeys, aspNetDiag) = inspectRequestBodySafely(req)

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
                            userAgent = req.header("User-Agent"),
                            formKeys = formKeys.takeIf { it.isNotEmpty() },
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
                                userAgent = req.header("User-Agent"),
                                isCompatibilitySsl = _isCompatibilityModeActive.value
                            ),
                            errorCategory = cat,
                            exceptionType = e.javaClass.simpleName,
                            exceptionMessage = e.message,
                            stackTrace = e.stackTraceToString().take(4000),
                            message = "HTTP $method $url failed with $cat: ${e.message}"
                        )
                    )
                } catch (_: Exception) {}
                throw e
            }
            val duration = System.currentTimeMillis() - start

            var redirectCount = 0
            var prior = response.priorResponse
            while (prior != null) {
                redirectCount++
                prior = prior.priorResponse
            }
            val redirectLocation = response.header("Location")
            val setCookieNames = response.headers("Set-Cookie").mapNotNull { header ->
                header.substringBefore('=', "").trim().takeIf { it.isNotEmpty() }
            }.distinct()

            val respContentType = response.body?.contentType()?.toString()
            val isBinaryAsset = respContentType?.startsWith("image/", ignoreCase = true) == true && response.isSuccessful
            val finalUrlStr = response.request.url.toString()
            val isSensitiveProfilePage = finalUrlStr.contains("MyProfile.aspx", ignoreCase = true) ||
                url.contains("MyProfile.aspx", ignoreCase = true)
            val snapshotRecord = if (!isBinaryAsset && !isSensitiveProfilePage && response.body != null) {
                try {
                    val peeked = response.peekBody(2 * 1024 * 1024L)
                    val rawText = peeked.string()
                    if (rawText.isNotEmpty()) {
                        AppLogger.savePageSnapshot(
                            operation = operation,
                            method = method,
                            url = response.request.url.toString(),
                            statusCode = response.code,
                            contentType = respContentType,
                            rawContent = rawText,
                            flowId = flowId,
                            requestId = reqId
                        )
                    } else null
                } catch (_: Exception) {
                    null
                }
            } else null

            val snapshotDiag = snapshotRecord?.let {
                SnapshotDiagnostics(
                    sequence = it.sequence,
                    fileName = it.fileName,
                    contentType = it.contentType,
                    sizeBytes = it.sizeBytes,
                    sha256Prefix = LogSanitizer.computeFingerprint(it.rawContent)
                )
            }

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
                            contentType = respContentType,
                            contentLength = response.body?.contentLength(),
                            redirectCount = redirectCount.takeIf { it > 0 },
                            finalUrl = response.request.url.toString(),
                            redirectLocation = redirectLocation,
                            setCookieNames = setCookieNames.takeIf { it.isNotEmpty() },
                            isCompatibilitySsl = _isCompatibilityModeActive.value,
                            isNoRedirect = response.isRedirect
                        ),
                        snapshot = snapshotDiag,
                        message = "HTTP ${response.code} (${duration}ms) - $url" +
                            (snapshotDiag?.let { " [Snapshot: ${it.fileName}]" } ?: "")
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
                val logCtx = request.tag(LogContext::class.java)
                AppLogger.logWarn(
                    component = "CullinanHttpClient",
                    operation = "SslFallbackActivated",
                    message = "TLS handshake failed on standard client for ${request.url}; switching to unverified compatibility SSL fallback",
                    throwable = e,
                    flowId = logCtx?.flowId,
                    errorCategory = "TLS_FALLBACK"
                )
                _isCompatibilityModeActive.value = true
                fallbackClient.newCall(request).execute()
            } else {
                throw e
            }
        }
    }

    fun setCompatibilityMode(enabled: Boolean) {
        val previous = _isCompatibilityModeActive.value
        _isCompatibilityModeActive.value = enabled
        if (previous != enabled) {
            AppLogger.logInfo(
                component = "CullinanHttpClient",
                operation = "CompatibilityModeChanged",
                message = "SSL compatibility mode changed: $previous -> $enabled"
            )
        }
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
                val logCtx = request.tag(LogContext::class.java)
                AppLogger.logWarn(
                    component = "CullinanHttpClient",
                    operation = "SslFallbackActivatedNoRedirect",
                    message = "TLS handshake failed on no-redirect client for ${request.url}; switching to unverified compatibility SSL fallback",
                    throwable = e,
                    flowId = logCtx?.flowId,
                    errorCategory = "TLS_FALLBACK"
                )
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
    private fun inspectRequestBodySafely(request: Request): Pair<List<String>, AspNetDiagnostics?> {
        val body = request.body ?: return Pair(emptyList(), null)
        val bodyString = try {
            val buffer = Buffer()
            body.writeTo(buffer)
            // Limit preview reading to max 128KB to prevent memory pressure
            val bytesToRead = minOf(buffer.size, 128 * 1024L)
            buffer.readString(bytesToRead, Charsets.UTF_8)
        } catch (_: Exception) {
            return Pair(emptyList(), null)
        }

        var vsTotalLen = 0
        val vsChunks = mutableListOf<String>()
        var vsGen: String? = null
        var hasEv = false
        var evLen: Int? = null
        var target: String? = null
        var fieldCountParam: Int? = null
        val formKeys = mutableListOf<String>()

        val pairs = bodyString.split("&")
        for (pair in pairs) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx != -1) {
                val rawKey = pair.substring(0, eqIdx)
                val rawVal = pair.substring(eqIdx + 1)
                val key = try { URLDecoder.decode(rawKey, "UTF-8") } catch (_: Exception) { rawKey }
                val value = try { URLDecoder.decode(rawVal, "UTF-8") } catch (_: Exception) { rawVal }

                if (key.isNotBlank() && formKeys.size < 40) {
                    formKeys.add(key)
                }

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
                    key.endsWith("ScriptManager") && value.isNotBlank() && target == null -> target = value
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

        return Pair(formKeys, aspNetDiag)
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
