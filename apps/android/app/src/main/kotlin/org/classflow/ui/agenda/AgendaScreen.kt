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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.classflow.data.model.CalendarEvent
import org.classflow.data.model.Course
import org.classflow.data.model.SectionTime
import org.classflow.data.model.TimelineItem
import org.classflow.ui.components.GoogleCalendarColorPicker
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
    courses: List<Course> = emptyList(),
    sectionTimes: List<SectionTime> = emptyList(),
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
    var showWeekPicker by remember { mutableStateOf(false) }

    val availableCourses = if (courses.isNotEmpty()) courses else remember {
        listOf(
            Course(id = "c_math", semesterId = "sem_1", name = "高等数学", color = "#4F46E5"),
            Course(id = "c_cs", semesterId = "sem_1", name = "数据结构与算法", color = "#0B8043"),
            Course(id = "c_eng", semesterId = "sem_1", name = "大学英语", color = "#E07A5F"),
            Course(id = "c_net", semesterId = "sem_1", name = "计算机网络", color = "#9C27B0")
        )
    }

    val availableSections = if (sectionTimes.isNotEmpty()) sectionTimes else remember {
        (1..12).map { s ->
            SectionTime(s, String.format("%02d:00", 7 + s), String.format("%02d:45", 7 + s))
        }
    }

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

    // Selected date title (compact "M/d" without "年", "月" or weekday)
    val formattedTitle = remember(selectedWeek, selectedDayOfWeek, currentWeek) {
        val cal = Calendar.getInstance()
        val diff = todayWeekday - selectedDayOfWeek
        cal.add(Calendar.DAY_OF_YEAR, -diff + (selectedWeek - currentWeek) * 7)
        val df = SimpleDateFormat("M/d", Locale.CHINESE)
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
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Date (M/d) + Return to Today Capsule (no "年", "月" or weekday, no subtitle)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formattedTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (selectedDayOfWeek != todayWeekday || selectedWeek != currentWeek) {
                    Surface(
                        onClick = {
                            selectedWeek = currentWeek
                            selectedDayOfWeek = todayWeekday
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "今日",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Right: Week Switcher Capsule + "+ 新建" Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Week Switcher Capsule (Tapping opens WeekPickerDialog!)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
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
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Text(
                        text = "第 $selectedWeek 周",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showWeekPicker = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
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
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // "+ 新建" Button (Always pinned and visible!)
                Surface(
                    onClick = { showCreateDialog = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
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
                .padding(vertical = 3.dp)
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

        // 3. Segmented Filter Tabs [全部 | 课程与日程 | 待办清单] (macOS Segmented Style pinned below Day Selector)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AgendaFilter.values().forEach { filter ->
                val isSelected = (selectedFilter == filter)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            } else Modifier
                        )
                        .clickable { selectedFilter = filter }
                        .padding(vertical = 5.5.dp),
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

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Scrollable Content Area
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Next Upcoming Class Hero Card (Shown only when viewing all or schedule, hidden in task mode)
            if (selectedFilter != AgendaFilter.TASK) {
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

    if (showWeekPicker) {
        WeekPickerDialog(
            currentWeek = currentWeek,
            selectedWeek = selectedWeek,
            totalWeeks = 25,
            onWeekSelected = { selectedWeek = it },
            onDismiss = { showWeekPicker = false }
        )
    }

    if (showCreateDialog) {
        CreateAgendaItemDialog(
            courses = availableCourses,
            sectionTimes = availableSections,
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
                if (!task.courseName.isNullOrBlank() || task.priority == "high" || task.priority == "medium") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        task.courseName?.let { cName ->
                            Text(
                                text = cName,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        if (task.priority == "high") {
                            Text(
                                text = "紧急",
                                fontSize = 10.sp,
                                color = Color(0xFFFF5A5F),
                                modifier = Modifier
                                    .background(Color(0xFFFF5A5F).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        } else if (task.priority == "medium") {
                            Text(
                                text = "重要",
                                fontSize = 10.sp,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
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

// -------------------------------------------------------------
// Week Picker Modal Dialog (1..25 Weeks Matrix Jump)
// -------------------------------------------------------------
@Composable
fun WeekPickerDialog(
    currentWeek: Int,
    selectedWeek: Int,
    totalWeeks: Int = 25,
    onWeekSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            shadowElevation = 8.dp,
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择学期周次",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 5 x 5 Grid for 1..25 weeks
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 5) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0 until 5) {
                                val week = row * 5 + col + 1
                                if (week <= totalWeeks) {
                                    val isSelected = (week == selectedWeek)
                                    val isCurrent = (week == currentWeek)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                }
                                            )
                                            .clickable {
                                                onWeekSelected(week)
                                                onDismiss()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$week",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                color = when {
                                                    isSelected -> Color.White
                                                    isCurrent -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (isCurrent && !isSelected) {
                                                Text(
                                                    text = "本周",
                                                    fontSize = 8.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Create Agenda / Task Dialog (macOS ScheduleItemCreateSheet 1:1 Parity)
// -------------------------------------------------------------
@Composable
fun CreateAgendaItemDialog(
    courses: List<Course> = emptyList(),
    sectionTimes: List<SectionTime> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (CalendarEvent) -> Unit
) {
    var isTaskMode by remember { mutableStateOf(false) } // false = 日程, true = 待办

    // Task fields
    var taskTitle by remember { mutableStateOf("") }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var showCourseDropdown by remember { mutableStateOf(false) }
    var taskPriority by remember { mutableStateOf("medium") } // "low", "medium", "high"
    var hasDueDate by remember { mutableStateOf(true) }
    val dfDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var taskDueDateStr by remember {
        mutableStateOf("${dfDate.format(Date())} 18:00")
    }
    var targetMinutes by remember { mutableIntStateOf(45) }
    var targetMinutesText by remember { mutableStateOf("45") }
    var taskNotes by remember { mutableStateOf("") }

    // Event fields
    var eventTitle by remember { mutableStateOf("") }
    val eventColors = listOf(
        "#FF5A5F", "#5B7FB5", "#56A3A6", "#E37B75",
        "#8E7DBE", "#E8A87C", "#7BB38E", "#457B9D"
    )
    var selectedColor by remember { mutableStateOf(eventColors.first()) }
    var eventDateStr by remember { mutableStateOf(dfDate.format(Date())) }
    var isAllDay by remember { mutableStateOf(false) }
    var timeMode by remember { mutableIntStateOf(0) } // 0: 按作息节次, 1: 按具体时间
    var startSection by remember { mutableIntStateOf(1) }
    var endSection by remember { mutableIntStateOf(2) }
    var startTimeStr by remember { mutableStateOf("09:00") }
    var endTimeStr by remember { mutableStateOf("10:30") }

    val frequencyLabels = listOf("不重复", "每天", "工作日 (周一至周五)", "自定义每周某几天", "每周", "每两周", "每月", "学期指定周次")
    var selectedFrequencyIndex by remember { mutableIntStateOf(0) }
    var showFrequencyDropdown by remember { mutableStateOf(false) }
    var customDaysOfWeek by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var customWeeks by remember { mutableStateOf((1..16).toSet()) }

    val alertLabels = listOf("无提醒", "日程开始时", "提前 5 分钟", "提前 15 分钟", "提前 30 分钟", "提前 1 小时", "提前 1 天")
    var selectedAlertIndex by remember { mutableIntStateOf(3) } // 提前 15 分钟
    var showAlertDropdown by remember { mutableStateOf(false) }

    var location by remember { mutableStateOf("") }
    var eventNotes by remember { mutableStateOf("") }

    val canSave = if (isTaskMode) taskTitle.isNotBlank() else eventTitle.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. macOS Modal Top Bar: [取消 | (日程 / 待办) | 添加]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    // Mode Segmented Capsule
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .padding(2.5.dp)
                    ) {
                        listOf(false to "日程", true to "待办").forEach { (isTask, label) ->
                            val isSelected = (isTaskMode == isTask)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .clickable { isTaskMode = isTask }
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

                    Text(
                        text = "添加",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = canSave) {
                                val event = CalendarEvent(
                                    title = if (isTaskMode) taskTitle.trim() else eventTitle.trim(),
                                    isTask = isTaskMode,
                                    startTime = if (!isTaskMode && !isAllDay) {
                                        if (timeMode == 0) (sectionTimes.getOrNull(startSection - 1)?.startTime ?: "08:00") else startTimeStr
                                    } else "08:00",
                                    endTime = if (!isTaskMode && !isAllDay) {
                                        if (timeMode == 0) (sectionTimes.getOrNull(endSection - 1)?.endTime ?: "09:35") else endTimeStr
                                    } else "09:00",
                                    isAllDay = isAllDay,
                                    startSection = if (!isTaskMode && !isAllDay && timeMode == 0) startSection else null,
                                    endSection = if (!isTaskMode && !isAllDay && timeMode == 0) endSection else null,
                                    frequency = if (!isTaskMode) {
                                        when (selectedFrequencyIndex) {
                                            0 -> "none"
                                            1 -> "daily"
                                            2 -> "workdays"
                                            3 -> "customDays"
                                            4 -> "weekly"
                                            5 -> "biweekly"
                                            6 -> "monthly"
                                            7 -> "customWeeks"
                                            else -> "none"
                                        }
                                    } else "none",
                                    customDaysOfWeek = if (!isTaskMode && selectedFrequencyIndex == 3) customDaysOfWeek.toList() else emptyList(),
                                    customWeeks = if (!isTaskMode && selectedFrequencyIndex == 7) customWeeks.toList() else emptyList(),
                                    location = location.trim().ifEmpty { null },
                                    description = if (isTaskMode) taskNotes.trim().ifEmpty { null } else eventNotes.trim().ifEmpty { null },
                                    color = if (isTaskMode) (selectedCourse?.color ?: "#4F46E5") else selectedColor,
                                    courseId = selectedCourse?.id,
                                    courseName = selectedCourse?.name,
                                    priority = if (isTaskMode) taskPriority else "medium",
                                    dueDate = if (isTaskMode && hasDueDate) taskDueDateStr else null,
                                    targetMinutes = if (isTaskMode) (targetMinutesText.toIntOrNull()?.coerceIn(1, 480) ?: targetMinutes) else null
                                )
                                onCreate(event)
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 2. Scrollable Body with Inset Grouped Cards
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isTaskMode) {
                        // ------------------------------------
                        // TASK FORM (待办 1:1 macOS Parity)
                        // ------------------------------------

                        // Card 1: Task Title
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(14.dp)) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = taskTitle,
                                    onValueChange = { taskTitle = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
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

                        // Card 2: 关联课程 & 优先级 & 截止时间
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 关联课程 (Course Linkage)
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
                                            onClick = { showCourseDropdown = true },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                selectedCourse?.let { c ->
                                                    val cColor = try { Color(android.graphics.Color.parseColor(c.color)) } catch (e: Throwable) { MaterialTheme.colorScheme.primary }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(cColor)
                                                    )
                                                }
                                                Text(
                                                    text = selectedCourse?.name ?: "无关联课程",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (selectedCourse != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showCourseDropdown,
                                            onDismissRequest = { showCourseDropdown = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("无关联课程", fontSize = 13.sp) },
                                                onClick = {
                                                    selectedCourse = null
                                                    showCourseDropdown = false
                                                }
                                            )
                                            courses.forEach { c ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            val dotColor = try { Color(android.graphics.Color.parseColor(c.color)) } catch (e: Throwable) { MaterialTheme.colorScheme.primary }
                                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                                                            Text(c.name, fontSize = 13.sp)
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedCourse = c
                                                        showCourseDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // 优先级 (Priority)
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
                                            .background(
                                                MaterialTheme.colorScheme.surface,
                                                CircleShape
                                            )
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

                                // 设置截止时间 (Due Date)
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
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Text(
                                                text = taskDueDateStr,
                                                fontSize = 11.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: 预计专注时长 (Focus Estimation)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
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
                                        IconButton(
                                            onClick = {
                                                val curr = targetMinutesText.toIntOrNull() ?: targetMinutes
                                                if (curr > 5) {
                                                    val next = (curr - 5).coerceAtLeast(5)
                                                    targetMinutes = next
                                                    targetMinutesText = next.toString()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text(
                                                text = "−",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                            ) {
                                                BasicTextField(
                                                    value = targetMinutesText,
                                                    onValueChange = { newStr ->
                                                        val filtered = newStr.filter { it.isDigit() }.take(3)
                                                        targetMinutesText = filtered
                                                        filtered.toIntOrNull()?.let { valMins ->
                                                            if (valMins in 1..480) {
                                                                targetMinutes = valMins
                                                            }
                                                        }
                                                    },
                                                    singleLine = true,
                                                    textStyle = TextStyle(
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        textAlign = TextAlign.Center
                                                    ),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.width(36.dp)
                                                )
                                                Text(
                                                    text = "分钟",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                val curr = targetMinutesText.toIntOrNull() ?: targetMinutes
                                                if (curr < 480) {
                                                    val next = (curr + 5).coerceAtMost(480)
                                                    targetMinutes = next
                                                    targetMinutesText = next.toString()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "加",
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Quick presets
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(15, 25, 30, 45, 60, 90).forEach { mins ->
                                        val isSelected = (targetMinutes == mins)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                                )
                                                .border(
                                                    0.5.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    targetMinutes = mins
                                                    targetMinutesText = mins.toString()
                                                }
                                                .padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${mins}m",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card 4: 待办详情与备忘说明
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                androidx.compose.foundation.text.BasicTextField(
                                    value = taskNotes,
                                    onValueChange = { taskNotes = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (taskNotes.isEmpty()) {
                                            Text(
                                                text = "待办详情与备忘说明 (选填)",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // ------------------------------------
                        // EVENT FORM (日程 1:1 macOS Parity)
                        // ------------------------------------

                        // Card 1: Title & Color Picker
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = eventTitle,
                                        onValueChange = { eventTitle = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
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

                                    val currColor = try { Color(android.graphics.Color.parseColor(selectedColor)) } catch (e: Throwable) { Color(0xFFFF5A5F) }
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(currColor)
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Color Palette Row with GoogleCalendarColorPicker (Presets + Custom Color Dialog with Hex & Hue)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "标记颜色",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    GoogleCalendarColorPicker(
                                        selectedHex = selectedColor,
                                        onColorSelected = { selectedColor = it }
                                    )
                                }
                            }
                        }

                        // Card 2: Date & Time Configuration
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Row 1: Date
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "日期",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(
                                            0.5.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Text(
                                            text = eventDateStr,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Row 2: All-Day Switch (Distinct row with no overlap)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
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

                                if (!isAllDay) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                    // Time Mode (按作息节次 vs 按具体时间)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "时间模式",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            listOf(0 to "按作息节次", 1 to "按具体时间").forEach { (tMode, tLabel) ->
                                                val isSelected = (timeMode == tMode)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                        .clickable { timeMode = tMode }
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

                                    if (timeMode == 0) {
                                        // By Section
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "节次范围",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    onClick = { if (startSection > 1) startSection-- },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        text = "第 $startSection 节",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }

                                                Text("至", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                                Surface(
                                                    onClick = { if (endSection < 12) endSection++ },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        text = "第 $endSection 节",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // By Exact Time
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "具体时间",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                ) {
                                                    androidx.compose.foundation.text.BasicTextField(
                                                        value = startTimeStr,
                                                        onValueChange = { startTimeStr = it },
                                                        singleLine = true,
                                                        textStyle = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 12.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                        ),
                                                        modifier = Modifier
                                                            .width(60.dp)
                                                            .padding(vertical = 4.dp)
                                                    )
                                                }

                                                Text("至", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                ) {
                                                    androidx.compose.foundation.text.BasicTextField(
                                                        value = endTimeStr,
                                                        onValueChange = { endTimeStr = it },
                                                        singleLine = true,
                                                        textStyle = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 12.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                        ),
                                                        modifier = Modifier
                                                            .width(60.dp)
                                                            .padding(vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: Recurrence & Reminder
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Frequency
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
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
                                            onClick = { showFrequencyDropdown = true },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = frequencyLabels[selectedFrequencyIndex],
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showFrequencyDropdown,
                                            onDismissRequest = { showFrequencyDropdown = false }
                                        ) {
                                            frequencyLabels.forEachIndexed { idx, fLabel ->
                                                DropdownMenuItem(
                                                    text = { Text(fLabel, fontSize = 13.sp) },
                                                    onClick = {
                                                        selectedFrequencyIndex = idx
                                                        showFrequencyDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (selectedFrequencyIndex == 3) {
                                    // Custom days of week pills
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日").forEach { (dInt, dLabel) ->
                                            val isSel = customDaysOfWeek.contains(dInt)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(CircleShape)
                                                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CircleShape)
                                                    .clickable {
                                                        customDaysOfWeek = if (isSel) {
                                                            if (customDaysOfWeek.size > 1) customDaysOfWeek - dInt else customDaysOfWeek
                                                        } else customDaysOfWeek + dInt
                                                    }
                                                    .padding(vertical = 5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dLabel,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                // Reminder Notification
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "提醒通知",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Box {
                                        Surface(
                                            onClick = { showAlertDropdown = true },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = alertLabels[selectedAlertIndex],
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showAlertDropdown,
                                            onDismissRequest = { showAlertDropdown = false }
                                        ) {
                                            alertLabels.forEachIndexed { idx, aLabel ->
                                                DropdownMenuItem(
                                                    text = { Text(aLabel, fontSize = 13.sp) },
                                                    onClick = {
                                                        selectedAlertIndex = idx
                                                        showAlertDropdown = false
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
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = location,
                                        onValueChange = { location = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (location.isEmpty()) {
                                                Text(
                                                    text = "地点 / 线上会议房间号 (选填)",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = eventNotes,
                                        onValueChange = { eventNotes = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (eventNotes.isEmpty()) {
                                                Text(
                                                    text = "备注说明 (选填)",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.fillMaxWidth()
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
