package com.example.data.model

import androidx.compose.runtime.Immutable

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

@Immutable
data class FoodOption(
    val id: String,
    val name: String,
    val priceRials: Long,
    val isHalfPortion: Boolean = false
)

@Immutable
data class SelfOption(
    val id: String,
    val name: String,
    val isDefault: Boolean = false
)

@Immutable
data class TableRowSnapshot(
    val mealType: MealType,
    val dayIndex: Int,
    val rowControlId: String, // e.g. "ctl02", "ctl06"
    val chkReserveName: String, // e.g. "ctl00$cphMain$grdReservationLunch$ctl06$chkReserve"
    val isChecked: Boolean,
    val drpSelfName: String, // e.g. "ctl00$cphMain$grdReservationLunch$ctl06$drpSelf"
    val drpSelfValue: String, // e.g. "1" or "-1"
    val drpFoodName: String, // e.g. "ctl00$cphMain$grdReservationLunch$ctl06$drpFood"
    val drpFoodValue: String, // e.g. "89247" or "-1"
    val drpFoodCountName: String, // e.g. "ctl00$cphMain$grdReservationLunch$ctl06$drpFoodCount"
    val drpFoodCountValue: String // e.g. "1" or "0"
)

@Immutable
data class MealItem(
    val id: String,
    val mealType: MealType,
    val dayIndex: Int, // 0: شنبه, ..., 6: جمعه
    val tableRowIndex: String = "ctl02", // ctl02 to ctl08
    val foodName: String,
    val diningHall: String,
    val priceRials: Long,
    val priceFormatted: String,
    val calories: Int,
    val status: ReservationStatus,
    val count: Int,
    val isEditable: Boolean = false,
    val selectedFoodId: String = "-1",
    val selectedSelfId: String = "-1",
    val availableFoods: List<String> = emptyList(),
    val availableFoodOptions: List<FoodOption> = emptyList(),
    val availableSelfOptions: List<SelfOption> = emptyList()
)

@Immutable
data class DayMenu(
    val dayIndex: Int, // 0 to 6
    val dayName: String, // شنبه، یکشنبه، ...
    val dateJalali: String = "", // e.g. 1405/08/16
    val isToday: Boolean = false,
    val meals: Map<MealType, List<MealItem>> = emptyMap()
)

@Immutable
data class WeeklyMenu(
    val weekDateRange: String, // e.g. "1405/08/16 الی 1405/08/22"
    val days: List<DayMenu>,
    val studentName: String = "",
    val studentNumber: String = "",
    val creditText: String = "",
    val creditValueRials: Long = 0,
    val viewState: String = "",
    val viewStateGenerator: String = "",
    val eventValidation: String = "",
    val tableRows: List<TableRowSnapshot> = emptyList(),
    val scriptManagerName: String = "ctl00\$ScriptManager",
    val updatePanelName: String = "ctl00\$UpdatePanel1",
    val alertMessage: String? = null
)

enum class WeekNavigationDirection {
    PREVIOUS,
    CURRENT,
    NEXT
}

@Immutable
data class ReservationResult(
    val isSuccess: Boolean,
    val message: String,
    val updatedMenu: WeeklyMenu? = null,
    val toastType: String = ""
)
