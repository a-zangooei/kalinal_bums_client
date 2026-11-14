package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    // Header card
    val fullName: String? = null,
    val userType: String? = null,

    // Personal info fieldset (مشخصات فردی)
    val idNumber: String? = null,
    val nationalCode: String? = null,
    val group: String? = null,
    val subGroup: String? = null,
    val gender: String? = null,
    val description: String? = null,
    val college: String? = null,
    val field: String? = null,
    val dorm: String? = null,
    val blockNumber: String? = null,
    val roomNumber: String? = null,
    val mobile: String? = null,
    val grade: String? = null,
    val email: String? = null,
    val bankAccount: String? = null,
    val lastLogin: String? = null,

    // Card & credit fieldset (مشخصات کارت و اعتبار)
    val cardNumber: String? = null,
    val barcodeNumber: String? = null,
    val cardDeliveryDate: String? = null,
    val cardExpirationDate: String? = null,
    val cardStatus: String? = null,

    // Master sidebar & footer metadata
    val selfCreditRials: Long? = null,
    val selfCreditText: String? = null,
    val cafeteriaCreditRials: Long? = null,
    val cafeteriaCreditText: String? = null,
    val todayDate: String? = null,
    val portalVersion: String? = null,
    val portalLastUpdate: String? = null,
    val alertMessage: String? = null,
    val isFullProfileLoaded: Boolean = false
)

sealed interface ProfileFetchOutcome {
    data class Success(val profile: UserProfile) : ProfileFetchOutcome
    data class SessionExpired(val message: String) : ProfileFetchOutcome
    data class UnreadMessagesRequired(val message: String) : ProfileFetchOutcome
    data class Failure(val message: String) : ProfileFetchOutcome
}

@Immutable
sealed interface ProfileUiState {
    data object Idle : ProfileUiState
    data class Loading(val fallbackProfile: UserProfile? = null) : ProfileUiState
    data class Loaded(
        val profile: UserProfile,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null
    ) : ProfileUiState
    data class Error(
        val message: String,
        val fallbackProfile: UserProfile? = null
    ) : ProfileUiState
    data class SessionExpired(val message: String) : ProfileUiState
    data class UnreadMessagesRequired(val message: String) : ProfileUiState
}
