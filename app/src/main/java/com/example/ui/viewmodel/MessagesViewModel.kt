package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.logger.AppLogger
import com.example.data.model.MessageItem
import com.example.data.model.MessageOpenResult
import com.example.data.model.MessagesPageState
import com.example.data.repository.MessageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MessagesViewModel(
    application: Application,
    private val messageRepository: MessageRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, MessageRepository())

    private val _pageState = MutableStateFlow(MessagesPageState(isLoading = false))
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

    private val detailMutex = Mutex()
    private var detailJob: Job? = null

    fun loadMessages() {
        AppLogger.logInfo("MessagesViewModel", "LoadMessages", "Fetching MyMessage.aspx messages")
        viewModelScope.launch {
            _pageState.value = _pageState.value.copy(isLoading = true, errorMessage = null)
            val result = messageRepository.fetchMessagesPage()
            result.onSuccess { state ->
                _pageState.value = state
                AppLogger.logInfo(
                    "MessagesViewModel",
                    "LoadMessagesSuccess",
                    "Loaded ${state.messages.size} messages (unread=${state.unreadCount}, unlocked=${state.isReservationUnlocked})"
                )
            }.onFailure { error ->
                val msg = error.message ?: "خطا در دریافت پیام‌ها از سرور"
                _pageState.value = _pageState.value.copy(
                    isLoading = false,
                    errorMessage = msg
                )
                AppLogger.logError("MessagesViewModel", "LoadMessages failed: $msg", error, operation = "LoadMessagesError", errorCategory = "MESSAGES_LOAD_ERROR")
            }
        }
    }

    fun selectMessageForDetails(message: MessageItem) {
        // If message text is already cached in memory, display it immediately
        if (!message.messageText.isNullOrBlank() && !message.messageText.startsWith("خطا")) {
            AppLogger.logDebug("MessagesViewModel", "OpenCachedMessage", "Showing cached message row=${message.rowIndex}")
            _selectedMessageDetail.value = message
            return
        }

        if (message.buttonInputName.isBlank()) {
            AppLogger.logWarn("MessagesViewModel", "MessageMissingButtonId", "Message row=${message.rowIndex} has blank buttonInputName", errorCategory = "MESSAGE_NO_BUTTON")
            _selectedMessageDetail.value = message.copy(
                messageText = "شناسه دکمه پیام موجود نیست یا این پیام محتوای متنی ندارد."
            )
            return
        }

        // Cancel any pending detail fetch job to prevent race conditions
        detailJob?.cancel()
        AppLogger.logInfo("MessagesViewModel", "OpenMessageDetail", "Opening message row=${message.rowIndex} (unread=${!message.isRead})")

        // Fetch message text from server via Delta POST with mutex protection
        detailJob = viewModelScope.launch {
            _selectedMessageDetail.value = message
            _isDetailLoading.value = true

            detailMutex.withLock {
                val currentState = _pageState.value
                val webFormsState = currentState.toWebFormsState()

                val result = messageRepository.openMessage(
                    buttonInputName = message.buttonInputName,
                    state = webFormsState,
                    scriptManagerName = currentState.scriptManagerName,
                    updatePanelName = currentState.updatePanelName
                )

                when (result) {
                    is MessageOpenResult.Success -> {
                        val updatedItem = message.copy(
                            isRead = true,
                            messageText = result.messageText
                        )
                        _selectedMessageDetail.value = updatedItem

                        // Update in state list and chain updated WebFormsState
                        val updatedList = _pageState.value.messages.map {
                            if (it.rowIndex == message.rowIndex) updatedItem else it
                        }
                        val newUnreadCount = updatedList.count { !it.isRead }
                        _pageState.value = _pageState.value.withUpdatedWebFormsState(result.updatedState).copy(
                            messages = updatedList,
                            hasUnreadMessages = newUnreadCount > 0,
                            unreadCount = newUnreadCount
                        )
                        AppLogger.logInfo("MessagesViewModel", "OpenMessageDetailSuccess", "Opened row=${message.rowIndex}; remainingUnread=$newUnreadCount")
                    }
                    is MessageOpenResult.Failure -> {
                        AppLogger.logError("MessagesViewModel", "Failed to load message text: ${result.error.userMessage}", result.error, operation = "OpenMessageDetailError", errorCategory = "MESSAGE_OPEN_ERROR")
                        _selectedMessageDetail.value = message.copy(
                            messageText = "خطا در دریافت متن پیام: ${result.error.userMessage}"
                        )
                    }
                }

                _isDetailLoading.value = false
            }
        }
    }

    fun dismissMessageDetails() {
        detailJob?.cancel()
        _selectedMessageDetail.value = null
        _isDetailLoading.value = false
        AppLogger.logDebug("MessagesViewModel", "DismissMessageDetail", "Closed message detail dialog")
    }

    fun markAllUnreadAsRead(onUnlocked: () -> Unit) {
        val currentState = _pageState.value
        val unreadCount = currentState.messages.count { !it.isRead }
        if (unreadCount == 0) {
            AppLogger.logInfo("MessagesViewModel", "MarkAllUnreadSkipped", "No unread messages remaining; invoking onUnlocked()")
            onUnlocked()
            return
        }

        AppLogger.logInfo("MessagesViewModel", "MarkAllUnreadStart", "Starting serialized read for $unreadCount unread messages")
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
                    AppLogger.logInfo("MessagesViewModel", "MarkAllUnreadSuccess", "All messages marked read (unlocked=${finalState.isReservationUnlocked})")
                    onUnlocked()
                } else {
                    AppLogger.logWarn("MessagesViewModel", "MarkAllUnreadStillLocked", "Messages processed but reservation still locked (remaining=${finalState.unreadCount})", errorCategory = "MESSAGES_STILL_LOCKED")
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
                AppLogger.logError("MessagesViewModel", "MarkAllUnread failed: ${err.message}", err, operation = "MarkAllUnreadError", errorCategory = "MESSAGES_SERIAL_ERROR")
            }
        }
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }
}
