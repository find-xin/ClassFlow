package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.Course
import org.classflow.data.model.CourseSchedule
import org.classflow.data.model.WeekPatternType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseAddDialog(
    semesterId: String,
    existingCourseNames: List<String> = emptyList(),
    existingTeachers: List<String> = emptyList(),
    existingClassrooms: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSaveCourse: (Course) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var classroom by remember { mutableStateOf("") }
    var dayOfWeek by remember { mutableStateOf(1) } // 1: Mon ... 7: Sun
    var startSection by remember { mutableStateOf("1") }
    var endSection by remember { mutableStateOf("2") }

    // Week range (从第几周到第几周)
    var startWeek by remember { mutableStateOf("1") }
    var endWeek by remember { mutableStateOf("16") }
    var weekPattern by remember { mutableStateOf(WeekPatternType.ALL) } // ALL, ODD, EVEN

    // Color preset
    val colorPresets = listOf("#4A90E2", "#5B7FB5", "#059669", "#E07A5F", "#9C27B0", "#FF5A5F", "#2A9D8F", "#3B82F6")
    var selectedColor by remember { mutableStateOf(colorPresets.first()) }

    // Autocomplete filtered suggestions
    val nameSuggestions = remember(name, existingCourseNames) {
        if (name.isBlank()) existingCourseNames.take(4)
        else existingCourseNames.filter { it.contains(name, ignoreCase = true) && it != name }.take(4)
    }

    val teacherSuggestions = remember(teacher, existingTeachers) {
        if (teacher.isBlank()) existingTeachers.take(4)
        else existingTeachers.filter { it.contains(teacher, ignoreCase = true) && it != teacher }.take(4)
    }

    val classroomSuggestions = remember(classroom, existingClassrooms) {
        if (classroom.isBlank()) existingClassrooms.take(4)
        else existingClassrooms.filter { it.contains(classroom, ignoreCase = true) && it != classroom }.take(4)
    }

    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("添加课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Course Name with Autocomplete Suggestions
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("课程名称 *") },
                        placeholder = { Text("例如：高等数学") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (nameSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            nameSuggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { name = suggestion },
                                    label = { Text(suggestion, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // 2. Teacher with Autocomplete Suggestions
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("任课教师 (可选)") },
                        placeholder = { Text("例如：张教授") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (teacherSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            teacherSuggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { teacher = suggestion },
                                    label = { Text(suggestion, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // 3. Classroom with Autocomplete Suggestions
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = classroom,
                        onValueChange = { classroom = it },
                        label = { Text("上课教室 / 地点 (可选)") },
                        placeholder = { Text("例如：东上 101") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (classroomSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            classroomSuggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { classroom = suggestion },
                                    label = { Text(suggestion, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 4. Weekday Selector
                Text("上课星期", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayNames.forEachIndexed { index, dayName ->
                        val dayVal = index + 1
                        FilterChip(
                            selected = dayOfWeek == dayVal,
                            onClick = { dayOfWeek = dayVal },
                            label = { Text(dayName, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 5. Section range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startSection,
                        onValueChange = { startSection = it.filter { ch -> ch.isDigit() } },
                        label = { Text("起始节次") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endSection,
                        onValueChange = { endSection = it.filter { ch -> ch.isDigit() } },
                        label = { Text("结束节次") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // 6. Week range (从第几周到第几周) & Single/Double week toggle
                Text("上课周次范围", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startWeek,
                        onValueChange = { startWeek = it.filter { ch -> ch.isDigit() } },
                        label = { Text("起始周") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endWeek,
                        onValueChange = { endWeek = it.filter { ch -> ch.isDigit() } },
                        label = { Text("结束周") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = weekPattern == WeekPatternType.ALL,
                        onClick = { weekPattern = WeekPatternType.ALL },
                        label = { Text("每周") }
                    )
                    FilterChip(
                        selected = weekPattern == WeekPatternType.ODD,
                        onClick = { weekPattern = WeekPatternType.ODD },
                        label = { Text("单周") }
                    )
                    FilterChip(
                        selected = weekPattern == WeekPatternType.EVEN,
                        onClick = { weekPattern = WeekPatternType.EVEN },
                        label = { Text("双周") }
                    )
                }

                // 7. Card color selection
                Text("卡片颜色", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colorPresets.forEach { hex ->
                        val col = try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (e: Throwable) {
                            Color.Blue
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(col)
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == hex) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val sSec = startSection.toIntOrNull() ?: 1
                        val eSec = endSection.toIntOrNull() ?: maxOf(sSec, 2)
                        val sWk = startWeek.toIntOrNull() ?: 1
                        val eWk = endWeek.toIntOrNull() ?: 16

                        val customWeeksList = (sWk..eWk).filter { wk ->
                            when (weekPattern) {
                                WeekPatternType.ALL -> true
                                WeekPatternType.ODD -> wk % 2 != 0
                                WeekPatternType.EVEN -> wk % 2 == 0
                                else -> true
                            }
                        }

                        val schedule = CourseSchedule(
                            dayOfWeek = dayOfWeek,
                            startSection = sSec,
                            endSection = eSec,
                            classroom = classroom.trim().ifEmpty { null },
                            weekPattern = weekPattern,
                            customWeeks = customWeeksList
                        )

                        val newCourse = Course(
                            semesterId = semesterId,
                            name = name.trim(),
                            teacher = teacher.trim().ifEmpty { null },
                            color = selectedColor,
                            schedules = listOf(schedule)
                        )

                        onSaveCourse(newCourse)
                    }
                }
            ) {
                Text("保存课程")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
