package org.classflow.ui.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.classflow.data.model.CalendarEvent
import org.classflow.ui.components.GoogleCalendarColorPicker
import java.text.SimpleDateFormat
import java.util.*

/**
 * Schedule item creation dialog matching macOS ScheduleItemCreateSheet:
 * - Allows adding schedules at ANY time of day (including early morning before section 1 or late evening after section 12).
 * - Location, repeating frequency, and Google Calendar style color picker.
 */
@Composable
fun ScheduleAddDialog(
    initialDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    initialStartTime: String = "07:30",
    initialEndTime: String = "08:30",
    onDismiss: () -> Unit,
    onSaveSchedule: (CalendarEvent) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var startTime by remember { mutableStateOf(initialStartTime) }
    var endTime by remember { mutableStateOf(initialEndTime) }
    var frequency by remember { mutableStateOf("不重复") }
    var color by remember { mutableStateOf("#3F51B5") }
    var description by remember { mutableStateOf("") }

    val frequencies = listOf("不重复", "每天", "每周", "每两周")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("添加日程", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("日程标题 *") },
                    placeholder = { Text("例如：晨读、社团会议、晚自习") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点 (可选)") },
                    placeholder = { Text("例如：图书馆二楼、学生活动中心") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("开始时间") },
                        placeholder = { Text("07:30") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("结束时间") },
                        placeholder = { Text("08:30") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Repeating Frequency
                Text("重复周期", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    frequencies.forEach { freq ->
                        FilterChip(
                            selected = frequency == freq,
                            onClick = { frequency = freq },
                            label = { Text(freq) }
                        )
                    }
                }

                // Color Selection
                Text("日程颜色", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                GoogleCalendarColorPicker(
                    selectedHex = color,
                    onColorSelected = { color = it }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注说明 (可选)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val newEvent = CalendarEvent(
                            title = title.trim(),
                            startTime = startTime.trim(),
                            endTime = endTime.trim(),
                            location = location.trim().ifEmpty { null },
                            description = description.trim().ifEmpty { null },
                            color = color,
                            frequency = frequency,
                            isTask = false
                        )
                        onSaveSchedule(newEvent)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("保存日程")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
