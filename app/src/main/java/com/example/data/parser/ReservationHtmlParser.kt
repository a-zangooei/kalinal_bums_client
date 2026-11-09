package com.example.data.parser

import com.example.data.model.DayMenu
import com.example.data.model.FoodOption
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.data.model.SelfOption
import com.example.data.model.TableRowSnapshot
import com.example.data.model.WeeklyMenu
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object ReservationHtmlParser {

    private val DAY_NAMES = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    private data class ParsedTableResult(
        val meals: List<MealItem>,
        val snapshots: List<TableRowSnapshot>
    )

    /**
     * Parses the HTML content of either the full Reservation.aspx page or the UpdatePanel1
     * fragment into a clean WeeklyMenu object, capturing exact TableRowSnapshots from the DOM.
     */
    fun parse(html: String, existingViewState: String = "", existingGenerator: String = ""): WeeklyMenu {
        val doc: Document = Jsoup.parse(html)

        // 1. Week Date Range
        val weekDateText = doc.select("#cphMain_lblWeekDate").text().trim()
            .ifEmpty { "برنامه غذایی هفتگی" }

        // 2. Credit Info (cphMain_lblCreditValue or lblUserCreditPrice)
        var rawCreditText = doc.select("#cphMain_lblCreditValue").text().trim()
        if (rawCreditText.isEmpty()) {
            rawCreditText = doc.select("#lblUserCreditPrice, #ctl00_lblUserCreditPrice").text().trim()
        }
        val creditRials = parseCreditRials(rawCreditText)
        val creditText = rawCreditText.removePrefix("اعتبار شما:").trim()

        // 3. User / Student Info
        val titleBoxValue = doc.select("#cphMain_titleBoxValue").text().trim()
        var studentName = ""
        var studentNumber = ""
        if (titleBoxValue.isNotEmpty()) {
            val cleanTitle = titleBoxValue.removePrefix("کاربر:").trim()
            val parts = cleanTitle.split("-")
            if (parts.isNotEmpty()) studentName = parts[0].trim()
            if (parts.size > 1) studentNumber = parts[1].trim()
        }

        if (studentName.isEmpty()) {
            studentName = doc.select("#lblIdNumber").text().trim()
        }
        if (studentNumber.isEmpty()) {
            studentNumber = doc.select(".divUsername, #divUsername").text().trim()
        }

        // 4. Hidden fields for ASP.NET state
        val viewState = doc.select("input[name=__VIEWSTATE]").`val`().ifEmpty { existingViewState }
        val viewStateGenerator = doc.select("input[name=__VIEWSTATEGENERATOR]").`val`().ifEmpty { existingGenerator }
        val eventValidation = doc.select("input[name=__EVENTVALIDATION]").`val`()

        // 5. Initialize 7 Days of the week
        val daysList = (0..6).map { dayIdx ->
            DayMenu(
                dayIndex = dayIdx,
                dayName = DAY_NAMES.getOrElse(dayIdx) { "روز $dayIdx" },
                dateJalali = "",
                meals = mutableMapOf()
            )
        }.toMutableList()

        // 6. Parse meals and raw snapshots for Breakfast, Lunch, Dinner
        val breakfastResult = parseTableMeals(doc.select("#cphMain_grdReservationBreakfast").first(), MealType.BREAKFAST, daysList)
        val lunchResult = parseTableMeals(doc.select("#cphMain_grdReservationLunch").first(), MealType.LUNCH, daysList)
        val dinnerResult = parseTableMeals(doc.select("#cphMain_grdReservationDinner").first(), MealType.DINNER, daysList)

        // Merge meals into their corresponding days
        populateDayMeals(daysList, breakfastResult.meals, MealType.BREAKFAST)
        populateDayMeals(daysList, lunchResult.meals, MealType.LUNCH)
        populateDayMeals(daysList, dinnerResult.meals, MealType.DINNER)

        val allTableRows = breakfastResult.snapshots + lunchResult.snapshots + dinnerResult.snapshots
        val totalMealsCount = daysList.sumOf { day -> day.meals.values.sumOf { it.size } }

        val pageType = when {
            doc.select("#cphMain_lblNotice, #btnAcceptNotice, .noticeBox").isNotEmpty() -> "NOTICE"
            doc.select("input[name=txtUsernamePlain], input[name=btnLogin]").isNotEmpty() -> "LOGIN"
            doc.select("#cphMain_grdReservationLunch, #cphMain_lblWeekDate").isNotEmpty() -> "RESERVATION"
            else -> "UNKNOWN"
        }

        com.example.data.logger.AppLogger.log(
            com.example.data.logger.LogEntry(
                timestamp = "",
                level = com.example.data.logger.LogLevel.DEBUG,
                component = "Parser",
                operation = "ParseWeeklyMenu",
                parser = com.example.data.logger.ParserDiagnostics(
                    pageType = pageType,
                    htmlLength = html.length,
                    tablesFound = (if (breakfastResult.meals.isNotEmpty()) 1 else 0) +
                            (if (lunchResult.meals.isNotEmpty()) 1 else 0) +
                            (if (dinnerResult.meals.isNotEmpty()) 1 else 0),
                    parsedMealsCount = totalMealsCount,
                    skippedRowsCount = 0,
                    isMenuEmpty = totalMealsCount == 0
                ),
                notice = com.example.data.logger.NoticeDiagnostics(
                    isNoticeDetected = pageType == "NOTICE",
                    noticeType = if (pageType == "NOTICE") "MANDATORY_NOTICE" else null
                ),
                message = "Parsed $pageType HTML (len: ${html.length}, meals: $totalMealsCount, credit: $creditText)"
            )
        )

        return WeeklyMenu(
            weekDateRange = weekDateText,
            days = daysList,
            studentName = studentName,
            studentNumber = studentNumber,
            creditText = creditText,
            creditValueRials = creditRials,
            viewState = viewState,
            viewStateGenerator = viewStateGenerator,
            eventValidation = eventValidation,
            tableRows = allTableRows
        )
    }

    private fun parseTableMeals(
        table: Element?,
        mealType: MealType,
        daysList: MutableList<DayMenu>
    ): ParsedTableResult {
        if (table == null) return ParsedTableResult(emptyList(), emptyList())
        val items = mutableListOf<MealItem>()
        val snapshots = mutableListOf<TableRowSnapshot>()

        val rows = table.select("tr").filter { !it.hasClass("HeaderStyle") && it.select("td").size >= 6 }

        val mealTypeName = when (mealType) {
            MealType.BREAKFAST -> "Breakfast"
            MealType.LUNCH -> "Lunch"
            MealType.DINNER -> "Dinner"
        }

        for ((index, row) in rows.withIndex()) {
            val tds = row.select("td")
            if (tds.size < 6) continue

            // Determine dayIndex and date from td[1] if present, otherwise by row index
            val td1Text = if (tds.size > 1) tds[1].text().trim() else ""
            val dayIndex = resolveDayIndex(td1Text, index)

            // Extract Jalali date (e.g. 1405/08/20 from "چهارشنبه 1405/08/20")
            val dateJalali = extractJalaliDate(td1Text)
            if (dateJalali.isNotEmpty() && dayIndex in daysList.indices) {
                val currentDay = daysList[dayIndex]
                if (currentDay.dateJalali.isEmpty()) {
                    daysList[dayIndex] = currentDay.copy(dateJalali = dateJalali)
                }
            }

            // td[0]: Checkbox
            val chkBox = tds[0].select("input[type=checkbox]").first()
            val isChecked = chkBox?.hasAttr("checked") == true
            val isDisabled = chkBox == null || chkBox.hasAttr("disabled") || row.hasClass("aspNetDisabled")
            val isEditable = !isDisabled

            // Extract exact control name & ctl number from HTML attributes
            val chkBoxNameAttr = chkBox?.attr("name")?.takeIf { it.isNotEmpty() }
            val ctlMatch = chkBoxNameAttr?.let { Regex("""\$(ctl\d+)\$""").find(it) }?.groupValues?.get(1)
                ?: "ctl%02d".format(dayIndex + 2)

            val finalChkName = chkBoxNameAttr ?: "ctl00\$cphMain\$grdReservation${mealTypeName}\$${ctlMatch}\$chkReserve"

            // td[2]: Dining Hall (سلف)
            val selfSelect = tds[2].select("select").first()
            val selfName = selfSelect?.attr("name")?.takeIf { it.isNotEmpty() }
                ?: "ctl00\$cphMain\$grdReservation${mealTypeName}\$${ctlMatch}\$drpSelf"

            val selfOptions = selfSelect?.select("option")?.mapNotNull { opt ->
                val valId = opt.attr("value")
                val optText = opt.text().trim()
                if (valId.isEmpty() || valId == "-1" || optText == "-" || optText.isEmpty()) null
                else SelfOption(id = valId, name = optText, isDefault = opt.hasAttr("selected"))
            } ?: emptyList()

            val selectedSelfOption = selfSelect?.select("option[selected]")?.first()
            val selectedSelfId = selectedSelfOption?.attr("value")
                ?: selfOptions.firstOrNull { it.isDefault }?.id
                ?: selfOptions.firstOrNull()?.id
                ?: "-1"

            val diningHall = selectedSelfOption?.text()?.trim()?.takeIf { it != "-" && it.isNotEmpty() }
                ?: (selfOptions.firstOrNull { it.id == selectedSelfId }?.name ?: "")

            // td[3]: Food selection (غذا)
            val foodSelect = tds[3].select("select").first()
            val foodName = foodSelect?.attr("name")?.takeIf { it.isNotEmpty() }
                ?: "ctl00\$cphMain\$grdReservation${mealTypeName}\$${ctlMatch}\$drpFood"

            val foodOptions = foodSelect?.select("option")?.mapNotNull { opt ->
                val valId = opt.attr("value")
                val rawText = opt.text().trim()
                if (valId.isEmpty() || valId == "-1" || valId == "0" || rawText == "-" || rawText.isEmpty()) null
                else {
                    val price = extractPriceFromOptionText(rawText)
                    val cleanName = extractCleanFoodName(rawText)
                    val isHalf = rawText.contains("نیم پرس") || rawText.contains("نیم‌پرس")
                    FoodOption(id = valId, name = cleanName, priceRials = price, isHalfPortion = isHalf)
                }
            } ?: emptyList()

            val selectedFoodOption = foodSelect?.select("option[selected]")?.first()
            val selectedFoodId = selectedFoodOption?.attr("value")
                ?: (if (foodOptions.isNotEmpty()) foodOptions.first().id else "-1")

            val rawFoodName = selectedFoodOption?.text()?.trim() ?: "-"
            val cleanFoodName = extractCleanFoodName(rawFoodName)

            // td[4]: Count (تعداد)
            val countSelect = tds[4].select("select").first()
            val countName = countSelect?.attr("name")?.takeIf { it.isNotEmpty() }
                ?: "ctl00\$cphMain\$grdReservation${mealTypeName}\$${ctlMatch}\$drpFoodCount"

            val count = countSelect?.select("option[selected]")?.text()?.toIntOrNull() ?: if (isChecked) 1 else 0

            // td[5]: Calories (کالری)
            val calorieText = tds[5].text().trim()
            val calories = calorieText.toIntOrNull() ?: 0

            // td[6]: Buy Amount (مبلغ خرید)
            val amountText = if (tds.size > 6) tds[6].text().trim() else "0"
            val priceRials = amountText.replace(Regex("[^0-9]"), "").toLongOrNull()
                ?: (foodOptions.find { it.id == selectedFoodId }?.priceRials ?: 0L)

            // td[7]: Status Image (وضعیت)
            val statusImg = if (tds.size > 7) tds[7].select("img").first() else null
            val statusTitle = statusImg?.attr("title") ?: ""
            val statusSrc = statusImg?.attr("src") ?: ""

            val status = determineStatus(isChecked, isDisabled, statusTitle, statusSrc)
            val formattedPrice = formatRials(priceRials)
            val id = chkBox?.attr("id") ?: "${mealType.name}_$dayIndex"

            items.add(
                MealItem(
                    id = id,
                    mealType = mealType,
                    dayIndex = dayIndex,
                    tableRowIndex = ctlMatch,
                    foodName = cleanFoodName,
                    diningHall = diningHall,
                    priceRials = priceRials,
                    priceFormatted = formattedPrice,
                    calories = calories,
                    status = status,
                    count = count,
                    isEditable = isEditable,
                    selectedFoodId = selectedFoodId,
                    selectedSelfId = selectedSelfId,
                    availableFoods = foodOptions.map { it.name },
                    availableFoodOptions = foodOptions,
                    availableSelfOptions = selfOptions
                )
            )

            snapshots.add(
                TableRowSnapshot(
                    mealType = mealType,
                    dayIndex = dayIndex,
                    rowControlId = ctlMatch,
                    chkReserveName = finalChkName,
                    isChecked = isChecked,
                    drpSelfName = selfName,
                    drpSelfValue = if (selectedSelfId != "-1") selectedSelfId else "-1",
                    drpFoodName = foodName,
                    drpFoodValue = if (selectedFoodId != "-1") selectedFoodId else "-1",
                    drpFoodCountName = countName,
                    drpFoodCountValue = if (isChecked || count > 0) count.coerceAtLeast(1).toString() else "0"
                )
            )
        }

        return ParsedTableResult(items, snapshots)
    }

    private fun resolveDayIndex(td1Text: String, defaultIndex: Int): Int {
        val clean = td1Text.trim()
        return when {
            clean.startsWith("شنبه") -> 0
            clean.startsWith("یکشنبه") -> 1
            clean.startsWith("دوشنبه") -> 2
            clean.startsWith("سه شنبه") || clean.startsWith("سه‌شنبه") -> 3
            clean.startsWith("چهارشنبه") -> 4
            clean.startsWith("پنجشنبه") -> 5
            clean.startsWith("جمعه") -> 6
            else -> defaultIndex.coerceIn(0, 6)
        }
    }

    private fun extractJalaliDate(td1Text: String): String {
        val parts = td1Text.split(" ")
        return parts.lastOrNull { it.contains("/") && it.length >= 8 } ?: ""
    }

    private fun extractPriceFromOptionText(rawText: String): Long {
        val regex = Regex("""(\d+)\s*ریال""")
        val match = regex.find(rawText)
        if (match != null) {
            return match.groupValues[1].toLongOrNull() ?: 0L
        }
        return rawText.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
    }

    private fun populateDayMeals(
        daysList: MutableList<DayMenu>,
        meals: List<MealItem>,
        mealType: MealType
    ) {
        for (meal in meals) {
            val day = daysList.getOrNull(meal.dayIndex) ?: continue
            val existingMeals = day.meals.toMutableMap()
            val mealList = (existingMeals[mealType] ?: emptyList()).toMutableList()
            mealList.add(meal)
            existingMeals[mealType] = mealList
            daysList[meal.dayIndex] = day.copy(meals = existingMeals)
        }
    }

    private fun determineStatus(
        isChecked: Boolean,
        isDisabled: Boolean,
        statusTitle: String,
        statusSrc: String
    ): ReservationStatus {
        return when {
            statusSrc.contains("Eating", ignoreCase = true) || statusTitle.contains("خورده شده") -> {
                ReservationStatus.CONSUMED
            }
            statusSrc.contains("lockAcceptGreen", ignoreCase = true) || (isChecked && isDisabled) -> {
                ReservationStatus.RESERVED_LOCKED
            }
            statusSrc.contains("AcceptGreen", ignoreCase = true) || isChecked -> {
                ReservationStatus.RESERVED
            }
            statusSrc.contains("lock", ignoreCase = true) || isDisabled -> {
                ReservationStatus.LOCKED
            }
            else -> {
                ReservationStatus.NOT_RESERVED
            }
        }
    }

    private fun extractCleanFoodName(rawFood: String): String {
        if (rawFood == "-" || rawFood.isBlank()) return "غذا انتخاب نشده است"
        // Remove trailing price e.g. "180000 ریال"
        return rawFood.replace(Regex("""\s+\d+\s*ریال\s*$"""), "").trim()
    }

    private fun parseCreditRials(creditText: String): Long {
        if (creditText.isBlank()) return 0L
        val isNegative = creditText.contains("-")
        val digitsOnly = creditText.replace(Regex("[^0-9]"), "")
        val number = digitsOnly.toLongOrNull() ?: 0L
        return if (isNegative) -number else number
    }

    fun formatRials(amount: Long): String {
        if (amount == 0L) return "رایگان"
        val formatted = String.format("%,d", kotlin.math.abs(amount))
        return if (amount < 0) "$formatted- ریال" else "$formatted ریال"
    }
}
