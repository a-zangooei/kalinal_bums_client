package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FoodOption
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.SelfOption
import com.example.data.model.UserSession
import com.example.data.model.WeekNavigationDirection
import com.example.data.parser.ReservationHtmlParser
import com.example.ui.components.CreditHeader
import com.example.ui.components.MealCard
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.AccentGold
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark
import com.example.ui.viewmodel.MenuViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch

private val MenuListPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private val WeekCardShape = RoundedCornerShape(16.dp)
private val CurrentWeekBtnShape = RoundedCornerShape(10.dp)
private val DayHeaderShape = RoundedCornerShape(14.dp)
private val DayEmptyCardShape = RoundedCornerShape(12.dp)
private val NoticeCardShape = RoundedCornerShape(12.dp)
private val TodayBadgeShape = RoundedCornerShape(6.dp)
private val ReservedBadgeShape = RoundedCornerShape(8.dp)

private val TealAlpha08 = TealPrimary.copy(alpha = 0.08f)
private val TealAlpha14 = TealPrimary.copy(alpha = 0.14f)
private val TealAlpha15 = TealPrimary.copy(alpha = 0.15f)
private val TealAlpha18 = TealPrimary.copy(alpha = 0.18f)
private val TealAlpha80 = TealPrimary.copy(alpha = 0.8f)
private val TodayDayBorder = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.45f))
private val ReservedGreen = Color(0xFF2E7D32)
private val ReservedGreenBg = Color(0xFFE8F5E9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    userSession: UserSession,
    viewModel: MenuViewModel,
    onLogout: () -> Unit,
    isLoggingOut: Boolean = false,
    modifier: Modifier = Modifier
) {
    val weeklyMenu by viewModel.weeklyMenu.collectAsState()
    val selectedDayIndex by viewModel.selectedDayIndex.collectAsState()
    val selectedMealFilter by viewModel.selectedMealFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    val processingDayIndex by viewModel.processingDayIndex.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val userFeedback by viewModel.userFeedback.collectAsState()
    val weekOffset by viewModel.weekOffset.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }
    var mealToReserve by remember { mutableStateOf<MealItem?>(null) }
    var mealToCancel by remember { mutableStateOf<MealItem?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val flattenedMenu = remember(weeklyMenu, selectedMealFilter) {
        buildFlattenedMenuList(
            weeklyMenu = weeklyMenu,
            selectedMealFilter = selectedMealFilter,
            headerItemCount = 3
        )
    }

    val currentHasMenu by rememberUpdatedState(weeklyMenu != null)
    val currentIsLoading by rememberUpdatedState(isLoading)
    val currentIsMutating by rememberUpdatedState(isMutating)

    val onRefreshCallback = remember(viewModel) {
        { if (!currentIsMutating) viewModel.refresh() }
    }
    val onReserveCallback = remember {
        { meal: MealItem -> if (!currentIsMutating) mealToReserve = meal }
    }
    val onCancelCallback = remember {
        { meal: MealItem -> if (!currentIsMutating) mealToCancel = meal }
    }

    LaunchedEffect(userSession) {
        viewModel.loadInitialMenu()
    }

    LaunchedEffect(userFeedback) {
        userFeedback?.let { (msg, _) ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearFeedback()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    PersianRtlLayout {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                            enabled = !isLoggingOut,
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            if (isLoggingOut) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Default.ExitToApp,
                                    contentDescription = "خروج از حساب",
                                    tint = Color.White
                                )
                            }
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
                    .pointerInput(viewModel) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!currentHasMenu) return@awaitEachGesture
                            val touchSlop = viewConfiguration.touchSlop
                            var totalDx = 0f
                            var totalDy = 0f
                            val pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) break
                                if (change.isConsumed) continue
                                val delta = change.position - change.previousPosition
                                totalDx += delta.x
                                totalDy += delta.y
                                // Bail out early once vertical scrolling is clear so vertical flings don't compute swipe math
                                if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                                    break
                                }
                                if (abs(totalDx) > 96f && abs(totalDx) > abs(totalDy) * 1.6f) {
                                    if (!currentIsLoading && !currentIsMutating) {
                                        if (totalDx > 0f) {
                                            viewModel.navigateWeek(WeekNavigationDirection.PREVIOUS)
                                        } else {
                                            viewModel.navigateWeek(WeekNavigationDirection.NEXT)
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
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
                            text = "در حال دریافت برنامه غذایی از سرور دانشگاه...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealAlpha15,
                        selectedLabelColor = TealPrimary
                    )
                    val weekButtonsEnabled = !isLoading && !isMutating && weeklyMenu != null

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = MenuListPadding
                    ) {
                        // 1. Credit & Student Header
                        item(key = "credit_header", contentType = "credit_header") {
                            CreditHeader(
                                studentName = weeklyMenu?.studentName?.ifEmpty { userSession.studentName } ?: userSession.studentName,
                                studentNumber = weeklyMenu?.studentNumber?.ifEmpty { userSession.studentNumber } ?: userSession.studentNumber,
                                creditText = weeklyMenu?.creditText?.ifEmpty { userSession.creditFormatted } ?: userSession.creditFormatted,
                                onRefresh = onRefreshCallback
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 2. Week Navigation Header Card
                        item(key = "week_navigation", contentType = "week_navigation") {
                            WeekNavigationCard(
                                weekDateRange = weeklyMenu?.weekDateRange ?: "در حال بارگذاری...",
                                weekOffset = weekOffset,
                                isLoading = isLoading,
                                enabled = weekButtonsEnabled,
                                onPrevWeek = { viewModel.navigateWeek(WeekNavigationDirection.PREVIOUS) },
                                onCurrentWeek = { viewModel.navigateWeek(WeekNavigationDirection.CURRENT) },
                                onNextWeek = { viewModel.navigateWeek(WeekNavigationDirection.NEXT) }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 3. Quick-jump Day Bar & Meal Type Filter (lightweight horizontalScroll Rows instead of nested LazyRow)
                        item(key = "quick_jump_filters", contentType = "quick_jump_filters") {
                            val dayPills = flattenedMenu.dayPills
                            if (dayPills.isNotEmpty()) {
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
                                                onClick = {
                                                    viewModel.selectDay(pill.dayIndex)
                                                    val targetIndex = flattenedMenu.dayScrollIndices[pill.dayIndex] ?: 3
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(targetIndex)
                                                    }
                                                },
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
                                                                    .clip(CircleShape)
                                                                    .background(ReservedGreen)
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
                                            onClick = { viewModel.selectMealFilter(null) },
                                            label = { Text("همه وعده‌ها") },
                                            colors = chipColors,
                                            modifier = Modifier.testTag("filter_all_meals")
                                        )
                                        MealType.entries.forEach { mealType ->
                                            FilterChip(
                                                selected = selectedMealFilter == mealType,
                                                onClick = { viewModel.selectMealFilter(mealType) },
                                                label = { Text(mealType.titlePersian) },
                                                colors = chipColors,
                                                modifier = Modifier.testTag("filter_${mealType.name}")
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }

                        // 4. Continuous Flattened All-Week Days and Meals (key + contentType for fast recycling)
                        items(
                            items = flattenedMenu.rows,
                            key = { it.key },
                            contentType = { it.contentType }
                        ) { row ->
                            when (row) {
                                MenuListRow.EmptyWeekRow -> EmptyWeekCard()
                                is MenuListRow.DayHeaderRow -> DayHeaderCard(
                                    day = row.day,
                                    isFirstDay = row.isFirstDay,
                                    hasReserved = row.hasReserved
                                )
                                is MenuListRow.DayEmptyRow -> DayEmptyCard(
                                    filterTitlePersian = row.filterTitlePersian
                                )
                                is MenuListRow.MealCardRow -> {
                                    MealCard(
                                        meal = row.meal,
                                        isProcessing = isMutating && processingDayIndex == row.meal.dayIndex,
                                        onReserveClick = onReserveCallback,
                                        onCancelClick = onCancelCallback,
                                        onChangeFoodClick = onReserveCallback
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        // 6. Reservation System Notice
                        item(key = "reservation_notice", contentType = "reservation_notice") {
                            ReservationNoticeCard()
                        }
                    }
                }
            }
        }

        // Food & Self Selection Bottom Sheet
        mealToReserve?.let { meal ->
            ReservationBottomSheet(
                meal = meal,
                dayName = weeklyMenu?.days?.getOrNull(meal.dayIndex)?.dayName ?: "روز انتخاب‌شده",
                dateJalali = weeklyMenu?.days?.getOrNull(meal.dayIndex)?.dateJalali ?: "",
                currentCreditRials = weeklyMenu?.creditValueRials ?: 0L,
                onDismiss = { mealToReserve = null },
                onConfirm = { selectedFoodId, selectedSelfId ->
                    val dayIdx = meal.dayIndex
                    val mealType = meal.mealType
                    mealToReserve = null
                    viewModel.reserveMeal(mealType, dayIdx, selectedFoodId, selectedSelfId)
                }
            )
        }

        // Cancel Confirmation Dialog
        mealToCancel?.let { meal ->
            val dayName = weeklyMenu?.days?.getOrNull(meal.dayIndex)?.dayName ?: "روز انتخاب‌شده"
            val mealTitle = meal.mealType.titlePersian
            AlertDialog(
                onDismissRequest = { mealToCancel = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "هشدار",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("لغو رزرو $mealTitle", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "آیا از لغو رزرو وعده $mealTitle «$dayName» اطمینان دارید؟",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "غذای فعلی: ${meal.foodName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (meal.priceRials > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "مبلغ ${meal.priceFormatted} به مانده اعتبار شما بازگردانده خواهد شد.",
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val dayIdx = meal.dayIndex
                            val mealType = meal.mealType
                            mealToCancel = null
                            viewModel.cancelMeal(mealType, dayIdx)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_cancel_lunch_button")
                    ) {
                        Text("بله، لغو رزرو شود", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mealToCancel = null }) {
                        Text("انصراف")
                    }
                }
            )
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
                        ProfileDetailRow("نام و نام خانوادگی", weeklyMenu?.studentName?.ifEmpty { userSession.studentName } ?: userSession.studentName)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservationBottomSheet(
    meal: MealItem,
    dayName: String,
    dateJalali: String,
    currentCreditRials: Long,
    onDismiss: () -> Unit,
    onConfirm: (selectedFoodId: String, selectedSelfId: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val foodOptions = meal.availableFoodOptions.ifEmpty {
        if (meal.selectedFoodId != "-1" && meal.foodName.isNotEmpty()) {
            listOf(FoodOption(meal.selectedFoodId, meal.foodName, meal.priceRials))
        } else {
            emptyList()
        }
    }

    var selectedFoodId by remember {
        mutableStateOf(
            meal.selectedFoodId.takeIf { it != "-1" && foodOptions.any { f -> f.id == it } }
                ?: foodOptions.firstOrNull()?.id
                ?: "-1"
        )
    }

    val selfOptions = meal.availableSelfOptions.ifEmpty {
        listOf(SelfOption("1", meal.diningHall.ifEmpty { "سلف پردیس" }, isDefault = true))
    }

    var selectedSelfId by remember {
        mutableStateOf(
            meal.selectedSelfId.takeIf { it != "-1" && selfOptions.any { s -> s.id == it } }
                ?: selfOptions.firstOrNull { it.isDefault }?.id
                ?: selfOptions.firstOrNull()?.id
                ?: "1"
        )
    }

    var selfDropdownExpanded by remember { mutableStateOf(false) }

    val selectedFood = foodOptions.find { it.id == selectedFoodId }
    val foodPrice = selectedFood?.priceRials ?: 0L
    val remainingCredit = currentCreditRials - foodPrice
    val isInsufficientCredit = currentCreditRials > 0 && remainingCredit < 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        PersianRtlLayout {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "رزرو ${meal.mealType.titlePersian} $dayName",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (dateJalali.isNotEmpty()) {
                            Text(
                                text = "تاریخ: $dateJalali",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(TealPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fastfood,
                            contentDescription = "غذا",
                            tint = TealPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // 1. Food Options Selection
                Text(
                    text = "انتخاب غذا و پرس:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (foodOptions.isEmpty()) {
                    Text(
                        text = "هیچ گزینه غذایی برای این روز یافت نشد.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    foodOptions.forEach { option ->
                        val isSelected = option.id == selectedFoodId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedFoodId = option.id }
                                .testTag("food_option_${option.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) TealPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, TealPrimary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedFoodId = option.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = TealPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = option.name,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (option.isHalfPortion) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFE0F2F1))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "نیم‌پرس",
                                                    fontSize = 10.sp,
                                                    color = TealPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = ReservationHtmlParser.formatRials(option.priceRials),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Dining Hall Selection
                Text(
                    text = "سلف توزیع غذا:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = selfDropdownExpanded,
                    onExpandedChange = { selfDropdownExpanded = !selfDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentSelfName = selfOptions.find { it.id == selectedSelfId }?.name ?: "پردیس"
                    OutlinedTextField(
                        value = currentSelfName,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, contentDescription = "سلف", tint = TealPrimary)
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = selfDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = selfDropdownExpanded,
                        onDismissRequest = { selfDropdownExpanded = false }
                    ) {
                        selfOptions.forEach { selfOpt ->
                            DropdownMenuItem(
                                text = { Text(selfOpt.name) },
                                onClick = {
                                    selectedSelfId = selfOpt.id
                                    selfDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Price & Credit Breakdown Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("اعتبار فعلی شما:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(ReservationHtmlParser.formatRials(currentCreditRials), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("مبلغ غذا:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(ReservationHtmlParser.formatRials(foodPrice), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("مانده پس از رزرو:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = ReservationHtmlParser.formatRials(remainingCredit),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingCredit >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (isInsufficientCredit) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "توجه: مانده اعتبار شما برای این رزرو کافی نیست، اما درخواست ارسال خواهد شد.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4. Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("انصراف")
                    }

                    Button(
                        onClick = { onConfirm(selectedFoodId, selectedSelfId) },
                        enabled = selectedFoodId != "-1",
                        modifier = Modifier
                            .weight(2f)
                            .height(46.dp)
                            .testTag("submit_reservation_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تأیید و ذخیره رزرو", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
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

@Composable
private fun WeekNavigationCard(
    weekDateRange: String,
    weekOffset: Int,
    isLoading: Boolean,
    enabled: Boolean,
    onPrevWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val navTint = if (enabled) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("week_navigation_card"),
        shape = WeekCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
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

@Composable
private fun EmptyWeekCard() {
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
private fun DayHeaderCard(
    day: com.example.data.model.DayMenu,
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
                        .clip(CircleShape)
                        .background(if (day.isToday) TealPrimary else MaterialTheme.colorScheme.primaryContainer),
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
                                .clip(CircleShape)
                                .background(ReservedGreen)
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
private fun DayEmptyCard(filterTitlePersian: String?) {
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
private fun ReservationNoticeCard() {
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

