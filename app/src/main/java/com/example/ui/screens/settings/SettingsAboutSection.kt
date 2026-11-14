package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

@Composable
internal fun AboutVersionSection(
    versionName: String,
    versionCode: Int,
    onVersionClick: () -> Unit,
    onCheckForUpdates: (() -> Unit)?
) {
    SettingsSectionTitle(
        title = "درباره برنامه",
        icon = Icons.Default.Info
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "کالینان",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TealPrimary
            )
            Text(
                text = "سامانه همراه مدیریت تغذیه دانشگاه علوم پزشکی بیرجند",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Version Click Target
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onVersionClick)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نسخه $versionName (شماره ساخت $versionCode)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (onCheckForUpdates != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCheckForUpdates,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TealPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "بررسی به‌روزرسانی جدید",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                }
            }

            Text(
                text = "منتشرشده تحت مجوز متن‌باز GPLv3",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "SettingsAbout - Light RTL")
@Composable
private fun SettingsAboutLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                AboutVersionSection(
                    versionName = "1.0.0",
                    versionCode = 66,
                    onVersionClick = {},
                    onCheckForUpdates = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "SettingsAbout - Dark RTL")
@Composable
private fun SettingsAboutDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                AboutVersionSection(
                    versionName = "1.0.0",
                    versionCode = 66,
                    onVersionClick = {},
                    onCheckForUpdates = {}
                )
            }
        }
    }
}
