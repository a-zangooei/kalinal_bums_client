package com.example.data.logger

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}

/**
 * Contextual correlation metadata attached to OkHttp Requests via Request.tag(LogContext::class.java).
 * Survives request retries and clones while providing correlation across UI, Repositories, Network, and CookieJar.
 */
data class LogContext(
    val flowId: String,
    val operation: String,
    val parentReqId: String? = null
)

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val component: String,
    val operation: String,
    val flowId: String? = null,
    val requestId: String? = null,
    val durationMs: Long? = null,
    val http: HttpDiagnostics? = null,
    val aspnet: AspNetDiagnostics? = null,
    val cookies: CookieDiagnostics? = null,
    val parser: ParserDiagnostics? = null,
    val notice: NoticeDiagnostics? = null,
    val message: String,
    val errorCategory: String? = null,
    val exceptionType: String? = null,
    val exceptionMessage: String? = null
)

data class HttpDiagnostics(
    val method: String,
    val url: String,
    val statusCode: Int? = null,
    val contentType: String? = null,
    val contentLength: Long? = null,
    val redirectCount: Int? = null,
    val finalUrl: String? = null,
    val isCompatibilitySsl: Boolean = false,
    val isNoRedirect: Boolean = false
)

data class AspNetDiagnostics(
    val viewStateLength: Int? = null,
    val viewStateFingerprint: String? = null,
    val viewStateChunksCount: Int? = null,
    val viewStateFieldCount: Int? = null,
    val viewStateGenerator: String? = null,
    val hasEventValidation: Boolean = false,
    val eventValidationLength: Int? = null,
    val eventTarget: String? = null,
    val formFieldCount: Int? = null,
    val isCachedState: Boolean = false
)

data class CookieDiagnostics(
    val action: String, // SAVE_RESPONSE, LOAD_REQUEST, CLEAR_ALL
    val cookieCount: Int,
    val hasSessionCookie: Boolean = false,
    val sessionFingerprint: String? = null,
    val expiredRemovedCount: Int = 0
)

data class ParserDiagnostics(
    val pageType: String, // RESERVATION, LOGIN, NOTICE, ERROR, UNKNOWN
    val htmlLength: Int,
    val rawTablesInDom: Int = 0,
    val tablesFound: Int = 0,
    val parsedMealsCount: Int = 0,
    val skippedRowsCount: Int = 0,
    val isMenuEmpty: Boolean = false,
    val isFragment: Boolean = false
)

data class NoticeDiagnostics(
    val isNoticeDetected: Boolean,
    val noticeType: String? = null,
    val isAcknowledged: Boolean = false,
    val actionUrl: String? = null
)
