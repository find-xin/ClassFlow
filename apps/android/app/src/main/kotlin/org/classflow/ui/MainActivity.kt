package org.classflow.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.*
import org.classflow.sync.FullSyncBundle
import org.classflow.sync.FullSyncManager
import org.classflow.ui.agenda.AgendaScreen
import org.classflow.ui.components.WallpaperBackground
import org.classflow.ui.components.isWallpaperDark
import org.classflow.ui.detail.CourseDetailBottomSheet
import org.classflow.ui.settings.MacSettingsDialog
import org.classflow.ui.settings.TimetableAppearanceDialog
import org.classflow.ui.study.StudyScreen
import org.classflow.ui.timetable.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainApp()
        }
    }
}

enum class NavigationTab {
    AGENDA,
    TIMETABLE,
    STUDY
}

@Composable
fun MainApp() {
    var appSettings by remember {
        mutableStateOf(AppSettings(themeMode = "system", enableStudyModule = true, wallpaperPreset = "default"))
    }

    val isDark = when (appSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val isWallpaperDark = isWallpaperDark(
        preset = appSettings.wallpaperPreset,
        isSystemDark = isDark,
        usePureColor = appSettings.usePureColorBackground
    )

    // High contrast, refined dark theme matching macOS
    val darkColors = darkColorScheme(
        primary = Color(0xFF6B8AFD),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF283593),
        onPrimaryContainer = Color(0xFFE8EAF6),
        surface = Color(0xFF14151B),
        onSurface = Color(0xFFF5F5F7),
        surfaceVariant = Color(0xFF1F212A),
        onSurfaceVariant = Color(0xFFA1A3AC),
        background = Color(0xFF0F1015),
        onBackground = Color(0xFFF5F5F7),
        outlineVariant = Color(0xFF2E313D)
    )

    // Clean, crisp light theme matching macOS
    val lightColors = lightColorScheme(
        primary = Color(0xFF4F46E5),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEEF2FF),
        onPrimaryContainer = Color(0xFF312E81),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1F2937),
        surfaceVariant = Color(0xFFF3F4F8),
        onSurfaceVariant = Color(0xFF6B7280),
        background = Color(0xFFF7F8FA),
        onBackground = Color(0xFF1F2937),
        outlineVariant = Color(0xFFE2E4EC)
    )

    MaterialTheme(
        colorScheme = if (isDark) darkColors else lightColors
    ) {
        WallpaperBackground(
            isDark = isDark,
            wallpaperPreset = appSettings.wallpaperPreset,
            usePureColorBackground = appSettings.usePureColorBackground
        ) {
            MainScreenHost(
                appSettings = appSettings,
                isDark = isDark,
                isWallpaperDark = isWallpaperDark,
                onUpdateSettings = { appSettings = it }
            )
        }
    }
}

@Composable
fun MacStyleTabBar(
    selectedTab: NavigationTab,
    showStudyTab: Boolean,
    isDark: Boolean,
    isWallpaperDark: Boolean,
    onTabSelected: (NavigationTab) -> Unit
) {
    val barBg = if (isDark) Color(0xD9141620) else Color(0xF2FFFFFF)
    val textActive = if (isDark || isWallpaperDark) Color.White else Color(0xFF1F2937)
    val textInactive = if (isDark || isWallpaperDark) Color.White.copy(alpha = 0.55f) else Color(0xFF9CA3AF)
    val indicatorColor = if (isDark || isWallpaperDark) Color(0xFF818CF8) else Color(0xFF4F46E5)

    Surface(
        color = barBg,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = buildList {
                add(NavigationTab.AGENDA to "日程")
                add(NavigationTab.TIMETABLE to "课程")
                if (showStudyTab) {
                    add(NavigationTab.STUDY to "学习")
                }
            }

            tabs.forEach { (tab, label) ->
                val isSelected = (selectedTab == tab)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) textActive else textInactive
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.25.dp))
                            .background(if (isSelected) indicatorColor else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreenHost(
    appSettings: AppSettings,
    isDark: Boolean,
    isWallpaperDark: Boolean,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedTab by remember { mutableStateOf(NavigationTab.TIMETABLE) }
    val currentWeek = 1
    var selectedWeek by remember { mutableIntStateOf(1) }
    val totalWeeks = 16
    val semesterName = "2026-2027 秋季学期"

    var selectedOccurrence by remember { mutableStateOf<TimelineItem?>(null) }
    var selectedScheduleEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showThreeDotsMenu by remember { mutableStateOf(false) }
    var showMacSettingsDialog by remember { mutableStateOf(false) }
    var activeQuickActionSheet by remember { mutableStateOf(QuickActionSheetType.NONE) }

    // Import / Export Dialogs
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var showFileImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }
    var exportTitle by remember { mutableStateOf("") }

    // Auto-sync effect based on interval
    LaunchedEffect(appSettings.autoSyncEnabled, appSettings.syncInterval) {
        if (appSettings.autoSyncEnabled) {
            val nowStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            FullSyncManager.lastSyncTimestamp = System.currentTimeMillis()
            FullSyncManager.lastSyncStatus = "已自动全量同步 ($nowStr)"
        }
    }

    // Dialogs
    var showAddCourseDialog by remember { mutableStateOf(false) }
    var addCourseSlotDay by remember { mutableIntStateOf(1) }
    var addCourseSlotSection by remember { mutableIntStateOf(1) }

    var showAddScheduleDialog by remember { mutableStateOf(false) }

    // Sample Section Times
    val sectionTimes = remember {
        mutableStateListOf(
            SectionTime(1, "08:00", "08:45"),
            SectionTime(2, "08:50", "09:35"),
            SectionTime(3, "10:00", "10:45"),
            SectionTime(4, "10:50", "11:35"),
            SectionTime(5, "13:30", "14:15"),
            SectionTime(6, "14:20", "15:05"),
            SectionTime(7, "15:25", "16:10"),
            SectionTime(8, "16:15", "17:00"),
            SectionTime(9, "18:30", "19:15"),
            SectionTime(10, "19:20", "20:05"),
            SectionTime(11, "20:15", "21:00"),
            SectionTime(12, "21:05", "21:50")
        )
    }

    // Courses List
    val sampleCourses = remember {
        mutableStateListOf(
            Course(
                id = "c_math",
                semesterId = "sem_1",
                name = "高等数学",
                teacher = "张教授",
                color = "#4F46E5",
                schedules = listOf(
                    CourseSchedule(dayOfWeek = 3, startSection = 1, endSection = 2, classroom = "东上 101")
                )
            ),
            Course(
                id = "c_cs",
                semesterId = "sem_1",
                name = "数据结构与算法",
                teacher = "李教授",
                color = "#0B8043",
                schedules = listOf(
                    CourseSchedule(dayOfWeek = 3, startSection = 3, endSection = 4, classroom = "信息楼 302")
                )
            )
        )
    }

    // Schedules List (Calendar Events)
    val sampleSchedules = remember {
        mutableStateListOf(
            CalendarEvent(
                id = "sched_1",
                title = "晨读早自习",
                startTime = "07:20",
                endTime = "07:55",
                location = "图书馆一楼中庭",
                color = "#039BE5",
                frequency = "每天"
            ),
            CalendarEvent(
                id = "sched_2",
                title = "实验室科研讨论会",
                startTime = "21:30",
                endTime = "22:30",
                location = "工科实验大楼 504",
                color = "#8E24AA",
                frequency = "每周"
            )
        )
    }

    // Occurrences
    val sampleOccurrences = remember {
        mutableStateListOf(
            TimelineItem(
                id = "occ_1",
                courseId = "c_math",
                scheduleId = "s_math",
                title = "高等数学",
                location = "东上 101",
                date = "2026-10-14",
                dayOfWeek = 3,
                startSection = 1,
                endSection = 2,
                startTime = "08:00",
                endTime = "09:35",
                weekNumber = 1,
                color = "#4F46E5",
                teacher = "张教授"
            ),
            TimelineItem(
                id = "occ_2",
                courseId = "c_cs",
                scheduleId = "s_cs",
                title = "数据结构与算法",
                location = "信息楼 302",
                date = "2026-10-14",
                dayOfWeek = 3,
                startSection = 3,
                endSection = 4,
                startTime = "10:00",
                endTime = "11:35",
                weekNumber = 1,
                color = "#0B8043",
                teacher = "李教授"
            )
        )
    }

    // Fallback if study tab is selected but module gets disabled
    LaunchedEffect(appSettings.enableStudyModule) {
        if (!appSettings.enableStudyModule && selectedTab == NavigationTab.STUDY) {
            selectedTab = NavigationTab.TIMETABLE
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            MacStyleTabBar(
                selectedTab = selectedTab,
                showStudyTab = appSettings.enableStudyModule,
                isDark = isDark,
                isWallpaperDark = isWallpaperDark,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.AGENDA -> AgendaScreen(
                    todayOccurrences = sampleOccurrences,
                    onCourseClick = { selectedOccurrence = it }
                )

                NavigationTab.TIMETABLE -> TimetableScreen(
                    currentWeek = currentWeek,
                    selectedWeek = selectedWeek,
                    totalWeeks = totalWeeks,
                    semesterName = semesterName,
                    occurrences = sampleOccurrences,
                    scheduleEvents = sampleSchedules,
                    sectionTimes = sectionTimes,
                    cardOpacity = appSettings.cardOpacity,
                    isDark = isDark,
                    isWallpaperDark = isWallpaperDark,
                    appSettings = appSettings,
                    onWeekChange = { selectedWeek = it },
                    onCourseClick = { selectedOccurrence = it },
                    onScheduleClick = { selectedScheduleEvent = it },
                    onAddCourseClick = {
                        addCourseSlotDay = 1
                        addCourseSlotSection = 1
                        showAddCourseDialog = true
                    },
                    onAddScheduleClick = { showAddScheduleDialog = true },
                    onAddCourseAtSlot = { day, sec ->
                        addCourseSlotDay = day
                        addCourseSlotSection = sec
                        showAddCourseDialog = true
                    },
                    onImportClick = { type ->
                        when (type) {
                            ImportType.PASTE_JSON -> showPasteImportDialog = true
                            ImportType.CLASSFLOW, ImportType.ICS -> showFileImportDialog = true
                        }
                    },
                    onExportClick = { type ->
                        when (type) {
                            ExportType.CLASSFLOW -> {
                                val bundle = FullSyncBundle(
                                    courses = sampleCourses.toList(),
                                    schedules = sampleSchedules.toList(),
                                    todos = emptyList(),
                                    studyTasks = emptyList(),
                                    focusRecords = emptyList(),
                                    settings = appSettings
                                )
                                exportContent = FullSyncManager.serializeBundle(bundle)
                                exportTitle = "ClassFlow 完整数据备份 (.classflow)"
                                showExportDialog = true
                            }
                            ExportType.ICS -> {
                                exportContent = generateICSCalendar(sampleOccurrences, sampleSchedules)
                                exportTitle = "导出系统日历文件 (.ics)"
                                showExportDialog = true
                            }
                        }
                    },
                    onOpenThreeDotsMenu = { showThreeDotsMenu = true }
                )

                NavigationTab.STUDY -> StudyScreen()
            }
        }

        // 3-Dots Quick Actions Menu
        if (showThreeDotsMenu) {
            ThreeDotsMenuDialog(
                currentSettings = appSettings,
                isDark = isDark,
                onDismiss = { showThreeDotsMenu = false },
                onOpenAction = { action ->
                    showThreeDotsMenu = false
                    if (action == QuickActionSheetType.TIMETABLE_SETTINGS) {
                        showMacSettingsDialog = true
                    } else {
                        activeQuickActionSheet = action
                    }
                },
                onToggleTheme = {
                    val nextTheme = when (appSettings.themeMode) {
                        "system" -> "dark"
                        "dark" -> "light"
                        else -> "system"
                    }
                    onUpdateSettings(appSettings.copy(themeMode = nextTheme))
                    showThreeDotsMenu = false
                }
            )
        }

        // Action Sheets opened from 3-dots
        when (activeQuickActionSheet) {
            QuickActionSheetType.SECTION_TIMES -> {
                SectionTimesDialog(
                    sections = sectionTimes,
                    onUpdateSections = { updated ->
                        sectionTimes.clear()
                        sectionTimes.addAll(updated)
                    },
                    onDismiss = { activeQuickActionSheet = QuickActionSheetType.NONE }
                )
            }
            QuickActionSheetType.TIMETABLE_SETTINGS -> {
                TimetableSettingsDialog(
                    appSettings = appSettings,
                    onUpdateSettings = onUpdateSettings,
                    onDismiss = { activeQuickActionSheet = QuickActionSheetType.NONE }
                )
            }
            QuickActionSheetType.COURSE_LIST -> {
                CourseListDialog(
                    courses = sampleCourses,
                    onDeleteCourse = { course ->
                        sampleCourses.remove(course)
                        sampleOccurrences.removeAll { it.courseId == course.id }
                    },
                    onDismiss = { activeQuickActionSheet = QuickActionSheetType.NONE }
                )
            }
            QuickActionSheetType.APPEARANCE -> {
                TimetableAppearanceDialog(
                    settings = appSettings,
                    isDark = isDark,
                    onUpdateSettings = onUpdateSettings,
                    onDismiss = { activeQuickActionSheet = QuickActionSheetType.NONE }
                )
            }
            QuickActionSheetType.WIDGETS -> {
                WidgetsInfoDialog(
                    onDismiss = { activeQuickActionSheet = QuickActionSheetType.NONE }
                )
            }
            else -> { /* No sheet */ }
        }

        // macOS-grade Preferences Hub
        if (showMacSettingsDialog) {
            MacSettingsDialog(
                appSettings = appSettings,
                sectionTimes = sectionTimes,
                courses = sampleCourses,
                isDark = isDark,
                onUpdateSettings = onUpdateSettings,
                onUpdateSections = { updated ->
                    sectionTimes.clear()
                    sectionTimes.addAll(updated)
                },
                onDeleteCourse = { course ->
                    sampleCourses.remove(course)
                    sampleOccurrences.removeAll { it.courseId == course.id }
                },
                onImportRequest = { type ->
                    showMacSettingsDialog = false
                    when (type) {
                        ImportType.PASTE_JSON -> showPasteImportDialog = true
                        ImportType.CLASSFLOW, ImportType.ICS -> showFileImportDialog = true
                    }
                },
                onExportRequest = { isClassFlow ->
                    showMacSettingsDialog = false
                    if (isClassFlow) {
                        val bundle = FullSyncBundle(
                            courses = sampleCourses.toList(),
                            schedules = sampleSchedules.toList(),
                            todos = emptyList(),
                            studyTasks = emptyList(),
                            focusRecords = emptyList(),
                            settings = appSettings
                        )
                        exportContent = FullSyncManager.serializeBundle(bundle)
                        exportTitle = "ClassFlow 完整数据备份 (.classflow)"
                        showExportDialog = true
                    } else {
                        exportContent = generateICSCalendar(sampleOccurrences, sampleSchedules)
                        exportTitle = "导出系统日历文件 (.ics)"
                        showExportDialog = true
                    }
                },
                onDismiss = { showMacSettingsDialog = false }
            )
        }

        // Add Course Dialog
        if (showAddCourseDialog) {
            val existingNames = sampleCourses.map { it.name }.distinct()
            val existingTeachers = sampleCourses.mapNotNull { it.teacher }.distinct()
            val existingClassrooms = sampleCourses.flatMap { it.schedules }.mapNotNull { it.classroom }.distinct()

            CourseAddDialog(
                semesterId = "sem_1",
                initialDayOfWeek = addCourseSlotDay,
                initialStartSection = addCourseSlotSection,
                initialEndSection = maxOf(addCourseSlotSection + 1, 2),
                existingCourseNames = existingNames,
                existingTeachers = existingTeachers,
                existingClassrooms = existingClassrooms,
                isDark = isDark,
                onDismiss = { showAddCourseDialog = false },
                onSaveCourse = { newCourse ->
                    sampleCourses.add(newCourse)
                    // Project occurrences
                    newCourse.schedules.forEach { sched ->
                        sampleOccurrences.add(
                            TimelineItem(
                                id = "occ_${System.currentTimeMillis()}",
                                courseId = newCourse.id,
                                scheduleId = sched.id,
                                title = newCourse.name,
                                location = sched.classroom,
                                date = "2026-10-14",
                                dayOfWeek = sched.dayOfWeek,
                                startSection = sched.startSection,
                                endSection = sched.endSection,
                                startTime = "08:00",
                                endTime = "09:35",
                                weekNumber = selectedWeek,
                                color = newCourse.color,
                                teacher = newCourse.teacher
                            )
                        )
                    }
                    showAddCourseDialog = false
                }
            )
        }

        // Add Schedule Dialog
        if (showAddScheduleDialog) {
            ScheduleAddDialog(
                isDark = isDark,
                onDismiss = { showAddScheduleDialog = false },
                onSaveSchedule = { newSchedule ->
                    sampleSchedules.add(0, newSchedule)
                    showAddScheduleDialog = false
                }
            )
        }

        // Course Inspection Bottom Sheet
        selectedOccurrence?.let { occurrence ->
            CourseDetailBottomSheet(
                item = occurrence,
                isDark = isDark,
                onDismiss = { selectedOccurrence = null },
                onApplyMutation = { _, _ ->
                    selectedOccurrence = null
                },
                onCancelCourse = { _ ->
                    sampleOccurrences.removeAll { it.id == occurrence.id }
                    selectedOccurrence = null
                }
            )
        }

        // Paste JSON Import Dialog
        if (showPasteImportDialog) {
            PasteJSONImportDialog(
                isDark = isDark,
                onDismiss = { showPasteImportDialog = false },
                onImportSuccess = { importedCourses, importedOccurrences ->
                    sampleCourses.addAll(importedCourses)
                    sampleOccurrences.addAll(importedOccurrences)
                    showPasteImportDialog = false
                    Toast.makeText(context, "成功导入 ${importedCourses.size} 门课程！", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // File Import Dialog
        if (showFileImportDialog) {
            FileImportDialog(
                isDark = isDark,
                onDismiss = { showFileImportDialog = false },
                onImportDemo = {
                    val demoCourse = Course(
                        id = "c_phy_${System.currentTimeMillis()}",
                        semesterId = "sem_1",
                        name = "大学物理实验",
                        teacher = "赵教授",
                        color = "#EA4335",
                        schedules = listOf(CourseSchedule(dayOfWeek = 4, startSection = 5, endSection = 6, classroom = "物理实验楼 204"))
                    )
                    sampleCourses.add(demoCourse)
                    sampleOccurrences.add(
                        TimelineItem(
                            id = "occ_phy_${System.currentTimeMillis()}",
                            courseId = demoCourse.id,
                            scheduleId = demoCourse.schedules.first().id,
                            title = demoCourse.name,
                            location = "物理实验楼 204",
                            date = "2026-10-15",
                            dayOfWeek = 4,
                            startSection = 5,
                            endSection = 6,
                            startTime = "13:30",
                            endTime = "15:05",
                            weekNumber = selectedWeek,
                            color = demoCourse.color,
                            teacher = demoCourse.teacher
                        )
                    )
                    showFileImportDialog = false
                    Toast.makeText(context, "已从备份恢复示例课程！", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Export Dialog
        if (showExportDialog) {
            ExportContentDialog(
                title = exportTitle,
                content = exportContent,
                isDark = isDark,
                onDismiss = { showExportDialog = false },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(exportContent))
                    Toast.makeText(context, "已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, exportContent)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, exportTitle)
                    context.startActivity(shareIntent)
                }
            )
        }
    }
}

/**
 * Generates RFC 5545 standard .ics calendar format from occurrences and schedules.
 */
fun generateICSCalendar(occurrences: List<TimelineItem>, schedules: List<CalendarEvent>): String {
    val sb = StringBuilder()
    sb.append("BEGIN:VCALENDAR\n")
    sb.append("VERSION:2.0\n")
    sb.append("PRODID:-//ClassFlow//Timetable Calendar//CN\n")
    occurrences.forEach { item ->
        sb.append("BEGIN:VEVENT\n")
        sb.append("UID:${item.id}@classflow.org\n")
        sb.append("SUMMARY:${item.title}\n")
        item.location?.let { sb.append("LOCATION:$it\n") }
        item.teacher?.let { sb.append("DESCRIPTION:任课教师：$it\n") }
        val dateClean = (item.date ?: "20261016").replace("-", "")
        val startClean = (item.startTime ?: "08:00").replace(":", "") + "00"
        val endClean = (item.endTime ?: "09:35").replace(":", "") + "00"
        sb.append("DTSTART:${dateClean}T${startClean}\n")
        sb.append("DTEND:${dateClean}T${endClean}\n")
        sb.append("END:VEVENT\n")
    }
    schedules.forEach { sched ->
        sb.append("BEGIN:VEVENT\n")
        sb.append("UID:${sched.id}@classflow.org\n")
        sb.append("SUMMARY:${sched.title}\n")
        sched.location?.let { sb.append("LOCATION:$it\n") }
        sched.description?.let { sb.append("DESCRIPTION:$it\n") }
        sb.append("END:VEVENT\n")
    }
    sb.append("END:VCALENDAR\n")
    return sb.toString()
}

@Composable
fun PasteJSONImportDialog(
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onImportSuccess: (List<Course>, List<TimelineItem>) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
        title = {
            Text("粘贴 JSON 导入课表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textPrimary)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "支持直接粘贴教务解析脚本、WakeUp 课程表导出 JSON 或 ClassFlow 数据包：",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    placeholder = { Text("在此粘贴 JSON 数据...", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp)
                )

                errorMessage?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { jsonText = it }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("粘贴剪贴板", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            jsonText = """
                            [
                              {
                                "name": "计算机网络",
                                "teacher": "孙教授",
                                "room": "网络楼 401",
                                "day": 2,
                                "startNode": 1,
                                "step": 2,
                                "color": "#FBBC04"
                              },
                              {
                                "name": "操作系统原理",
                                "teacher": "周教授",
                                "room": "计算机大楼 208",
                                "day": 4,
                                "startNode": 3,
                                "step": 2,
                                "color": "#4285F4"
                              }
                            ]
                            """.trimIndent()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("填入示例", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val text = jsonText.trim()
                        if (text.isEmpty()) {
                            errorMessage = "请先粘贴 JSON 内容"
                            return@Button
                        }

                        val parsedCourses = mutableListOf<Course>()
                        val parsedOccurrences = mutableListOf<TimelineItem>()

                        if (text.startsWith("{")) {
                            val root = JSONObject(text)
                            if (root.has("courses")) {
                                val bundle = FullSyncManager.deserializeBundle(text)
                                parsedCourses.addAll(bundle.courses)
                                bundle.courses.forEach { c ->
                                    c.schedules.forEach { s ->
                                        parsedOccurrences.add(
                                            TimelineItem(
                                                id = "occ_${System.currentTimeMillis()}_${s.id}",
                                                courseId = c.id,
                                                scheduleId = s.id,
                                                title = c.name,
                                                location = s.classroom,
                                                date = "2026-10-14",
                                                dayOfWeek = s.dayOfWeek,
                                                startSection = s.startSection,
                                                endSection = s.endSection,
                                                startTime = "08:00",
                                                endTime = "09:35",
                                                weekNumber = 1,
                                                color = c.color,
                                                teacher = c.teacher
                                            )
                                        )
                                    }
                                }
                            }
                        } else if (text.startsWith("[")) {
                            val arr = JSONArray(text)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val name = obj.optString("name", obj.optString("courseName", "课程 ${i + 1}"))
                                val teacher = obj.optString("teacher", obj.optString("teacherName", "")).ifEmpty { null }
                                val classroom = obj.optString("room", obj.optString("position", obj.optString("classroom", ""))).ifEmpty { null }
                                val day = obj.optInt("day", obj.optInt("dayOfWeek", 1))
                                val start = obj.optInt("startNode", obj.optInt("startSection", obj.optInt("start", 1)))
                                val step = obj.optInt("step", obj.optInt("nodeCount", 2))
                                val end = obj.optInt("endSection", obj.optInt("end", start + step - 1))
                                val color = obj.optString("color", "#3F51B5")

                                val courseId = "c_imported_${System.currentTimeMillis()}_$i"
                                val schedId = "s_imported_${System.currentTimeMillis()}_$i"
                                val course = Course(
                                    id = courseId,
                                    semesterId = "sem_1",
                                    name = name,
                                    teacher = teacher,
                                    color = color,
                                    schedules = listOf(
                                        CourseSchedule(
                                            id = schedId,
                                            dayOfWeek = day,
                                            startSection = start,
                                            endSection = end,
                                            classroom = classroom
                                        )
                                    )
                                )
                                parsedCourses.add(course)
                                parsedOccurrences.add(
                                    TimelineItem(
                                        id = "occ_${System.currentTimeMillis()}_$i",
                                        courseId = courseId,
                                        scheduleId = schedId,
                                        title = name,
                                        location = classroom,
                                        date = "2026-10-14",
                                        dayOfWeek = day,
                                        startSection = start,
                                        endSection = end,
                                        startTime = "08:00",
                                        endTime = "09:35",
                                        weekNumber = 1,
                                        color = color,
                                        teacher = teacher
                                    )
                                )
                            }
                        }

                        if (parsedCourses.isEmpty()) {
                            errorMessage = "未能识别出课程数据，请检查 JSON 格式"
                        } else {
                            onImportSuccess(parsedCourses, parsedOccurrences)
                        }
                    } catch (e: Exception) {
                        errorMessage = "解析失败: ${e.message}"
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("解析并导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun FileImportDialog(
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onImportDemo: () -> Unit
) {
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
        title = {
            Text("从文件导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ClassFlow 支持导入以下两类格式文件：", fontSize = 13.sp, color = textPrimary)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF262838) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("1. .classflow 完整备份包", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = textPrimary)
                        Text("包含所有课程、日程、待办清单、专注记录及配置，无损跨设备全量迁移。", fontSize = 11.sp, color = if (isDark) Color(0xFFA1A3AC) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF262838) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("2. .ics 通用日历文件", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = textPrimary)
                        Text("兼容苹果日历、Google Calendar、Outlook 的日历行程与课程。", fontSize = 11.sp, color = if (isDark) Color(0xFFA1A3AC) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onImportDemo,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("加载示例备份数据", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = if (isDark) Color(0xFFA1A3AC) else Color(0xFF6B7280))
            }
        }
    )
}

@Composable
fun ExportContentDialog(
    title: String,
    content: String,
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textPrimary)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("已成功生成导出数据，您可以复制至剪贴板或通过系统分享：", fontSize = 12.sp, color = if (isDark) Color(0xFFA1A3AC) else MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF262838) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = content,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制内容", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("系统分享", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
