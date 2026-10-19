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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
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
import org.classflow.data.model.Course
import org.classflow.data.model.CourseSchedule
import org.classflow.data.model.SectionTime
import org.classflow.data.model.WeekPatternType
import org.classflow.ui.components.GoogleCalendarColorPicker
import org.classflow.ui.components.WeekMatrixPicker
import org.classflow.ui.components.parseHexColor
import org.classflow.ui.components.showTimePicker
import java.util.UUID

class ScheduleDraft(
    val id: String = UUID.randomUUID().toString(),
    dayOfWeek: Int = 1,
    timeMode: String = "bySection", // "bySection" or "byCustomTime"
    startSection: Int = 1,
    endSection: Int = 2,
    customStartTime: String = "08:00",
    customEndTime: String = "09:35",
    classroom: String = "",
    teacher: String = "",
    customWeeks: Set<Int> = (1..20).toSet()
) {
    var dayOfWeek by mutableIntStateOf(dayOfWeek)
    var timeMode by mutableStateOf(timeMode)
    var startSection by mutableIntStateOf(startSection)
    var endSection by mutableIntStateOf(endSection)
    var customStartTime by mutableStateOf(customStartTime)
    var customEndTime by mutableStateOf(customEndTime)
    var classroom by mutableStateOf(classroom)
    var teacher by mutableStateOf(teacher)
    var customWeeks by mutableStateOf(customWeeks)
}

/**
 * Unified Course Creation Dialog with macOS-grade modal aesthetics:
 * - Surface(RoundedCornerShape(20.dp), heightIn(max = 680.dp))
 * - Inset rounded cards (RoundedCornerShape(12.dp), surfaceVariant)
 * - BasicTextField without thick box outlines
 * - Multi-slot arrangements with interactive time pickers & WeekMatrixPicker (1..totalWeeks)
 */
@Composable
fun CourseAddDialog(
    semesterId: String,
    initialDayOfWeek: Int = 1,
    initialStartSection: Int = 1,
    initialEndSection: Int = 2,
    totalWeeks: Int = 20,
    availableSections: List<SectionTime> = emptyList(),
    existingCourseNames: List<String> = emptyList(),
    existingTeachers: List<String> = emptyList(),
    existingClassrooms: List<String> = emptyList(),
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onSaveCourse: (Course) -> Unit
) {
    val context = LocalContext.current

    var courseName by remember { mutableStateOf("") }
    var defaultTeacher by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("#5B7FB5") }

    val schedules = remember {
        mutableStateListOf(
            ScheduleDraft(
                dayOfWeek = initialDayOfWeek,
                startSection = initialStartSection,
                endSection = maxOf(initialStartSection, initialEndSection),
                customWeeks = (1..maxOf(1, totalWeeks)).toSet()
            )
        )
    }

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

    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val canSave = courseName.trim().isNotBlank()

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
                // 1. Navigation Header Bar matching macOS: [ 取消 | 添加课程 | 保存 ]
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

                    Text(
                        text = "添加课程",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "保存",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = canSave) {
                                val courseId = UUID.randomUUID().toString()
                                val courseSchedules = schedules.map { draft ->
                                    val weeks = draft.customWeeks.sorted()
                                    val pattern = if (weeks.size >= 25) WeekPatternType.ALL else WeekPatternType.CUSTOM
                                    val customStart = if (draft.timeMode == "byCustomTime") draft.customStartTime else null
                                    val customEnd = if (draft.timeMode == "byCustomTime") draft.customEndTime else null

                                    CourseSchedule(
                                        id = UUID.randomUUID().toString(),
                                        courseId = courseId,
                                        dayOfWeek = draft.dayOfWeek,
                                        startSection = draft.startSection,
                                        endSection = maxOf(draft.startSection, draft.endSection),
                                        classroom = draft.classroom.trim().ifEmpty { null },
                                        teacher = draft.teacher.trim().ifEmpty { defaultTeacher.trim().ifEmpty { null } },
                                        weekPattern = pattern,
                                        customWeeks = weeks,
                                        customStartTime = customStart,
                                        customEndTime = customEnd
                                    )
                                }

                                val newCourse = Course(
                                    id = courseId,
                                    semesterId = semesterId,
                                    name = courseName.trim(),
                                    teacher = defaultTeacher.trim().ifEmpty { null },
                                    color = colorHex,
                                    notes = notes.trim().ifEmpty { null },
                                    schedules = courseSchedules
                                )
                                onSaveCourse(newCourse)
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 2. Scrollable Content Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Card 1: Basic Info (Name, Teacher, Notes, Color)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Course Name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = courseName,
                                    onValueChange = { courseName = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    decorationBox = { inner ->
                                        if (courseName.isEmpty()) {
                                            Text(
                                                text = "课程名称 (如：高等数学)",
                                                fontSize = 14.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(colorHex))
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                            // Default Teacher
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                BasicTextField(
                                    value = defaultTeacher,
                                    onValueChange = { defaultTeacher = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                    decorationBox = { inner ->
                                        if (defaultTeacher.isEmpty()) {
                                            Text("任课教师 (选填)", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                            // Notes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                BasicTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                    decorationBox = { inner ->
                                        if (notes.isEmpty()) {
                                            Text("课程备注说明 (选填)", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                            // Color Picker Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "卡片颜色",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                GoogleCalendarColorPicker(
                                    selectedHex = colorHex,
                                    onColorSelected = { colorHex = it }
                                )
                            }
                        }
                    }

                    // Schedule Arrangements Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "上课安排 (${schedules.size} 个时段)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            onClick = {
                                val last = schedules.lastOrNull()
                                val nextDay = if (last != null) (last.dayOfWeek % 7) + 1 else 1
                                schedules.add(
                                    ScheduleDraft(
                                        dayOfWeek = nextDay,
                                        startSection = last?.startSection ?: 1,
                                        endSection = last?.endSection ?: 2,
                                        classroom = last?.classroom ?: "",
                                        teacher = last?.teacher ?: defaultTeacher,
                                        customWeeks = last?.customWeeks ?: (1..maxOf(1, totalWeeks)).toSet()
                                    )
                                )
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Text("添加时段", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Multi-draft schedule cards
                    schedules.forEachIndexed { index, draft ->
                        var showDayMenu by remember { mutableStateOf(false) }
                        var showStartSecMenu by remember { mutableStateOf(false) }
                        var showEndSecMenu by remember { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Slot header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "时段 ${index + 1}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                        )
                                    }

                                    if (schedules.size > 1) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "删除时段",
                                            tint = Color(0xFFFF5A5F),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { schedules.removeAt(index) }
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Weekday Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "上课星期",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.clickable { showDayMenu = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = dayNames.getOrElse(draft.dayOfWeek - 1) { "周一" },
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        DropdownMenu(expanded = showDayMenu, onDismissRequest = { showDayMenu = false }) {
                                            dayNames.forEachIndexed { dIdx, dName ->
                                                DropdownMenuItem(
                                                    text = { Text(dName, fontSize = 13.sp) },
                                                    onClick = {
                                                        draft.dayOfWeek = dIdx + 1
                                                        showDayMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Time Mode: bySection vs byCustomTime
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "时间模式",
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
                                        listOf("bySection" to "按作息节次", "byCustomTime" to "按具体时间").forEach { (tm, tmLabel) ->
                                            val isSel = (draft.timeMode == tm)
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                    .clickable { draft.timeMode = tm }
                                                    .padding(horizontal = 10.dp, vertical = 3.5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = tmLabel,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                if (draft.timeMode == "bySection") {
                                    // Section range
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "节次范围",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable { showStartSecMenu = true }
                                                ) {
                                                    Text("第 ${draft.startSection} 节 ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                }
                                                DropdownMenu(expanded = showStartSecMenu, onDismissRequest = { showStartSecMenu = false }) {
                                                    effectiveSections.forEach { sec ->
                                                        DropdownMenuItem(
                                                            text = { Text("第 ${sec.section} 节 (${sec.startTime})", fontSize = 12.5.sp) },
                                                            onClick = {
                                                                draft.startSection = sec.section
                                                                if (draft.endSection < sec.section) draft.endSection = sec.section
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
                                                    Text("第 ${draft.endSection} 节 ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                                }
                                                DropdownMenu(expanded = showEndSecMenu, onDismissRequest = { showEndSecMenu = false }) {
                                                    effectiveSections.filter { it.section >= draft.startSection }.forEach { sec ->
                                                        DropdownMenuItem(
                                                            text = { Text("第 ${sec.section} 节 (${sec.endTime})", fontSize = 12.5.sp) },
                                                            onClick = {
                                                                draft.endSection = sec.section
                                                                showEndSecMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Custom Time Pickers
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "时间范围",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                modifier = Modifier.clickable {
                                                    showTimePicker(context, draft.customStartTime) { picked ->
                                                        draft.customStartTime = picked
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = draft.customStartTime,
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
                                                    showTimePicker(context, draft.customEndTime) { picked ->
                                                        draft.customEndTime = picked
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = draft.customEndTime,
                                                    fontSize = 12.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Classroom (@教室)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                    BasicTextField(
                                        value = draft.classroom,
                                        onValueChange = { draft.classroom = it },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                        decorationBox = { inner ->
                                            if (draft.classroom.isEmpty()) {
                                                Text("教室地点 (如：教三 201)", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                            }
                                            inner()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Teacher for slot
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                    BasicTextField(
                                        value = draft.teacher,
                                        onValueChange = { draft.teacher = it },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                        decorationBox = { inner ->
                                            if (draft.teacher.isEmpty()) {
                                                Text("该时段任课教师 (选填)", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                            }
                                            inner()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Week Matrix
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
                                        Text(
                                            text = "学期生效周次",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "已选 ${draft.customWeeks.size} 周",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                    WeekMatrixPicker(
                                        selectedWeeks = draft.customWeeks,
                                        onWeeksChange = { draft.customWeeks = it },
                                        maxWeeks = totalWeeks,
                                        accentColor = parseHexColor(colorHex),
                                        isDark = isDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
