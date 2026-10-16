package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.Semester
import java.text.SimpleDateFormat
import java.util.*

/**
 * WakeUp 课程表 Aesthetic Top Bar matching Image 3:
 * - Top-Left:
 *     Line 1: 2026/10/16
 *     Line 2: 第 7 周 周五
 *     (Clicking opens floating Week Slider & Timetable Switcher drawer)
 * - Top-Right (4 distinct action icon buttons):
 *     (+) Add (Course / Schedule)
 *     (↓) Import (.classflow / .ics / Paste JSON)
 *     (⤴) Export (Export .ics / Full Backup .classflow)
 *     (⋮) More / Settings Drawer
 */
@Composable
fun WakeUpHeaderView(
    currentWeek: Int,
    selectedWeek: Int,
    totalWeeks: Int,
    semesterName: String,
    displayMode: String = "week", // "week" or "day"
    isDark: Boolean = false,
    isWallpaperDark: Boolean = true,
    onWeekChange: (Int) -> Unit,
    onToggleDisplayMode: (String) -> Unit = {},
    onSemesterChange: (String) -> Unit = {},
    onAddCourseClick: () -> Unit,
    onAddScheduleClick: () -> Unit,
    onImportClick: (ImportType) -> Unit,
    onExportClick: (ExportType) -> Unit,
    onOpenThreeDotsMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showWeekDrawer by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showCreateSemesterDialog by remember { mutableStateOf(false) }
    var showManageSemestersDialog by remember { mutableStateOf(false) }

    val semesters = remember {
        mutableStateListOf(
            Semester(id = "sem_main", name = semesterName.ifEmpty { "大四上学期" }, startDate = "2026-09-01", endDate = "2027-01-20", totalWeeks = totalWeeks),
            Semester(id = "sem_prev", name = "大三下学期", startDate = "2026-02-25", endDate = "2026-07-15", totalWeeks = 20)
        )
    }
    var activeSemesterId by remember { mutableStateOf("sem_main") }

    val textPrimary = if (isWallpaperDark) Color.White else Color(0xFF1F2937)
    val textSecondary = if (isWallpaperDark) Color.White.copy(alpha = 0.85f) else Color(0xFF6B7280)
    val iconTint = if (isWallpaperDark) Color.White else Color(0xFF374151)
    val menuBg = if (isDark) Color(0xFF222432) else Color.White

    val todayWeekdayShort = remember {
        val cal = Calendar.getInstance()
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "一"
            Calendar.TUESDAY -> "二"
            Calendar.WEDNESDAY -> "三"
            Calendar.THURSDAY -> "四"
            Calendar.FRIDAY -> "五"
            Calendar.SATURDAY -> "六"
            Calendar.SUNDAY -> "日"
            else -> "五"
        }
    }

    val currentDateStr = remember {
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left: Clickable Date & Week Title (exact WakeUp layout Image 1)
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { showWeekDrawer = true }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = currentDateStr,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 $selectedWeek 周 周$todayWeekdayShort",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                    fontWeight = FontWeight.Medium,
                    color = textSecondary
                )
                if (selectedWeek != currentWeek) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF5A5F))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "非本周",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    if (showWeekDrawer) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 2. Center-Right: Week / Day View Toggle & 4 Action Icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Week / Day View Toggle Capsule (macOS & mobile parity)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isWallpaperDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (displayMode == "week") (if (isWallpaperDark) Color.White.copy(alpha = 0.25f) else Color.White) else Color.Transparent)
                        .clickable { onToggleDisplayMode("week") }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "周",
                        fontSize = 11.5.sp,
                        fontWeight = if (displayMode == "week") FontWeight.Bold else FontWeight.Medium,
                        color = textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (displayMode == "day") (if (isWallpaperDark) Color.White.copy(alpha = 0.25f) else Color.White) else Color.Transparent)
                        .clickable { onToggleDisplayMode("day") }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "日",
                        fontSize = 11.5.sp,
                        fontWeight = if (displayMode == "day") FontWeight.Bold else FontWeight.Medium,
                        color = textPrimary
                    )
                }
            }

            // (+) Add Action (macOS popover menu anchored to button)
            Box {
                IconButton(
                    onClick = { showAddMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加",
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                SlotActionMenu(
                    expanded = showAddMenu,
                    dayOfWeek = 1,
                    section = 1,
                    isDark = isDark,
                    onDismiss = { showAddMenu = false },
                    onAddCourse = { _, _ ->
                        showAddMenu = false
                        onAddCourseClick()
                    },
                    onAddSchedule = { _, _ ->
                        showAddMenu = false
                        onAddScheduleClick()
                    }
                )
            }

            // (↓) Import Menu
            Box {
                IconButton(
                    onClick = { showImportMenu = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "导入",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false },
                    modifier = Modifier.background(menuBg)
                ) {
                    DropdownMenuItem(
                        text = { Text("从 .classflow 备份包导入...", fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showImportMenu = false
                            onImportClick(ImportType.CLASSFLOW)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("从 .ics 日历文件导入...", fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showImportMenu = false
                            onImportClick(ImportType.ICS)
                        }
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2))
                    DropdownMenuItem(
                        text = { Text("粘贴教务脚本 / WakeUp JSON 导入...", fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showImportMenu = false
                            onImportClick(ImportType.PASTE_JSON)
                        }
                    )
                }
            }

            // (⤴) Export Menu
            Box {
                IconButton(
                    onClick = { showExportMenu = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "导出",
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }

                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false },
                    modifier = Modifier.background(menuBg)
                ) {
                    DropdownMenuItem(
                        text = { Text("导出当前课表为日历 (.ics)...", fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showExportMenu = false
                            onExportClick(ExportType.ICS)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出完整全量备份包 (.classflow)...", fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showExportMenu = false
                            onExportClick(ExportType.CLASSFLOW)
                        }
                    )
                }
            }

            // (⋮) More / Settings Menu
            IconButton(
                onClick = onOpenThreeDotsMenu,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "设置与快捷功能",
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // WakeUp Style Floating Week Slider & Timetable Card Drawer (matching Image 3)
    if (showWeekDrawer) {
        AlertDialog(
            onDismissRequest = { showWeekDrawer = false },
            containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("周数", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isDark) Color.White else Color(0xFF1F2937))
                    TextButton(
                        onClick = {
                            onWeekChange(currentWeek)
                            showWeekDrawer = false
                        }
                    ) {
                        Text("回到当前周 (第${currentWeek}周)", fontSize = 12.sp, color = Color(0xFF4F46E5))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Smooth Week Slider matching Image 3
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "正在查看: 第 $selectedWeek 周",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
                                fontSize = 14.sp
                            )
                            Text(
                                text = "共 $totalWeeks 周",
                                fontSize = 12.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280)
                            )
                        }

                        Slider(
                            value = selectedWeek.toFloat(),
                            onValueChange = { onWeekChange(it.toInt()) },
                            valueRange = 1f..totalWeeks.toFloat(),
                            steps = totalWeeks - 2,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4F46E5),
                                activeTrackColor = Color(0xFF4F46E5)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFE5E7EB))

                    // Timetable Semester Card Switcher matching Image 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("课表", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isDark) Color.White else Color(0xFF1F2937))
                        Row {
                            TextButton(onClick = { showCreateSemesterDialog = true }) {
                                Text("新建课表", fontSize = 12.sp, color = Color(0xFF4F46E5))
                            }
                            TextButton(onClick = { showManageSemestersDialog = true }) {
                                Text("管理", fontSize = 12.sp, color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280))
                            }
                        }
                    }

                    // Semester Cards Thumbnail List (Horizontal scrollable)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        semesters.forEach { sem ->
                            val isSelected = (sem.id == activeSemesterId)
                            Box(
                                modifier = Modifier
                                    .width(135.dp)
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) {
                                            if (isDark) Color(0xFF2C2250) else Color(0xFFEEF2FF)
                                        } else {
                                            if (isDark) Color(0xFF262838) else Color(0xFFF3F4F8)
                                        }
                                    )
                                    .border(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) Color(0xFF4F46E5) else (if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE5E7EB)),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        activeSemesterId = sem.id
                                        onSemesterChange(sem.name)
                                    }
                                    .padding(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = sem.name,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isDark) Color.White else Color(0xFF1F2937),
                                            maxLines = 1
                                        )
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF4F46E5),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isSelected) "当前主课表 · 共${sem.totalWeeks}周" else "共${sem.totalWeeks}周",
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color(0xFF4F46E5) else (if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280))
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showWeekDrawer = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("完成", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Create Semester Dialog
    if (showCreateSemesterDialog) {
        var semNameInput by remember { mutableStateOf("") }
        var semWeeksInput by remember { mutableStateOf("20") }
        AlertDialog(
            onDismissRequest = { showCreateSemesterDialog = false },
            title = { Text("新建课表", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = semNameInput,
                        onValueChange = { semNameInput = it },
                        label = { Text("课表名称") },
                        placeholder = { Text("例如：大四下学期") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = semWeeksInput,
                        onValueChange = { semWeeksInput = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("总周数 (1-30)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = semNameInput.trim().ifEmpty { "新课表" }
                        val weeks = semWeeksInput.toIntOrNull()?.coerceIn(1, 30) ?: 20
                        val newSem = Semester(id = UUID.randomUUID().toString(), name = name, startDate = "2026-09-01", endDate = "2027-01-20", totalWeeks = weeks)
                        semesters.add(newSem)
                        activeSemesterId = newSem.id
                        onSemesterChange(newSem.name)
                        showCreateSemesterDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSemesterDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Manage Semesters Dialog
    if (showManageSemestersDialog) {
        AlertDialog(
            onDismissRequest = { showManageSemestersDialog = false },
            title = { Text("课表管理", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    semesters.forEach { sem ->
                        val isCurrent = (sem.id == activeSemesterId)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isCurrent) 1.5.dp else 0.5.dp,
                                if (isCurrent) Color(0xFF4F46E5) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sem.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isCurrent) "当前主课表 · 共${sem.totalWeeks}周" else "共${sem.totalWeeks}周",
                                        fontSize = 11.5.sp,
                                        color = if (isCurrent) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!isCurrent) {
                                        TextButton(onClick = {
                                            activeSemesterId = sem.id
                                            onSemesterChange(sem.name)
                                        }) {
                                            Text("设为主课表", fontSize = 12.sp)
                                        }

                                        if (semesters.size > 1) {
                                            IconButton(
                                                onClick = { semesters.remove(sem) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "删除",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "当前主课表",
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showManageSemestersDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("完成")
                }
            }
        )
    }
}

enum class ImportType {
    CLASSFLOW,
    ICS,
    PASTE_JSON
}

enum class ExportType {
    ICS,
    CLASSFLOW
}
