package com.example.data.parser

import com.example.data.model.DayMenu
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.data.model.WeeklyMenu
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object ReservationHtmlParser {

    private val DAY_NAMES = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    /**
     * Parses the HTML content of either the full Reservation.aspx page or the UpdatePanel1
     * fragment into a clean WeeklyMenu object.
     */
    fun parse(html: String, existingViewState: String = "", existingGenerator: String = ""): WeeklyMenu {
        val doc: Document = Jsoup.parse(html)

        // 1. Week Date Range
        val weekDateText = doc.select("#cphMain_lblWeekDate").text().trim()
            .ifEmpty { "1405/08/16 الی 1405/08/22" }

        // 2. Credit Info
        val creditText = doc.select("#cphMain_lblCreditValue").text().trim()
        val creditRials = parseCreditRials(creditText)

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

        // 6. Parse meals for Breakfast, Lunch, Dinner
        val breakfastMeals = parseTableMeals(doc.select("#cphMain_grdReservationBreakfast").first(), MealType.BREAKFAST)
        val lunchMeals = parseTableMeals(doc.select("#cphMain_grdReservationLunch").first(), MealType.LUNCH)
        val dinnerMeals = parseTableMeals(doc.select("#cphMain_grdReservationDinner").first(), MealType.DINNER)

        // Merge meals into their corresponding days
        populateDayMeals(daysList, breakfastMeals, MealType.BREAKFAST)
        populateDayMeals(daysList, lunchMeals, MealType.LUNCH)
        populateDayMeals(daysList, dinnerMeals, MealType.DINNER)

        return WeeklyMenu(
            weekDateRange = weekDateText,
            days = daysList,
            studentName = studentName,
            studentNumber = studentNumber,
            creditText = creditText,
            creditValueRials = creditRials,
            viewState = viewState,
            viewStateGenerator = viewStateGenerator,
            eventValidation = eventValidation
        )
    }

    private fun parseTableMeals(table: Element?, mealType: MealType): List<MealItem> {
        if (table == null) return emptyList()
        val items = mutableListOf<MealItem>()

        val rows = table.select("tr").filter { !it.hasClass("HeaderStyle") && it.select("td").size >= 6 }

        for ((index, row) in rows.withIndex()) {
            val dayIndex = index.coerceIn(0, 6)
            val tds = row.select("td")
            if (tds.size < 6) continue

            // td[0]: Checkbox
            val chkBox = tds[0].select("input[type=checkbox]").first()
            val isChecked = chkBox?.hasAttr("checked") == true
            val isDisabled = chkBox?.hasAttr("disabled") == true || row.hasClass("aspNetDisabled")

            // td[2]: Dining Hall (سلف)
            val selfSelect = tds[2].select("select").first()
            val selectedSelf = selfSelect?.select("option[selected]")?.text()?.trim() ?: "-"
            val diningHall = if (selectedSelf == "-") "" else selectedSelf

            // td[3]: Food selection (غذا)
            val foodSelect = tds[3].select("select").first()
            val selectedFood = foodSelect?.select("option[selected]")?.text()?.trim() ?: "-"
            val availableFoods = foodSelect?.select("option")
                ?.map { it.text().trim() }
                ?.filter { it != "-" && it != "0" && it.isNotEmpty() }
                ?: emptyList()

            // td[4]: Count (تعداد)
            val countSelect = tds[4].select("select").first()
            val count = countSelect?.select("option[selected]")?.text()?.toIntOrNull() ?: if (isChecked) 1 else 0

            // td[5]: Calories (کالری)
            val calorieText = tds[5].text().trim()
            val calories = calorieText.toIntOrNull() ?: 0

            // td[6]: Buy Amount (مبلغ خرید)
            val amountText = if (tds.size > 6) tds[6].text().trim() else "0"
            val priceRials = amountText.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L

            // td[7]: Status Image (وضعیت)
            val statusImg = if (tds.size > 7) tds[7].select("img").first() else null
            val statusTitle = statusImg?.attr("title") ?: ""
            val statusSrc = statusImg?.attr("src") ?: ""

            val status = determineStatus(isChecked, isDisabled, statusTitle, statusSrc)
            val cleanFoodName = extractCleanFoodName(selectedFood)
            val formattedPrice = formatRials(priceRials)
            val id = chkBox?.attr("id") ?: "${mealType.name}_$dayIndex"

            items.add(
                MealItem(
                    id = id,
                    mealType = mealType,
                    dayIndex = dayIndex,
                    foodName = cleanFoodName,
                    diningHall = diningHall,
                    priceRials = priceRials,
                    priceFormatted = formattedPrice,
                    calories = calories,
                    status = status,
                    count = count,
                    availableFoods = availableFoods
                )
            )
        }

        return items
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
        return rawFood.replace(Regex("""\s+\d+\s+ریال\s*$"""), "").trim()
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
