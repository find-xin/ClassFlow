package org.classflow.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.CalendarEvent
import org.classflow.data.model.TimelineItem
import java.text.SimpleDateFormat
import java.util.*

enum class AgendaFilter(val label: String) {
    ALL("全部"),
    SCHEDULE("课程与日程"),
    TASK("待办清单")
}

@Composable
fun AgendaScreen(
    todayOccurrences: List<TimelineItem> = emptyList(),
    scheduleEvents: List<CalendarEvent> = emptyList(),
    currentWeek: Int = 1,
    onCourseClick: (TimelineItem) -> Unit = {},
    onScheduleClick: (CalendarEvent) -> Unit = {},
    onStartFocus: (title: String, courseName: String?) -> Unit = { _, _ -> }
) {
    var selectedWeek by remember { mutableIntStateOf(currentWeek) }
    val todayWeekday = remember {
        val cal = Calendar.getInstance()
        val w = cal.get(Calendar.DAY_OF_WEEK)
        if (w == Calendar.SUNDAY) 7 else w - 1
    }
    var selectedDayOfWeek by remember { mutableIntStateOf(todayWeekday) }
    var selectedFilter by remember { mutableStateOf(AgendaFilter.ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // Calculate dates for current view (Monday to Sunday)
    val dayDates = remember(selectedWeek, currentWeek) {
        val cal = Calendar.getInstance()
        val diff = todayWeekday - 1
        cal.add(Calendar.DAY_OF_YEAR, -diff + (selectedWeek - currentWeek) * 7)
        val df = SimpleDateFormat("M/d", Locale.CHINESE)
        (1..7).map {
            val dStr = df.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            dStr
        }
    }

    // Selected date title
    val formattedTitle = remember(selectedWeek, selectedDayOfWeek, currentWeek) {
        val cal = Calendar.getInstance()
        val diff = todayWeekday - selectedDayOfWeek
        cal.add(Calendar.DAY_OF_YEAR, -diff + (selectedWeek - currentWeek) * 7)
        val df = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE)
        df.format(cal.time)
    }

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

    // Courses for selected day
    val selectedDayCourses = remember(todayOccurrences, selectedDayOfWeek, selectedWeek) {
        todayOccurrences.filter {
            it.dayOfWeek == selectedDayOfWeek && it.weekNumber == selectedWeek
        }.sortedBy { it.startSection }
    }

    // Next upcoming course hero logic
    val nextUpcomingCourse = remember(todayOccurrences, todayWeekday, currentWeek) {
        val todayCourses = todayOccurrences.filter {
            it.dayOfWeek == todayWeekday && it.weekNumber == currentWeek
        }.sortedBy { it.startSection }

        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        todayCourses.firstOrNull { item ->
            val start = item.startTime
            if (start != null && start.contains(":")) {
                val parts = start.split(":")
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                val itemMinutes = h * 60 + m
                itemMinutes >= currentMinutes - 20
            } else {
                false
            }
        } ?: todayCourses.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 1. Top Header (Date Title, Week Navigation & Actions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formattedTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "日程规划与待办专注",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Week Switcher Capsule
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    IconButton(
                        onClick = { if (selectedWeek > 1) selectedWeek-- },
                        modifier = Modifier.size(24.dp),
                        enabled = selectedWeek > 1
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "上一周",
                            tint = if (selectedWeek > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "第 $selectedWeek 周",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = { if (selectedWeek < 25) selectedWeek++ },
                        modifier = Modifier.size(24.dp),
                        enabled = selectedWeek < 25
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "下一周",
                            tint = if (selectedWeek < 25) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // "回到今日" Capsule
                if (selectedDayOfWeek != todayWeekday || selectedWeek != currentWeek) {
                    Surface(
                        onClick = {
                            selectedWeek = currentWeek
                            selectedDayOfWeek = todayWeekday
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "今日",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }

                // "+ 新建" Button
                Surface(
                    onClick = { showCreateDialog = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("新建", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 2. Day Selector Horizontal Strip (周一 ~ 周日 with Date badge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            (1..7).forEach { day ->
                val isSelected = (day == selectedDayOfWeek)
                val isToday = (day == todayWeekday && selectedWeek == currentWeek)
                val dateLabel = if (day - 1 in dayDates.indices) dayDates[day - 1] else ""

                val bgModifier = if (isSelected) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(9.dp)
                        )
                } else if (isToday) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(9.dp)
                    )
                } else {
                    Modifier
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(bgModifier)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { selectedDayOfWeek = day }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = dayNames[day - 1],
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else (if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = dateLabel,
                        fontSize = 9.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Scrollable Content Area
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Next Upcoming Class Hero Card
            item {
                if (nextUpcomingCourse != null && selectedWeek == currentWeek && selectedDayOfWeek == todayWeekday) {
                    NextClassHeroCard(
                        item = nextUpcomingCourse,
                        onStartFocus = { onStartFocus(nextUpcomingCourse.title, nextUpcomingCourse.title) },
                        onClick = { onCourseClick(nextUpcomingCourse) }
                    )
                } else {
                    NoUpcomingHeroCard(
                        dayName = dayNames[selectedDayOfWeek - 1],
                        dateStr = if (selectedDayOfWeek - 1 in dayDates.indices) dayDates[selectedDayOfWeek - 1] else ""
                    )
                }
            }

            // Segmented Filter Capsule
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AgendaFilter.values().forEach { filter ->
                        val isSelected = (selectedFilter == filter)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            0.5.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                    } else Modifier
                                )
                                .clickable { selectedFilter = filter }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Courses for the selected day
            if (selectedFilter == AgendaFilter.ALL || selectedFilter == AgendaFilter.SCHEDULE) {
                if (selectedDayCourses.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "当日课程 (${selectedDayCourses.size} 节)",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    items(selectedDayCourses, key = { it.id }) { item ->
                        AgendaCourseCard(
                            item = item,
                            onClick = { onCourseClick(item) }
                        )
                    }
                }

                // Schedules (Calendar Events)
                if (calendarEvents.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "当日日程 (${calendarEvents.size} 项)",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    items(calendarEvents, key = { it.id }) { event ->
                        AgendaEventCard(
                            event = event,
                            onDelete = { calendarEvents.remove(event) }
                        )
                    }
                }
            }

            // Checklist Tasks
            if (selectedFilter == AgendaFilter.ALL || selectedFilter == AgendaFilter.TASK) {
                item {
                    val incompletedCount = taskItems.count { !it.isCompleted }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "待办事项 (未完成 $incompletedCount / 共 ${taskItems.size})",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(taskItems, key = { it.id }) { task ->
                    TaskChecklistItem(
                        task = task,
                        onToggle = {
                            val idx = taskItems.indexOf(task)
                            if (idx >= 0) {
                                taskItems[idx] = task.copy(isCompleted = !task.isCompleted)
                            }
                        },
                        onDelete = { taskItems.remove(task) },
                        onStartFocus = { onStartFocus(task.title, null) }
                    )
                }
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
private fun NextClassHeroCard(
    item: TimelineItem,
    onStartFocus: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2563EB).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF2563EB), CircleShape)
                        )
                        Text(
                            text = "下一节课",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                    }
                }

                val timeSpan = if (!item.startTime.isNullOrBlank() && !item.endTime.isNullOrBlank()) {
                    "${item.startTime} - ${item.endTime}"
                } else {
                    "第 ${item.startSection}-${item.endSection} 节"
                }

                Text(
                    text = timeSpan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = item.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!item.location.isNullOrBlank()) {
                        Text(
                            text = "@${item.location}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!item.teacher.isNullOrBlank()) {
                        Text(
                            text = item.teacher,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Surface(
                    onClick = onStartFocus,
                    shape = CircleShape,
                    color = Color(0xFF2563EB)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Text("进入专注", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoUpcomingHeroCard(
    dayName: String,
    dateStr: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$dayName · 无剩余待上课程",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "自律给自由，专注致远方。把握课余时间完成学业目标！",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun TaskChecklistItem(
    task: CalendarEvent,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onStartFocus: () -> Unit = {}
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular toggle button matching macOS
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.isCompleted) Color(0xFF2563EB) else Color.Transparent
                    )
                    .border(
                        width = if (task.isCompleted) 0.dp else 1.5.dp,
                        color = if (task.isCompleted) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                if (!task.description.isNullOrBlank()) {
                    Text(
                        text = task.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }

            if (!task.isCompleted) {
                Surface(
                    onClick = onStartFocus,
                    shape = CircleShape,
                    color = Color(0xFF2563EB).copy(alpha = 0.10f),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(11.dp))
                        Text("专注", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(15.dp)
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

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeText = if (event.isAllDay) "全天日程" else "${event.startTime} - ${event.endTime}"
                    Text(
                        text = timeText,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (event.frequency != "none" && event.frequency.isNotBlank()) {
                        Text(
                            text = "· ${event.frequency}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!event.location.isNullOrBlank()) {
                    Text(
                        text = "@${event.location}",
                        fontSize = 11.5.sp,
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(15.dp)
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

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                val timeSpan = if (!item.startTime.isNullOrBlank() && !item.endTime.isNullOrBlank()) {
                    "${item.startTime} - ${item.endTime} (第${item.startSection}-${item.endSection}节)"
                } else {
                    "第${item.startSection}-${item.endSection}节"
                }
                Text(
                    text = timeSpan,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.location.isNullOrBlank()) {
                    Text(
                        text = "@${item.location}",
                        fontSize = 11.5.sp,
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
    var isTaskMode by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(false) }

    var useSectionMode by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("10:00") }
    var startSection by remember { mutableStateOf("1") }
    var endSection by remember { mutableStateOf("2") }

    val frequencyOptions = listOf("不重复", "每天", "每周", "工作日", "自定义每周几天")
    var selectedFrequency by remember { mutableStateOf("不重复") }

    val colorPresets = listOf("#4A90E2", "#5B7FB5", "#059669", "#E07A5F", "#9C27B0", "#FF5A5F", "#2A9D8F")
    var selectedColor by remember { mutableStateOf(colorPresets.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (!isTaskMode) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { isTaskMode = false }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "日程",
                        fontSize = 13.sp,
                        fontWeight = if (!isTaskMode) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isTaskMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isTaskMode) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { isTaskMode = true }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "待办",
                        fontSize = 13.sp,
                        fontWeight = if (isTaskMode) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTaskMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

                Text("标签颜色", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                org.classflow.ui.components.GoogleCalendarColorPicker(
                    selectedHex = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

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
