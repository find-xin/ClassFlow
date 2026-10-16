package org.classflow.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import org.classflow.ui.components.showDatePicker
import org.classflow.ui.components.showDateTimePicker
import org.classflow.ui.components.showTimePicker
import java.text.SimpleDateFormat
import java.util.*

enum class ScheduleItemMode {
    EVENT,
    TASK
}

/**
 * Unified Schedule & Task Creation Dialog with macOS-grade modal aesthetics:
 * - Surface(RoundedCornerShape(20.dp), heightIn(max = 680.dp))
 * - Inset rounded cards (RoundedCornerShape(12.dp), surfaceVariant)
 * - BasicTextField with placeholder (no thick box borders)
 * - Clickable date & time pills with native DatePickerDialog / TimePickerDialog
 * - Complete macOS Calendar Recurrence & Task Parity
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
    val context = LocalContext.current
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
    var taskDueDateStr by remember { mutableStateOf("$initialDate 18:00") }
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
        "每周",
        "每月",
        "自定义每周某几天"
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
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Navigation Header Bar matching macOS: [ 取消 | (日程/待办) | 添加 ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    // Mode Segmented Capsule [日程 | 待办]
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .padding(2.5.dp)
                    ) {
                        listOf(ScheduleItemMode.EVENT to "日程", ScheduleItemMode.TASK to "待办").forEach { (m, label) ->
                            val isSelected = (mode == m)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .clickable { mode = m }
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = "添加",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = canSave) {
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
                                        intervalWeeks = 1,
                                        customDaysOfWeek = if (frequency == "自定义每周某几天") customDaysOfWeek.toList() else emptyList(),
                                        customWeeks = if (frequency == "自定义每周某几天") customWeeks.toList() else emptyList(),
                                        endRepeatType = endRepeatType,
                                        endRepeatUntil = endRepeatUntil,
                                        endRepeatCount = endRepeatCount,
                                        alertMinutes = alertMins,
                                        isTask = false
                                    )
                                    onSaveSchedule(event)
                                } else {
                                    val matchedCourse = courses.find { it.id == selectedCourseId }
                                    val dueDate = if (hasDueDate) taskDueDateStr else null
                                    val taskEvent = CalendarEvent(
                                        title = taskTitle.trim(),
                                        startTime = if (hasDueDate) taskDueDateStr else "$initialDate 09:00",
                                        endTime = if (hasDueDate) taskDueDateStr else "$initialDate 10:00",
                                        startDate = if (hasDueDate) taskDueDateStr.substringBefore(" ") else initialDate,
                                        endDate = if (hasDueDate) taskDueDateStr.substringBefore(" ") else initialDate,
                                        isAllDay = false,
                                        color = "#3F51B5",
                                        isTask = true,
                                        isCompleted = false,
                                        courseId = selectedCourseId,
                                        courseName = matchedCourse?.name,
                                        priority = taskPriority,
                                        dueDate = dueDate,
                                        targetMinutes = targetMinutes,
                                        description = taskNotes.trim().ifEmpty { null }
                                    )
                                    onSaveSchedule(taskEvent)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 2. Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (mode == ScheduleItemMode.EVENT) {
                        // ==================== EVENT MODE ====================
                        // Card 1: Title and Color
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = eventTitle,
                                        onValueChange = { eventTitle = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (eventTitle.isEmpty()) {
                                                Text(
                                                    text = "日程标题 (如：学院例会 / 期中考试)",
                                                    fontSize = 14.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(eventColor))
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "标记颜色",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    GoogleCalendarColorPicker(
                                        selectedHex = eventColor,
                                        onColorSelected = { eventColor = it }
                                    )
                                }
                            }
                        }

                        // Card 2: Date & Time
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Start date
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "开始日期",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            showDatePicker(context, startDateStr) { picked ->
                                                startDateStr = picked
                                                if (endDateStr < picked) endDateStr = picked
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = startDateStr,
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // All-day switch
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "全天日程",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Switch(
                                        checked = isAllDay,
                                        onCheckedChange = { isAllDay = it }
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // End date
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "结束日期",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            showDatePicker(context, endDateStr) { picked ->
                                                endDateStr = picked
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = endDateStr,
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                if (!isAllDay) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                    // Time Mode: bySection vs byTime
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "时间模式",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Row(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    CircleShape
                                                )
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CircleShape)
                                                .padding(2.5.dp)
                                        ) {
                                            listOf("bySection" to "按作息节次", "byTime" to "按具体时间").forEach { (tKey, tLabel) ->
                                                val isSelected = (timeMode == tKey)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                        .clickable { timeMode = tKey }
                                                        .padding(horizontal = 10.dp, vertical = 3.5.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = tLabel,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                    if (timeMode == "bySection") {
                                        // Section range
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "节次范围",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                        modifier = Modifier.clickable { showStartSecMenu = true }
                                                    ) {
                                                        Text("第 $startSection 节 ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                    }
                                                    DropdownMenu(expanded = showStartSecMenu, onDismissRequest = { showStartSecMenu = false }) {
                                                        effectiveSections.forEach { sec ->
                                                            DropdownMenuItem(
                                                                text = { Text("第 ${sec.section} 节 (${sec.startTime})", fontSize = 12.5.sp) },
                                                                onClick = {
                                                                    startSection = sec.section
                                                                    if (endSection < sec.section) endSection = sec.section
                                                                    showStartSecMenu = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                Text("至", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                                Box {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                        modifier = Modifier.clickable { showEndSecMenu = true }
                                                    ) {
                                                        Text("第 $endSection 节 ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                    }
                                                    DropdownMenu(expanded = showEndSecMenu, onDismissRequest = { showEndSecMenu = false }) {
                                                        effectiveSections.filter { it.section >= startSection }.forEach { sec ->
                                                            DropdownMenuItem(
                                                                text = { Text("第 ${sec.section} 节 (${sec.endTime})", fontSize = 12.5.sp) },
                                                                onClick = {
                                                                    endSection = sec.section
                                                                    showEndSecMenu = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Specific time pickers
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "时间范围",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable {
                                                        showTimePicker(context, startTimeStr) { picked ->
                                                            startTimeStr = picked
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = startTimeStr,
                                                        fontSize = 12.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }

                                                Text("至", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable {
                                                        showTimePicker(context, endTimeStr) { picked ->
                                                            endTimeStr = picked
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = endTimeStr,
                                                        fontSize = 12.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: macOS Calendar Recurrence & Reminders
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Frequency
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "重复频次",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.clickable { showFreqMenu = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(text = frequency, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        DropdownMenu(expanded = showFreqMenu, onDismissRequest = { showFreqMenu = false }) {
                                            frequencies.forEach { f ->
                                                DropdownMenuItem(
                                                    text = { Text(f, fontSize = 13.sp) },
                                                    onClick = {
                                                        frequency = f
                                                        showFreqMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (frequency == "自定义每周某几天") {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("生效星期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val weekDays = listOf(
                                            7 to "日", 1 to "一", 2 to "二", 3 to "三",
                                            4 to "四", 5 to "五", 6 to "六"
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            weekDays.forEach { (dVal, dName) ->
                                                val isSel = customDaysOfWeek.contains(dVal)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
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
                                                        fontSize = 12.5.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }

                                        Text("学期生效周次", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        WeekMatrixPicker(
                                            selectedWeeks = customWeeks,
                                            onWeeksChange = { customWeeks = it },
                                            maxWeeks = 25,
                                            accentColor = parseHexColor(eventColor),
                                            isDark = isDark
                                        )
                                    }
                                }

                                if (frequency != "不重复") {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

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
                                            Text("结束重复", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                            Box {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable { showEndRepeatMenu = true }
                                                ) {
                                                    val label = when (endRepeatType) {
                                                        "until_date" -> "于指定日期终止"
                                                        "count" -> "达到指定次数终止"
                                                        else -> "永不终止"
                                                    }
                                                    Text("$label ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("终止日期", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable {
                                                        showDatePicker(context, endRepeatUntil) { picked ->
                                                            endRepeatUntil = picked
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = endRepeatUntil,
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        } else if (endRepeatType == "count") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("发生次数", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                    ) {
                                                        BasicTextField(
                                                            value = endRepeatCount.toString(),
                                                            onValueChange = { endRepeatCount = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 10 },
                                                            singleLine = true,
                                                            textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
                                                            modifier = Modifier.width(40.dp).padding(horizontal = 6.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    Text("次后停止", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Reminder
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "提醒通知",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.clickable { showAlertMenu = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(text = alertOption, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
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
                        }

                        // Card 4: Location & Notes
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                    BasicTextField(
                                        value = location,
                                        onValueChange = { location = it },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                        decorationBox = { inner ->
                                            if (location.isEmpty()) {
                                                Text("地点 (选填)", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                            }
                                            inner()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                    BasicTextField(
                                        value = eventNotes,
                                        onValueChange = { eventNotes = it },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                        decorationBox = { inner ->
                                            if (eventNotes.isEmpty()) {
                                                Text("备注说明 (选填)", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                            }
                                            inner()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        // ==================== TASK MODE ====================
                        // Card 1: Task Title
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(14.dp)) {
                                BasicTextField(
                                    value = taskTitle,
                                    onValueChange = { taskTitle = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (taskTitle.isEmpty()) {
                                            Text(
                                                text = "待办事项名称 (如：完成编译原理大作业)",
                                                fontSize = 14.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Card 2: Course Linkage & Priority & Deadline
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Course Linkage
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "关联课程",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Box {
                                        Surface(
                                            onClick = { showCourseMenu = true },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val selCourse = courses.find { it.id == selectedCourseId }
                                                selCourse?.let { c ->
                                                    val cColor = parseHexColor(c.color)
                                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(cColor))
                                                }
                                                Text(
                                                    text = selCourse?.name ?: "无关联课程",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (selCourse != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
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
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(parseHexColor(c.color)))
                                                            Text(c.name, fontSize = 13.sp)
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedCourseId = c.id
                                                        showCourseMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Priority
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "优先级",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CircleShape)
                                            .padding(2.5.dp)
                                    ) {
                                        listOf("low" to "普通", "medium" to "重要", "high" to "紧急").forEach { (pCode, pLabel) ->
                                            val isSelected = (taskPriority == pCode)
                                            val activeColor = when (pCode) {
                                                "high" -> Color(0xFFFF5A5F)
                                                "medium" -> Color(0xFFE8A87C)
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent)
                                                    .clickable { taskPriority = pCode }
                                                    .padding(horizontal = 10.dp, vertical = 3.5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = pLabel,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Deadline Date & Time Picker (Interactive click to invoke DateTimePicker)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = hasDueDate,
                                            onCheckedChange = { hasDueDate = it },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "设置截止时间",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (hasDueDate) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.clickable {
                                                showDateTimePicker(context, taskDueDateStr) { picked ->
                                                    taskDueDateStr = picked
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = taskDueDateStr,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: Target Minutes
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "预计专注时长",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            BasicTextField(
                                                value = targetMinutes.toString(),
                                                onValueChange = { targetMinutes = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 45 },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    fontSize = 12.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                modifier = Modifier
                                                    .width(42.dp)
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text("分钟", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(15, 30, 45, 60, 90, 120).forEach { mins ->
                                        val isSel = (targetMinutes == mins)
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                                                .border(0.5.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CircleShape)
                                                .clickable { targetMinutes = mins }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${mins}m",
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card 4: Task Notes
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                BasicTextField(
                                    value = taskNotes,
                                    onValueChange = { taskNotes = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                    decorationBox = { inner ->
                                        if (taskNotes.isEmpty()) {
                                            Text("备注说明 (选填)", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
