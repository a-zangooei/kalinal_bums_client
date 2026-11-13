package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.logger.AppLogger
import com.example.data.model.MealType
import com.example.data.model.WeekNavigationDirection
import com.example.data.model.WeeklyMenu
import com.example.data.repository.MenuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MenuViewModel(
    private val menuRepository: MenuRepository
) : ViewModel() {

    constructor() : this(MenuRepository())

    private val _weeklyMenu = MutableStateFlow<WeeklyMenu?>(null)
    val weeklyMenu: StateFlow<WeeklyMenu?> = _weeklyMenu.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(0)
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private val _selectedMealFilter = MutableStateFlow<MealType?>(null) // null means All meals
    val selectedMealFilter: StateFlow<MealType?> = _selectedMealFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _processingDayIndex = MutableStateFlow<Int?>(null)
    val processingDayIndex: StateFlow<Int?> = _processingDayIndex.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userFeedback = MutableStateFlow<Pair<String, Boolean>?>(null) // message to isSuccess
    val userFeedback: StateFlow<Pair<String, Boolean>?> = _userFeedback.asStateFlow()

    private val _hasUnreadMessagesBlocked = MutableStateFlow(false)
    val hasUnreadMessagesBlocked: StateFlow<Boolean> = _hasUnreadMessagesBlocked.asStateFlow()

    private val _weekOffset = MutableStateFlow(0)
    val weekOffset: StateFlow<Int> = _weekOffset.asStateFlow()

    fun clearUnreadBlocked() {
        _hasUnreadMessagesBlocked.value = false
    }

    fun loadInitialMenu() {
        AppLogger.logInfo("MenuViewModel", "LoadInitialMenu", "Loading initial weekly reservation menu")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = menuRepository.fetchInitialMenu()
            result.onSuccess { menu ->
                _weeklyMenu.value = menu
                _weekOffset.value = 0
                _hasUnreadMessagesBlocked.value = false
                AppLogger.logInfo("MenuViewModel", "LoadInitialMenuSuccess", "Loaded week '${menu.weekDateRange}' with credit=${menu.creditValueRials}")
            }.onFailure { error ->
                if (error is com.example.data.model.UnreadMessagesException) {
                    _hasUnreadMessagesBlocked.value = true
                    _errorMessage.value = error.message
                    AppLogger.logWarn("MenuViewModel", "LoadInitialMenuBlocked", "Blocked by ${error.unreadCount} unread messages", errorCategory = "UNREAD_MESSAGES_BLOCK")
                } else {
                    val msg = error.message ?: "خطا در دریافت جدول برنامه غذایی از سرور دانشگاه"
                    _errorMessage.value = msg
                    AppLogger.logError("MenuViewModel", "LoadInitialMenu failed: $msg", error, operation = "LoadInitialMenuFailure", errorCategory = "MENU_LOAD_ERROR")
                }
            }
            _isLoading.value = false
        }
    }

    fun navigateWeek(direction: WeekNavigationDirection) {
        val current = _weeklyMenu.value
        if (current == null) {
            AppLogger.logWarn("MenuViewModel", "NavigateWeekSkipped", "Cannot navigate $direction because weeklyMenu is null", errorCategory = "NULL_MENU_STATE")
            return
        }
        if (_isLoading.value || _isMutating.value) {
            AppLogger.logWarn("MenuViewModel", "NavigateWeekSkipped", "Ignored $direction while loading=${_isLoading.value} mutating=${_isMutating.value}", errorCategory = "BUSY_GUARD")
            return
        }

        val activeTabId = _selectedMealFilter.value?.tabId ?: MealType.LUNCH.tabId
        AppLogger.logInfo("MenuViewModel", "NavigateWeek", "Navigating $direction (currentOffset=${_weekOffset.value}, tabId=$activeTabId)")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = menuRepository.navigateWeek(
                direction = direction,
                currentMenu = current,
                currentOffset = _weekOffset.value,
                selectedTabId = activeTabId
            )

            result.onSuccess { (updatedMenu, newOffset) ->
                _weeklyMenu.value = updatedMenu
                _weekOffset.value = newOffset
                val alert = updatedMenu.alertMessage
                if (!alert.isNullOrBlank()) {
                    _userFeedback.value = Pair(alert, true)
                }
                AppLogger.logInfo("MenuViewModel", "NavigateWeekSuccess", "Moved to '${updatedMenu.weekDateRange}' (offset=$newOffset, alert=${alert != null})")
            }.onFailure { error ->
                if (error is com.example.data.model.UnreadMessagesException) {
                    _hasUnreadMessagesBlocked.value = true
                }
                val msg = error.message ?: "خطا در جابه‌جایی هفته"
                _errorMessage.value = msg
                AppLogger.logError("MenuViewModel", "NavigateWeek failed: $msg", error, operation = "NavigateWeekFailure", errorCategory = "WEEK_NAV_ERROR")
            }

            _isLoading.value = false
        }
    }

    fun reserveMeal(mealType: MealType, dayIndex: Int, foodId: String, selfId: String) {
        val current = _weeklyMenu.value
        if (current == null) {
            AppLogger.logWarn("MenuViewModel", "ReserveMealSkipped", "Skipped reserveMeal(${mealType.name}, day=$dayIndex) because weeklyMenu is null", errorCategory = "NULL_MENU_STATE")
            return
        }
        if (_isMutating.value) {
            AppLogger.logWarn("MenuViewModel", "ReserveMealSkipped", "Skipped reserveMeal(${mealType.name}, day=$dayIndex) because another mutation is in progress", errorCategory = "MUTATION_GUARD")
            return
        }

        AppLogger.logInfo("MenuViewModel", "ReserveMeal", "Requesting reserve ${mealType.name} day=$dayIndex foodId=$foodId selfId=$selfId")
        viewModelScope.launch {
            _isMutating.value = true
            _processingDayIndex.value = dayIndex
            _errorMessage.value = null

            val result = menuRepository.reserveMeal(
                currentMenu = current,
                mealType = mealType,
                targetDayIndex = dayIndex,
                targetFoodId = foodId,
                targetSelfId = selfId
            )

            result.onSuccess { res ->
                if (res.updatedMenu != null) {
                    _weeklyMenu.value = res.updatedMenu
                }
                _userFeedback.value = Pair(res.message, res.isSuccess)
                if (!res.isSuccess) {
                    _errorMessage.value = res.message
                    AppLogger.logWarn("MenuViewModel", "ReserveMealRejected", "Server toast (${res.toastType}): ${res.message}", errorCategory = "RESERVE_REJECTED")
                } else {
                    AppLogger.logInfo("MenuViewModel", "ReserveMealSuccess", "Reserved ${mealType.name} day=$dayIndex: ${res.message}")
                }
            }.onFailure { error ->
                val msg = error.message ?: "خطا در ارسال درخواست رزرو ${mealType.titlePersian}"
                _errorMessage.value = msg
                _userFeedback.value = Pair(msg, false)
                AppLogger.logError("MenuViewModel", "ReserveMeal exception: $msg", error, operation = "ReserveMealError", errorCategory = "RESERVE_ERROR")
            }

            _processingDayIndex.value = null
            _isMutating.value = false
        }
    }

    fun cancelMeal(mealType: MealType, dayIndex: Int) {
        val current = _weeklyMenu.value
        if (current == null) {
            AppLogger.logWarn("MenuViewModel", "CancelMealSkipped", "Skipped cancelMeal(${mealType.name}, day=$dayIndex) because weeklyMenu is null", errorCategory = "NULL_MENU_STATE")
            return
        }
        if (_isMutating.value) {
            AppLogger.logWarn("MenuViewModel", "CancelMealSkipped", "Skipped cancelMeal(${mealType.name}, day=$dayIndex) because another mutation is in progress", errorCategory = "MUTATION_GUARD")
            return
        }

        AppLogger.logInfo("MenuViewModel", "CancelMeal", "Requesting cancel ${mealType.name} day=$dayIndex")
        viewModelScope.launch {
            _isMutating.value = true
            _processingDayIndex.value = dayIndex
            _errorMessage.value = null

            val result = menuRepository.cancelMeal(
                currentMenu = current,
                mealType = mealType,
                targetDayIndex = dayIndex
            )

            result.onSuccess { res ->
                if (res.updatedMenu != null) {
                    _weeklyMenu.value = res.updatedMenu
                }
                _userFeedback.value = Pair(res.message, res.isSuccess)
                if (!res.isSuccess) {
                    _errorMessage.value = res.message
                    AppLogger.logWarn("MenuViewModel", "CancelMealRejected", "Server toast (${res.toastType}): ${res.message}", errorCategory = "CANCEL_REJECTED")
                } else {
                    AppLogger.logInfo("MenuViewModel", "CancelMealSuccess", "Cancelled ${mealType.name} day=$dayIndex: ${res.message}")
                }
            }.onFailure { error ->
                val msg = error.message ?: "خطا در ارسال درخواست لغو رزرو ${mealType.titlePersian}"
                _errorMessage.value = msg
                _userFeedback.value = Pair(msg, false)
                AppLogger.logError("MenuViewModel", "CancelMeal exception: $msg", error, operation = "CancelMealError", errorCategory = "CANCEL_ERROR")
            }

            _processingDayIndex.value = null
            _isMutating.value = false
        }
    }

    fun reserveLunch(dayIndex: Int, foodId: String, selfId: String) =
        reserveMeal(MealType.LUNCH, dayIndex, foodId, selfId)

    fun cancelLunch(dayIndex: Int) =
        cancelMeal(MealType.LUNCH, dayIndex)

    fun selectDay(index: Int) {
        val clamped = index.coerceIn(0, 4)
        _selectedDayIndex.value = clamped
        AppLogger.logDebug("MenuViewModel", "SelectDay", "Selected dayIndex=$clamped")
    }

    fun selectMealFilter(filter: MealType?) {
        _selectedMealFilter.value = filter
        AppLogger.logDebug("MenuViewModel", "SelectMealFilter", "Filter=${filter?.name ?: "ALL"}")
    }

    fun refresh() {
        AppLogger.logAction("Manual menu refresh triggered")
        loadInitialMenu()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearFeedback() {
        _userFeedback.value = null
    }
}
