package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private var currentWeekOffset = 0

    fun clearUnreadBlocked() {
        _hasUnreadMessagesBlocked.value = false
    }

    fun loadInitialMenu() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = menuRepository.fetchInitialMenu()
            result.onSuccess { menu ->
                _weeklyMenu.value = menu
                currentWeekOffset = 0
                _hasUnreadMessagesBlocked.value = false
            }.onFailure { error ->
                if (error is com.example.data.model.UnreadMessagesException) {
                    _hasUnreadMessagesBlocked.value = true
                    _errorMessage.value = error.message
                } else {
                    _errorMessage.value = error.message ?: "خطا در دریافت جدول برنامه غذایی از سرور دانشگاه"
                }
            }
            _isLoading.value = false
        }
    }

    fun navigateWeek(direction: WeekNavigationDirection) {
        val current = _weeklyMenu.value ?: return
        if (_isMutating.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = menuRepository.navigateWeek(
                direction = direction,
                currentMenu = current,
                currentOffset = currentWeekOffset
            )

            result.onSuccess { (updatedMenu, newOffset) ->
                _weeklyMenu.value = updatedMenu
                currentWeekOffset = newOffset
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "خطا در جابه‌جایی هفته"
            }

            _isLoading.value = false
        }
    }

    fun reserveMeal(mealType: MealType, dayIndex: Int, foodId: String, selfId: String) {
        val current = _weeklyMenu.value ?: return
        if (_isMutating.value) return

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
                }
            }.onFailure { error ->
                val msg = error.message ?: "خطا در ارسال درخواست رزرو ${mealType.titlePersian}"
                _errorMessage.value = msg
                _userFeedback.value = Pair(msg, false)
            }

            _processingDayIndex.value = null
            _isMutating.value = false
        }
    }

    fun cancelMeal(mealType: MealType, dayIndex: Int) {
        val current = _weeklyMenu.value ?: return
        if (_isMutating.value) return

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
                }
            }.onFailure { error ->
                val msg = error.message ?: "خطا در ارسال درخواست لغو رزرو ${mealType.titlePersian}"
                _errorMessage.value = msg
                _userFeedback.value = Pair(msg, false)
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
        _selectedDayIndex.value = index.coerceIn(0, 4)
    }

    fun selectMealFilter(filter: MealType?) {
        _selectedMealFilter.value = filter
    }

    fun refresh() {
        loadInitialMenu()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearFeedback() {
        _userFeedback.value = null
    }
}
