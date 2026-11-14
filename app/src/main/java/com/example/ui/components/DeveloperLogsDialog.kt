package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.logger.AppLogger
import com.example.data.logger.LogEntry
import com.example.data.logger.LogLevel
import com.example.data.logger.PageSnapshotRecord
import com.example.data.network.CullinanHttpClient
import com.example.ui.theme.TealPrimary
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DialogSurfaceShape = RoundedCornerShape(20.dp)
private val LogCardShape = RoundedCornerShape(12.dp)
private val SmallBadgeShape = RoundedCornerShape(6.dp)
private val SnapshotBtnShape = RoundedCornerShape(8.dp)
private val LogsListPadding = PaddingValues(vertical = 4.dp)

private val FatalBg = Color(0xFFB71C1C)
private val ErrorBg = Color(0xFFFFEBEE)
private val ErrorFg = Color(0xFFC62828)
private val WarnBg = Color(0xFFFFF3E0)
private val WarnFg = Color(0xFFE65100)
private val InfoBg = Color(0xFFE0F2F1)
private val StackTraceBg = Color(0xFFFFEBEE).copy(alpha = 0.6f)
private val ErrorCardBorder = BorderStroke(1.dp, Color(0xFFC62828).copy(alpha = 0.45f))
private val TealAlpha14 = TealPrimary.copy(alpha = 0.14f)
private val TealAlpha16 = TealPrimary.copy(alpha = 0.16f)
private val SnapshotOkBg = Color(0xFFE8F5E9)
private val SnapshotOkFg = Color(0xFF2E7D32)

private enum class LogLevelFilter(val label: String) {
    ALL("همه"),
    ERRORS("خطا و کرش"),
    WARNINGS_PLUS("هشدار+"),
    INFO_PLUS("اطلاعات+"),
    DEBUG_ONLY("دیباگ")
}

private enum class LogCategoryFilter(val label: String) {
    ALL("همه بخش‌ها"),
    HTTP("شبکه و کوکی"),
    PARSER("پارسرها"),
    AUTH("ورود و رمزنگاری"),
    MENU("منو و رزرو"),
    MESSAGES("پیام‌ها"),
    SYSTEM("سیستم و کرش")
}

@Composable
fun DeveloperLogsDialog(onDismiss: () -> Unit) {
    val entries by AppLogger.recentEntries.collectAsStateWithLifecycle()
    val snapshots by AppLogger.pageSnapshots.collectAsStateWithLifecycle()
    val isCompatibilityModeActive by CullinanHttpClient.isCompatibilityModeActive.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf(LogLevelFilter.ALL) }
    var categoryFilter by remember { mutableStateOf(LogCategoryFilter.ALL) }
    var onlyWithSnapshot by remember { mutableStateOf(false) }
    var activeSnapshot by remember { mutableStateOf<PageSnapshotRecord?>(null) }
    var showSharePreviewDialog by remember { mutableStateOf(false) }

    val onOpenSnapshotCallback = remember(context) {
        { fileName: String ->
            val rec = AppLogger.getSnapshotByFileName(fileName)
            if (rec != null) {
                activeSnapshot = rec
            } else {
                Toast.makeText(context, "فایل صفحه $fileName یافت نشد", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onCopyEntryCallback = remember(context) {
        { entry: LogEntry -> copyLogEntryToClipboard(context, entry) }
    }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            saveDiagnosticsZipToUri(
                context = context,
                uri = uri,
                sslCompatibilityActive = isCompatibilityModeActive
            )
        }
    }

    val errorWarnCount by remember(entries) {
        derivedStateOf {
            entries.count {
                it.level == LogLevel.FATAL || it.level == LogLevel.ERROR || it.level == LogLevel.WARN
            }
        }
    }

    val filteredEntries by remember(entries, searchQuery, levelFilter, categoryFilter, onlyWithSnapshot) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            entries.asReversed().filter { entry ->
                val matchesLevel = when (levelFilter) {
                    LogLevelFilter.ALL -> true
                    LogLevelFilter.ERRORS -> entry.level == LogLevel.ERROR || entry.level == LogLevel.FATAL
                    LogLevelFilter.WARNINGS_PLUS -> entry.level.ordinal >= LogLevel.WARN.ordinal
                    LogLevelFilter.INFO_PLUS -> entry.level.ordinal >= LogLevel.INFO.ordinal
                    LogLevelFilter.DEBUG_ONLY -> entry.level == LogLevel.DEBUG || entry.level == LogLevel.TRACE
                }
                if (!matchesLevel) return@filter false

                if (onlyWithSnapshot && entry.snapshot == null) return@filter false

                val comp = entry.component
                val matchesCategory = when (categoryFilter) {
                    LogCategoryFilter.ALL -> true
                    LogCategoryFilter.HTTP -> comp.contains("HTTP", true) || comp.contains("Cookie", true) || comp.contains("Cullinan", true)
                    LogCategoryFilter.PARSER -> comp.contains("Parser", true) || comp.contains("PageClassifier", true)
                    LogCategoryFilter.AUTH -> comp.contains("Auth", true) || comp.contains("Rsa", true)
                    LogCategoryFilter.MENU -> comp.contains("Menu", true)
                    LogCategoryFilter.MESSAGES -> comp.contains("Message", true)
                    LogCategoryFilter.SYSTEM -> comp.contains("Crash", true) || comp.contains("AppLogger", true) ||
                        comp.contains("UserPreferences", true) || comp.contains("Update", true) ||
                        comp.contains("UI", true) || comp.contains("Navigation", true)
                }
                if (!matchesCategory) return@filter false

                if (q.isEmpty()) return@filter true
                entry.message.lowercase().contains(q) ||
                    entry.component.lowercase().contains(q) ||
                    entry.operation.lowercase().contains(q) ||
                    (entry.flowId?.lowercase()?.contains(q) == true) ||
                    (entry.requestId?.lowercase()?.contains(q) == true) ||
                    (entry.http?.url?.lowercase()?.contains(q) == true) ||
                    (entry.snapshot?.fileName?.lowercase()?.contains(q) == true) ||
                    (entry.errorCategory?.lowercase()?.contains(q) == true) ||
                    (entry.exceptionMessage?.lowercase()?.contains(q) == true)
            }
        }
    }

    val filteredSnapshots by remember(snapshots, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            snapshots.asReversed().filter { snap ->
                if (q.isEmpty()) return@filter true
                snap.fileName.lowercase().contains(q) ||
                    snap.operation.lowercase().contains(q) ||
                    snap.url.lowercase().contains(q) ||
                    snap.statusCode.toString().contains(q) ||
                    (snap.flowId?.lowercase()?.contains(q) == true) ||
                    snap.rawContent.lowercase().contains(q)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PersianRtlLayout {
            Surface(
                shape = DialogSurfaceShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 760.dp)
                    .heightIn(min = 420.dp, max = 720.dp)
                    .padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "کنسول لاگ و صفحات HTML توسعه‌دهنده",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${entries.size} رویداد · $errorWarnCount خطا/هشدار · ${snapshots.size} صفحه خام",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                AppLogger.clearLogs()
                                Toast.makeText(context, "تمام لاگ‌ها و صفحات HTML پاکسازی شد", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_logs_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "پاکسازی لاگ‌ها",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_logs_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "بستن پنجره لاگ‌ها"
                            )
                        }
                    }

                    // Tabs
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.testTag("logs_events_tab"),
                            text = {
                                Text(
                                    text = "رویدادها (${filteredEntries.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.testTag("logs_snapshots_tab"),
                            text = {
                                Text(
                                    text = "صفحات HTML/دلتا (${filteredSnapshots.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (selectedTab == 0) {
                                    "جستجو در پیام، flowId، آدرس، خطا یا نام فایل..."
                                } else {
                                    "جستجو در آدرس، کد وضعیت یا سورس HTML/دلتا..."
                                },
                                fontSize = 11.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن جستجو", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("logs_search_input")
                    )

                    if (selectedTab == 0) {
                        // Filter Chips Row 1: Levels + Only HTML
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LogLevelFilter.entries.forEach { lvl ->
                                FilterChip(
                                    selected = levelFilter == lvl,
                                    onClick = { levelFilter = lvl },
                                    label = { Text(lvl.label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TealPrimary.copy(alpha = 0.16f),
                                        selectedLabelColor = TealPrimary
                                    ),
                                    modifier = Modifier.testTag("filter_level_${lvl.name.lowercase()}")
                                )
                            }
                            FilterChip(
                                selected = onlyWithSnapshot,
                                onClick = { onlyWithSnapshot = !onlyWithSnapshot },
                                leadingIcon = {
                                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                label = { Text("دارای HTML", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TealPrimary.copy(alpha = 0.16f),
                                    selectedLabelColor = TealPrimary
                                ),
                                modifier = Modifier.testTag("filter_only_snapshot")
                            )
                        }

                        // Filter Chips Row 2: Categories
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LogCategoryFilter.entries.forEach { cat ->
                                FilterChip(
                                    selected = categoryFilter == cat,
                                    onClick = { categoryFilter = cat },
                                    label = { Text(cat.label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.testTag("filter_category_${cat.name.lowercase()}")
                                )
                            }
                        }

                        // Events List
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (filteredEntries.isEmpty()) {
                                EmptyLogsPlaceholder(
                                    text = if (entries.isEmpty()) {
                                        "هنوز رویدادی ثبت نشده است."
                                    } else {
                                        "هیچ رویدادی با فیلترهای فعلی مطابقت ندارد."
                                    }
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = LogsListPadding
                                ) {
                                    items(
                                        items = filteredEntries,
                                        key = { entry -> if (entry.id > 0L) entry.id else "${entry.timestamp}_${entry.component}_${entry.operation}_${entry.message.hashCode()}" },
                                        contentType = { "log_entry" }
                                    ) { entry ->
                                        LogEntryCard(
                                            entry = entry,
                                            onOpenSnapshot = onOpenSnapshotCallback,
                                            onCopyEntry = onCopyEntryCallback
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Snapshots List
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (filteredSnapshots.isEmpty()) {
                                EmptyLogsPlaceholder(
                                    text = if (snapshots.isEmpty()) {
                                        "هنوز هیچ صفحه HTML یا پاسخ دلتایی از سرور ذخیره نشده است."
                                    } else {
                                        "هیچ صفحه‌ای با عبارت جستجو مطابقت ندارد."
                                    }
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = LogsListPadding
                                ) {
                                    items(
                                        items = filteredSnapshots,
                                        key = { "${it.sequence}_${it.fileName}" },
                                        contentType = { "snapshot_card" }
                                    ) { snap ->
                                        SnapshotCard(
                                            snapshot = snap,
                                            onClick = { activeSnapshot = snap }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Export Footer Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                showSharePreviewDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("share_zip_button")
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اشتراک‌گذاری ZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                saveZipLauncher.launch("kalinan_diagnostics_$timestamp.zip")
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("save_zip_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ذخیره فایل ZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    activeSnapshot?.let { snapshot ->
        SnapshotViewerDialog(
            snapshot = snapshot,
            onDismiss = { activeSnapshot = null }
        )
    }

    if (showSharePreviewDialog) {
        LogSharePreviewDialog(
            onConfirmShare = {
                showSharePreviewDialog = false
                shareDiagnosticZip(
                    context = context,
                    sslCompatibilityActive = isCompatibilityModeActive
                )
            },
            onDismiss = { showSharePreviewDialog = false }
        )
    }
}

@Composable
fun LogSharePreviewDialog(
    onConfirmShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val sanitizedPreview = remember {
        AppLogger.getExportableText().ifBlank { "لاگی برای نمایش وجود ندارد." }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PersianRtlLayout {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(min = 320.dp, max = 640.dp)
                    .testTag("log_share_preview_dialog"),
                shape = DialogSurfaceShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "پیش‌نمایش گزارش سالم‌سازی‌شده پیش از ارسال",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarnBg),
                        shape = LogCardShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("log_share_developer_warning")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarnFg,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "هشدار امنیتی: این گزارش را صرفاً برای توسعه‌دهنده ارسال کنید و از انتشار عمومی آن خودداری نمایید.",
                                color = WarnFg,
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "پیش‌نمایش متن پالایش‌شده (فیلدهای حساس مانند رمز عبور، کوکی و شناسه کاربری حذف یا جایگزین شده‌اند):",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = LogCardShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = sanitizedPreview.take(12000),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                                    .testTag("log_share_preview_text")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("cancel_share_log_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("انصراف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onConfirmShare,
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("confirm_share_log_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تأیید و ارسال به توسعه‌دهنده", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLogsPlaceholder(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LogEntryCard(
    entry: LogEntry,
    onOpenSnapshot: (String) -> Unit,
    onCopyEntry: (LogEntry) -> Unit
) {
    val (levelBg, levelFg) = when (entry.level) {
        LogLevel.FATAL -> FatalBg to Color.White
        LogLevel.ERROR -> ErrorBg to ErrorFg
        LogLevel.WARN -> WarnBg to WarnFg
        LogLevel.INFO -> InfoBg to TealPrimary
        LogLevel.DEBUG, LogLevel.TRACE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    val border = remember(entry.level, outlineColor) {
        if (entry.level == LogLevel.ERROR || entry.level == LogLevel.FATAL) {
            ErrorCardBorder
        } else {
            BorderStroke(1.dp, outlineColor.copy(alpha = 0.18f))
        }
    }

    val formattedTime = remember(entry.id, entry.timestamp) {
        entry.timestamp.substringAfter('T').removeSuffix("Z")
    }
    val metaLine = remember(entry.id) {
        buildList {
            entry.flowId?.let { add("flow=$it") }
            entry.requestId?.let { add("req=$it") }
            entry.durationMs?.let { add("${it}ms") }
            entry.errorCategory?.let { add("err=$it") }
        }.joinToString(" · ")
    }
    val httpSummary = remember(entry.id) {
        entry.http?.let { h ->
            buildString {
                append("${h.method} ${h.url}")
                h.statusCode?.let { append(" -> $it") }
                h.redirectLocation?.let { append(" [Loc: $it]") }
            }
        }
    }
    val stackPreview = remember(entry.id) {
        entry.stackTrace?.takeIf { it.isNotBlank() }?.lines()?.take(5)?.joinToString("\n")
    }

    val onCopyClick = remember(entry, onCopyEntry) { { onCopyEntry(entry) } }
    val snapFileName = entry.snapshot?.fileName
    val onSnapshotClick = remember(snapFileName, onOpenSnapshot) {
        { if (snapFileName != null) onOpenSnapshot(snapFileName) }
    }

    Card(
        shape = LogCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = border,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = SmallBadgeShape,
                            color = levelBg
                        ) {
                            Text(
                                text = entry.level.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = levelFg,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${entry.component}/${entry.operation}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedTime,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = onCopyClick,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "کپی متن رویداد",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (metaLine.isNotEmpty()) {
                    Text(
                        text = metaLine,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Text(
                    text = entry.message,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (httpSummary != null) {
                    Text(
                        text = httpSummary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                entry.exceptionType?.let { exType ->
                    Text(
                        text = "Exception: $exType - ${entry.exceptionMessage ?: ""}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ErrorFg,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                if (stackPreview != null) {
                    Text(
                        text = stackPreview,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = FatalBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(StackTraceBg, SmallBadgeShape)
                            .padding(6.dp)
                    )
                }

                entry.snapshot?.let { snap ->
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onSnapshotClick,
                        shape = SnapshotBtnShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("open_snapshot_${snap.sequence}")
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = "مشاهده فایل HTML",
                            modifier = Modifier.size(15.dp),
                            tint = TealPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "HTML/Delta #${snap.sequence}: ${snap.fileName} (${formatBytes(snap.sizeBytes)})",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TealPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(
    snapshot: PageSnapshotRecord,
    onClick: () -> Unit
) {
    val isOk = snapshot.statusCode in 200..299
    val isRedirect = snapshot.statusCode in 300..399
    val statusBg = when {
        isOk -> SnapshotOkBg
        isRedirect -> WarnBg
        else -> ErrorBg
    }
    val statusFg = when {
        isOk -> SnapshotOkFg
        isRedirect -> WarnFg
        else -> ErrorFg
    }
    val seqText = remember(snapshot.sequence) {
        "#${String.format(Locale.US, "%04d", snapshot.sequence)}"
    }
    val sizeText = remember(snapshot.sizeBytes) { formatBytes(snapshot.sizeBytes) }
    val preview = remember(snapshot.fileName, snapshot.rawContent.length) {
        snapshot.rawContent.trimStart().replace('\n', ' ').take(140)
    }

    Card(
        onClick = onClick,
        shape = LogCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("snapshot_card_${snapshot.sequence}")
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(shape = SmallBadgeShape, color = TealAlpha14) {
                            Text(
                                text = seqText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TealPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(shape = SmallBadgeShape, color = statusBg) {
                            Text(
                                text = "${snapshot.method} ${snapshot.statusCode}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = statusFg,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = snapshot.operation,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = sizeText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = snapshot.url,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Text(
                    text = "${snapshot.fileName} · flow=${snapshot.flowId ?: "-"}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Text(
                    text = preview,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SnapshotViewerDialog(
    snapshot: PageSnapshotRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val chunks = remember(snapshot.fileName, snapshot.rawContent.length) {
        snapshot.rawContent.lines().chunked(30).map { it.joinToString("\n") }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PersianRtlLayout {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .widthIn(max = 800.dp)
                    .heightIn(min = 420.dp, max = 740.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "سورس خام مرحله #${snapshot.sequence} (${snapshot.operation})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = "${snapshot.method} ${snapshot.statusCode} · ${snapshot.fileName} (${formatBytes(snapshot.sizeBytes)})",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_snapshot_viewer_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "بستن نمایشگر سورس")
                        }
                    }

                    HorizontalDivider()

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(10.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            SelectionContainer {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    itemsIndexed(
                                        items = chunks,
                                        key = { index, _ -> index },
                                        contentType = { _, _ -> "snapshot_chunk" }
                                    ) { _, chunk ->
                                        Text(
                                            text = chunk,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText(snapshot.fileName, snapshot.rawContent))
                                Toast.makeText(context, "سورس کامل صفحه کپی شد", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("copy_snapshot_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("کپی کامل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { shareSingleSnapshot(context, snapshot) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("share_snapshot_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ارسال فایل صفحه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun shareDiagnosticZip(
    context: Context,
    sslCompatibilityActive: Boolean
) {
    try {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipFile = File(exportDir, "kalinan_diagnostics_$timestamp.zip")

        val ok = AppLogger.exportDiagnosticsZip(
            destFile = zipFile,
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            sslCompatibilityActive = sslCompatibilityActive
        )
        if (!ok || !zipFile.exists()) {
            Toast.makeText(context, "ساخت فایل ZIP لاگ‌ها ناموفق بود", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            zipFile
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kalinan Diagnostic Bundle (Logs + HTML Snapshots)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "ارسال بسته کامل لاگ و HTML به توسعه‌دهنده"))
    } catch (e: Exception) {
        AppLogger.logError("SettingsScreen", "Failed to share diagnostic ZIP: ${e.message}", e, operation = "ShareDiagnosticsZipError")
        Toast.makeText(context, "خطا در اشتراک‌گذاری فایل ZIP: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun saveDiagnosticsZipToUri(
    context: Context,
    uri: Uri,
    sslCompatibilityActive: Boolean
) {
    try {
        val tempDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempZip = File(tempDir, "kalinan_save_temp.zip")
        val ok = AppLogger.exportDiagnosticsZip(
            destFile = tempZip,
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            sslCompatibilityActive = sslCompatibilityActive
        )
        if (!ok || !tempZip.exists()) {
            Toast.makeText(context, "ایجاد فایل ZIP ناموفق بود", Toast.LENGTH_SHORT).show()
            return
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            FileInputStream(tempZip).use { input ->
                input.copyTo(out)
            }
        }
        Toast.makeText(context, "فایل ZIP لاگ‌ها و صفحات HTML با موفقیت ذخیره شد", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        AppLogger.logError("DeveloperLogsDialog", "Failed to save ZIP to user URI: ${e.message}", e, operation = "SaveDiagnosticsZipError")
        Toast.makeText(context, "خطا در ذخیره فایل ZIP: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun shareSingleSnapshot(context: Context, snapshot: PageSnapshotRecord) {
    try {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, snapshot.fileName)
        outFile.writeText(snapshot.rawContent, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            outFile
        )
        val mime = when {
            snapshot.fileName.endsWith(".html") -> "text/html"
            snapshot.fileName.endsWith(".json") -> "application/json"
            else -> "text/plain"
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kalinan Snapshot #${snapshot.sequence}: ${snapshot.fileName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "ارسال فایل صفحه HTML/دلتا"))
    } catch (e: Exception) {
        AppLogger.logError("DeveloperLogsDialog", "Failed to share single snapshot: ${e.message}", e, operation = "ShareSnapshotError")
        Toast.makeText(context, "خطا در ارسال فایل صفحه: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun copyLogEntryToClipboard(context: Context, entry: LogEntry) {
    val text = buildString {
        append("[${entry.timestamp}] [${entry.level.name}] [${entry.component}/${entry.operation}]")
        entry.flowId?.let { append(" flow=$it") }
        entry.requestId?.let { append(" req=$it") }
        entry.durationMs?.let { append(" (${it}ms)") }
        appendLine()
        appendLine(entry.message)
        entry.http?.let { h ->
            appendLine("${h.method} ${h.url} -> ${h.statusCode ?: "-"}")
        }
        entry.exceptionType?.let { ex ->
            appendLine("Exception: $ex - ${entry.exceptionMessage ?: ""}")
        }
        entry.stackTrace?.takeIf { it.isNotBlank() }?.let { stack ->
            appendLine(stack)
        }
    }.trim()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("log_entry_${entry.id}", text))
    Toast.makeText(context, "متن کامل رویداد کپی شد", Toast.LENGTH_SHORT).show()
}

private fun formatBytes(bytes: Int): String {
    return if (bytes < 1024) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f KB", bytes / 1024f)
    }
}
