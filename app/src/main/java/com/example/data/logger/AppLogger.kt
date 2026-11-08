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

object AppLogger {

    private const val TAG = "AppLogger"
    private const val MAX_LOG_SIZE_BYTES = 3 * 1024 * 1024L // 3 MB
    private const val PRUNE_TARGET_BYTES = 1024 * 1024L // Prune to ~1 MB on overflow
    private const val MAX_MEMORY_ENTRIES = 150

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logFile: File? = null

    private val lock = Any()
    private val memoryLogList = mutableListOf<String>()

    private val _recentLogs = MutableStateFlow<List<String>>(emptyList())
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        synchronized(lock) {
            if (logFile == null) {
                val logDir = File(context.filesDir, "logs")
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                logFile = File(logDir, "cullinan_events.log")
                if (!logFile!!.exists()) {
                    logFile!!.createNewFile()
                }
                loadInitialMemoryLogs()
            }
        }
    }

    private fun loadInitialMemoryLogs() {
        val file = logFile ?: return
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

    fun logAction(action: String, details: String = "") {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val msg = if (details.isNotBlank()) "[$timestamp] [ACTION] $action - $details" else "[$timestamp] [ACTION] $action"
        appendLog(msg)
    }

    fun logHttpRequest(method: String, url: String, headers: Map<String, String>, bodyString: String?) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val cleanUrl = sanitize(url)
        val cleanHeaders = sanitizeHeaders(headers)
        val cleanBody = bodyString?.let { sanitize(it) } ?: ""

        val sb = StringBuilder()
        sb.append("[$timestamp] [HTTP REQ] $method $cleanUrl\n")
        if (cleanHeaders.isNotBlank()) {
            sb.append("  Headers: $cleanHeaders\n")
        }
        if (cleanBody.isNotBlank()) {
            val preview = if (cleanBody.length > 500) cleanBody.take(500) + "... [truncated ${cleanBody.length} chars]" else cleanBody
            sb.append("  Body: $preview\n")
        }
        appendLog(sb.toString().trimEnd())
    }

    fun logHttpResponse(code: Int, url: String, durationMs: Long, bodyPreview: String? = null) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val cleanUrl = sanitize(url)
        val statusEmoji = if (code in 200..299) "SUCCESS" else if (code in 300..399) "REDIRECT" else "ERROR"

        val sb = StringBuilder()
        sb.append("[$timestamp] [HTTP RESP] $code $statusEmoji ($durationMs ms) - $cleanUrl\n")
        if (!bodyPreview.isNullOrBlank()) {
            val sanitizedPreview = sanitize(bodyPreview)
            val preview = if (sanitizedPreview.length > 400) sanitizedPreview.take(400) + "... [truncated]" else sanitizedPreview
            sb.append("  Preview: $preview\n")
        }
        appendLog(sb.toString().trimEnd())
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val errStr = throwable?.let { ": ${it.javaClass.simpleName} - ${it.message}" } ?: ""
        appendLog("[$timestamp] [ERROR] [$tag] $message$errStr")
    }

    private fun appendLog(line: String) {
        // Update memory list immediately on main thread / stateflow
        synchronized(lock) {
            memoryLogList.add(line)
            if (memoryLogList.size > MAX_MEMORY_ENTRIES) {
                memoryLogList.removeAt(0)
            }
            _recentLogs.value = memoryLogList.toList()
        }

        // Persist to disk asynchronously
        scope.launch {
            synchronized(lock) {
                val file = logFile ?: return@synchronized
                try {
                    file.appendText("$line\n\n", Charsets.UTF_8)
                    checkAndPruneFile(file)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write log to disk", e)
                }
            }
        }
    }

    private fun checkAndPruneFile(file: File) {
        if (!file.exists()) return
        val currentSize = file.length()
        if (currentSize > MAX_LOG_SIZE_BYTES) {
            try {
                // Prune oldest half of the file
                val skipBytes = currentSize - PRUNE_TARGET_BYTES
                val raf = RandomAccessFile(file, "r")
                raf.seek(skipBytes)
                // Read until newline to start on clean record boundary
                raf.readLine()
                val remainingBytes = ByteArray((currentSize - raf.filePointer).toInt())
                raf.readFully(remainingBytes)
                raf.close()

                file.writeBytes(remainingBytes)
                Log.i(TAG, "Pruned log file from $currentSize bytes to ${file.length()} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prune log file", e)
            }
        }
    }

    fun clearLogs() {
        synchronized(lock) {
            memoryLogList.clear()
            _recentLogs.value = emptyList()
            try {
                logFile?.writeText("", Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear log file", e)
            }
        }
    }

    fun getLogFile(): File? = logFile

    fun getExportableText(): String {
        synchronized(lock) {
            return try {
                val text = logFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8)
                if (!text.isNullOrBlank()) text else memoryLogList.joinToString("\n\n")
            } catch (_: Exception) {
                memoryLogList.joinToString("\n\n")
            }
        }
    }

    /**
     * Sanitizes sensitive information: passwords, national IDs, RSA private keys, and session cookies.
     */
    fun sanitize(raw: String): String {
        val base = raw
            // Mask Passwords
            .replace(Regex("(txtPassword|txtPasswordPlain)=([^&]*)", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            // Mask Usernames (National ID / Student ID)
            .replace(Regex("(txtUsername|txtUsernamePlain)=([^&]*)", RegexOption.IGNORE_CASE), "$1=[REDACTED_USER]")
            // Mask ASP.NET Session Cookie
            .replace(Regex("ASP\\.NET_SessionId=([^;,\\s&]+)", RegexOption.IGNORE_CASE), "ASP.NET_SessionId=***")
            // Mask Public Key in form body to keep log readable
            .replace(Regex("(publicKeytxt=)([^&]*)", RegexOption.IGNORE_CASE), "$1[PEM_KEY_TRUNCATED]")

        return Regex("(__VIEWSTATE=)([^&]{40,})").replace(base) { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            "${key}[VIEWSTATE_LENGTH_${value.length}]"
        }
    }

    private fun sanitizeHeaders(headers: Map<String, String>): String {
        return headers.entries.joinToString(", ") { (k, v) ->
            val cleanValue = if (k.equals("Cookie", ignoreCase = true) || k.equals("Set-Cookie", ignoreCase = true)) {
                v.replace(Regex("ASP\\.NET_SessionId=([^;,\\s]+)", RegexOption.IGNORE_CASE), "ASP.NET_SessionId=***")
            } else {
                v
            }
            "$k=$cleanValue"
        }
    }
}
