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
    private val menuRepository: MenuRepository = MenuRepository()
) : ViewModel() {

    private val _weeklyMenu = MutableStateFlow<WeeklyMenu?>(null)
    val weeklyMenu: StateFlow<WeeklyMenu?> = _weeklyMenu.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(0)
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private val _selectedMealFilter = MutableStateFlow<MealType?>(null) // null means All meals
    val selectedMealFilter: StateFlow<MealType?> = _selectedMealFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentWeekOffset = 0
    private var isDemoMode = false

    fun loadInitialMenu(isDemo: Boolean) {
        this.isDemoMode = isDemo
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = menuRepository.fetchInitialMenu(isDemo)
            result.onSuccess { menu ->
                _weeklyMenu.value = menu
                currentWeekOffset = 0
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "خطا در دریافت جدول برنامه غذایی"
            }
            _isLoading.value = false
        }
    }

    fun navigateWeek(direction: WeekNavigationDirection) {
        val current = _weeklyMenu.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = menuRepository.navigateWeek(
                direction = direction,
                currentMenu = current,
                isDemo = isDemoMode,
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

    fun selectDay(index: Int) {
        _selectedDayIndex.value = index.coerceIn(0, 6)
    }

    fun selectMealFilter(filter: MealType?) {
        _selectedMealFilter.value = filter
    }

    fun refresh() {
        loadInitialMenu(isDemoMode)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
