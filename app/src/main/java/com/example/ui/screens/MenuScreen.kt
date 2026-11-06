package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.UserSession
import com.example.data.model.WeekNavigationDirection
import com.example.ui.components.CreditHeader
import com.example.ui.components.MealCard
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.AccentGold
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark
import com.example.ui.viewmodel.MenuViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    userSession: UserSession,
    viewModel: MenuViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weeklyMenu by viewModel.weeklyMenu.collectAsState()
    val selectedDayIndex by viewModel.selectedDayIndex.collectAsState()
    val selectedMealFilter by viewModel.selectedMealFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userSession) {
        viewModel.loadInitialMenu()
    }

    PersianRtlLayout {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(AccentGold),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestaurantMenu,
                                    contentDescription = "کالینان",
                                    tint = TealPrimaryDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "برنامه غذایی هفتگی",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier.testTag("profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "مشخصات دانشجو",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ExitToApp,
                                contentDescription = "خروج از حساب",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TealPrimary,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (isLoading && weeklyMenu == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = TealPrimary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "در حال دریافت برنامه غذایی...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // 1. Credit & Student Header
                        item {
                            CreditHeader(
                                studentName = weeklyMenu?.studentName?.ifEmpty { userSession.studentName } ?: userSession.studentName,
                                studentNumber = weeklyMenu?.studentNumber?.ifEmpty { userSession.studentNumber } ?: userSession.studentNumber,
                                creditText = weeklyMenu?.creditText?.ifEmpty { userSession.creditFormatted } ?: userSession.creditFormatted,
                                onRefresh = { viewModel.refresh() }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 2. Week Navigation Header Card
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("week_navigation_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Previous Week Button
                                    IconButton(
                                        onClick = { viewModel.navigateWeek(WeekNavigationDirection.PREVIOUS) },
                                        enabled = !isLoading,
                                        modifier = Modifier.testTag("prev_week_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Default.ArrowForward,
                                            contentDescription = "هفته قبلی",
                                            tint = TealPrimary
                                        )
                                    }

                                    // Week Date Range
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = "بازه زمانی هفته",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = weeklyMenu?.weekDateRange ?: "در حال بارگذاری...",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.navigateWeek(WeekNavigationDirection.CURRENT) },
                                            enabled = !isLoading,
                                            modifier = Modifier.size(28.dp).testTag("current_week_button")
                                        ) {
                                            Text(
                                                text = "هفته جاری",
                                                fontSize = 11.sp,
                                                color = TealPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Next Week Button
                                    IconButton(
                                        onClick = { viewModel.navigateWeek(WeekNavigationDirection.NEXT) },
                                        enabled = !isLoading,
                                        modifier = Modifier.testTag("next_week_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                            contentDescription = "هفته بعدی",
                                            tint = TealPrimary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 3. Weekday Tabs (شنبه تا جمعه)
                        item {
                            val days = weeklyMenu?.days ?: emptyList()
                            if (days.isNotEmpty()) {
                                ScrollableTabRow(
                                    selectedTabIndex = selectedDayIndex,
                                    edgePadding = 0.dp,
                                    containerColor = Color.Transparent,
                                    contentColor = TealPrimary,
                                    indicator = { tabPositions ->
                                        if (selectedDayIndex < tabPositions.size) {
                                            TabRowDefaults.SecondaryIndicator(
                                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedDayIndex]),
                                                color = TealPrimary,
                                                height = 3.dp
                                            )
                                        }
                                    },
                                    divider = {}
                                ) {
                                    days.forEachIndexed { index, day ->
                                        val isSelected = index == selectedDayIndex
                                        val hasReservedMeals = day.meals.values.flatten().any { it.status.isReserved }

                                        Tab(
                                            selected = isSelected,
                                            onClick = { viewModel.selectDay(index) },
                                            modifier = Modifier.testTag("day_tab_$index")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = day.dayName,
                                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                        color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 14.sp
                                                    )
                                                    if (hasReservedMeals) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF2E7D32))
                                                        )
                                                    }
                                                }
                                                if (day.dateJalali.isNotEmpty()) {
                                                    Text(
                                                        text = day.dateJalali,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 4. Meal Type Filter Chips
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedMealFilter == null,
                                        onClick = { viewModel.selectMealFilter(null) },
                                        label = { Text("همه وعده‌ها") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TealPrimary.copy(alpha = 0.15f),
                                            selectedLabelColor = TealPrimary
                                        ),
                                        modifier = Modifier.testTag("filter_all_meals")
                                    )
                                }
                                items(MealType.values()) { mealType ->
                                    FilterChip(
                                        selected = selectedMealFilter == mealType,
                                        onClick = { viewModel.selectMealFilter(mealType) },
                                        label = { Text(mealType.titlePersian) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TealPrimary.copy(alpha = 0.15f),
                                            selectedLabelColor = TealPrimary
                                        ),
                                        modifier = Modifier.testTag("filter_${mealType.name}")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 5. Meal Cards List for Selected Day
                        val currentDay = weeklyMenu?.days?.getOrNull(selectedDayIndex)
                        val mealsToShow = if (currentDay != null) {
                            if (selectedMealFilter == null) {
                                // Flatten all meals ordered: Breakfast, Lunch, Dinner
                                listOfNotNull(
                                    currentDay.meals[MealType.BREAKFAST],
                                    currentDay.meals[MealType.LUNCH],
                                    currentDay.meals[MealType.DINNER]
                                ).flatten()
                            } else {
                                currentDay.meals[selectedMealFilter] ?: emptyList()
                            }
                        } else {
                            emptyList()
                        }

                        if (mealsToShow.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    shape = RoundedCornerShape(16.dp),
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
                                            text = "هیچ وعده غذایی برای این روز ثبت نشده است",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(mealsToShow) { meal ->
                                MealCard(meal = meal)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        // 6. Phase 1 Read-Only Notice
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "اطلاعیه",
                                        tint = TealPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "مرحله ۱ (مشاهده برنامه غذایی): این نسخه صرفاً نمایشی و فقط‌خواندنی است؛ تغییری در رزروهای شما ایجاد نمی‌شود.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // Student Profile Dialog
        if (showProfileDialog) {
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "مشخصات",
                            tint = TealPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("اطلاعات حساب کاربری", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        ProfileDetailRow("نام و نام خانوادگی", weeklyMenu?.studentName?.ifEmpty { userSession.studentName } ?: "علیرضا زنگویی")
                        ProfileDetailRow("شماره دانشجویی", weeklyMenu?.studentNumber?.ifEmpty { userSession.studentNumber } ?: userSession.studentNumber)
                        ProfileDetailRow("وضعیت مانده اعتبار", weeklyMenu?.creditText?.ifEmpty { userSession.creditFormatted } ?: userSession.creditFormatted)
                        ProfileDetailRow("سامانه متصل", "سامانه کالینان (jeton.bums.ac.ir)")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("بستن", color = TealPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileDetailRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
