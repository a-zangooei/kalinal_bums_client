package com.example.ui.screens

import androidx.compose.runtime.Immutable
import com.example.data.model.DayMenu
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.data.model.WeekNavigationDirection
import com.example.data.model.WeeklyMenu
import kotlin.math.abs

fun isMealActionable(meal: MealItem): Boolean = meal.isEditable && (
    meal.status == ReservationStatus.RESERVED ||
    (meal.status == ReservationStatus.NOT_RESERVED && (
        meal.availableFoodOptions.isNotEmpty() ||
        (meal.selectedFoodId != "-1" && meal.foodName.isNotBlank() && meal.foodName != "غذا انتخاب نشده است")
    ))
)

/**
 * Resolves horizontal swipe gesture into week navigation direction.
 * Adjusted to match natural RTL user expectation where swiping toward the right direction triggers PREVIOUS
 * and swiping toward the left triggers NEXT.
 */
fun resolveWeekSwipeDirection(
    totalDx: Float,
    totalDy: Float,
    minSwipeDistancePx: Float = 96f
): WeekNavigationDirection? {
    if (abs(totalDx) <= minSwipeDistancePx || abs(totalDx) <= abs(totalDy) * 1.6f) {
        return null
    }
    return if (totalDx < 0f) {
        WeekNavigationDirection.PREVIOUS
    } else {
        WeekNavigationDirection.NEXT
    }
}

@Immutable
data class DayPillModel(
    val dayIndex: Int,
    val dayName: String,
    val isToday: Boolean,
    val hasReserved: Boolean
)

@Immutable
sealed interface MenuListRow {
    val key: String
    val contentType: String

    @Immutable
    object EmptyWeekRow : MenuListRow {
        override val key: String = "empty_week"
        override val contentType: String = "empty_week"
    }

    @Immutable
    data class DayHeaderRow(
        val day: DayMenu,
        val isFirstDay: Boolean,
        val hasReserved: Boolean
    ) : MenuListRow {
        override val key: String = "day_header_${day.dayIndex}"
        override val contentType: String = "day_header"
    }

    @Immutable
    data class DayEmptyRow(
        val dayIndex: Int,
        val filterTitlePersian: String?
    ) : MenuListRow {
        override val key: String = "day_empty_$dayIndex"
        override val contentType: String = "day_empty"
    }

    @Immutable
    data class MealCardRow(
        val meal: MealItem
    ) : MenuListRow {
        override val key: String = "meal_${meal.id}_${meal.dayIndex}_${meal.mealType.name}"
        override val contentType: String = when {
            !isMealActionable(meal) -> "meal_card_readonly"
            meal.status == ReservationStatus.NOT_RESERVED -> "meal_card_reserve"
            meal.status == ReservationStatus.RESERVED && meal.availableFoodOptions.size > 1 -> "meal_card_reserved_2btn"
            meal.status == ReservationStatus.RESERVED -> "meal_card_reserved_1btn"
            else -> "meal_card_readonly"
        }
    }
}

@Immutable
data class FlattenedMenuList(
    val rows: List<MenuListRow>,
    val dayPills: List<DayPillModel>,
    val dayScrollIndices: Map<Int, Int>
)

fun filterMealsForDay(day: DayMenu, filter: MealType?): List<MealItem> {
    if (filter != null) {
        return day.meals[filter] ?: emptyList()
    }
    val breakfast = day.meals[MealType.BREAKFAST]
    val lunch = day.meals[MealType.LUNCH]
    val dinner = day.meals[MealType.DINNER]
    val totalSize = (breakfast?.size ?: 0) + (lunch?.size ?: 0) + (dinner?.size ?: 0)
    if (totalSize == 0) return emptyList()
    return ArrayList<MealItem>(totalSize).apply {
        if (breakfast != null) addAll(breakfast)
        if (lunch != null) addAll(lunch)
        if (dinner != null) addAll(dinner)
    }
}

fun buildFlattenedMenuList(
    weeklyMenu: WeeklyMenu?,
    selectedMealFilter: MealType?,
    headerItemCount: Int = 3
): FlattenedMenuList {
    val visibleDays = weeklyMenu?.days?.filter { it.dayIndex in 0..4 }.orEmpty()
    if (visibleDays.isEmpty()) {
        return FlattenedMenuList(
            rows = listOf(MenuListRow.EmptyWeekRow),
            dayPills = emptyList(),
            dayScrollIndices = emptyMap()
        )
    }

    val dayPills = ArrayList<DayPillModel>(visibleDays.size)
    val rows = ArrayList<MenuListRow>(visibleDays.size * 4)
    val dayScrollIndices = HashMap<Int, Int>(visibleDays.size)

    for (i in visibleDays.indices) {
        val day = visibleDays[i]
        val hasReserved = day.meals.values.any { mealList ->
            mealList.any { it.status.isReserved }
        }
        dayPills.add(
            DayPillModel(
                dayIndex = day.dayIndex,
                dayName = day.dayName,
                isToday = day.isToday,
                hasReserved = hasReserved
            )
        )

        dayScrollIndices[day.dayIndex] = headerItemCount + rows.size
        rows.add(
            MenuListRow.DayHeaderRow(
                day = day,
                isFirstDay = i == 0,
                hasReserved = hasReserved
            )
        )

        val dayMeals = filterMealsForDay(day, selectedMealFilter)
        if (dayMeals.isEmpty()) {
            rows.add(
                MenuListRow.DayEmptyRow(
                    dayIndex = day.dayIndex,
                    filterTitlePersian = selectedMealFilter?.titlePersian
                )
            )
        } else {
            for (meal in dayMeals) {
                rows.add(MenuListRow.MealCardRow(meal))
            }
        }
    }

    return FlattenedMenuList(
        rows = rows,
        dayPills = dayPills,
        dayScrollIndices = dayScrollIndices
    )
}
