package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

@Composable
internal fun SecurityBiometricSection(
    isEncryptionAvailable: Boolean,
    canUseBiometric: Boolean,
    isBiometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    onOpenDeviceSecuritySettings: () -> Unit = {},
    onRecoveryWipeAndDisableLock: () -> Unit = {}
) {
    SettingsSectionTitle(
        title = "امنیت و حفاظت از اطلاعات ورود",
        icon = Icons.Default.Fingerprint
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .toggleable(
                        value = isBiometricEnabled && isEncryptionAvailable,
                        enabled = isEncryptionAvailable && canUseBiometric,
                        role = Role.Switch,
                        onValueChange = onToggleBiometric
                    )
                    .padding(vertical = 4.dp)
                    .testTag("biometric_lock_switch"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "قفل بیومتریک یا رمز دستگاه برای رمز عبور ذخیره‌شده",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            !isEncryptionAvailable -> "رمزنگاری سخت‌افزاری دستگاه در دسترس نیست."
                            isBiometricEnabled && !canUseBiometric ->
                                "قفل امنیتی فعال است اما اثر انگشت یا قفل صفحه روی دستگاه تنظیم نشده؛ برای غیرفعال‌سازی، احراز هویت دستگاه را فعال یا رمز عبور ذخیره‌شده را پاک‌سازی کنید."
                            !canUseBiometric -> "در تنظیمات دستگاه، قفل صفحه یا اثر انگشت فعال نشده است."
                            else -> "پیش از پر شدن خودکار رمز عبور ذخیره‌شده و هنگام غیرفعال‌کردن این گزینه، احراز هویت دستگاه درخواست می‌شود."
                        },
                        fontSize = 10.sp,
                        color = if (isBiometricEnabled && !canUseBiometric && isEncryptionAvailable) {
                            Color(0xFFE65100)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Switch(
                    checked = isBiometricEnabled && isEncryptionAvailable,
                    enabled = isEncryptionAvailable && canUseBiometric,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedThumbColor = TealPrimary)
                )
            }

            AnimatedVisibility(visible = isEncryptionAvailable && isBiometricEnabled && !canUseBiometric) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenDeviceSecuritySettings,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_open_security_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary)
                        ) {
                            Text(
                                text = "تنظیمات امنیتی دستگاه",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = onRecoveryWipeAndDisableLock,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_wipe_biometric_recovery_button")
                        ) {
                            Text(
                                text = "پاک‌سازی رمز عبور و غیرفعال‌سازی",
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
}

@Preview(showBackground = true, name = "SettingsSecurity - Light RTL")
@Composable
private fun SettingsSecurityLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                SecurityBiometricSection(
                    isEncryptionAvailable = true,
                    canUseBiometric = true,
                    isBiometricEnabled = true,
                    onToggleBiometric = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "SettingsSecurity - Dark RTL")
@Composable
private fun SettingsSecurityDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                SecurityBiometricSection(
                    isEncryptionAvailable = true,
                    canUseBiometric = false,
                    isBiometricEnabled = true,
                    onToggleBiometric = {}
                )
            }
        }
    }
}
