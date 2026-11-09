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
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object AppLogger {

    private const val TAG = "AppLogger"
    private const val MAX_LOG_SIZE_BYTES = 3 * 1024 * 1024L // 3 MB
    private const val PRUNE_TARGET_BYTES = 1024 * 1024L // Prune to ~1 MB on overflow
    private const val MAX_MEMORY_ENTRIES = 150

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logTextFile: File? = null
    private var logJsonFile: File? = null

    private val lock = Any()
    private val memoryLogList = mutableListOf<String>()

    private val _recentLogs = MutableStateFlow<List<String>>(emptyList())
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    var currentMinLogLevel: LogLevel = LogLevel.DEBUG

    fun init(context: Context) {
        synchronized(lock) {
            if (logTextFile == null) {
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
            val tail = if (lines.size > MAX_MEMORY_ENTRIES) lines.takeLast(MAX_MEMORY_ENTRIES) else lines
            memoryLogList.clear()
            memoryLogList.addAll(tail)
            _recentLogs.value = memoryLogList.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load initial memory logs", e)
        }
    }

    fun log(entry: LogEntry) {
        if (entry.level.ordinal < currentMinLogLevel.ordinal) return

        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val cleanEntry = entry.copy(
            timestamp = timestamp,
            message = LogSanitizer.sanitizeText(entry.message)
        )

        val humanReadableText = buildHumanReadableLine(cleanEntry)
        val jsonlLine = buildJsonlLine(cleanEntry)

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
        sb.append("[${e.timestamp}] [${e.level}] [${e.component}]${flowStr}${reqStr}: ${e.message}${durStr}")

        e.http?.let { h ->
            sb.append("\n  HTTP: ${h.method} ${LogSanitizer.sanitizeText(h.url)}")
            h.statusCode?.let { sb.append(" -> $it") }
            if (h.isCompatibilitySsl) sb.append(" (SSL Fallback)")
        }

        e.aspnet?.let { a ->
            sb.append("\n  ASP.NET: ViewState len=${a.viewStateLength ?: 0} (fp=${a.viewStateFingerprint ?: "N/A"})")
            if (a.hasEventValidation) sb.append(" EV_len=${a.eventValidationLength ?: 0}")
            a.eventTarget?.let { sb.append(" Target=$it") }
        }

        e.cookies?.let { c ->
            sb.append("\n  Cookies: Action=${c.action} Count=${c.cookieCount} SessionFP=${c.sessionFingerprint ?: "NONE"}")
        }

        e.parser?.let { p ->
            sb.append("\n  Parser: Type=${p.pageType} HTML_len=${p.htmlLength} Meals=${p.parsedMealsCount} Tables=${p.tablesFound}")
        }

        e.exceptionType?.let {
            sb.append("\n  Exception: $it - ${e.exceptionMessage ?: ""}")
        }

        return sb.toString()
    }

    private fun buildJsonlLine(e: LogEntry): String {
        // Escape special chars for manual clean JSON serialization without heavy DI
        fun escape(s: String?) = s?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.replace("\n", "\\n")?.replace("\r", "\\r") ?: ""

        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"ts\":\"${e.timestamp}\",")
        sb.append("\"lvl\":\"${e.level}\",")
        sb.append("\"comp\":\"${escape(e.component)}\",")
        sb.append("\"op\":\"${escape(e.operation)}\",")
        if (e.flowId != null) sb.append("\"flowId\":\"${escape(e.flowId)}\",")
        if (e.requestId != null) sb.append("\"reqId\":\"${escape(e.requestId)}\",")
        if (e.durationMs != null) sb.append("\"durMs\":${e.durationMs},")

        e.http?.let { h ->
            sb.append("\"http\":{")
            sb.append("\"method\":\"${h.method}\",")
            sb.append("\"url\":\"${escape(LogSanitizer.sanitizeText(h.url))}\"")
            if (h.statusCode != null) sb.append(",\"status\":${h.statusCode}")
            if (h.isCompatibilitySsl) sb.append(",\"sslFallback\":true")
            sb.append("},")
        }

        e.aspnet?.let { a ->
            sb.append("\"aspnet\":{")
            sb.append("\"vsLen\":${a.viewStateLength ?: 0}")
            if (a.viewStateFingerprint != null) sb.append(",\"vsFp\":\"${a.viewStateFingerprint}\"")
            if (a.hasEventValidation) sb.append(",\"hasEv\":true")
            if (a.eventTarget != null) sb.append(",\"target\":\"${escape(a.eventTarget)}\"")
            sb.append("},")
        }

        e.cookies?.let { c ->
            sb.append("\"cookies\":{")
            sb.append("\"act\":\"${c.action}\",")
            sb.append("\"cnt\":${c.cookieCount}")
            if (c.sessionFingerprint != null) sb.append(",\"sessionFp\":\"${c.sessionFingerprint}\"")
            sb.append("},")
        }

        e.parser?.let { p ->
            sb.append("\"parser\":{")
            sb.append("\"pageType\":\"${p.pageType}\",")
            sb.append("\"htmlLen\":${p.htmlLength},")
            sb.append("\"meals\":${p.parsedMealsCount},")
            sb.append("\"tables\":${p.tablesFound}")
            sb.append("},")
        }

        sb.append("\"msg\":\"${escape(e.message)}\"")
        if (e.exceptionType != null) {
            sb.append(",\"exType\":\"${escape(e.exceptionType)}\",\"exMsg\":\"${escape(e.exceptionMessage)}\"")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun checkAndPruneFile(file: File) {
        if (!file.exists()) return
        val currentSize = file.length()
        if (currentSize > MAX_LOG_SIZE_BYTES) {
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
                if (!text.isNullOrBlank()) text else memoryLogList.joinToString("\n\n")
            } catch (_: Exception) {
                memoryLogList.joinToString("\n\n")
            }
        }
    }

    fun getExportableJsonl(): String {
        synchronized(lock) {
            return try {
                logJsonFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: ""
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun sanitize(raw: String): String = LogSanitizer.sanitizeText(raw)
}
