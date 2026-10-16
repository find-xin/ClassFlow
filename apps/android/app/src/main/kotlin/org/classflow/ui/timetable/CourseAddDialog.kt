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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.classflow.ui.components.SearchSuggestionTextField
import org.classflow.ui.components.WeekMatrixPicker
import org.classflow.ui.components.parseHexColor
import java.util.UUID

data class ScheduleDraft(
    val id: String = UUID.randomUUID().toString(),
    var dayOfWeek: Int = 1,
    var timeMode: String = "bySection", // "bySection" or "byCustomTime"
    var startSection: Int = 1,
    var endSection: Int = 2,
    var customStartTime: String = "08:00",
    var customEndTime: String = "09:35",
    var classroom: String = "",
    var teacher: String = "",
    var startWeek: Int = 1,
    var endWeek: Int = 16,
    var customWeeks: Set<Int> = (1..16).toSet()
)

/**
 * 1:1 Replica of macOS CourseAddSheet.swift & media_1792136177747_12682d4f.png:
 * - Cancel / Title / Save top navigation bar
 * - Card 1: 课程名称, 任课教师, 课程备注, 卡片颜色
 * - Header: 上课安排 (X 个时段) + 添加时段
 * - Multi-draft cards: 时段胶囊, 星期, 按节次/具体时间, 教室/教师, 周次范围, 周次网格矩阵
 */
@Composable
fun CourseAddDialog(
    semesterId: String,
    initialDayOfWeek: Int = 1,
    initialStartSection: Int = 1,
    initialEndSection: Int = 2,
    availableSections: List<SectionTime> = emptyList(),
    existingCourseNames: List<String> = emptyList(),
    existingTeachers: List<String> = emptyList(),
    existingClassrooms: List<String> = emptyList(),
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onSaveCourse: (Course) -> Unit
) {
    var courseName by remember { mutableStateOf("") }
    var defaultTeacher by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("#5B7FB5") }

    val schedules = remember {
        mutableStateListOf(
            ScheduleDraft(
                dayOfWeek = initialDayOfWeek,
                startSection = initialStartSection,
                endSection = maxOf(initialStartSection, initialEndSection)
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

    val bg = if (isDark) Color(0xFF14151B) else Color(0xFFF7F8FA)
    val cardBg = if (isDark) Color(0xFF1F212A) else Color.White
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFFA1A3AC) else Color(0xFF6B7280)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE5E7EB)
    val accentRed = Color(0xFFFF5A5F)

    val canSave = courseName.trim().isNotBlank()

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
                // 1. Navigation Header Bar matching macOS
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

                    Text(
                        text = "添加课程",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )

                    TextButton(
                        onClick = {
                            if (canSave) {
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
                                        teacher = draft.teacher.trim().ifEmpty { null },
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
                        },
                        enabled = canSave
                    ) {
                        Text(
                            text = "保存",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canSave) accentRed else textSecondary.copy(alpha = 0.4f)
                        )
                    }
                }

                HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

                // 2. Scrollable Content Body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Basic Info Card (macOS styled card)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                    ) {
                        // 课程名称
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "课程名称",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary,
                                modifier = Modifier.width(72.dp)
                            )
                            SearchSuggestionTextField(
                                value = courseName,
                                onValueChange = { courseName = it },
                                placeholder = "例如：高等数学",
                                suggestions = existingCourseNames,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                        // 任课教师
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "任课教师",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary,
                                modifier = Modifier.width(72.dp)
                            )
                            SearchSuggestionTextField(
                                value = defaultTeacher,
                                onValueChange = { defaultTeacher = it },
                                placeholder = "例如：张教授 (选填)",
                                suggestions = existingTeachers,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 14.dp))

                        // 课程备注
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "课程备注",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary,
                                modifier = Modifier.width(72.dp)
                            )
                            TextField(
                                value = notes,
                                onValueChange = { notes = it },
                                placeholder = { Text("例如：期末闭卷考 / 需带实验手册", fontSize = 13.5.sp, color = textSecondary.copy(alpha = 0.7f)) },
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

                        // 卡片颜色
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "卡片颜色",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary,
                                modifier = Modifier.width(72.dp)
                            )
                            GoogleCalendarColorPicker(
                                selectedHex = colorHex,
                                onColorSelected = { colorHex = it }
                            )
                        }
                    }

                    // 2. Schedule Section Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "上课安排 (${schedules.size} 个时段)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary
                        )

                        TextButton(
                            onClick = {
                                val nextDay = minOf(7, (schedules.lastOrNull()?.dayOfWeek ?: 1) + 2)
                                schedules.add(
                                    ScheduleDraft(
                                        dayOfWeek = nextDay,
                                        startSection = 1,
                                        endSection = 2,
                                        customWeeks = (1..16).toSet()
                                    )
                                )
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = accentRed, modifier = Modifier.size(15.dp))
                                Text("添加时段", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accentRed)
                            }
                        }
                    }

                    // 3. Schedule Draft Cards
                    schedules.forEachIndexed { index, draft ->
                        ScheduleDraftCard(
                            index = index,
                            draft = draft,
                            totalDrafts = schedules.size,
                            availableSections = effectiveSections,
                            accentColor = parseHexColor(colorHex),
                            existingClassrooms = existingClassrooms,
                            existingTeachers = existingTeachers,
                            isDark = isDark,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            dividerColor = dividerColor,
                            onDelete = { schedules.removeAt(index) },
                            onDraftChange = { updated ->
                                schedules[index] = updated
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleDraftCard(
    index: Int,
    draft: ScheduleDraft,
    totalDrafts: Int,
    availableSections: List<SectionTime>,
    accentColor: Color,
    existingClassrooms: List<String>,
    existingTeachers: List<String>,
    isDark: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color,
    onDelete: () -> Unit,
    onDraftChange: (ScheduleDraft) -> Unit
) {
    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    var showDayMenu by remember { mutableStateOf(false) }
    var showStartSecMenu by remember { mutableStateOf(false) }
    var showEndSecMenu by remember { mutableStateOf(false) }
    var showStartWeekMenu by remember { mutableStateOf(false) }
    var showEndWeekMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card Header: 時段 X pill & Delete button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "时段 ${index + 1}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            if (totalDrafts > 1) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除时段",
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Day of Week & Time Mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Day selector dropdown button
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                    modifier = Modifier.clickable { showDayMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = dayNames.getOrElse(draft.dayOfWeek - 1) { "周一" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary
                        )
                        Text("▾", fontSize = 11.sp, color = textSecondary)
                    }
                }

                DropdownMenu(
                    expanded = showDayMenu,
                    onDismissRequest = { showDayMenu = false }
                ) {
                    dayNames.forEachIndexed { dIdx, dName ->
                        DropdownMenuItem(
                            text = { Text(dName, fontSize = 13.sp) },
                            onClick = {
                                showDayMenu = false
                                onDraftChange(draft.copy(dayOfWeek = dIdx + 1))
                            }
                        )
                    }
                }
            }

            // Segmented picker: 按节次 vs 具体时间
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
                        .background(if (draft.timeMode == "bySection") (if (isDark) Color(0xFF383C52) else Color.White) else Color.Transparent)
                        .clickable { onDraftChange(draft.copy(timeMode = "bySection")) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "按节次",
                        fontSize = 12.sp,
                        fontWeight = if (draft.timeMode == "bySection") FontWeight.Bold else FontWeight.Normal,
                        color = textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (draft.timeMode == "byCustomTime") (if (isDark) Color(0xFF383C52) else Color.White) else Color.Transparent)
                        .clickable { onDraftChange(draft.copy(timeMode = "byCustomTime")) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "具体时间",
                        fontSize = 12.sp,
                        fontWeight = if (draft.timeMode == "byCustomTime") FontWeight.Bold else FontWeight.Normal,
                        color = textPrimary
                    )
                }
            }
        }

        // Time Picker or Section Range
        if (draft.timeMode == "bySection") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("时段:", fontSize = 12.sp, color = textSecondary)

                // Start Section Dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                        modifier = Modifier.clickable { showStartSecMenu = true }
                    ) {
                        val curSec = availableSections.find { it.section == draft.startSection }
                        val label = "第 ${draft.startSection} 节 (${curSec?.startTime ?: "08:00"}) ▾"
                        Text(label, fontSize = 12.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                    DropdownMenu(expanded = showStartSecMenu, onDismissRequest = { showStartSecMenu = false }) {
                        availableSections.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("第 ${s.section} 节 (${s.startTime})", fontSize = 12.5.sp) },
                                onClick = {
                                    showStartSecMenu = false
                                    val newEnd = maxOf(s.section, draft.endSection)
                                    onDraftChange(draft.copy(startSection = s.section, endSection = newEnd))
                                }
                            )
                        }
                    }
                }

                Text("至", fontSize = 12.sp, color = textSecondary)

                // End Section Dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                        modifier = Modifier.clickable { showEndSecMenu = true }
                    ) {
                        val curSec = availableSections.find { it.section == draft.endSection }
                        val label = "第 ${draft.endSection} 节 (${curSec?.endTime ?: "09:35"}) ▾"
                        Text(label, fontSize = 12.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                    DropdownMenu(expanded = showEndSecMenu, onDismissRequest = { showEndSecMenu = false }) {
                        availableSections.filter { it.section >= draft.startSection }.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("第 ${s.section} 节 (${s.endTime})", fontSize = 12.5.sp) },
                                onClick = {
                                    showEndSecMenu = false
                                    onDraftChange(draft.copy(endSection = s.section))
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
                Text("时间:", fontSize = 12.sp, color = textSecondary)
                OutlinedTextField(
                    value = draft.customStartTime,
                    onValueChange = { onDraftChange(draft.copy(customStartTime = it)) },
                    placeholder = { Text("08:00", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(82.dp)
                )
                Text("至", fontSize = 12.sp, color = textSecondary)
                OutlinedTextField(
                    value = draft.customEndTime,
                    onValueChange = { onDraftChange(draft.copy(customEndTime = it)) },
                    placeholder = { Text("09:35", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(82.dp)
                )
            }
        }

        // Classroom Location & Assigned Teacher Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchSuggestionTextField(
                value = draft.classroom,
                onValueChange = { onDraftChange(draft.copy(classroom = it)) },
                placeholder = "教室 (如：理学楼 302)",
                suggestions = existingClassrooms,
                leadingIcon = Icons.Default.LocationOn,
                modifier = Modifier.weight(1f)
            )

            SearchSuggestionTextField(
                value = draft.teacher,
                onValueChange = { onDraftChange(draft.copy(teacher = it)) },
                placeholder = "教师 (选填)",
                suggestions = existingTeachers,
                leadingIcon = Icons.Default.Person,
                modifier = Modifier.weight(1f)
            )
        }

        // Teaching Weeks Matrix (周次范围 + 周次矩阵直选)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("周次范围:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textSecondary)

                // Start Week Dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                        modifier = Modifier.clickable { showStartWeekMenu = true }
                    ) {
                        Text("第 ${draft.startWeek} 周 ▾", fontSize = 12.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                    DropdownMenu(expanded = showStartWeekMenu, onDismissRequest = { showStartWeekMenu = false }) {
                        (1..25).forEach { w ->
                            DropdownMenuItem(
                                text = { Text("第 $w 周", fontSize = 12.5.sp) },
                                onClick = {
                                    showStartWeekMenu = false
                                    val low = minOf(w, draft.endWeek)
                                    val high = maxOf(w, draft.endWeek)
                                    onDraftChange(draft.copy(startWeek = low, endWeek = high, customWeeks = (low..high).toSet()))
                                }
                            )
                        }
                    }
                }

                Text("至", fontSize = 12.sp, color = textSecondary)

                // End Week Dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6),
                        modifier = Modifier.clickable { showEndWeekMenu = true }
                    ) {
                        Text("第 ${draft.endWeek} 周 ▾", fontSize = 12.sp, color = textPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                    DropdownMenu(expanded = showEndWeekMenu, onDismissRequest = { showEndWeekMenu = false }) {
                        (1..25).forEach { w ->
                            DropdownMenuItem(
                                text = { Text("第 $w 周", fontSize = 12.5.sp) },
                                onClick = {
                                    showEndWeekMenu = false
                                    val low = minOf(draft.startWeek, w)
                                    val high = maxOf(draft.startWeek, w)
                                    onDraftChange(draft.copy(startWeek = low, endWeek = high, customWeeks = (low..high).toSet()))
                                }
                            )
                        }
                    }
                }
            }

            // Week Matrix Picker
            WeekMatrixPicker(
                selectedWeeks = draft.customWeeks,
                onWeeksChange = { updatedWeeks ->
                    onDraftChange(draft.copy(customWeeks = updatedWeeks))
                },
                maxWeeks = 25,
                accentColor = accentColor,
                isDark = isDark
            )
        }
    }
}
