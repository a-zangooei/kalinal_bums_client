package com.example.ui.screens

import androidx.compose.runtime.Immutable
import com.example.data.model.DayMenu
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.WeeklyMenu

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
        override val contentType: String = "meal_card"
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
