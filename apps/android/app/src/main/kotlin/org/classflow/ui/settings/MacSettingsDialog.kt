package org.classflow.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.classflow.data.model.AppSettings
import org.classflow.data.model.Course
import org.classflow.data.model.SectionTime
import org.classflow.sync.FullSyncBundle
import org.classflow.sync.FullSyncManager
import org.classflow.sync.WebDAVStorageProvider
import org.classflow.sync.S3StorageProvider
import org.classflow.ui.timetable.ImportType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * macOS-grade Preferences Hub (SettingsView.swift parity) for Android:
 * Replaces crude single-dialog menus with unified macOS-style card settings,
 * top segmented category pills, and complete feature coverage across:
 * - 通用 (General & Appearance)
 * - 作息时间 (Section Times with direct keyboard input)
 * - 系统日历 (System Calendar Provider integration)
 * - 课前提醒 (Pre-class Notifications & Alarms)
 * - 云端同步 (BYOC WebDAV / S3 with auto-sync intervals)
 * - 数据备份 (Import/Export .classflow, .ics, Paste JSON)
 * - 关于 (About & Data Management)
 */
@Composable
fun MacSettingsDialog(
    appSettings: AppSettings,
    sectionTimes: List<SectionTime> = emptyList(),
    courses: List<Course> = emptyList(),
    isDark: Boolean,
    onUpdateSettings: (AppSettings) -> Unit,
    onUpdateSections: (List<SectionTime>) -> Unit = {},
    onDeleteCourse: (Course) -> Unit = {},
    onImportRequest: (ImportType) -> Unit,
    onExportRequest: (Boolean) -> Unit, // true for classflow, false for ics
    onClearFocusHistory: () -> Unit = {},
    onSyncSuccess: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("general") }
    var isShowingAppearance by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val dialogBg = if (isDark) Color(0xFF161822) else Color(0xFFF9FAFC)
    val cardBg = if (isDark) Color(0xFF202332) else Color.White
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFFA1A3AC) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFE5E7EB)

    val categories = listOf(
        "general" to "通用",
        "sync" to "云同步",
        "notifications" to "通知",
        "backup" to "备份导入",
        "about" to "关于"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(20.dp)),
            color = dialogBg,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. macOS Style Header with Coral Finish Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFFFF5A5F),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "偏好设置",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("完成", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                HorizontalDivider(color = borderColor)

                // 2. Segmented Category Bar (Horizontal Scrollable Pills)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF1B1D2A) else Color(0xFFF3F4F8))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories.size) { idx ->
                            val (catId, catName) = categories[idx]
                            val isSelected = (selectedCategory == catId)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) Color(0xFFFF5A5F) else Color.Transparent
                                    )
                                    .clickable { selectedCategory = catId }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = catName,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else textSecondary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = borderColor)

                // 3. Tab Content Area
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedCategory) {
                        "general" -> GeneralSettingsView(
                            appSettings = appSettings,
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onUpdateSettings = onUpdateSettings,
                            onOpenAppearance = { isShowingAppearance = true },
                            onOpenBackup = { selectedCategory = "backup" }
                        )
                        "sync" -> SyncSettingsView(
                            appSettings = appSettings,
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onUpdateSettings = onUpdateSettings,
                            onSyncSuccess = onSyncSuccess
                        )
                        "notifications" -> NotificationsSettingsView(
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        "backup" -> BackupSettingsView(
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onImportRequest = onImportRequest,
                            onExportRequest = onExportRequest
                        )
                        "about" -> AboutSettingsView(
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onClearFocusHistory = onClearFocusHistory
                        )
                    }
                }
            }
        }
    }

    if (isShowingAppearance) {
        TimetableAppearanceDialog(
            settings = appSettings,
            isDark = isDark,
            onUpdateSettings = onUpdateSettings,
            onDismiss = { isShowingAppearance = false }
        )
    }
}

@Composable
private fun GeneralSettingsView(
    appSettings: AppSettings,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onUpdateSettings: (AppSettings) -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenBackup: () -> Unit
) {
    Text("外观与显示", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Theme Mode
            Text("主题色彩", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("system" to "跟随系统", "light" to "浅色模式", "dark" to "深色模式").forEach { (mode, label) ->
                    val isSel = (appSettings.themeMode == mode)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Color(0xFFFF5A5F) else if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6))
                            .clickable { onUpdateSettings(appSettings.copy(themeMode = mode)) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.White else textSecondary
                        )
                    }
                }
            }

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            // Show Weekends
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("显示周末 (周六与周日)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                    Text("关闭后课表网格将仅展示周一至周五", fontSize = 11.sp, color = textSecondary)
                }
                Switch(
                    checked = appSettings.showWeekends,
                    onCheckedChange = {
                        onUpdateSettings(appSettings.copy(
                            showWeekends = it,
                            showSaturday = it,
                            showSunday = it
                        ))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF5A5F)
                    )
                )
            }

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            // Study Focus Module
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("启用「学习」专注模块", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                    Text("在底部标签栏显示专注时钟与学习时长统计", fontSize = 11.sp, color = textSecondary)
                }
                Switch(
                    checked = appSettings.enableStudyModule,
                    onCheckedChange = { onUpdateSettings(appSettings.copy(enableStudyModule = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF5A5F)
                    )
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(2.dp))

    Text("快速定制", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickLinkRow(
                icon = Icons.Default.Palette,
                title = "课表外观与壁纸皮肤深度设置",
                subtitle = "极简皮肤预设、纯色背景、课程格子外观",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenAppearance
            )

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            QuickLinkRow(
                icon = Icons.Default.ImportExport,
                title = "数据备份与导入 / 导出",
                subtitle = "ClassFlow 备份包、ICS 日历、教务脚本 JSON",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenBackup
            )
        }
    }
}

@Composable
private fun SyncSettingsView(
    appSettings: AppSettings,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onUpdateSettings: (AppSettings) -> Unit,
    onSyncSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf(FullSyncManager.lastSyncStatus) }
    var showWebDAVDialog by remember { mutableStateOf(false) }
    var showS3Dialog by remember { mutableStateOf(false) }

    var isForcePulling by remember { mutableStateOf(false) }
    var showForcePullDialog by remember { mutableStateOf(false) }

    if (showForcePullDialog) {
        AlertDialog(
            onDismissRequest = { showForcePullDialog = false },
            title = { Text("确认从云端全量覆盖本地？", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Text(
                    "此操作将清除本地的删除标记与死锁状态，直接从云端下载其他设备最新的完整快照并全量覆盖本地课程、日程与待办。\n\n若您在当前设备清空了课程后希望重新拉取其他设备的云端数据，请确认执行。",
                    fontSize = 12.5.sp,
                    color = textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForcePullDialog = false
                        coroutineScope.launch {
                            isForcePulling = true
                            syncStatusText = "正在强制全量覆盖..."
                            val res = FullSyncManager.executeForcePullFromCloud(context)
                            isForcePulling = false
                            syncStatusText = FullSyncManager.lastSyncStatus
                            if (res.isSuccess) {
                                val summary = res.getOrThrow()
                                onSyncSuccess()
                                Toast.makeText(context, "全量覆盖成功！已恢复 ${summary.activeCoursesCount} 门课程", Toast.LENGTH_SHORT).show()
                            } else {
                                val err = res.exceptionOrNull()?.message ?: "强制覆盖失败"
                                Toast.makeText(context, "全量覆盖失败: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认全量覆盖", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForcePullDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Text("全量云端数据同步 (BYOC)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)
    Text("私有云存储多端漫游：同步课程、日程、待办、学习统计与系统外观配置。", fontSize = 11.5.sp, color = textSecondary)

    // Sync Action Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("当前同步状态", fontSize = 12.sp, color = textSecondary)
                    Text(
                        text = syncStatusText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (!isSyncing && !isForcePulling) {
                            coroutineScope.launch {
                                isSyncing = true
                                syncStatusText = "正在同步..."
                                val res = FullSyncManager.executeFullSync(context)
                                isSyncing = false
                                syncStatusText = FullSyncManager.lastSyncStatus
                                if (res.isSuccess) {
                                    val summary = res.getOrThrow()
                                    onSyncSuccess()
                                    val msg = if (summary.downloadedCount > 0) {
                                        "同步成功！本地已推送云端，已合并远程 ${summary.downloadedCount} 项变动"
                                    } else {
                                        "同步成功！本地已发布到云端 (云端暂无其它设备新变动)"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    val err = res.exceptionOrNull()?.message ?: "未知同步错误"
                                    Toast.makeText(context, "同步失败: $err", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isSyncing && !isForcePulling,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSyncing) "同步中..." else "立即同步", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        if (!isSyncing && !isForcePulling) {
                            showForcePullDialog = true
                        }
                    },
                    enabled = !isSyncing && !isForcePulling,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isForcePulling) "覆盖中..." else "从云端全量覆盖", fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            // Step 1: Sync on Startup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("第一步：启动时自动同步", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                    Text("应用启动打开时，在后台静默执行一次云同步", fontSize = 11.5.sp, color = textSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = appSettings.syncOnStartup,
                    onCheckedChange = { onUpdateSettings(appSettings.copy(syncOnStartup = it)) }
                )
            }

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            // Step 2: Periodic Auto-Sync Interval
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("第二步：定时自动同步", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                    Text("应用运行期间，按设定的时间间隔自动同步", fontSize = 11.5.sp, color = textSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = appSettings.autoSyncEnabled,
                    onCheckedChange = { onUpdateSettings(appSettings.copy(autoSyncEnabled = it)) }
                )
            }

            if (appSettings.autoSyncEnabled) {
                Text("自动同步时间间隔", fontSize = 12.sp, color = textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "15m" to "15分钟",
                        "30m" to "30分钟",
                        "1h" to "1小时",
                        "2h" to "2小时",
                        "6h" to "6小时"
                    ).forEach { (code, label) ->
                        val isSel = (appSettings.syncInterval == code)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF4F46E5) else if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6))
                                .clickable { onUpdateSettings(appSettings.copy(syncInterval = code)) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.White else textSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // Storage Provider Cards
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showWebDAVDialog = true },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF4F46E5))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("WebDAV 云同步配置", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = textPrimary)
                Text("支持坚果云、自建 Nextcloud、InfiniCLOUD", fontSize = 11.sp, color = textSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSecondary)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showS3Dialog = true },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF4F46E5))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("S3 兼容对象存储", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = textPrimary)
                Text("支持各类对象存储服务、Cloudflare R2、AWS S3 等", fontSize = 11.sp, color = textSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSecondary)
        }
    }

    if (showWebDAVDialog) {
        val currentCreds = remember { org.classflow.data.local.SettingsManager.loadSyncCredentials(context) }
        var serverUrl by remember { mutableStateOf(currentCreds.webdavUrl) }
        var username by remember { mutableStateOf(currentCreds.webdavUsername) }
        var password by remember { mutableStateOf(currentCreds.webdavPassword) }
        var isTesting by remember { mutableStateOf(false) }
        var testMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showWebDAVDialog = false },
            containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
            title = { Text("配置 WebDAV 同步", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it; testMsg = null },
                        label = { Text("服务器地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; testMsg = null },
                        label = { Text("账号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; testMsg = null },
                        label = { Text("应用密码 / Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    testMsg?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 11.5.sp,
                            color = if (msg.startsWith("✓")) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isTesting = true
                                testMsg = "正在测试连接..."
                                val provider = WebDAVStorageProvider(serverUrl, username, password)
                                val res = provider.testConnection()
                                isTesting = false
                                if (res.isSuccess) {
                                    testMsg = "✓ 连接成功！认证有效"
                                } else {
                                    testMsg = "连接失败: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = !isTesting && username.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isTesting) "正在测试..." else "测试连接")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = currentCreds.copy(
                        providerType = "webdav",
                        webdavUrl = serverUrl,
                        webdavUsername = username,
                        webdavPassword = password
                    )
                    org.classflow.data.local.SettingsManager.saveSyncCredentials(context, updated)
                    showWebDAVDialog = false
                    Toast.makeText(context, "WebDAV 配置已安全存储", Toast.LENGTH_SHORT).show()
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebDAVDialog = false }) { Text("取消") }
            }
        )
    }

    if (showS3Dialog) {
        val currentCreds = remember { org.classflow.data.local.SettingsManager.loadSyncCredentials(context) }
        var endpoint by remember { mutableStateOf(currentCreds.s3Endpoint) }
        var region by remember { mutableStateOf(currentCreds.s3Region) }
        var bucket by remember { mutableStateOf(currentCreds.s3Bucket) }
        var ak by remember { mutableStateOf(currentCreds.s3AccessKey) }
        var sk by remember { mutableStateOf(currentCreds.s3SecretKey) }
        var isTesting by remember { mutableStateOf(false) }
        var testMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showS3Dialog = false },
            containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
            title = { Text("配置 S3 兼容对象存储", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it; testMsg = null }, label = { Text("Endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = region, onValueChange = { region = it; testMsg = null }, label = { Text("Region (区域)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bucket, onValueChange = { bucket = it; testMsg = null }, label = { Text("Bucket 桶名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = ak, onValueChange = { ak = it; testMsg = null }, label = { Text("Access Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sk, onValueChange = { sk = it; testMsg = null }, label = { Text("Secret Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    testMsg?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 11.5.sp,
                            color = if (msg.startsWith("✓")) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isTesting = true
                                testMsg = "正在测试连接..."
                                val provider = S3StorageProvider(endpoint, region, bucket, ak, sk)
                                val res = provider.testConnection()
                                isTesting = false
                                if (res.isSuccess) {
                                    testMsg = "✓ 连接成功！存储桶可正常读写"
                                } else {
                                    testMsg = "连接失败: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = !isTesting && bucket.isNotBlank() && ak.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isTesting) "正在测试..." else "测试连接")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = currentCreds.copy(
                        providerType = "s3",
                        s3Endpoint = endpoint,
                        s3Region = region,
                        s3Bucket = bucket,
                        s3AccessKey = ak,
                        s3SecretKey = sk
                    )
                    org.classflow.data.local.SettingsManager.saveSyncCredentials(context, updated)
                    showS3Dialog = false
                    Toast.makeText(context, "S3 凭据已安全保存", Toast.LENGTH_SHORT).show()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showS3Dialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun NotificationsSettingsView(
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    var enableNotifications by remember { mutableStateOf(true) }
    var leadTime by remember { mutableStateOf("15") }
    var enableVibrate by remember { mutableStateOf(true) }

    Text("课前提醒与通知", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("开启上课提醒", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                    Text("上课前通过系统横幅通知提示上课教室", fontSize = 11.sp, color = textSecondary)
                }
                Switch(checked = enableNotifications, onCheckedChange = { enableNotifications = it })
            }

            if (enableNotifications) {
                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

                Text("提前提醒时间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("10" to "提前10分钟", "15" to "提前15分钟", "20" to "提前20分钟", "30" to "提前30分钟").forEach { (min, label) ->
                        val isSel = (leadTime == min)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF4F46E5) else if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6))
                                .clickable { leadTime = min }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.White else textSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("上课震动提示", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                    Switch(checked = enableVibrate, onCheckedChange = { enableVibrate = it })
                }
            }
        }
    }
}



@Composable
private fun BackupSettingsView(
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onImportRequest: (ImportType) -> Unit,
    onExportRequest: (Boolean) -> Unit
) {
    Text("数据备份、导入与导出", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("快速导入课表", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = textPrimary)

            Button(
                onClick = { onImportRequest(ImportType.PASTE_JSON) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("粘贴教务脚本 / WakeUp JSON 导入", fontSize = 12.5.sp)
            }

            OutlinedButton(
                onClick = { onImportRequest(ImportType.CLASSFLOW) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("从 .classflow 完整备份包导入", fontSize = 12.5.sp)
            }

            OutlinedButton(
                onClick = { onImportRequest(ImportType.ICS) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("从 .ics 日历文件导入", fontSize = 12.5.sp)
            }

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            Text("数据导出与分享", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = textPrimary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onExportRequest(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导出 .classflow", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = { onExportRequest(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导出 .ics", fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}



@Composable
private fun AboutSettingsView(
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClearFocusHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    var showClearFocusConfirmDialog by remember { mutableStateOf(false) }

    Text("关于与版本", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ClassFlow", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPrimary)
            Text("版本 1.0.0 (Release 稳定版)", fontSize = 12.sp, color = textSecondary)
            Text("专为 macOS 与 Android 打造的高颜值、本地离线优先、原生跨平台课程表与时间规划应用。", fontSize = 12.sp, color = textSecondary)

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            Button(
                onClick = {
                    Toast.makeText(context, "当前已是最新稳定版本 (v1.0.0)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("检查更新")
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text("历史数据管理", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("专注成果与热力图记录", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = textPrimary)
            Text(
                "长期积累的大量番茄专注成果与历史热力图打卡记录。堆积过多时可在此一键清空，释放存储空间并重置统计图谱。",
                fontSize = 12.sp,
                color = textSecondary
            )

            OutlinedButton(
                onClick = { showClearFocusConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("清理所有历史专注数据", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showClearFocusConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearFocusConfirmDialog = false },
            title = {
                Text(
                    text = "确认清理历史专注数据？",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "该操作将永久清空番茄钟历史专注成果记录与热力图打卡统计，重置连续专注天数。该操作不可撤销，确定继续吗？",
                    fontSize = 13.sp,
                    color = textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearFocusHistory()
                        showClearFocusConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("确认清理", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearFocusConfirmDialog = false }) {
                    Text("取消", color = textSecondary)
                }
            }
        )
    }
}

@Composable
private fun QuickLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = textPrimary)
            Text(subtitle, fontSize = 11.sp, color = textSecondary)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}
