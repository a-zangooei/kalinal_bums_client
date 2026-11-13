package com.example.data.parser

import com.example.data.logger.AppLogger
import com.example.data.logger.AspNetDiagnostics
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.LogSanitizer
import com.example.data.logger.NoticeDiagnostics
import com.example.data.logger.ParserDiagnostics
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
        val snapshots: List<TableRowSnapshot>,
        val tableFound: Boolean,
        val totalRows: Int,
        val skippedRows: Int
    )

    /**
     * Parses the HTML content of either the full Reservation.aspx page or the UpdatePanel1
     * fragment into a clean WeeklyMenu object, capturing exact TableRowSnapshots from the DOM.
     */
    fun parse(
        html: String,
        existingViewState: String = "",
        existingGenerator: String = "",
        flowId: String? = null,
        existingEventValidation: String = "",
        existingScriptManagerName: String = "ctl00\$ScriptManager",
        existingUpdatePanelName: String = "ctl00\$UpdatePanel1"
    ): WeeklyMenu {
        val doc: Document = Jsoup.parse(html)
        val isFragment = html.startsWith("1|#||4|") || doc.select("html, body").isEmpty()

        // 1. Week Date Range
        val weekDateText = doc.select("#cphMain_lblWeekDate").text().trim()
            .ifEmpty { "برنامه غذایی هفتگی" }

        // 1b. Optional visible server alert box (#cphMain_alertBox / #cphMain_alertBoxValue)
        val alertBoxEl = doc.select("#cphMain_alertBox").firstOrNull()
        val alertStyle = alertBoxEl?.attr("style")?.replace(" ", "")?.lowercase() ?: ""
        val isAlertHidden = alertStyle.contains("display:none")
        val alertText = doc.select("#cphMain_alertBoxValue").text().trim()
        val visibleAlert = if (!isAlertHidden && alertText.isNotEmpty()) alertText else null

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
        val eventValidation = doc.select("input[name=__EVENTVALIDATION]").`val`().ifEmpty { existingEventValidation }

        val scriptManagerInput = doc.select("input[name*='ScriptManager']").firstOrNull()
        val scriptManagerName = scriptManagerInput?.attr("name")?.takeIf { it.isNotBlank() }
            ?: existingScriptManagerName

        val updatePanelDiv = doc.select("div[id*='UpdatePanel']").firstOrNull()
        val updatePanelHtmlId = updatePanelDiv?.attr("id")
        val updatePanelName = when {
            updatePanelHtmlId != null && updatePanelHtmlId.contains("ctl00") -> updatePanelHtmlId.replace('_', '$')
            updatePanelHtmlId != null && updatePanelHtmlId.isNotEmpty() -> "ctl00\$$updatePanelHtmlId"
            else -> existingUpdatePanelName
        }

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
        val breakfastTable = doc.select("#cphMain_grdReservationBreakfast").first()
        val lunchTable = doc.select("#cphMain_grdReservationLunch").first()
        val dinnerTable = doc.select("#cphMain_grdReservationDinner").first()

        val rawTablesInDom = (if (breakfastTable != null) 1 else 0) +
                (if (lunchTable != null) 1 else 0) +
                (if (dinnerTable != null) 1 else 0)

        val breakfastResult = parseTableMeals(breakfastTable, MealType.BREAKFAST, daysList)
        val lunchResult = parseTableMeals(lunchTable, MealType.LUNCH, daysList)
        val dinnerResult = parseTableMeals(dinnerTable, MealType.DINNER, daysList)

        // Merge meals into their corresponding days
        populateDayMeals(daysList, breakfastResult.meals, MealType.BREAKFAST)
        populateDayMeals(daysList, lunchResult.meals, MealType.LUNCH)
        populateDayMeals(daysList, dinnerResult.meals, MealType.DINNER)

        val allTableRows = breakfastResult.snapshots + lunchResult.snapshots + dinnerResult.snapshots
        val totalMealsCount = daysList.sumOf { day -> day.meals.values.sumOf { it.size } }
        val totalSkippedRows = breakfastResult.skippedRows + lunchResult.skippedRows + dinnerResult.skippedRows
        val totalTablesWithMeals = (if (breakfastResult.meals.isNotEmpty()) 1 else 0) +
                (if (lunchResult.meals.isNotEmpty()) 1 else 0) +
                (if (dinnerResult.meals.isNotEmpty()) 1 else 0)

        val classification = PageClassifier.classify(html)

        try {
            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.DEBUG,
                    component = "Parser",
                    operation = "ParseWeeklyMenu",
                    flowId = flowId,
                    aspnet = AspNetDiagnostics(
                        viewStateLength = viewState.length,
                        viewStateFingerprint = LogSanitizer.computeFingerprint(viewState),
                        viewStateGenerator = viewStateGenerator,
                        hasEventValidation = eventValidation.isNotBlank(),
                        eventValidationLength = eventValidation.length
                    ),
                    parser = ParserDiagnostics(
                        pageType = classification.pageType,
                        htmlLength = html.length,
                        rawTablesInDom = rawTablesInDom,
                        tablesFound = totalTablesWithMeals,
                        parsedMealsCount = totalMealsCount,
                        skippedRowsCount = totalSkippedRows,
                        isMenuEmpty = totalMealsCount == 0,
                        isFragment = isFragment
                    ),
                    notice = NoticeDiagnostics(
                        isNoticeDetected = classification.pageType == PageClassifier.TYPE_NOTICE,
                        noticeType = classification.noticeType,
                        actionUrl = classification.noticeActionUrl
                    ),
                    message = "Parsed ${classification.pageType} (len: ${html.length}, tables: $rawTablesInDom, meals: $totalMealsCount, skippedRows: $totalSkippedRows)"
                )
            )
        } catch (_: Exception) {}

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
            tableRows = allTableRows,
            scriptManagerName = scriptManagerName,
            updatePanelName = updatePanelName,
            alertMessage = visibleAlert
        )
    }

    private fun parseTableMeals(
        table: Element?,
        mealType: MealType,
        daysList: MutableList<DayMenu>
    ): ParsedTableResult {
        if (table == null) return ParsedTableResult(emptyList(), emptyList(), false, 0, 0)
        val items = mutableListOf<MealItem>()
        val snapshots = mutableListOf<TableRowSnapshot>()

        val rows = table.select("tr")
        var skippedCount = 0

        for (row in rows) {
            // Skip header and footer rows
            if (row.hasClass("HeaderStyle") || row.hasClass("FooterStyle") || row.select("th").isNotEmpty()) {
                skippedCount++
                continue
            }

            val cells = row.select("td")
            if (cells.size < 4) {
                skippedCount++
                continue
            }

            // Identify cell with checkbox vs cell with day text
            // In live portal: cell 0 = checkbox, cell 1 = day name (e.g. شنبه 1405/08/16)
            // In demo data: cell 0 = checkbox or day name
            var reserveCell: Element
            var dayText: String
            var selfCell: Element
            var foodCell: Element
            var countCell: Element? = null
            var buyAmountCell: Element? = null

            val cell0HasCheck = cells[0].select("input[type=checkbox]").isNotEmpty()
            if (cell0HasCheck) {
                reserveCell = cells[0]
                dayText = cells[1].text().trim()
                selfCell = cells[2]
                foodCell = cells[3]
                countCell = cells.getOrNull(4)
                buyAmountCell = cells.getOrNull(6)
            } else {
                dayText = cells[0].text().trim()
                val cell1HasCheck = cells[1].select("input[type=checkbox]").isNotEmpty()
                if (cell1HasCheck) {
                    reserveCell = cells[1]
                    selfCell = cells[2]
                    foodCell = cells[3]
                    countCell = cells.getOrNull(4)
                } else {
                    reserveCell = cells.getOrNull(2) ?: cells[0]
                    selfCell = cells.getOrNull(1) ?: cells[0]
                    foodCell = cells.getOrNull(3) ?: cells[0]
                }
            }

            val dayIndex = resolveDayIndex(dayText)
            val dateRegex = Regex("\\d{4}/\\d{2}/\\d{2}")
            val dateJalali = dateRegex.find(dayText)?.value ?: ""

            if (dayIndex in 0..6 && dateJalali.isNotEmpty()) {
                val currentDay = daysList[dayIndex]
                if (currentDay.dateJalali.isEmpty()) {
                    daysList[dayIndex] = currentDay.copy(dateJalali = dateJalali)
                }
            }

            val chkBoxElem = reserveCell.select("input[type=checkbox]").first()
            val isChecked = chkBoxElem?.attr("checked").equals("checked", ignoreCase = true) ||
                    chkBoxElem?.hasAttr("checked") == true
            val isLocked = chkBoxElem?.hasAttr("disabled") == true ||
                    row.select("img[src*='lock']").isNotEmpty() ||
                    row.text().contains("غیرفعال")

            val chkReserveName = chkBoxElem?.attr("name") ?: ""
            val rowControlId = extractRowControlId(chkReserveName, dayIndex)

            val selfSelect = selfCell.select("select[name*='drpSelf']").first() ?: selfCell.select("select").first()
            val foodSelect = foodCell.select("select[name*='drpFood']").first() ?: foodCell.select("select").first()
            val foodCountSelect = countCell?.select("select[name*='drpFoodCount']")?.first() ?: countCell?.select("select")?.first()

            val drpSelfName = selfSelect?.attr("name") ?: ""
            val drpSelfValue = selfSelect?.select("option")?.firstOrNull { it.hasAttr("selected") }?.`val`()
                ?: selfSelect?.select("option[selected]")?.`val`()
                ?: selfSelect?.`val`() ?: "-1"

            val drpFoodName = foodSelect?.attr("name") ?: ""
            val drpFoodValue = foodSelect?.select("option")?.firstOrNull { it.hasAttr("selected") }?.`val`()
                ?: foodSelect?.select("option[selected]")?.`val`()
                ?: foodSelect?.`val`() ?: "-1"

            val drpFoodCountName = foodCountSelect?.attr("name") ?: ""
            val drpFoodCountValue = foodCountSelect?.select("option")?.firstOrNull { it.hasAttr("selected") }?.`val`()
                ?: foodCountSelect?.select("option[selected]")?.`val`()
                ?: foodCountSelect?.`val`() ?: "0"

            val availableSelfOptions = selfSelect?.select("option")?.map { opt ->
                SelfOption(
                    id = opt.`val`(),
                    name = opt.text().trim(),
                    isDefault = opt.hasAttr("selected")
                )
            } ?: emptyList()

            val availableFoodOptions = foodSelect?.select("option")?.map { opt ->
                val rawFoodName = opt.text().trim()
                val priceFromText = parsePriceFromFoodOption(rawFoodName)
                FoodOption(
                    id = opt.`val`(),
                    name = rawFoodName,
                    priceRials = priceFromText,
                    isHalfPortion = rawFoodName.contains("نیم پرس")
                )
            } ?: emptyList()

            val activeOption = availableFoodOptions.firstOrNull { it.id == drpFoodValue && it.id != "-1" }
            val rawFoodName = activeOption?.name
                ?: foodCell.select("span[id*='lblFood']").text().trim()
                .ifEmpty { if (isChecked) "غذای رزرو شده" else "انتخاب غذا" }
            val cleanFoodName = rawFoodName.replace(Regex("\\s*\\d[\\d,]*\\s*ریال.*"), "").trim()

            val activeSelfName = availableSelfOptions.firstOrNull { it.id == drpSelfValue && it.id != "-1" }?.name
                ?: selfCell.select("span[id*='lblSelf']").text().trim()
                .ifEmpty { "سلف مرکزی" }

            val buyPriceText = buyAmountCell?.text()?.trim() ?: ""
            val parsedPrice = parseCreditRials(buyPriceText).takeIf { it > 0 }
                ?: activeOption?.priceRials
                ?: 0L

            val count = drpFoodCountValue.toIntOrNull() ?: if (isChecked) 1 else 0

            val status = when {
                isChecked && isLocked -> ReservationStatus.RESERVED_LOCKED
                isChecked && !isLocked -> ReservationStatus.RESERVED
                !isChecked && isLocked -> ReservationStatus.LOCKED
                else -> ReservationStatus.NOT_RESERVED
            }

            if (rowControlId.isNotEmpty()) {
                snapshots.add(
                    TableRowSnapshot(
                        mealType = mealType,
                        dayIndex = dayIndex,
                        rowControlId = rowControlId,
                        chkReserveName = chkReserveName.ifEmpty { "ctl00\$cphMain\$grdReservation${mealType.name.lowercase().replaceFirstChar { it.uppercase() }}\$$rowControlId\$chkReserve" },
                        isChecked = isChecked,
                        drpSelfName = drpSelfName.ifEmpty { "ctl00\$cphMain\$grdReservation${mealType.name.lowercase().replaceFirstChar { it.uppercase() }}\$$rowControlId\$drpSelf" },
                        drpSelfValue = drpSelfValue,
                        drpFoodName = drpFoodName.ifEmpty { "ctl00\$cphMain\$grdReservation${mealType.name.lowercase().replaceFirstChar { it.uppercase() }}\$$rowControlId\$drpFood" },
                        drpFoodValue = drpFoodValue,
                        drpFoodCountName = drpFoodCountName.ifEmpty { "ctl00\$cphMain\$grdReservation${mealType.name.lowercase().replaceFirstChar { it.uppercase() }}\$$rowControlId\$drpFoodCount" },
                        drpFoodCountValue = drpFoodCountValue
                    )
                )
            }

            val mealItem = MealItem(
                id = "${mealType.name}_${dayIndex}_$rowControlId",
                mealType = mealType,
                dayIndex = dayIndex,
                tableRowIndex = rowControlId,
                foodName = cleanFoodName,
                diningHall = activeSelfName,
                priceRials = parsedPrice,
                priceFormatted = formatRials(parsedPrice),
                calories = 0,
                status = status,
                count = count,
                isEditable = !isLocked,
                selectedFoodId = drpFoodValue,
                selectedSelfId = drpSelfValue,
                availableFoods = availableFoodOptions.map { it.name },
                availableFoodOptions = availableFoodOptions,
                availableSelfOptions = availableSelfOptions
            )

            items.add(mealItem)
        }

        return ParsedTableResult(items, snapshots, true, rows.size, skippedCount)
    }

    private fun parsePriceFromFoodOption(name: String): Long {
        val match = Regex("(\\d[\\d,]*)\\s*ریال").find(name)
        if (match != null) {
            val numStr = match.groupValues[1].replace(",", "")
            return numStr.toLongOrNull() ?: 0L
        }
        return 0L
    }

    private fun populateDayMeals(
        daysList: MutableList<DayMenu>,
        items: List<MealItem>,
        mealType: MealType
    ) {
        val groupedByDay = items.groupBy { it.dayIndex }
        for ((dayIdx, mealItems) in groupedByDay) {
            if (dayIdx in 0..6) {
                val currentDay = daysList[dayIdx]
                val updatedMeals = currentDay.meals.toMutableMap()
                updatedMeals[mealType] = mealItems
                daysList[dayIdx] = currentDay.copy(meals = updatedMeals)
            }
        }
    }

    private fun resolveDayIndex(dayName: String): Int {
        val normalized = dayName.replace("‌", " ").trim() // Replace half-space with space
        return when {
            normalized.contains("چهارشنبه") || normalized.contains("چهار شنبه") -> 4
            normalized.contains("سه شنبه") || normalized.contains("سه‌شنبه") -> 3
            normalized.contains("دوشنبه") || normalized.contains("دو شنبه") -> 2
            normalized.contains("یکشنبه") || normalized.contains("یک شنبه") -> 1
            normalized.contains("پنجشنبه") || normalized.contains("پنج شنبه") -> 5
            normalized.contains("جمعه") -> 6
            normalized.contains("شنبه") -> 0
            else -> 0
        }
    }

    private fun extractRowControlId(controlName: String, fallbackDayIndex: Int = 0): String {
        val matches = Regex("ctl\\d{2}").findAll(controlName).map { it.value }.filter { it != "ctl00" }.toList()
        return matches.lastOrNull() ?: "ctl${String.format("%02d", fallbackDayIndex + 2)}"
    }

    private fun parseCreditRials(rawText: String): Long {
        val digits = rawText.filter { it.isDigit() }
        val value = digits.toLongOrNull() ?: 0L
        return if (rawText.contains("-")) -value else value
    }

    fun formatRials(amount: Long): String {
        return if (amount <= 0) "رایگان / مصوب" else "%,d ریال".format(amount)
    }
}
