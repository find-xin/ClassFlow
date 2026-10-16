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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
fun StudyScreen() {
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

    // Tasks list
    val tasks = remember {
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

    val activeTask = remember(tasks, activeTaskId) {
        tasks.firstOrNull { it.id == activeTaskId }
    }

    // Recent Focus Sessions Log
    val focusRecords = remember {
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

    // Daily focus minutes history for heatmap & analytics
    val dailyMinutesMap = remember {
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

    // Timer Ticker
    LaunchedEffect(isRunning, secondsRemaining, countUpSeconds) {
        if (isRunning) {
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
                    dailyMinutesMap[todayKey] = (dailyMinutesMap[todayKey] ?: 0) + added

                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    focusRecords.add(
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
                        val idx = tasks.indexOf(activeTask)
                        if (idx >= 0) {
                            val newCompleted = activeTask.completedMinutes + added
                            tasks[idx] = activeTask.copy(
                                completedMinutes = newCompleted,
                                isCompleted = newCompleted >= activeTask.targetMinutes
                            )
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

        // 2. Scrollable Content Area
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            if (selectedSubTab == 0) {
                // Focus Arena Card (macOS 1:1 Parity)
                item {
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
                                dailyMinutesMap[todayKey] = (dailyMinutesMap[todayKey] ?: 0) + earnedMinutes

                                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                focusRecords.add(
                                    0,
                                    FocusRecord(
                                        taskTitle = activeTask?.title ?: "自由专注",
                                        courseName = activeTask?.courseName,
                                        durationMinutes = earnedMinutes,
                                        completedAt = "今日 $timeStr"
                                    )
                                )

                                if (activeTask != null) {
                                    val idx = tasks.indexOf(activeTask)
                                    if (idx >= 0) {
                                        val newCompleted = activeTask.completedMinutes + earnedMinutes
                                        tasks[idx] = activeTask.copy(
                                            completedMinutes = newCompleted,
                                            isCompleted = newCompleted >= activeTask.targetMinutes
                                        )
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
                }

                // Study ToDo Task Management Card
                item {
                    FocusTaskListCard(
                        tasks = tasks,
                        taskFilter = taskFilter,
                        activeTaskId = activeTaskId,
                        onFilterChange = { taskFilter = it },
                        onNewTaskClick = { showCreateTaskDialog = true },
                        onToggleTask = { task ->
                            val idx = tasks.indexOf(task)
                            if (idx >= 0) {
                                tasks[idx] = task.copy(isCompleted = !task.isCompleted)
                            }
                        },
                        onStartTask = { task ->
                            activeTaskId = task.id
                            if (!isRunning) {
                                focusMode = StudyFocusMode.POMODORO
                                focusDurationMinutes = 25
                                secondsRemaining = 25 * 60
                            }
                        },
                        onDeleteTask = { task ->
                            tasks.remove(task)
                            if (activeTaskId == task.id) activeTaskId = null
                        }
                    )
                }

                // Recent Focus Records Card
                if (focusRecords.isNotEmpty()) {
                    item {
                        RecentFocusRecordsCard(records = focusRecords)
                    }
                }
            } else {
                // Statistics & Heatmap Tab
                item {
                    PomodoroStatisticsCard(
                        dailyMinutesMap = dailyMinutesMap,
                        completedPomodoroCount = completedPomodoroCount,
                        tasks = tasks
                    )
                }
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

                    tasks.filter { !it.isCompleted }.forEach { task ->
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

    // Create Task Dialog
    if (showCreateTaskDialog) {
        var taskTitle by remember { mutableStateOf("") }
        var courseName by remember { mutableStateOf("") }
        var targetMinutesStr by remember { mutableStateOf("45") }

        AlertDialog(
            onDismissRequest = { showCreateTaskDialog = false },
            title = { Text("新建待办任务", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (taskTitle.isNotBlank()) {
                            val mins = targetMinutesStr.toIntOrNull()?.coerceIn(5, 360) ?: 45
                            tasks.add(
                                0,
                                StudyTask(
                                    title = taskTitle.trim(),
                                    courseName = courseName.trim().ifEmpty { null },
                                    targetMinutes = mins
                                )
                            )
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
            val ringGradient = Brush.sweepGradient(
                listOf(
                    Color(0xFF2563EB),
                    Color(0xFF60A5FA),
                    Color(0xFF2563EB)
                )
            )

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
                                            text = "⏱ ${task.completedMinutes}/${task.targetMinutes}m",
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
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
private fun RecentFocusRecordsCard(records: List<FocusRecord>) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "最近专注成果",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

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
@Composable
private fun PomodoroStatisticsCard(
    dailyMinutesMap: Map<String, Int>,
    completedPomodoroCount: Int,
    tasks: List<StudyTask>
) {
    val totalMinutes = dailyMinutesMap.values.sum()
    val totalHours = String.format("%.1f", totalMinutes / 60.0)
    val completedTasks = tasks.count { it.isCompleted }
    val completionRate = if (tasks.isNotEmpty()) "${(completedTasks * 100) / tasks.size}%" else "0%"

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 4 Core Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricPill(
                title = "连续专注",
                value = "3 天",
                color = Color(0xFF2563EB),
                modifier = Modifier.weight(1f)
            )
            MetricPill(
                title = "累计投入",
                value = "$totalHours 小时",
                color = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricPill(
                title = "收获番茄",
                value = "$completedPomodoroCount 个",
                color = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )
            MetricPill(
                title = "待办完成率",
                value = completionRate,
                color = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f)
            )
        }

        // GitHub-Style Heatmap Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "学期专注热力图",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "过去 12 周",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Heatmap Matrix
                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val todayCal = Calendar.getInstance()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                ) {
                    // 12 columns (weeks), each 7 rows (days)
                    for (weekOffset in 11 downTo 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.5.dp)) {
                            for (dayOfWeek in 0..6) {
                                val cellCal = Calendar.getInstance().apply {
                                    time = todayCal.time
                                    add(Calendar.DAY_OF_YEAR, -(weekOffset * 7 + (6 - dayOfWeek)))
                                }
                                val dateKey = df.format(cellCal.time)
                                val mins = dailyMinutesMap[dateKey] ?: 0

                                val cellColor = when {
                                    mins == 0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    mins < 30 -> Color(0xFF9BE9A8)
                                    mins < 60 -> Color(0xFF40C463)
                                    mins < 90 -> Color(0xFF30A14E)
                                    else -> Color(0xFF216E39)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clip(RoundedCornerShape(2.5.dp))
                                        .background(cellColor)
                                )
                            }
                        }
                    }
                }

                // Heatmap Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("少", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    listOf(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        Color(0xFF9BE9A8),
                        Color(0xFF40C463),
                        Color(0xFF30A14E),
                        Color(0xFF216E39)
                    ).forEach { c ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.5.dp)
                                .size(9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(c)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("多", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 7-Day Bar Chart Trend Card
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "近 7 天投入趋势 (分钟)",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                val sample7Days = listOf(45, 60, 25, 80, 50, 90, 75)
                val maxMins = (sample7Days.maxOrNull() ?: 90).coerceAtLeast(60)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    sample7Days.forEachIndexed { idx, mins ->
                        val barHeightFactor = (mins.toFloat() / maxMins).coerceIn(0.1f, 1f)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${mins}m",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight(barHeightFactor)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF2563EB), Color(0xFF60A5FA))
                                        )
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = dayNames[idx],
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
