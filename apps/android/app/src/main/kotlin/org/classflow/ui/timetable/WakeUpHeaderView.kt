package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.classflow.data.model.Semester
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

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
    var editingSemester by remember { mutableStateOf<Semester?>(null) }
    var semesterToDelete by remember { mutableStateOf<Semester?>(null) }
    var showDeleteSemesterConfirm by remember { mutableStateOf(false) }

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

    // macOS 1:1 Parity Week Popover & Timetable Switcher (matching media_1792142244324_8535e84f.png)
    if (showWeekDrawer) {
        Dialog(onDismissRequest = { showWeekDrawer = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isDark) Color(0xFF1E202B) else Color(0xFFF9FAFB),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Header: "周次选择" & "回到本周" / "当前正是本周"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "周次选择",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isDark) Color.White else Color(0xFF1F2937)
                        )

                        if (selectedWeek != currentWeek) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        onWeekChange(currentWeek)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5A5F),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "回到本周",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF5A5F)
                                )
                            }
                        } else {
                            Text(
                                text = "当前正是本周",
                                fontSize = 11.5.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF9CA3AF)
                            )
                        }
                    }

                    // 2. Custom Week Slider with Floating White Pill Thumb
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (isDark) Color(0xFF2C2D3B) else Color(0xFFE5E7EB))
                                .pointerInput(totalWeeks) {
                                    detectTapGestures { offset ->
                                        val trackWidth = size.width - 46.dp.toPx()
                                        if (trackWidth > 0) {
                                            val progress = ((offset.x - 23.dp.toPx()) / trackWidth).coerceIn(0f, 1f)
                                            val targetWeek = (progress * (totalWeeks - 1)).roundToInt() + 1
                                            onWeekChange(targetWeek.coerceIn(1, totalWeeks))
                                        }
                                    }
                                }
                                .pointerInput(totalWeeks) {
                                    detectDragGestures { change, _ ->
                                        val trackWidth = size.width - 46.dp.toPx()
                                        if (trackWidth > 0) {
                                            val progress = ((change.position.x - 23.dp.toPx()) / trackWidth).coerceIn(0f, 1f)
                                            val targetWeek = (progress * (totalWeeks - 1)).roundToInt() + 1
                                            onWeekChange(targetWeek.coerceIn(1, totalWeeks))
                                        }
                                    }
                                }
                        ) {
                            val trackWidthDp = maxWidth - 46.dp
                            val fraction = if (totalWeeks > 1) (selectedWeek - 1).toFloat() / (totalWeeks - 1) else 0f
                            val thumbOffsetDp = trackWidthDp * fraction.coerceIn(0f, 1f)

                            Box(
                                modifier = Modifier
                                    .offset(x = thumbOffsetDp)
                                    .size(width = 46.dp, height = 26.dp)
                                    .align(Alignment.CenterStart)
                                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                                    .background(if (isDark) Color(0xFF383A4A) else Color.White, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$selectedWeek",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = if (isDark) Color.White else Color(0xFF1F2937)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "第1周",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF6B7280)
                            )
                            Text(
                                text = "第${totalWeeks}周",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF6B7280)
                            )
                        }
                    }

                    // 3. Quick Week Jump Pills Grid (5 columns x 4 rows)
                    val maxDisplayWeeks = minOf(25, maxOf(20, totalWeeks))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..maxDisplayWeeks).chunked(5).forEach { rowWeeks ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowWeeks.forEach { w ->
                                    val isSel = (w == selectedWeek)
                                    val isCur = (w == currentWeek)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSel) Color(0xFFFF5A5F)
                                                else if (isDark) Color(0xFF2C2D3B) else Color(0xFFE5E7EB)
                                            )
                                            .clickable { onWeekChange(w) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$w",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) Color.White else (if (isDark) Color.White else Color(0xFF1F2937))
                                        )
                                        if (isCur && !isSel) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF5A5F))
                                            )
                                        }
                                    }
                                }
                                if (rowWeeks.size < 5) {
                                    repeat(5 - rowWeeks.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFE5E7EB),
                        thickness = 1.dp
                    )

                    // 4. "切换课表" Header & Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "切换课表",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = if (isDark) Color.White else Color(0xFF1F2937)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "新建课表",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFFF5A5F),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        showCreateSemesterDialog = true
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )

                            Text(
                                text = "管理",
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF6B7280),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        showManageSemestersDialog = true
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 5. Horizontal Scroll of App-Style Semester Cards
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        semesters.forEach { sem ->
                            val isSelected = (sem.id == activeSemesterId)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(68.dp)
                                    .clickable {
                                        activeSemesterId = sem.id
                                        onSemesterChange(sem.name)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 68.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) {
                                                androidx.compose.ui.graphics.SolidColor(Color(0xFF2C2C2E))
                                            } else {
                                                androidx.compose.ui.graphics.Brush.linearGradient(
                                                    listOf(Color(0xFF5B7FB5), Color(0xFF7A68A5))
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选择",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                Text(
                                    text = sem.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) (if (isDark) Color.White else Color(0xFF1F2937)) else (if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF6B7280)),
                                    maxLines = 1,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Add Timetable Card ("新建")
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(68.dp)
                                .clickable {
                                    showCreateSemesterDialog = true
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 68.dp, height = 60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isDark) Color.White.copy(alpha = 0.3f) else Color(0xFFD1D5DB),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "新建课表",
                                    tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            Text(
                                text = "新建",
                                fontSize = 11.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF6B7280),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
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
                        label = { Text("总周数 (1-35)") },
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
                        val weeks = semWeeksInput.toIntOrNull()?.coerceIn(1, 35) ?: 20
                        val newSem = Semester(id = UUID.randomUUID().toString(), name = name, startDate = "2026-09-01", endDate = "2027-01-20", totalWeeks = weeks)
                        semesters.add(newSem)
                        activeSemesterId = newSem.id
                        onSemesterChange(newSem.name)
                        showCreateSemesterDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F))
                ) {
                    Text("创建", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSemesterDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Manage Semesters Dialog (Parity with macOS SemesterManageSheet)
    if (showManageSemestersDialog) {
        Dialog(onDismissRequest = { showManageSemestersDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) Color(0xFF1E202B) else Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "课表管理",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isDark) Color.White else Color(0xFF1F2937)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = { showCreateSemesterDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5A5F))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("新建课表", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }
                            }

                            Button(
                                onClick = { showManageSemestersDialog = false },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color(0xFF2C2D3B) else Color(0xFFE5E7EB),
                                    contentColor = if (isDark) Color.White else Color(0xFF1F2937)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("完成", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE5E7EB))

                    Spacer(modifier = Modifier.height(12.dp))

                    // List of Semesters
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        semesters.forEach { sem ->
                            val isCurrent = (sem.id == activeSemesterId)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF262838) else Color(0xFFF9FAFB),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isCurrent) 1.5.dp else 1.dp,
                                    if (isCurrent) Color(0xFFFF5A5F) else (if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFE5E7EB))
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isCurrent) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "当前主课表",
                                                tint = Color(0xFFFF5A5F),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .border(
                                                        1.5.dp,
                                                        if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF9CA3AF),
                                                        CircleShape
                                                    )
                                            )
                                        }

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = sem.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isDark) Color.White else Color(0xFF1F2937)
                                                )
                                                if (isCurrent) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFFF5A5F).copy(alpha = 0.12f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "当前主课表",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFFF5A5F)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = "开学: ${sem.startDate} · 共 ${sem.totalWeeks} 周",
                                                fontSize = 11.5.sp,
                                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6B7280)
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (!isCurrent) {
                                            TextButton(
                                                onClick = {
                                                    activeSemesterId = sem.id
                                                    onSemesterChange(sem.name)
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("设为主课表", fontSize = 11.5.sp, color = Color(0xFF4F46E5))
                                            }
                                        }

                                        IconButton(
                                            onClick = { editingSemester = sem },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "编辑",
                                                tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF4B5563),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (semesters.size > 1) {
                                                    semesterToDelete = sem
                                                    showDeleteSemesterConfirm = true
                                                }
                                            },
                                            enabled = semesters.size > 1,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "删除",
                                                tint = if (semesters.size > 1) MaterialTheme.colorScheme.error else Color.Gray.copy(alpha = 0.35f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "共 ${semesters.size} 个课表。点击「设为主课表」切换主视图显示的学期。",
                        fontSize = 11.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF9CA3AF)
                    )
                }
            }
        }
    }

    // Inline Edit Semester Dialog
    if (editingSemester != null) {
        val sem = editingSemester!!
        var editName by remember(sem.id) { mutableStateOf(sem.name) }
        var editWeeks by remember(sem.id) { mutableStateOf(sem.totalWeeks.toString()) }
        var editStart by remember(sem.id) { mutableStateOf(sem.startDate) }

        AlertDialog(
            onDismissRequest = { editingSemester = null },
            title = { Text("编辑课表信息", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("课表名称") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editStart,
                        onValueChange = { editStart = it },
                        label = { Text("开学日期 (第一周周一, yyyy-MM-dd)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editWeeks,
                        onValueChange = { editWeeks = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("总周数 (1-35)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = editName.trim().ifEmpty { sem.name }
                        val weeks = editWeeks.toIntOrNull()?.coerceIn(1, 35) ?: sem.totalWeeks
                        val idx = semesters.indexOfFirst { it.id == sem.id }
                        if (idx >= 0) {
                            val updated = sem.copy(name = trimmedName, totalWeeks = weeks, startDate = editStart.trim().ifEmpty { sem.startDate })
                            semesters[idx] = updated
                            if (updated.id == activeSemesterId) {
                                onSemesterChange(updated.name)
                            }
                        }
                        editingSemester = null
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F))
                ) {
                    Text("保存修改", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSemester = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Delete Semester Confirmation Dialog
    if (showDeleteSemesterConfirm && semesterToDelete != null) {
        val targetSem = semesterToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteSemesterConfirm = false
                semesterToDelete = null
            },
            title = {
                Text(
                    text = "确定要删除课表「${targetSem.name}」吗？",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "该课表下的所有课程安排与调课记录都将被移除，此操作不可撤销。",
                    fontSize = 13.5.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF4B5563)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val wasActive = (targetSem.id == activeSemesterId)
                        semesters.remove(targetSem)
                        if (wasActive && semesters.isNotEmpty()) {
                            val nextActive = semesters.first()
                            activeSemesterId = nextActive.id
                            onSemesterChange(nextActive.name)
                        }
                        showDeleteSemesterConfirm = false
                        semesterToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除课表及其全部课程", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteSemesterConfirm = false
                        semesterToDelete = null
                    }
                ) {
                    Text("取消")
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
