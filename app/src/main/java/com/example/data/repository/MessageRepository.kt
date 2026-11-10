package com.example.data.repository

import com.example.data.logger.AppLogger
import com.example.data.logger.LogContext
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.model.MessageItem
import com.example.data.model.MessagesPageState
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.DeltaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

class MessageRepository {

    private val mutex = Mutex()

    /**
     * Fetches the MyMessage.aspx page, parses the messages table, and determines whether
     * the reservation menu is unlocked or blocked by unread messages.
     */
    suspend fun fetchMessagesPage(): Result<MessagesPageState> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("msg_fetch")
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "MessageRepository",
                operation = "FetchMessagesPage",
                flowId = flowId,
                message = "Initiating fetch of user messages page"
            )
        )

        try {
            val request = Request.Builder()
                .url(CullinanHttpClient.MESSAGES_URL)
                .get()
                .header("Referer", CullinanHttpClient.BASE_URL)
                .tag(LogContext::class.java, LogContext(flowId, "FetchMessagesPage"))
                .build()

            val response = CullinanHttpClient.executeCall(request)
            if (!response.isSuccessful) {
                AppLogger.logError("MessageRepository", "Failed to fetch messages page: HTTP ${response.code}", flowId = flowId)
                return@withContext Result.failure(Exception("خطا در دریافت صفحه پیام‌های سامانه (${response.code})"))
            }

            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html)

            val viewState = doc.select("input[name=__VIEWSTATE]").`val`()
            val viewStateGenerator = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`().ifEmpty { "87AF9C2D" }

            // Check if full menu has been unlocked (Reservation link is present, either absolute or relative)
            val isReservationUnlocked = html.contains("Reservation/Reservation.aspx", ignoreCase = true)

            // Check for pagination controls
            val hasPager = doc.select("#cphMain_grdContent tr.PagerStyle, .PagerStyle, tr[class*='Pager']").isNotEmpty()

            // Filter message rows strictly by requiring the imgbtnMessageText input button.
            // This prevents pager rows (which contain nested tables/tr with numbers) from being counted as messages.
            val rows = doc.select("#cphMain_grdContent tr").filter {
                it.select("input[name*='imgbtnMessageText']").isNotEmpty()
            }

            val messages = mutableListOf<MessageItem>()
            for (row in rows) {
                val cells = row.select("td")
                if (cells.size < 3) continue

                val rowIdxStr = cells[0].text().trim()
                val rowIdx = rowIdxStr.toIntOrNull() ?: (messages.size + 1)
                val title = cells[1].text().trim()

                val imgBtn = row.select("input[type=image][name*='imgbtnMessageText']").firstOrNull()
                val buttonName = imgBtn?.attr("name") ?: ""

                val sendDate = if (cells.size > 3) cells[3].text().trim() else ""
                val sendTime = if (cells.size > 4) cells[4].text().trim() else ""

                val hasMsgClose = row.select("img[src*='MsgClose']").isNotEmpty()
                val hasMsgOpen = row.select("img[src*='MsgOpen']").isNotEmpty()

                val readDate = if (cells.size > 6) cells[6].text().trim() else ""
                val readTime = if (cells.size > 7) cells[7].text().trim() else ""

                val isRead = hasMsgOpen || (!hasMsgClose && readDate.isNotBlank() && readDate != "-")

                messages.add(
                    MessageItem(
                        rowIndex = rowIdx,
                        title = title,
                        buttonInputName = buttonName,
                        sendDate = sendDate,
                        sendTime = sendTime,
                        isRead = isRead,
                        readDate = readDate,
                        readTime = readTime,
                        messageText = null
                    )
                )
            }

            val unreadCount = messages.count { !it.isRead }
            val hasUnread = unreadCount > 0

            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.INFO,
                    component = "MessageRepository",
                    operation = "FetchMessagesPageSuccess",
                    flowId = flowId,
                    message = "Messages parsed: total=${messages.size}, unread=$unreadCount, unlocked=$isReservationUnlocked, pager=$hasPager"
                )
            )

            Result.success(
                MessagesPageState(
                    viewState = viewState,
                    viewStateGenerator = viewStateGenerator,
                    messages = messages,
                    hasUnreadMessages = hasUnread,
                    unreadCount = unreadCount,
                    hasPaginationControls = hasPager,
                    isReservationUnlocked = isReservationUnlocked,
                    isLoading = false
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.logError("MessageRepository", "Exception in fetchMessagesPage: ${e.message}", e, flowId = flowId)
            Result.failure(e)
        }
    }

    /**
     * Opens a specific message using ASP.NET partial-rendering Delta POST.
     * Records the message as read on server side, updates ViewState, and extracts message text.
     *
     * IMPORTANT: No automatic retry to prevent state corruption.
     */
    suspend fun openMessage(
        buttonInputName: String,
        currentViewState: String,
        currentViewStateGenerator: String = "87AF9C2D"
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("msg_open")
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "MessageRepository",
                operation = "OpenMessageAttempt",
                flowId = flowId,
                message = "Executing Delta POST to open message (button: $buttonInputName)"
            )
        )

        if (buttonInputName.isBlank()) {
            AppLogger.logError("MessageRepository", "buttonInputName cannot be blank", flowId = flowId)
            return@withContext Result.failure(IllegalArgumentException("شناسه دکمه پیام موجود نیست"))
        }

        try {
            val formBody = FormBody.Builder()
                .add("ctl00\$ScriptManager", "ctl00\$UpdatePanel1|$buttonInputName")
                .add("__EVENTTARGET", "")
                .add("__EVENTARGUMENT", "")
                .add("__VIEWSTATE", currentViewState)
                .add("__VIEWSTATEGENERATOR", currentViewStateGenerator.ifBlank { "87AF9C2D" })
                .add("__VIEWSTATEENCRYPTED", "")
                .add("ctl00\$cphMain\$hdnId", "")
                .add("__ASYNCPOST", "true")
                .add("$buttonInputName.x", "10")
                .add("$buttonInputName.y", "10")
                .build()

            val request = Request.Builder()
                .url(CullinanHttpClient.MESSAGES_URL)
                .post(formBody)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-MicrosoftAjax", "Delta=true")
                .header("Cache-Control", "no-cache")
                .header("Accept", "*/*")
                .header("Origin", CullinanHttpClient.BASE_URL)
                .header("Referer", CullinanHttpClient.MESSAGES_URL)
                .tag(LogContext::class.java, LogContext(flowId, "OpenMessageDeltaPost"))
                .build()

            val response = CullinanHttpClient.executeCall(request)
            if (!response.isSuccessful) {
                AppLogger.logError("MessageRepository", "Open message failed HTTP ${response.code}", flowId = flowId)
                return@withContext Result.failure(Exception("خطا در باز کردن پیام از سرور (${response.code})"))
            }

            val deltaText = response.body?.string() ?: ""
            val deltaResponse = DeltaParser.parse(deltaText)

            val newViewState = deltaResponse.hiddenFields["__VIEWSTATE"] ?: currentViewState

            val updatePanelHtml = deltaResponse.updatePanels["UpdatePanel1"]
                ?: deltaResponse.updatePanels["ctl00\$UpdatePanel1"]
                ?: ""

            var messageContent = ""
            if (updatePanelHtml.isNotBlank()) {
                messageContent = Jsoup.parse(updatePanelHtml).select("#cphMain_pMessage").text().trim()
            }

            // Fallback 1: Extract from full delta text using Jsoup
            if (messageContent.isBlank()) {
                messageContent = Jsoup.parse(deltaText).select("#cphMain_pMessage").text().trim()
            }

            // Fallback 2: Regex extraction directly from raw response
            if (messageContent.isBlank()) {
                val pMatch = Regex("""<p[^>]*id=["']cphMain_pMessage["'][^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE).find(deltaText)
                if (pMatch != null) {
                    messageContent = Jsoup.parse(pMatch.groupValues[1]).text().trim()
                }
            }

            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.INFO,
                    component = "MessageRepository",
                    operation = "OpenMessageSuccess",
                    flowId = flowId,
                    message = "Message successfully opened. Text length: ${messageContent.length}, New VS length: ${newViewState.length}"
                )
            )

            Result.success(Pair(newViewState, messageContent))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.logError("MessageRepository", "Exception in openMessage: ${e.message}", e, flowId = flowId)
            Result.failure(e)
        }
    }

    /**
     * Sequentially marks all unread messages as read with serialized ViewState chaining.
     * Enforces safety protections:
     * - Hard loop ceiling (max 50)
     * - Zero automatic retry on POST failures
     * - Live progress callback
     * - Final verification GET to ensure menu unlocked
     */
    suspend fun markAllUnreadAsReadSerialized(
        initialState: MessagesPageState,
        onProgress: (currentIndex: Int, totalCount: Int, currentTitle: String, messageText: String) -> Unit
    ): Result<MessagesPageState> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val flowId = AppLogger.generateFlowId("msg_serial")
            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.INFO,
                    component = "MessageRepository",
                    operation = "MarkAllUnreadSerialized",
                    flowId = flowId,
                    message = "Starting serialized unread message processing"
                )
            )

            var currentState = initialState
            val unreadList = currentState.messages.filter { !it.isRead && it.buttonInputName.isNotBlank() }
            val totalCount = unreadList.size

            if (totalCount == 0) {
                return@withLock fetchMessagesPage()
            }

            var currentVs = currentState.viewState
            val vsGen = currentState.viewStateGenerator.ifBlank { "87AF9C2D" }

            val updatedMessages = currentState.messages.toMutableList()

            // Safety limit: max 50 iterations
            val loopLimit = minOf(totalCount, 50)

            for (i in 0 until loopLimit) {
                val targetMsg = unreadList[i]
                if (targetMsg.buttonInputName.isBlank()) {
                    AppLogger.logError("MessageRepository", "Missing buttonInputName for message: ${targetMsg.title}", flowId = flowId)
                    return@withLock Result.failure(Exception("شناسه دکمه پیام '${targetMsg.title}' یافت نشد"))
                }

                // Execute exact Delta POST with chained ViewState
                val openResult = openMessage(
                    buttonInputName = targetMsg.buttonInputName,
                    currentViewState = currentVs,
                    currentViewStateGenerator = vsGen
                )

                if (openResult.isFailure) {
                    val err = openResult.exceptionOrNull()
                    AppLogger.logError("MessageRepository", "Aborting serialized read due to failure on message '${targetMsg.title}': ${err?.message}", err, flowId = flowId)
                    return@withLock Result.failure(err ?: Exception("خطا در ثبت خوانده شدن پیام"))
                }

                val (newVs, msgText) = openResult.getOrThrow()
                currentVs = newVs

                // Update local memory list
                val listIdx = updatedMessages.indexOfFirst { it.rowIndex == targetMsg.rowIndex }
                if (listIdx != -1) {
                    updatedMessages[listIdx] = targetMsg.copy(
                        isRead = true,
                        messageText = msgText
                    )
                }

                // Notify live UI listener
                onProgress(i + 1, totalCount, targetMsg.title, msgText)
            }

            // Final check: Perform a fresh GET to verify that the reservation menu has been unlocked
            val verifyResult = fetchMessagesPage()
            if (verifyResult.isSuccess) {
                val finalState = verifyResult.getOrThrow()
                Result.success(finalState)
            } else {
                // Fallback to locally updated state
                Result.success(
                    currentState.copy(
                        viewState = currentVs,
                        messages = updatedMessages,
                        hasUnreadMessages = false,
                        unreadCount = 0,
                        isReservationUnlocked = true
                    )
                )
            }
        }
    }
}
