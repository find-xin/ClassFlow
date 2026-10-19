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
import androidx.compose.ui.draw.alpha
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
import org.classflow.data.model.ScheduleException
import org.classflow.data.model.SectionTime
import org.classflow.data.model.StudyTask
import org.classflow.data.model.TimelineItem
import org.classflow.data.local.TimelineProjection
import org.classflow.ui.components.GoogleCalendarColorPicker
import org.classflow.ui.timetable.ScheduleAddDialog
import java.text.SimpleDateFormat
import java.util.*

enum class AgendaTaskFilter(val label: String) {
    IN_PROGRESS("待办中"),
    ALL("全部"),
    COMPLETED("已完成")
}

@Composable
fun AgendaScreen(
    todayOccurrences: List<TimelineItem> = emptyList(),
    scheduleEvents: List<CalendarEvent> = emptyList(),
    courses: List<Course> = emptyList(),
    sectionTimes: List<SectionTime> = emptyList(),
    exceptions: List<ScheduleException> = emptyList(),
    semesterStartDate: String = "2026-09-01",
    currentWeek: Int = 1,
    isDark: Boolean = false,
    tasks: List<StudyTask> = emptyList(),
    onToggleTask: (StudyTask) -> Unit = {},
    onDeleteTask: (StudyTask) -> Unit = {},
    onAddTask: (StudyTask) -> Unit = {},
    onAddSchedule: (CalendarEvent) -> Unit = {},
    onCourseClick: (TimelineItem) -> Unit = {},
    onScheduleClick: (CalendarEvent) -> Unit = {},
    onNavigateToTimetable: () -> Unit = {},
    onStartFocus: (title: String, courseName: String?) -> Unit = { _, _ -> }
) {
    var selectedWeek by remember { mutableIntStateOf(currentWeek) }
    val todayWeekday = remember {
        val cal = Calendar.getInstance()
        val w = cal.get(Calendar.DAY_OF_WEEK)
        if (w == Calendar.SUNDAY) 7 else w - 1
    }
    var selectedDayOfWeek by remember { mutableIntStateOf(todayWeekday) }
    var taskFilter by remember { mutableStateOf(AgendaTaskFilter.IN_PROGRESS) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }

    val availableCourses = remember(courses) {
        if (courses.isNotEmpty()) courses else listOf(
            Course(id = "c_math", semesterId = "sem_1", name = "高等数学", color = "#4F46E5"),
            Course(id = "c_cs", semesterId = "sem_1", name = "数据结构与算法", color = "#0B8043"),
            Course(id = "c_eng", semesterId = "sem_1", name = "大学英语", color = "#E07A5F"),
            Course(id = "c_net", semesterId = "sem_1", name = "计算机网络", color = "#9C27B0")
        )
    }

    val availableSections = remember(sectionTimes) {
        if (sectionTimes.isNotEmpty()) sectionTimes else (1..12).map { s ->
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

    // Selected date title (compact "yyyy/M/d" including year)
    val formattedTitle = remember(selectedWeek, selectedDayOfWeek, currentWeek) {
        val cal = Calendar.getInstance()
        val diff = todayWeekday - selectedDayOfWeek
        cal.add(Calendar.DAY_OF_YEAR, -diff + (selectedWeek - currentWeek) * 7)
        val df = SimpleDateFormat("yyyy/M/d", Locale.getDefault())
        df.format(cal.time)
    }

    // Dynamically project occurrences for the selectedWeek
    val weekOccurrences = remember(
        courses,
        scheduleEvents,
        exceptions,
        selectedWeek,
        currentWeek,
        sectionTimes,
        semesterStartDate
    ) {
        TimelineProjection.projectOccurrences(
            courses = courses,
            schedules = scheduleEvents,
            selectedWeek = selectedWeek,
            currentWeek = currentWeek,
            sectionTimes = sectionTimes,
            totalWeeks = 25,
            showNonCurrentWeekCourses = true,
            semesterStartDate = semesterStartDate,
            exceptions = exceptions
        )
    }

    // Courses & Schedules for selected day
    val selectedDayCourses = remember(weekOccurrences, selectedDayOfWeek) {
        weekOccurrences.filter {
            it.dayOfWeek == selectedDayOfWeek
        }.sortedBy { it.startSection }
    }

    // Next upcoming course hero logic
    val nextUpcomingCourse = remember(todayOccurrences, todayWeekday, currentWeek) {
        val todayCourses = todayOccurrences.filter {
            it.dayOfWeek == todayWeekday && it.status != org.classflow.data.model.OccurrenceStatus.CANCELLED
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

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Scrollable Content Area matching macOS TodayAgendaTimelineView 1:1
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
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
                        dateStr = if (selectedDayOfWeek - 1 in dayDates.indices) dayDates[selectedDayOfWeek - 1] else "",
                        onAddNew = { showCreateDialog = true }
                    )
                }
            }

            // Section 1: Course Schedule for Day Card (macOS dayCourseScheduleColumn)
            item {
                DayCourseScheduleCard(
                    dayName = dayNames[selectedDayOfWeek - 1],
                    selectedDayCourses = selectedDayCourses,
                    onCourseClick = onCourseClick,
                    onNavigateToTimetable = onNavigateToTimetable
                )
            }

            // Section 2: Study Tasks Card for Day (macOS dayTasksColumn with segmented filter)
            item {
                DayTasksCard(
                    tasks = tasks,
                    taskFilter = taskFilter,
                    onFilterChange = { taskFilter = it },
                    onToggle = onToggleTask,
                    onDelete = onDeleteTask,
                    onStartFocus = { task ->
                        onStartFocus(task.title, task.courseName)
                    },
                    onNewTask = { showCreateDialog = true }
                )
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
        ScheduleAddDialog(
            courses = availableCourses,
            availableSections = availableSections,
            isDark = isDark,
            onDismiss = { showCreateDialog = false },
            onSaveSchedule = { event ->
                onAddSchedule(event)
                showCreateDialog = false
            },
            onSaveTask = { task ->
                onAddTask(task)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun DayCourseScheduleCard(
    dayName: String,
    selectedDayCourses: List<TimelineItem>,
    onCourseClick: (TimelineItem) -> Unit,
    onNavigateToTimetable: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$dayName 课程日程",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onNavigateToTimetable() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "全周课表",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (selectedDayCourses.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.FreeBreakfast,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Text(
                        text = "这一天没有课表课程安排",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "享受自习与休息时光，或在下方安排待办",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedDayCourses.forEach { item ->
                        AgendaCourseCard(
                            item = item,
                            onClick = { onCourseClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTasksCard(
    tasks: List<StudyTask>,
    taskFilter: AgendaTaskFilter,
    onFilterChange: (AgendaTaskFilter) -> Unit,
    onToggle: (StudyTask) -> Unit,
    onDelete: (StudyTask) -> Unit,
    onStartFocus: (StudyTask) -> Unit,
    onNewTask: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日待办",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Segmented Filter [待办中 | 全部 | 已完成] matching macOS
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AgendaTaskFilter.values().forEach { filter ->
                        val isSelected = (taskFilter == filter)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { onFilterChange(filter) }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val filteredTasks = remember(tasks, taskFilter) {
                when (taskFilter) {
                    AgendaTaskFilter.IN_PROGRESS -> tasks.filter { !it.isCompleted }
                    AgendaTaskFilter.COMPLETED -> tasks.filter { it.isCompleted }
                    AgendaTaskFilter.ALL -> tasks
                }
            }

            if (filteredTasks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Checklist,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Text(
                        text = if (taskFilter == AgendaTaskFilter.COMPLETED) "暂无已完成的任务" else "今日暂无待办任务",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        onClick = onNewTask,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                            Text("新建待办目标", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredTasks.forEach { task ->
                        TaskChecklistItem(
                            task = task,
                            onToggle = { onToggle(task) },
                            onDelete = { onDelete(task) },
                            onStartFocus = { onStartFocus(task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextClassHeroCard(
    item: TimelineItem,
    onStartFocus: () -> Unit,
    onClick: () -> Unit
) {
    val themeColor = try {
        Color(android.graphics.Color.parseColor(item.color))
    } catch (e: Throwable) {
        Color(0xFF4F46E5)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = themeColor,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(themeColor, themeColor.copy(alpha = 0.85f))
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color.White, CircleShape)
                        )
                        Text(
                            text = "今日即将开始 / 正在进行",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }

                    val timeSpan = if (!item.startTime.isNullOrBlank()) {
                        "开课: ${item.startTime}"
                    } else {
                        "第${item.startSection}节"
                    }
                    Text(
                        text = timeSpan,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.location.isNullOrBlank()) {
                                Text(
                                    text = "@${item.location}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            if (!item.teacher.isNullOrBlank()) {
                                Text(
                                    text = item.teacher,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            if (item.startSection > 0) {
                                Text(
                                    text = "第${item.startSection}-${item.endSection}节",
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onStartFocus,
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = themeColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "专注",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoUpcomingHeroCard(
    dayName: String,
    dateStr: String,
    onAddNew: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$dayName · 今日无待上课程",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "把握自习与课余时光，高效完成待办",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            Surface(
                onClick = onAddNew,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Text("加安排", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun TaskChecklistItem(
    task: StudyTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onStartFocus: () -> Unit = {}
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular toggle button matching macOS
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.isCompleted) Color(0xFF56A3A6) else Color.Transparent
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
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = task.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    task.courseName?.let { cName ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        ) {
                            Text(
                                text = cName,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (task.priority == "high") {
                        Text(
                            text = "紧急",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFF5A5F)
                        )
                    } else if (task.priority == "medium") {
                        Text(
                            text = "重要",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF59E0B)
                        )
                    }

                    if (!task.dueDate.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF5A5F).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "截止 ${task.dueDate.takeLast(11)}",
                                fontSize = 9.sp,
                                color = Color(0xFFFF5A5F),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (!task.frequency.isNullOrBlank() && task.frequency != "none") {
                        val fLabel = when (task.frequency) {
                            "daily" -> "每天"
                            "workday" -> "工作日"
                            "weekly" -> "每周"
                            "monthly" -> "每月"
                            else -> task.frequency
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2563EB).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "↻ $fLabel",
                                fontSize = 9.sp,
                                color = Color(0xFF2563EB),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (!task.notes.isNullOrBlank()) {
                        Text(
                            text = task.notes,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!task.isCompleted) {
                TextButton(
                    onClick = onStartFocus,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("专注", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun AgendaEventCard(
    event: CalendarEvent,
    onClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    val barColor = try {
        Color(android.graphics.Color.parseColor(event.color))
    } catch (e: Throwable) {
        Color(0xFF34C759)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Time range column
            Column(
                modifier = Modifier.width(46.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                val s = if (event.isAllDay) "全天" else (event.startTime ?: "--:--")
                val e = if (event.isAllDay) "" else (event.endTime ?: "")
                Text(
                    text = s,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (e.isNotEmpty()) {
                    Text(
                        text = e,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Vertical indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = event.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (event.frequency != "none" && event.frequency.isNotBlank()) {
                        Text(
                            text = event.frequency,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (!event.location.isNullOrBlank()) {
                    Text(
                        text = "@${event.location}",
                        fontSize = 11.sp,
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
                    modifier = Modifier.size(14.dp)
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
    val isCancelled = item.status == org.classflow.data.model.OccurrenceStatus.CANCELLED
    val barColor = try {
        Color(android.graphics.Color.parseColor(item.color))
    } catch (e: Throwable) {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isCancelled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isCancelled) 0.15f else 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isCancelled) Modifier.alpha(0.55f) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Time range column matching macOS
            Column(
                modifier = Modifier.width(46.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = item.startTime ?: "--:--",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.endTime ?: "--:--",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Vertical indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCancelled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isCancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCancelled) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "已停课",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (item.startSection > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "第${item.startSection}-${item.endSection}节",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!item.location.isNullOrBlank()) {
                        Text(
                            text = "@${item.location}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!item.teacher.isNullOrBlank()) {
                        Text(
                            text = item.teacher,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
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
    ScheduleAddDialog(
        courses = courses,
        availableSections = sectionTimes,
        onDismiss = onDismiss,
        onSaveSchedule = onCreate
    )
}
