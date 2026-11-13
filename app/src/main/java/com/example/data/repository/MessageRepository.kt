package com.example.data.repository

import com.example.data.logger.AppLogger
import com.example.data.logger.LogContext
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
import com.example.data.model.MessageError
import com.example.data.model.MessageItem
import com.example.data.model.MessageOpenResult
import com.example.data.model.MessagesPageState
import com.example.data.model.WebFormsState
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

            var isSuccessful = false
            var responseCode = 0
            var html = ""
            CullinanHttpClient.executeCall(request).use { response ->
                isSuccessful = response.isSuccessful
                responseCode = response.code
                if (isSuccessful) {
                    html = response.body?.string() ?: ""
                }
            }
            if (!isSuccessful) {
                AppLogger.logError("MessageRepository", "Failed to fetch messages page: HTTP $responseCode", flowId = flowId)
                return@withContext Result.failure(Exception("خطا در دریافت صفحه پیام‌های سامانه ($responseCode)"))
            }

            val doc = Jsoup.parse(html)

            val viewState = doc.select("input[name=__VIEWSTATE]").`val`()
            val viewStateGenerator = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`().ifEmpty { "87AF9C2D" }
            val eventValidation = doc.select("input[name=__EVENTVALIDATION]").`val`()

            // In ASP.NET WebForms, the default ScriptManager UniqueID is usually ctl00$ScriptManager
            val scriptManagerInput = doc.select("input[name*='ScriptManager']").firstOrNull { input ->
                val name = input.attr("name")
                name.isNotBlank() &&
                    !name.endsWith("_HiddenField", ignoreCase = true) &&
                    !name.endsWith("_TSM", ignoreCase = true) &&
                    !name.contains("HiddenField", ignoreCase = true)
            }
            val scriptManagerName = when (val rawSmName = scriptManagerInput?.attr("name")?.takeIf { it.isNotBlank() }) {
                null -> {
                    val hiddenSm = doc.select("input[name*='ScriptManager']").firstOrNull()?.attr("name") ?: ""
                    if (hiddenSm.contains("ctl00") && hiddenSm.endsWith("_HiddenField", ignoreCase = true)) {
                        hiddenSm.removeSuffix("_HiddenField").replace('_', '$')
                    } else {
                        "ctl00\$ScriptManager"
                    }
                }
                else -> rawSmName
            }

            // UpdatePanel UniqueID: Check for UpdatePanel div
            val updatePanelDiv = doc.select("div[id$='UpdatePanel1'], div[id*='UpdatePanel']").firstOrNull { div ->
                val id = div.attr("id")
                id.isNotBlank() && !id.contains("UpdateProgress", ignoreCase = true)
            }
            val updatePanelHtmlId = updatePanelDiv?.attr("id")
            val updatePanelName = when {
                updatePanelHtmlId != null && updatePanelHtmlId.contains("ctl00") -> updatePanelHtmlId.replace('_', '$')
                updatePanelHtmlId != null && updatePanelHtmlId.isNotEmpty() -> "ctl00\$$updatePanelHtmlId"
                else -> "ctl00\$UpdatePanel1"
            }

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

            if (viewState.isBlank() || (messages.isEmpty() && doc.select("#cphMain_grdContent").isEmpty())) {
                AppLogger.logWarn(
                    component = "MessageRepository",
                    operation = "FetchMessagesAnomalousDom",
                    message = "Anomalous MyMessage DOM (VS len=${viewState.length}, grdPresent=${doc.select("#cphMain_grdContent").isNotEmpty()}, rows=${rows.size}, parsed=${messages.size})",
                    flowId = flowId,
                    errorCategory = "MESSAGES_DOM_ANOMALY"
                )
            }

            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.INFO,
                    component = "MessageRepository",
                    operation = "FetchMessagesPageSuccess",
                    flowId = flowId,
                    message = "Messages parsed: total=${messages.size}, unread=$unreadCount, unlocked=$isReservationUnlocked, pager=$hasPager, SM='$scriptManagerName', UP='$updatePanelName'"
                )
            )

            Result.success(
                MessagesPageState(
                    viewState = viewState,
                    viewStateGenerator = viewStateGenerator,
                    eventValidation = eventValidation,
                    scriptManagerName = scriptManagerName,
                    updatePanelName = updatePanelName,
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
     * Records the message as read on server side, updates WebForms state, and extracts message text.
     *
     * IMPORTANT: No automatic retry to prevent state corruption.
     */
    suspend fun openMessage(
        buttonInputName: String,
        state: WebFormsState,
        scriptManagerName: String = "ctl00\$ScriptManager",
        updatePanelName: String = "ctl00\$UpdatePanel1"
    ): MessageOpenResult = withContext(Dispatchers.IO) {
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
            return@withContext MessageOpenResult.Failure(
                MessageError.ServerError(400, "شناسه دکمه پیام موجود نیست")
            )
        }

        try {
            val formBuilder = FormBody.Builder()
                .add(scriptManagerName, "$updatePanelName|$buttonInputName")
                .add("__EVENTTARGET", "")
                .add("__EVENTARGUMENT", "")
                .add("__VIEWSTATE", state.viewState)
                .add("__VIEWSTATEGENERATOR", state.viewStateGenerator.ifBlank { "87AF9C2D" })
                .add("__VIEWSTATEENCRYPTED", "")

            if (!state.eventValidation.isNullOrBlank()) {
                formBuilder.add("__EVENTVALIDATION", state.eventValidation)
            }

            formBuilder.add("ctl00\$cphMain\$hdnId", "")
                .add("__ASYNCPOST", "true")
                .add("$buttonInputName.x", "10")
                .add("$buttonInputName.y", "10")

            val request = Request.Builder()
                .url(CullinanHttpClient.MESSAGES_URL)
                .post(formBuilder.build())
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-MicrosoftAjax", "Delta=true")
                .header("Cache-Control", "no-cache")
                .header("Accept", "*/*")
                .header("Origin", CullinanHttpClient.BASE_URL)
                .header("Referer", CullinanHttpClient.MESSAGES_URL)
                .tag(LogContext::class.java, LogContext(flowId, "OpenMessageDeltaPost"))
                .build()

            var isSuccessful = false
            var responseCode = 0
            var deltaText = ""
            CullinanHttpClient.executeCall(request).use { response ->
                isSuccessful = response.isSuccessful
                responseCode = response.code
                if (isSuccessful) {
                    deltaText = response.body?.string() ?: ""
                }
            }
            if (!isSuccessful) {
                AppLogger.logError("MessageRepository", "Open message failed HTTP $responseCode", flowId = flowId)
                return@withContext MessageOpenResult.Failure(
                    MessageError.ServerError(responseCode, "پاسخ ناموفق از سرور با کد $responseCode")
                )
            }

            // Strict check 1: Check for full HTML page (redirect or error) instead of delta
            val isFullHtml = (deltaText.contains("<!DOCTYPE", ignoreCase = true) || deltaText.contains("<html", ignoreCase = true)) &&
                !deltaText.contains("|updatePanel|") && !deltaText.contains("|hiddenField|")

            if (isFullHtml) {
                if (deltaText.contains("Login.aspx", ignoreCase = true) || deltaText.contains("txtUsernamePlain")) {
                    AppLogger.logError("MessageRepository", "Session expired detected via HTML redirect", flowId = flowId)
                    return@withContext MessageOpenResult.Failure(MessageError.SessionExpired)
                }
                AppLogger.logError("MessageRepository", "Unexpected full HTML received instead of Delta response", flowId = flowId)
                return@withContext MessageOpenResult.Failure(MessageError.InvalidDeltaResponse)
            }

            val deltaResponse = DeltaParser.parse(deltaText, flowId = flowId)

            // Strict check 2: Session expired token
            if (deltaResponse.isSessionExpired) {
                AppLogger.logError("MessageRepository", "Session expired detected via Delta redirect", flowId = flowId)
                return@withContext MessageOpenResult.Failure(MessageError.SessionExpired)
            }

            // Strict check 3: Valid Delta structure
            if (deltaResponse.rawTokens.isEmpty()) {
                AppLogger.logError("MessageRepository", "Empty delta tokens returned by server", flowId = flowId)
                return@withContext MessageOpenResult.Failure(MessageError.InvalidDeltaResponse)
            }

            // Strict check 4: Presence of UpdatePanel
            if (deltaResponse.updatePanels.isEmpty()) {
                AppLogger.logError("MessageRepository", "No update panels returned in delta response", flowId = flowId)
                return@withContext MessageOpenResult.Failure(MessageError.MissingUpdatePanel)
            }

            // Strict check 5: Strict message extraction from update panels or message paragraph
            var messageContent: String? = null

            // 1. Search in all update panels (e.g. UpdatePanel1, ctl00$UpdatePanel1)
            for ((_, panelHtml) in deltaResponse.updatePanels) {
                val doc = Jsoup.parse(panelHtml)
                val pText = doc.select("#cphMain_pMessage, .messageText, #lblMessageText").text().trim()
                if (pText.isNotBlank()) {
                    messageContent = pText
                    break
                }
            }

            // 2. Fallback regex search for explicit message container tag in delta
            if (messageContent.isNullOrBlank()) {
                val pMatch = Regex("""<(?:p|span|div)[^>]*id=["'](?:cphMain_)?(?:p|lbl)?Message(?:Text)?["'][^>]*>([\s\S]*?)</(?:p|span|div)>""", RegexOption.IGNORE_CASE).find(deltaText)
                if (pMatch != null) {
                    val candidate = Jsoup.parse(pMatch.groupValues[1]).text().trim()
                    if (candidate.isNotBlank()) {
                        messageContent = candidate
                        AppLogger.logWarn(
                            component = "MessageRepository",
                            operation = "OpenMessageRegexFallback",
                            message = "Extracted message content via fallback regex instead of UpdatePanel DOM selector",
                            flowId = flowId,
                            errorCategory = "MESSAGE_SELECTOR_FALLBACK"
                        )
                    }
                }
            }

            // If still missing or empty, fail strictly without silent empty success
            if (messageContent.isNullOrBlank()) {
                AppLogger.logError("MessageRepository", "No message content found inside expected panel elements", flowId = flowId)
                return@withContext MessageOpenResult.Failure(MessageError.EmptyMessageContent)
            }

            // Extract updated WebForms atomic state (ViewState, Generator, EventValidation)
            val updatedState = DeltaParser.extractWebFormsState(deltaResponse, state)

            // Privacy-preserving diagnostics: NEVER log messageContent or raw delta body
            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.INFO,
                    component = "MessageRepository",
                    operation = "OpenMessageSuccess",
                    flowId = flowId,
                    message = "Message successfully opened. Extracted text length: ${messageContent.length}, Delta tokens: ${deltaResponse.rawTokens.size}, Panels: ${deltaResponse.updatePanels.keys.joinToString()}, State fingerprint: ${LogSanitizer.computeFingerprint(updatedState.viewState)}"
                )
            )

            MessageOpenResult.Success(
                updatedState = updatedState,
                messageText = messageContent
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.logError("MessageRepository", "Exception in openMessage: ${e.message}", e, flowId = flowId)
            MessageOpenResult.Failure(MessageError.NetworkError(e))
        }
    }

    /**
     * Backwards-compatible overload for openMessage with primitive parameters.
     */
    suspend fun openMessage(
        buttonInputName: String,
        currentViewState: String,
        currentViewStateGenerator: String = "87AF9C2D",
        currentEventValidation: String = "",
        scriptManagerName: String = "ctl00\$ScriptManager",
        updatePanelName: String = "ctl00\$UpdatePanel1"
    ): MessageOpenResult = openMessage(
        buttonInputName = buttonInputName,
        state = WebFormsState(
            viewState = currentViewState,
            viewStateGenerator = currentViewStateGenerator.ifBlank { "87AF9C2D" },
            eventValidation = currentEventValidation.ifBlank { null }
        ),
        scriptManagerName = scriptManagerName,
        updatePanelName = updatePanelName
    )

    /**
     * Sequentially marks all unread messages as read with serialized ViewState and EventValidation chaining.
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

            val currentState = initialState
            val unreadList = currentState.messages.filter { !it.isRead && it.buttonInputName.isNotBlank() }
            val totalCount = unreadList.size

            if (totalCount == 0) {
                return@withLock fetchMessagesPage()
            }

            var currentWebFormsState = currentState.toWebFormsState()
            val updatedMessages = currentState.messages.toMutableList()

            // Safety limit: max 50 iterations
            val loopLimit = minOf(totalCount, 50)

            for (i in 0 until loopLimit) {
                val targetMsg = unreadList[i]
                if (targetMsg.buttonInputName.isBlank()) {
                    AppLogger.logError("MessageRepository", "Missing buttonInputName for message: ${targetMsg.title}", flowId = flowId)
                    return@withLock Result.failure(Exception("شناسه دکمه پیام '${targetMsg.title}' یافت نشد"))
                }

                // Execute exact Delta POST with chained WebFormsState
                val openResult = openMessage(
                    buttonInputName = targetMsg.buttonInputName,
                    state = currentWebFormsState,
                    scriptManagerName = currentState.scriptManagerName,
                    updatePanelName = currentState.updatePanelName
                )

                when (openResult) {
                    is MessageOpenResult.Failure -> {
                        AppLogger.logError("MessageRepository", "Aborting serialized read due to failure on message '${targetMsg.title}': ${openResult.error.userMessage}", openResult.error, flowId = flowId)
                        return@withLock Result.failure(openResult.error)
                    }
                    is MessageOpenResult.Success -> {
                        currentWebFormsState = openResult.updatedState

                        // Update local memory list
                        val listIdx = updatedMessages.indexOfFirst { it.rowIndex == targetMsg.rowIndex }
                        if (listIdx != -1) {
                            updatedMessages[listIdx] = targetMsg.copy(
                                isRead = true,
                                messageText = openResult.messageText
                            )
                        }

                        // Notify live UI listener
                        onProgress(i + 1, totalCount, targetMsg.title, openResult.messageText)
                    }
                }
            }

            // Final check: Perform a fresh GET to verify that the reservation menu has been unlocked
            val verifyResult = fetchMessagesPage()
            if (verifyResult.isSuccess) {
                val freshState = verifyResult.getOrThrow()
                val mergedMessages = freshState.messages.map { freshMsg ->
                    val local = updatedMessages.firstOrNull { it.rowIndex == freshMsg.rowIndex }
                    if (local != null && local.isRead) {
                        freshMsg.copy(isRead = true, messageText = local.messageText)
                    } else {
                        freshMsg
                    }
                }
                AppLogger.logInfo(
                    component = "MessageRepository",
                    operation = "MarkAllUnreadVerified",
                    message = "Serialized read verified via fresh GET: unlocked=${freshState.isReservationUnlocked}, remainingUnread=${mergedMessages.count { !it.isRead }}",
                    flowId = flowId
                )
                Result.success(
                    freshState.copy(
                        messages = mergedMessages,
                        unreadCount = mergedMessages.count { !it.isRead },
                        hasUnreadMessages = mergedMessages.any { !it.isRead }
                    )
                )
            } else {
                AppLogger.logWarn(
                    component = "MessageRepository",
                    operation = "MarkAllUnreadVerifyFallback",
                    message = "Final verification GET failed after marking $loopLimit messages; returning locally chained state",
                    throwable = verifyResult.exceptionOrNull(),
                    flowId = flowId,
                    errorCategory = "MESSAGES_VERIFY_FALLBACK"
                )
                Result.success(
                    currentState.withUpdatedWebFormsState(currentWebFormsState).copy(
                        messages = updatedMessages,
                        unreadCount = updatedMessages.count { !it.isRead },
                        hasUnreadMessages = updatedMessages.any { !it.isRead }
                    )
                )
            }
        }
    }
}
