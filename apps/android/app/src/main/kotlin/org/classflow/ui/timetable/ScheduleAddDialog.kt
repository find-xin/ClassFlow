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
    maxWeeks: Int = 20,
    initialEvent: CalendarEvent? = null,
    initialTask: org.classflow.data.model.StudyTask? = null,
    availableSections: List<SectionTime> = emptyList(),
    courses: List<Course> = emptyList(),
    semesterId: String? = null,
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onSaveSchedule: (CalendarEvent) -> Unit,
    onSaveTask: ((org.classflow.data.model.StudyTask) -> Unit)? = null
) {
    val context = LocalContext.current
    val isEditing = initialEvent != null || initialTask != null
    var mode by remember {
        mutableStateOf(if (initialTask != null) ScheduleItemMode.TASK else ScheduleItemMode.EVENT)
    }

    // MARK: - Event States
    var eventTitle by remember { mutableStateOf(initialEvent?.title ?: "") }
    var startDateStr by remember {
        mutableStateOf(initialEvent?.startDate ?: initialEvent?.startTime?.split(" ")?.firstOrNull() ?: initialDate)
    }
    var endDateStr by remember {
        mutableStateOf(initialEvent?.endDate ?: startDateStr)
    }
    var isAllDay by remember { mutableStateOf(initialEvent?.isAllDay ?: false) }
    var timeMode by remember {
        mutableStateOf(if (initialEvent?.startSection != null) "bySection" else "byTime")
    }
    var startSection by remember { mutableIntStateOf(initialEvent?.startSection ?: initialSection) }
    var endSection by remember { mutableIntStateOf(initialEvent?.endSection ?: maxOf(initialSection, initialSection + 1)) }
    var startTimeStr by remember {
        mutableStateOf(
            if (initialEvent != null && initialEvent.startTime.contains(" ")) {
                initialEvent.startTime.substringAfter(" ").take(5)
            } else "09:00"
        )
    }
    var endTimeStr by remember {
        mutableStateOf(
            if (initialEvent != null && initialEvent.endTime.contains(" ")) {
                initialEvent.endTime.substringAfter(" ").take(5)
            } else "10:30"
        )
    }

    // Recurrence states
    var frequency by remember {
        mutableStateOf(
            when (initialEvent?.frequency) {
                "daily", "每天" -> "每天"
                "workdays", "工作日" -> "工作日 (周一至周五)"
                "weekends", "周末" -> "周末 (周六周日)"
                "weekly", "每周" -> "每周"
                "monthly", "每月" -> "每月"
                "customDays", "自定义每周某几天" -> "自定义每周某几天"
                "customWeeks", "按周次重复" -> "按周次重复"
                else -> "不重复"
            }
        )
    }
    var customDaysOfWeek by remember {
        mutableStateOf(
            if (!initialEvent?.customDaysOfWeek.isNullOrEmpty()) initialEvent!!.customDaysOfWeek.toSet()
            else setOf(initialDayOfWeek)
        )
    }
    var customWeeks by remember {
        mutableStateOf(
            if (!initialEvent?.customWeeks.isNullOrEmpty()) initialEvent!!.customWeeks.toSet()
            else (1..maxOf(1, maxWeeks)).toSet()
        )
    }
    var endRepeatType by remember { mutableStateOf(initialEvent?.endRepeatType ?: "never") }
    var endRepeatUntil by remember { mutableStateOf(initialEvent?.endRepeatUntil ?: initialDate) }
    var endRepeatCount by remember { mutableIntStateOf(initialEvent?.endRepeatCount ?: 10) }

    var alertOption by remember {
        mutableStateOf(
            when (initialEvent?.alertMinutes) {
                0 -> "日程开始时"
                5 -> "提前 5 分钟"
                15 -> "提前 15 分钟"
                30 -> "提前 30 分钟"
                60 -> "提前 1 小时"
                1440 -> "提前 1 天"
                else -> if (initialEvent != null) "无提醒" else "提前 15 分钟"
            }
        )
    }
    var eventColor by remember { mutableStateOf(initialEvent?.color ?: "#FF5A5F") }
    var location by remember { mutableStateOf(initialEvent?.location ?: "") }
    var eventNotes by remember { mutableStateOf(initialEvent?.description ?: "") }

    // MARK: - Task States (Google Tasks Parity)
    var taskTitle by remember { mutableStateOf(initialTask?.title ?: "") }
    var selectedCourseId by remember {
        mutableStateOf(
            initialTask?.courseName?.let { cName -> courses.find { it.name == cName }?.id }
        )
    }
    var taskPriority by remember { mutableStateOf(initialTask?.priority ?: "medium") }
    var hasStartDate by remember { mutableStateOf(initialTask?.startDate != null) }
    var taskStartDateStr by remember { mutableStateOf(initialTask?.startDate ?: initialDate) }
    var hasDueDate by remember { mutableStateOf(initialTask?.dueDate != null || initialTask == null) }
    var taskDueDateStr by remember { mutableStateOf(initialTask?.dueDate ?: "$initialDate 18:00") }
    var taskFrequency by remember {
        mutableStateOf(
            when (initialTask?.frequency) {
                "daily", "每天" -> "每天"
                "workdays", "工作日" -> "工作日 (周一至周五)"
                "weekends", "周末" -> "周末 (周六周日)"
                "weekly", "每周" -> "每周"
                "monthly", "每月" -> "每月"
                "customDays", "自定义每周某几天" -> "自定义每周某几天"
                "customWeeks", "按周次重复" -> "按周次重复"
                else -> "不重复"
            }
        )
    }
    var targetMinutes by remember { mutableIntStateOf(initialTask?.targetMinutes ?: 45) }
    var taskNotes by remember { mutableStateOf(initialTask?.notes ?: "") }

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
        "周末 (周六周日)",
        "每周",
        "每月",
        "自定义每周某几天",
        "按周次重复"
    )

    val taskFrequencies = listOf(
        "不重复",
        "每天",
        "工作日 (周一至周五)",
        "周末 (周六周日)",
        "每周",
        "每月",
        "自定义每周某几天",
        "按周次重复"
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
    var showTaskFreqMenu by remember { mutableStateOf(false) }
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

                    if (isEditing) {
                        Text(
                            text = if (initialTask != null) "编辑待办" else "编辑日程",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
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
                    }

                    Text(
                        text = if (isEditing) "保存" else "添加",
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

                                    val isRecurring = frequency != "不重复" && frequency.isNotBlank()
                                    val resolvedEndDate = if (!isRecurring && isAllDay) endDateStr else null
                                    val eventFreqCode = when (frequency) {
                                        "每天" -> "daily"
                                        "工作日 (周一至周五)" -> "workdays"
                                        "周末 (周六周日)" -> "weekends"
                                        "每周" -> "weekly"
                                        "每月" -> "monthly"
                                        "自定义每周某几天" -> "customDays"
                                        "按周次重复" -> "customWeeks"
                                        else -> "none"
                                    }
                                    val baseEvent = initialEvent ?: CalendarEvent(
                                        id = UUID.randomUUID().toString(),
                                        semesterId = semesterId,
                                        title = eventTitle.trim(),
                                        startTime = "$startDateStr $resolvedStartT",
                                        endTime = if (resolvedEndDate != null) "$resolvedEndDate $resolvedEndT" else "$startDateStr $resolvedEndT",
                                        startDate = startDateStr,
                                        endDate = resolvedEndDate,
                                        isAllDay = isAllDay,
                                        location = location.trim().ifEmpty { null },
                                        description = eventNotes.trim().ifEmpty { null },
                                        color = eventColor,
                                        startSection = resolvedStartSec,
                                        endSection = resolvedEndSec,
                                        frequency = eventFreqCode,
                                        intervalWeeks = 1,
                                        customDaysOfWeek = if (frequency == "自定义每周某几天") customDaysOfWeek.toList() else emptyList(),
                                        customWeeks = if (frequency == "按周次重复") customWeeks.toList() else emptyList(),
                                        endRepeatType = endRepeatType,
                                        endRepeatUntil = endRepeatUntil,
                                        endRepeatCount = endRepeatCount,
                                        alertMinutes = alertMins,
                                        isTask = false
                                    )
                                    val updatedEvent = baseEvent.copy(
                                        title = eventTitle.trim(),
                                        startTime = "$startDateStr $resolvedStartT",
                                        endTime = if (resolvedEndDate != null) "$resolvedEndDate $resolvedEndT" else "$startDateStr $resolvedEndT",
                                        startDate = startDateStr,
                                        endDate = resolvedEndDate,
                                        isAllDay = isAllDay,
                                        location = location.trim().ifEmpty { null },
                                        description = eventNotes.trim().ifEmpty { null },
                                        color = eventColor,
                                        startSection = resolvedStartSec,
                                        endSection = resolvedEndSec,
                                        frequency = eventFreqCode,
                                        intervalWeeks = 1,
                                        customDaysOfWeek = if (frequency == "自定义每周某几天") customDaysOfWeek.toList() else emptyList(),
                                        customWeeks = if (frequency == "按周次重复") customWeeks.toList() else emptyList(),
                                        endRepeatType = endRepeatType,
                                        endRepeatUntil = endRepeatUntil,
                                        endRepeatCount = endRepeatCount,
                                        alertMinutes = alertMins,
                                        isTask = false
                                    )
                                    onSaveSchedule(updatedEvent)
                                } else {
                                    val matchedCourse = courses.find { it.id == selectedCourseId }
                                    val taskFreqCode = when (taskFrequency) {
                                        "每天" -> "daily"
                                        "工作日 (周一至周五)" -> "workdays"
                                        "周末 (周六周日)" -> "weekends"
                                        "每周" -> "weekly"
                                        "每月" -> "monthly"
                                        "自定义每周某几天" -> "customDays"
                                        "按周次重复" -> "customWeeks"
                                        else -> "none"
                                    }
                                    val baseTask = initialTask ?: org.classflow.data.model.StudyTask(
                                        id = UUID.randomUUID().toString(),
                                        semesterId = semesterId,
                                        title = taskTitle.trim(),
                                        courseName = matchedCourse?.name,
                                        targetMinutes = targetMinutes,
                                        completedMinutes = 0,
                                        isCompleted = false,
                                        startDate = if (hasStartDate) taskStartDateStr else null,
                                        dueDate = if (hasDueDate) taskDueDateStr else null,
                                        frequency = taskFreqCode,
                                        customDaysOfWeek = if (taskFrequency == "自定义每周某几天") customDaysOfWeek.toList() else emptyList(),
                                        customWeeks = if (taskFrequency == "按周次重复") customWeeks.toList() else emptyList(),
                                        endRepeatType = if (taskFrequency != "不重复") endRepeatType else "never",
                                        endRepeatUntil = if (taskFrequency != "不重复" && endRepeatType == "until_date") endRepeatUntil else null,
                                        endRepeatCount = if (taskFrequency != "不重复" && endRepeatType == "count") endRepeatCount else null,
                                        priority = taskPriority,
                                        notes = taskNotes.trim().ifEmpty { null }
                                    )
                                    val updatedTask = baseTask.copy(
                                        title = taskTitle.trim(),
                                        courseName = matchedCourse?.name,
                                        targetMinutes = targetMinutes,
                                        startDate = if (hasStartDate) taskStartDateStr else null,
                                        dueDate = if (hasDueDate) taskDueDateStr else null,
                                        frequency = taskFreqCode,
                                        customDaysOfWeek = if (taskFrequency == "自定义每周某几天") customDaysOfWeek.toList() else emptyList(),
                                        customWeeks = if (taskFrequency == "按周次重复") customWeeks.toList() else emptyList(),
                                        endRepeatType = if (taskFrequency != "不重复") endRepeatType else "never",
                                        endRepeatUntil = if (taskFrequency != "不重复" && endRepeatType == "until_date") endRepeatUntil else null,
                                        endRepeatCount = if (taskFrequency != "不重复" && endRepeatType == "count") endRepeatCount else null,
                                        priority = taskPriority,
                                        notes = taskNotes.trim().ifEmpty { null }
                                    )
                                    if (onSaveTask != null) {
                                        onSaveTask(updatedTask)
                                    } else {
                                        val dueDate = if (hasDueDate) taskDueDateStr else null
                                        val taskEvent = CalendarEvent(
                                            semesterId = semesterId,
                                            title = taskTitle.trim(),
                                            startTime = if (hasDueDate) taskDueDateStr else "$initialDate 09:00",
                                            endTime = if (hasDueDate) taskDueDateStr else "$initialDate 10:00",
                                            startDate = if (hasStartDate) taskStartDateStr else (if (hasDueDate) taskDueDateStr.substringBefore(" ") else initialDate),
                                            endDate = if (hasDueDate) taskDueDateStr.substringBefore(" ") else initialDate,
                                            isAllDay = false,
                                            color = "#3F51B5",
                                            isTask = true,
                                            isCompleted = false,
                                            courseId = selectedCourseId,
                                            courseName = matchedCourse?.name,
                                            priority = taskPriority,
                                            dueDate = dueDate,
                                            frequency = taskFreqCode,
                                            customDaysOfWeek = if (taskFrequency == "自定义每周某几天") customDaysOfWeek.toList() else emptyList(),
                                            customWeeks = if (taskFrequency == "按周次重复") customWeeks.toList() else emptyList(),
                                            endRepeatType = if (taskFrequency != "不重复") endRepeatType else "never",
                                            endRepeatUntil = if (taskFrequency != "不重复" && endRepeatType == "until_date") endRepeatUntil else null,
                                            endRepeatCount = if (taskFrequency != "不重复" && endRepeatType == "count") endRepeatCount else null,
                                            targetMinutes = targetMinutes,
                                            description = taskNotes.trim().ifEmpty { null }
                                        )
                                        onSaveSchedule(taskEvent)
                                    }
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

                                if (frequency == "不重复" && isAllDay) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                    // End date (only relevant for multi-day non-repeating all-day events)
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
                                                text = "单次上课节次",
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
                                                text = "单次起止时间",
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

                                if (frequency == "自定义每周某几天" || frequency == "按周次重复") {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (frequency == "自定义每周某几天") {
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
                                        } else if (frequency == "按周次重复") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("学期生效周次", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("已选 ${customWeeks.size} 周", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                            }
                                            WeekMatrixPicker(
                                                selectedWeeks = customWeeks,
                                                onWeeksChange = { customWeeks = it },
                                                maxWeeks = maxWeeks,
                                                accentColor = parseHexColor(eventColor),
                                                isDark = isDark
                                            )
                                        }
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
                                            Text("重复终止条件", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                            Box {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable { showEndRepeatMenu = true }
                                                ) {
                                                    val label = when (endRepeatType) {
                                                        "until_date" -> "按日期终止 (指定最后一天)"
                                                        "count" -> "按次数终止 (达到次数后)"
                                                        else -> "从不终止 (一直重复)"
                                                    }
                                                    Text("$label ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                }
                                                DropdownMenu(expanded = showEndRepeatMenu, onDismissRequest = { showEndRepeatMenu = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text("从不终止 (一直重复)", fontSize = 12.5.sp) },
                                                        onClick = {
                                                            endRepeatType = "never"
                                                            showEndRepeatMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("按日期终止 (指定最后一天)", fontSize = 12.5.sp) },
                                                        onClick = {
                                                            endRepeatType = "until_date"
                                                            showEndRepeatMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("按次数终止 (达到次数后)", fontSize = 12.5.sp) },
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
                                                Text("最后重复日期", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                Text("共重复", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                    Text("次后停止重复", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                                // Start Date Picker (Google Tasks style)
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
                                            checked = hasStartDate,
                                            onCheckedChange = { hasStartDate = it },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "设置开始日期",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (hasStartDate) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.clickable {
                                                showDatePicker(context, taskStartDateStr) { picked ->
                                                    taskStartDateStr = picked
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = taskStartDateStr,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Task Recurrence (Google Tasks style)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
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
                                            modifier = Modifier.clickable { showTaskFreqMenu = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = taskFrequency,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showTaskFreqMenu,
                                            onDismissRequest = { showTaskFreqMenu = false }
                                        ) {
                                            taskFrequencies.forEach { freq ->
                                                DropdownMenuItem(
                                                    text = { Text(freq, fontSize = 12.5.sp) },
                                                    onClick = {
                                                        taskFrequency = freq
                                                        showTaskFreqMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (taskFrequency == "自定义每周某几天" || taskFrequency == "按周次重复") {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (taskFrequency == "自定义每周某几天") {
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
                                        } else if (taskFrequency == "按周次重复") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("学期生效周次", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("已选 ${customWeeks.size} 周", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                            }
                                            WeekMatrixPicker(
                                                selectedWeeks = customWeeks,
                                                onWeeksChange = { customWeeks = it },
                                                maxWeeks = maxWeeks,
                                                accentColor = MaterialTheme.colorScheme.primary,
                                                isDark = isDark
                                            )
                                        }
                                    }
                                }

                                if (taskFrequency != "不重复") {
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
                                            Text("重复终止条件", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                            Box {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable { showEndRepeatMenu = true }
                                                ) {
                                                    val label = when (endRepeatType) {
                                                        "until_date" -> "按日期终止 (指定最后一天)"
                                                        "count" -> "按次数终止 (达到次数后)"
                                                        else -> "从不终止 (一直重复)"
                                                    }
                                                    Text("$label ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                }
                                                DropdownMenu(expanded = showEndRepeatMenu, onDismissRequest = { showEndRepeatMenu = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text("从不终止 (一直重复)", fontSize = 12.5.sp) },
                                                        onClick = {
                                                            endRepeatType = "never"
                                                            showEndRepeatMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("按日期终止 (指定最后一天)", fontSize = 12.5.sp) },
                                                        onClick = {
                                                            endRepeatType = "until_date"
                                                            showEndRepeatMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("按次数终止 (达到次数后)", fontSize = 12.5.sp) },
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
                                                Text("最后重复日期", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                Text("共重复", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                    Text("次后停止重复", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
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
                                    listOf(15, 30, 45, 60, 90).forEach { mins ->
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
