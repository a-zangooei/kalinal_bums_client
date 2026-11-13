package com.example.data.repository

import com.example.data.logger.AppLogger
import com.example.data.model.MealType
import com.example.data.model.WeekNavigationDirection
import com.example.data.model.WeeklyMenu
import okhttp3.FormBody

/**
 * Responsible for constructing ASP.NET WebForms POST payloads for weekly navigation
 * and meal reservation/cancellation while preserving exact browser control order.
 */
internal object MenuFormBodyBuilder {

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

        appendCommonTrailingFields(builder, currentMenu)
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
        appendCommonTrailingFields(builder, currentMenu)
        builder.add(btnId, btnValue)

        return builder.build()
    }

    private fun appendCommonTrailingFields(builder: FormBody.Builder, currentMenu: WeeklyMenu) {
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
            AppLogger.logWarn(
                component = "MenuRepository",
                operation = "SyntheticRowFallback",
                message = "No TableRowSnapshots present for ${type.name}; using synthetic ctl02..ctl08 postback fields",
                errorCategory = "TABLE_SNAPSHOT_MISSING"
            )
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
