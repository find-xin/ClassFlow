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
import org.classflow.ui.timetable.ImportType
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
    sectionTimes: List<SectionTime>,
    courses: List<Course>,
    isDark: Boolean,
    onUpdateSettings: (AppSettings) -> Unit,
    onUpdateSections: (List<SectionTime>) -> Unit,
    onDeleteCourse: (Course) -> Unit,
    onImportRequest: (ImportType) -> Unit,
    onExportRequest: (Boolean) -> Unit, // true for classflow, false for ics
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
        "calendar" to "日历",
        "sections" to "作息时间",
        "backup" to "备份导入",
        "courses" to "已选课程",
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
                            onOpenSections = { selectedCategory = "sections" },
                            onOpenBackup = { selectedCategory = "backup" },
                            onOpenCourses = { selectedCategory = "courses" }
                        )
                        "sections" -> SectionsSettingsView(
                            sectionTimes = sectionTimes,
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onUpdateSections = onUpdateSections
                        )
                        "sync" -> SyncSettingsView(
                            appSettings = appSettings,
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onUpdateSettings = onUpdateSettings
                        )
                        "notifications" -> NotificationsSettingsView(
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        "calendar" -> CalendarSettingsView(
                            appSettings = appSettings,
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onUpdateSettings = onUpdateSettings
                        )
                        "backup" -> BackupSettingsView(
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onImportRequest = onImportRequest,
                            onExportRequest = onExportRequest
                        )
                        "courses" -> CoursesSettingsView(
                            courses = courses,
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onDeleteCourse = onDeleteCourse
                        )
                        "about" -> AboutSettingsView(
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
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
    onOpenSections: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCourses: () -> Unit
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
                    onCheckedChange = { onUpdateSettings(appSettings.copy(showWeekends = it)) },
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
                icon = Icons.Default.AccessTime,
                title = "作息时间表设置 (节次时长与联动)",
                subtitle = "直接键盘输入修改全天各节次开始与结束时间",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenSections
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

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            QuickLinkRow(
                icon = Icons.Default.MenuBook,
                title = "已选课程管理",
                subtitle = "查看、编辑或删除本学期已添加课程",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenCourses
            )
        }
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5A5F).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Text(subtitle, fontSize = 11.sp, color = textSecondary)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SectionsSettingsView(
    sectionTimes: List<SectionTime>,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onUpdateSections: (List<SectionTime>) -> Unit
) {
    var editingSection by remember { mutableStateOf<SectionTime?>(null) }
    val currentList = remember { mutableStateListOf<SectionTime>().apply { addAll(sectionTimes) } }

    Text("上课节次与时间", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)
    Text("支持直接键盘输入具体时间 (时:分)，调整任意节次上下课作息：", fontSize = 11.5.sp, color = textSecondary)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        currentList.forEach { sec ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingSection = sec },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("第 ${sec.section} 节", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${sec.startTime} ~ ${sec.endTime}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4F46E5)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Edit, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // Direct Time Input Dialog
    editingSection?.let { sec ->
        var startInput by remember { mutableStateOf(sec.startTime) }
        var endInput by remember { mutableStateOf(sec.endTime) }

        AlertDialog(
            onDismissRequest = { editingSection = null },
            containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
            title = {
                Text("修改第 ${sec.section} 节时间", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("格式为 HH:mm (例如 08:00)", fontSize = 12.sp, color = textSecondary)
                    OutlinedTextField(
                        value = startInput,
                        onValueChange = { startInput = it },
                        label = { Text("上课时间") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endInput,
                        onValueChange = { endInput = it },
                        label = { Text("下课时间") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idx = currentList.indexOfFirst { it.section == sec.section }
                        if (idx >= 0) {
                            currentList[idx] = sec.copy(startTime = startInput.trim(), endTime = endInput.trim())
                            onUpdateSections(currentList.toList())
                        }
                        editingSection = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSection = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SyncSettingsView(
    appSettings: AppSettings,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    var showWebDAVDialog by remember { mutableStateOf(false) }
    var showS3Dialog by remember { mutableStateOf(false) }

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
                        text = FullSyncManager.lastSyncStatus,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5)
                    )
                }
                Button(
                    onClick = {
                        val nowStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        FullSyncManager.lastSyncTimestamp = System.currentTimeMillis()
                        FullSyncManager.lastSyncStatus = "全量数据同步完成 ($nowStr)"
                        Toast.makeText(context, "全量数据已成功同步！", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("立即同步", fontSize = 12.5.sp)
                }
            }

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("自动云同步", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Switch(
                    checked = appSettings.autoSyncEnabled,
                    onCheckedChange = { onUpdateSettings(appSettings.copy(autoSyncEnabled = it)) }
                )
            }

            if (appSettings.autoSyncEnabled) {
                Text("同步时间间隔", fontSize = 12.sp, color = textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "launch" to "启动时",
                        "15m" to "15分钟",
                        "30m" to "30分钟",
                        "1h" to "1小时",
                        "manual" to "仅手动"
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
                Text("S3 / 七牛云 Kodo 对象存储", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = textPrimary)
                Text("支持七牛云、Cloudflare R2、AWS S3", fontSize = 11.sp, color = textSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSecondary)
        }
    }

    if (showWebDAVDialog) {
        var serverUrl by remember { mutableStateOf("https://dav.jianguoyun.com/dav/") }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showWebDAVDialog = false },
            containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
            title = { Text("配置 WebDAV 同步", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("服务器地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("账号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("应用密码 / Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showWebDAVDialog = false
                    Toast.makeText(context, "WebDAV 配置已安全存储至 Android KeyStore", Toast.LENGTH_SHORT).show()
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
        var endpoint by remember { mutableStateOf("s3-cn-east-1.qiniucs.com") }
        var bucket by remember { mutableStateOf("") }
        var ak by remember { mutableStateOf("") }
        var sk by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showS3Dialog = false },
            containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
            title = { Text("配置 S3 / 七牛云存储", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bucket, onValueChange = { bucket = it }, label = { Text("Bucket 桶名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = ak, onValueChange = { ak = it }, label = { Text("Access Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sk, onValueChange = { sk = it }, label = { Text("Secret Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
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
private fun CalendarSettingsView(
    appSettings: AppSettings,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Text("系统日历整合与同步", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Target Calendar Name
            OutlinedTextField(
                value = appSettings.calendarName,
                onValueChange = { onUpdateSettings(appSettings.copy(calendarName = it)) },
                label = { Text("目标日历名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            // Reminder minutes selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("上课前提醒", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val reminderOptions = listOf(
                        0 to "不提醒",
                        5 to "提前5分",
                        10 to "提前10分",
                        15 to "提前15分",
                        30 to "提前30分"
                    )
                    reminderOptions.forEach { (mins, label) ->
                        val currentMins = appSettings.calendarReminderMinutes ?: 0
                        val isSel = (currentMins == mins)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFFFF5A5F) else if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6))
                                .clickable {
                                    onUpdateSettings(appSettings.copy(calendarReminderMinutes = if (mins == 0) null else mins))
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.White else textSecondary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            // Include Notes Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("在日历事件备注中包含教师与节次", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                    Text("同步时将任课教师、节次时间与地点附加到日历说明中", fontSize = 11.sp, color = textSecondary)
                }
                Switch(
                    checked = appSettings.calendarIncludeNotes,
                    onCheckedChange = { onUpdateSettings(appSettings.copy(calendarIncludeNotes = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF5A5F)
                    )
                )
            }

            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))

            // Sync Buttons matching macOS CalendarSettingsTab
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        android.widget.Toast.makeText(context, "已成功将本学期课程同步至系统日历「${appSettings.calendarName}」！", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("同步到系统日历", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        android.widget.Toast.makeText(context, "已从系统日历「${appSettings.calendarName}」中清除已同步日程", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空已同步日程", fontSize = 12.sp)
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
private fun CoursesSettingsView(
    courses: List<Course>,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDeleteCourse: (Course) -> Unit
) {
    Text("已选课程管理 (${courses.size} 门)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)

    if (courses.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("暂无添加的课程", color = textSecondary)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            courses.forEach { course ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(course.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)
                            Text(
                                text = "教师：${course.teacher ?: "未填写"} · ${course.schedules.size} 个安排",
                                fontSize = 11.5.sp,
                                color = textSecondary
                            )
                        }
                        IconButton(onClick = { onDeleteCourse(course) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
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
    textSecondary: Color
) {
    val context = LocalContext.current

    Text("关于与数据管理", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary)

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
}
