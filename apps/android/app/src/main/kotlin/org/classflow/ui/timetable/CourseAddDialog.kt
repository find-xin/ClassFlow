package org.classflow.ui.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.Course
import org.classflow.data.model.CourseSchedule
import org.classflow.data.model.WeekPatternType
import org.classflow.ui.components.GoogleCalendarColorPicker
import org.classflow.ui.components.SearchSuggestionTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseAddDialog(
    semesterId: String,
    initialDayOfWeek: Int = 1,
    initialStartSection: Int = 1,
    initialEndSection: Int = 2,
    existingCourseNames: List<String> = emptyList(),
    existingTeachers: List<String> = emptyList(),
    existingClassrooms: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSaveCourse: (Course) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var classroom by remember { mutableStateOf("") }
    var dayOfWeek by remember { mutableIntStateOf(initialDayOfWeek) }
    var startSection by remember { mutableStateOf(initialStartSection.toString()) }
    var endSection by remember { mutableStateOf(initialEndSection.toString()) }

    // Week range (从第几周到第几周)
    var startWeek by remember { mutableStateOf("1") }
    var endWeek by remember { mutableStateOf("16") }
    var weekPattern by remember { mutableStateOf(WeekPatternType.ALL) } // ALL, ODD, EVEN

    // Color preset
    var selectedColor by remember { mutableStateOf("#3F51B5") }

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
                // 1. Course Name with WakeUp Autocomplete
                SearchSuggestionTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "课程名称 *",
                    placeholder = "例如：高等数学",
                    suggestions = existingCourseNames,
                    leadingIcon = Icons.Default.Book
                )

                // 2. Teacher with WakeUp Autocomplete
                SearchSuggestionTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = "任课教师 (可选)",
                    placeholder = "例如：张教授",
                    suggestions = existingTeachers,
                    leadingIcon = Icons.Default.Person
                )

                // 3. Classroom with WakeUp Autocomplete
                SearchSuggestionTextField(
                    value = classroom,
                    onValueChange = { classroom = it },
                    label = "上课教室 / 地点 (可选)",
                    placeholder = "例如：东上 101",
                    suggestions = existingClassrooms,
                    leadingIcon = Icons.Default.LocationOn
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endSection,
                        onValueChange = { endSection = it.filter { ch -> ch.isDigit() } },
                        label = { Text("结束节次") },
                        shape = RoundedCornerShape(12.dp),
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endWeek,
                        onValueChange = { endWeek = it.filter { ch -> ch.isDigit() } },
                        label = { Text("结束周") },
                        shape = RoundedCornerShape(12.dp),
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

                // 7. Google Calendar Style Color Picker
                Text("卡片颜色", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                GoogleCalendarColorPicker(
                    selectedHex = selectedColor,
                    onColorSelected = { selectedColor = it }
                )
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
                },
                shape = RoundedCornerShape(10.dp)
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
