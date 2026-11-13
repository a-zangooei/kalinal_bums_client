package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    onToggleBiometric: (Boolean) -> Unit
) {
    SettingsSectionTitle(
        title = "امنیت و قفل اعتبارنامه",
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "قفل بیومتریک / پین دستگاه برای رمز ذخیره‌شده",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            !isEncryptionAvailable -> "رمزنگاری سخت‌افزاری دستگاه در دسترس نیست"
                            !canUseBiometric -> "در تنظیمات گوشی اثر انگشت یا قفل صفحه فعال نشده است"
                            else -> "پیش از پر شدن خودکار رمز ذخیره‌شده، اثر انگشت یا قفل دستگاه را مطالبه می‌کند"
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isBiometricEnabled && isEncryptionAvailable && canUseBiometric,
                    enabled = isEncryptionAvailable && canUseBiometric,
                    onCheckedChange = onToggleBiometric,
                    colors = SwitchDefaults.colors(checkedThumbColor = TealPrimary),
                    modifier = Modifier.testTag("biometric_lock_switch")
                )
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
                    isBiometricEnabled = false,
                    onToggleBiometric = {}
                )
            }
        }
    }
}
