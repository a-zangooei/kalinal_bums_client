package com.example.ui.screens.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DayMenu
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

private val WeekCardShape = RoundedCornerShape(16.dp)
private val DayHeaderShape = RoundedCornerShape(14.dp)
private val DayEmptyCardShape = RoundedCornerShape(12.dp)
private val NoticeCardShape = RoundedCornerShape(12.dp)
private val TodayBadgeShape = RoundedCornerShape(6.dp)
private val ReservedBadgeShape = RoundedCornerShape(8.dp)

private val TealAlpha08 = TealPrimary.copy(alpha = 0.08f)
private val TodayDayBorder = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.45f))
private val ReservedGreen = Color(0xFF2E7D32)
private val ReservedGreenBg = Color(0xFFE8F5E9)

@Composable
internal fun MenuLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = TealPrimary)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "در حال دریافت برنامه غذایی از سرور دانشگاه...",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
internal fun EmptyWeekCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = WeekCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = "بدون برنامه",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "هیچ برنامه‌ای برای این هفته دریافت نشد",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun DayHeaderCard(
    day: DayMenu,
    isFirstDay: Boolean,
    hasReserved: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirstDay) 0.dp else 16.dp, bottom = 8.dp),
        shape = DayHeaderShape,
        colors = CardDefaults.cardColors(
            containerColor = if (day.isToday) TealAlpha08 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (day.isToday) TodayDayBorder else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (day.isToday) TealPrimary else MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.dayName.take(1),
                        color = if (day.isToday) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = day.dayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (day.isToday) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = TodayBadgeShape,
                                color = TealPrimary,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "امروز",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (day.dateJalali.isNotEmpty()) {
                        Text(
                            text = day.dateJalali,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (hasReserved) {
                Surface(
                    shape = ReservedBadgeShape,
                    color = ReservedGreenBg
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(ReservedGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "دارای رزرو",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ReservedGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DayEmptyCard(filterTitlePersian: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = DayEmptyCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
    ) {
        Text(
            text = if (filterTitlePersian != null) {
                "هیچ وعده $filterTitlePersian برای این روز ثبت نشده است"
            } else {
                "هیچ وعده غذایی برای این روز ثبت نشده است"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun ReservationNoticeCard() {
    Spacer(modifier = Modifier.height(16.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NoticeCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "اطلاعیه",
                tint = TealPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "سامانه رزرو و لغو صبحانه، ناهار و شام فعال است. درخواست‌ها به صورت مستقیم با سرور سامانه تغذیه کالینان (jeton.bums.ac.ir) مبادله می‌شوند.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun MenuRowsPreviewContent() {
    val sampleDay = DayMenu(
        dayIndex = 1,
        dayName = "یکشنبه",
        dateJalali = "۱۴۰۵/۰۸/۱۷",
        isToday = true
    )
    Column(modifier = Modifier.padding(16.dp)) {
        DayHeaderCard(day = sampleDay, isFirstDay = true, hasReserved = true)
        DayEmptyCard(filterTitlePersian = "ناهار")
        EmptyWeekCard()
        ReservationNoticeCard()
    }
}

@Preview(showBackground = true, name = "MenuRowCards - Light RTL")
@Composable
private fun MenuRowsLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            MenuRowsPreviewContent()
        }
    }
}

@Preview(showBackground = true, name = "MenuRowCards - Dark RTL")
@Composable
private fun MenuRowsDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            MenuRowsPreviewContent()
        }
    }
}
