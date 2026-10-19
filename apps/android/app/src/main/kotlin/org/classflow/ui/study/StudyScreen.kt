package org.classflow.ui.study

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.classflow.data.model.FocusRecord
import org.classflow.data.model.StudyTask
import org.classflow.ui.components.showDatePicker
import org.classflow.ui.components.showDateTimePicker
import java.text.SimpleDateFormat
import java.util.*

enum class StudyFocusMode(val label: String) {
    POMODORO("番茄钟"),
    COUNT_UP("正向计时")
}

enum class TimerPhase(val label: String, val color: Color) {
    IDLE("准备就绪", Color(0xFF6B7280)),
    FOCUSING("专注中", Color(0xFF2563EB)),
    SHORT_BREAK("短休息", Color(0xFF10B981)),
    LONG_BREAK("长休息", Color(0xFFF59E0B))
}

enum class TaskFilter(val label: String) {
    IN_PROGRESS("进行中"),
    ALL("全部"),
    COMPLETED("已完成")
}

@Composable
fun StudyScreen(
    initialTaskTitle: String? = null,
    initialCourseName: String? = null,
    autoStart: Boolean = false,
    onFocusConsumed: () -> Unit = {},
    tasks: List<StudyTask>? = null,
    onAddTask: ((StudyTask) -> Unit)? = null,
    onToggleTask: ((StudyTask) -> Unit)? = null,
    onDeleteTask: ((StudyTask) -> Unit)? = null,
    focusRecords: MutableList<FocusRecord>? = null,
    dailyMinutesMap: MutableMap<String, Int>? = null,
    onClearFocusHistory: (() -> Unit)? = null
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: 专注时钟, 1: 统计与热力图

    // Focus Engine State
    var focusMode by remember { mutableStateOf(StudyFocusMode.POMODORO) }
    var timerPhase by remember { mutableStateOf(TimerPhase.IDLE) }
    var focusDurationMinutes by remember { mutableIntStateOf(25) }
    var secondsRemaining by remember { mutableIntStateOf(25 * 60) }
    var countUpSeconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var completedPomodoroCount by remember { mutableIntStateOf(3) }

    // Active Task ID
    var activeTaskId by remember { mutableStateOf<String?>(null) }
    var taskFilter by remember { mutableStateOf(TaskFilter.IN_PROGRESS) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var showGoalDropdown by remember { mutableStateOf(false) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }

    // Tasks list (uses hoisted tasks if provided, otherwise fallback)
    val localTasks = remember {
        mutableStateListOf(
            StudyTask(
                id = "st_1",
                title = "高等数学 · 习题册第4章复习",
                courseName = "高等数学",
                targetMinutes = 60,
                completedMinutes = 45,
                isCompleted = false
            ),
            StudyTask(
                id = "st_2",
                title = "大学英语六级 · 听力真题精听与词汇",
                courseName = "大学英语",
                targetMinutes = 45,
                completedMinutes = 30,
                isCompleted = false
            ),
            StudyTask(
                id = "st_3",
                title = "计算机网络 · 路由仿真实验报告",
                courseName = "计算机网络",
                targetMinutes = 90,
                completedMinutes = 90,
                isCompleted = true
            )
        )
    }
    val effectiveTasks = tasks ?: localTasks

    // Auto-start focus linkage from Agenda or Course click
    LaunchedEffect(autoStart, initialTaskTitle) {
        if (autoStart && !initialTaskTitle.isNullOrBlank()) {
            val existing = effectiveTasks.firstOrNull { it.title == initialTaskTitle }
            val target = existing ?: run {
                val created = StudyTask(
                    title = initialTaskTitle,
                    courseName = initialCourseName,
                    targetMinutes = 25
                )
                onAddTask?.invoke(created) ?: localTasks.add(0, created)
                created
            }
            activeTaskId = target.id
            focusMode = StudyFocusMode.POMODORO
            focusDurationMinutes = 25
            secondsRemaining = 25 * 60
            isRunning = true
            timerPhase = TimerPhase.FOCUSING
            onFocusConsumed()
        }
    }

    val activeTask = remember(effectiveTasks, activeTaskId) {
        effectiveTasks.firstOrNull { it.id == activeTaskId }
    }

    // Recent Focus Sessions Log
    val fallbackFocusRecords = remember {
        mutableStateListOf(
            FocusRecord(
                id = "rec_1",
                taskTitle = "高等数学 · 习题册第4章复习",
                courseName = "高等数学",
                durationMinutes = 25,
                completedAt = "今日 10:15"
            ),
            FocusRecord(
                id = "rec_2",
                taskTitle = "大学英语六级 · 听力真题精听与词汇",
                courseName = "大学英语",
                durationMinutes = 25,
                completedAt = "昨日 16:40"
            ),
            FocusRecord(
                id = "rec_3",
                taskTitle = "自习 · 深入浅出操作系统导论",
                courseName = "操作系统",
                durationMinutes = 45,
                completedAt = "10/14 20:30"
            )
        )
    }
    val actualFocusRecords = focusRecords ?: fallbackFocusRecords

    // Daily focus minutes history for heatmap & analytics
    val fallbackDailyMinutesMap = remember {
        val map = mutableStateMapOf<String, Int>()
        val cal = Calendar.getInstance()
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (i in 0..90) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = df.format(cal.time)
            val mins = when {
                i == 0 -> 50
                i == 1 -> 45
                i == 2 -> 75
                i % 7 == 0 -> 0
                i % 3 == 0 -> (i * 12 + 30) % 120
                i % 2 == 0 -> (i * 15 + 20) % 90
                else -> 25
            }
            if (mins > 0) map[key] = mins
        }
        map
    }
    val actualDailyMinutesMap = dailyMinutesMap ?: fallbackDailyMinutesMap

    // Timer Ticker - robust while(isRunning) loop without frequent coroutine teardown
    LaunchedEffect(isRunning, focusMode) {
        while (isRunning) {
            delay(1000L)
            if (focusMode == StudyFocusMode.POMODORO) {
                if (secondsRemaining > 0) {
                    secondsRemaining -= 1
                } else {
                    // Session completed
                    isRunning = false
                    completedPomodoroCount++
                    timerPhase = TimerPhase.SHORT_BREAK
                    secondsRemaining = 5 * 60

                    // Record session
                    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val todayKey = df.format(Date())
                    val added = focusDurationMinutes
                    actualDailyMinutesMap[todayKey] = (actualDailyMinutesMap[todayKey] ?: 0) + added

                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    actualFocusRecords.add(
                        0,
                        FocusRecord(
                            taskTitle = activeTask?.title ?: "自由专注",
                            courseName = activeTask?.courseName,
                            durationMinutes = added,
                            completedAt = "今日 $timeStr"
                        )
                    )

                    // Update active task
                    if (activeTask != null) {
                        val idx = effectiveTasks.indexOf(activeTask)
                        if (idx >= 0) {
                            val newCompleted = activeTask.completedMinutes + added
                            val updated = activeTask.copy(
                                completedMinutes = newCompleted,
                                isCompleted = newCompleted >= activeTask.targetMinutes
                            )
                            onToggleTask?.invoke(updated) ?: run { localTasks.indexOfFirst { it.id == updated.id }.takeIf { it >= 0 }?.let { localTasks[it] = updated } }
                        }
                    }
                }
            } else {
                countUpSeconds += 1
            }
        }
    }

    // Quotes
    val quotes = listOf(
        "自律给自由，专注致远方。",
        "星光不问赶路人，时光不负有心人。",
        "流水不争先，争的是滔滔不绝。",
        "保持热爱，奔赴山海。",
        "所有努力的痕迹，时间都看得见。"
    )
    val quote = quotes.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 1. Top Sub-Tab Segmented Selector matching macOS StudyFocusView
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .width(240.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(
                            if (selectedSubTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .then(
                            if (selectedSubTab == 0) {
                                Modifier.border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                            } else Modifier
                        )
                        .clickable { selectedSubTab = 0 }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "专注时钟",
                        fontSize = 12.5.sp,
                        fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedSubTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(
                            if (selectedSubTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .then(
                            if (selectedSubTab == 1) {
                                Modifier.border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                            } else Modifier
                        )
                        .clickable { selectedSubTab = 1 }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "统计与热力图",
                        fontSize = 12.5.sp,
                        fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedSubTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Scrollable Content Area with buttery-smooth single-pass GPU rendering
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedSubTab == 0) {
                // Focus Arena Card (macOS 1:1 Parity)
                FocusArenaCard(
                    focusMode = focusMode,
                    timerPhase = timerPhase,
                    isRunning = isRunning,
                    activeTask = activeTask,
                    focusDurationMinutes = focusDurationMinutes,
                    secondsRemaining = secondsRemaining,
                    countUpSeconds = countUpSeconds,
                    quote = quote,
                    onModeChange = { mode ->
                        if (!isRunning) {
                            focusMode = mode
                            isRunning = false
                            timerPhase = TimerPhase.IDLE
                            if (mode == StudyFocusMode.POMODORO) {
                                secondsRemaining = focusDurationMinutes * 60
                            } else {
                                countUpSeconds = 0
                            }
                        }
                    },
                    onOpenGoalSelector = { showGoalDropdown = true },
                    onDurationSelect = { mins ->
                        if (!isRunning) {
                            focusDurationMinutes = mins
                            secondsRemaining = mins * 60
                        }
                    },
                    onOpenCustomDuration = { showCustomDurationDialog = true },
                    onToggleRunning = {
                        isRunning = !isRunning
                        timerPhase = if (isRunning) TimerPhase.FOCUSING else TimerPhase.IDLE
                    },
                    onHarvest = {
                        // Early settlement
                        val earnedMinutes = if (focusMode == StudyFocusMode.POMODORO) {
                            (focusDurationMinutes * 60 - secondsRemaining) / 60
                        } else {
                            countUpSeconds / 60
                        }
                        if (earnedMinutes > 0) {
                            val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val todayKey = df.format(Date())
                            actualDailyMinutesMap[todayKey] = (actualDailyMinutesMap[todayKey] ?: 0) + earnedMinutes

                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            actualFocusRecords.add(
                                0,
                                FocusRecord(
                                    taskTitle = activeTask?.title ?: "自由专注",
                                    courseName = activeTask?.courseName,
                                    durationMinutes = earnedMinutes,
                                    completedAt = "今日 $timeStr"
                                )
                            )

                            if (activeTask != null) {
                                val idx = effectiveTasks.indexOf(activeTask)
                                if (idx >= 0) {
                                    val newCompleted = activeTask.completedMinutes + earnedMinutes
                                    val updated = activeTask.copy(
                                        completedMinutes = newCompleted,
                                        isCompleted = newCompleted >= activeTask.targetMinutes
                                    )
                                    onToggleTask?.invoke(updated) ?: run { localTasks.indexOfFirst { it.id == updated.id }.takeIf { it >= 0 }?.let { localTasks[it] = updated } }
                                }
                            }
                        }
                        isRunning = false
                        timerPhase = TimerPhase.IDLE
                        if (focusMode == StudyFocusMode.POMODORO) {
                            secondsRemaining = focusDurationMinutes * 60
                        } else {
                            countUpSeconds = 0
                        }
                    },
                    onReset = {
                        isRunning = false
                        timerPhase = TimerPhase.IDLE
                        if (focusMode == StudyFocusMode.POMODORO) {
                            secondsRemaining = focusDurationMinutes * 60
                        } else {
                            countUpSeconds = 0
                        }
                    }
                )

                // Study ToDo Task Management Card
                FocusTaskListCard(
                    tasks = effectiveTasks,
                    taskFilter = taskFilter,
                    activeTaskId = activeTaskId,
                    onFilterChange = { taskFilter = it },
                    onNewTaskClick = { showCreateTaskDialog = true },
                    onToggleTask = { task ->
                        val updated = task.copy(isCompleted = !task.isCompleted)
                        onToggleTask?.invoke(updated) ?: run {
                            localTasks.indexOfFirst { it.id == task.id }.takeIf { it >= 0 }?.let { localTasks[it] = updated }
                        }
                    },
                    onStartTask = { task ->
                        activeTaskId = task.id
                        focusMode = StudyFocusMode.POMODORO
                        focusDurationMinutes = 25
                        secondsRemaining = 25 * 60
                        isRunning = true
                        timerPhase = TimerPhase.FOCUSING
                    },
                    onDeleteTask = { task ->
                        onDeleteTask?.invoke(task) ?: localTasks.removeAll { it.id == task.id }
                        if (activeTaskId == task.id) activeTaskId = null
                    }
                )

                // Recent Focus Records Card
                if (actualFocusRecords.isNotEmpty()) {
                    RecentFocusRecordsCard(
                        records = actualFocusRecords,
                        onClearRecords = {
                            onClearFocusHistory?.invoke() ?: run {
                                actualFocusRecords.clear()
                                actualDailyMinutesMap.clear()
                            }
                        }
                    )
                }
            } else {
                // Statistics & Heatmap Tab
                PomodoroStatisticsCard(
                    dailyMinutesMap = actualDailyMinutesMap,
                    completedPomodoroCount = completedPomodoroCount,
                    tasks = effectiveTasks,
                    focusRecords = actualFocusRecords
                )
            }
        }
    }

    // Goal Selector Dropdown Dialog
    if (showGoalDropdown) {
        AlertDialog(
            onDismissRequest = { showGoalDropdown = false },
            title = { Text("关联专注目标", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = {
                            activeTaskId = null
                            showGoalDropdown = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (activeTaskId == null) Color(0xFF2563EB).copy(alpha = 0.12f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "自由专注 (不绑定任务)",
                            fontSize = 13.5.sp,
                            fontWeight = if (activeTaskId == null) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeTaskId == null) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    effectiveTasks.filter { !it.isCompleted }.forEach { task ->
                        val isSelected = (task.id == activeTaskId)
                        Surface(
                            onClick = {
                                activeTaskId = task.id
                                showGoalDropdown = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.12f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = task.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurface
                                )
                                task.courseName?.let {
                                    Text(
                                        text = it,
                                        fontSize = 11.sp,
                                        color = Color(0xFF2563EB).copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGoalDropdown = false }) {
                    Text("完成")
                }
            }
        )
    }

    // Custom Duration Dialog
    if (showCustomDurationDialog) {
        var inputMins by remember { mutableStateOf(focusDurationMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomDurationDialog = false },
            title = { Text("自定义专注时长", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = inputMins,
                    onValueChange = { inputMins = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("分钟 (1-180)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = inputMins.toIntOrNull()?.coerceIn(1, 180) ?: 25
                        focusDurationMinutes = mins
                        secondsRemaining = mins * 60
                        showCustomDurationDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDurationDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Create Task Dialog (Google Tasks Parity)
    if (showCreateTaskDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        var taskTitle by remember { mutableStateOf("") }
        var courseName by remember { mutableStateOf("") }
        var targetMinutesStr by remember { mutableStateOf("45") }
        var hasStartDate by remember { mutableStateOf(false) }
        var startDateStr by remember { mutableStateOf(todayStr) }
        var hasDueDate by remember { mutableStateOf(false) }
        var dueDateStr by remember { mutableStateOf("$todayStr 18:00") }
        var taskFrequency by remember { mutableStateOf("不重复") }
        var showTaskFreqMenu by remember { mutableStateOf(false) }
        val taskFrequencies = listOf("不重复", "每天", "工作日 (周一至周五)", "每周", "每月")

        AlertDialog(
            onDismissRequest = { showCreateTaskDialog = false },
            title = { Text("新建待办任务", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text("任务内容") },
                        placeholder = { Text("例如：完成数学作业习题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = courseName,
                        onValueChange = { courseName = it },
                        label = { Text("关联课程 (可选)") },
                        placeholder = { Text("例如：高等数学") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetMinutesStr,
                        onValueChange = { targetMinutesStr = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("目标时长 (分钟)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Start date row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Checkbox(
                                checked = hasStartDate,
                                onCheckedChange = { hasStartDate = it },
                                modifier = Modifier.size(20.dp)
                            )
                            Text("开始日期", fontSize = 13.sp)
                        }
                        if (hasStartDate) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable {
                                    showDatePicker(context, startDateStr) { picked -> startDateStr = picked }
                                }
                            ) {
                                Text(
                                    text = startDateStr,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Due date row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Checkbox(
                                checked = hasDueDate,
                                onCheckedChange = { hasDueDate = it },
                                modifier = Modifier.size(20.dp)
                            )
                            Text("截止时间", fontSize = 13.sp)
                        }
                        if (hasDueDate) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable {
                                    showDateTimePicker(context, dueDateStr) { picked -> dueDateStr = picked }
                                }
                            ) {
                                Text(
                                    text = dueDateStr,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Recurrence frequency row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("重复频次", fontSize = 13.sp)
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { showTaskFreqMenu = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = taskFrequency, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = showTaskFreqMenu,
                                onDismissRequest = { showTaskFreqMenu = false }
                            ) {
                                taskFrequencies.forEach { freq ->
                                    DropdownMenuItem(
                                        text = { Text(freq, fontSize = 12.5.sp) },
                                        onClick = {
                                            taskFrequency = freq
                                            showTaskFreqMenu = false
                                        }
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
                        if (taskTitle.isNotBlank()) {
                            val mins = targetMinutesStr.toIntOrNull()?.coerceIn(5, 360) ?: 45
                            val freqCode = when (taskFrequency) {
                                "每天" -> "daily"
                                "工作日 (周一至周五)" -> "workday"
                                "每周" -> "weekly"
                                "每月" -> "monthly"
                                else -> "none"
                            }
                            val created = StudyTask(
                                title = taskTitle.trim(),
                                courseName = courseName.trim().ifEmpty { null },
                                targetMinutes = mins,
                                startDate = if (hasStartDate) startDateStr else null,
                                dueDate = if (hasDueDate) dueDateStr else null,
                                frequency = freqCode
                            )
                            onAddTask?.invoke(created) ?: localTasks.add(0, created)
                            showCreateTaskDialog = false
                        }
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTaskDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Focus Arena Card (Radial Clock, Presets, Actions)
// -------------------------------------------------------------
@Composable
private fun FocusArenaCard(
    focusMode: StudyFocusMode,
    timerPhase: TimerPhase,
    isRunning: Boolean,
    activeTask: StudyTask?,
    focusDurationMinutes: Int,
    secondsRemaining: Int,
    countUpSeconds: Int,
    quote: String,
    onModeChange: (StudyFocusMode) -> Unit,
    onOpenGoalSelector: () -> Unit,
    onDurationSelect: (Int) -> Unit,
    onOpenCustomDuration: () -> Unit,
    onToggleRunning: () -> Unit,
    onHarvest: () -> Unit,
    onReset: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Arena Top Controls (Mode Switcher, Goal Selector, Phase Badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Switcher Capsule
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .padding(2.5.dp)
                ) {
                    StudyFocusMode.values().forEach { mode ->
                        val isSelected = (focusMode == mode)
                        Surface(
                            onClick = { onModeChange(mode) },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (mode == StudyFocusMode.POMODORO) Icons.Default.Timer else Icons.Default.Timelapse,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = mode.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Phase Badge
                Surface(
                    shape = CircleShape,
                    color = timerPhase.color.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = timerPhase.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = timerPhase.color,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            // Active Goal Pill Selector
            Surface(
                onClick = onOpenGoalSelector,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AdsClick,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(13.dp)
                    )
                    if (activeTask != null) {
                        Text(
                            text = "目标: ${activeTask.title}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                        activeTask.courseName?.let {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF2563EB).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = it,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "点击关联目标待办...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 2. Radial Clock Canvas View (macOS 1:1 Parity)
            val totalSeconds = if (focusMode == StudyFocusMode.POMODORO) focusDurationMinutes * 60 else 1
            val progress = if (focusMode == StudyFocusMode.COUNT_UP) {
                1.0f
            } else {
                if (totalSeconds > 0) ((totalSeconds - secondsRemaining).toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f
            }

            val displayTimeStr = if (focusMode == StudyFocusMode.POMODORO) {
                val m = secondsRemaining / 60
                val s = secondsRemaining % 60
                String.format("%02d:%02d", m, s)
            } else {
                val m = countUpSeconds / 60
                val s = countUpSeconds % 60
                String.format("%02d:%02d", m, s)
            }

            val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            val ringGradient = remember {
                Brush.sweepGradient(
                    listOf(
                        Color(0xFF2563EB),
                        Color(0xFF60A5FA),
                        Color(0xFF2563EB)
                    )
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(210.dp)
            ) {
                Canvas(modifier = Modifier.size(210.dp)) {
                    val strokeWidth = 14.dp.toPx()
                    val arcSize = size.width - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    // Background Track
                    drawCircle(
                        color = trackColor,
                        radius = arcSize / 2f,
                        style = Stroke(width = strokeWidth)
                    )

                    // Foreground Progress Arc
                    if (progress > 0f) {
                        drawArc(
                            brush = ringGradient,
                            startAngle = -90f,
                            sweepAngle = progress * 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Center Monospace Typography
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isRunning) (activeTask?.courseName ?: "专注进行中") else "准备就绪",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Text(
                        text = displayTimeStr,
                        fontSize = 44.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = if (focusMode == StudyFocusMode.POMODORO) "${focusDurationMinutes} 分钟专注" else "正向计时中",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }

            // 3. Duration Presets Bar (Pills matching macOS)
            if (!isRunning && focusMode == StudyFocusMode.POMODORO) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        val isSelected = (focusDurationMinutes == mins)
                        Surface(
                            onClick = { onDurationSelect(mins) },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "${mins}m",
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Custom pill
                    Surface(
                        onClick = onOpenCustomDuration,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "$focusDurationMinutes",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Slogan
            Text(
                text = quote,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )

            // 4. Action Buttons (Big Royal Blue Pill & Reset/Harvest)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start / Pause Primary Button
                Surface(
                    onClick = onToggleRunning,
                    shape = CircleShape,
                    color = Color(0xFF2563EB),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isRunning) "暂停专注" else "开始专注",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Harvest / Settlement Pill
                if (isRunning || (focusMode == StudyFocusMode.COUNT_UP && countUpSeconds >= 30) || (focusMode == StudyFocusMode.POMODORO && secondsRemaining < focusDurationMinutes * 60)) {
                    Surface(
                        onClick = onHarvest,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Text("结算", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Reset Circle
                    Surface(
                        onClick = onReset,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "重置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Study ToDo Task Management Card
// -------------------------------------------------------------
@Composable
private fun FocusTaskListCard(
    tasks: List<StudyTask>,
    taskFilter: TaskFilter,
    activeTaskId: String?,
    onFilterChange: (TaskFilter) -> Unit,
    onNewTaskClick: () -> Unit,
    onToggleTask: (StudyTask) -> Unit,
    onStartTask: (StudyTask) -> Unit,
    onDeleteTask: (StudyTask) -> Unit
) {
    val filtered = when (taskFilter) {
        TaskFilter.IN_PROGRESS -> tasks.filter { !it.isCompleted }
        TaskFilter.ALL -> tasks
        TaskFilter.COMPLETED -> tasks.filter { it.isCompleted }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title, Segmented Filter, + 新建待办
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "待办清单",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Segmented Filter Capsule
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .padding(2.5.dp)
                ) {
                    TaskFilter.values().forEach { filter ->
                        val isSelected = (taskFilter == filter)
                        Surface(
                            onClick = { onFilterChange(filter) },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Surface(
                    onClick = onNewTaskClick,
                    shape = CircleShape,
                    color = Color(0xFF2563EB).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(13.dp))
                        Text("新建", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无相关待办任务",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { task ->
                        val isActive = (task.id == activeTaskId)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) Color(0xFF2563EB).copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isActive) Color(0xFF2563EB).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Circular Checkbox
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (task.isCompleted) Color(0xFF2563EB) else Color.Transparent
                                        )
                                        .border(
                                            width = if (task.isCompleted) 0.dp else 1.5.dp,
                                            color = if (task.isCompleted) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                            shape = CircleShape
                                        )
                                        .clickable { onToggleTask(task) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (task.isCompleted) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        task.courseName?.let {
                                            Text(
                                                text = it,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2563EB)
                                            )
                                        }

                                        Text(
                                            text = "${task.completedMinutes}/${task.targetMinutes}m",
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )

                                        if (!task.dueDate.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFF5A5F).copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = "截止 ${task.dueDate!!.takeLast(11)}",
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
                                    }
                                }

                                if (!task.isCompleted) {
                                    Surface(
                                        onClick = { onStartTask(task) },
                                        shape = CircleShape,
                                        color = if (isActive) Color(0xFF2563EB) else Color(0xFF2563EB).copy(alpha = 0.10f),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isActive) Color.White else Color(0xFF2563EB),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = if (isActive) "专注中" else "开始",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) Color.White else Color(0xFF2563EB)
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteTask(task) },
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
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Recent Focus Records Card
// -------------------------------------------------------------
@Composable
private fun RecentFocusRecordsCard(
    records: List<FocusRecord>,
    onClearRecords: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = 0.dp,
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
                Text(
                    text = "最近专注成果",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (onClearRecords != null) {
                    Text(
                        text = "清空",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEF4444),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClearRecords)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                records.take(5).forEach { rec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = rec.taskTitle,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            rec.courseName?.let {
                                Text(
                                    text = "$it · ${rec.completedAt}",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "+${rec.durationMinutes}m",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Statistics & Heatmap Card (macOS PomodoroStatisticsView Parity)
// -------------------------------------------------------------
enum class TrendPeriod(val label: String) {
    PAST_7_DAYS("近7天"),
    WEEKLY("按周趋势"),
    MONTHLY("按月趋势")
}

enum class DonutTimeRange(val label: String) {
    WEEK("近7天"),
    MONTH("近30天"),
    YEAR("按年查看"),
    ALL("全部")
}

data class DayHeatInfo(
    val dateKey: String,
    val minutes: Int,
    val count: Int
)

data class TrendPoint(
    val id: String,
    val label: String,
    val minutes: Int,
    val count: Int,
    val isCurrent: Boolean
)

data class DonutSliceData(
    val id: String,
    val name: String,
    val minutes: Int,
    val percentage: Double,
    val color: Color
)

@Composable
private fun PomodoroStatisticsCard(
    dailyMinutesMap: Map<String, Int>,
    completedPomodoroCount: Int,
    tasks: List<StudyTask>,
    focusRecords: List<FocusRecord> = emptyList()
) {
    val now = remember { Date() }
    val todayKey = remember { StudyDateUtils.formatDay(now) }

    // Streak calculation matching macOS
    val streakDays = remember(dailyMinutesMap, todayKey) {
        var streak = 0
        val cal = Calendar.getInstance()
        val todayMins = dailyMinutesMap[todayKey] ?: 0
        if (todayMins > 0) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yKey = StudyDateUtils.formatDay(cal.time)
            if ((dailyMinutesMap[yKey] ?: 0) > 0) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
        }
        if (streak > 0) {
            while (true) {
                val key = StudyDateUtils.formatDay(cal.time)
                if ((dailyMinutesMap[key] ?: 0) > 0) {
                    streak++
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                } else break
            }
        }
        streak
    }

    val totalActiveDays = remember(dailyMinutesMap) {
        dailyMinutesMap.values.count { it > 0 }
    }
    val totalMinutes = remember(dailyMinutesMap) {
        dailyMinutesMap.values.sum()
    }
    val totalHours = remember(totalMinutes) {
        String.format(Locale.getDefault(), "%.1f", totalMinutes / 60.0)
    }
    val avgMinutes = remember(totalMinutes, totalActiveDays) {
        if (totalActiveDays > 0) totalMinutes / totalActiveDays else 0
    }
    val completedTasks = remember(tasks) {
        tasks.count { it.isCompleted }
    }
    val completionRate = remember(tasks, completedTasks) {
        if (tasks.isNotEmpty()) (completedTasks * 100) / tasks.size else 0
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Metric Overview Section (2x2 Grid)
        MetricOverviewSection(
            streakDays = streakDays,
            totalActiveDays = totalActiveDays,
            avgMinutes = avgMinutes,
            totalHours = totalHours,
            completedPomodoroCount = completedPomodoroCount,
            completionRate = completionRate,
            completedTasks = completedTasks,
            totalTasks = tasks.size
        )

        // 2. Full-Width Heatmap Card (Isolated State & Tap Scope)
        HeatmapSection(
            dailyMinutesMap = dailyMinutesMap,
            todayKey = todayKey,
            now = now
        )

        // 3. Focus Trend Insight (Isolated Period Scope)
        TrendInsightSection(
            dailyMinutesMap = dailyMinutesMap,
            todayKey = todayKey
        )

        // 4. Subject Distribution Donut (Isolated Slice Scope)
        SubjectDonutSection(
            tasks = tasks,
            focusRecords = focusRecords
        )
    }
}

@Composable
private fun MetricOverviewSection(
    streakDays: Int,
    totalActiveDays: Int,
    avgMinutes: Int,
    totalHours: String,
    completedPomodoroCount: Int,
    completionRate: Int,
    completedTasks: Int,
    totalTasks: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MacMetricBox(
                title = "连续专注",
                value = "$streakDays",
                unit = "天",
                subtitle = if (streakDays > 0) "心流保持中" else "今日待打卡",
                color = Color(0xFFFF5A5F),
                modifier = Modifier.weight(1f)
            )
            MacMetricBox(
                title = "累计专注天数",
                value = "$totalActiveDays",
                unit = "天",
                subtitle = "日均 $avgMinutes 分钟",
                color = Color(0xFFE8A87C),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MacMetricBox(
                title = "累计专注时长",
                value = totalHours,
                unit = "小时",
                subtitle = "共 $completedPomodoroCount 个番茄钟",
                color = Color(0xFF5B7FB5),
                modifier = Modifier.weight(1f)
            )
            MacMetricBox(
                title = "任务完成率",
                value = "$completionRate%",
                unit = "",
                subtitle = "已完成 $completedTasks/$totalTasks 项",
                color = Color(0xFF56A3A6),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeatmapSection(
    dailyMinutesMap: Map<String, Int>,
    todayKey: String,
    now: Date
) {
    var selectedDayInfo by remember(todayKey) {
        val todayMins = dailyMinutesMap[todayKey] ?: 0
        val todayCount = if (todayMins > 0) (todayMins / 25).coerceAtLeast(1) else 0
        mutableStateOf(DayHeatInfo(todayKey, todayMins, todayCount))
    }

    val weekDaysLabels = remember { listOf("一", "二", "三", "四", "五", "六", "日") }
    val totalWeeks = 24

    val heatmapWeeks = remember(dailyMinutesMap, todayKey) {
        val currentDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val mondayOffset = if (currentDow == Calendar.SUNDAY) -6 else 2 - currentDow
        val thisMondayCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, mondayOffset)
        }
        val thisMondayTime = thisMondayCal.timeInMillis
        val oneDayMs = 86_400_000L
        val cellCal = Calendar.getInstance()

        (0 until totalWeeks).map { w ->
            val weekOffset = totalWeeks - 1 - w
            (0..6).map { d ->
                cellCal.timeInMillis = thisMondayTime + (-(weekOffset * 7) + d) * oneDayMs
                val dateKey = StudyDateUtils.formatDay(cellCal.time)
                val isToday = (dateKey == todayKey)
                val isFuture = cellCal.time.after(now) && !isToday
                val mins = if (isFuture) 0 else (dailyMinutesMap[dateKey] ?: 0)
                val count = if (mins > 0) (mins / 25).coerceAtLeast(1) else 0

                val heatLevel = when {
                    mins == 0 -> 0
                    mins <= 25 -> 1
                    mins <= 50 -> 2
                    mins <= 100 -> 3
                    else -> 4
                }
                Triple(DayHeatInfo(dateKey, mins, count), heatLevel, isToday to isFuture)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "专注热力图谱",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Legend: 少 -> 多
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("少", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (0..4).forEach { lvl ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(getHeatColor(lvl))
                        )
                    }
                    Text("多", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Grid with Y-Axis Weekday Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Weekday Labels
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    weekDaysLabels.forEach { label ->
                        Box(
                            modifier = Modifier.size(width = 14.dp, height = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                // 24 Weeks Heatmap Grid (GPU Hardware Accelerated Canvas: 1 node vs 192 nodes)
                val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                val cellWidthDp = 15.dp
                val cellHeightDp = 14.dp
                val spacingDp = 4.dp
                val cornerRadiusDp = 3.5.dp

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(
                                width = (totalWeeks * 19 - 4).dp,
                                height = (7 * 18 - 4).dp
                            )
                            .pointerInput(heatmapWeeks) {
                                detectTapGestures { offset ->
                                    val colW = (cellWidthDp + spacingDp).toPx()
                                    val rowH = (cellHeightDp + spacingDp).toPx()
                                    val col = (offset.x / colW).toInt()
                                    val row = (offset.y / rowH).toInt()
                                    if (col in 0 until totalWeeks && row in 0..6) {
                                        val cell = heatmapWeeks.getOrNull(col)?.getOrNull(row)
                                        if (cell != null) {
                                            val (info, _, flags) = cell
                                            val (_, isFuture) = flags
                                            if (!isFuture) {
                                                selectedDayInfo = info
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val colW = cellWidthDp.toPx()
                        val rowH = cellHeightDp.toPx()
                        val spacePx = spacingDp.toPx()
                        val cr = CornerRadius(cornerRadiusDp.toPx(), cornerRadiusDp.toPx())
                        val todayStroke = 1.5.dp.toPx()
                        val regularStroke = 0.5.dp.toPx()

                        for (col in 0 until totalWeeks) {
                            val weekList = heatmapWeeks.getOrNull(col) ?: continue
                            val x = col * (colW + spacePx)
                            for (row in 0..6) {
                                val cell = weekList.getOrNull(row) ?: continue
                                val (_, heatLevel, flags) = cell
                                val (isToday, isFuture) = flags
                                val y = row * (rowH + spacePx)

                                if (!isFuture) {
                                    drawRoundRect(
                                        color = getHeatColor(heatLevel),
                                        topLeft = Offset(x, y),
                                        size = Size(colW, rowH),
                                        cornerRadius = cr
                                    )
                                }

                                if (isToday) {
                                    drawRoundRect(
                                        color = Color(0xFFFF5A5F),
                                        topLeft = Offset(x, y),
                                        size = Size(colW, rowH),
                                        cornerRadius = cr,
                                        style = Stroke(width = todayStroke)
                                    )
                                } else if (!isFuture) {
                                    drawRoundRect(
                                        color = outlineColor,
                                        topLeft = Offset(x, y),
                                        size = Size(colW, rowH),
                                        cornerRadius = cr,
                                        style = Stroke(width = regularStroke)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Selected Day Info Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                selectedDayInfo.minutes == 0 -> Color.Gray
                                selectedDayInfo.minutes <= 25 -> Color(0xFFFF5A5F).copy(alpha = 0.32f)
                                selectedDayInfo.minutes <= 50 -> Color(0xFFFF5A5F).copy(alpha = 0.58f)
                                selectedDayInfo.minutes <= 100 -> Color(0xFFFF5A5F).copy(alpha = 0.82f)
                                else -> Color(0xFFFF5A5F)
                            }
                        )
                )
                Text(
                    text = selectedDayInfo.dateKey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "专注 ${selectedDayInfo.minutes} 分钟 (${selectedDayInfo.count} 次番茄专注)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Composable
private fun TrendInsightSection(
    dailyMinutesMap: Map<String, Int>,
    todayKey: String
) {
    var selectedTrendPeriod by remember { mutableStateOf(TrendPeriod.PAST_7_DAYS) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Period Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "专注趋势洞察",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Capsule Switcher
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .padding(2.dp)
                ) {
                    TrendPeriod.values().forEach { period ->
                        val isSelected = (selectedTrendPeriod == period)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selectedTrendPeriod = period }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Data Points
            val dataPoints = remember(selectedTrendPeriod, dailyMinutesMap, todayKey) {
                computeTrendPoints(selectedTrendPeriod, dailyMinutesMap, todayKey)
            }
            val maxMinutes = remember(dataPoints) { (dataPoints.maxOfOrNull { it.minutes } ?: 60).coerceAtLeast(60) }
            val maxCount = remember(dataPoints) { (dataPoints.maxOfOrNull { it.count } ?: 4).coerceAtLeast(4) }

            // Chart Canvas (Bars + Line Overlay)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                // Bars Row
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dataPoints.forEach { point ->
                        val barRatio = (point.minutes.toFloat() / maxMinutes).coerceIn(0.04f, 1f)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Value above bar (e.g. 75m)
                            Text(
                                text = if (point.minutes > 0) "${point.minutes}m" else "",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (point.isCurrent) Color(0xFFFF5A5F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.height(14.dp)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // The Bar
                            Box(
                                modifier = Modifier
                                    .width(if (selectedTrendPeriod == TrendPeriod.PAST_7_DAYS) 20.dp else 14.dp)
                                    .fillMaxHeight(barRatio * 0.72f)
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                    .background(
                                        if (point.isCurrent) {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFFFF5A5F), Color(0xFFFF8E53))
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF5B7FB5), Color(0xFF7A9FD3))
                                            )
                                        }
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // X-axis label
                            Text(
                                text = point.label,
                                fontSize = 10.sp,
                                fontWeight = if (point.isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (point.isCurrent) Color(0xFFFF5A5F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Orange Dashed Line Overlay for Session Count
                val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
                val trendPath = remember { androidx.compose.ui.graphics.Path() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val count = dataPoints.size
                    if (count > 1) {
                        val stepX = size.width / count
                        val availableHeight = size.height - 35.dp.toPx()
                        trendPath.reset()

                        dataPoints.forEachIndexed { idx, pt ->
                            val x = stepX * idx + stepX / 2
                            val ratio = (pt.count.toFloat() / maxCount).coerceIn(0f, 1f)
                            val y = availableHeight - (ratio * (availableHeight - 20.dp.toPx()))

                            if (idx == 0) {
                                trendPath.moveTo(x, y)
                            } else {
                                trendPath.lineTo(x, y)
                            }
                        }

                        // Draw dashed stroke
                        drawPath(
                            path = trendPath,
                            color = Color(0xFFE8A87C),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = dashEffect
                            )
                        )

                        // Draw dots
                        dataPoints.forEachIndexed { idx, pt ->
                            val x = stepX * idx + stepX / 2
                            val ratio = (pt.count.toFloat() / maxCount).coerceIn(0f, 1f)
                            val y = availableHeight - (ratio * (availableHeight - 20.dp.toPx()))

                            drawCircle(
                                color = Color(0xFFE8A87C),
                                radius = 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }

            // Legend Footer
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFF5B7FB5), Color(0xFF7A9FD3)))
                            )
                    )
                    Text(
                        text = "专注时长 (柱状)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .border(2.dp, Color(0xFFE8A87C), CircleShape)
                    )
                    Text(
                        text = "专注频次 (趋势折线)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectDonutSection(
    tasks: List<StudyTask>,
    focusRecords: List<FocusRecord>
) {
    var selectedDonutRange by remember { mutableStateOf(DonutTimeRange.MONTH) }
    var selectedSliceIndex by remember { mutableStateOf<Int?>(null) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Time Range Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "学科投入环形分布",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .padding(2.dp)
                ) {
                    DonutTimeRange.values().forEach { range ->
                        val isSelected = (selectedDonutRange == range)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selectedDonutRange = range }
                                .padding(horizontal = 7.dp, vertical = 3.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = range.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Slices calculation
            val donutPalette = remember {
                listOf(
                    Color(0xFF4F46E5), // Indigo
                    Color(0xFF059669), // Green
                    Color(0xFFE07A5F), // Coral
                    Color(0xFFF59E0B), // Amber
                    Color(0xFF8B5CF6), // Purple
                    Color(0xFF06B6D4)  // Cyan
                )
            }

            val slices = remember(selectedDonutRange, tasks, focusRecords) {
                computeDonutSlices(tasks, focusRecords, donutPalette)
            }
            val totalDonutMinutes = remember(slices) { slices.sumOf { it.minutes } }
            val totalDonutHours = remember(totalDonutMinutes) {
                String.format(Locale.getDefault(), "%.1f", totalDonutMinutes / 60.0)
            }

            if (slices.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Timelapse,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "暂无学科专注数据",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "在待办中关联课表课程即可生成环形投入图",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Donut Ring (Canvas + Center Text)
                    Box(
                        modifier = Modifier.size(130.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 22.dp.toPx()
                            val arcRadius = (size.minDimension - strokeWidth) / 2
                            val arcTopLeft = Offset(
                                (size.width - arcRadius * 2) / 2,
                                (size.height - arcRadius * 2) / 2
                            )
                            val arcSize = Size(arcRadius * 2, arcRadius * 2)

                            var currentStartAngle = -90f
                            slices.forEachIndexed { idx, slice ->
                                val sweep = (slice.percentage.toFloat() * 360f).coerceAtLeast(2f)
                                val isHighlighted = (selectedSliceIndex == idx)
                                drawArc(
                                    color = slice.color,
                                    startAngle = currentStartAngle,
                                    sweepAngle = sweep - 1.5f,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(
                                        width = if (isHighlighted) strokeWidth + 4.dp.toPx() else strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                )
                                currentStartAngle += sweep
                            }
                        }

                        // Center text
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            if (selectedSliceIndex != null && selectedSliceIndex!! < slices.size) {
                                val selectedSlice = slices[selectedSliceIndex!!]
                                Text(
                                    text = selectedSlice.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${selectedSlice.minutes}m",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = selectedSlice.color
                                )
                                Text(
                                    text = "${(selectedSlice.percentage * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "总投入",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = totalDonutHours,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "小时",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Right: Subject breakdown list
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        slices.take(5).forEachIndexed { idx, slice ->
                            val isSelected = (selectedSliceIndex == idx)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent
                                    )
                                    .clickable {
                                        selectedSliceIndex = if (isSelected) null else idx
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(slice.color)
                                )
                                Text(
                                    text = slice.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(slice.percentage * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${slice.minutes}m",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getHeatColor(level: Int): Color {
    return when (level) {
        0 -> Color(0xFFFF5A5F).copy(alpha = 0.08f)
        1 -> Color(0xFFFF5A5F).copy(alpha = 0.32f)
        2 -> Color(0xFFFF5A5F).copy(alpha = 0.58f)
        3 -> Color(0xFFFF5A5F).copy(alpha = 0.82f)
        else -> Color(0xFFFF5A5F)
    }
}

private object StudyDateUtils {
    private val dfDayLocal = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    private val dfMonthLocal = ThreadLocal.withInitial { SimpleDateFormat("M月", Locale.CHINESE) }
    private val dfYearMonthLocal = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM", Locale.getDefault()) }
    private val dfWeekdayLocal = ThreadLocal.withInitial { SimpleDateFormat("E", Locale.CHINESE) }

    fun formatDay(date: Date): String = dfDayLocal.get()!!.format(date)
    fun formatMonth(date: Date): String = dfMonthLocal.get()!!.format(date)
    fun formatYearMonth(date: Date): String = dfYearMonthLocal.get()!!.format(date)
    fun formatWeekday(date: Date): String = dfWeekdayLocal.get()!!.format(date)
}

private fun computeTrendPoints(
    period: TrendPeriod,
    dailyMinutes: Map<String, Int>,
    todayKey: String
): List<TrendPoint> {
    val cal = Calendar.getInstance()

    return when (period) {
        TrendPeriod.PAST_7_DAYS -> {
            (0..6).reversed().map { i ->
                cal.time = Date()
                cal.add(Calendar.DAY_OF_YEAR, -i)
                val key = StudyDateUtils.formatDay(cal.time)
                val mins = dailyMinutes[key] ?: 0
                val count = if (mins > 0) (mins / 25).coerceAtLeast(1) else 0
                val label = StudyDateUtils.formatWeekday(cal.time).replace("周", "")
                TrendPoint(key, label, mins, count, key == todayKey)
            }
        }
        TrendPeriod.WEEKLY -> {
            (0..7).reversed().map { w ->
                cal.time = Date()
                cal.add(Calendar.WEEK_OF_YEAR, -w)
                val weekNum = cal.get(Calendar.WEEK_OF_YEAR)
                val baseTime = cal.timeInMillis
                var totalMins = 0
                val oneDayMs = 86_400_000L
                for (d in 0..6) {
                    cal.timeInMillis = baseTime + (d - 3) * oneDayMs
                    val k = StudyDateUtils.formatDay(cal.time)
                    totalMins += dailyMinutes[k] ?: 0
                }
                val count = if (totalMins > 0) (totalMins / 25).coerceAtLeast(1) else 0
                TrendPoint("w$weekNum", "第${weekNum}周", totalMins, count, w == 0)
            }
        }
        TrendPeriod.MONTHLY -> {
            (0..5).reversed().map { m ->
                cal.time = Date()
                cal.add(Calendar.MONTH, -m)
                val mLabel = StudyDateUtils.formatMonth(cal.time)
                val prefix = StudyDateUtils.formatYearMonth(cal.time)
                var totalMins = 0
                for (entry in dailyMinutes.entries) {
                    if (entry.key.startsWith(prefix)) {
                        totalMins += entry.value
                    }
                }
                val count = if (totalMins > 0) (totalMins / 25).coerceAtLeast(1) else 0
                TrendPoint(mLabel, mLabel, totalMins, count, m == 0)
            }
        }
    }
}

private fun computeDonutSlices(
    tasks: List<StudyTask>,
    focusRecords: List<FocusRecord>,
    palette: List<Color>
): List<DonutSliceData> {
    val map = mutableMapOf<String, Int>()
    focusRecords.forEach { rec ->
        val name = rec.courseName ?: "自由专注"
        map[name] = (map[name] ?: 0) + rec.durationMinutes
    }
    tasks.forEach { t ->
        val name = t.courseName ?: "待办任务"
        if (!map.containsKey(name) && t.completedMinutes > 0) {
            map[name] = t.completedMinutes
        }
    }
    if (map.isEmpty()) {
        map["高等数学"] = 50
        map["大学英语"] = 26
        map["计算机网络"] = 45
    }
    val total = map.values.sum().coerceAtLeast(1)
    return map.entries.sortedByDescending { it.value }.mapIndexed { idx, entry ->
        DonutSliceData(
            id = entry.key,
            name = entry.key,
            minutes = entry.value,
            percentage = entry.value.toDouble() / total.toDouble(),
            color = palette[idx % palette.size]
        )
    }
}

@Composable
private fun MacMetricBox(
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = color.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

