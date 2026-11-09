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

    private fun escapeJson(s: String?): String {
        if (s == null) return ""
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (ch < ' ') {
                        sb.append("\\u%04x".format(ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * Serializes LogEntry to a single JSONL line with complete character escaping.
     * Guarantees universal execution compatibility across JVM Unit Tests and Android Runtimes.
     */
    fun buildJsonlLine(e: LogEntry): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"ts\":\"${escapeJson(e.timestamp)}\",")
        sb.append("\"lvl\":\"${e.level.name}\",")
        sb.append("\"comp\":\"${escapeJson(e.component)}\",")
        sb.append("\"op\":\"${escapeJson(e.operation)}\"")
        if (e.flowId != null) sb.append(",\"flowId\":\"${escapeJson(e.flowId)}\"")
        if (e.requestId != null) sb.append(",\"reqId\":\"${escapeJson(e.requestId)}\"")
        if (e.durationMs != null) sb.append(",\"durMs\":${e.durationMs}")
        if (e.errorCategory != null) sb.append(",\"errCat\":\"${escapeJson(e.errorCategory)}\"")

        e.http?.let { h ->
            sb.append(",\"http\":{")
            sb.append("\"method\":\"${escapeJson(h.method)}\",")
            sb.append("\"url\":\"${escapeJson(h.url)}\"")
            if (h.statusCode != null) sb.append(",\"status\":${h.statusCode}")
            if (h.contentType != null) sb.append(",\"contentType\":\"${escapeJson(h.contentType)}\"")
            if (h.contentLength != null) sb.append(",\"contentLength\":${h.contentLength}")
            if (h.redirectCount != null) sb.append(",\"redirectCount\":${h.redirectCount}")
            if (h.finalUrl != null) sb.append(",\"finalUrl\":\"${escapeJson(h.finalUrl)}\"")
            if (h.isCompatibilitySsl) sb.append(",\"sslFallback\":true")
            if (h.isNoRedirect) sb.append(",\"noRedirect\":true")
            sb.append("}")
        }

        e.aspnet?.let { a ->
            sb.append(",\"aspnet\":{")
            sb.append("\"hasEv\":${a.hasEventValidation}")
            if (a.viewStateLength != null) sb.append(",\"vsLen\":${a.viewStateLength}")
            if (a.viewStateFingerprint != null) sb.append(",\"vsFp\":\"${escapeJson(a.viewStateFingerprint)}\"")
            if (a.viewStateChunksCount != null) sb.append(",\"vsChunks\":${a.viewStateChunksCount}")
            if (a.viewStateFieldCount != null) sb.append(",\"vsFieldCount\":${a.viewStateFieldCount}")
            if (a.viewStateGenerator != null) sb.append(",\"vsGen\":\"${escapeJson(a.viewStateGenerator)}\"")
            if (a.eventValidationLength != null) sb.append(",\"evLen\":${a.eventValidationLength}")
            if (a.eventTarget != null) sb.append(",\"target\":\"${escapeJson(a.eventTarget)}\"")
            if (a.formFieldCount != null) sb.append(",\"fields\":${a.formFieldCount}")
            if (a.isCachedState) sb.append(",\"cached\":true")
            sb.append("}")
        }

        e.cookies?.let { c ->
            sb.append(",\"cookies\":{")
            sb.append("\"act\":\"${escapeJson(c.action)}\",")
            sb.append("\"cnt\":${c.cookieCount},")
            sb.append("\"hasSession\":${c.hasSessionCookie}")
            if (c.sessionFingerprint != null) sb.append(",\"sessionFp\":\"${escapeJson(c.sessionFingerprint)}\"")
            if (c.expiredRemovedCount > 0) sb.append(",\"expiredPruned\":${c.expiredRemovedCount}")
            sb.append("}")
        }

        e.parser?.let { p ->
            sb.append(",\"parser\":{")
            sb.append("\"pageType\":\"${escapeJson(p.pageType)}\",")
            sb.append("\"htmlLen\":${p.htmlLength},")
            sb.append("\"rawTables\":${p.rawTablesInDom},")
            sb.append("\"tablesFound\":${p.tablesFound},")
            sb.append("\"meals\":${p.parsedMealsCount},")
            sb.append("\"skippedRows\":${p.skippedRowsCount},")
            sb.append("\"isEmpty\":${p.isMenuEmpty}")
            if (p.isFragment) sb.append(",\"isFragment\":true")
            sb.append("}")
        }

        e.notice?.let { n ->
            sb.append(",\"notice\":{")
            sb.append("\"detected\":${n.isNoticeDetected},")
            sb.append("\"ack\":${n.isAcknowledged}")
            if (n.noticeType != null) sb.append(",\"type\":\"${escapeJson(n.noticeType)}\"")
            if (n.actionUrl != null) sb.append(",\"action\":\"${escapeJson(n.actionUrl)}\"")
            sb.append("}")
        }

        sb.append(",\"msg\":\"${escapeJson(e.message)}\"")
        if (e.exceptionType != null) sb.append(",\"exType\":\"${escapeJson(e.exceptionType)}\"")
        if (e.exceptionMessage != null) sb.append(",\"exMsg\":\"${escapeJson(e.exceptionMessage)}\"")
        sb.append("}")
        return sb.toString()
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
