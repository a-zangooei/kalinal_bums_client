package com.example.ui.screens.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark

@Composable
internal fun LoginTopSettingsAction(onOpenSettings: (() -> Unit)?) {
    if (onOpenSettings == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .testTag("login_settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "تنظیمات برنامه",
                tint = TealPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
internal fun LoginBrandingHeader() {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(TealPrimary, TealPrimaryDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.RestaurantMenu,
            contentDescription = "کالینان",
            tint = AccentGold,
            modifier = Modifier.size(44.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "سامانه تغذیه کالینان",
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )

    Text(
        text = "دانشگاه علوم پزشکی بیرجند",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

@Composable
internal fun ServerConnectionBadge(isCompatibilityModeActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = TealPrimary.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CloudQueue,
                contentDescription = "اتصال سرور",
                tint = TealPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "اتصال مستقیم به سامانه کالینان دانشگاه",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary
                )
                Text(
                    text = "https://jeton.bums.ac.ir",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCompatibilityModeActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF3E0)
                ) {
                    Text(
                        text = "بدون بررسی گواهی",
                        fontSize = 10.sp,
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun EncryptionUnavailableBanner(visible: Boolean) {
    AnimatedVisibility(visible = visible) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("encryption_unavailable_banner"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "هشدار رمزنگاری",
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ذخیره‌ساز امن سخت‌افزاری (KeyStore) روی این دستگاه در دسترس نیست. برای حفظ امنیت، گزینه «مرا به خاطر بسپار» غیرفعال شده است و رمز شما ذخیره نمی‌شود.",
                    color = Color(0xFFE65100),
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun BiometricUnavailableLockedBanner(
    visible: Boolean,
    onOpenSecuritySettings: () -> Unit,
    onClearSavedCredentials: () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("biometric_unavailable_banner"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "هشدار قفل امنیتی",
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "قفل بیومتریک برای رمز ذخیره‌شده فعال است اما اثر انگشت یا قفل صفحه روی دستگاه در دسترس نیست. برای حفظ امنیت، بازیابی خودکار مسدود شد.",
                        color = Color(0xFFE65100),
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSecuritySettings,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("open_device_security_settings_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100))
                    ) {
                        Text(
                            text = "تنظیمات قفل گوشی",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = onClearSavedCredentials,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("wipe_locked_credentials_button")
                    ) {
                        Text(
                            text = "پاکسازی رمز ذخیره‌شده",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LoginErrorBanner(
    errorMessage: String?,
    onRetryConnection: () -> Unit
) {
    AnimatedVisibility(visible = errorMessage != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "خطا",
                        tint = Color(0xFFC62828),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRetryConnection) {
                        Text(
                            text = "تلاش مجدد اتصال",
                            fontSize = 11.sp,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "LoginHeaderBanners - Light RTL")
@Composable
private fun LoginHeaderBannersLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoginBrandingHeader()
                Spacer(modifier = Modifier.height(12.dp))
                ServerConnectionBadge(isCompatibilityModeActive = true)
                Spacer(modifier = Modifier.height(10.dp))
                BiometricUnavailableLockedBanner(
                    visible = true,
                    onOpenSecuritySettings = {},
                    onClearSavedCredentials = {}
                )
                EncryptionUnavailableBanner(visible = true)
                LoginErrorBanner(errorMessage = "کد امنیتی وارد شده اشتباه است", onRetryConnection = {})
            }
        }
    }
}

@Preview(showBackground = true, name = "LoginHeaderBanners - Dark RTL")
@Composable
private fun LoginHeaderBannersDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoginBrandingHeader()
                Spacer(modifier = Modifier.height(12.dp))
                ServerConnectionBadge(isCompatibilityModeActive = false)
                Spacer(modifier = Modifier.height(10.dp))
                BiometricUnavailableLockedBanner(
                    visible = true,
                    onOpenSecuritySettings = {},
                    onClearSavedCredentials = {}
                )
                LoginErrorBanner(errorMessage = "خطا در ارتباط با سرور", onRetryConnection = {})
            }
        }
    }
}
