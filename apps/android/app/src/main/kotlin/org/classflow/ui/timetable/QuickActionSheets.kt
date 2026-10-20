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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.AppSettings
import org.classflow.data.model.CalendarEvent
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
    onSyncSuccess: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var isForcePulling by remember { mutableStateOf(false) }
    var showForcePullDialog by remember { mutableStateOf(false) }
    var syncStatusText by remember { mutableStateOf(org.classflow.sync.FullSyncManager.lastSyncStatus) }
    var showWebDAVDialog by remember { mutableStateOf(false) }
    var showS3Dialog by remember { mutableStateOf(false) }

    if (showForcePullDialog) {
        AlertDialog(
            onDismissRequest = { showForcePullDialog = false },
            title = { Text("确认从云端全量覆盖本地？", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Text(
                    "此操作将清除本地的删除标记与死锁状态，直接从云端下载其他设备最新的完整快照并全量覆盖本地课程、日程与待办。\n\n若您在当前设备清空了课程后希望重新拉取其他设备的云端数据，请确认执行。",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForcePullDialog = false
                        coroutineScope.launch {
                            isForcePulling = true
                            syncStatusText = "正在强制全量覆盖..."
                            val res = org.classflow.sync.FullSyncManager.executeForcePullFromCloud(context)
                            isForcePulling = false
                            syncStatusText = org.classflow.sync.FullSyncManager.lastSyncStatus
                            if (res.isSuccess) {
                                val summary = res.getOrThrow()
                                onSyncSuccess()
                                android.widget.Toast.makeText(context, "全量覆盖成功！已恢复 ${summary.activeCoursesCount} 门课程", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val err = res.exceptionOrNull()?.message ?: "强制覆盖失败"
                                android.widget.Toast.makeText(context, "全量覆盖失败: $err", android.widget.Toast.LENGTH_LONG).show()
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
                                    text = syncStatusText,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
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
                                            syncStatusText = "正在全量同步..."
                                            val res = org.classflow.sync.FullSyncManager.executeFullSync(context)
                                            isSyncing = false
                                            syncStatusText = org.classflow.sync.FullSyncManager.lastSyncStatus
                                            if (res.isSuccess) {
                                                val s = res.getOrThrow()
                                                onSyncSuccess()
                                                android.widget.Toast.makeText(context, "全量数据同步完成！发现 ${s.peerDevicesCount} 台协同设备", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                val err = res.exceptionOrNull()?.message ?: "同步失败"
                                                android.widget.Toast.makeText(context, "同步失败: $err", android.widget.Toast.LENGTH_LONG).show()
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // Step 1: Sync on Startup
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("第一步：启动时自动同步", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Text("应用打开时静默执行一次云同步", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = appSettings.syncOnStartup,
                                onCheckedChange = { onUpdateSettings(appSettings.copy(syncOnStartup = it)) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // Step 2: Periodic Auto-Sync Interval
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("第二步：定时自动同步", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Text("运行期间按设定间隔自动同步", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = appSettings.autoSyncEnabled,
                                onCheckedChange = { onUpdateSettings(appSettings.copy(autoSyncEnabled = it)) }
                            )
                        }

                        // Sync Interval Selection
                        if (appSettings.autoSyncEnabled) {
                            Text("自动同步时间间隔", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val intervals = listOf(
                                    "15m" to "15分钟",
                                    "30m" to "30分钟",
                                    "1h" to "1小时",
                                    "2h" to "2小时",
                                    "6h" to "6小时"
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
                            Text("支持各类对象存储服务、Cloudflare R2、AWS S3 等", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val creds = remember { org.classflow.data.local.SettingsManager.loadSyncCredentials(context) }
    var serverUrl by remember { mutableStateOf(creds.webdavUrl) }
    var username by remember { mutableStateOf(creds.webdavUsername) }
    var password by remember { mutableStateOf(creds.webdavPassword) }
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
                    onValueChange = { serverUrl = it; testResult = null },
                    label = { Text("服务器地址 (URL)") },
                    placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; testResult = null },
                    label = { Text("账号") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; testResult = null },
                    label = { Text("密码 / 应用授权码") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                testResult?.let { msg ->
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
                            testResult = "正在测试连接..."
                            val provider = org.classflow.sync.WebDAVStorageProvider(serverUrl, username, password)
                            val res = provider.testConnection()
                            isTesting = false
                            if (res.isSuccess) {
                                testResult = "✓ 连接成功！认证有效"
                            } else {
                                testResult = "连接失败: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    enabled = !isTesting && username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isTesting) "正在测试..." else "测试连接")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = creds.copy(
                        providerType = "webdav",
                        webdavUrl = serverUrl,
                        webdavUsername = username,
                        webdavPassword = password
                    )
                    org.classflow.data.local.SettingsManager.saveSyncCredentials(context, updated)
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val creds = remember { org.classflow.data.local.SettingsManager.loadSyncCredentials(context) }
    var endpoint by remember { mutableStateOf(creds.s3Endpoint) }
    var region by remember { mutableStateOf(creds.s3Region) }
    var bucket by remember { mutableStateOf(creds.s3Bucket) }
    var accessKey by remember { mutableStateOf(creds.s3AccessKey) }
    var secretKey by remember { mutableStateOf(creds.s3SecretKey) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("配置 S3 兼容对象存储同步", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it; testResult = null },
                    label = { Text("Endpoint 地址") },
                    placeholder = { Text("https://s3.cn-east-1.qiniucs.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it; testResult = null },
                    label = { Text("Region (区域)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bucket,
                    onValueChange = { bucket = it; testResult = null },
                    label = { Text("Bucket 存储桶名") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessKey,
                    onValueChange = { accessKey = it; testResult = null },
                    label = { Text("Access Key (AK)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it; testResult = null },
                    label = { Text("Secret Key (SK)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                testResult?.let { msg ->
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
                            testResult = "正在测试连接..."
                            val provider = org.classflow.sync.S3StorageProvider(endpoint, region, bucket, accessKey, secretKey)
                            val res = provider.testConnection()
                            isTesting = false
                            if (res.isSuccess) {
                                testResult = "✓ 连接成功！存储桶可正常读写"
                            } else {
                                testResult = "连接失败: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    enabled = !isTesting && bucket.isNotBlank() && accessKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isTesting) "正在测试..." else "测试连接")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = creds.copy(
                        providerType = "s3",
                        s3Endpoint = endpoint,
                        s3Region = region,
                        s3Bucket = bucket,
                        s3AccessKey = accessKey,
                        s3SecretKey = secretKey
                    )
                    org.classflow.data.local.SettingsManager.saveSyncCredentials(context, updated)
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

fun formatWeeksSummary(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val sorted = weeks.distinct().sorted()
    val ranges = mutableListOf<String>()
    var start = sorted[0]
    var prev = sorted[0]
    for (i in 1 until sorted.size) {
        val curr = sorted[i]
        if (curr == prev + 1) {
            prev = curr
        } else {
            ranges.add(if (start == prev) "${start}周" else "$start-${prev}周")
            start = curr
            prev = curr
        }
    }
    ranges.add(if (start == prev) "${start}周" else "$start-${prev}周")
    return ranges.joinToString(", ")
}

/**
 * Course List Dialog allowing course deletion, inspection and editing.
 */
@Composable
fun UnifiedItemManagerDialog(
    courses: List<Course>,
    schedules: List<CalendarEvent> = emptyList(),
    tasks: List<org.classflow.data.model.StudyTask> = emptyList(),
    onDeleteCourse: (Course) -> Unit = {},
    onDeleteSchedule: (CalendarEvent, org.classflow.data.model.EditingScope) -> Unit = { _, _ -> },
    onDeleteTask: (org.classflow.data.model.StudyTask) -> Unit = {},
    onEditCourse: (Course) -> Unit = {},
    onEditSchedule: (CalendarEvent) -> Unit = {},
    onEditTask: (org.classflow.data.model.StudyTask) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var scheduleToDelete by remember { mutableStateOf<CalendarEvent?>(null) }
    var showDeleteScheduleConfirm by remember { mutableStateOf(false) }

    val query = searchQuery.trim().lowercase()

    val filteredCourses = remember(courses, query) {
        if (query.isEmpty()) courses
        else courses.filter {
            it.name.lowercase().contains(query) ||
            (it.teacher?.lowercase()?.contains(query) == true) ||
            (it.notes?.lowercase()?.contains(query) == true)
        }
    }

    val filteredSchedules = remember(schedules, query) {
        if (query.isEmpty()) schedules
        else schedules.filter {
            it.title.lowercase().contains(query) ||
            (it.location?.lowercase()?.contains(query) == true) ||
            (it.description?.lowercase()?.contains(query) == true)
        }
    }

    val filteredTasks = remember(tasks, query) {
        if (query.isEmpty()) tasks
        else tasks.filter {
            it.title.lowercase().contains(query) ||
            (it.courseName?.lowercase()?.contains(query) == true) ||
            (it.notes?.lowercase()?.contains(query) == true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("事项管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索课程、日程或待办事项...", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Segmented Tabs: [课程 | 日程 | 待办]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        0 to "课程 (${filteredCourses.size})",
                        1 to "日程 (${filteredSchedules.size})",
                        2 to "待办 (${filteredTasks.size})"
                    )
                    tabs.forEach { (idx, label) ->
                        val isSelected = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Content List
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> {
                            if (filteredCourses.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("未找到相关课程", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.5.sp)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredCourses.forEach { course ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = parseHexColor(course.color).copy(alpha = 0.12f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(parseHexColor(course.color))
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(course.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                                    val teacherText = course.teacher ?: "暂无任课教师"
                                                    val creditBadge = if (course.credit != null && course.credit > 0) " · ${if (course.credit % 1.0 == 0.0) course.credit.toInt() else course.credit}学分" else ""
                                                    val schedCount = "${course.schedules.size} 个上课时段"
                                                    Text("$teacherText$creditBadge · $schedCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    if (course.schedules.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        course.schedules.forEach { s ->
                                                            val dayName = when (s.dayOfWeek) {
                                                                1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; 7 -> "周日"
                                                                else -> "周${s.dayOfWeek}"
                                                            }
                                                            val weekText = if (s.customWeeks.isNotEmpty()) {
                                                                formatWeeksSummary(s.customWeeks)
                                                            } else {
                                                                "${s.startWeek}-${s.endWeek}周"
                                                            }
                                                            val room = if (!s.classroom.isNullOrBlank()) " · @${s.classroom}" else ""
                                                            Text(
                                                                text = "$dayName 第${s.startSection}-${s.endSection}节 ($weekText)$room",
                                                                fontSize = 11.5.sp,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { onEditCourse(course) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "编辑课程", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = { onDeleteCourse(course) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "删除课程", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (filteredSchedules.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("未找到相关日程", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.5.sp)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredSchedules.forEach { event ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = parseHexColor(event.color).copy(alpha = 0.12f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(parseHexColor(event.color))
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(event.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                                    val timeInfo = "${event.startTime} · ${event.frequency ?: "不重复"}"
                                                    val loc = if (!event.location.isNullOrBlank()) " · ${event.location}" else ""
                                                    val repeatEnd = if (!event.endRepeatUntil.isNullOrBlank()) " (至 ${event.endRepeatUntil} 截止)"
                                                                    else if (!event.endDate.isNullOrBlank() && event.frequency != "none" && event.frequency != "不重复") " (至 ${event.endDate} 截止)"
                                                                    else ""
                                                    val weeksInfo = if (event.customWeeks.isNotEmpty()) " · ${formatWeeksSummary(event.customWeeks)}" else ""
                                                    Text("$timeInfo$repeatEnd$weeksInfo$loc", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(
                                                    onClick = { onEditSchedule(event) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "编辑日程", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        scheduleToDelete = event
                                                        showDeleteScheduleConfirm = true
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "删除日程", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (filteredTasks.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("未找到相关待办", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.5.sp)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filteredTasks.forEach { task ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (task.isCompleted) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(task.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                                    val course = if (!task.courseName.isNullOrBlank()) "${task.courseName} · " else ""
                                                    val due = if (!task.dueDate.isNullOrBlank()) "截止: ${task.dueDate} · " else ""
                                                    val pomodoros = "${task.targetMinutes}分钟"
                                                    Text("$course$due$pomodoros", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(
                                                    onClick = { onEditTask(task) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "编辑待办", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = { onDeleteTask(task) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "删除待办", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
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

    if (showDeleteScheduleConfirm && scheduleToDelete != null) {
        val ev = scheduleToDelete!!
        val isRecurring = ev.frequency != "none" && ev.frequency != "不重复"
        if (isRecurring) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteScheduleConfirm = false
                    scheduleToDelete = null
                },
                title = { Text("删除重复日程", fontWeight = FontWeight.Bold) },
                text = { Text("「${ev.title}」是循环重复日程，请选择删除范围：") },
                confirmButton = {},
                dismissButton = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                onDeleteSchedule(ev, org.classflow.data.model.EditingScope.THIS_ONLY)
                                showDeleteScheduleConfirm = false
                                scheduleToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("仅删除本次日程")
                        }
                        OutlinedButton(
                            onClick = {
                                onDeleteSchedule(ev, org.classflow.data.model.EditingScope.THIS_AND_FUTURE)
                                showDeleteScheduleConfirm = false
                                scheduleToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("删除此日程及以后的所有重复")
                        }
                        Button(
                            onClick = {
                                onDeleteSchedule(ev, org.classflow.data.model.EditingScope.ALL)
                                showDeleteScheduleConfirm = false
                                scheduleToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("删除所有重复日程", color = Color.White)
                        }
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = {
                    showDeleteScheduleConfirm = false
                    scheduleToDelete = null
                },
                title = { Text("删除日程", fontWeight = FontWeight.Bold) },
                text = { Text("确定要删除日程「${ev.title}」吗？") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteSchedule(ev, org.classflow.data.model.EditingScope.ALL)
                            showDeleteScheduleConfirm = false
                            scheduleToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F))
                    ) {
                        Text("删除", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteScheduleConfirm = false
                        scheduleToDelete = null
                    }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
fun CourseListDialog(
    courses: List<Course>,
    schedules: List<CalendarEvent> = emptyList(),
    tasks: List<org.classflow.data.model.StudyTask> = emptyList(),
    onDeleteCourse: (Course) -> Unit = {},
    onDeleteSchedule: (CalendarEvent) -> Unit = {},
    onEditCourse: (Course) -> Unit = {},
    onEditSchedule: (CalendarEvent) -> Unit = {},
    onEditTask: (org.classflow.data.model.StudyTask) -> Unit = {},
    onDeleteTask: (org.classflow.data.model.StudyTask) -> Unit = {},
    onDismiss: () -> Unit
) {
    UnifiedItemManagerDialog(
        courses = courses,
        schedules = schedules,
        tasks = tasks,
        onDeleteCourse = onDeleteCourse,
        onDeleteSchedule = { event, _ -> onDeleteSchedule(event) },
        onDeleteTask = onDeleteTask,
        onEditCourse = onEditCourse,
        onEditSchedule = onEditSchedule,
        onEditTask = onEditTask,
        onDismiss = onDismiss
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
