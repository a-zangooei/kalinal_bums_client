package com.example.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProfileUiState
import com.example.data.model.UserProfile
import com.example.ui.components.PersianRtlLayout
import com.example.ui.screens.menu.rememberDecodedAvatar
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.StatusAvailableBlue
import com.example.ui.theme.StatusAvailableBlueBg
import com.example.ui.theme.StatusReservedGreen
import com.example.ui.theme.StatusReservedGreenBg
import com.example.ui.theme.TealPrimary

private val SectionCardShape = RoundedCornerShape(18.dp)
private val StatusPillShape = RoundedCornerShape(12.dp)

internal enum class SensitiveFieldKind {
    NATIONAL_CODE,
    MOBILE,
    EMAIL,
    ID_OR_CARD
}

internal object ProfilePrivacyFormatter {
    fun mask(value: String?, kind: SensitiveFieldKind): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return "ثبت‌نشده"

        return when (kind) {
            SensitiveFieldKind.NATIONAL_CODE -> {
                if (raw.length <= 5) "•••••"
                else "${raw.take(3)}${"•".repeat(raw.length - 6).ifEmpty { "••••" }}${raw.takeLast(3)}"
            }
            SensitiveFieldKind.MOBILE -> {
                if (raw.length <= 6) "••••••"
                else "${raw.take(4)}${"•".repeat(raw.length - 7).ifEmpty { "••••" }}${raw.takeLast(3)}"
            }
            SensitiveFieldKind.EMAIL -> {
                val atIndex = raw.indexOf('@')
                if (atIndex <= 0) {
                    "••••••"
                } else {
                    val local = raw.substring(0, atIndex)
                    val domain = raw.substring(atIndex)
                    "${local.take(1)}•••$domain"
                }
            }
            SensitiveFieldKind.ID_OR_CARD -> {
                if (raw.length >= 10) {
                    "${raw.take(4)}${"•".repeat((raw.length - 8).coerceAtMost(8))}${raw.takeLast(4)}"
                } else if (raw.length >= 5) {
                    "${raw.take(2)}${"•".repeat(raw.length - 4)}${raw.takeLast(2)}"
                } else {
                    "••••"
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileModalSheet(
    profileState: ProfileUiState,
    avatarBytes: ByteArray?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRelogin: () -> Unit,
    onOpenUnreadMessages: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        PersianRtlLayout {
            ProfileSheetContent(
                profileState = profileState,
                avatarBytes = avatarBytes,
                onClose = onDismiss,
                onRefresh = onRefresh,
                onRelogin = onRelogin,
                onOpenUnreadMessages = onOpenUnreadMessages,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
            )
        }
    }
}

@Composable
internal fun ProfileSheetContent(
    profileState: ProfileUiState,
    avatarBytes: ByteArray?,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRelogin: () -> Unit,
    onOpenUnreadMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeProfile: UserProfile? = when (profileState) {
        is ProfileUiState.Loaded -> profileState.profile
        is ProfileUiState.Loading -> profileState.fallbackProfile
        is ProfileUiState.Error -> profileState.fallbackProfile
        ProfileUiState.Idle,
        is ProfileUiState.SessionExpired,
        is ProfileUiState.UnreadMessagesRequired -> null
    }
    val isLoading = profileState is ProfileUiState.Loading ||
        (profileState is ProfileUiState.Loaded && profileState.isRefreshing)

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("profile_sheet_content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Sheet Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("profile_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "بستن پروفایل",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "پروفایل کاربری",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "محافظت‌شده در برابر اسکرین‌شات",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("profile_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "بروزرسانی اطلاعات پروفایل",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                AnimatedVisibility(visible = isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .testTag("profile_loading_indicator"),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("profile_lazy_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Identity Card
            item(key = "hero_identity") {
                ProfileIdentityHeroCard(
                    profile = activeProfile,
                    avatarBytes = avatarBytes,
                    isLoading = isLoading
                )
            }

            // 2. State Banners (Expired Session / Unread Messages / Fetch Error / Server Alert)
            if (profileState is ProfileUiState.SessionExpired) {
                item(key = "session_expired_banner") {
                    ProfileActionWarningCard(
                        title = "انقضای نشست کاربری",
                        message = profileState.message,
                        actionLabel = "ورود مجدد به سامانه",
                        onAction = onRelogin,
                        testTagName = "profile_relogin_button",
                        isError = true
                    )
                }
            } else if (profileState is ProfileUiState.UnreadMessagesRequired) {
                item(key = "unread_messages_banner") {
                    ProfileActionWarningCard(
                        title = "پیام‌های خوانده‌نشده فعال است",
                        message = profileState.message,
                        actionLabel = "مشاهده پیام‌های سامانه",
                        onAction = onOpenUnreadMessages,
                        testTagName = "profile_unread_messages_button",
                        isError = false
                    )
                }
            } else if (profileState is ProfileUiState.Error) {
                item(key = "error_banner") {
                    ProfileActionWarningCard(
                        title = "خطا در دریافت اطلاعات کامل پروفایل",
                        message = profileState.message,
                        actionLabel = "تلاش مجدد",
                        onAction = onRefresh,
                        testTagName = "profile_retry_button",
                        isError = true
                    )
                }
            } else if (profileState is ProfileUiState.Loaded && !profileState.refreshError.isNullOrBlank()) {
                item(key = "refresh_error_banner") {
                    ProfileActionWarningCard(
                        title = "بروزرسانی ناموفق بود",
                        message = profileState.refreshError,
                        actionLabel = "تلاش مجدد",
                        onAction = onRefresh,
                        testTagName = "profile_retry_button",
                        isError = true
                    )
                }
            }

            activeProfile?.alertMessage?.let { serverAlert ->
                item(key = "server_alert") {
                    Card(
                        shape = SectionCardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = serverAlert,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 3. Credits Card (Master Sidebar Live Balance)
            if (activeProfile != null &&
                (activeProfile.selfCreditText != null ||
                    activeProfile.cafeteriaCreditText != null ||
                    activeProfile.todayDate != null)
            ) {
                item(key = "credits_section") {
                    ProfileCreditsCard(profile = activeProfile)
                }
            }

            // 4. Personal Details Card (مشخصات فردی)
            if (activeProfile != null) {
                item(key = "personal_details_section") {
                    ProfilePersonalDetailsCard(profile = activeProfile)
                }

                // 5. Card & Validity Card (مشخصات کارت و اعتبار)
                if (activeProfile.isFullProfileLoaded ||
                    activeProfile.cardNumber != null ||
                    activeProfile.barcodeNumber != null
                ) {
                    item(key = "card_details_section") {
                        ProfileCardDetailsCard(profile = activeProfile)
                    }
                }

                // 6. Privacy & Portal Version Footer
                item(key = "privacy_footer") {
                    ProfilePrivacyFooter(
                        portalVersion = activeProfile.portalVersion,
                        portalLastUpdate = activeProfile.portalLastUpdate
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileIdentityHeroCard(
    profile: UserProfile?,
    avatarBytes: ByteArray?,
    isLoading: Boolean
) {
    val avatarBitmap = rememberDecodedAvatar(avatarBytes)
    val heroBrush = Brush.linearGradient(
        colors = listOf(
            TealPrimary,
            MaterialTheme.colorScheme.primaryContainer
        )
    )

    Card(
        shape = SectionCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush.let {
                    Brush.verticalGradient(
                        listOf(
                            TealPrimary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                })
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.5.dp, AccentGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = "تصویر پروفایل کاربر",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("profile_hero_avatar")
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "آواتار پیش‌فرض کاربر",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.fullName ?: if (isLoading) "در حال دریافت مشخصات..." else "کاربر سامانه تغذیه",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("profile_full_name")
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        profile?.userType?.let { userType ->
                            Surface(
                                shape = StatusPillShape,
                                color = StatusAvailableBlueBg
                            ) {
                                Text(
                                    text = userType,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StatusAvailableBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        profile?.cardStatus?.let { cardStatus ->
                            val isActive = cardStatus.contains("فعال") && !cardStatus.contains("غیر")
                            Surface(
                                shape = StatusPillShape,
                                color = if (isActive) StatusReservedGreenBg else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = cardStatus,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isActive) StatusReservedGreen else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (profile != null && !profile.isFullProfileLoaded && isLoading) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "در حال دریافت اطلاعات کامل از سامانه...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCreditsCard(profile: UserProfile) {
    Card(
        shape = SectionCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(
                icon = Icons.Default.AccountBalanceWallet,
                title = "وضعیت اعتبار و تاریخ سامانه"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            profile.selfCreditText?.let { credit ->
                val isNegative = (profile.selfCreditRials ?: 0L) < 0L
                ProfileInfoRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = "اعتبار سلف",
                    value = credit,
                    valueColor = if (isNegative) MaterialTheme.colorScheme.error else StatusReservedGreen,
                    forceLtr = false
                )
            }

            profile.cafeteriaCreditText?.let { cafeteriaCredit ->
                val isNegative = (profile.cafeteriaCreditRials ?: 0L) < 0L
                ProfileInfoRow(
                    icon = Icons.Default.Restaurant,
                    label = "اعتبار رستوران",
                    value = cafeteriaCredit,
                    valueColor = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    forceLtr = false
                )
            }

            profile.todayDate?.let { today ->
                ProfileInfoRow(
                    icon = Icons.Default.CalendarToday,
                    label = "تاریخ امروز سامانه",
                    value = today
                )
            }
        }
    }
}

@Composable
private fun ProfilePersonalDetailsCard(profile: UserProfile) {
    Card(
        shape = SectionCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionHeader(
                icon = Icons.Default.Badge,
                title = "مشخصات فردی و تحصیلی"
            )
            Text(
                text = "برای مشاهده مقادیر محرمانه روی علامت چشم بزنید",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            SensitiveProfileInfoRow(
                icon = Icons.Default.Badge,
                label = "شناسه کاربری (شماره شناسایی)",
                rawValue = profile.idNumber,
                kind = SensitiveFieldKind.ID_OR_CARD,
                resetKey = profile,
                testTagName = "profile_row_id_number"
            )

            if (profile.isFullProfileLoaded) {
                SensitiveProfileInfoRow(
                    icon = Icons.Default.Lock,
                    label = "کد ملی",
                    rawValue = profile.nationalCode,
                    kind = SensitiveFieldKind.NATIONAL_CODE,
                    resetKey = profile,
                    testTagName = "profile_row_national_code"
                )
                SensitiveProfileInfoRow(
                    icon = Icons.Default.Phone,
                    label = "تلفن همراه",
                    rawValue = profile.mobile,
                    kind = SensitiveFieldKind.MOBILE,
                    resetKey = profile,
                    testTagName = "profile_row_mobile"
                )
                SensitiveProfileInfoRow(
                    icon = Icons.Default.Email,
                    label = "ایمیل",
                    rawValue = profile.email,
                    kind = SensitiveFieldKind.EMAIL,
                    resetKey = profile,
                    testTagName = "profile_row_email"
                )
                SensitiveProfileInfoRow(
                    icon = Icons.Default.AccountBalance,
                    label = "کارت یا حساب بانکی",
                    rawValue = profile.bankAccount,
                    kind = SensitiveFieldKind.ID_OR_CARD,
                    resetKey = profile,
                    testTagName = "profile_row_bank_account"
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                ProfileInfoRow(icon = Icons.Default.School, label = "دانشکده", value = profile.college)
                ProfileInfoRow(icon = Icons.Default.School, label = "رشته", value = profile.field)
                ProfileInfoRow(icon = Icons.Default.School, label = "مقطع تحصیلی", value = profile.grade)
                ProfileInfoRow(icon = Icons.Default.Person, label = "گروه", value = profile.group)
                ProfileInfoRow(icon = Icons.Default.Person, label = "زیرگروه", value = profile.subGroup)
                ProfileInfoRow(icon = Icons.Default.Person, label = "جنسیت", value = profile.gender)
                ProfileInfoRow(icon = Icons.Default.Home, label = "خوابگاه", value = profile.dorm)
                ProfileInfoRow(icon = Icons.Default.Home, label = "بلوک", value = profile.blockNumber)
                ProfileInfoRow(icon = Icons.Default.Home, label = "اتاق", value = profile.roomNumber)
                ProfileInfoRow(icon = Icons.Default.Info, label = "توضیحات", value = profile.description)
                ProfileInfoRow(
                    icon = Icons.Default.Schedule,
                    label = "آخرین ورود ثبت‌شده",
                    value = profile.lastLogin,
                    forceLtr = true
                )
            }
        }
    }
}

@Composable
private fun ProfileCardDetailsCard(profile: UserProfile) {
    Card(
        shape = SectionCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionHeader(
                icon = Icons.Default.CreditCard,
                title = "مشخصات کارت و اعتبار"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            SensitiveProfileInfoRow(
                icon = Icons.Default.CreditCard,
                label = "شماره کارت",
                rawValue = profile.cardNumber,
                kind = SensitiveFieldKind.ID_OR_CARD,
                resetKey = profile,
                testTagName = "profile_row_card_number"
            )
            ProfileInfoRow(
                icon = Icons.Default.QrCode2,
                label = "شماره بارکد",
                value = profile.barcodeNumber,
                forceLtr = true
            )
            ProfileInfoRow(
                icon = Icons.Default.CalendarToday,
                label = "تاریخ تحویل کارت",
                value = profile.cardDeliveryDate,
                forceLtr = true
            )
            ProfileInfoRow(
                icon = Icons.Default.CalendarToday,
                label = "تاریخ اعتبار کارت",
                value = profile.cardExpirationDate,
                forceLtr = true
            )
            ProfileInfoRow(
                icon = Icons.Default.Info,
                label = "وضعیت کارت",
                value = profile.cardStatus
            )
        }
    }
}

@Composable
private fun ProfileActionWarningCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTagName: String,
    isError: Boolean
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.40f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    }
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

    Card(
        shape = SectionCardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = tint)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = tint),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag(testTagName)
            ) {
                Text(actionLabel, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ProfilePrivacyFooter(
    portalVersion: String?,
    portalLastUpdate: String?
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "داده‌های پروفایل و تصویر کاربر صرفاً در حافظه موقت نشست نگهداری می‌شوند و پس از خروج پاک خواهند شد.",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (portalVersion != null || portalLastUpdate != null) {
                val meta = listOfNotNull(portalVersion, portalLastUpdate).joinToString("  •  ")
                Text(
                    text = meta,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String?,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    forceLtr: Boolean = false
) {
    val displayValue = value?.takeIf { it.isNotBlank() } ?: "ثبت‌نشده"
    val effectiveColor = if (value.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else valueColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(0.46f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (forceLtr && !value.isNullOrBlank()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = displayValue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = effectiveColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.54f)
                )
            }
        } else {
            Text(
                text = displayValue,
                fontSize = 13.sp,
                fontWeight = if (value.isNullOrBlank()) FontWeight.Normal else FontWeight.SemiBold,
                color = effectiveColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.54f)
            )
        }
    }
}

@Composable
private fun SensitiveProfileInfoRow(
    icon: ImageVector,
    label: String,
    rawValue: String?,
    kind: SensitiveFieldKind,
    resetKey: Any?,
    testTagName: String
) {
    var isRevealed by remember(resetKey, rawValue) { mutableStateOf(false) }
    val hasValue = !rawValue.isNullOrBlank()
    val displayed = when {
        !hasValue -> "ثبت‌نشده"
        isRevealed -> rawValue!!
        else -> ProfilePrivacyFormatter.mask(rawValue, kind)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (hasValue) {
                    Modifier.clickable { isRevealed = !isRevealed }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp)
            .testTag(testTagName),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(0.44f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(0.56f)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = displayed,
                    fontSize = 13.sp,
                    fontWeight = if (hasValue) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (hasValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            if (hasValue) {
                IconButton(
                    onClick = { isRevealed = !isRevealed },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagName}_toggle")
                ) {
                    Icon(
                        imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isRevealed) "مخفی کردن $label" else "نمایش $label",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "ProfileSheetContent - Light RTL")
@Composable
private fun ProfileSheetLightPreview() {
    val sample = UserProfile(
        fullName = "نام و نام خانوادگی نمونه",
        userType = "دانشجو",
        idNumber = "4000000000000000",
        nationalCode = "0000000000",
        group = "بومی",
        subGroup = "بومی",
        gender = "مرد",
        college = "دانشکده نمونه",
        field = "رشته نمونه",
        dorm = "مجوز شام دارد",
        mobile = "09000000000",
        grade = "دکتری عمومی",
        email = "user@example.com",
        lastLogin = "12:00:00 - 1405/01/01",
        cardNumber = "4000000000000000",
        barcodeNumber = "00000",
        cardDeliveryDate = "1400/01/01",
        cardExpirationDate = "1450/12/29",
        cardStatus = "فعال",
        selfCreditRials = -123000L,
        selfCreditText = "123,000- ریال (بدهی)",
        cafeteriaCreditRials = 0L,
        cafeteriaCreditText = "0 ریال",
        todayDate = "شنبه 01 فروردین 1405",
        portalVersion = "Version : 8.9.78.1",
        portalLastUpdate = "Last Update: 25/11/1402",
        isFullProfileLoaded = true
    )
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            ProfileSheetContent(
                profileState = ProfileUiState.Loaded(sample),
                avatarBytes = null,
                onClose = {},
                onRefresh = {},
                onRelogin = {},
                onOpenUnreadMessages = {}
            )
        }
    }
}
