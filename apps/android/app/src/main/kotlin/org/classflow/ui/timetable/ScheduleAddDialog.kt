package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    isDark: Boolean = false,
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

    val frequencies = listOf("不重复", "每天", "每周", "双周")

    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFFA1A3AC) else Color(0xFF6B7280)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
        unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color(0xFFD1D5DB),
        focusedContainerColor = if (isDark) Color(0xFF262836) else Color(0xFFF9FAFB),
        unfocusedContainerColor = if (isDark) Color(0xFF262836) else Color(0xFFF3F4F6),
        focusedTextColor = if (isDark) Color.White else Color(0xFF1F2937),
        unfocusedTextColor = if (isDark) Color.White else Color(0xFF1F2937),
        focusedLabelColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
        unfocusedLabelColor = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF6B7280)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
        title = {
            Text("添加日程", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = textPrimary)
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
                    colors = textFieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点 (可选)") },
                    placeholder = { Text("例如：图书馆二楼、学生活动中心") },
                    singleLine = true,
                    colors = textFieldColors,
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
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("结束时间") },
                        placeholder = { Text("08:30") },
                        singleLine = true,
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Repeating Frequency
                Text("重复周期", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    frequencies.forEach { freq ->
                        val isSel = (frequency == freq)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSel) Color(0xFF4F46E5)
                                    else if (isDark) Color(0xFF282B3C)
                                    else Color(0xFFF3F4F6)
                                )
                                .clickable { frequency = freq }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = freq,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else textSecondary
                            )
                        }
                    }
                }

                // Color Selection
                Text("日程颜色", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)
                GoogleCalendarColorPicker(
                    selectedHex = color,
                    onColorSelected = { color = it }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注说明 (可选)") },
                    colors = textFieldColors,
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
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("保存日程", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = textSecondary)
            }
        }
    )
}
