package org.classflow.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.classflow.data.model.StudyTask
import java.text.SimpleDateFormat
import java.util.*

enum class FocusTimerMode(val label: String) {
    COUNTDOWN("倒计时"),
    COUNT_UP("正向计时")
}

enum class StatsScope(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年")
}

@Composable
fun StudyScreen() {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: 专注时钟, 1: 投入统计

    // Timer State
    var timerMode by remember { mutableStateOf(FocusTimerMode.COUNTDOWN) }
    var targetMinutesInput by remember { mutableStateOf("25") }
    var targetDurationSeconds by remember { mutableIntStateOf(25 * 60) }
    var currentSeconds by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }

    // Statistics Scope
    var statsScope by remember { mutableStateOf(StatsScope.MONTH) }

    // Study Tasks (with clean minute units, no pomodoro/tomato icons or "专注" prefix)
    val tasks = remember {
        mutableStateListOf(
            StudyTask(title = "高等数学 · 习题册第4章复习", courseName = "高等数学", targetMinutes = 60, completedMinutes = 45),
            StudyTask(title = "大学英语 · 听力真题精听与词汇", courseName = "大学英语", targetMinutes = 45, completedMinutes = 30),
            StudyTask(title = "计算机网络 · 路由仿真实验报告", courseName = "计算机网络", targetMinutes = 90, completedMinutes = 30)
        )
    }

    // Daily focus history
    val dailyMinutesMap = remember {
        val map = mutableStateMapOf<String, Int>()
        val cal = Calendar.getInstance()
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (i in 0..45) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = df.format(cal.time)
            val mins = if (i % 2 == 0) (i * 15 + 25) % 150 else (if (i % 3 == 0) 45 else 0)
            if (mins > 0) map[key] = mins
        }
        map
    }

    // Isolated timer ticker
    LaunchedEffect(isRunning, currentSeconds) {
        if (isRunning) {
            delay(1000L)
            if (timerMode == FocusTimerMode.COUNTDOWN) {
                if (currentSeconds > 0) {
                    currentSeconds -= 1
                } else {
                    isRunning = false
                    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val todayKey = df.format(Date())
                    val added = targetDurationSeconds / 60
                    dailyMinutesMap[todayKey] = (dailyMinutesMap[todayKey] ?: 0) + added
                }
            } else {
                currentSeconds += 1
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Sub-Tab Segmented Selector matching macOS StudyFocusView
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.width(260.dp)) {
                SegmentedButton(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("专注时钟", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                SegmentedButton(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("投入统计", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

        // High performance LazyColumn scrolling
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedSubTab == 0) {
                // Mode Toggle & Direct Time Input
                item(key = "timer_controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FocusTimerMode.values().forEach { mode ->
                                FilterChip(
                                    selected = timerMode == mode,
                                    onClick = {
                                        if (!isRunning) {
                                            timerMode = mode
                                            if (mode == FocusTimerMode.COUNTDOWN) {
                                                val mins = targetMinutesInput.toIntOrNull() ?: 25
                                                targetDurationSeconds = mins * 60
                                                currentSeconds = targetDurationSeconds
                                            } else {
                                                currentSeconds = 0
                                            }
                                        }
                                    },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        // Direct Minute Input (支持键盘直接手动输入)
                        if (timerMode == FocusTimerMode.COUNTDOWN && !isRunning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = targetMinutesInput,
                                    onValueChange = { newVal ->
                                        val filtered = newVal.filter { it.isDigit() }.take(3)
                                        targetMinutesInput = filtered
                                        val mins = filtered.toIntOrNull() ?: 25
                                        targetDurationSeconds = mins * 60
                                        currentSeconds = targetDurationSeconds
                                    },
                                    label = { Text("设定分钟") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.width(110.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(15, 25, 45, 60, 90).forEach { preset ->
                                        FilterChip(
                                            selected = targetMinutesInput == preset.toString(),
                                            onClick = {
                                                targetMinutesInput = preset.toString()
                                                targetDurationSeconds = preset * 60
                                                currentSeconds = targetDurationSeconds
                                            },
                                            label = { Text("${preset}m", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Aesthetic Radial Clock Card
                item(key = "clock_display") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val mins = currentSeconds / 60
                            val secs = currentSeconds % 60
                            val timeStr = String.format("%02d:%02d", mins, secs)

                            Text(
                                text = timeStr,
                                fontSize = 60.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Button(
                                    onClick = { isRunning = !isRunning },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(46.dp)
                                ) {
                                    Icon(
                                        if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isRunning) "暂停" else "开始", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        isRunning = false
                                        if (timerMode == FocusTimerMode.COUNTDOWN) {
                                            currentSeconds = targetDurationSeconds
                                        } else {
                                            currentSeconds = 0
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(46.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("重置")
                                }
                            }
                        }
                    }
                }

                // Tasks List Header (clean, no redundant subtitle explanation)
                item(key = "task_header") {
                    Text(
                        text = "自习任务",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tasks List Items
                items(tasks, key = { it.title }) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (task.completedMinutes >= task.targetMinutes) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (task.completedMinutes >= task.targetMinutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.title, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                task.courseName?.let {
                                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(
                                text = "${task.completedMinutes} / ${task.targetMinutes} 分钟",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Statistics Tab (Clean: No redundant subtitle explanations)
                item(key = "stats_scope") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatsScope.values().forEach { scope ->
                            FilterChip(
                                selected = statsScope == scope,
                                onClick = { statsScope = scope },
                                label = { Text(scope.label) }
                            )
                        }
                    }
                }

                // 1. Metric Cards
                item(key = "stats_metrics") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeDays = dailyMinutesMap.filter { it.value > 0 }.size
                        val totalMinutes = dailyMinutesMap.values.sum()
                        val totalHours = String.format("%.1f", totalMinutes / 60.0)

                        CleanMetricCard(modifier = Modifier.weight(1f), title = "连续打卡", value = "5 天")
                        CleanMetricCard(modifier = Modifier.weight(1f), title = "累计专注", value = "$totalHours 小时")
                        CleanMetricCard(modifier = Modifier.weight(1f), title = "活跃天数", value = "$activeDays 天")
                    }
                }

                // 2. Course Breakdown
                item(key = "course_breakdown") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("学科投入时间分布", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            CleanCourseProgressRow("高等数学", 120, 0.42f, Color(0xFF4A90E2))
                            CleanCourseProgressRow("大学英语", 85, 0.28f, Color(0xFF5B7FB5))
                            CleanCourseProgressRow("计算机网络", 65, 0.20f, Color(0xFF059669))
                            CleanCourseProgressRow("自由自习", 30, 0.10f, Color(0xFFE07A5F))
                        }
                    }
                }

                // 3. Heatmap Matrix
                item(key = "stats_heatmap") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
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
                                Text("近一月专注趋势", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("少", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    CleanHeatBox(level = 0)
                                    CleanHeatBox(level = 1)
                                    CleanHeatBox(level = 2)
                                    CleanHeatBox(level = 3)
                                    CleanHeatBox(level = 4)
                                    Text("多", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val cal = Calendar.getInstance()
                                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                for (week in 11 downTo 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        for (day in 0..6) {
                                            cal.time = Date()
                                            cal.add(Calendar.WEEK_OF_YEAR, -week)
                                            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY + day)
                                            val key = df.format(cal.time)
                                            val mins = dailyMinutesMap[key] ?: 0
                                            val level = when {
                                                mins == 0 -> 0
                                                mins <= 25 -> 1
                                                mins <= 50 -> 2
                                                mins <= 90 -> 3
                                                else -> 4
                                            }
                                            CleanHeatBox(level = level)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CleanMetricCard(modifier: Modifier = Modifier, title: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun CleanHeatBox(level: Int) {
    val color = when (level) {
        0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        1 -> Color(0xFF4A90E2).copy(alpha = 0.35f)
        2 -> Color(0xFF4A90E2).copy(alpha = 0.60f)
        3 -> Color(0xFF4A90E2).copy(alpha = 0.85f)
        else -> Color(0xFF4A90E2)
    }
    Box(
        modifier = Modifier
            .size(13.dp)
            .clip(RoundedCornerShape(2.5.dp))
            .background(color)
    )
}

@Composable
fun CleanCourseProgressRow(name: String, minutes: Int, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("$minutes 分钟", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
