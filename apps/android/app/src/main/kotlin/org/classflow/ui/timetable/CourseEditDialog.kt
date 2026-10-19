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

/**
 * 1:1 Parity with macOS CourseEditSheet:
 * - Card-based editing for complete multi-schedule course updates (teachers, rooms, section ranges/custom times)
 * - WeekMatrixPicker (1..25) with quick toggles (全选, 单周, 双周, 前8周, 后8周, 清空)
 * - Inset rounded cards with refined typography
 */
@Composable
fun CourseEditDialog(
    course: Course,
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

    var courseName by remember { mutableStateOf(course.name) }
    var defaultTeacher by remember { mutableStateOf(course.teacher ?: "") }
    var notes by remember { mutableStateOf(course.notes ?: "") }
    var colorHex by remember { mutableStateOf(course.color) }

    val schedules = remember {
        mutableStateListOf<ScheduleDraft>().apply {
            if (course.schedules.isEmpty()) {
                add(ScheduleDraft(customWeeks = (1..maxOf(1, totalWeeks)).toSet()))
            } else {
                course.schedules.forEach { s ->
                    val customSet = if (!s.customWeeks.isNullOrEmpty()) s.customWeeks.toSet() else (1..maxOf(1, totalWeeks)).toSet()
                    add(
                        ScheduleDraft(
                            id = s.id,
                            dayOfWeek = s.dayOfWeek,
                            timeMode = if (s.customStartTime != null) "byCustomTime" else "bySection",
                            startSection = s.startSection,
                            endSection = s.endSection,
                            customStartTime = s.customStartTime ?: "08:00",
                            customEndTime = s.customEndTime ?: "09:35",
                            classroom = s.classroom ?: "",
                            teacher = s.teacher ?: "",
                            customWeeks = customSet
                        )
                    )
                }
            }
        }
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
                // 1. Navigation Header Bar matching macOS: [ 取消 | 编辑课程 | 保存 ]
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
                        text = "编辑课程",
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
                                val courseSchedules = schedules.map { draft ->
                                    val weeks = draft.customWeeks.sorted()
                                    val pattern = if (weeks.size >= 25) WeekPatternType.ALL else WeekPatternType.CUSTOM
                                    val customStart = if (draft.timeMode == "byCustomTime") draft.customStartTime else null
                                    val customEnd = if (draft.timeMode == "byCustomTime") draft.customEndTime else null

                                    CourseSchedule(
                                        id = draft.id,
                                        courseId = course.id,
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

                                val updated = course.copy(
                                    name = courseName.trim(),
                                    teacher = defaultTeacher.trim().ifEmpty { null },
                                    color = colorHex,
                                    notes = notes.trim().ifEmpty { null },
                                    schedules = courseSchedules
                                )
                                onSaveCourse(updated)
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
                                                text = "课程名称 (如：面向对象程序设计)",
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
                                            Text("任课教师 (如：李明副教授)", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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
                            color = Color(0xFFFF5A5F).copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, Color(0xFFFF5A5F).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(13.dp))
                                Text("添加时段", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5A5F))
                            }
                        }
                    }

                    // Schedule Arrangement Cards
                    schedules.forEachIndexed { index, draft ->
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
                                // Slot Badge & Delete Action
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = parseHexColor(colorHex).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "时段 ${index + 1}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = parseHexColor(colorHex),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    if (schedules.size > 1) {
                                        IconButton(
                                            onClick = { schedules.removeAt(index) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "删除该时段",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Day of Week & Time Mode Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Day of week selector
                                    var showDayMenu by remember { mutableStateOf(false) }
                                    Box {
                                        Surface(
                                            onClick = { showDayMenu = true },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    dayNames.getOrElse(draft.dayOfWeek - 1) { "周一" },
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        DropdownMenu(expanded = showDayMenu, onDismissRequest = { showDayMenu = false }) {
                                            dayNames.forEachIndexed { dIdx, name ->
                                                DropdownMenuItem(
                                                    text = { Text(name, fontSize = 13.sp) },
                                                    onClick = {
                                                        draft.dayOfWeek = dIdx + 1
                                                        showDayMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Time Mode Capsule: [ 按节次 | 具体时间 ]
                                    Row(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (draft.timeMode == "bySection") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                .clickable { draft.timeMode = "bySection" }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "按节次",
                                                fontSize = 11.sp,
                                                fontWeight = if (draft.timeMode == "bySection") FontWeight.Bold else FontWeight.Normal,
                                                color = if (draft.timeMode == "bySection") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (draft.timeMode == "byCustomTime") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                .clickable { draft.timeMode = "byCustomTime" }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "具体时间",
                                                fontSize = 11.sp,
                                                fontWeight = if (draft.timeMode == "byCustomTime") FontWeight.Bold else FontWeight.Normal,
                                                color = if (draft.timeMode == "byCustomTime") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Time Range: Section Range or Custom Times
                                if (draft.timeMode == "bySection") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("节次:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        var showStartMenu by remember { mutableStateOf(false) }
                                        Box {
                                            Surface(
                                                onClick = { showStartMenu = true },
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val secTime = effectiveSections.find { it.section == draft.startSection }
                                                    val label = if (secTime != null) "第${secTime.section}节 (${secTime.startTime})" else "第${draft.startSection}节"
                                                    Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                            DropdownMenu(expanded = showStartMenu, onDismissRequest = { showStartMenu = false }) {
                                                effectiveSections.forEach { sec ->
                                                    DropdownMenuItem(
                                                        text = { Text("第 ${sec.section} 节 (${sec.startTime})", fontSize = 12.sp) },
                                                        onClick = {
                                                            draft.startSection = sec.section
                                                            if (draft.endSection < sec.section) {
                                                                draft.endSection = sec.section
                                                            }
                                                            showStartMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Text("至", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        var showEndMenu by remember { mutableStateOf(false) }
                                        Box {
                                            Surface(
                                                onClick = { showEndMenu = true },
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val secTime = effectiveSections.find { it.section == draft.endSection }
                                                    val label = if (secTime != null) "第${secTime.section}节 (${secTime.endTime})" else "第${draft.endSection}节"
                                                    Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                            DropdownMenu(expanded = showEndMenu, onDismissRequest = { showEndMenu = false }) {
                                                effectiveSections.filter { it.section >= draft.startSection }.forEach { sec ->
                                                    DropdownMenuItem(
                                                        text = { Text("第 ${sec.section} 节 (${sec.endTime})", fontSize = 12.sp) },
                                                        onClick = {
                                                            draft.endSection = sec.section
                                                            showEndMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("时间:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        Surface(
                                            onClick = {
                                                showTimePicker(context, draft.customStartTime) { newTime ->
                                                    draft.customStartTime = newTime
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = draft.customStartTime,
                                                fontSize = 12.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }

                                        Text("至", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        Surface(
                                            onClick = {
                                                showTimePicker(context, draft.customEndTime) { newTime ->
                                                    draft.customEndTime = newTime
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = draft.customEndTime,
                                                fontSize = 12.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                // Classroom and Assigned Teacher
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        BasicTextField(
                                            value = draft.classroom,
                                            onValueChange = { draft.classroom = it },
                                            singleLine = true,
                                            textStyle = TextStyle(fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface),
                                            decorationBox = { inner ->
                                                if (draft.classroom.isEmpty()) {
                                                    Text("上课教室 (如：理学楼 302)", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                }
                                                inner()
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        BasicTextField(
                                            value = draft.teacher,
                                            onValueChange = { draft.teacher = it },
                                            singleLine = true,
                                            textStyle = TextStyle(fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface),
                                            decorationBox = { inner ->
                                                if (draft.teacher.isEmpty()) {
                                                    Text("该时段教师 (选填)", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                }
                                                inner()
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                // Teaching Weeks (WeekMatrixPicker 1..25)
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
                                    maxWeeks = totalWeeks,
                                    accentColor = parseHexColor(colorHex),
                                    isDark = isDark,
                                    onWeeksChange = { updated -> draft.customWeeks = updated }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
