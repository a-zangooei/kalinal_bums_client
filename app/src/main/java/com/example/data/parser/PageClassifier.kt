package com.example.data.parser

import com.example.data.logger.AppLogger
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.NoticeDiagnostics
import com.example.data.logger.ParserDiagnostics
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object PageClassifier {

    const val TYPE_RESERVATION = "RESERVATION"
    const val TYPE_LOGIN = "LOGIN"
    const val TYPE_NOTICE = "NOTICE"
    const val TYPE_MESSAGES = "MESSAGES"
    const val TYPE_ERROR = "ERROR"
    const val TYPE_UNKNOWN = "UNKNOWN"

    data class ClassificationResult(
        val pageType: String,
        val evidence: List<String>,
        val noticeActionUrl: String? = null,
        val noticeType: String? = null,
        val hasReservationGrid: Boolean = false,
        val isSessionExpired: Boolean = false,
        val hasUnreadMessages: Boolean = false,
        val unreadMessageCount: Int = 0,
        val hasPaginationControls: Boolean = false
    )

    /**
     * Pure, side-effect-free response classifier executed BEFORE menu parsing.
     * Guarantees a Notice or Login page is never falsely identified as an empty reservation menu.
     */
    fun classify(
        html: String,
        httpStatusCode: Int = 200,
        finalUrl: String? = null,
        flowId: String? = null
    ): ClassificationResult {
        val result = classifyInternal(html, httpStatusCode, finalUrl)
        logClassification(result, html.length, httpStatusCode, finalUrl, flowId)
        return result
    }

    private fun classifyInternal(
        html: String,
        httpStatusCode: Int,
        finalUrl: String?
    ): ClassificationResult {
        if (httpStatusCode >= 500) {
            return ClassificationResult(
                pageType = TYPE_ERROR,
                evidence = listOf("HTTP_STATUS_$httpStatusCode", "SERVER_ERROR")
            )
        }

        if (finalUrl?.contains("Login.aspx", ignoreCase = true) == true) {
            return ClassificationResult(
                pageType = TYPE_LOGIN,
                evidence = listOf("URL_CONTAINS_LOGIN"),
                isSessionExpired = true
            )
        }

        if (html.isBlank()) {
            return ClassificationResult(pageType = TYPE_UNKNOWN, evidence = listOf("EMPTY_HTML"))
        }

        val doc: Document = Jsoup.parse(html)
        val evidence = mutableListOf<String>()

        // 1. Check for Login page
        val hasLoginInput = doc.select("input[name=txtUsernamePlain], input[name=btnLogin], #btnLogin").isNotEmpty()
        if (hasLoginInput) {
            evidence.add("HAS_LOGIN_INPUTS")
            return ClassificationResult(
                pageType = TYPE_LOGIN,
                evidence = evidence,
                isSessionExpired = true
            )
        }

        // 2. Check for Mandatory Notice (Multiple multi-selector criteria)
        val noticeBox = doc.select(".noticeBox, #cphMain_lblNotice, #btnAcceptNotice, #cphMain_pnlNotice, input[name*='btnAcceptNotice']")
        val noticeText = doc.select("#cphMain_lblNotice, .noticeContent, #lblNotice").text().trim()
        if (noticeBox.isNotEmpty() || (noticeText.isNotBlank() && doc.select("#cphMain_grdReservationLunch").isEmpty())) {
            evidence.add("NOTICE_SELECTORS_FOUND")
            val actionUrl = doc.select("form#aspnetForm, form#MainForm").attr("action")
            return ClassificationResult(
                pageType = TYPE_NOTICE,
                evidence = evidence,
                noticeActionUrl = actionUrl.ifBlank { null },
                noticeType = if (noticeText.contains("بدهی") || noticeText.contains("کسر")) "FINANCIAL" else "ANNOUNCEMENT"
            )
        }

        // 3. Check for Server Error / ASP.NET Yellow Screen / MAC failure
        val isYellowScreen = doc.select("h1, h2").text().contains("Server Error in '/' Application") ||
                html.contains("Validation of viewstate MAC failed", ignoreCase = true) ||
                html.contains("HttpException", ignoreCase = true)
        if (isYellowScreen) {
            evidence.add("ASPNET_SERVER_EXCEPTION")
            return ClassificationResult(pageType = TYPE_ERROR, evidence = evidence)
        }

        // 4. Check for Reservation Grid
        val hasLunchGrid = doc.select("#cphMain_grdReservationLunch").isNotEmpty()
        val hasWeekDate = doc.select("#cphMain_lblWeekDate").isNotEmpty()
        if (hasLunchGrid || hasWeekDate) {
            if (hasLunchGrid) evidence.add("HAS_LUNCH_GRID")
            if (hasWeekDate) evidence.add("HAS_WEEK_DATE")
            return ClassificationResult(
                pageType = TYPE_RESERVATION,
                evidence = evidence,
                hasReservationGrid = hasLunchGrid
            )
        }

        // 5. UpdatePanel fragment check
        if (html.startsWith("1|#||4|") || html.contains("|updatePanel|")) {
            evidence.add("DELTA_FRAGMENT")
            return ClassificationResult(pageType = TYPE_RESERVATION, evidence = evidence, hasReservationGrid = true)
        }

        // 6. Check for Messages Page (cphMain_grdContent or MyMessage.aspx)
        val hasMessagesTable = doc.select("#cphMain_grdContent").isNotEmpty()
        val isMessagesUrl = finalUrl?.contains("MyMessage.aspx", ignoreCase = true) == true
        if (hasMessagesTable || isMessagesUrl) {
            evidence.add("MESSAGES_PAGE")
            if (hasMessagesTable) evidence.add("HAS_MESSAGES_GRID")
            if (isMessagesUrl) evidence.add("URL_CONTAINS_MYMESSAGE")

            val rows = doc.select("#cphMain_grdContent tr").filter {
                it.select("input[name*='imgbtnMessageText']").isNotEmpty()
            }
            var unreadCount = 0
            for (row in rows) {
                val hasCloseIcon = row.select("img[src*='MsgClose']").isNotEmpty()
                val hasOpenIcon = row.select("img[src*='MsgOpen']").isNotEmpty()
                val cells = row.select("td")
                val readDate = if (cells.size > 6) cells[6].text().trim() else ""
                val isUnread = hasCloseIcon || (!hasOpenIcon && (readDate == "-" || readDate.isBlank()))
                if (isUnread) {
                    unreadCount++
                }
            }

            val hasPager = doc.select("#cphMain_grdContent tr.PagerStyle, .PagerStyle, tr[class*='Pager']").isNotEmpty()
            if (hasPager) evidence.add("HAS_PAGINATION_CONTROLS")

            return ClassificationResult(
                pageType = TYPE_MESSAGES,
                evidence = evidence,
                hasUnreadMessages = unreadCount > 0,
                unreadMessageCount = unreadCount,
                hasPaginationControls = hasPager
            )
        }

        evidence.add("NO_MATCHING_SIGNATURE")
        return ClassificationResult(pageType = TYPE_UNKNOWN, evidence = evidence)
    }

    private fun logClassification(
        result: ClassificationResult,
        htmlLength: Int,
        httpStatusCode: Int,
        finalUrl: String?,
        flowId: String?
    ) {
        try {
            val level = when (result.pageType) {
                TYPE_ERROR, TYPE_UNKNOWN, TYPE_NOTICE -> LogLevel.WARN
                else -> LogLevel.DEBUG
            }
            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = level,
                    component = "PageClassifier",
                    operation = "ClassifyResponse",
                    flowId = flowId,
                    errorCategory = if (result.pageType == TYPE_ERROR || result.pageType == TYPE_UNKNOWN) {
                        "CLASSIFY_${result.pageType}"
                    } else null,
                    parser = ParserDiagnostics(
                        pageType = result.pageType,
                        htmlLength = htmlLength
                    ),
                    notice = if (result.pageType == TYPE_NOTICE) {
                        NoticeDiagnostics(
                            isNoticeDetected = true,
                            noticeType = result.noticeType,
                            actionUrl = result.noticeActionUrl
                        )
                    } else null,
                    message = "Classified as ${result.pageType} (status=$httpStatusCode, len=$htmlLength, evidence=[${result.evidence.joinToString()}], unread=${result.unreadMessageCount}, url=${finalUrl ?: "N/A"})"
                )
            )
        } catch (_: Exception) {}
    }
}

