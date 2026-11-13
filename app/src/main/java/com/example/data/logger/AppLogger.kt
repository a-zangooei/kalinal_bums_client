package com.example.data.logger

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AppLogger {

    private const val TAG = "AppLogger"
    private const val MAX_LOG_FILE_BYTES = 2 * 1024 * 1024L // 2 MB
    private const val PRUNE_TARGET_BYTES = 1024 * 1024L // Prune to 1 MB
    private const val MAX_MEMORY_ENTRIES = 500
    private const val MAX_SNAPSHOT_COUNT = 60
    private const val MAX_SNAPSHOT_TOTAL_BYTES = 8 * 1024 * 1024L // 8 MB

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logDir: File? = null
    private var logTextFile: File? = null
    private var logJsonFile: File? = null
    private var snapshotsDir: File? = null
    private var snapshotsIndexFile: File? = null

    private val lock = Any()
    private val snapshotSequence = AtomicInteger(0)
    private val entryIdGenerator = AtomicLong(0L)

    private val memoryLogList = mutableListOf<String>()
    private val memoryEntryList = mutableListOf<LogEntry>()
    private val memorySnapshotList = mutableListOf<PageSnapshotRecord>()

    private val _recentLogs = MutableStateFlow<List<String>>(emptyList())
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    private val _recentEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentEntries: StateFlow<List<LogEntry>> = _recentEntries.asStateFlow()

    private val _pageSnapshots = MutableStateFlow<List<PageSnapshotRecord>>(emptyList())
    val pageSnapshots: StateFlow<List<PageSnapshotRecord>> = _pageSnapshots.asStateFlow()

    @Volatile
    private var crashHandlerInstalled = false

    // Strictly generate genuine ISO-8601 UTC timestamp with millisecond precision
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    var currentMinLogLevel: LogLevel = LogLevel.DEBUG

    fun init(context: Context) {
        synchronized(lock) {
            if (logTextFile == null) {
                try {
                    val baseLogDir = File(context.filesDir, "logs")
                    if (!baseLogDir.exists()) baseLogDir.mkdirs()
                    logDir = baseLogDir

                    logTextFile = File(baseLogDir, "cullinan_events.log").apply {
                        if (!exists()) createNewFile()
                    }
                    logJsonFile = File(baseLogDir, "cullinan_events.jsonl").apply {
                        if (!exists()) createNewFile()
                    }

                    val snapDir = File(baseLogDir, "snapshots")
                    if (!snapDir.exists()) snapDir.mkdirs()
                    snapshotsDir = snapDir
                    snapshotsIndexFile = File(snapDir, "index.jsonl").apply {
                        if (!exists()) createNewFile()
                    }

                    loadInitialMemoryLogs()
                    loadInitialSnapshots()
                } catch (e: Exception) {
                    Log.e(TAG, "Initialization failed", e)
                }
            }
        }
    }

    /**
     * Installs an idempotent global UncaughtExceptionHandler that synchronously persists
     * full fatal crash diagnostics to disk before delegating to the default handler.
     */
    fun installCrashHandler(context: Context) {
        init(context)
        if (crashHandlerInstalled) return
        synchronized(lock) {
            if (crashHandlerInstalled) return
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    logCrash(thread.name, throwable)
                } catch (_: Throwable) {
                    // Never block default crash reporting
                }
                previousHandler?.uncaughtException(thread, throwable)
            }
            crashHandlerInstalled = true
            logInfo("AppLogger", "CrashHandlerInstalled", "Global uncaught exception handler installed")
        }
    }

    fun generateFlowId(prefix: String = "flw"): String {
        return "${prefix}_${UUID.randomUUID().toString().take(8)}"
    }

    fun generateRequestId(): String {
        return "req_${UUID.randomUUID().toString().take(8)}"
    }

    private fun loadInitialMemoryLogs() {
        val textFile = logTextFile
        val jsonFile = logJsonFile
        try {
            if (textFile != null && textFile.exists()) {
                val lines = textFile.readLines()
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

            if (jsonFile != null && jsonFile.exists()) {
                memoryEntryList.clear()
                val jsonLines = jsonFile.readLines().takeLast(MAX_MEMORY_ENTRIES)
                for (line in jsonLines) {
                    parseJsonlToEntry(line)?.let { memoryEntryList.add(it) }
                }
                _recentEntries.value = memoryEntryList.toList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read initial logs from file", e)
        }
    }

    private fun loadInitialSnapshots() {
        val snapDir = snapshotsDir ?: return
        val indexFile = snapshotsIndexFile ?: return
        try {
            memorySnapshotList.clear()
            var maxSeq = 0
            if (indexFile.exists()) {
                val indexLines = indexFile.readLines().takeLast(MAX_SNAPSHOT_COUNT)
                for (line in indexLines) {
                    if (line.isBlank()) continue
                    try {
                        val obj = JSONObject(line)
                        val seq = obj.optInt("seq", 0)
                        val fileName = obj.optString("file", "")
                        if (seq > maxSeq) maxSeq = seq
                        if (fileName.isBlank()) continue
                        val file = File(snapDir, fileName)
                        if (file.exists()) {
                            val raw = file.readText(Charsets.UTF_8)
                            memorySnapshotList.add(
                                PageSnapshotRecord(
                                    sequence = seq,
                                    fileName = fileName,
                                    timestamp = obj.optString("ts", ""),
                                    operation = obj.optString("op", ""),
                                    method = obj.optString("method", ""),
                                    url = obj.optString("url", ""),
                                    statusCode = obj.optInt("status", 0),
                                    contentType = obj.optString("contentType", "").ifBlank { null },
                                    flowId = obj.optString("flowId", "").ifBlank { null },
                                    requestId = obj.optString("reqId", "").ifBlank { null },
                                    sizeBytes = raw.toByteArray(Charsets.UTF_8).size,
                                    rawContent = raw
                                )
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            snapshotSequence.set(maxSeq)
            _pageSnapshots.value = memorySnapshotList.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load snapshots", e)
        }
    }

    /**
     * Captures a full, uncensored, untruncated server response (HTML, ASP.NET Delta, JSON, error page)
     * sequentially step-by-step and stores it on disk and in memory so log entries can reference it.
     */
    fun savePageSnapshot(
        operation: String,
        method: String,
        url: String,
        statusCode: Int,
        contentType: String?,
        rawContent: String,
        flowId: String? = null,
        requestId: String? = null
    ): PageSnapshotRecord? {
        if (rawContent.isEmpty()) return null
        return try {
            val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
            val seq = snapshotSequence.incrementAndGet()
            val safeOp = operation.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "Page" }
            val isHtml = rawContent.trimStart().startsWith("<") ||
                (contentType?.contains("html", ignoreCase = true) == true)
            val isJson = contentType?.contains("json", ignoreCase = true) == true ||
                rawContent.trimStart().startsWith("{") || rawContent.trimStart().startsWith("[")
            val ext = when {
                isHtml -> "html"
                isJson -> "json"
                else -> "txt"
            }
            val fileName = String.format(Locale.US, "step_%04d_%s_%d.%s", seq, safeOp, statusCode, ext)
            val sizeBytes = rawContent.toByteArray(Charsets.UTF_8).size

            val record = PageSnapshotRecord(
                sequence = seq,
                fileName = fileName,
                timestamp = timestamp,
                operation = operation,
                method = method,
                url = url,
                statusCode = statusCode,
                contentType = contentType,
                flowId = flowId,
                requestId = requestId,
                sizeBytes = sizeBytes,
                rawContent = rawContent
            )

            synchronized(lock) {
                snapshotsDir?.let { dir ->
                    if (!dir.exists()) dir.mkdirs()
                    File(dir, fileName).writeText(rawContent, Charsets.UTF_8)
                    val indexLine = buildSnapshotIndexLine(record)
                    snapshotsIndexFile?.appendText("$indexLine\n", Charsets.UTF_8)
                }

                memorySnapshotList.add(record)
                pruneSnapshotsLocked()
                _pageSnapshots.value = memorySnapshotList.toList()
            }

            record
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save page snapshot", e)
            null
        }
    }

    private fun pruneSnapshotsLocked() {
        while (memorySnapshotList.size > MAX_SNAPSHOT_COUNT ||
            memorySnapshotList.sumOf { it.sizeBytes.toLong() } > MAX_SNAPSHOT_TOTAL_BYTES
        ) {
            if (memorySnapshotList.isEmpty()) break
            val oldest = memorySnapshotList.removeAt(0)
            snapshotsDir?.let { dir ->
                val f = File(dir, oldest.fileName)
                if (f.exists()) f.delete()
            }
        }
        // Rewrite compact index on disk to match retained snapshots if index grew large
        val snapDir = snapshotsDir
        val indexFile = snapshotsIndexFile
        if (snapDir != null && indexFile != null && indexFile.length() > 128 * 1024L) {
            val content = memorySnapshotList.joinToString("\n") { buildSnapshotIndexLine(it) }
            indexFile.writeText(if (content.isEmpty()) "" else "$content\n", Charsets.UTF_8)
        }
    }

    fun getSnapshotByFileName(fileName: String): PageSnapshotRecord? {
        synchronized(lock) {
            val cached = memorySnapshotList.firstOrNull { it.fileName == fileName }
            if (cached != null) return cached
            val file = snapshotsDir?.let { File(it, fileName) }
            if (file != null && file.exists()) {
                val raw = file.readText(Charsets.UTF_8)
                return PageSnapshotRecord(
                    sequence = 0,
                    fileName = fileName,
                    timestamp = "",
                    operation = fileName,
                    method = "",
                    url = "",
                    statusCode = 0,
                    contentType = null,
                    flowId = null,
                    requestId = null,
                    sizeBytes = raw.toByteArray(Charsets.UTF_8).size,
                    rawContent = raw
                )
            }
            return null
        }
    }

    fun log(entry: LogEntry, flushImmediately: Boolean = false) {
        if (entry.level.ordinal < currentMinLogLevel.ordinal) return

        try {
            val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
            val assignedId = if (entry.id > 0L) entry.id else entryIdGenerator.incrementAndGet()
            val sanitized = LogSanitizer.sanitizeEntry(entry).copy(
                timestamp = timestamp,
                id = assignedId
            )

            val humanReadableText = buildHumanReadableLine(sanitized)
            val jsonlLine = buildJsonlLine(sanitized)

            synchronized(lock) {
                memoryLogList.add(humanReadableText)
                if (memoryLogList.size > MAX_MEMORY_ENTRIES) {
                    memoryLogList.removeAt(0)
                }
                _recentLogs.value = memoryLogList.toList()

                memoryEntryList.add(sanitized)
                if (memoryEntryList.size > MAX_MEMORY_ENTRIES) {
                    memoryEntryList.removeAt(0)
                }
                _recentEntries.value = memoryEntryList.toList()
            }

            if (flushImmediately) {
                writeLinesToDiskSync(humanReadableText, jsonlLine)
            } else {
                scope.launch {
                    writeLinesToDiskSync(humanReadableText, jsonlLine)
                }
            }
        } catch (e: Exception) {
            // Failure isolation: Logging failure must never throw into business logic
            Log.e(TAG, "Logging failed safely", e)
        }
    }

    private fun writeLinesToDiskSync(humanReadableText: String, jsonlLine: String) {
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

    fun logDebug(component: String, operation: String, message: String, flowId: String? = null) {
        log(
            LogEntry(
                timestamp = "",
                level = LogLevel.DEBUG,
                component = component,
                operation = operation,
                flowId = flowId,
                message = message
            )
        )
    }

    fun logInfo(component: String, operation: String, message: String, flowId: String? = null) {
        log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = component,
                operation = operation,
                flowId = flowId,
                message = message
            )
        )
    }

    fun logWarn(
        component: String,
        operation: String,
        message: String,
        throwable: Throwable? = null,
        flowId: String? = null,
        errorCategory: String? = null
    ) {
        log(
            LogEntry(
                timestamp = "",
                level = LogLevel.WARN,
                component = component,
                operation = operation,
                flowId = flowId,
                errorCategory = errorCategory,
                message = message,
                exceptionType = throwable?.javaClass?.simpleName,
                exceptionMessage = throwable?.message
            )
        )
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

    fun logError(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        flowId: String? = null,
        operation: String = "Error",
        errorCategory: String? = null
    ) {
        log(
            LogEntry(
                timestamp = "",
                level = LogLevel.ERROR,
                component = tag,
                operation = operation,
                flowId = flowId,
                errorCategory = errorCategory,
                message = message,
                exceptionType = throwable?.javaClass?.simpleName,
                exceptionMessage = throwable?.message,
                stackTrace = throwable?.stackTraceToString()?.take(4000)
            )
        )
    }

    fun logCrash(threadName: String, throwable: Throwable) {
        val stack = throwable.stackTraceToString().take(12000)
        log(
            entry = LogEntry(
                timestamp = "",
                level = LogLevel.FATAL,
                component = "CrashHandler",
                operation = "UncaughtException",
                errorCategory = "UNCAUGHT_CRASH",
                message = "Uncaught exception on thread '$threadName': ${throwable.message ?: throwable.javaClass.simpleName}",
                exceptionType = throwable.javaClass.name,
                exceptionMessage = throwable.message,
                stackTrace = stack
            ),
            flushImmediately = true
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
            h.finalUrl?.takeIf { it != h.url }?.let { sb.append(" (final: $it)") }
            h.redirectLocation?.let { sb.append(" [Location: $it]") }
            h.redirectCount?.takeIf { it > 0 }?.let { sb.append(" [Redirects=$it]") }
            if (h.isCompatibilitySsl) sb.append(" (SSL Fallback)")
            if (h.isNoRedirect) sb.append(" (No-Redirect)")
            h.formKeys?.takeIf { it.isNotEmpty() }?.let { keys ->
                sb.append("\n  FormKeys(${keys.size}): ${keys.joinToString(", ")}")
            }
            h.setCookieNames?.takeIf { it.isNotEmpty() }?.let { sc ->
                sb.append("\n  SetCookieKeys: ${sc.joinToString(", ")}")
            }
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
            c.cookieNames?.takeIf { it.isNotEmpty() }?.let { names ->
                sb.append(" Names=[${names.joinToString(",")}]")
            }
            if (c.expiredRemovedCount > 0) sb.append(" ExpiredPruned=${c.expiredRemovedCount}")
        }

        e.parser?.let { p ->
            sb.append("\n  Parser: Type=${p.pageType} HTML_len=${p.htmlLength} TablesDom=${p.rawTablesInDom} Meals=${p.parsedMealsCount} Skipped=${p.skippedRowsCount}")
            if (p.isFragment) sb.append(" (UpdatePanel Fragment)")
        }

        e.notice?.let { n ->
            sb.append("\n  Notice: Detected=${n.isNoticeDetected} Type=${n.noticeType ?: "None"} Ack=${n.isAcknowledged}")
        }

        e.snapshot?.let { s ->
            sb.append("\n  Snapshot: #${s.sequence} -> ${s.fileName} (${s.sizeBytes} bytes, type=${s.contentType ?: "text"})")
        }

        e.exceptionType?.let {
            sb.append("\n  Exception: $it - ${e.exceptionMessage ?: ""}")
        }

        e.stackTrace?.takeIf { it.isNotBlank() }?.let { stack ->
            sb.append("\n  StackTrace:\n${stack.prependIndent("    ")}")
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
            if (h.redirectLocation != null) sb.append(",\"redirectLoc\":\"${escapeJson(h.redirectLocation)}\"")
            if (h.userAgent != null) sb.append(",\"ua\":\"${escapeJson(h.userAgent)}\"")
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

        e.snapshot?.let { s ->
            sb.append(",\"snapshot\":{")
            sb.append("\"seq\":${s.sequence},")
            sb.append("\"file\":\"${escapeJson(s.fileName)}\",")
            sb.append("\"size\":${s.sizeBytes}")
            if (s.contentType != null) sb.append(",\"type\":\"${escapeJson(s.contentType)}\"")
            if (s.sha256Prefix != null) sb.append(",\"fp\":\"${escapeJson(s.sha256Prefix)}\"")
            sb.append("}")
        }

        sb.append(",\"msg\":\"${escapeJson(e.message)}\"")
        if (e.exceptionType != null) sb.append(",\"exType\":\"${escapeJson(e.exceptionType)}\"")
        if (e.exceptionMessage != null) sb.append(",\"exMsg\":\"${escapeJson(e.exceptionMessage)}\"")
        if (e.stackTrace != null) sb.append(",\"stack\":\"${escapeJson(e.stackTrace)}\"")
        sb.append("}")
        return sb.toString()
    }

    private fun buildSnapshotIndexLine(r: PageSnapshotRecord): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"seq\":${r.sequence},")
        sb.append("\"file\":\"${escapeJson(r.fileName)}\",")
        sb.append("\"ts\":\"${escapeJson(r.timestamp)}\",")
        sb.append("\"op\":\"${escapeJson(r.operation)}\",")
        sb.append("\"method\":\"${escapeJson(r.method)}\",")
        sb.append("\"url\":\"${escapeJson(r.url)}\",")
        sb.append("\"status\":${r.statusCode},")
        sb.append("\"size\":${r.sizeBytes}")
        if (r.contentType != null) sb.append(",\"contentType\":\"${escapeJson(r.contentType)}\"")
        if (r.flowId != null) sb.append(",\"flowId\":\"${escapeJson(r.flowId)}\"")
        if (r.requestId != null) sb.append(",\"reqId\":\"${escapeJson(r.requestId)}\"")
        sb.append("}")
        return sb.toString()
    }

    private fun parseJsonlToEntry(line: String): LogEntry? {
        if (line.isBlank()) return null
        return try {
            val obj = JSONObject(line)
            val level = try {
                LogLevel.valueOf(obj.optString("lvl", "INFO"))
            } catch (_: Exception) {
                LogLevel.INFO
            }
            val snapObj = obj.optJSONObject("snapshot")
            val snap = snapObj?.let {
                SnapshotDiagnostics(
                    sequence = it.optInt("seq", 0),
                    fileName = it.optString("file", ""),
                    contentType = it.optString("type", "").ifBlank { null },
                    sizeBytes = it.optInt("size", 0),
                    sha256Prefix = it.optString("fp", "").ifBlank { null }
                )
            }
            val httpObj = obj.optJSONObject("http")
            val httpDiag = httpObj?.let {
                HttpDiagnostics(
                    method = it.optString("method", ""),
                    url = it.optString("url", ""),
                    statusCode = if (it.has("status")) it.optInt("status") else null,
                    contentType = it.optString("contentType", "").ifBlank { null },
                    finalUrl = it.optString("finalUrl", "").ifBlank { null },
                    redirectLocation = it.optString("redirectLoc", "").ifBlank { null },
                    isCompatibilitySsl = it.optBoolean("sslFallback", false),
                    isNoRedirect = it.optBoolean("noRedirect", false)
                )
            }
            LogEntry(
                timestamp = obj.optString("ts", ""),
                level = level,
                component = obj.optString("comp", "System"),
                operation = obj.optString("op", ""),
                flowId = obj.optString("flowId", "").ifBlank { null },
                requestId = obj.optString("reqId", "").ifBlank { null },
                durationMs = if (obj.has("durMs")) obj.optLong("durMs") else null,
                http = httpDiag,
                snapshot = snap,
                message = obj.optString("msg", ""),
                errorCategory = obj.optString("errCat", "").ifBlank { null },
                exceptionType = obj.optString("exType", "").ifBlank { null },
                exceptionMessage = obj.optString("exMsg", "").ifBlank { null },
                stackTrace = obj.optString("stack", "").ifBlank { null },
                id = entryIdGenerator.incrementAndGet()
            )
        } catch (_: Exception) {
            null
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
            memoryEntryList.clear()
            memorySnapshotList.clear()
            snapshotSequence.set(0)
            _recentLogs.value = emptyList()
            _recentEntries.value = emptyList()
            _pageSnapshots.value = emptyList()
            try {
                logTextFile?.writeText("", Charsets.UTF_8)
                logJsonFile?.writeText("", Charsets.UTF_8)
                snapshotsIndexFile?.writeText("", Charsets.UTF_8)
                snapshotsDir?.listFiles()?.forEach { file ->
                    if (file.name != "index.jsonl") file.delete()
                }
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
                val text = logJsonFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8)
                if (!text.isNullOrBlank()) {
                    LogSanitizer.sanitizeText(text)
                } else {
                    memoryEntryList.joinToString("\n") { buildJsonlLine(it) }
                }
            } catch (_: Exception) {
                memoryEntryList.joinToString("\n") { buildJsonlLine(it) }
            }
        }
    }

    /**
     * Packages logs, JSONL structured events, device/app manifest, and all step-by-step
     * raw HTML/Delta page snapshots into a single ZIP file so exports never fail Android's
     * Binder 1MB limit.
     */
    fun exportDiagnosticsZip(
        destFile: File,
        appVersion: String = "unknown",
        versionCode: Int = 0,
        sslCompatibilityActive: Boolean = false
    ): Boolean {
        return try {
            destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            val logsText = getExportableText()
            val jsonlText = getExportableJsonl()
            val snapshotsCopy: List<PageSnapshotRecord>
            synchronized(lock) {
                snapshotsCopy = memorySnapshotList.toList()
            }

            val manifest = buildString {
                appendLine("=== Kalinan Diagnostic Bundle ===")
                appendLine("ExportedAtUTC: ${synchronized(dateFormat) { dateFormat.format(Date()) }}")
                appendLine("AppVersion: $appVersion (build #$versionCode)")
                appendLine("Device: ${Build.MANUFACTURER ?: "JVM"} ${Build.MODEL ?: "Test"} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("SSLCompatibilityMode: $sslCompatibilityActive")
                appendLine("SnapshotsCount: ${snapshotsCopy.size}")
                appendLine()
                appendLine("--- Step-by-Step Captured Server Responses (Uncensored) ---")
                for (s in snapshotsCopy) {
                    appendLine("#${"%04d".format(Locale.US, s.sequence)} | ${s.fileName} | ${s.method} ${s.statusCode} | ${s.url} | ${s.sizeBytes} bytes | flow=${s.flowId ?: "-"} req=${s.requestId ?: "-"}")
                }
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(destFile))).use { zos ->
                zos.putNextEntry(ZipEntry("manifest.txt"))
                zos.write(manifest.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("cullinan_events.log"))
                zos.write(logsText.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("cullinan_events.jsonl"))
                zos.write(jsonlText.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val indexJsonl = snapshotsCopy.joinToString("\n") { buildSnapshotIndexLine(it) }
                zos.putNextEntry(ZipEntry("snapshots/index.jsonl"))
                zos.write(indexJsonl.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                for (s in snapshotsCopy) {
                    zos.putNextEntry(ZipEntry("snapshots/${s.fileName}"))
                    zos.write(s.rawContent.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export diagnostics zip", e)
            false
        }
    }

    fun sanitize(raw: String): String = LogSanitizer.sanitizeText(raw)
}
