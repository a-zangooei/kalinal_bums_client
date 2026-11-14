package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.logger.AppLogger
import com.example.data.model.ProfileFetchOutcome
import com.example.data.model.ProfileUiState
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import com.example.data.model.WeeklyMenu
import com.example.data.parser.ProfileParser
import com.example.data.repository.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val _avatarBytes = MutableStateFlow<ByteArray?>(null)
    val avatarBytes: StateFlow<ByteArray?> = _avatarBytes.asStateFlow()

    private val _isAvatarLoading = MutableStateFlow(false)
    val isAvatarLoading: StateFlow<Boolean> = _isAvatarLoading.asStateFlow()

    private val _profileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _isProfileSheetOpen = MutableStateFlow(false)
    val isProfileSheetOpen: StateFlow<Boolean> = _isProfileSheetOpen.asStateFlow()

    private var hasAttemptedAvatarForSession = false
    private var avatarJob: Job? = null
    private var profileJob: Job? = null

    /**
     * Triggered once authenticated (after login / when entering menus) so the user's
     * profile photo from UserImage.ashx is always available across portal menus in memory.
     */
    fun ensureAvatarLoaded() {
        if (hasAttemptedAvatarForSession || _isAvatarLoading.value) return
        hasAttemptedAvatarForSession = true
        avatarJob?.cancel()
        avatarJob = viewModelScope.launch {
            _isAvatarLoading.value = true
            val result = profileRepository.fetchProfilePhoto()
            result.onSuccess { bytes ->
                if (bytes != null) {
                    _avatarBytes.value = bytes
                }
            }
            _isAvatarLoading.value = false
        }
    }

    /**
     * Lazy-loads full MyProfile.aspx ONLY when the user taps the profile button.
     * Shows cached sidebar data immediately while waiting for server response.
     */
    fun openProfile(userSession: UserSession?, weeklyMenu: WeeklyMenu?) {
        _isProfileSheetOpen.value = true
        ensureAvatarLoaded()

        val fallback = buildFallbackProfile(userSession, weeklyMenu)
        val currentState = _profileState.value
        if (currentState is ProfileUiState.Loading ||
            (currentState is ProfileUiState.Loaded && currentState.profile.isFullProfileLoaded)
        ) {
            return
        }

        AppLogger.logAction("Opened full profile sheet; lazy-fetching MyProfile.aspx")
        fetchFullProfile(fallback = fallback, isRefresh = false)
    }

    fun refreshProfile(userSession: UserSession?, weeklyMenu: WeeklyMenu?) {
        val fallback = buildFallbackProfile(userSession, weeklyMenu)
        val currentLoaded = (_profileState.value as? ProfileUiState.Loaded)?.profile ?: fallback
        AppLogger.logAction("Manual profile refresh requested")
        fetchFullProfile(fallback = currentLoaded, isRefresh = true)
        if (_avatarBytes.value == null) {
            hasAttemptedAvatarForSession = false
            ensureAvatarLoaded()
        }
    }

    fun closeProfile() {
        _isProfileSheetOpen.value = false
    }

    fun clearSession() {
        avatarJob?.cancel()
        profileJob?.cancel()
        hasAttemptedAvatarForSession = false
        _isAvatarLoading.value = false
        _avatarBytes.value = null
        _profileState.value = ProfileUiState.Idle
        _isProfileSheetOpen.value = false
    }

    private fun fetchFullProfile(fallback: UserProfile?, isRefresh: Boolean) {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            _profileState.value = if (isRefresh && fallback != null && fallback.isFullProfileLoaded) {
                ProfileUiState.Loaded(profile = fallback, isRefreshing = true)
            } else {
                ProfileUiState.Loading(fallbackProfile = fallback)
            }

            when (val outcome = profileRepository.fetchProfile()) {
                is ProfileFetchOutcome.Success -> {
                    _profileState.value = ProfileUiState.Loaded(profile = outcome.profile)
                }
                is ProfileFetchOutcome.SessionExpired -> {
                    _profileState.value = ProfileUiState.SessionExpired(outcome.message)
                }
                is ProfileFetchOutcome.UnreadMessagesRequired -> {
                    _profileState.value = ProfileUiState.UnreadMessagesRequired(outcome.message)
                }
                is ProfileFetchOutcome.Failure -> {
                    val current = _profileState.value
                    _profileState.value = if (current is ProfileUiState.Loaded) {
                        current.copy(isRefreshing = false, refreshError = outcome.message)
                    } else {
                        ProfileUiState.Error(message = outcome.message, fallbackProfile = fallback)
                    }
                }
            }
        }
    }

    internal fun buildFallbackProfile(userSession: UserSession?, weeklyMenu: WeeklyMenu?): UserProfile? {
        val rawName = weeklyMenu?.studentName?.ifBlank { null }
            ?: userSession?.studentName?.ifBlank { null }
        val rawId = weeklyMenu?.studentNumber?.ifBlank { null }
            ?: userSession?.studentNumber?.ifBlank { null }
            ?: userSession?.username?.ifBlank { null }
        val rawCredit = weeklyMenu?.creditText?.ifBlank { null }
            ?: userSession?.creditFormatted?.ifBlank { null }
        val creditRials = weeklyMenu?.creditValueRials ?: ProfileParser.parseCreditRialsOrNull(rawCredit)

        if (rawName == null && rawId == null && rawCredit == null) return null

        return UserProfile(
            fullName = rawName,
            idNumber = rawId,
            selfCreditRials = creditRials,
            selfCreditText = rawCredit,
            isFullProfileLoaded = false
        )
    }
}
