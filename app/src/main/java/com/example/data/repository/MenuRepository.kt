package com.example.data.repository

import com.example.data.model.MealType
import com.example.data.model.ReservationResult
import com.example.data.model.WeekNavigationDirection
import com.example.data.model.WeeklyMenu
import com.example.data.network.CullinanHttpClient
import com.example.data.parser.DeltaParser
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
        mutex.withLock {
            try {
                val request = Request.Builder()
                    .url(CullinanHttpClient.RESERVATION_URL)
                    .get()
                    .header("Referer", CullinanHttpClient.BASE_URL)
                    .build()

                val response = CullinanHttpClient.client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("خطا در دریافت جدول برنامه غذایی (${response.code})"))
                }

                val html = response.body?.string() ?: ""
                val menu = ReservationHtmlParser.parse(html)
                Result.success(menu)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun navigateWeek(
        direction: WeekNavigationDirection,
        currentMenu: WeeklyMenu,
        currentOffset: Int
    ): Result<Pair<WeeklyMenu, Int>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val newOffset = when (direction) {
                WeekNavigationDirection.PREVIOUS -> currentOffset - 1
                WeekNavigationDirection.NEXT -> currentOffset + 1
                WeekNavigationDirection.CURRENT -> 0
            }

            try {
                val buttonName = when (direction) {
                    WeekNavigationDirection.PREVIOUS -> "ctl00\$cphMain\$imgbtnLastWeek"
                    WeekNavigationDirection.NEXT -> "ctl00\$cphMain\$imgbtnNextWeek"
                    WeekNavigationDirection.CURRENT -> "ctl00\$cphMain\$imgbtnCurrentWeek"
                }

                val formBody = FormBody.Builder()
                    .add("ctl00\$ScriptManager", "ctl00\$UpdatePanel1|$buttonName")
                    .add("ctl00\$cphMain\$hdnSelectedTab", "css3-tabstrip-0-1")
                    .add("__EVENTTARGET", "")
                    .add("__EVENTARGUMENT", "")
                    .add("__VIEWSTATE", currentMenu.viewState)
                    .add("__VIEWSTATEGENERATOR", currentMenu.viewStateGenerator.ifEmpty { "3ED98A75" })
                    .add("__VIEWSTATEENCRYPTED", "")
                    .add("__ASYNCPOST", "true")
                    .add("$buttonName.x", "8")
                    .add("$buttonName.y", "29")
                    .build()

                val request = Request.Builder()
                    .url(CullinanHttpClient.RESERVATION_URL)
                    .post(formBody)
                    .header("X-MicrosoftAjax", "Delta=true")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", CullinanHttpClient.RESERVATION_URL)
                    .header("Origin", CullinanHttpClient.BASE_URL)
                    .header("Cache-Control", "no-cache")
                    .build()

                val response = CullinanHttpClient.client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("خطا در جابه‌جایی هفته (${response.code})"))
                }

                val deltaText = response.body?.string() ?: ""
                val deltaResponse = DeltaParser.parse(deltaText)

                val updatePanelHtml = deltaResponse.updatePanels["UpdatePanel1"]
                    ?: return@withContext Result.failure(Exception("پاسخ سرور شامل محتوای جدول هفته نبود"))

                val newViewState = deltaResponse.hiddenFields["__VIEWSTATE"] ?: currentMenu.viewState
                val newGenerator = deltaResponse.hiddenFields["__VIEWSTATEGENERATOR"] ?: currentMenu.viewStateGenerator

                val updatedMenu = ReservationHtmlParser.parse(
                    html = updatePanelHtml,
                    existingViewState = newViewState,
                    existingGenerator = newGenerator
                )

                Result.success(Pair(updatedMenu, newOffset))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun reserveLunch(
        currentMenu: WeeklyMenu,
        targetDayIndex: Int,
        targetFoodId: String,
        targetSelfId: String
    ): Result<ReservationResult> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val formBody = buildSaveLunchBody(
                    currentMenu = currentMenu,
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
                    .build()

                val response = CullinanHttpClient.client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("خطا در ثبت رزرو غذا در سرور (${response.code})"))
                }

                val deltaText = response.body?.string() ?: ""
                val deltaResponse = DeltaParser.parse(deltaText)

                val updatePanelHtml = deltaResponse.updatePanels["UpdatePanel1"]
                    ?: return@withContext Result.failure(Exception("پاسخ سرور ناقص بود یا جلسه کاری منقضی شده است"))

                val newViewState = deltaResponse.hiddenFields["__VIEWSTATE"] ?: currentMenu.viewState
                val newGenerator = deltaResponse.hiddenFields["__VIEWSTATEGENERATOR"] ?: currentMenu.viewStateGenerator

                val updatedMenu = ReservationHtmlParser.parse(
                    html = updatePanelHtml,
                    existingViewState = newViewState,
                    existingGenerator = newGenerator
                )

                val isSuccess = deltaResponse.toastType.equals("success", ignoreCase = true)
                val message = deltaResponse.toastMessage
                    ?: if (isSuccess) "رزرو غذا با موفقیت انجام شد" else "خطا در پردازش رزرو توسط سرور سلف"

                Result.success(
                    ReservationResult(
                        isSuccess = isSuccess,
                        message = message,
                        updatedMenu = updatedMenu,
                        toastType = deltaResponse.toastType ?: ""
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun cancelLunch(
        currentMenu: WeeklyMenu,
        targetDayIndex: Int
    ): Result<ReservationResult> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val targetMeal = currentMenu.days.getOrNull(targetDayIndex)
                    ?.meals?.get(MealType.LUNCH)?.firstOrNull()

                val foodId = targetMeal?.selectedFoodId?.takeIf { it != "-1" }
                    ?: targetMeal?.availableFoodOptions?.firstOrNull()?.id ?: "-1"
                val selfId = targetMeal?.selectedSelfId?.takeIf { it != "-1" } ?: "1"

                val formBody = buildSaveLunchBody(
                    currentMenu = currentMenu,
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
                    .build()

                val response = CullinanHttpClient.client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("خطا در لغو رزرو غذا در سرور (${response.code})"))
                }

                val deltaText = response.body?.string() ?: ""
                val deltaResponse = DeltaParser.parse(deltaText)

                val updatePanelHtml = deltaResponse.updatePanels["UpdatePanel1"]
                    ?: return@withContext Result.failure(Exception("پاسخ سرور ناقص بود یا جلسه کاری منقضی شده است"))

                val newViewState = deltaResponse.hiddenFields["__VIEWSTATE"] ?: currentMenu.viewState
                val newGenerator = deltaResponse.hiddenFields["__VIEWSTATEGENERATOR"] ?: currentMenu.viewStateGenerator

                val updatedMenu = ReservationHtmlParser.parse(
                    html = updatePanelHtml,
                    existingViewState = newViewState,
                    existingGenerator = newGenerator
                )

                val isSuccess = deltaResponse.toastType.equals("success", ignoreCase = true)
                val message = deltaResponse.toastMessage
                    ?: if (isSuccess) "لغو رزرو غذا با موفقیت انجام شد" else "خطا در لغو رزرو توسط سرور سلف"

                Result.success(
                    ReservationResult(
                        isSuccess = isSuccess,
                        message = message,
                        updatedMenu = updatedMenu,
                        toastType = deltaResponse.toastType ?: ""
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun buildSaveLunchBody(
        currentMenu: WeeklyMenu,
        targetDayIndex: Int,
        targetFoodId: String,
        targetSelfId: String,
        isCancelling: Boolean
    ): FormBody {
        val builder = FormBody.Builder()

        // 1. ScriptManager & Tab Selection
        builder.add("ctl00\$ScriptManager", "ctl00\$UpdatePanel1|ctl00\$cphMain\$btnSaveLunch")
        builder.add("ctl00\$cphMain\$hdnSelectedTab", "css3-tabstrip-0-1")

        val breakfastRows = currentMenu.tableRows.filter { it.mealType == MealType.BREAKFAST }
        val lunchRows = currentMenu.tableRows.filter { it.mealType == MealType.LUNCH }
        val dinnerRows = currentMenu.tableRows.filter { it.mealType == MealType.DINNER }

        // 2. Breakfast rows (from snapshot or fallback)
        if (breakfastRows.isNotEmpty()) {
            for (row in breakfastRows) {
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
        } else {
            for (dayIdx in 0..6) {
                val ctl = "ctl%02d".format(dayIdx + 2)
                builder.add("ctl00\$cphMain\$grdReservationBreakfast\$$ctl\$drpSelf", "-1")
                builder.add("ctl00\$cphMain\$grdReservationBreakfast\$$ctl\$drpFood", "-1")
                builder.add("ctl00\$cphMain\$grdReservationBreakfast\$$ctl\$drpFoodCount", "0")
            }
        }

        // 3. Active tab indicator
        builder.add("css3-tabstrip-0", "on")

        // 4. Lunch rows (from snapshot or fallback)
        if (lunchRows.isNotEmpty()) {
            for (row in lunchRows) {
                if (row.dayIndex == targetDayIndex) {
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
                    // PRESERVE OTHER DAYS EXACT STATE
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
        } else {
            // Fallback if snapshots not present
            for (dayIdx in 0..6) {
                val ctl = "ctl%02d".format(dayIdx + 2)
                val meal = currentMenu.days.getOrNull(dayIdx)?.meals?.get(MealType.LUNCH)?.firstOrNull()

                if (dayIdx == targetDayIndex) {
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
                        builder.add("ctl00\$cphMain\$grdReservationLunch\$$ctl\$chkReserve", "on")
                    }
                    builder.add("ctl00\$cphMain\$grdReservationLunch\$$ctl\$drpSelf", selfVal)
                    builder.add("ctl00\$cphMain\$grdReservationLunch\$$ctl\$drpFood", foodVal)
                    builder.add("ctl00\$cphMain\$grdReservationLunch\$$ctl\$drpFoodCount", "1")
                } else {
                    if (meal != null && meal.status.isReserved) {
                        builder.add("ctl00\$cphMain\$grdReservationLunch\$$ctl\$chkReserve", "on")
                        builder.add(
                            "ctl00\$cphMain\$grdReservationLunch\$$ctl\$drpSelf",
                            meal.selectedSelfId.takeIf { it != "-1" } ?: "1"
                        )
                        builder.add(
                            "ctl00\$cphMain\$grdReservationLunch\$$ctl\$drpFood",
                            meal.selectedFoodId.takeIf { it != "-1" } ?: "-1"
                        )
                        builder.add(
                            "ctl00\$cphMain\$grdReservationLunch\$$ctl\$drpFoodCount",
                            meal.count.coerceAtLeast(1).toString()
                        )
                    } else {
                        builder.add("ctl00\$cphMain\$grdReservationLunch\$$ctl\$drpSelf", "-1")
                        builder.add("ctl00\$cphMain\$grdReservationLunch\$$ctl\$drpFood", "-1")
                        builder.add("ctl00\$cphMain\$grdReservationLunch\$$ctl\$drpFoodCount", "0")
                    }
                }
            }
        }

        // 5. Dinner rows (from snapshot or fallback)
        if (dinnerRows.isNotEmpty()) {
            for (row in dinnerRows) {
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
        } else {
            for (dayIdx in 0..6) {
                val ctl = "ctl%02d".format(dayIdx + 2)
                builder.add("ctl00\$cphMain\$grdReservationDinner\$$ctl\$drpSelf", "-1")
                builder.add("ctl00\$cphMain\$grdReservationDinner\$$ctl\$drpFood", "-1")
                builder.add("ctl00\$cphMain\$grdReservationDinner\$$ctl\$drpFoodCount", "0")
            }
        }

        // 6. Fixed trailing fields
        builder.add("__EVENTTARGET", "")
        builder.add("__EVENTARGUMENT", "")
        builder.add("__LASTFOCUS", "")
        builder.add("__VIEWSTATE", currentMenu.viewState)
        builder.add("__VIEWSTATEGENERATOR", currentMenu.viewStateGenerator.ifEmpty { "3ED98A75" })
        builder.add("__VIEWSTATEENCRYPTED", "")
        builder.add("__ASYNCPOST", "true")
        builder.add("ctl00\$cphMain\$btnSaveLunch", "ذخیره ناهار")

        return builder.build()
    }
}
