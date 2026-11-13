package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.local.UserPreferences
import com.example.data.logger.AppLogger
import com.example.data.model.AppThemeMode
import com.example.data.network.CullinanHttpClient
import com.example.ui.components.DeveloperLogsDialog
import com.example.ui.components.PersianRtlLayout
import com.example.ui.components.shareDiagnosticZip
import com.example.ui.theme.TealPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    onThemeChanged: (AppThemeMode) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onCheckForUpdates: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferences(context) }
    var isDevUnlocked by remember { mutableStateOf(userPrefs.isDeveloperModeUnlocked) }
    var tapCounter by remember { mutableIntStateOf(0) }
    var showLogsDialog by remember { mutableStateOf(false) }

    val isCompatibilityModeActive by CullinanHttpClient.isCompatibilityModeActive.collectAsState()

    PersianRtlLayout {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "تنظیمات برنامه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "بازگشت"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            modifier = modifier
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {

                // 1. Theme Selection Section
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
                            onClick = {
                                userPrefs.themeMode = AppThemeMode.SYSTEM
                                onThemeChanged(AppThemeMode.SYSTEM)
                                AppLogger.logAction("Theme changed to SYSTEM")
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        ThemeOptionRow(
                            title = "حالت روشن (Light)",
                            subtitle = "پوسته روشن فیروزه‌ای استاندارد",
                            selected = currentThemeMode == AppThemeMode.LIGHT,
                            onClick = {
                                userPrefs.themeMode = AppThemeMode.LIGHT
                                onThemeChanged(AppThemeMode.LIGHT)
                                AppLogger.logAction("Theme changed to LIGHT")
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        ThemeOptionRow(
                            title = "حالت تاریک (Dark)",
                            subtitle = "پوسته تیره مناسب برای شب و کاهش مصرف باتری",
                            selected = currentThemeMode == AppThemeMode.DARK,
                            onClick = {
                                userPrefs.themeMode = AppThemeMode.DARK
                                onThemeChanged(AppThemeMode.DARK)
                                AppLogger.logAction("Theme changed to DARK")
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Network & Connectivity Status
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
                                    text = CullinanHttpClient.BASE_URL,
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
                                onCheckedChange = { enable ->
                                    CullinanHttpClient.setCompatibilityMode(enable)
                                    AppLogger.logAction("SSL Compatibility Mode toggled: $enable")
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = TealPrimary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Developer Mode Tools (Appears only when unlocked)
                AnimatedVisibility(visible = isDevUnlocked) {
                    Column {
                        SettingsSectionTitle(
                            title = "ابزارهای توسعه‌دهنده و لاگ‌های وب (Developer)",
                            icon = Icons.Default.BugReport
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ثبت کامل لاگ‌های توسعه + صفحات خام HTML و دلتا",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary
                                )
                                Text(
                                    text = "رویدادهای شبکه، پارسرها، ViewModel، رمزنگاری و کرش‌ها با پالایش گذرواژه ثبت شده و صفحات خام HTML/Delta مرحله‌به‌مرحله تا ۸ مگابایت ذخیره می‌شوند.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showLogsDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("open_developer_logs_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مشاهده لاگ‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            shareDiagnosticZip(
                                                context = context,
                                                sslCompatibilityActive = isCompatibilityModeActive
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("export_developer_zip_button"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("خروجی فایل ZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            AppLogger.clearLogs()
                                            Toast.makeText(context, "تاریخچه لاگ‌ها با موفقیت پاکسازی شد", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC62828))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("پاکسازی لاگ‌ها", fontSize = 11.sp, color = Color(0xFFC62828))
                                    }

                                    TextButton(
                                        onClick = {
                                            isDevUnlocked = false
                                            userPrefs.isDeveloperModeUnlocked = false
                                            tapCounter = 0
                                            Toast.makeText(context, "حالت توسعه‌دهنده غیرفعال شد", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("خروج از حالت توسعه‌دهنده", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // 4. About & Version Footer with Easter Egg
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
                            text = "کالینان (Kalinan)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                        Text(
                            text = "کلاینت غیررسمی و متن‌باز سامانه تغذیه دانشگاه علوم پزشکی بیرجند",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        // Version Click Target for Easter Egg
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (!isDevUnlocked) {
                                        tapCounter++
                                        if (tapCounter >= 7) {
                                            isDevUnlocked = true
                                            userPrefs.isDeveloperModeUnlocked = true
                                            Toast.makeText(context, "🎉 تبریک! حالت توسعه‌دهنده فعال شد.", Toast.LENGTH_LONG).show()
                                            AppLogger.logAction("Developer Mode unlocked via 7-tap Easter Egg")
                                        } else if (tapCounter in 3..6) {
                                            val remaining = 7 - tapCounter
                                            Toast.makeText(context, "تنها $remaining ضربه دیگر تا فعال‌سازی حالت توسعه‌دهنده...", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "حالت توسعه‌دهنده از قبل فعال است", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "نسخه v${BuildConfig.VERSION_NAME} (بیلد #${BuildConfig.VERSION_CODE})",
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
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = TealPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "بررسی به‌روزرسانی نسخه جدید",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary
                                )
                            }
                        }

                        Text(
                            text = "توسعه‌یافته با عشق توسط جامعه دانشجویی • مجوز GPLv3",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Logs Dialog
    if (showLogsDialog) {
        DeveloperLogsDialog(onDismiss = { showLogsDialog = false })
    }
}

@Composable
fun SettingsSectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
