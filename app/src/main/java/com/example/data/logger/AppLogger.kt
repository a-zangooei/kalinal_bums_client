package com.example.data.logger

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object AppLogger {

    private const val TAG = "AppLogger"
    private const val MAX_LOG_FILE_BYTES = 200 * 1024L // 200 KB
    private const val PRUNE_TARGET_BYTES = 100 * 1024L // Prune to 100 KB
    private const val MAX_MEMORY_ENTRIES = 150

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logTextFile: File? = null
    private var logJsonFile: File? = null

    private val lock = Any()
    private val memoryLogList = mutableListOf<String>()
    private val _recentLogs = MutableStateFlow<List<String>>(emptyList())
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    // Strictly generate genuine ISO-8601 UTC timestamp with millisecond precision
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    var currentMinLogLevel: LogLevel = LogLevel.DEBUG

    fun init(context: Context) {
        synchronized(lock) {
            if (logTextFile == null) {
                try {
                    val logDir = File(context.filesDir, "logs")
                    if (!logDir.exists()) {
                        logDir.mkdirs()
                    }

                    logTextFile = File(logDir, "cullinan_events.log")
                    if (!logTextFile!!.exists()) {
                        logTextFile!!.createNewFile()
                    }

                    logJsonFile = File(logDir, "cullinan_events.jsonl")
                    if (!logJsonFile!!.exists()) {
                        logJsonFile!!.createNewFile()
                    }

                    loadInitialMemoryLogs()
                } catch (e: Exception) {
                    Log.e(TAG, "Initialization failed", e)
                }
            }
        }
    }

    fun generateFlowId(prefix: String = "flw"): String {
        return "${prefix}_${UUID.randomUUID().toString().take(8)}"
    }

    fun generateRequestId(): String {
        return "req_${UUID.randomUUID().toString().take(8)}"
    }

    private fun loadInitialMemoryLogs() {
        val file = logTextFile ?: return
        if (!file.exists()) return
        try {
            val lines = file.readLines()
            synchronized(lock) {
                memoryLogList.clear()
                val chunk = StringBuilder()
                for (line in lines) {
                    if (line.isBlank()) {
                        if (chunk.isNotBlank()) {
                            memoryLogList.add(chunk.toString().trim())
                            chunk.clear()
                        }
                    } else {
                        chunk.append(line).append("\n")
                    }
                }
                if (chunk.isNotBlank()) {
                    memoryLogList.add(chunk.toString().trim())
                }
                while (memoryLogList.size > MAX_MEMORY_ENTRIES) {
                    memoryLogList.removeAt(0)
                }
                _recentLogs.value = memoryLogList.toList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read initial logs from file", e)
        }
    }

    fun log(entry: LogEntry) {
        if (entry.level.ordinal < currentMinLogLevel.ordinal) return

        try {
            val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
            // Apply centralized deep sanitization across ALL fields
            val sanitized = LogSanitizer.sanitizeEntry(entry).copy(timestamp = timestamp)

            val humanReadableText = buildHumanReadableLine(sanitized)
            val jsonlLine = buildJsonlLine(sanitized)

            synchronized(lock) {
                memoryLogList.add(humanReadableText)
                if (memoryLogList.size > MAX_MEMORY_ENTRIES) {
                    memoryLogList.removeAt(0)
                }
                _recentLogs.value = memoryLogList.toList()
            }

            scope.launch {
                synchronized(lock) {
                    try {
                        logTextFile?.let { file ->
                            file.appendText("$humanReadableText\n\n", Charsets.UTF_8)
                            checkAndPruneFile(file)
                        }
                        logJsonFile?.let { file ->
                            file.appendText("$jsonlLine\n", Charsets.UTF_8)
                            checkAndPruneFile(file)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to write log to disk", e)
                    }
                }
            }
        } catch (e: Exception) {
            // Failure isolation: Logging failure must never throw into business logic
            Log.e(TAG, "Logging failed safely", e)
        }
    }

    fun logAction(action: String, details: String = "", flowId: String? = null) {
        log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "UI",
                operation = action,
                flowId = flowId,
                message = details.ifBlank { action }
            )
        )
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null, flowId: String? = null) {
        log(
            LogEntry(
                timestamp = "",
                level = LogLevel.ERROR,
                component = tag,
                operation = "Error",
                flowId = flowId,
                message = message,
                exceptionType = throwable?.javaClass?.simpleName,
                exceptionMessage = throwable?.message
            )
        )
    }

    private fun buildHumanReadableLine(e: LogEntry): String {
        val flowStr = e.flowId?.let { " [$it]" } ?: ""
        val reqStr = e.requestId?.let { " [$it]" } ?: ""
        val durStr = e.durationMs?.let { " (${it}ms)" } ?: ""

        val sb = StringBuilder()
        val opStr = if (e.operation.isNotBlank() && e.operation != e.component) "/${e.operation}" else ""
        sb.append("[${e.timestamp}] [${e.level}] [${e.component}${opStr}]${flowStr}${reqStr}: ${e.message}${durStr}")

        e.http?.let { h ->
            sb.append("\n  HTTP: ${h.method} ${h.url}")
            h.statusCode?.let { sb.append(" -> $it") }
            if (h.isCompatibilitySsl) sb.append(" (SSL Fallback)")
            if (h.isNoRedirect) sb.append(" (No-Redirect)")
        }

        e.aspnet?.let { a ->
            sb.append("\n  ASP.NET: ViewState len=${a.viewStateLength ?: 0} (fp=${a.viewStateFingerprint ?: "N/A"})")
            a.viewStateChunksCount?.let { sb.append(" Chunks=$it") }
            if (a.hasEventValidation) sb.append(" EV_len=${a.eventValidationLength ?: 0}")
            a.eventTarget?.let { sb.append(" Target=$it") }
            if (a.isCachedState) sb.append(" (Cached)")
        }

        e.cookies?.let { c ->
            sb.append("\n  Cookies: Action=${c.action} Count=${c.cookieCount} SessionFP=${c.sessionFingerprint ?: "NONE"}")
            if (c.expiredRemovedCount > 0) sb.append(" ExpiredPruned=${c.expiredRemovedCount}")
        }

        e.parser?.let { p ->
            sb.append("\n  Parser: Type=${p.pageType} HTML_len=${p.htmlLength} TablesDom=${p.rawTablesInDom} Meals=${p.parsedMealsCount}")
            if (p.isFragment) sb.append(" (UpdatePanel Fragment)")
        }

        e.notice?.let { n ->
            sb.append("\n  Notice: Detected=${n.isNoticeDetected} Type=${n.noticeType ?: "None"} Ack=${n.isAcknowledged}")
        }

        e.exceptionType?.let {
            sb.append("\n  Exception: $it - ${e.exceptionMessage ?: ""}")
        }

        return sb.toString()
    }

    /**
     * Serializes LogEntry to a single JSONL line using native org.json.JSONObject.
     * Guarantees correct escaping for all control characters and Unicode.
     */
    fun buildJsonlLine(e: LogEntry): String {
        return try {
            val json = JSONObject()
            json.put("ts", e.timestamp)
            json.put("lvl", e.level.name)
            json.put("comp", e.component)
            json.put("op", e.operation)
            e.flowId?.let { json.put("flowId", it) }
            e.requestId?.let { json.put("reqId", it) }
            e.durationMs?.let { json.put("durMs", it) }
            e.errorCategory?.let { json.put("errCat", it) }

            e.http?.let { h ->
                val httpJson = JSONObject()
                httpJson.put("method", h.method)
                httpJson.put("url", h.url)
                h.statusCode?.let { httpJson.put("status", it) }
                h.contentType?.let { httpJson.put("contentType", it) }
                h.contentLength?.let { httpJson.put("contentLength", it) }
                h.redirectCount?.let { httpJson.put("redirectCount", it) }
                h.finalUrl?.let { httpJson.put("finalUrl", it) }
                if (h.isCompatibilitySsl) httpJson.put("sslFallback", true)
                if (h.isNoRedirect) httpJson.put("noRedirect", true)
                json.put("http", httpJson)
            }

            e.aspnet?.let { a ->
                val aspJson = JSONObject()
                a.viewStateLength?.let { aspJson.put("vsLen", it) }
                a.viewStateFingerprint?.let { aspJson.put("vsFp", it) }
                a.viewStateChunksCount?.let { aspJson.put("vsChunks", it) }
                a.viewStateFieldCount?.let { aspJson.put("vsFieldCount", it) }
                a.viewStateGenerator?.let { aspJson.put("vsGen", it) }
                if (a.hasEventValidation) {
                    aspJson.put("hasEv", true)
                    a.eventValidationLength?.let { aspJson.put("evLen", it) }
                }
                a.eventTarget?.let { aspJson.put("target", it) }
                a.formFieldCount?.let { aspJson.put("fields", it) }
                if (a.isCachedState) aspJson.put("cached", true)
                json.put("aspnet", aspJson)
            }

            e.cookies?.let { c ->
                val cookieJson = JSONObject()
                cookieJson.put("act", c.action)
                cookieJson.put("cnt", c.cookieCount)
                cookieJson.put("hasSession", c.hasSessionCookie)
                c.sessionFingerprint?.let { cookieJson.put("sessionFp", it) }
                if (c.expiredRemovedCount > 0) cookieJson.put("expiredPruned", c.expiredRemovedCount)
                json.put("cookies", cookieJson)
            }

            e.parser?.let { p ->
                val parserJson = JSONObject()
                parserJson.put("pageType", p.pageType)
                parserJson.put("htmlLen", p.htmlLength)
                parserJson.put("rawTables", p.rawTablesInDom)
                parserJson.put("tablesFound", p.tablesFound)
                parserJson.put("meals", p.parsedMealsCount)
                parserJson.put("skippedRows", p.skippedRowsCount)
                parserJson.put("isEmpty", p.isMenuEmpty)
                if (p.isFragment) parserJson.put("isFragment", true)
                json.put("parser", parserJson)
            }

            e.notice?.let { n ->
                val noticeJson = JSONObject()
                noticeJson.put("detected", n.isNoticeDetected)
                n.noticeType?.let { noticeJson.put("type", it) }
                noticeJson.put("ack", n.isAcknowledged)
                n.actionUrl?.let { noticeJson.put("action", it) }
                json.put("notice", noticeJson)
            }

            json.put("msg", e.message)
            e.exceptionType?.let { json.put("exType", it) }
            e.exceptionMessage?.let { json.put("exMsg", it) }

            json.toString()
        } catch (ex: Exception) {
            Log.e(TAG, "buildJsonlLine failed for ${e.component}/${e.operation}", ex)
            "{\"ts\":\"${e.timestamp}\",\"lvl\":\"ERROR\",\"comp\":\"${e.component}\",\"msg\":\"Serialization failure: ${ex.message}\"}"
        }
    }

    private fun checkAndPruneFile(file: File) {
        if (!file.exists()) return
        val currentSize = file.length()
        if (currentSize > MAX_LOG_FILE_BYTES) {
            try {
                val skipBytes = currentSize - PRUNE_TARGET_BYTES
                val raf = RandomAccessFile(file, "r")
                raf.seek(skipBytes)
                raf.readLine()
                val remainingBytes = ByteArray((currentSize - raf.filePointer).toInt())
                raf.readFully(remainingBytes)
                raf.close()

                file.writeBytes(remainingBytes)
                Log.i(TAG, "Pruned log file ${file.name} to ${file.length()} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prune log file ${file.name}", e)
            }
        }
    }

    fun clearLogs() {
        synchronized(lock) {
            memoryLogList.clear()
            _recentLogs.value = emptyList()
            try {
                logTextFile?.writeText("", Charsets.UTF_8)
                logJsonFile?.writeText("", Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear log files", e)
            }
        }
    }

    fun getExportableText(): String {
        synchronized(lock) {
            return try {
                val text = logTextFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8)
                if (!text.isNullOrBlank()) LogSanitizer.sanitizeText(text) else LogSanitizer.sanitizeText(memoryLogList.joinToString("\n\n"))
            } catch (_: Exception) {
                LogSanitizer.sanitizeText(memoryLogList.joinToString("\n\n"))
            }
        }
    }

    fun getExportableJsonl(): String {
        synchronized(lock) {
            return try {
                val text = logJsonFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: ""
                LogSanitizer.sanitizeText(text)
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun sanitize(raw: String): String = LogSanitizer.sanitizeText(raw)
}
