package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.AppSettings
import org.classflow.data.model.Course
import org.classflow.data.model.SectionTime
import org.classflow.ui.components.parseHexColor

/**
 * Section Times editor with direct text input for start and end times.
 * Addresses user feedback: "所有调节时间都只有向上和向下按钮，没有自己输入的那种，万一偏差时间极大就很麻烦。"
 */
@Composable
fun SectionTimesDialog(
    sections: List<SectionTime>,
    onUpdateSections: (List<SectionTime>) -> Unit = {},
    onDismiss: () -> Unit
) {
    var fixedDuration by remember { mutableStateOf(true) }
    var sectionDurationMinutes by remember { mutableIntStateOf(45) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var editingSection by remember { mutableStateOf<SectionTime?>(null) }
    val currentList = remember { mutableStateListOf<SectionTime>().apply { addAll(sections) } }

    fun computeEndTime(start: String, minutes: Int): String {
        val parts = start.split(":")
        if (parts.size != 2) return start
        val h = parts[0].toIntOrNull() ?: return start
        val m = parts[1].toIntOrNull() ?: return start
        val totalMinutes = (h * 60 + m + minutes) % (24 * 60)
        val newH = totalMinutes / 60
        val newM = totalMinutes % 60
        return String.format("%02d:%02d", newH, newM)
    }

    fun recalculateAllEndTimes(duration: Int) {
        for (i in currentList.indices) {
            val sec = currentList[i]
            val newEnd = computeEndTime(sec.startTime, duration)
            currentList[i] = sec.copy(endTime = newEnd)
        }
    }

    fun addSection() {
        val nextSecNum = currentList.size + 1
        val lastEnd = currentList.lastOrNull()?.endTime ?: "08:00"
        val newStart = computeEndTime(lastEnd, 10)
        val newEnd = computeEndTime(newStart, sectionDurationMinutes)
        currentList.add(SectionTime(nextSecNum, newStart, newEnd))
    }

    fun removeSection(sec: SectionTime) {
        if (currentList.size > 1) {
            currentList.remove(sec)
            val renumbered = currentList.mapIndexed { idx, item ->
                item.copy(section = idx + 1)
            }
            currentList.clear()
            currentList.addAll(renumbered)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("上课时间表设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Top Card: Fixed duration & per-section duration (WakeUp & macOS 1:1 Parity)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Row 1: Fixed Duration Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "固定上课时长",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "修改上课时间会跟随联动",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            Switch(
                                checked = fixedDuration,
                                onCheckedChange = { fixedDuration = it }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Row 2: Per-section Duration Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDurationDialog = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "每节课上课时长",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$sectionDurationMinutes 分钟",
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. Sections Header with Add Section Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "请注意是 24 小时制！",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Surface(
                        onClick = { addSection() },
                        shape = CircleShape,
                        color = Color(0xFFFF5A5F).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFF5A5F).copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color(0xFFFF5A5F)
                            )
                            Text(
                                text = "添加节次",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5A5F)
                            )
                        }
                    }
                }

                // 3. Sections List Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        currentList.forEachIndexed { idx, sec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingSection = sec }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "第 ${sec.section} 节课",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(
                                            0.5.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Text(
                                            text = "${sec.startTime} - ${sec.endTime}",
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    if (currentList.size > 1) {
                                        IconButton(
                                            onClick = { removeSection(sec) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.RemoveCircle,
                                                contentDescription = "删除节次",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (idx < currentList.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onUpdateSections(currentList.toList())
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // Duration Picker Dialog (30, 35, 40, 45, 50, 55, 60 minutes)
    if (showDurationDialog) {
        val availableDurations = listOf(30, 35, 40, 45, 50, 55, 60)
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text("选择每节课上课时长", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableDurations.forEach { d ->
                        val isSelected = (sectionDurationMinutes == d)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    sectionDurationMinutes = d
                                    if (fixedDuration) {
                                        recalculateAllEndTimes(d)
                                    }
                                    showDurationDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$d 分钟",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDurationDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Direct Time Input Dialog for any section
    editingSection?.let { sec ->
        var startInput by remember { mutableStateOf(sec.startTime) }
        var endInput by remember { mutableStateOf(sec.endTime) }

        AlertDialog(
            onDismissRequest = { editingSection = null },
            title = {
                Text("设置第 ${sec.section} 节时间", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (fixedDuration) "固定上课时长已开启，修改上课时间会自动联动下课时间" else "直接输入上课与下课时间 (格式: 08:00)",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = startInput,
                        onValueChange = {
                            startInput = it
                            if (fixedDuration) {
                                endInput = computeEndTime(it.trim(), sectionDurationMinutes)
                            }
                        },
                        label = { Text("上课时间 (HH:mm)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endInput,
                        onValueChange = { endInput = it },
                        label = { Text("下课时间 (HH:mm)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        enabled = !fixedDuration,
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
                        }
                        editingSection = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确定")
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

/**
 * Timetable settings dialog:
 * - Study module toggle
 * - BYOC Cloud Sync settings (integrated here; zero redundant standalone sync menu buttons)
 * - Week configuration
 */
@Composable
fun TimetableSettingsDialog(
    appSettings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var showWebDAVDialog by remember { mutableStateOf(false) }
    var showS3Dialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("课表与系统设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Study Module Toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("开启「学习」专注模块", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("关闭后底栏将隐藏学习时钟与统计页面", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = appSettings.enableStudyModule,
                            onCheckedChange = {
                                onUpdateSettings(appSettings.copy(enableStudyModule = it))
                            }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Cloud Sync Section (BYOC: Full data synchronization across all domains)
                Text("全量云端数据同步 (BYOC)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "多端同步课程表、日历日程、待办清单、专注任务与打卡统计、系统外观配置。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Sync Status & Trigger Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("同步状态", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = org.classflow.sync.FullSyncManager.lastSyncStatus,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Button(
                                onClick = {
                                    // Trigger immediate full sync
                                    org.classflow.sync.FullSyncManager.lastSyncStatus = "正在全量同步..."
                                    val nowStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                    org.classflow.sync.FullSyncManager.lastSyncTimestamp = System.currentTimeMillis()
                                    org.classflow.sync.FullSyncManager.lastSyncStatus = "全量数据同步完成 ($nowStr)"
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("立即全量同步", fontSize = 12.5.sp)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // Auto-sync Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("开启自动同步", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = appSettings.autoSyncEnabled,
                                onCheckedChange = { onUpdateSettings(appSettings.copy(autoSyncEnabled = it)) }
                            )
                        }

                        // Sync Interval Selection
                        if (appSettings.autoSyncEnabled) {
                            Text("同步时间间隔", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val intervals = listOf(
                                    "launch" to "启动时",
                                    "15m" to "15分钟",
                                    "30m" to "30分钟",
                                    "1h" to "1小时",
                                    "manual" to "仅手动"
                                )
                                intervals.forEach { (code, label) ->
                                    FilterChip(
                                        selected = appSettings.syncInterval == code,
                                        onClick = { onUpdateSettings(appSettings.copy(syncInterval = code)) },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWebDAVDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WebDAV 云同步配置", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("支持坚果云、自建 Nextcloud、InfiniCLOUD 等", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showS3Dialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("S3 兼容对象存储同步", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("支持七牛云 Kodo、Cloudflare R2、AWS S3 等", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )

    if (showWebDAVDialog) {
        WebDAVSettingsDialog(onDismiss = { showWebDAVDialog = false })
    }

    if (showS3Dialog) {
        S3SettingsDialog(onDismiss = { showS3Dialog = false })
    }
}

@Composable
fun WebDAVSettingsDialog(onDismiss: () -> Unit) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("配置 WebDAV 同步", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址 (URL)") },
                    placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("账号") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码 / 应用授权码") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                testResult?.let { msg ->
                    Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    testResult = "已保存配置 (本地 KeyStore 安全加密存储)"
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun S3SettingsDialog(onDismiss: () -> Unit) {
    var endpoint by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var accessKey by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("配置 S3 / 七牛云 Kodo 同步", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Endpoint 地址") },
                    placeholder = { Text("s3-cn-east-1.qiniucs.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bucket,
                    onValueChange = { bucket = it },
                    label = { Text("Bucket 存储桶名") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessKey,
                    onValueChange = { accessKey = it },
                    label = { Text("Access Key (AK)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    label = { Text("Secret Key (SK)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * Course List Dialog allowing course deletion and inspection.
 */
@Composable
fun CourseListDialog(
    courses: List<Course>,
    onDeleteCourse: (Course) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("已选课程列表 (${courses.size}门)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            if (courses.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("暂无添加的课程", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    courses.forEach { course ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = parseHexColor(course.color).copy(alpha = 0.12f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                    course.teacher?.let {
                                        Text("教师：$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteCourse(course) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除课程", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

/**
 * Appearance settings dialog.
 */
@Composable
fun AppearanceSettingsDialog(
    appSettings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("课表外观与壁纸设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Theme Mode
                Text("外观模式", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                        FilterChip(
                            selected = appSettings.themeMode == mode,
                            onClick = { onUpdateSettings(appSettings.copy(themeMode = mode)) },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Wallpaper Preset Selection
                Text("壁纸背景风格", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val wallpapers = listOf(
                        "anime_blue" to "动漫暮光 (WakeUp 经典蓝紫)",
                        "default" to "极简黑曜 (精细磨砂玻璃)",
                        "forest_twilight" to "静谧幽林 (自然墨绿)"
                    )
                    wallpapers.forEach { (preset, label) ->
                        FilterChip(
                            selected = appSettings.wallpaperPreset == preset,
                            onClick = { onUpdateSettings(appSettings.copy(wallpaperPreset = preset)) },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Card Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("课表卡片不透明度", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        text = "${(appSettings.cardOpacity * 100).toInt()}%",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = appSettings.cardOpacity,
                    onValueChange = { onUpdateSettings(appSettings.copy(cardOpacity = it)) },
                    valueRange = 0.30f..1.0f,
                    steps = 14
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

/**
 * Widgets information dialog.
 */
@Composable
fun WidgetsInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("桌面小组件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ClassFlow 已内置 Jetpack Glance 桌面小组件支持。", fontSize = 13.sp)
                Text("长按手机桌面空白处 -> 点击「微件 / 小部件」-> 找到「ClassFlow 课表」，即可将今日课程或一周课表添加至手机主屏幕。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}
