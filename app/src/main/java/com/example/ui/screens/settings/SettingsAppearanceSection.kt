package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeMode
import com.example.ui.components.PersianRtlLayout
import com.example.ui.theme.CullinanTheme
import com.example.ui.theme.TealPrimary

@Composable
fun SettingsSectionTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TealPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TealPrimary
        )
    }
}

@Composable
internal fun ThemeSelectionSection(
    currentThemeMode: AppThemeMode,
    onSelectTheme: (AppThemeMode) -> Unit
) {
    SettingsSectionTitle(
        title = "ظاهر و پوسته برنامه",
        icon = Icons.Default.Palette
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ThemeOptionRow(
                title = "پیش‌فرض سیستم (خودکار)",
                subtitle = "هماهنگ با حالت تیره یا روشن گوشی شما",
                selected = currentThemeMode == AppThemeMode.SYSTEM,
                onClick = { onSelectTheme(AppThemeMode.SYSTEM) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            ThemeOptionRow(
                title = "حالت روشن (Light)",
                subtitle = "پوسته روشن فیروزه‌ای استاندارد",
                selected = currentThemeMode == AppThemeMode.LIGHT,
                onClick = { onSelectTheme(AppThemeMode.LIGHT) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            ThemeOptionRow(
                title = "حالت تاریک (Dark)",
                subtitle = "پوسته تیره مناسب برای شب و کاهش مصرف باتری",
                selected = currentThemeMode == AppThemeMode.DARK,
                onClick = { onSelectTheme(AppThemeMode.DARK) }
            )
        }
    }
}

@Composable
fun ThemeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) TealPrimary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = TealPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "SettingsAppearance - Light RTL")
@Composable
private fun SettingsAppearanceLightPreview() {
    CullinanTheme(darkTheme = false) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                ThemeSelectionSection(currentThemeMode = AppThemeMode.LIGHT, onSelectTheme = {})
            }
        }
    }
}

@Preview(showBackground = true, name = "SettingsAppearance - Dark RTL")
@Composable
private fun SettingsAppearanceDarkPreview() {
    CullinanTheme(darkTheme = true) {
        PersianRtlLayout {
            Column(modifier = Modifier.padding(16.dp)) {
                ThemeSelectionSection(currentThemeMode = AppThemeMode.DARK, onSelectTheme = {})
            }
        }
    }
}
