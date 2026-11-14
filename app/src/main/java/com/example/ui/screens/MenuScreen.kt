package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DayMenu
import com.example.data.model.FoodOption
import com.example.data.model.MealItem
import com.example.data.model.MealType
import com.example.data.model.ReservationStatus
import com.example.data.model.UserSession
import com.example.data.model.WeekNavigationDirection
import com.example.data.model.WeeklyMenu
import com.example.ui.components.CreditHeader
import com.example.ui.components.MealCard
import com.example.ui.components.PersianRtlLayout
import com.example.ui.screens.menu.CancelMealConfirmationDialog
import com.example.ui.screens.menu.DayEmptyCard
import com.example.ui.screens.menu.DayHeaderCard
import com.example.ui.screens.menu.EmptyWeekCard
import com.example.ui.screens.menu.MenuLoadingState
import com.example.ui.screens.menu.MenuQuickJumpAndFilterBar
import com.example.ui.screens.menu.MenuTopBar
import com.example.ui.screens.menu.ReservationBottomSheet
import com.example.ui.screens.menu.StudentProfileDialog
import com.example.ui.screens.menu.WeekNavigationCard
import com.example.ui.theme.CullinanTheme
import com.example.ui.viewmodel.MenuViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch

private val MenuListPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Composable
fun MenuScreen(
    userSession: UserSession,
    viewModel: MenuViewModel,
    onLogout: () -> Unit,
    isLoggingOut: Boolean = false,
    modifier: Modifier = Modifier
) {
    val weeklyMenu by viewModel.weeklyMenu.collectAsStateWithLifecycle()
    val selectedDayIndex by viewModel.selectedDayIndex.collectAsStateWithLifecycle()
    val selectedMealFilter by viewModel.selectedMealFilter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val processingDayIndex by viewModel.processingDayIndex.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val userFeedback by viewModel.userFeedback.collectAsStateWithLifecycle()
    val weekOffset by viewModel.weekOffset.collectAsStateWithLifecycle()

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
    val onNavigateWeek = remember(viewModel) {
        { direction: WeekNavigationDirection -> viewModel.navigateWeek(direction) }
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

    MenuScreenContent(
        userSession = userSession,
        weeklyMenu = weeklyMenu,
        flattenedMenu = flattenedMenu,
        selectedDayIndex = selectedDayIndex,
        selectedMealFilter = selectedMealFilter,
        isLoading = isLoading,
        isMutating = isMutating,
        processingDayIndex = processingDayIndex,
        weekOffset = weekOffset,
        isLoggingOut = isLoggingOut,
        snackbarHostState = snackbarHostState,
        listState = listState,
        onOpenProfile = { showProfileDialog = true },
        onLogout = onLogout,
        onRefresh = onRefreshCallback,
        onNavigateWeek = onNavigateWeek,
        onDaySelected = { pill ->
            viewModel.selectDay(pill.dayIndex)
            val targetIndex = flattenedMenu.dayScrollIndices[pill.dayIndex] ?: 3
            coroutineScope.launch {
                listState.animateScrollToItem(targetIndex)
            }
        },
        onMealFilterSelected = { filter -> viewModel.selectMealFilter(filter) },
        onReserveClick = onReserveCallback,
        onCancelClick = onCancelCallback,
        onSwipeGesture = { swipeDirection ->
            if (!currentIsLoading && !currentIsMutating) {
                viewModel.navigateWeek(swipeDirection)
            }
        },
        hasMenuForSwipe = { currentHasMenu },
        swipeKey = viewModel,
        modifier = modifier
    )

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
        CancelMealConfirmationDialog(
            meal = meal,
            dayName = dayName,
            onDismiss = { mealToCancel = null },
            onConfirm = {
                val dayIdx = meal.dayIndex
                val mealType = meal.mealType
                mealToCancel = null
                viewModel.cancelMeal(mealType, dayIdx)
            }
        )
    }

    // Student Profile Dialog
    if (showProfileDialog) {
        StudentProfileDialog(
            studentName = weeklyMenu?.studentName?.ifEmpty { userSession.studentName } ?: userSession.studentName,
            studentNumber = weeklyMenu?.studentNumber?.ifEmpty { userSession.studentNumber } ?: userSession.studentNumber,
            creditFormatted = weeklyMenu?.creditText?.ifEmpty { userSession.creditFormatted } ?: userSession.creditFormatted,
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
internal fun MenuScreenContent(
    userSession: UserSession,
    weeklyMenu: WeeklyMenu?,
    flattenedMenu: FlattenedMenuList,
    selectedDayIndex: Int,
    selectedMealFilter: MealType?,
    isLoading: Boolean,
    isMutating: Boolean,
    processingDayIndex: Int?,
    weekOffset: Int,
    isLoggingOut: Boolean,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateWeek: (WeekNavigationDirection) -> Unit,
    onDaySelected: (DayPillModel) -> Unit,
    onMealFilterSelected: (MealType?) -> Unit,
    onReserveClick: (MealItem) -> Unit,
    onCancelClick: (MealItem) -> Unit,
    onSwipeGesture: (WeekNavigationDirection) -> Unit,
    hasMenuForSwipe: () -> Boolean,
    swipeKey: Any,
    modifier: Modifier = Modifier
) {
    PersianRtlLayout {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                MenuTopBar(
                    onOpenProfile = onOpenProfile,
                    onLogout = onLogout,
                    isLoggingOut = isLoggingOut
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(swipeKey) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!hasMenuForSwipe()) return@awaitEachGesture
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
                                val swipeDirection = resolveWeekSwipeDirection(totalDx, totalDy)
                                if (swipeDirection != null) {
                                    onSwipeGesture(swipeDirection)
                                    break
                                }
                            }
                        }
                    }
            ) {
                if (isLoading && weeklyMenu == null) {
                    MenuLoadingState()
                } else {
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
                                onRefresh = onRefresh
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 2. Week Navigation Header Card
                        item(key = "week_navigation", contentType = "week_navigation") {
                            val onPrevWeek = remember(onNavigateWeek) { { onNavigateWeek(WeekNavigationDirection.PREVIOUS) } }
                            val onCurrentWeek = remember(onNavigateWeek) { { onNavigateWeek(WeekNavigationDirection.CURRENT) } }
                            val onNextWeek = remember(onNavigateWeek) { { onNavigateWeek(WeekNavigationDirection.NEXT) } }
                            WeekNavigationCard(
                                weekDateRange = weeklyMenu?.weekDateRange ?: "در حال بارگذاری...",
                                weekOffset = weekOffset,
                                isLoading = isLoading,
                                enabled = weekButtonsEnabled,
                                onPrevWeek = onPrevWeek,
                                onCurrentWeek = onCurrentWeek,
                                onNextWeek = onNextWeek
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 3. Quick-jump Day Bar & Meal Type Filter
                        item(key = "quick_jump_filters", contentType = "quick_jump_filters") {
                            MenuQuickJumpAndFilterBar(
                                dayPills = flattenedMenu.dayPills,
                                selectedDayIndex = selectedDayIndex,
                                selectedMealFilter = selectedMealFilter,
                                onDayClick = onDaySelected,
                                onMealFilterClick = onMealFilterSelected
                            )
                        }

                        // 4. Continuous Flattened All-Week Days and Meals
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
                                        onReserveClick = onReserveClick,
                                        onCancelClick = onCancelClick,
                                        onChangeFoodClick = onReserveClick
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuScreenPreviewHost(darkTheme: Boolean) {
    val sampleMenu = WeeklyMenu(
        weekDateRange = "۱۴۰۵/۰۸/۱۶ الی ۱۴۰۵/۰۸/۲۲",
        studentName = "کاربر نمونه",
        studentNumber = "۴۰۳۱۰۰۱۲۳۴",
        creditText = "۵۹۰,۰۰۰ ریال",
        creditValueRials = 590000L,
        days = listOf(
            DayMenu(
                dayIndex = 0,
                dayName = "شنبه",
                dateJalali = "۱۴۰۵/۰۸/۱۶",
                isToday = true,
                meals = mapOf(
                    MealType.LUNCH to listOf(
                        MealItem(
                            id = "sat_lunch",
                            mealType = MealType.LUNCH,
                            dayIndex = 0,
                            foodName = "چلو جوجه کباب",
                            diningHall = "سلف پردیس",
                            priceRials = 180000L,
                            priceFormatted = "۱۸۰,۰۰۰ ریال",
                            calories = 650,
                            status = ReservationStatus.RESERVED,
                            count = 1,
                            isEditable = true,
                            availableFoodOptions = listOf(FoodOption("89247", "چلو جوجه کباب", 180000L))
                        )
                    )
                )
            )
        )
    )
    val flattened = remember(sampleMenu) { buildFlattenedMenuList(sampleMenu, null, 3) }
    CullinanTheme(darkTheme = darkTheme) {
        MenuScreenContent(
            userSession = UserSession(
                isLoggedIn = true,
                username = "4031001234",
                studentName = "کاربر نمونه",
                studentNumber = "۴۰۳۱۰۰۱۲۳۴",
                creditFormatted = "۵۹۰,۰۰۰ ریال"
            ),
            weeklyMenu = sampleMenu,
            flattenedMenu = flattened,
            selectedDayIndex = 0,
            selectedMealFilter = null,
            isLoading = false,
            isMutating = false,
            processingDayIndex = null,
            weekOffset = 0,
            isLoggingOut = false,
            snackbarHostState = remember { SnackbarHostState() },
            listState = rememberLazyListState(),
            onOpenProfile = {},
            onLogout = {},
            onRefresh = {},
            onNavigateWeek = {},
            onDaySelected = {},
            onMealFilterSelected = {},
            onReserveClick = {},
            onCancelClick = {},
            onSwipeGesture = {},
            hasMenuForSwipe = { true },
            swipeKey = Unit
        )
    }
}

@Preview(showBackground = true, name = "MenuScreen - Light RTL")
@Composable
private fun MenuScreenLightPreview() {
    MenuScreenPreviewHost(darkTheme = false)
}

@Preview(showBackground = true, name = "MenuScreen - Dark RTL")
@Composable
private fun MenuScreenDarkPreview() {
    MenuScreenPreviewHost(darkTheme = true)
}
