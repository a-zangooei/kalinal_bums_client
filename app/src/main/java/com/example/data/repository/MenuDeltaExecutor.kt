package com.example.data.repository

import com.example.data.logger.AppLogger
import com.example.data.logger.LogContext
import com.example.data.model.UnreadMessagesException
import com.example.data.model.WeeklyMenu
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.DeltaParser
import com.example.data.parser.DeltaResponse
import com.example.data.parser.PageClassifier
import com.example.data.parser.ReservationHtmlParser
import okhttp3.FormBody
import okhttp3.Request

/**
 * Executes ASP.NET AJAX Delta requests against Reservation.aspx, chains ViewState /
 * EventValidation tokens, and parses the updated UpdatePanel1 HTML fragment.
 */
internal object MenuDeltaExecutor {

    data class DeltaSpec(
        val logTagOperation: String,
        val httpErrorMessage: (Int) -> String,
        val httpErrorLogMessage: (Int) -> String,
        val checkFullHtmlFallback: Boolean,
        val allowPanelHtmlHeuristic: Boolean,
        val sessionExpiredLogMessage: String,
        val sessionExpiredOperation: String = "Error",
        val sessionExpiredErrorCategory: String? = null,
        val serverErrorLogMessage: (DeltaResponse) -> String,
        val serverErrorUserPrefix: String,
        val serverErrorOperation: String = "Error",
        val serverErrorCategory: String? = null,
        val missingPanelLogMessage: (DeltaResponse) -> String,
        val missingPanelUserMessage: String,
        val missingPanelOperation: String = "Error",
        val missingPanelErrorCategory: String? = null
    )

    sealed class Outcome {
        data class Success(
            val updatedMenu: WeeklyMenu,
            val deltaResponse: DeltaResponse
        ) : Outcome()

        data class Failure(val exception: Exception) : Outcome()
    }

    fun execute(
        formBody: FormBody,
        currentMenu: WeeklyMenu,
        flowId: String,
        spec: DeltaSpec
    ): Outcome {
        val request = Request.Builder()
            .url(CullinanHttpClient.RESERVATION_URL)
            .post(formBody)
            .header("X-MicrosoftAjax", "Delta=true")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", CullinanHttpClient.RESERVATION_URL)
            .header("Origin", CullinanHttpClient.BASE_URL)
            .header("Cache-Control", "no-cache")
            .tag(LogContext::class.java, LogContext(flowId, spec.logTagOperation))
            .build()

        var isSuccessful = false
        var responseCode = 0
        var finalUrl = ""
        var deltaText = ""
        CullinanHttpClient.executeCall(request).use { response ->
            isSuccessful = response.isSuccessful
            responseCode = response.code
            finalUrl = response.request.url.toString()
            deltaText = if (isSuccessful) response.body?.string() ?: "" else ""
        }
        if (!isSuccessful) {
            AppLogger.logError("MenuRepository", spec.httpErrorLogMessage(responseCode), flowId = flowId)
            return Outcome.Failure(Exception(spec.httpErrorMessage(responseCode)))
        }

        if (spec.checkFullHtmlFallback) {
            val isFullHtml = (deltaText.contains("<!DOCTYPE", ignoreCase = true) || deltaText.contains("<html", ignoreCase = true)) &&
                !deltaText.contains("|updatePanel|") && !deltaText.contains("|hiddenField|")
            if (isFullHtml) {
                val classification = PageClassifier.classify(deltaText, responseCode, finalUrl, flowId = flowId)
                AppLogger.logError(
                    "MenuRepository",
                    "Full HTML returned instead of Delta during week navigation: ${classification.pageType}",
                    flowId = flowId,
                    operation = "NavigateWeekFullHtml",
                    errorCategory = "UNEXPECTED_FULL_HTML"
                )
                return Outcome.Failure(
                    when (classification.pageType) {
                        PageClassifier.TYPE_MESSAGES -> UnreadMessagesException(classification.unreadMessageCount)
                        PageClassifier.TYPE_LOGIN -> Exception("نشست کاری شما در سامانه منقضی شده است. لطفاً مجدداً وارد شوید.")
                        PageClassifier.TYPE_NOTICE -> Exception("اطلاعیه سامانه دسترسی به برنامه غذایی را مسدود کرده است.")
                        else -> Exception("پاسخ سرور در قالب جدول نبود. لطفاً صفحه را تازه‌سازی کنید.")
                    }
                )
            }
        }

        val deltaResponse = DeltaParser.parse(deltaText, flowId = flowId)

        if (deltaResponse.isSessionExpired) {
            AppLogger.logError(
                "MenuRepository",
                spec.sessionExpiredLogMessage,
                flowId = flowId,
                operation = spec.sessionExpiredOperation,
                errorCategory = spec.sessionExpiredErrorCategory
            )
            return Outcome.Failure(Exception("نشست کاری شما در سامانه منقضی شده است. لطفاً مجدداً وارد شوید."))
        }

        if (!deltaResponse.errorMessage.isNullOrBlank()) {
            AppLogger.logError(
                "MenuRepository",
                spec.serverErrorLogMessage(deltaResponse),
                flowId = flowId,
                operation = spec.serverErrorOperation,
                errorCategory = spec.serverErrorCategory
            )
            return Outcome.Failure(Exception("${spec.serverErrorUserPrefix}${deltaResponse.errorMessage}"))
        }

        val updatePanelHtml = deltaResponse.updatePanels["UpdatePanel1"]
            ?: deltaResponse.updatePanels["ctl00\$UpdatePanel1"]
            ?: deltaResponse.updatePanels[currentMenu.updatePanelName]
            ?: if (spec.allowPanelHtmlHeuristic) {
                deltaResponse.updatePanels.values.firstOrNull {
                    it.contains("cphMain_lblWeekDate") || it.contains("grdReservation")
                }
            } else {
                null
            }
            ?: run {
                AppLogger.logError(
                    "MenuRepository",
                    spec.missingPanelLogMessage(deltaResponse),
                    flowId = flowId,
                    operation = spec.missingPanelOperation,
                    errorCategory = spec.missingPanelErrorCategory
                )
                return Outcome.Failure(Exception(spec.missingPanelUserMessage))
            }

        val newViewState = deltaResponse.hiddenFields["__VIEWSTATE"] ?: currentMenu.viewState
        val newGenerator = deltaResponse.hiddenFields["__VIEWSTATEGENERATOR"] ?: currentMenu.viewStateGenerator
        val newEventValidation = deltaResponse.hiddenFields["__EVENTVALIDATION"] ?: currentMenu.eventValidation

        val parsedMenu = ReservationHtmlParser.parse(
            html = updatePanelHtml,
            existingViewState = newViewState,
            existingGenerator = newGenerator,
            flowId = flowId,
            existingEventValidation = newEventValidation,
            existingScriptManagerName = currentMenu.scriptManagerName,
            existingUpdatePanelName = currentMenu.updatePanelName
        )

        return Outcome.Success(parsedMenu, deltaResponse)
    }
}
