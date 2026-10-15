package org.classflow.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.CalendarEvent
import org.classflow.data.model.TimelineItem
import java.text.SimpleDateFormat
import java.util.*

enum class AgendaFilter(val label: String) {
    ALL("全部"),
    SCHEDULE("日程"),
    TASK("待办")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    todayOccurrences: List<TimelineItem>,
    onCourseClick: (TimelineItem) -> Unit,
    onStartFocus: (title: String, courseName: String?) -> Unit = { _, _ -> }
) {
    var selectedFilter by remember { mutableStateOf(AgendaFilter.ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Sample Calendar Events
    val calendarEvents = remember {
        mutableStateListOf(
            CalendarEvent(
                id = "evt_1",
                title = "高等代数辅导答疑",
                startTime = "14:00",
                endTime = "15:30",
                location = "数学学院 304",
                color = "#4A90E2",
                frequency = "每周"
            ),
            CalendarEvent(
                id = "evt_2",
                title = "系学术前沿讲座",
                startTime = "16:00",
                endTime = "17:30",
                location = "学生活动中心大厅",
                color = "#9C27B0",
                frequency = "none"
            )
        )
    }

    // Sample Tasks (Checklist ToDos)
    val taskItems = remember {
        mutableStateListOf(
            CalendarEvent(
                id = "task_1",
                title = "高等数学 · 课后习题第4章巩固",
                isTask = true,
                isCompleted = false,
                color = "#4A90E2"
            ),
            CalendarEvent(
                id = "task_2",
                title = "大学英语六级 · 听力真题一篇精听",
                isTask = true,
                isCompleted = true,
                color = "#059669"
            ),
            CalendarEvent(
                id = "task_3",
                title = "数据结构 · 实验报告整理提交",
                isTask = true,
                isCompleted = false,
                color = "#E07A5F"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val df = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINESE) }
                        Text(
                            text = df.format(Date()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "今日安排与待办清单",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Filter Pills Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AgendaFilter.values().forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            // 1. Courses Section
            if (selectedFilter == AgendaFilter.ALL || selectedFilter == AgendaFilter.SCHEDULE) {
                if (todayOccurrences.isNotEmpty()) {
                    item {
                        Text(
                            text = "今日课程 (${todayOccurrences.size}节)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(todayOccurrences) { item ->
                        AgendaCourseCard(
                            item = item,
                            onClick = { onCourseClick(item) }
                        )
                    }
                }

                // 2. Schedule Events Section
                if (calendarEvents.isNotEmpty()) {
                    item {
                        Text(
                            text = "今日日程 (${calendarEvents.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(calendarEvents) { event ->
                        AgendaEventCard(
                            event = event,
                            onDelete = { calendarEvents.remove(event) }
                        )
                    }
                }
            }

            // 3. Checklist Tasks Section
            if (selectedFilter == AgendaFilter.ALL || selectedFilter == AgendaFilter.TASK) {
                item {
                    val incompletedCount = taskItems.count { !it.isCompleted }
                    Text(
                        text = "待办事项 (未完成: $incompletedCount / 共: ${taskItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(taskItems) { task ->
                    TaskChecklistItem(
                        task = task,
                        onToggle = {
                            val idx = taskItems.indexOf(task)
                            if (idx >= 0) {
                                taskItems[idx] = task.copy(isCompleted = !task.isCompleted)
                            }
                        },
                        onDelete = { taskItems.remove(task) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showCreateDialog) {
        CreateAgendaItemDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { event ->
                if (event.isTask) {
                    taskItems.add(0, event)
                } else {
                    calendarEvents.add(0, event)
                }
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun TaskChecklistItem(
    task: CalendarEvent,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                )
                if (!task.description.isNullOrBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AgendaEventCard(
    event: CalendarEvent,
    onDelete: () -> Unit
) {
    val barColor = try {
        Color(android.graphics.Color.parseColor(event.color))
    } catch (e: Throwable) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeText = if (event.isAllDay) "全天日程" else "${event.startTime} - ${event.endTime}"
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (event.frequency != "none" && event.frequency.isNotBlank()) {
                        Text(
                            text = "· ${event.frequency}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!event.location.isNullOrBlank()) {
                    Text(
                        text = "@${event.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AgendaCourseCard(
    item: TimelineItem,
    onClick: () -> Unit
) {
    val barColor = try {
        Color(android.graphics.Color.parseColor(item.color))
    } catch (e: Throwable) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                val timeSpan = if (!item.startTime.isNullOrBlank() && !item.endTime.isNullOrBlank()) {
                    "${item.startTime} - ${item.endTime} (第${item.startSection}-${item.endSection}节)"
                } else {
                    "第${item.startSection}-${item.endSection}节"
                }
                Text(
                    text = timeSpan,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.location.isNullOrBlank()) {
                    Text(
                        text = "@${item.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAgendaItemDialog(
    onDismiss: () -> Unit,
    onCreate: (CalendarEvent) -> Unit
) {
    var isTaskMode by remember { mutableStateOf(false) } // false: 日程 (Event), true: 待办 (Task)
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(false) }

    // Time settings
    var useSectionMode by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("10:00") }
    var startSection by remember { mutableStateOf("1") }
    var endSection by remember { mutableStateOf("2") }

    // Frequency
    val frequencyOptions = listOf("不重复", "每天", "每周", "工作日", "自定义每周几天")
    var selectedFrequency by remember { mutableStateOf("不重复") }
    val customDays = remember { mutableStateListOf<Int>() }

    // Color preset
    val colorPresets = listOf("#4A90E2", "#5B7FB5", "#059669", "#E07A5F", "#9C27B0", "#FF5A5F", "#2A9D8F")
    var selectedColor by remember { mutableStateOf(colorPresets.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isTaskMode,
                    onClick = { isTaskMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("日程")
                }
                SegmentedButton(
                    selected = isTaskMode,
                    onClick = { isTaskMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("待办")
                }
            }
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
                    label = { Text(if (isTaskMode) "待办标题" else "日程标题") },
                    placeholder = { Text(if (isTaskMode) "例如：完成高数作业第3节" else "例如：导师科研会议") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (!isTaskMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("全天日程", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
                    }

                    if (!isAllDay) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("设定时间方式", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = !useSectionMode,
                                    onClick = { useSectionMode = false },
                                    label = { Text("具体时间") }
                                )
                                FilterChip(
                                    selected = useSectionMode,
                                    onClick = { useSectionMode = true },
                                    label = { Text("节次") }
                                )
                            }
                        }

                        if (!useSectionMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = startTime,
                                    onValueChange = { startTime = it },
                                    label = { Text("开始时间") },
                                    placeholder = { Text("09:00") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = endTime,
                                    onValueChange = { endTime = it },
                                    label = { Text("结束时间") },
                                    placeholder = { Text("10:00") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = startSection,
                                    onValueChange = { startSection = it },
                                    label = { Text("起始节") },
                                    placeholder = { Text("1") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = endSection,
                                    onValueChange = { endSection = it },
                                    label = { Text("结束节") },
                                    placeholder = { Text("2") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    // Frequency selector
                    Text("重复频次", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        frequencyOptions.take(4).forEach { freq ->
                            FilterChip(
                                selected = selectedFrequency == freq,
                                onClick = { selectedFrequency = freq },
                                label = { Text(freq, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("地点 (可选)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Color picker
                Text("标签颜色", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
                                .clickable { selectedColor = hex }
                                .then(
                                    if (selectedColor == hex) Modifier.padding(2.dp) else Modifier
                                ),
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

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注说明 (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(
                            CalendarEvent(
                                title = title.trim(),
                                isTask = isTaskMode,
                                startTime = startTime.trim(),
                                endTime = endTime.trim(),
                                isAllDay = isAllDay,
                                location = location.trim().ifEmpty { null },
                                description = description.trim().ifEmpty { null },
                                color = selectedColor,
                                startSection = startSection.toIntOrNull(),
                                endSection = endSection.toIntOrNull(),
                                frequency = if (selectedFrequency == "不重复") "none" else selectedFrequency
                            )
                        )
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
