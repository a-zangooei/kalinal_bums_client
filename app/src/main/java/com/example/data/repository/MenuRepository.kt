package com.example.data.repository

import com.example.data.logger.AppLogger
import com.example.data.logger.LogContext
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.NoticeDiagnostics
import com.example.data.logger.ParserDiagnostics
import com.example.data.model.MealType
import com.example.data.model.ReservationResult
import com.example.data.model.UnreadMessagesException
import com.example.data.model.WeekNavigationDirection
import com.example.data.model.WeeklyMenu
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.DeltaResponse
import com.example.data.parser.PageClassifier
import com.example.data.parser.ReservationHtmlParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

class MenuRepository {

    private val mutex = Mutex()

    suspend fun fetchInitialMenu(): Result<WeeklyMenu> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("menu")
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "MenuRepository",
                operation = "FetchInitialMenu",
                flowId = flowId,
                message = "Initiated initial menu fetch"
            )
        )
        mutex.withLock {
            try {
                val request = Request.Builder()
                    .url(CullinanHttpClient.RESERVATION_URL)
                    .get()
                    .header("Referer", CullinanHttpClient.BASE_URL)
                    .tag(LogContext::class.java, LogContext(flowId, "FetchInitialMenu"))
                    .build()

                var isSuccessful = false
                var responseCode = 0
                var finalUrl = ""
                var html = ""
                CullinanHttpClient.executeCall(request).use { response ->
                    isSuccessful = response.isSuccessful
                    responseCode = response.code
                    finalUrl = response.request.url.toString()
                    if (isSuccessful) {
                        html = response.body?.string() ?: ""
                    }
                }
                if (!isSuccessful) {
                    AppLogger.logError("MenuRepository", "Initial menu fetch failed HTTP $responseCode", flowId = flowId)
                    return@withContext Result.failure(Exception("خطا در دریافت جدول برنامه غذایی ($responseCode)"))
                }

                val classification = PageClassifier.classify(html, responseCode, finalUrl, flowId = flowId)

                if (classification.pageType != PageClassifier.TYPE_RESERVATION) {
                    AppLogger.log(
                        LogEntry(
                            timestamp = "",
                            level = LogLevel.WARN,
                            component = "MenuRepository",
                            operation = "InitialMenuPageBlocked",
                            flowId = flowId,
                            errorCategory = "NON_RESERVATION_PAGE_${classification.pageType}",
                            parser = ParserDiagnostics(
                                pageType = classification.pageType,
                                htmlLength = html.length
                            ),
                            notice = NoticeDiagnostics(
                                isNoticeDetected = classification.pageType == PageClassifier.TYPE_NOTICE,
                                noticeType = classification.noticeType,
                                actionUrl = classification.noticeActionUrl
                            ),
                            message = "Initial menu response classified as ${classification.pageType} (evidence: ${classification.evidence.joinToString()})"
                        )
                    )

                    val failureException = when (classification.pageType) {
                        PageClassifier.TYPE_MESSAGES -> UnreadMessagesException(classification.unreadMessageCount)
                        PageClassifier.TYPE_NOTICE -> Exception("اطلاعیه در سامانه تغذیه دانشگاه فعال است و دسترسی به جدول رزرو مسدود می‌باشد")
                        PageClassifier.TYPE_LOGIN -> Exception("نشست کاری در سامانه دانشگاه منقضی شده است. لطفاً مجدداً وارد شوید")
                        else -> Exception("پاسخ سرور دانشگاه نامعتبر بوده یا با خطا مواجه شد")
                    }
                    return@withContext Result.failure(failureException)
                }

                val menu = ReservationHtmlParser.parse(html, flowId = flowId)
                AppLogger.log(
                    LogEntry(
                        timestamp = "",
                        level = LogLevel.INFO,
                        component = "MenuRepository",
                        operation = "FetchInitialMenuSuccess",
                        flowId = flowId,
                        message = "Successfully loaded menu range: ${menu.weekDateRange}"
                    )
                )
                Result.success(menu)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.logError("MenuRepository", "Initial menu fetch exception: ${e.message}", e, flowId = flowId)
                Result.failure(e)
            }
        }
    }

    suspend fun navigateWeek(
        direction: WeekNavigationDirection,
        currentMenu: WeeklyMenu,
        currentOffset: Int,
        selectedTabId: String = "css3-tabstrip-0-1"
    ): Result<Pair<WeeklyMenu, Int>> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("nav")
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "MenuRepository",
                operation = "NavigateWeek",
                flowId = flowId,
                message = "Navigating week direction: $direction (offset: $currentOffset, prevRange: ${currentMenu.weekDateRange})"
            )
        )
        mutex.withLock {
            val newOffset = when (direction) {
                WeekNavigationDirection.PREVIOUS -> currentOffset - 1
                WeekNavigationDirection.NEXT -> currentOffset + 1
                WeekNavigationDirection.CURRENT -> 0
            }

            try {
                val formBody = buildNavigateWeekBody(
                    direction = direction,
                    currentMenu = currentMenu,
                    selectedTabId = selectedTabId
                )

                when (val outcome = MenuDeltaExecutor.execute(formBody, currentMenu, flowId, navigateSpec)) {
                    is MenuDeltaExecutor.Outcome.Failure -> Result.failure(outcome.exception)
                    is MenuDeltaExecutor.Outcome.Success -> {
                        val alertMsg = outcome.updatedMenu.alertMessage?.takeIf { it.isNotBlank() }
                            ?: outcome.deltaResponse.toastMessage?.takeIf { it.isNotBlank() }

                        val updatedMenu = outcome.updatedMenu.copy(alertMessage = alertMsg)

                        AppLogger.log(
                            LogEntry(
                                timestamp = "",
                                level = LogLevel.INFO,
                                component = "MenuRepository",
                                operation = "NavigateWeekSuccess",
                                flowId = flowId,
                                message = "Week navigation completed (new range: ${updatedMenu.weekDateRange}, meals: ${updatedMenu.days.sumOf { d -> d.meals.values.sumOf { it.size } }}, alert: ${alertMsg != null})"
                            )
                        )

                        Result.success(Pair(updatedMenu, newOffset))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.logError("MenuRepository", "Week navigation exception: ${e.message}", e, flowId = flowId)
                Result.failure(e)
            }
        }
    }

    suspend fun reserveMeal(
        currentMenu: WeeklyMenu,
        mealType: MealType,
        targetDayIndex: Int,
        targetFoodId: String,
        targetSelfId: String
    ): Result<ReservationResult> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("res")
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "MenuRepository",
                operation = "ReserveMeal",
                flowId = flowId,
                message = "Reserving meal ${mealType.name} day=$targetDayIndex food=$targetFoodId self=$targetSelfId"
            )
        )
        mutex.withLock {
            try {
                val formBody = buildSaveMealBody(
                    currentMenu = currentMenu,
                    mealType = mealType,
                    targetDayIndex = targetDayIndex,
                    targetFoodId = targetFoodId,
                    targetSelfId = targetSelfId,
                    isCancelling = false
                )

                when (val outcome = MenuDeltaExecutor.execute(formBody, currentMenu, flowId, reserveSpec)) {
                    is MenuDeltaExecutor.Outcome.Failure -> Result.failure(outcome.exception)
                    is MenuDeltaExecutor.Outcome.Success -> {
                        val isSuccess = outcome.deltaResponse.toastType.equals("success", ignoreCase = true)
                        val message = outcome.deltaResponse.toastMessage
                            ?: if (isSuccess) "رزرو ${mealType.titlePersian} با موفقیت انجام شد" else "خطا در پردازش رزرو توسط سرور سلف"

                        AppLogger.log(
                            LogEntry(
                                timestamp = "",
                                level = if (isSuccess) LogLevel.INFO else LogLevel.WARN,
                                component = "MenuRepository",
                                operation = "ReserveMealCompleted",
                                flowId = flowId,
                                message = "Reserve meal completed: success=$isSuccess msg=$message"
                            )
                        )

                        Result.success(
                            ReservationResult(
                                isSuccess = isSuccess,
                                message = message,
                                updatedMenu = outcome.updatedMenu,
                                toastType = outcome.deltaResponse.toastType ?: ""
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.logError("MenuRepository", "Reserve meal exception: ${e.message}", e, flowId = flowId)
                Result.failure(e)
            }
        }
    }

    suspend fun cancelMeal(
        currentMenu: WeeklyMenu,
        mealType: MealType,
        targetDayIndex: Int
    ): Result<ReservationResult> = withContext(Dispatchers.IO) {
        val flowId = AppLogger.generateFlowId("cancel")
        AppLogger.log(
            LogEntry(
                timestamp = "",
                level = LogLevel.INFO,
                component = "MenuRepository",
                operation = "CancelMeal",
                flowId = flowId,
                message = "Cancelling meal ${mealType.name} on day $targetDayIndex"
            )
        )
        mutex.withLock {
            try {
                val targetMeal = currentMenu.days.getOrNull(targetDayIndex)
                    ?.meals?.get(mealType)?.firstOrNull()

                val foodId = targetMeal?.selectedFoodId?.takeIf { it != "-1" }
                    ?: targetMeal?.availableFoodOptions?.firstOrNull()?.id ?: "-1"
                val selfId = targetMeal?.selectedSelfId?.takeIf { it != "-1" } ?: "1"

                val formBody = buildSaveMealBody(
                    currentMenu = currentMenu,
                    mealType = mealType,
                    targetDayIndex = targetDayIndex,
                    targetFoodId = foodId,
                    targetSelfId = selfId,
                    isCancelling = true
                )

                when (val outcome = MenuDeltaExecutor.execute(formBody, currentMenu, flowId, cancelSpec)) {
                    is MenuDeltaExecutor.Outcome.Failure -> Result.failure(outcome.exception)
                    is MenuDeltaExecutor.Outcome.Success -> {
                        val isSuccess = outcome.deltaResponse.toastType.equals("success", ignoreCase = true)
                        val message = outcome.deltaResponse.toastMessage
                            ?: if (isSuccess) "لغو رزرو ${mealType.titlePersian} با موفقیت انجام شد" else "خطا در لغو رزرو توسط سرور سلف"

                        AppLogger.log(
                            LogEntry(
                                timestamp = "",
                                level = if (isSuccess) LogLevel.INFO else LogLevel.WARN,
                                component = "MenuRepository",
                                operation = "CancelMealCompleted",
                                flowId = flowId,
                                message = "Cancel meal completed: success=$isSuccess msg=$message"
                            )
                        )

                        Result.success(
                            ReservationResult(
                                isSuccess = isSuccess,
                                message = message,
                                updatedMenu = outcome.updatedMenu,
                                toastType = outcome.deltaResponse.toastType ?: ""
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.logError("MenuRepository", "Cancel meal exception: ${e.message}", e, flowId = flowId)
                Result.failure(e)
            }
        }
    }

    suspend fun reserveLunch(
        currentMenu: WeeklyMenu,
        targetDayIndex: Int,
        targetFoodId: String,
        targetSelfId: String
    ): Result<ReservationResult> = reserveMeal(
        currentMenu = currentMenu,
        mealType = MealType.LUNCH,
        targetDayIndex = targetDayIndex,
        targetFoodId = targetFoodId,
        targetSelfId = targetSelfId
    )

    suspend fun cancelLunch(
        currentMenu: WeeklyMenu,
        targetDayIndex: Int
    ): Result<ReservationResult> = cancelMeal(
        currentMenu = currentMenu,
        mealType = MealType.LUNCH,
        targetDayIndex = targetDayIndex
    )

    fun buildSaveLunchBody(
        currentMenu: WeeklyMenu,
        targetDayIndex: Int,
        targetFoodId: String,
        targetSelfId: String,
        isCancelling: Boolean
    ): FormBody = buildSaveMealBody(
        currentMenu = currentMenu,
        mealType = MealType.LUNCH,
        targetDayIndex = targetDayIndex,
        targetFoodId = targetFoodId,
        targetSelfId = targetSelfId,
        isCancelling = isCancelling
    )

    fun buildNavigateWeekBody(
        direction: WeekNavigationDirection,
        currentMenu: WeeklyMenu,
        selectedTabId: String = "css3-tabstrip-0-1"
    ): FormBody = MenuFormBodyBuilder.buildNavigateWeekBody(direction, currentMenu, selectedTabId)

    fun buildSaveMealBody(
        currentMenu: WeeklyMenu,
        mealType: MealType,
        targetDayIndex: Int,
        targetFoodId: String,
        targetSelfId: String,
        isCancelling: Boolean
    ): FormBody = MenuFormBodyBuilder.buildSaveMealBody(
        currentMenu = currentMenu,
        mealType = mealType,
        targetDayIndex = targetDayIndex,
        targetFoodId = targetFoodId,
        targetSelfId = targetSelfId,
        isCancelling = isCancelling
    )

    private companion object {
        val navigateSpec = MenuDeltaExecutor.DeltaSpec(
            logTagOperation = "NavigateWeek",
            httpErrorMessage = { code -> "خطا در جابه‌جایی هفته ($code)" },
            httpErrorLogMessage = { code -> "Week navigation failed HTTP $code" },
            checkFullHtmlFallback = true,
            allowPanelHtmlHeuristic = true,
            sessionExpiredLogMessage = "Session expired during week navigation",
            serverErrorLogMessage = { d -> "Server delta error during week navigation (${d.errorStatusCode}): ${d.errorMessage}" },
            serverErrorUserPrefix = "خطای سرور در تغییر هفته: ",
            missingPanelLogMessage = { d -> "Missing UpdatePanel1 in week navigation response (tokens=${d.rawTokens.size}, panels=${d.updatePanels.keys.joinToString()})" },
            missingPanelUserMessage = "پاسخ سرور شامل محتوای جدول هفته نبود"
        )

        val reserveSpec = MenuDeltaExecutor.DeltaSpec(
            logTagOperation = "ReserveMeal",
            httpErrorMessage = { code -> "خطا در ثبت رزرو غذا در سرور ($code)" },
            httpErrorLogMessage = { code -> "Reserve meal HTTP failure $code" },
            checkFullHtmlFallback = false,
            allowPanelHtmlHeuristic = false,
            sessionExpiredLogMessage = "Session expired during meal reservation",
            sessionExpiredOperation = "ReserveMealSessionExpired",
            sessionExpiredErrorCategory = "SESSION_EXPIRED",
            serverErrorLogMessage = { d -> "Server error during meal reservation: ${d.errorMessage}" },
            serverErrorUserPrefix = "خطای سرور در ثبت رزرو: ",
            serverErrorOperation = "ReserveMealServerError",
            serverErrorCategory = "DELTA_SERVER_ERROR",
            missingPanelLogMessage = { d -> "Missing UpdatePanel1 in reserveMeal response (tokens=${d.rawTokens.size}, panels=${d.updatePanels.keys.joinToString()})" },
            missingPanelUserMessage = "پاسخ سرور ناقص بود یا جلسه کاری منقضی شده است",
            missingPanelOperation = "ReserveMealMissingPanel",
            missingPanelErrorCategory = "MISSING_UPDATE_PANEL"
        )

        val cancelSpec = MenuDeltaExecutor.DeltaSpec(
            logTagOperation = "CancelMeal",
            httpErrorMessage = { code -> "خطا در لغو رزرو غذا در سرور ($code)" },
            httpErrorLogMessage = { code -> "Cancel meal HTTP failure $code" },
            checkFullHtmlFallback = false,
            allowPanelHtmlHeuristic = false,
            sessionExpiredLogMessage = "Session expired during meal cancellation",
            sessionExpiredOperation = "CancelMealSessionExpired",
            sessionExpiredErrorCategory = "SESSION_EXPIRED",
            serverErrorLogMessage = { d -> "Server error during meal cancellation: ${d.errorMessage}" },
            serverErrorUserPrefix = "خطای سرور در لغو رزرو: ",
            serverErrorOperation = "CancelMealServerError",
            serverErrorCategory = "DELTA_SERVER_ERROR",
            missingPanelLogMessage = { d -> "Missing UpdatePanel1 in cancelMeal response (tokens=${d.rawTokens.size}, panels=${d.updatePanels.keys.joinToString()})" },
            missingPanelUserMessage = "پاسخ سرور ناقص بود یا جلسه کاری منقضی شده است",
            missingPanelOperation = "CancelMealMissingPanel",
            missingPanelErrorCategory = "MISSING_UPDATE_PANEL"
        )
    }
}
