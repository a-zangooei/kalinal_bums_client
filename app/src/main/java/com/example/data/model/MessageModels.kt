package com.example.data.model

/**
 * Represents a single message / announcement row in Cullinan's MyMessage.aspx table.
 */
data class MessageItem(
    val rowIndex: Int,
    val title: String,
    val buttonInputName: String,
    val sendDate: String,
    val sendTime: String,
    val isRead: Boolean,
    val readDate: String,
    val readTime: String,
    val messageText: String? = null
)

/**
 * State representing the loaded messages page, including ASP.NET ViewState and unlock status.
 */
data class MessagesPageState(
    val viewState: String = "",
    val viewStateGenerator: String = "87AF9C2D",
    val eventValidation: String = "",
    val scriptManagerName: String = "ctl00\$ScriptManager",
    val updatePanelName: String = "ctl00\$UpdatePanel1",
    val messages: List<MessageItem> = emptyList(),
    val hasUnreadMessages: Boolean = false,
    val unreadCount: Int = 0,
    val hasPaginationControls: Boolean = false,
    val isReservationUnlocked: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class UnreadMessagesException(
    val unreadCount: Int = 1
) : Exception("پیام خوانده‌نشده در سامانه وجود دارد. منوی رزرو غذا تا زمان خواندن پیام‌ها قفل می‌باشد")

