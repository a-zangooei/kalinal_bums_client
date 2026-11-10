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
 * Represents ASP.NET WebForms atomic state fields required for chaining postbacks.
 */
data class WebFormsState(
    val viewState: String,
    val viewStateGenerator: String = "87AF9C2D",
    val eventValidation: String? = null
)

/**
 * Result model for Delta-based message retrieval.
 */
sealed class MessageOpenResult {
    data class Success(
        val updatedState: WebFormsState,
        val messageText: String
    ) : MessageOpenResult()

    data class Failure(
        val error: MessageError
    ) : MessageOpenResult()
}

/**
 * Explicit error types for message retrieval failure modes.
 */
sealed class MessageError(val userMessage: String) : Exception(userMessage) {
    object SessionExpired : MessageError("نشست کاربری منقضی شده است. لطفاً مجدداً وارد سامانه شوید.")
    object MissingUpdatePanel : MessageError("پاسخ سرور فاقد پنل محتوای پیام (UpdatePanel) است.")
    object EmptyMessageContent : MessageError("محتوای متنی معتبری برای این پیام در سرور یافت نشد.")
    object InvalidDeltaResponse : MessageError("فرمت پاسخ دلتای سرور نامعتبر است یا توکنی دریافت نشد.")
    data class ServerError(val code: Int, val detail: String) : MessageError("خطای سرور ($code): $detail")
    data class NetworkError(val originalException: Throwable) : MessageError("خطای ارتباط با سرور: ${originalException.message ?: "نامشخص"}")
}

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
) {
    fun toWebFormsState(): WebFormsState = WebFormsState(
        viewState = viewState,
        viewStateGenerator = viewStateGenerator.ifBlank { "87AF9C2D" },
        eventValidation = eventValidation.ifBlank { null }
    )

    fun withUpdatedWebFormsState(state: WebFormsState): MessagesPageState = copy(
        viewState = state.viewState,
        viewStateGenerator = state.viewStateGenerator,
        eventValidation = state.eventValidation ?: eventValidation
    )
}

class UnreadMessagesException(
    val unreadCount: Int = 1
) : Exception("پیام خوانده‌نشده در سامانه وجود دارد. منوی رزرو غذا تا زمان خواندن پیام‌ها قفل می‌باشد")
