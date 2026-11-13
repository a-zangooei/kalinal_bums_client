package com.example

import com.example.data.logger.AppLogger
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.model.DayMenu
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.data.model.WeeklyMenu
import com.example.ui.screens.MenuListRow
import com.example.ui.screens.buildFlattenedMenuList
import com.example.ui.screens.filterMealsForDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuListModelsUnitTest {

    private fun sampleMeal(
        id: String,
        dayIndex: Int,
        mealType: MealType,
        status: ReservationStatus = ReservationStatus.NOT_RESERVED
    ): MealItem = MealItem(
        id = id,
        mealType = mealType,
        dayIndex = dayIndex,
        foodName = "Food $id",
        diningHall = "سلف پردیس",
        priceRials = 50000L,
        priceFormatted = "۵۰,۰۰۰ ریال",
        calories = 600,
        status = status,
        count = if (status.isReserved) 1 else 0,
        isEditable = !status.isLocked
    )

    @Test
    fun buildFlattenedMenuList_handlesEmptyOrNullMenu() {
        val flattenedNull = buildFlattenedMenuList(null, null, headerItemCount = 3)
        assertEquals(1, flattenedNull.rows.size)
        assertTrue(flattenedNull.rows.first() is MenuListRow.EmptyWeekRow)
        assertTrue(flattenedNull.dayPills.isEmpty())
        assertTrue(flattenedNull.dayScrollIndices.isEmpty())

        val emptyMenu = WeeklyMenu(weekDateRange = "", days = emptyList())
        val flattenedEmpty = buildFlattenedMenuList(emptyMenu, MealType.LUNCH, headerItemCount = 3)
        assertEquals(listOf(MenuListRow.EmptyWeekRow), flattenedEmpty.rows)
    }

    @Test
    fun buildFlattenedMenuList_producesUniqueKeysAndAccurateScrollIndices() {
        val sat = DayMenu(
            dayIndex = 0,
            dayName = "شنبه",
            isToday = true,
            meals = mapOf(
                MealType.BREAKFAST to listOf(sampleMeal("b0", 0, MealType.BREAKFAST)),
                MealType.LUNCH to listOf(sampleMeal("l0", 0, MealType.LUNCH, ReservationStatus.RESERVED))
            )
        )
        val sun = DayMenu(
            dayIndex = 1,
            dayName = "یکشنبه",
            meals = emptyMap()
        )
        val mon = DayMenu(
            dayIndex = 2,
            dayName = "دوشنبه",
            meals = mapOf(
                MealType.DINNER to listOf(sampleMeal("d2", 2, MealType.DINNER))
            )
        )
        val fri = DayMenu(
            dayIndex = 6,
            dayName = "جمعه",
            meals = mapOf(MealType.LUNCH to listOf(sampleMeal("l6", 6, MealType.LUNCH)))
        )

        val menu = WeeklyMenu(
            weekDateRange = "1405/08/16 الی 1405/08/22",
            days = listOf(sat, sun, mon, fri)
        )

        val flattened = buildFlattenedMenuList(menu, selectedMealFilter = null, headerItemCount = 3)

        // Friday (dayIndex = 6) excluded from 0..4 pills & rows
        assertEquals(3, flattened.dayPills.size)
        assertTrue(flattened.dayPills[0].hasReserved)
        assertTrue(flattened.dayPills[0].isToday)
        assertFalse(flattened.dayPills[1].hasReserved)
        assertFalse(flattened.dayPills[2].hasReserved)

        // Rows order:
        // [0] Sat header -> LazyColumn index 3
        // [1] b0
        // [2] l0
        // [3] Sun header -> LazyColumn index 6
        // [4] Sun empty
        // [5] Mon header -> LazyColumn index 8
        // [6] d2
        assertEquals(7, flattened.rows.size)
        assertEquals(3, flattened.dayScrollIndices[0])
        assertEquals(6, flattened.dayScrollIndices[1])
        assertEquals(8, flattened.dayScrollIndices[2])

        val distinctKeys = flattened.rows.map { it.key }.toSet()
        assertEquals(flattened.rows.size, distinctKeys.size)
    }

    @Test
    fun filterMealsForDay_respectsMealFilterAndPreservesOrder() {
        val day = DayMenu(
            dayIndex = 0,
            dayName = "شنبه",
            meals = mapOf(
                MealType.DINNER to listOf(sampleMeal("d", 0, MealType.DINNER)),
                MealType.BREAKFAST to listOf(sampleMeal("b", 0, MealType.BREAKFAST)),
                MealType.LUNCH to listOf(sampleMeal("l", 0, MealType.LUNCH))
            )
        )

        val allMeals = filterMealsForDay(day, null)
        assertEquals(listOf("b", "l", "d"), allMeals.map { it.id })

        val lunchOnly = filterMealsForDay(day, MealType.LUNCH)
        assertEquals(listOf("l"), lunchOnly.map { it.id })
    }

    @Test
    fun appLogger_assignsUniquePositiveMonotonicIdsToEntries() {
        AppLogger.clearLogs()
        repeat(5) { idx ->
            AppLogger.log(
                LogEntry(
                    timestamp = "",
                    level = LogLevel.INFO,
                    component = "PerfTest",
                    operation = "Emit_$idx",
                    message = "Entry $idx"
                )
            )
        }
        val entries = AppLogger.recentEntries.value
        assertEquals(5, entries.size)
        val ids = entries.map { it.id }
        assertTrue(ids.all { it > 0L })
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.zipWithNext().all { (a, b) -> a < b })
    }
}
