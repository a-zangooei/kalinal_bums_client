package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

@Composable
internal fun NetworkStatusSection(
    baseUrl: String,
    isCompatibilityModeActive: Boolean,
    onToggleCompatibilityMode: (Boolean) -> Unit
) {
    SettingsSectionTitle(
        title = "وضعیت سامانه و اتصال شبکه",
        icon = Icons.Default.CloudQueue
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "سامانه تغذیه کالینان",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = baseUrl,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCompatibilityModeActive) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
                ) {
                    Text(
                        text = if (isCompatibilityModeActive) "بدون بررسی گواهی" else "اعتبارسنجی استاندارد",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompatibilityModeActive) Color(0xFFE65100) else Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "حالت عبور از خطای SSL (موقت)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "بدون اعتبارسنجی گواهی و نام میزبان؛ در شبکه‌های ناامن می‌تواند ناامن باشد",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isCompatibilityModeActive,
                    onCheckedChange = onToggleCompatibilityMode,
                    colors = SwitchDefaults.colors(checkedThumbColor = TealPrimary)
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "SettingsNetwork - Light RTL")
@Composable
private fun SettingsNetworkLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                NetworkStatusSection(
                    baseUrl = "https://jeton.bums.ac.ir",
                    isCompatibilityModeActive = false,
                    onToggleCompatibilityMode = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "SettingsNetwork - Dark RTL")
@Composable
private fun SettingsNetworkDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                NetworkStatusSection(
                    baseUrl = "https://jeton.bums.ac.ir",
                    isCompatibilityModeActive = true,
                    onToggleCompatibilityMode = {}
                )
            }
        }
    }
}
