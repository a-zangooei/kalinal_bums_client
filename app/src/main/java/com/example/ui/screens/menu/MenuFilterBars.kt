package com.example.ui.screens.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MealType
import com.example.ui.components.PersianRtlLayout
import com.example.ui.screens.DayPillModel
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

private val TealAlpha15 = TealPrimary.copy(alpha = 0.15f)
private val TealAlpha80 = TealPrimary.copy(alpha = 0.8f)
private val ReservedGreen = Color(0xFF2E7D32)

@Composable
internal fun MenuQuickJumpAndFilterBar(
    dayPills: List<DayPillModel>,
    selectedDayIndex: Int,
    selectedMealFilter: MealType?,
    onDayClick: (DayPillModel) -> Unit,
    onMealFilterClick: (MealType?) -> Unit
) {
    if (dayPills.isEmpty()) return

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = TealAlpha15,
        selectedLabelColor = TealPrimary
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "روزهای هفته (مشاهده پیوسته یا پرش مستقیم):",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            dayPills.forEachIndexed { index, pill ->
                val isSelected = pill.dayIndex == selectedDayIndex
                FilterChip(
                    selected = isSelected,
                    onClick = { onDayClick(pill) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pill.dayName)
                            if (pill.isToday) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(امروز)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TealPrimary else TealAlpha80
                                )
                            }
                            if (pill.hasReserved) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(ReservedGreen, CircleShape)
                                )
                            }
                        }
                    },
                    colors = chipColors,
                    modifier = Modifier.testTag("day_tab_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = selectedMealFilter == null,
                onClick = { onMealFilterClick(null) },
                label = { Text("همه وعده‌ها") },
                colors = chipColors,
                modifier = Modifier.testTag("filter_all_meals")
            )
            MealType.entries.forEach { mealType ->
                FilterChip(
                    selected = selectedMealFilter == mealType,
                    onClick = { onMealFilterClick(mealType) },
                    label = { Text(mealType.titlePersian) },
                    colors = chipColors,
                    modifier = Modifier.testTag("filter_${mealType.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private val samplePills = listOf(
    DayPillModel(dayIndex = 0, dayName = "شنبه", isToday = false, hasReserved = true),
    DayPillModel(dayIndex = 1, dayName = "یکشنبه", isToday = true, hasReserved = true),
    DayPillModel(dayIndex = 2, dayName = "دوشنبه", isToday = false, hasReserved = false),
    DayPillModel(dayIndex = 3, dayName = "سه‌شنبه", isToday = false, hasReserved = false),
    DayPillModel(dayIndex = 4, dayName = "چهارشنبه", isToday = false, hasReserved = true)
)

@Preview(showBackground = true, name = "MenuFilterBars - Light RTL")
@Composable
private fun MenuFilterBarsLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            MenuQuickJumpAndFilterBar(
                dayPills = samplePills,
                selectedDayIndex = 1,
                selectedMealFilter = MealType.LUNCH,
                onDayClick = {},
                onMealFilterClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "MenuFilterBars - Dark RTL")
@Composable
private fun MenuFilterBarsDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            MenuQuickJumpAndFilterBar(
                dayPills = samplePills,
                selectedDayIndex = 0,
                selectedMealFilter = null,
                onDayClick = {},
                onMealFilterClick = {}
            )
        }
    }
}
