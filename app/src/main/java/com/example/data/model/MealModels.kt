package com.example.data.model

enum class MealType(val titlePersian: String, val tabId: String) {
    BREAKFAST("صبحانه", "css3-tabstrip-0-0"),
    LUNCH("ناهار", "css3-tabstrip-0-1"),
    DINNER("شام", "css3-tabstrip-0-2")
}

enum class ReservationStatus(
    val titlePersian: String,
    val isReserved: Boolean,
    val isLocked: Boolean
) {
    RESERVED("رزرو شده", true, false),
    RESERVED_LOCKED("رزرو شده (قفل)", true, true),
    NOT_RESERVED("رزرو نشده", false, false),
    LOCKED("غیرفعال / پایان مهلت", false, true),
    CONSUMED("تحویل داده شده", true, true)
}

data class MealItem(
    val id: String,
    val mealType: MealType,
    val dayIndex: Int, // 0: شنبه, ..., 6: جمعه
    val foodName: String,
    val diningHall: String,
    val priceRials: Long,
    val priceFormatted: String,
    val calories: Int,
    val status: ReservationStatus,
    val count: Int,
    val availableFoods: List<String> = emptyList()
)

data class DayMenu(
    val dayIndex: Int, // 0 to 6
    val dayName: String, // شنبه، یکشنبه، ...
    val dateJalali: String, // e.g. 1405/08/16
    val isToday: Boolean = false,
    val meals: Map<MealType, List<MealItem>> = emptyMap()
)

data class WeeklyMenu(
    val weekDateRange: String, // e.g. "1405/08/16 الی 1405/08/22"
    val days: List<DayMenu>,
    val studentName: String = "",
    val studentNumber: String = "",
    val creditText: String = "",
    val creditValueRials: Long = 0,
    val viewState: String = "",
    val viewStateGenerator: String = "",
    val eventValidation: String = ""
)

enum class WeekNavigationDirection {
    PREVIOUS,
    CURRENT,
    NEXT
}
