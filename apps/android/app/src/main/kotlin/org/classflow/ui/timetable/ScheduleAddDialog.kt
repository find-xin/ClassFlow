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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.classflow.data.model.CalendarEvent
import org.classflow.data.model.Course
import org.classflow.data.model.SectionTime
import org.classflow.ui.components.GoogleCalendarColorPicker
import org.classflow.ui.components.WeekMatrixPicker
import org.classflow.ui.components.parseHexColor
import java.text.SimpleDateFormat
import java.util.*

enum class ScheduleItemMode {
    EVENT,
    TASK
}

/**
 * 1:1 Parity of macOS ScheduleItemCreateSheet & macOS Native Calendar:
 * - Top bar: 取消 | [日程 | 待办] segmented pill | 添加
 * - Comprehensive Date & Time: 开始日期 + 结束日期, 全天日程, [按作息节次 | 按具体时间]
 * - macOS Calendar Advanced Recurrence (Image 4 & 5):
 *   - 重复频次: 不重复, 每天, 工作日 (周一至周五), 自定义每周某几天, 每周, 每两周, 每月, 学期指定周次, 自定义重复...
 *   - 每 N 周, [日 一 二 三 四 五 六] 星期几多选
 *   - 生效周次矩阵 1..25 自由组合 (覆盖周几与周次联合过滤)
 *   - 结束重复: 永不 / 于指定日期结束 / 达到指定次数
 * - Reminder, Location, Notes, and Task Course Linkage
 */
@Composable
fun ScheduleAddDialog(
    initialDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    initialDayOfWeek: Int = 1,
    initialSection: Int = 1,
    availableSections: List<SectionTime> = emptyList(),
    courses: List<Course> = emptyList(),
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onSaveSchedule: (CalendarEvent) -> Unit
) {
    var mode by remember { mutableStateOf(ScheduleItemMode.EVENT) }

    // MARK: - Event States
    var eventTitle by remember { mutableStateOf("") }
    var startDateStr by remember { mutableStateOf(initialDate) }
    var endDateStr by remember { mutableStateOf(initialDate) }
    var isAllDay by remember { mutableStateOf(false) }
    var timeMode by remember { mutableStateOf("bySection") } // "bySection" or "byTime"
    var startSection by remember { mutableIntStateOf(initialSection) }
    var endSection by remember { mutableIntStateOf(maxOf(initialSection, initialSection + 1)) }
    var startTimeStr by remember { mutableStateOf("09:00") }
    var endTimeStr by remember { mutableStateOf("10:30") }

    // Recurrence states
    var frequency by remember { mutableStateOf("不重复") }
    var intervalWeeks by remember { mutableIntStateOf(1) }
    var customDaysOfWeek by remember { mutableStateOf(setOf(initialDayOfWeek)) }
    var customWeeks by remember { mutableStateOf((1..16).toSet()) }
    var endRepeatType by remember { mutableStateOf("never") } // "never", "until_date", "count"
    var endRepeatUntil by remember { mutableStateOf(initialDate) }
    var endRepeatCount by remember { mutableIntStateOf(10) }

    var alertOption by remember { mutableStateOf("提前 15 分钟") }
    var eventColor by remember { mutableStateOf("#FF5A5F") }
    var location by remember { mutableStateOf("") }
    var eventNotes by remember { mutableStateOf("") }

    // MARK: - Task States
    var taskTitle by remember { mutableStateOf("") }
    var selectedCourseId by remember { mutableStateOf<String?>(null) }
    var taskPriority by remember { mutableStateOf("medium") } // "low", "medium", "high"
    var hasDueDate by remember { mutableStateOf(true) }
    var taskDueDate by remember { mutableStateOf(initialDate) }
    var targetMinutes by remember { mutableIntStateOf(45) }
    var taskNotes by remember { mutableStateOf("") }

    val effectiveSections = remember(availableSections) {
        if (availableSections.isNotEmpty()) availableSections
        else (1..12).map { s ->
            SectionTime(
                section = s,
                startTime = String.format("%02d:00", 7 + s),
                endTime = String.format("%02d:45", 7 + s)
            )
        }
    }

    val frequencies = listOf(
        "不重复",
        "每天",
        "工作日 (周一至周五)",
        "自定义每周某几天",
        "每周",
        "每两周",
        "每月",
        "学期指定周次"
    )

    val alertOptions = listOf(
        "无提醒",
        "日程开始时",
        "提前 5 分钟",
        "提前 15 分钟",
        "提前 30 分钟",
        "提前 1 小时",
        "提前 1 天"
    )

    val bg = if (isDark) Color(0xFF14151B) else Color(0xFFF7F8FA)
    val cardBg = if (isDark) Color(0xFF1F212A) else Color.White
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFFA1A3AC) else Color(0xFF6B7280)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE5E7EB)
    val accentRed = Color(0xFFFF5A5F)

    val canSave = if (mode == ScheduleItemMode.EVENT) {
        eventTitle.trim().isNotBlank()
    } else {
        taskTitle.trim().isNotBlank()
    }

    // Dropdown toggles
    var showFreqMenu by remember { mutableStateOf(false) }
    var showAlertMenu by remember { mutableStateOf(false) }
    var showStartSecMenu by remember { mutableStateOf(false) }
    var showEndSecMenu by remember { mutableStateOf(false) }
    var showCourseMenu by remember { mutableStateOf(false) }
    var showEndRepeatMenu by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(bg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Navigation Header Bar with [日程 | 待办] segmented pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", fontSize = 15.sp, color = textSecondary)
                    }

                    // Mode Segmented Pill [日程 | 待办]
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF282B3C) else Color(0xFFE5E7EB))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (mode == ScheduleItemMode.EVENT) (if (isDark) Color(0xFF383C52) else Color.White) else Color.Transparent)
                            .clickable { mode = ScheduleItemMode.EVENT }
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "日程",
                                fontSize = 13.sp,
                                fontWeight = if (mode == ScheduleItemMode.EVENT) FontWeight.Bold else FontWeight.Normal,
                                color = textPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (mode == ScheduleItemMode.TASK) (if (isDark) Color(0xFF383C52) else Color.White) else Color.Transparent)
                                .clickable { mode = ScheduleItemMode.TASK }
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "待办",
                                fontSize = 13.sp,
                                fontWeight = if (mode == ScheduleItemMode.TASK) FontWeight.Bold else FontWeight.Normal,
                                color = textPrimary
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            if (canSave) {
                                if (mode == ScheduleItemMode.EVENT) {
                                    val resolvedStartSec = if (isAllDay) 1 else startSection
                                    val resolvedEndSec = if (isAllDay) effectiveSections.size else endSection
                                    val resolvedStartT = if (isAllDay) "00:00" else if (timeMode == "bySection") {
                                        effectiveSections.find { it.section == startSection }?.startTime ?: "08:00"
                                    } else startTimeStr
                                    val resolvedEndT = if (isAllDay) "23:59" else if (timeMode == "bySection") {
                                        effectiveSections.find { it.section == endSection }?.endTime ?: "09:35"
                                    } else endTimeStr

                                    val alertMins = when (alertOption) {
                                        "日程开始时" -> 0
                                        "提前 5 分钟" -> 5
                                        "提前 15 分钟" -> 15
                                        "提前 30 分钟" -> 30
                                        "提前 1 小时" -> 60
                                        "提前 1 天" -> 1440
                                        else -> null
                                    }

                                    val event = CalendarEvent(
                                        title = eventTitle.trim(),
                                        startTime = "$startDateStr $resolvedStartT",
                                        endTime = "$endDateStr $resolvedEndT",
                                        startDate = startDateStr,
                                        endDate = endDateStr,
                                        isAllDay = isAllDay,
                                        location = location.trim().ifEmpty { null },
                                        description = eventNotes.trim().ifEmpty { null },
                                        color = eventColor,
                                        startSection = resolvedStartSec,
                                        endSection = resolvedEndSec,
                                        frequency = frequency,
                                        intervalWeeks = intervalWeeks,
                                        customDaysOfWeek = customDaysOfWeek.toList(),
                                        customWeeks = customWeeks.toList(),
                                        endRepeatType = endRepeatType,
                                        endRepeatUntil = endRepeatUntil,
                                        endRepeatCount = endRepeatCount,
                                        alertMinutes = alertMins,
                                        isTask = false
                                    )
                                    onSaveSchedule(event)
                                } else {
                                    val matchedCourse = courses.find { it.id == selectedCourseId }
                                    val taskEvent = CalendarEvent(
                                        title = taskTitle.trim(),
                                        startTime = "$taskDueDate 09:00",
                                        endTime = "$taskDueDate 10:00",
                                        startDate = taskDueDate,
                                        endDate = taskDueDate,
                                        isAllDay = false,
                                        color = "#3F51B5",
                                        isTask = true,
                                        isCompleted = false,
                                        courseId = selectedCourseId,
                                        courseName = matchedCourse?.name,
                                        priority = taskPriority,
                                        dueDate = if (hasDueDate) "$taskDueDate 23:59" else null,
                                        targetMinutes = targetMinutes,
                                        description = taskNotes.trim().ifEmpty { null }
                                    )
                                    onSaveSchedule(taskEvent)
                                }
                            }
                        },
                        enabled = canSave
                    ) {
                        Text(
                            text = "添加",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canSave) accentRed else textSecondary.copy(alpha = 0.4f)
                        )
                    }
                }

                HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

                // 2. Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (mode == ScheduleItemMode.EVENT) {
                        // ==================== EVENT MODE ====================
                        // Card 1: 标题与标记颜色
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = eventTitle,
                                    onValueChange = { eventTitle = it },
                                    placeholder = { Text("日程标题 (如：学院例会 / 期中考试)", fontSize = 14.sp, color = textSecondary.copy(alpha = 0.7f)) },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(eventColor))
                                )
                            }

                            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("标记颜色", fontSize = 13.sp, color = textSecondary)
                                GoogleCalendarColorPicker(
                                    selectedHex = eventColor,
                                    onColorSelected = { eventColor = it }
                                )
                            }
                        }

                        // Card 2: 日期与时间 (覆盖完整开始日期与结束日期)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                        ) {
                            // 开始日期 + 全天日程复选框
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("开始日期", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                    OutlinedTextField(
                                        value = startDateStr,
                                        onValueChange = { startDateStr = it },
                                        placeholder = { Text("2026-10-16", fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.width(120.dp)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isAllDay,
                                        onCheckedChange = { isAllDay = it },
                                        colors = CheckboxDefaults.colors(checkedColor = accentRed)
                                    )
                                    Text("全天日程", fontSize = 13.sp, color = textPrimary)
                                }
                            }

                            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                            // 结束日期
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("结束日期", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                OutlinedTextField(
                                    value = endDateStr,
                                    onValueChange = { endDateStr = it },
                                    placeholder = { Text("2026-10-16", fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(120.dp)
                                )
                            }

                            if (!isAllDay) {
                                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                                // 时间模式: 按作息节次 vs 按具体时间
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("时间模式", fontSize = 13.sp, color = textSecondary)

                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6))
                                            .padding(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (timeMode == "bySection") (if (isDark) Color(0xFF383C52) else Color.White) else Color.Transparent)
                                                .clickable { timeMode = "bySection" }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "按作息节次",
                                                fontSize = 12.sp,
                                                fontWeight = if (timeMode == "bySection") FontWeight.Bold else FontWeight.Normal,
                                                color = textPrimary
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (timeMode == "byTime") (if (isDark) Color(0xFF383C52) else Color.White) else Color.Transparent)
                                                .clickable { timeMode = "byTime" }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "按具体时间",
                                                fontSize = 12.sp,
                                                fontWeight = if (timeMode == "byTime") FontWeight.Bold else FontWeight.Normal,
                                                color = textPrimary
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                                // 节次范围 or 具体时间
                                if (timeMode == "bySection") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("节次范围", fontSize = 13.sp, color = textPrimary)
                                        Spacer(modifier = Modifier.weight(1f))

                                        // Start Section
                                        Box {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                                                modifier = Modifier.clickable { showStartSecMenu = true }
                                            ) {
                                                val curSec = effectiveSections.find { it.section == startSection }
                                                Text("第 $startSection 节 (${curSec?.startTime ?: "08:00"}) ▾", fontSize = 12.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                            }
                                            DropdownMenu(expanded = showStartSecMenu, onDismissRequest = { showStartSecMenu = false }) {
                                                effectiveSections.forEach { s ->
                                                    DropdownMenuItem(
                                                        text = { Text("第 ${s.section} 节 (${s.startTime})", fontSize = 12.sp) },
                                                        onClick = {
                                                            showStartSecMenu = false
                                                            startSection = s.section
                                                            endSection = maxOf(s.section, endSection)
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Text("至", fontSize = 12.sp, color = textSecondary)

                                        // End Section
                                        Box {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                                                modifier = Modifier.clickable { showEndSecMenu = true }
                                            ) {
                                                val curSec = effectiveSections.find { it.section == endSection }
                                                Text("第 $endSection 节 (${curSec?.endTime ?: "09:35"}) ▾", fontSize = 12.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                            }
                                            DropdownMenu(expanded = showEndSecMenu, onDismissRequest = { showEndSecMenu = false }) {
                                                effectiveSections.filter { it.section >= startSection }.forEach { s ->
                                                    DropdownMenuItem(
                                                        text = { Text("第 ${s.section} 节 (${s.endTime})", fontSize = 12.sp) },
                                                        onClick = {
                                                            showEndSecMenu = false
                                                            endSection = s.section
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("具体时间", fontSize = 13.sp, color = textPrimary)
                                        Spacer(modifier = Modifier.weight(1f))
                                        OutlinedTextField(
                                            value = startTimeStr,
                                            onValueChange = { startTimeStr = it },
                                            placeholder = { Text("09:00", fontSize = 12.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.width(82.dp)
                                        )
                                        Text("至", fontSize = 12.sp, color = textSecondary)
                                        OutlinedTextField(
                                            value = endTimeStr,
                                            onValueChange = { endTimeStr = it },
                                            placeholder = { Text("10:30", fontSize = 12.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.width(82.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Card 3: 重复规则与高级自定义 (macOS 日历高级重复)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                        ) {
                            // 重复频次下拉选择
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("重复频次", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)

                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                                        modifier = Modifier.clickable { showFreqMenu = true }
                                    ) {
                                        Text("$frequency ▾", fontSize = 13.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                    DropdownMenu(expanded = showFreqMenu, onDismissRequest = { showFreqMenu = false }) {
                                        frequencies.forEach { freq ->
                                            DropdownMenuItem(
                                                text = { Text(freq, fontSize = 13.sp) },
                                                onClick = {
                                                    frequency = freq
                                                    showFreqMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // If 自定义每周某几天 or 每周 (macOS Calendar Image 5 style)
                            if (frequency == "自定义每周某几天" || frequency == "每周" || frequency == "每两周") {
                                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // 每 [ 1 ] 周:
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("每", fontSize = 13.sp, color = textPrimary)
                                        OutlinedTextField(
                                            value = intervalWeeks.toString(),
                                            onValueChange = {
                                                intervalWeeks = it.filter { ch -> ch.isDigit() }.toIntOrNull()?.coerceIn(1, 10) ?: 1
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.width(55.dp)
                                        )
                                        Text("周:", fontSize = 13.sp, color = textPrimary)
                                    }

                                    // Weekday Selection Row [日 一 二 三 四 五 六]
                                    val weekDays = listOf(
                                        7 to "日", 1 to "一", 2 to "二", 3 to "三",
                                        4 to "四", 5 to "五", 6 to "六"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(0.8.dp, dividerColor, RoundedCornerShape(8.dp)),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        weekDays.forEach { (dVal, dName) ->
                                            val isSel = customDaysOfWeek.contains(dVal)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(if (isSel) (if (isDark) Color(0xFF3B4058) else Color(0xFFD1D5DB)) else Color.Transparent)
                                                    .clickable {
                                                        val next = customDaysOfWeek.toMutableSet()
                                                        if (isSel) {
                                                            if (next.size > 1) next.remove(dVal)
                                                        } else {
                                                            next.add(dVal)
                                                        }
                                                        customDaysOfWeek = next
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dName,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    color = textPrimary
                                                )
                                            }
                                        }
                                    }

                                    // 学期生效周次 (进一步自定义周次，彻底补足漏洞)
                                    Text("学期生效周次", fontSize = 12.sp, color = textSecondary)
                                    WeekMatrixPicker(
                                        selectedWeeks = customWeeks,
                                        onWeeksChange = { customWeeks = it },
                                        maxWeeks = 25,
                                        accentColor = parseHexColor(eventColor),
                                        isDark = isDark
                                    )

                                    // 结束重复设置
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("结束重复", fontSize = 12.sp, color = textSecondary)
                                        Box {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                                                modifier = Modifier.clickable { showEndRepeatMenu = true }
                                            ) {
                                                val label = when (endRepeatType) {
                                                    "until_date" -> "于指定日期终止"
                                                    "count" -> "达到指定次数终止"
                                                    else -> "永不终止"
                                                }
                                                Text("$label ▾", fontSize = 12.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                            DropdownMenu(expanded = showEndRepeatMenu, onDismissRequest = { showEndRepeatMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("永不终止", fontSize = 12.5.sp) },
                                                    onClick = {
                                                        endRepeatType = "never"
                                                        showEndRepeatMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("于指定日期终止", fontSize = 12.5.sp) },
                                                    onClick = {
                                                        endRepeatType = "until_date"
                                                        showEndRepeatMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("达到指定次数终止", fontSize = 12.5.sp) },
                                                    onClick = {
                                                        endRepeatType = "count"
                                                        showEndRepeatMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (endRepeatType == "until_date") {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("截止日期:", fontSize = 12.sp, color = textSecondary)
                                            OutlinedTextField(
                                                value = endRepeatUntil,
                                                onValueChange = { endRepeatUntil = it },
                                                placeholder = { Text("2026-12-31", fontSize = 12.sp) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.width(120.dp)
                                            )
                                        }
                                    } else if (endRepeatType == "count") {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("重复发生:", fontSize = 12.sp, color = textSecondary)
                                            OutlinedTextField(
                                                value = endRepeatCount.toString(),
                                                onValueChange = { endRepeatCount = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 10 },
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.width(60.dp)
                                            )
                                            Text("次后停止", fontSize = 12.sp, color = textSecondary)
                                        }
                                    }
                                }
                            }

                            // If 学期指定周次
                            if (frequency == "学期指定周次") {
                                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("选择活动周次", fontSize = 12.sp, color = textSecondary)
                                    WeekMatrixPicker(
                                        selectedWeeks = customWeeks,
                                        onWeeksChange = { customWeeks = it },
                                        maxWeeks = 25,
                                        accentColor = parseHexColor(eventColor),
                                        isDark = isDark
                                    )
                                }
                            }

                            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                            // 提醒通知
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("提醒通知", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                                        modifier = Modifier.clickable { showAlertMenu = true }
                                    ) {
                                        Text("$alertOption ▾", fontSize = 13.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                    DropdownMenu(expanded = showAlertMenu, onDismissRequest = { showAlertMenu = false }) {
                                        alertOptions.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt, fontSize = 13.sp) },
                                                onClick = {
                                                    alertOption = opt
                                                    showAlertMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card 4: 地点与备注
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = textSecondary, modifier = Modifier.size(17.dp))
                                TextField(
                                    value = location,
                                    onValueChange = { location = it },
                                    placeholder = { Text("地点 / 线上会议房间号 (选填)", fontSize = 13.5.sp, color = textSecondary.copy(alpha = 0.7f)) },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Notes, contentDescription = null, tint = textSecondary, modifier = Modifier.size(17.dp))
                                TextField(
                                    value = eventNotes,
                                    onValueChange = { eventNotes = it },
                                    placeholder = { Text("备注说明 (选填)", fontSize = 13.5.sp, color = textSecondary.copy(alpha = 0.7f)) },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        // ==================== TASK MODE ====================
                        // Card 1: 待办标题
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            TextField(
                                value = taskTitle,
                                onValueChange = { taskTitle = it },
                                placeholder = { Text("待办事项名称 (如：完成编译原理大作业)", fontSize = 14.sp, color = textSecondary.copy(alpha = 0.7f)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Card 2: 关联课程与优先级
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                        ) {
                            // 关联课程
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("关联课程", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                                        modifier = Modifier.clickable { showCourseMenu = true }
                                    ) {
                                        val selName = courses.find { it.id == selectedCourseId }?.name ?: "无关联课程"
                                        Text("$selName ▾", fontSize = 13.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                    DropdownMenu(expanded = showCourseMenu, onDismissRequest = { showCourseMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("无关联课程", fontSize = 13.sp) },
                                            onClick = {
                                                selectedCourseId = null
                                                showCourseMenu = false
                                            }
                                        )
                                        courses.forEach { c ->
                                            DropdownMenuItem(
                                                text = { Text(c.name, fontSize = 13.sp) },
                                                onClick = {
                                                    selectedCourseId = c.id
                                                    showCourseMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                            // 优先级
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("优先级", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6))
                                        .padding(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("low" to "普通", "medium" to "重要", "high" to "紧急").forEach { (priCode, priLabel) ->
                                        val isSel = (taskPriority == priCode)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) (if (isDark) Color(0xFF383C52) else Color.White) else Color.Transparent)
                                                .clickable { taskPriority = priCode }
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = priLabel,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel && priCode == "high") Color.Red else textPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                            // 截止时间
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = hasDueDate,
                                        onCheckedChange = { hasDueDate = it },
                                        colors = CheckboxDefaults.colors(checkedColor = accentRed)
                                    )
                                    Text("设置截止时间", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                }

                                if (hasDueDate) {
                                    OutlinedTextField(
                                        value = taskDueDate,
                                        onValueChange = { taskDueDate = it },
                                        placeholder = { Text("2026-10-16", fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.width(120.dp)
                                    )
                                }
                            }
                        }

                        // Card 3: 预计专注时长
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("预计专注时长", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = targetMinutes.toString(),
                                        onValueChange = { targetMinutes = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 45 },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.width(55.dp)
                                    )
                                    Text("分钟", fontSize = 12.5.sp, color = textSecondary)
                                }
                            }

                            // Quick minutes pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(15, 30, 45, 60, 90, 120).forEach { mins ->
                                    val isSel = (targetMinutes == mins)
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSel) textPrimary else (if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6)))
                                            .clickable { targetMinutes = mins }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${mins}m",
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) (if (isDark) Color.Black else Color.White) else textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // Card 4: 待办备忘
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            TextField(
                                value = taskNotes,
                                onValueChange = { taskNotes = it },
                                placeholder = { Text("待办详情与备忘说明 (选填)", fontSize = 13.5.sp, color = textSecondary.copy(alpha = 0.7f)) },
                                singleLine = false,
                                maxLines = 3,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
