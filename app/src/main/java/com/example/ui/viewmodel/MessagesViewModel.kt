package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.logger.AppLogger
import com.example.data.model.MessageItem
import com.example.data.model.MessagesPageState
import com.example.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MessagesViewModel(
    application: Application,
    private val messageRepository: MessageRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, MessageRepository())

    private val _pageState = MutableStateFlow(MessagesPageState(isLoading = true))
    val pageState: StateFlow<MessagesPageState> = _pageState.asStateFlow()

    private val _isReadingSerialized = MutableStateFlow(false)
    val isReadingSerialized: StateFlow<Boolean> = _isReadingSerialized.asStateFlow()

    private val _readingProgress = MutableStateFlow<String?>(null)
    val readingProgress: StateFlow<String?> = _readingProgress.asStateFlow()

    private val _selectedMessageDetail = MutableStateFlow<MessageItem?>(null)
    val selectedMessageDetail: StateFlow<MessageItem?> = _selectedMessageDetail.asStateFlow()

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        loadMessages()
    }

    fun loadMessages() {
        viewModelScope.launch {
            _pageState.value = _pageState.value.copy(isLoading = true, errorMessage = null)
            val result = messageRepository.fetchMessagesPage()
            result.onSuccess { state ->
                _pageState.value = state
            }.onFailure { error ->
                _pageState.value = _pageState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "خطا در دریافت پیام‌ها از سرور"
                )
            }
        }
    }

    fun selectMessageForDetails(message: MessageItem) {
        if (!message.messageText.isNullOrBlank()) {
            _selectedMessageDetail.value = message
            return
        }

        // Fetch message text from server via Delta POST
        viewModelScope.launch {
            _selectedMessageDetail.value = message
            _isDetailLoading.value = true

            val currentVs = _pageState.value.viewState
            val vsGen = _pageState.value.viewStateGenerator

            val result = messageRepository.openMessage(
                buttonInputName = message.buttonInputName,
                currentViewState = currentVs,
                currentViewStateGenerator = vsGen
            )

            result.onSuccess { (newVs, text) ->
                val updatedItem = message.copy(
                    isRead = true,
                    messageText = text.ifBlank { "متن پیام خالی است یا بارگذاری نشد." }
                )
                _selectedMessageDetail.value = updatedItem

                // Update in state list
                val updatedList = _pageState.value.messages.map {
                    if (it.rowIndex == message.rowIndex) updatedItem else it
                }
                val newUnreadCount = updatedList.count { !it.isRead }
                _pageState.value = _pageState.value.copy(
                    viewState = newVs,
                    messages = updatedList,
                    hasUnreadMessages = newUnreadCount > 0,
                    unreadCount = newUnreadCount
                )
            }.onFailure { err ->
                AppLogger.logError("MessagesViewModel", "Failed to load message text: ${err.message}", err)
                _selectedMessageDetail.value = message.copy(
                    messageText = "خطا در دریافت متن پیام از سرور: ${err.message}"
                )
            }

            _isDetailLoading.value = false
        }
    }

    fun dismissMessageDetails() {
        _selectedMessageDetail.value = null
        _isDetailLoading.value = false
    }

    fun markAllUnreadAsRead(onUnlocked: () -> Unit) {
        val currentState = _pageState.value
        val unreadCount = currentState.messages.count { !it.isRead }
        if (unreadCount == 0) {
            onUnlocked()
            return
        }

        viewModelScope.launch {
            _isReadingSerialized.value = true
            _readingProgress.value = "آماده‌سازی برای ثبت خوانده‌شدن پیام‌ها..."
            _pageState.value = _pageState.value.copy(errorMessage = null)

            val result = messageRepository.markAllUnreadAsReadSerialized(currentState) { currentIdx, total, title, _ ->
                _readingProgress.value = "در حال ثبت پیام $currentIdx از $total:\n$title"
            }

            result.onSuccess { finalState ->
                _pageState.value = finalState
                _isReadingSerialized.value = false
                _readingProgress.value = null

                if (finalState.isReservationUnlocked || !finalState.hasUnreadMessages) {
                    _userMessage.value = "تمامی پیام‌ها با موفقیت خوانده شدند و دسترسی به منوی رزرو غذا فعال گردید."
                    onUnlocked()
                } else {
                    _pageState.value = finalState.copy(
                        errorMessage = "تعدادی از پیام‌ها خوانده شدند اما منوی رزرو هنوز قفل است. لطفاً صفحه را تازه‌سازی کنید."
                    )
                }
            }.onFailure { err ->
                _isReadingSerialized.value = false
                _readingProgress.value = null
                _pageState.value = _pageState.value.copy(
                    errorMessage = "خطا در فرآیند ثبت خوانده‌شدن پیام‌ها: ${err.message}"
                )
            }
        }
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }
}
