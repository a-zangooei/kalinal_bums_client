package com.example.ui.screens.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

private val WeekCardShape = RoundedCornerShape(16.dp)
private val CurrentWeekBtnShape = RoundedCornerShape(10.dp)
private val TealAlpha14 = TealPrimary.copy(alpha = 0.14f)
private val TealAlpha18 = TealPrimary.copy(alpha = 0.18f)

@Composable
internal fun WeekNavigationCard(
    weekDateRange: String,
    weekOffset: Int,
    isLoading: Boolean,
    enabled: Boolean,
    onPrevWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val navTint = if (enabled) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val cardBorder = remember(outlineColor) {
        BorderStroke(1.dp, outlineColor.copy(alpha = 0.7f))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("week_navigation_card"),
        shape = WeekCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onPrevWeek,
                        enabled = enabled,
                        modifier = Modifier.testTag("prev_week_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "هفته قبلی",
                            tint = navTint
                        )
                    }
                    Text(
                        text = "قبلی",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp,
                                color = TealPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "بازه زمانی هفته",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = weekDateRange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        onClick = onCurrentWeek,
                        enabled = enabled,
                        shape = CurrentWeekBtnShape,
                        color = if (weekOffset != 0) TealAlpha14 else Color.Transparent,
                        modifier = Modifier.testTag("current_week_button")
                    ) {
                        Text(
                            text = if (weekOffset != 0) "بازگشت به هفته جاری" else "هفته جاری (برای تغییر بکشید)",
                            fontSize = 11.sp,
                            color = if (weekOffset != 0) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (weekOffset != 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onNextWeek,
                        enabled = enabled,
                        modifier = Modifier.testTag("next_week_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "هفته بعدی",
                            tint = navTint
                        )
                    }
                    Text(
                        text = "بعدی",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = TealPrimary,
                    trackColor = TealAlpha18
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "WeekNavigationCard - Light RTL")
@Composable
private fun WeekNavigationCardLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            WeekNavigationCard(
                weekDateRange = "۱۴۰۵/۰۸/۱۶ الی ۱۴۰۵/۰۸/۲۲",
                weekOffset = 0,
                isLoading = false,
                enabled = true,
                onPrevWeek = {},
                onCurrentWeek = {},
                onNextWeek = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "WeekNavigationCard - Dark RTL")
@Composable
private fun WeekNavigationCardDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            WeekNavigationCard(
                weekDateRange = "۱۴۰۵/۰۸/۲۳ الی ۱۴۰۵/۰۸/۲۹",
                weekOffset = 1,
                isLoading = true,
                enabled = false,
                onPrevWeek = {},
                onCurrentWeek = {},
                onNextWeek = {}
            )
        }
    }
}
