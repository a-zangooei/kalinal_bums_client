package com.example.data.repository

import com.example.data.logger.LogContext
import com.example.data.model.MealType
import com.example.data.model.ReservationResult
import com.example.data.model.WeekNavigationDirection
import com.example.data.model.UnreadMessagesException
import com.example.data.model.WeeklyMenu
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.DeltaParser
import com.example.data.parser.PageClassifier
import com.example.data.parser.ReservationHtmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

class MenuRepository {

    private val mutex = Mutex()

    suspend fun fetchInitialMenu(): Result<WeeklyMenu> = withContext(Dispatchers.IO) {
        val flowId = com.example.data.logger.AppLogger.generateFlowId("menu")
        com.example.data.logger.AppLogger.log(
            com.example.data.logger.LogEntry(
                timestamp = "",
                level = com.example.data.logger.LogLevel.INFO,
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

                val response = CullinanHttpClient.executeCall(request)
                if (!response.isSuccessful) {
                    com.example.data.logger.AppLogger.logError("MenuRepository", "Initial menu fetch failed HTTP ${response.code}", flowId = flowId)
                    return@withContext Result.failure(Exception("خطا در دریافت جدول برنامه غذایی (${response.code})"))
                }

                val html = response.body?.string() ?: ""
                val classification = PageClassifier.classify(html, response.code, response.request.url.toString())

                if (classification.pageType != PageClassifier.TYPE_RESERVATION) {
                    com.example.data.logger.AppLogger.log(
                        com.example.data.logger.LogEntry(
                            timestamp = "",
                            level = com.example.data.logger.LogLevel.WARN,
                            component = "MenuRepository",
                            operation = "InitialMenuPageBlocked",
                            flowId = flowId,
                            errorCategory = "NON_RESERVATION_PAGE_${classification.pageType}",
                            parser = com.example.data.logger.ParserDiagnostics(
                                pageType = classification.pageType,
                                htmlLength = html.length
                            ),
                            notice = com.example.data.logger.NoticeDiagnostics(
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
                com.example.data.logger.AppLogger.log(
                    com.example.data.logger.LogEntry(
                        timestamp = "",
                        level = com.example.data.logger.LogLevel.INFO,
                        component = "MenuRepository",
                        operation = "FetchInitialMenuSuccess",
                        flowId = flowId,
                        message = "Successfully loaded menu range: ${menu.weekDateRange}"
                    )
                )
                Result.success(menu)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.example.data.logger.AppLogger.logError("MenuRepository", "Initial menu fetch exception: ${e.message}", e, flowId = flowId)
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
        val flowId = com.example.data.logger.AppLogger.generateFlowId("nav")
        com.example.data.logger.AppLogger.log(
            com.example.data.logger.LogEntry(
                timestamp = "",
                level = com.example.data.logger.LogLevel.INFO,
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

                val request = Request.Builder()
                    .url(CullinanHttpClient.RESERVATION_URL)
                    .post(formBody)
                    .header("X-MicrosoftAjax", "Delta=true")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", CullinanHttpClient.RESERVATION_URL)
                    .header("Origin", CullinanHttpClient.BASE_URL)
                    .header("Cache-Control", "no-cache")
                    .tag(LogContext::class.java, LogContext(flowId, "NavigateWeek"))
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
                    com.example.data.logger.AppLogger.logError("MenuRepository", "Week navigation failed HTTP $responseCode", flowId = flowId)
                    return@withContext Result.failure(Exception("خطا در جابه‌جایی هفته ($responseCode)"))
                }

                // Detect unexpected full HTML page instead of ASP.NET AJAX Delta
                val isFullHtml = (deltaText.contains("<!DOCTYPE", ignoreCase = true) || deltaText.contains("<html", ignoreCase = true)) &&
                    !deltaText.contains("|updatePanel|") && !deltaText.contains("|hiddenField|")
                if (isFullHtml) {
                    val classification = PageClassifier.classify(deltaText, responseCode, finalUrl)
                    com.example.data.logger.AppLogger.logError(
                        "MenuRepository",
                        "Full HTML returned instead of Delta during week navigation: ${classification.pageType}",
                        flowId = flowId
                    )
                    return@withContext Result.failure(
                        when (classification.pageType) {
                            PageClassifier.TYPE_MESSAGES -> UnreadMessagesException(classification.unreadMessageCount)
                            PageClassifier.TYPE_LOGIN -> Exception("نشست کاری شما در سامانه منقضی شده است. لطفاً مجدداً وارد شوید.")
                            PageClassifier.TYPE_NOTICE -> Exception("اطلاعیه سامانه دسترسی به برنامه غذایی را مسدود کرده است.")
                            else -> Exception("پاسخ سرور در قالب جدول نبود. لطفاً صفحه را تازه‌سازی کنید.")
                        }
                    )
                }

                val deltaResponse = DeltaParser.parse(deltaText)

                if (deltaResponse.isSessionExpired) {
                    com.example.data.logger.AppLogger.logError("MenuRepository", "Session expired during week navigation", flowId = flowId)
                    return@withContext Result.failure(Exception("نشست کاری شما در سامانه منقضی شده است. لطفاً مجدداً وارد شوید."))
                }

                if (!deltaResponse.errorMessage.isNullOrBlank()) {
                    com.example.data.logger.AppLogger.logError(
                        "MenuRepository",
                        "Server delta error during week navigation (${deltaResponse.errorStatusCode}): ${deltaResponse.errorMessage}",
                        flowId = flowId
                    )
                    return@withContext Result.failure(Exception("خطای سرور در تغییر هفته: ${deltaResponse.errorMessage}"))
                }

                val updatePanelHtml = deltaResponse.updatePanels["UpdatePanel1"]
                    ?: deltaResponse.updatePanels["ctl00\$UpdatePanel1"]
                    ?: deltaResponse.updatePanels[currentMenu.updatePanelName]
                    ?: deltaResponse.updatePanels.values.firstOrNull {
                        it.contains("cphMain_lblWeekDate") || it.contains("grdReservation")
                    }
                    ?: run {
                        com.example.data.logger.AppLogger.logError(
                            "MenuRepository",
                            "Missing UpdatePanel1 in week navigation response (tokens=${deltaResponse.rawTokens.size}, panels=${deltaResponse.updatePanels.keys.joinToString()})",
                            flowId = flowId
                        )
                        return@withContext Result.failure(Exception("پاسخ سرور شامل محتوای جدول هفته نبود"))
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

                val alertMsg = parsedMenu.alertMessage?.takeIf { it.isNotBlank() }
                    ?: deltaResponse.toastMessage?.takeIf { it.isNotBlank() }

                val updatedMenu = parsedMenu.copy(alertMessage = alertMsg)

                com.example.data.logger.AppLogger.log(
                    com.example.data.logger.LogEntry(
                        timestamp = "",
                        level = com.example.data.logger.LogLevel.INFO,
                        component = "MenuRepository",
                        operation = "NavigateWeekSuccess",
                        flowId = flowId,
                        message = "Week navigation completed (new range: ${updatedMenu.weekDateRange}, meals: ${updatedMenu.days.sumOf { d -> d.meals.values.sumOf { it.size } }}, alert: ${alertMsg != null})"
                    )
                )

                Result.success(Pair(updatedMenu, newOffset))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.example.data.logger.AppLogger.logError("MenuRepository", "Week navigation exception: ${e.message}", e, flowId = flowId)
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
        val flowId = com.example.data.logger.AppLogger.generateFlowId("res")
        com.example.data.logger.AppLogger.log(
            com.example.data.logger.LogEntry(
                timestamp = "",
                level = com.example.data.logger.LogLevel.INFO,
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

                val request = Request.Builder()
                    .url(CullinanHttpClient.RESERVATION_URL)
                    .post(formBody)
                    .header("X-MicrosoftAjax", "Delta=true")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", CullinanHttpClient.RESERVATION_URL)
                    .header("Origin", CullinanHttpClient.BASE_URL)
                    .header("Cache-Control", "no-cache")
                    .tag(LogContext::class.java, LogContext(flowId, "ReserveMeal"))
                    .build()

                val response = CullinanHttpClient.executeCall(request)
                if (!response.isSuccessful) {
                    com.example.data.logger.AppLogger.logError("MenuRepository", "Reserve meal HTTP failure ${response.code}", flowId = flowId)
                    return@withContext Result.failure(Exception("خطا در ثبت رزرو غذا در سرور (${response.code})"))
                }

                val deltaText = response.body?.string() ?: ""
                val deltaResponse = DeltaParser.parse(deltaText)

                if (deltaResponse.isSessionExpired) {
                    com.example.data.logger.AppLogger.logError("MenuRepository", "Session expired during meal reservation", flowId = flowId)
                    return@withContext Result.failure(Exception("نشست کاری شما در سامانه منقضی شده است. لطفاً مجدداً وارد شوید."))
                }

                if (!deltaResponse.errorMessage.isNullOrBlank()) {
                    com.example.data.logger.AppLogger.logError("MenuRepository", "Server error during meal reservation: ${deltaResponse.errorMessage}", flowId = flowId)
                    return@withContext Result.failure(Exception("خطای سرور در ثبت رزرو: ${deltaResponse.errorMessage}"))
                }

                val updatePanelHtml = deltaResponse.updatePanels["UpdatePanel1"]
                    ?: deltaResponse.updatePanels["ctl00\$UpdatePanel1"]
                    ?: deltaResponse.updatePanels[currentMenu.updatePanelName]
                    ?: return@withContext Result.failure(Exception("پاسخ سرور ناقص بود یا جلسه کاری منقضی شده است"))

                val newViewState = deltaResponse.hiddenFields["__VIEWSTATE"] ?: currentMenu.viewState
                val newGenerator = deltaResponse.hiddenFields["__VIEWSTATEGENERATOR"] ?: currentMenu.viewStateGenerator
                val newEventValidation = deltaResponse.hiddenFields["__EVENTVALIDATION"] ?: currentMenu.eventValidation

                val updatedMenu = ReservationHtmlParser.parse(
                    html = updatePanelHtml,
                    existingViewState = newViewState,
                    existingGenerator = newGenerator,
                    flowId = flowId,
                    existingEventValidation = newEventValidation,
                    existingScriptManagerName = currentMenu.scriptManagerName,
                    existingUpdatePanelName = currentMenu.updatePanelName
                )

                val isSuccess = deltaResponse.toastType.equals("success", ignoreCase = true)
                val message = deltaResponse.toastMessage
                    ?: if (isSuccess) "رزرو ${mealType.titlePersian} با موفقیت انجام شد" else "خطا در پردازش رزرو توسط سرور سلف"

                com.example.data.logger.AppLogger.log(
                    com.example.data.logger.LogEntry(
                        timestamp = "",
                        level = if (isSuccess) com.example.data.logger.LogLevel.INFO else com.example.data.logger.LogLevel.WARN,
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
                        updatedMenu = updatedMenu,
                        toastType = deltaResponse.toastType ?: ""
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.example.data.logger.AppLogger.logError("MenuRepository", "Reserve meal exception: ${e.message}", e, flowId = flowId)
                Result.failure(e)
            }
        }
    }

    suspend fun cancelMeal(
        currentMenu: WeeklyMenu,
        mealType: MealType,
        targetDayIndex: Int
    ): Result<ReservationResult> = withContext(Dispatchers.IO) {
        val flowId = com.example.data.logger.AppLogger.generateFlowId("cancel")
        com.example.data.logger.AppLogger.log(
            com.example.data.logger.LogEntry(
                timestamp = "",
                level = com.example.data.logger.LogLevel.INFO,
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

                val request = Request.Builder()
                    .url(CullinanHttpClient.RESERVATION_URL)
                    .post(formBody)
                    .header("X-MicrosoftAjax", "Delta=true")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", CullinanHttpClient.RESERVATION_URL)
                    .header("Origin", CullinanHttpClient.BASE_URL)
                    .header("Cache-Control", "no-cache")
                    .tag(LogContext::class.java, LogContext(flowId, "CancelMeal"))
                    .build()

                val response = CullinanHttpClient.executeCall(request)
                if (!response.isSuccessful) {
                    com.example.data.logger.AppLogger.logError("MenuRepository", "Cancel meal HTTP failure ${response.code}", flowId = flowId)
                    return@withContext Result.failure(Exception("خطا در لغو رزرو غذا در سرور (${response.code})"))
                }

                val deltaText = response.body?.string() ?: ""
                val deltaResponse = DeltaParser.parse(deltaText)

                if (deltaResponse.isSessionExpired) {
                    com.example.data.logger.AppLogger.logError("MenuRepository", "Session expired during meal cancellation", flowId = flowId)
                    return@withContext Result.failure(Exception("نشست کاری شما در سامانه منقضی شده است. لطفاً مجدداً وارد شوید."))
                }

                if (!deltaResponse.errorMessage.isNullOrBlank()) {
                    com.example.data.logger.AppLogger.logError("MenuRepository", "Server error during meal cancellation: ${deltaResponse.errorMessage}", flowId = flowId)
                    return@withContext Result.failure(Exception("خطای سرور در لغو رزرو: ${deltaResponse.errorMessage}"))
                }

                val updatePanelHtml = deltaResponse.updatePanels["UpdatePanel1"]
                    ?: deltaResponse.updatePanels["ctl00\$UpdatePanel1"]
                    ?: deltaResponse.updatePanels[currentMenu.updatePanelName]
                    ?: return@withContext Result.failure(Exception("پاسخ سرور ناقص بود یا جلسه کاری منقضی شده است"))

                val newViewState = deltaResponse.hiddenFields["__VIEWSTATE"] ?: currentMenu.viewState
                val newGenerator = deltaResponse.hiddenFields["__VIEWSTATEGENERATOR"] ?: currentMenu.viewStateGenerator
                val newEventValidation = deltaResponse.hiddenFields["__EVENTVALIDATION"] ?: currentMenu.eventValidation

                val updatedMenu = ReservationHtmlParser.parse(
                    html = updatePanelHtml,
                    existingViewState = newViewState,
                    existingGenerator = newGenerator,
                    flowId = flowId,
                    existingEventValidation = newEventValidation,
                    existingScriptManagerName = currentMenu.scriptManagerName,
                    existingUpdatePanelName = currentMenu.updatePanelName
                )

                val isSuccess = deltaResponse.toastType.equals("success", ignoreCase = true)
                val message = deltaResponse.toastMessage
                    ?: if (isSuccess) "لغو رزرو ${mealType.titlePersian} با موفقیت انجام شد" else "خطا در لغو رزرو توسط سرور سلف"

                com.example.data.logger.AppLogger.log(
                    com.example.data.logger.LogEntry(
                        timestamp = "",
                        level = if (isSuccess) com.example.data.logger.LogLevel.INFO else com.example.data.logger.LogLevel.WARN,
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
                        updatedMenu = updatedMenu,
                        toastType = deltaResponse.toastType ?: ""
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.example.data.logger.AppLogger.logError("MenuRepository", "Cancel meal exception: ${e.message}", e, flowId = flowId)
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
    ): FormBody {
        val buttonName = when (direction) {
            WeekNavigationDirection.PREVIOUS -> "ctl00\$cphMain\$imgbtnLastWeek"
            WeekNavigationDirection.NEXT -> "ctl00\$cphMain\$imgbtnNextWeek"
            WeekNavigationDirection.CURRENT -> "ctl00\$cphMain\$imgbtnCurrentWeek"
        }

        val builder = FormBody.Builder()
        val scriptManager = currentMenu.scriptManagerName.ifBlank { "ctl00\$ScriptManager" }
        val updatePanel = currentMenu.updatePanelName.ifBlank { "ctl00\$UpdatePanel1" }

        builder.add(scriptManager, "$updatePanel|$buttonName")
        builder.add("ctl00\$cphMain\$hdnSelectedTab", selectedTabId)

        // Include existing GridView form controls so ASP.NET WebForms sees a complete browser form postback
        appendMealRows(builder, currentMenu, MealType.BREAKFAST, "Breakfast", targetMealType = null, targetDayIndex = -1, targetFoodId = "", targetSelfId = "", isCancelling = false)
        builder.add("css3-tabstrip-0", "on")
        appendMealRows(builder, currentMenu, MealType.LUNCH, "Lunch", targetMealType = null, targetDayIndex = -1, targetFoodId = "", targetSelfId = "", isCancelling = false)
        appendMealRows(builder, currentMenu, MealType.DINNER, "Dinner", targetMealType = null, targetDayIndex = -1, targetFoodId = "", targetSelfId = "", isCancelling = false)

        builder.add("__EVENTTARGET", "")
        builder.add("__EVENTARGUMENT", "")
        builder.add("__LASTFOCUS", "")
        builder.add("__VIEWSTATE", currentMenu.viewState)
        builder.add("__VIEWSTATEGENERATOR", currentMenu.viewStateGenerator.ifEmpty { "3ED98A75" })
        builder.add("__VIEWSTATEENCRYPTED", "")
        if (currentMenu.eventValidation.isNotEmpty()) {
            builder.add("__EVENTVALIDATION", currentMenu.eventValidation)
        }
        builder.add("__ASYNCPOST", "true")
        builder.add("$buttonName.x", "8")
        builder.add("$buttonName.y", "29")

        return builder.build()
    }

    fun buildSaveMealBody(
        currentMenu: WeeklyMenu,
        mealType: MealType,
        targetDayIndex: Int,
        targetFoodId: String,
        targetSelfId: String,
        isCancelling: Boolean
    ): FormBody {
        val builder = FormBody.Builder()

        val (btnId, btnValue, tabId) = when (mealType) {
            MealType.BREAKFAST -> Triple("ctl00\$cphMain\$btnSaveBreakfast", "ذخیره صبحانه", "css3-tabstrip-0-0")
            MealType.LUNCH -> Triple("ctl00\$cphMain\$btnSaveLunch", "ذخیره ناهار", "css3-tabstrip-0-1")
            MealType.DINNER -> Triple("ctl00\$cphMain\$btnSaveDinner", "ذخیره شام", "css3-tabstrip-0-2")
        }

        // 1. ScriptManager & Selected Tab
        val scriptManager = currentMenu.scriptManagerName.ifBlank { "ctl00\$ScriptManager" }
        val updatePanel = currentMenu.updatePanelName.ifBlank { "ctl00\$UpdatePanel1" }
        builder.add(scriptManager, "$updatePanel|$btnId")
        builder.add("ctl00\$cphMain\$hdnSelectedTab", tabId)

        // 2. Breakfast rows
        appendMealRows(builder, currentMenu, MealType.BREAKFAST, "Breakfast", mealType, targetDayIndex, targetFoodId, targetSelfId, isCancelling)

        // 3. Tab radio indicator
        builder.add("css3-tabstrip-0", "on")

        // 4. Lunch rows
        appendMealRows(builder, currentMenu, MealType.LUNCH, "Lunch", mealType, targetDayIndex, targetFoodId, targetSelfId, isCancelling)

        // 5. Dinner rows
        appendMealRows(builder, currentMenu, MealType.DINNER, "Dinner", mealType, targetDayIndex, targetFoodId, targetSelfId, isCancelling)

        // 6. Fixed trailing fields
        builder.add("__EVENTTARGET", "")
        builder.add("__EVENTARGUMENT", "")
        builder.add("__LASTFOCUS", "")
        builder.add("__VIEWSTATE", currentMenu.viewState)
        builder.add("__VIEWSTATEGENERATOR", currentMenu.viewStateGenerator.ifEmpty { "3ED98A75" })
        builder.add("__VIEWSTATEENCRYPTED", "")
        if (currentMenu.eventValidation.isNotEmpty()) {
            builder.add("__EVENTVALIDATION", currentMenu.eventValidation)
        }
        builder.add("__ASYNCPOST", "true")
        builder.add(btnId, btnValue)

        return builder.build()
    }

    private fun appendMealRows(
        builder: FormBody.Builder,
        currentMenu: WeeklyMenu,
        type: MealType,
        mealTypeName: String,
        targetMealType: MealType?,
        targetDayIndex: Int,
        targetFoodId: String,
        targetSelfId: String,
        isCancelling: Boolean
    ) {
        val rows = currentMenu.tableRows.filter { it.mealType == type }
        val isTargetType = (targetMealType != null && type == targetMealType)

        if (rows.isNotEmpty()) {
            for (row in rows) {
                if (isTargetType && row.dayIndex == targetDayIndex) {
                    val selfVal = if (targetSelfId.isNotEmpty() && targetSelfId != "-1") {
                        targetSelfId
                    } else {
                        row.drpSelfValue.takeIf { it != "-1" && it.isNotEmpty() } ?: "1"
                    }

                    val foodVal = if (targetFoodId.isNotEmpty() && targetFoodId != "-1") {
                        targetFoodId
                    } else {
                        row.drpFoodValue.takeIf { it != "-1" && it.isNotEmpty() } ?: "-1"
                    }

                    if (!isCancelling) {
                        builder.add(row.chkReserveName, "on")
                    }
                    builder.add(row.drpSelfName, selfVal)
                    builder.add(row.drpFoodName, foodVal)
                    builder.add(row.drpFoodCountName, "1")
                } else {
                    // Preserve existing state of other rows
                    if (row.isChecked) {
                        builder.add(row.chkReserveName, "on")
                        builder.add(row.drpSelfName, row.drpSelfValue.ifEmpty { "1" })
                        builder.add(row.drpFoodName, row.drpFoodValue.ifEmpty { "-1" })
                        builder.add(row.drpFoodCountName, row.drpFoodCountValue.ifEmpty { "1" })
                    } else {
                        builder.add(row.drpSelfName, "-1")
                        builder.add(row.drpFoodName, "-1")
                        builder.add(row.drpFoodCountName, "0")
                    }
                }
            }
        } else if (targetMealType != null) {
            for (dayIdx in 0..6) {
                val ctl = "ctl%02d".format(dayIdx + 2)
                val meal = currentMenu.days.getOrNull(dayIdx)?.meals?.get(type)?.firstOrNull()

                if (isTargetType && dayIdx == targetDayIndex) {
                    val selfVal = if (targetSelfId.isNotEmpty() && targetSelfId != "-1") {
                        targetSelfId
                    } else {
                        meal?.selectedSelfId?.takeIf { it != "-1" } ?: "1"
                    }

                    val foodVal = if (targetFoodId.isNotEmpty() && targetFoodId != "-1") {
                        targetFoodId
                    } else {
                        meal?.selectedFoodId?.takeIf { it != "-1" }
                            ?: meal?.availableFoodOptions?.firstOrNull()?.id
                            ?: "-1"
                    }

                    if (!isCancelling) {
                        builder.add("ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$chkReserve", "on")
                    }
                    builder.add("ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$drpSelf", selfVal)
                    builder.add("ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$drpFood", foodVal)
                    builder.add("ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$drpFoodCount", "1")
                } else {
                    if (meal != null && meal.status.isReserved) {
                        builder.add("ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$chkReserve", "on")
                        builder.add(
                            "ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$drpSelf",
                            meal.selectedSelfId.takeIf { it != "-1" } ?: "1"
                        )
                        builder.add(
                            "ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$drpFood",
                            meal.selectedFoodId.takeIf { it != "-1" } ?: "-1"
                        )
                        builder.add(
                            "ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$drpFoodCount",
                            meal.count.coerceAtLeast(1).toString()
                        )
                    } else {
                        builder.add("ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$drpSelf", "-1")
                        builder.add("ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$drpFood", "-1")
                        builder.add("ctl00\$cphMain\$grdReservation${mealTypeName}\$$ctl\$drpFoodCount", "0")
                    }
                }
            }
        }
    }
}
