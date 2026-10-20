package org.classflow.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.classflow.data.local.ClassFlowBackupManager
import org.classflow.data.local.CourseDataManager
import org.classflow.data.local.TimelineProjection
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val targetTabState = mutableStateOf<NavigationTab?>(NavigationTab.TIMETABLE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MainApp(
                targetTab = targetTabState.value,
                onTargetTabConsumed = { targetTabState.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Any launch from widgets or external actions directly opens the timetable tab
        targetTabState.value = NavigationTab.TIMETABLE
    }
}

enum class NavigationTab {
    AGENDA,
    TIMETABLE,
    STUDY
}

@Composable
fun MainApp(
    targetTab: NavigationTab? = null,
    onTargetTabConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var appSettings by remember {
        mutableStateOf(org.classflow.data.local.SettingsManager.loadSettings(context))
    }

    val isDark = when (appSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val isWallpaperDark = isWallpaperDark(
        preset = appSettings.wallpaperPreset,
        isSystemDark = isDark,
        usePureColor = appSettings.usePureColorBackground,
        customWallpaperUri = appSettings.customWallpaperUri
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
            customWallpaperUri = appSettings.customWallpaperUri,
            usePureColorBackground = appSettings.usePureColorBackground
        ) {
            MainScreenHost(
                appSettings = appSettings,
                isDark = isDark,
                isWallpaperDark = isWallpaperDark,
                targetTab = targetTab,
                onTargetTabConsumed = onTargetTabConsumed,
                onUpdateSettings = { updated ->
                    appSettings = updated
                    org.classflow.data.local.SettingsManager.saveSettings(context, updated)
                }
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
    val textActive = if (isDark || isWallpaperDark) Color.White else Color(0xFF1F2937)
    val textInactive = if (isDark || isWallpaperDark) Color.White.copy(alpha = 0.70f) else Color(0xFF6B7280)
    val indicatorColor = if (isDark || isWallpaperDark) Color.White else Color(0xFF1F2937)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
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
                        .width(14.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.25.dp))
                        .background(if (isSelected) indicatorColor else Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun MainScreenHost(
    appSettings: AppSettings,
    isDark: Boolean,
    isWallpaperDark: Boolean,
    targetTab: NavigationTab? = null,
    onTargetTabConsumed: () -> Unit = {},
    onUpdateSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedTab by remember { mutableStateOf(NavigationTab.TIMETABLE) }

    LaunchedEffect(targetTab) {
        if (targetTab != null) {
            selectedTab = targetTab
            onTargetTabConsumed()
        }
    }

    val semesters = remember {
        mutableStateListOf<Semester>().apply {
            addAll(CourseDataManager.loadSemesters(context))
        }
    }
    var activeSemesterId by remember {
        mutableStateOf(semesters.find { it.isCurrent }?.id ?: semesters.firstOrNull()?.id ?: "sem_1")
    }
    val currentSemester = semesters.find { it.id == activeSemesterId }
        ?: semesters.find { it.isCurrent }
        ?: semesters.firstOrNull()
        ?: Semester(
            id = "sem_1",
            name = "2026-2027 秋季学期",
            startDate = "2026-09-01",
            endDate = "2027-01-20",
            totalWeeks = 16,
            isCurrent = true
        )
    val defaultSemId = semesters.firstOrNull()?.id ?: "sem_1"
    val totalWeeks = currentSemester.totalWeeks
    var semesterName by remember { mutableStateOf(currentSemester.name) }
    val currentWeek = remember(currentSemester.startDate, totalWeeks) {
        TimelineProjection.calculateCurrentWeek(currentSemester.startDate, totalWeeks)
    }
    var selectedWeek by remember { mutableIntStateOf(currentWeek) }

    LaunchedEffect(activeSemesterId, currentSemester.name) {
        semesterName = currentSemester.name
        selectedWeek = currentWeek
    }

    val topCourseSelections = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(CourseDataManager.loadTopCourseSelections(context))
        }
    }

    var focusTargetTitle by remember { mutableStateOf<String?>(null) }
    var focusTargetCourse by remember { mutableStateOf<String?>(null) }
    var autoStartFocus by remember { mutableStateOf(false) }

    var selectedOccurrence by remember { mutableStateOf<TimelineItem?>(null) }
    var selectedScheduleEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showThreeDotsMenu by remember { mutableStateOf(false) }
    var showMacSettingsDialog by remember { mutableStateOf(false) }
    var activeQuickActionSheet by remember { mutableStateOf(QuickActionSheetType.NONE) }

    // Course Edit State
    var courseToEdit by remember { mutableStateOf<Course?>(null) }
    var showCourseEditDialog by remember { mutableStateOf(false) }

    // Schedule & Task Edit State
    var scheduleToEdit by remember { mutableStateOf<CalendarEvent?>(null) }
    var taskToEdit by remember { mutableStateOf<org.classflow.data.model.StudyTask?>(null) }

    // Section Times loaded from CourseDataManager single source of truth
    val sectionTimes = remember {
        mutableStateListOf<SectionTime>().apply {
            addAll(CourseDataManager.loadSectionTimes(context))
        }
    }

    // Courses List loaded from persistent storage
    val sampleCourses = remember {
        mutableStateListOf<Course>().apply {
            addAll(CourseDataManager.loadCourses(context))
        }
    }

    // Schedules List (Calendar Events)
    val sampleSchedules = remember {
        mutableStateListOf<CalendarEvent>().apply {
            addAll(CourseDataManager.loadSchedules(context))
        }
    }

    // Schedule Exceptions loaded from persistent storage
    val sampleExceptions = remember {
        mutableStateListOf<ScheduleException>().apply {
            addAll(CourseDataManager.loadExceptions(context))
        }
    }

    // Import / Export Dialogs & SAF Launchers
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var showFileImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }
    var exportTitle by remember { mutableStateOf("") }

    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null && pendingExportBytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(pendingExportBytes!!)
                }
                Toast.makeText(context, "已成功导出 ClassFlow 完整备份包！", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出写入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pendingExportBytes = null
            }
        }
    }

    var pendingExportIcs by remember { mutableStateOf<String?>(null) }
    val exportIcsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri: Uri? ->
        if (uri != null && pendingExportIcs != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(pendingExportIcs!!.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                }
                Toast.makeText(context, "已成功导出 .ics 日历文件！", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出写入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                pendingExportIcs = null
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val path = uri.path?.lowercase() ?: ""
                val isIcs = path.endsWith(".ics") || uri.toString().lowercase().endsWith(".ics")
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    if (isIcs) {
                        val result = ClassFlowBackupManager.importICS(stream)
                        if (result.courses.isNotEmpty() || result.schedules.isNotEmpty()) {
                            sampleCourses.addAll(result.courses)
                            sampleSchedules.addAll(result.schedules)
                            CourseDataManager.saveCourses(context, sampleCourses)
                            CourseDataManager.saveSchedules(context, sampleSchedules)
                            Toast.makeText(context, "已成功导入 ${result.courses.size} 门课程与 ${result.schedules.size} 项日程！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "日历文件中未找到有效事件", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        try {
                            val result = ClassFlowBackupManager.importPackageZip(stream)
                            if (result.courses.isNotEmpty() || result.semesters.isNotEmpty() || result.exceptions.isNotEmpty()) {
                                if (result.courses.isNotEmpty()) {
                                    sampleCourses.clear()
                                    sampleCourses.addAll(result.courses)
                                    CourseDataManager.saveCourses(context, sampleCourses)
                                }
                                if (result.exceptions.isNotEmpty()) {
                                    sampleExceptions.clear()
                                    sampleExceptions.addAll(result.exceptions)
                                    CourseDataManager.saveExceptions(context, sampleExceptions)
                                }
                                if (result.semesters.isNotEmpty()) {
                                    semesters.clear()
                                    semesters.addAll(result.semesters)
                                    CourseDataManager.saveSemesters(context, semesters)
                                    val active = semesters.find { it.isCurrent } ?: semesters.firstOrNull()
                                    if (active != null) {
                                        semesterName = active.name
                                    }
                                }
                                Toast.makeText(context, "成功导入 .classflow 备份包（包含 ${result.courses.size} 门课程）！", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "备份包中未发现有效数据", Toast.LENGTH_SHORT).show()
                            }
                        } catch (zipErr: Exception) {
                            context.contentResolver.openInputStream(uri)?.use { fallbackStream ->
                                val icsResult = ClassFlowBackupManager.importICS(fallbackStream)
                                if (icsResult.courses.isNotEmpty() || icsResult.schedules.isNotEmpty()) {
                                    sampleCourses.addAll(icsResult.courses)
                                    sampleSchedules.addAll(icsResult.schedules)
                                    CourseDataManager.saveCourses(context, sampleCourses)
                                    CourseDataManager.saveSchedules(context, sampleSchedules)
                                    Toast.makeText(context, "已从日历文件导入 ${icsResult.courses.size} 门课程！", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "解析文件失败: ${zipErr.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showAddCourseDialog by remember { mutableStateOf(false) }
    var addCourseSlotDay by remember { mutableIntStateOf(1) }
    var addCourseSlotSection by remember { mutableIntStateOf(1) }

    var showAddScheduleDialog by remember { mutableStateOf(false) }
    var addScheduleSlotDay by remember { mutableIntStateOf(1) }
    var addScheduleSlotSection by remember { mutableIntStateOf(1) }

    // Scoped active courses, schedules and tasks for current semester
    val activeCourses = remember(sampleCourses.toList(), activeSemesterId, defaultSemId) {
        sampleCourses.filter {
            it.semesterId == activeSemesterId || (it.semesterId.isBlank() && activeSemesterId == defaultSemId)
        }
    }
    val activeSchedules = remember(sampleSchedules.toList(), activeSemesterId, defaultSemId) {
        sampleSchedules.filter {
            it.semesterId == activeSemesterId || (it.semesterId.isNullOrBlank() && activeSemesterId == defaultSemId)
        }
    }

    // Hoisted Study Tasks, Focus Sessions Log & Daily Map for study module & settings management
    val sampleStudyTasks = remember {
        mutableStateListOf<org.classflow.data.model.StudyTask>().apply {
            addAll(org.classflow.data.local.CourseDataManager.loadStudyTasks(context))
        }
    }

    val activeStudyTasks = remember(sampleStudyTasks.toList(), activeSemesterId, defaultSemId) {
        sampleStudyTasks.filter {
            it.semesterId == activeSemesterId || (it.semesterId.isNullOrBlank() && activeSemesterId == defaultSemId)
        }
    }

    // Reactively project occurrences for the selected timetable week
    val currentOccurrences = remember(
        activeCourses,
        activeSchedules,
        sampleExceptions.toList(),
        selectedWeek,
        currentWeek,
        sectionTimes.toList(),
        appSettings.showNonCurrentWeekCourses,
        currentSemester.startDate,
        totalWeeks
    ) {
        TimelineProjection.projectOccurrences(
            courses = activeCourses,
            schedules = activeSchedules,
            selectedWeek = selectedWeek,
            currentWeek = currentWeek,
            sectionTimes = sectionTimes.toList(),
            totalWeeks = totalWeeks,
            showNonCurrentWeekCourses = appSettings.showNonCurrentWeekCourses,
            semesterStartDate = currentSemester.startDate,
            exceptions = sampleExceptions.toList()
        )
    }

    // Reactively project occurrences for the current real-world week (for Agenda and Widgets)
    val todayOccurrences = remember(
        activeCourses,
        activeSchedules,
        sampleExceptions.toList(),
        currentWeek,
        sectionTimes.toList(),
        currentSemester.startDate,
        totalWeeks
    ) {
        TimelineProjection.projectOccurrences(
            courses = activeCourses,
            schedules = activeSchedules,
            selectedWeek = currentWeek,
            currentWeek = currentWeek,
            sectionTimes = sectionTimes.toList(),
            totalWeeks = totalWeeks,
            showNonCurrentWeekCourses = true,
            semesterStartDate = currentSemester.startDate,
            exceptions = sampleExceptions.toList()
        )
    }

    val focusRecords = remember {
        mutableStateListOf<FocusRecord>().apply {
            addAll(org.classflow.data.local.CourseDataManager.loadFocusRecords(context))
        }
    }

    val dailyMinutesMap = remember {
        mutableStateMapOf<String, Int>().apply {
            putAll(org.classflow.data.local.CourseDataManager.loadDailyMinutes(context))
        }
    }

    // Auto-save sampleStudyTasks whenever modified (offloaded to Dispatchers.IO)
    LaunchedEffect(sampleStudyTasks.toList()) {
        withContext(Dispatchers.IO) {
            org.classflow.data.local.CourseDataManager.saveStudyTasks(context, sampleStudyTasks.toList())
        }
    }

    // Auto-save focusRecords whenever modified (offloaded to Dispatchers.IO)
    LaunchedEffect(focusRecords.toList()) {
        withContext(Dispatchers.IO) {
            org.classflow.data.local.CourseDataManager.saveFocusRecords(context, focusRecords.toList())
        }
    }

    // Auto-save dailyMinutesMap whenever modified (offloaded to Dispatchers.IO)
    LaunchedEffect(dailyMinutesMap.toMap()) {
        withContext(Dispatchers.IO) {
            org.classflow.data.local.CourseDataManager.saveDailyMinutes(context, dailyMinutesMap.toMap())
        }
    }

    val reloadAllData = {
        sampleCourses.clear()
        sampleCourses.addAll(org.classflow.data.local.CourseDataManager.loadCourses(context))
        sampleSchedules.clear()
        sampleSchedules.addAll(org.classflow.data.local.CourseDataManager.loadSchedules(context))
        sampleExceptions.clear()
        sampleExceptions.addAll(org.classflow.data.local.CourseDataManager.loadExceptions(context))
        sampleStudyTasks.clear()
        sampleStudyTasks.addAll(org.classflow.data.local.CourseDataManager.loadStudyTasks(context))
        focusRecords.clear()
        focusRecords.addAll(org.classflow.data.local.CourseDataManager.loadFocusRecords(context))
        dailyMinutesMap.clear()
        dailyMinutesMap.putAll(org.classflow.data.local.CourseDataManager.loadDailyMinutes(context))
        semesters.clear()
        semesters.addAll(org.classflow.data.local.CourseDataManager.loadSemesters(context))
        val active = semesters.find { it.id == activeSemesterId } ?: semesters.find { it.isCurrent } ?: semesters.firstOrNull()
        if (active != null) {
            activeSemesterId = active.id
            semesterName = active.name
        }
        sectionTimes.clear()
        sectionTimes.addAll(org.classflow.data.local.CourseDataManager.loadSectionTimes(context))
    }

    // Step 1: Remote Save Sync on Startup
    LaunchedEffect(Unit) {
        if (appSettings.syncOnStartup) {
            val creds = org.classflow.data.local.SettingsManager.loadSyncCredentials(context)
            if (creds.providerType != "none") {
                val res = FullSyncManager.executeFullSync(context)
                if (res.isSuccess) {
                    reloadAllData()
                }
            }
        }
    }

    // Step 2: Remote Save Periodic Auto-sync based on interval
    LaunchedEffect(appSettings.autoSyncEnabled, appSettings.syncInterval) {
        if (!appSettings.autoSyncEnabled || appSettings.syncInterval == "manual" || appSettings.syncInterval == "off") {
            return@LaunchedEffect
        }
        val intervalMillis = when (appSettings.syncInterval) {
            "15m" -> 15 * 60 * 1000L
            "30m" -> 30 * 60 * 1000L
            "1h" -> 60 * 60 * 1000L
            "2h" -> 2 * 60 * 60 * 1000L
            "6h" -> 6 * 60 * 60 * 1000L
            else -> 30 * 60 * 1000L
        }
        while (isActive) {
            delay(intervalMillis)
            val creds = org.classflow.data.local.SettingsManager.loadSyncCredentials(context)
            if (creds.providerType != "none") {
                val res = FullSyncManager.executeFullSync(context)
                if (res.isSuccess) {
                    reloadAllData()
                }
            }
        }
    }

    // Fallback if study tab is selected but module gets disabled
    LaunchedEffect(appSettings.enableStudyModule) {
        if (!appSettings.enableStudyModule && selectedTab == NavigationTab.STUDY) {
            selectedTab = NavigationTab.TIMETABLE
        }
    }

    // Continuously sync live courses, schedules, week, and theme mode to Glance home screen widgets (offloaded to Dispatchers.IO)
    LaunchedEffect(
        activeCourses,
        activeSchedules,
        todayOccurrences,
        currentWeek,
        semesterName,
        isDark,
        appSettings.themeMode,
        currentSemester.startDate,
        totalWeeks
    ) {
        withContext(Dispatchers.IO) {
            org.classflow.data.local.WidgetDataManager.saveAndUpdate(
                context = context,
                semesterName = semesterName,
                currentWeek = currentWeek,
                isDark = isDark,
                themeMode = appSettings.themeMode,
                courses = activeCourses,
                occurrences = todayOccurrences,
                schedules = activeSchedules,
                sectionTimes = sectionTimes.toList(),
                semesterStartDate = currentSemester.startDate,
                totalWeeks = totalWeeks,
                exceptions = sampleExceptions.toList()
            )
        }
    }

    val hasWallpaper = !appSettings.customWallpaperUri.isNullOrBlank() || (!appSettings.usePureColorBackground && appSettings.wallpaperPreset != "default")

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
                    todayOccurrences = todayOccurrences,
                    scheduleEvents = activeSchedules,
                    courses = activeCourses,
                    sectionTimes = sectionTimes,
                    exceptions = sampleExceptions,
                    semesterStartDate = currentSemester.startDate,
                    currentWeek = currentWeek,
                    isDark = isDark,
                    tasks = activeStudyTasks,
                    onToggleTask = { task ->
                        val idx = sampleStudyTasks.indexOfFirst { it.id == task.id }
                        if (idx >= 0) {
                            sampleStudyTasks[idx] = task.copy(isCompleted = !task.isCompleted)
                            CourseDataManager.saveStudyTasks(context, sampleStudyTasks)
                        }
                    },
                    onDeleteTask = { task ->
                        sampleStudyTasks.removeAll { it.id == task.id }
                        CourseDataManager.addTombstone(context, task.id, "studyTask")
                        CourseDataManager.saveStudyTasks(context, sampleStudyTasks)
                    },
                    onAddTask = { task ->
                        sampleStudyTasks.add(task.copy(semesterId = activeSemesterId))
                        CourseDataManager.saveStudyTasks(context, sampleStudyTasks)
                    },
                    onAddSchedule = { event ->
                        sampleSchedules.add(event.copy(semesterId = activeSemesterId))
                        CourseDataManager.saveSchedules(context, sampleSchedules)
                    },
                    onCourseClick = { selectedOccurrence = it },
                    onScheduleClick = { evt ->
                        val todayWeekday = java.util.Calendar.getInstance().let {
                            val w = it.get(java.util.Calendar.DAY_OF_WEEK)
                            if (w == java.util.Calendar.SUNDAY) 7 else w - 1
                        }
                        val defaultDate = TimelineProjection.calculateDateString(semesterStartDate = currentSemester.startDate, selectedWeek = currentWeek, dayOfWeek = todayWeekday)
                        val occurrenceDate = if (!evt.startDate.isNullOrBlank()) {
                            evt.startDate
                        } else if (evt.startTime.length >= 10 && evt.startTime.contains("-")) {
                            evt.startTime.substring(0, 10)
                        } else {
                            defaultDate
                        }
                        val rawStart = evt.startTime
                        val startT = when {
                            rawStart.contains("T") -> rawStart.substringAfter("T").take(5)
                            rawStart.contains(" ") -> rawStart.substringAfter(" ").take(5)
                            else -> rawStart
                        }
                        val rawEnd = evt.endTime
                        val endT = when {
                            rawEnd.contains("T") -> rawEnd.substringAfter("T").take(5)
                            rawEnd.contains(" ") -> rawEnd.substringAfter(" ").take(5)
                            else -> rawEnd
                        }
                        val normFreq = TimelineProjection.normalizeFrequency(evt.frequency)
                        selectedOccurrence = TimelineItem(
                            id = "event_${evt.id}",
                            calendarEventId = evt.id,
                            scheduleId = evt.id,
                            title = evt.title,
                            location = evt.location,
                            date = occurrenceDate,
                            dayOfWeek = todayWeekday,
                            startSection = evt.startSection ?: 1,
                            endSection = evt.endSection ?: (evt.startSection ?: 1),
                            startTime = startT,
                            endTime = endT,
                            weekNumber = currentWeek,
                            isCurrentWeek = true,
                            status = OccurrenceStatus.NORMAL,
                            color = evt.color,
                            notes = evt.description,
                            isAllDay = evt.isAllDay,
                            isRecurring = (normFreq != "none") || evt.customWeeks.isNotEmpty() || evt.customDaysOfWeek.size > 1
                        )
                    },
                    onNavigateToTimetable = { selectedTab = NavigationTab.TIMETABLE },
                    onStartFocus = { title, course ->
                        focusTargetTitle = title
                        focusTargetCourse = course
                        autoStartFocus = true
                        selectedTab = NavigationTab.STUDY
                    }
                )

                NavigationTab.TIMETABLE -> TimetableScreen(
                    currentWeek = currentWeek,
                    selectedWeek = selectedWeek,
                    totalWeeks = totalWeeks,
                    semesterName = semesterName,
                    activeSemesterId = activeSemesterId,
                    semestersList = semesters.toList(),
                    occurrences = currentOccurrences,
                    scheduleEvents = activeSchedules,
                    sectionTimes = sectionTimes,
                    cardOpacity = appSettings.cardOpacity,
                    isDark = isDark,
                    isWallpaperDark = isWallpaperDark,
                    appSettings = appSettings,
                    topCourseSelections = topCourseSelections.toMap(),
                    onSelectTopCourse = { slotKey, courseId ->
                        topCourseSelections[slotKey] = courseId
                        CourseDataManager.saveTopCourseSelections(context, topCourseSelections.toMap())
                    },
                    onWeekChange = { selectedWeek = it },
                    onSemesterChange = { semesterName = it },
                    onSelectSemester = { sem ->
                        activeSemesterId = sem.id
                        semesterName = sem.name
                        for (i in semesters.indices) {
                            semesters[i] = semesters[i].copy(isCurrent = (semesters[i].id == sem.id))
                        }
                        CourseDataManager.saveSemesters(context, semesters.toList())
                    },
                    onCreateSemester = { newSem ->
                        for (i in semesters.indices) {
                            semesters[i] = semesters[i].copy(isCurrent = false)
                        }
                        semesters.add(newSem)
                        CourseDataManager.saveSemesters(context, semesters.toList())
                        activeSemesterId = newSem.id
                        semesterName = newSem.name
                    },
                    onUpdateSemester = { updated ->
                        val idx = semesters.indexOfFirst { it.id == updated.id }
                        if (idx >= 0) {
                            semesters[idx] = updated
                            CourseDataManager.saveSemesters(context, semesters.toList())
                            if (updated.id == activeSemesterId) {
                                semesterName = updated.name
                            }
                        }
                    },
                    onDeleteSemester = { targetSem ->
                        if (semesters.size > 1) {
                            semesters.removeAll { it.id == targetSem.id }
                            sampleCourses.removeAll { it.semesterId == targetSem.id || (it.semesterId.isBlank() && targetSem.id == defaultSemId) }
                            sampleSchedules.removeAll { it.semesterId == targetSem.id || (it.semesterId.isNullOrBlank() && targetSem.id == defaultSemId) }
                            sampleStudyTasks.removeAll { it.semesterId == targetSem.id || (it.semesterId.isNullOrBlank() && targetSem.id == defaultSemId) }

                            CourseDataManager.addTombstone(context, targetSem.id, "semester")
                            CourseDataManager.saveSemesters(context, semesters.toList())
                            CourseDataManager.saveCourses(context, sampleCourses.toList())
                            CourseDataManager.saveSchedules(context, sampleSchedules.toList())
                            CourseDataManager.saveStudyTasks(context, sampleStudyTasks.toList())

                            if (targetSem.id == activeSemesterId) {
                                val nextSem = semesters.first()
                                activeSemesterId = nextSem.id
                                semesterName = nextSem.name
                                for (i in semesters.indices) {
                                    semesters[i] = semesters[i].copy(isCurrent = (semesters[i].id == nextSem.id))
                                }
                                CourseDataManager.saveSemesters(context, semesters.toList())
                            }
                        }
                    },
                    onCourseClick = { selectedOccurrence = it },
                    onScheduleClick = { evt ->
                        val todayWeekday = java.util.Calendar.getInstance().let {
                            val w = it.get(java.util.Calendar.DAY_OF_WEEK)
                            if (w == java.util.Calendar.SUNDAY) 7 else w - 1
                        }
                        val defaultDate = TimelineProjection.calculateDateString(semesterStartDate = currentSemester.startDate, selectedWeek = selectedWeek, dayOfWeek = todayWeekday)
                        val occurrenceDate = if (!evt.startDate.isNullOrBlank()) {
                            evt.startDate
                        } else if (evt.startTime.length >= 10 && evt.startTime.contains("-")) {
                            evt.startTime.substring(0, 10)
                        } else {
                            defaultDate
                        }
                        val rawStart = evt.startTime
                        val startT = when {
                            rawStart.contains("T") -> rawStart.substringAfter("T").take(5)
                            rawStart.contains(" ") -> rawStart.substringAfter(" ").take(5)
                            else -> rawStart
                        }
                        val rawEnd = evt.endTime
                        val endT = when {
                            rawEnd.contains("T") -> rawEnd.substringAfter("T").take(5)
                            rawEnd.contains(" ") -> rawEnd.substringAfter(" ").take(5)
                            else -> rawEnd
                        }
                        val normFreq = TimelineProjection.normalizeFrequency(evt.frequency)
                        selectedOccurrence = TimelineItem(
                            id = "event_${evt.id}",
                            calendarEventId = evt.id,
                            scheduleId = evt.id,
                            title = evt.title,
                            location = evt.location,
                            date = occurrenceDate,
                            dayOfWeek = todayWeekday,
                            startSection = evt.startSection ?: 1,
                            endSection = evt.endSection ?: (evt.startSection ?: 1),
                            startTime = startT,
                            endTime = endT,
                            weekNumber = selectedWeek,
                            isCurrentWeek = true,
                            status = OccurrenceStatus.NORMAL,
                            color = evt.color,
                            notes = evt.description,
                            isAllDay = evt.isAllDay,
                            isRecurring = (normFreq != "none") || evt.customWeeks.isNotEmpty() || evt.customDaysOfWeek.size > 1
                        )
                    },
                    onAddCourseClick = {
                        addCourseSlotDay = 1
                        addCourseSlotSection = 1
                        showAddCourseDialog = true
                    },
                    onAddScheduleClick = {
                        addScheduleSlotDay = 1
                        addScheduleSlotSection = 1
                        showAddScheduleDialog = true
                    },
                    onAddCourseAtSlot = { day, sec ->
                        addCourseSlotDay = day
                        addCourseSlotSection = sec
                        showAddCourseDialog = true
                    },
                    onAddScheduleAtSlot = { day, sec ->
                        addScheduleSlotDay = day
                        addScheduleSlotSection = sec
                        showAddScheduleDialog = true
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
                                val zipBytes = ClassFlowBackupManager.exportPackageBytes(
                                    semesters = semesters.toList(),
                                    courses = sampleCourses.toList(),
                                    exceptions = sampleExceptions.toList(),
                                    settings = mapOf(
                                        "themeMode" to appSettings.themeMode,
                                        "showWeekends" to appSettings.showWeekends.toString(),
                                        "showNonCurrentWeekCourses" to appSettings.showNonCurrentWeekCourses.toString()
                                    )
                                )
                                pendingExportBytes = zipBytes
                                exportZipLauncher.launch("classflow_backup_${System.currentTimeMillis()}.classflow")
                            }
                            ExportType.ICS -> {
                                val icsText = ClassFlowBackupManager.generateICSCalendar(
                                    occurrences = currentOccurrences,
                                    schedules = sampleSchedules.toList()
                                )
                                pendingExportIcs = icsText
                                exportIcsLauncher.launch("classflow_schedule_${System.currentTimeMillis()}.ics")
                            }
                        }
                    },
                    onOpenThreeDotsMenu = { showThreeDotsMenu = true }
                )

                NavigationTab.STUDY -> StudyScreen(
                    initialTaskTitle = focusTargetTitle,
                    initialCourseName = focusTargetCourse,
                    autoStart = autoStartFocus,
                    onFocusConsumed = { autoStartFocus = false },
                    tasks = activeStudyTasks,
                    onAddTask = { task ->
                        sampleStudyTasks.add(task.copy(semesterId = activeSemesterId))
                        CourseDataManager.saveStudyTasks(context, sampleStudyTasks)
                    },
                    onToggleTask = { task ->
                        val idx = sampleStudyTasks.indexOfFirst { it.id == task.id }
                        if (idx >= 0) {
                            sampleStudyTasks[idx] = sampleStudyTasks[idx].copy(isCompleted = !sampleStudyTasks[idx].isCompleted)
                            CourseDataManager.saveStudyTasks(context, sampleStudyTasks)
                        }
                    },
                    onDeleteTask = { task ->
                        sampleStudyTasks.removeAll { it.id == task.id }
                        CourseDataManager.addTombstone(context, task.id, "studyTask")
                        CourseDataManager.saveStudyTasks(context, sampleStudyTasks)
                    },
                    focusRecords = focusRecords,
                    dailyMinutesMap = dailyMinutesMap,
                    onClearFocusHistory = {
                        focusRecords.clear()
                        dailyMinutesMap.clear()
                        org.classflow.data.local.CourseDataManager.saveFocusRecords(context, emptyList())
                        org.classflow.data.local.CourseDataManager.saveDailyMinutes(context, emptyMap())
                        Toast.makeText(context, "已成功清空所有历史专注数据与热力图记录", Toast.LENGTH_SHORT).show()
                    }
                )
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
                        org.classflow.data.local.CourseDataManager.saveSectionTimes(context, sectionTimes)
                        val curIdx = semesters.indexOfFirst { it.isCurrent }
                        if (curIdx >= 0) {
                            semesters[curIdx] = semesters[curIdx].copy(sections = updated)
                            org.classflow.data.local.CourseDataManager.saveSemesters(context, semesters.toList())
                        } else if (semesters.isNotEmpty()) {
                            semesters[0] = semesters[0].copy(sections = updated)
                            org.classflow.data.local.CourseDataManager.saveSemesters(context, semesters.toList())
                        }
                    },
                    onDismiss = { activeQuickActionSheet = QuickActionSheetType.NONE }
                )
            }
            QuickActionSheetType.TIMETABLE_SETTINGS -> {
                TimetableSettingsDialog(
                    appSettings = appSettings,
                    onUpdateSettings = onUpdateSettings,
                    onSyncSuccess = {
                        reloadAllData()
                    },
                    onDismiss = { activeQuickActionSheet = QuickActionSheetType.NONE }
                )
            }
            QuickActionSheetType.COURSE_LIST -> {
                CourseListDialog(
                    courses = activeCourses,
                    schedules = activeSchedules,
                    tasks = activeStudyTasks,
                    onDeleteCourse = { course ->
                        sampleCourses.removeAll { it.id == course.id }
                        org.classflow.data.local.CourseDataManager.saveCourses(context, sampleCourses)
                        org.classflow.data.local.CourseDataManager.addTombstone(context, course.id, "course")
                        course.schedules.forEach { s ->
                            org.classflow.data.local.CourseDataManager.addTombstone(context, s.id, "schedule")
                        }
                        Toast.makeText(context, "已删除课程「${course.name}」", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteSchedule = { schedule ->
                        sampleSchedules.removeAll { it.id == schedule.id }
                        sampleExceptions.removeAll { it.scheduleId == schedule.id }
                        org.classflow.data.local.CourseDataManager.saveSchedules(context, sampleSchedules)
                        org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                        org.classflow.data.local.CourseDataManager.addTombstone(context, schedule.id, "calendarEvent")
                        Toast.makeText(context, "已删除日程「${schedule.title}」", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteTask = { task ->
                        sampleStudyTasks.removeAll { it.id == task.id }
                        org.classflow.data.local.CourseDataManager.saveStudyTasks(context, sampleStudyTasks)
                        org.classflow.data.local.CourseDataManager.addTombstone(context, task.id, "pomodoroTask")
                        Toast.makeText(context, "已删除待办「${task.title}」", Toast.LENGTH_SHORT).show()
                    },
                    onEditCourse = { course ->
                        courseToEdit = course
                        showCourseEditDialog = true
                        activeQuickActionSheet = QuickActionSheetType.NONE
                    },
                    onEditSchedule = { schedule ->
                        scheduleToEdit = schedule
                        showAddScheduleDialog = true
                        activeQuickActionSheet = QuickActionSheetType.NONE
                    },
                    onEditTask = { task ->
                        taskToEdit = task
                        showAddScheduleDialog = true
                        activeQuickActionSheet = QuickActionSheetType.NONE
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
                courses = activeCourses,
                isDark = isDark,
                onUpdateSettings = onUpdateSettings,
                onSyncSuccess = {
                    reloadAllData()
                },
                onUpdateSections = { updated ->
                    sectionTimes.clear()
                    sectionTimes.addAll(updated)
                    org.classflow.data.local.CourseDataManager.saveSectionTimes(context, sectionTimes)
                    val curIdx = semesters.indexOfFirst { it.isCurrent }
                    if (curIdx >= 0) {
                        semesters[curIdx] = semesters[curIdx].copy(sections = updated)
                        org.classflow.data.local.CourseDataManager.saveSemesters(context, semesters.toList())
                    } else if (semesters.isNotEmpty()) {
                        semesters[0] = semesters[0].copy(sections = updated)
                        org.classflow.data.local.CourseDataManager.saveSemesters(context, semesters.toList())
                    }
                },
                onDeleteCourse = { course ->
                    sampleCourses.removeAll { it.id == course.id }
                    org.classflow.data.local.CourseDataManager.saveCourses(context, sampleCourses)
                    org.classflow.data.local.CourseDataManager.addTombstone(context, course.id, "course")
                    course.schedules.forEach { s ->
                        org.classflow.data.local.CourseDataManager.addTombstone(context, s.id, "schedule")
                    }
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
                        val zipBytes = ClassFlowBackupManager.exportPackageBytes(
                            semesters = semesters.toList(),
                            courses = sampleCourses.toList(),
                            exceptions = sampleExceptions.toList(),
                            settings = mapOf(
                                "themeMode" to appSettings.themeMode,
                                "showWeekends" to appSettings.showWeekends.toString(),
                                "showNonCurrentWeekCourses" to appSettings.showNonCurrentWeekCourses.toString()
                            )
                        )
                        pendingExportBytes = zipBytes
                        exportZipLauncher.launch("classflow_backup_${System.currentTimeMillis()}.classflow")
                    } else {
                        val icsText = ClassFlowBackupManager.generateICSCalendar(
                            occurrences = currentOccurrences,
                            schedules = sampleSchedules.toList()
                        )
                        pendingExportIcs = icsText
                        exportIcsLauncher.launch("classflow_schedule_${System.currentTimeMillis()}.ics")
                    }
                },
                onClearFocusHistory = {
                    focusRecords.clear()
                    dailyMinutesMap.clear()
                    org.classflow.data.local.CourseDataManager.saveFocusRecords(context, emptyList())
                    org.classflow.data.local.CourseDataManager.saveDailyMinutes(context, emptyMap())
                    Toast.makeText(context, "已成功清空所有历史专注数据与热力图记录", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showMacSettingsDialog = false }
            )
        }

        // Add Course Dialog
        if (showAddCourseDialog) {
            val existingNames = activeCourses.map { it.name }.distinct()
            val existingTeachers = activeCourses.mapNotNull { it.teacher }.distinct()
            val existingClassrooms = activeCourses.flatMap { it.schedules }.mapNotNull { it.classroom }.distinct()

            CourseAddDialog(
                semesterId = activeSemesterId,
                initialDayOfWeek = addCourseSlotDay,
                initialStartSection = addCourseSlotSection,
                initialEndSection = maxOf(addCourseSlotSection + 1, 2),
                totalWeeks = currentSemester?.totalWeeks ?: 20,
                availableSections = sectionTimes,
                existingCourseNames = existingNames,
                existingTeachers = existingTeachers,
                existingClassrooms = existingClassrooms,
                isDark = isDark,
                onDismiss = { showAddCourseDialog = false },
                onSaveCourse = { newCourse ->
                    val courseWithSem = newCourse.copy(semesterId = activeSemesterId)
                    sampleCourses.add(courseWithSem)
                    org.classflow.data.local.CourseDataManager.saveCourses(context, sampleCourses)
                    showAddCourseDialog = false
                    Toast.makeText(context, "已成功添加课程「${newCourse.name}」", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Add / Edit Schedule & Task Dialog
        if (showAddScheduleDialog) {
            ScheduleAddDialog(
                initialDayOfWeek = addScheduleSlotDay,
                initialSection = addScheduleSlotSection,
                maxWeeks = currentSemester?.totalWeeks ?: 20,
                initialEvent = scheduleToEdit,
                initialTask = taskToEdit,
                availableSections = sectionTimes,
                courses = activeCourses,
                semesterId = activeSemesterId,
                isDark = isDark,
                onDismiss = {
                    showAddScheduleDialog = false
                    scheduleToEdit = null
                    taskToEdit = null
                },
                onSaveSchedule = { savedSchedule ->
                    val scheduleWithSem = savedSchedule.copy(semesterId = activeSemesterId)
                    val idx = sampleSchedules.indexOfFirst { it.id == scheduleWithSem.id }
                    if (idx >= 0) {
                        sampleSchedules[idx] = scheduleWithSem
                    } else {
                        sampleSchedules.add(0, scheduleWithSem)
                    }
                    org.classflow.data.local.CourseDataManager.saveSchedules(context, sampleSchedules)
                    showAddScheduleDialog = false
                    scheduleToEdit = null
                    Toast.makeText(context, "已保存日程「${savedSchedule.title}」", Toast.LENGTH_SHORT).show()
                },
                onSaveTask = { savedTask ->
                    val taskWithSem = savedTask.copy(semesterId = activeSemesterId)
                    val idx = sampleStudyTasks.indexOfFirst { it.id == taskWithSem.id }
                    if (idx >= 0) {
                        sampleStudyTasks[idx] = taskWithSem
                    } else {
                        sampleStudyTasks.add(0, taskWithSem)
                    }
                    org.classflow.data.local.CourseDataManager.saveStudyTasks(context, sampleStudyTasks)
                    showAddScheduleDialog = false
                    taskToEdit = null
                    Toast.makeText(context, "已保存待办「${savedTask.title}」", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Course & Event Inspection Bottom Sheet
        selectedOccurrence?.let { occurrence ->
            CourseDetailBottomSheet(
                item = occurrence,
                isDark = isDark,
                onDismiss = { selectedOccurrence = null },
                onEditCourse = {
                    if (occurrence.calendarEventId != null || occurrence.courseId == null) {
                        val eventId = occurrence.calendarEventId ?: occurrence.scheduleId
                        val event = sampleSchedules.find { it.id == eventId }
                        if (event != null) {
                            scheduleToEdit = event
                            showAddScheduleDialog = true
                        }
                    } else {
                        val found = sampleCourses.find { it.id == occurrence.courseId }
                        courseToEdit = found ?: Course(
                            id = occurrence.courseId ?: UUID.randomUUID().toString(),
                            semesterId = activeSemesterId,
                            name = occurrence.title,
                            teacher = occurrence.teacher,
                            color = occurrence.color,
                            notes = occurrence.notes,
                            schedules = listOf(
                                CourseSchedule(
                                    id = occurrence.scheduleId ?: UUID.randomUUID().toString(),
                                    courseId = occurrence.courseId,
                                    dayOfWeek = occurrence.dayOfWeek,
                                    startSection = occurrence.startSection,
                                    endSection = occurrence.endSection,
                                    classroom = occurrence.location,
                                    teacher = occurrence.teacher,
                                    customStartTime = occurrence.startTime,
                                    customEndTime = occurrence.endTime
                                )
                            )
                        )
                        showCourseEditDialog = true
                    }
                    selectedOccurrence = null
                },
                onStartFocus = { title, location ->
                    focusTargetTitle = title
                    focusTargetCourse = title
                    autoStartFocus = true
                    selectedTab = NavigationTab.STUDY
                },
                onDeleteCourse = { scope ->
                    val isEvent = occurrence.calendarEventId != null || occurrence.courseId == null
                    val schedId = occurrence.scheduleId ?: occurrence.calendarEventId

                    if (isEvent) {
                        val targetEventId = occurrence.calendarEventId ?: occurrence.scheduleId
                        when (scope) {
                            EditingScope.THIS_ONLY -> {
                                if (targetEventId != null) {
                                    val ex = ScheduleException(
                                        id = UUID.randomUUID().toString(),
                                        scheduleId = targetEventId,
                                        originalDate = occurrence.date,
                                        type = ExceptionType.CANCEL,
                                        reason = "单次删除"
                                    )
                                    sampleExceptions.add(ex)
                                    org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                                }
                            }
                            EditingScope.THIS_AND_FUTURE -> {
                                val event = sampleSchedules.find { it.id == targetEventId }
                                if (event != null) {
                                    val startDStr = event.startDate ?: event.startTime.split("T").firstOrNull() ?: occurrence.date
                                    if (occurrence.date <= startDStr) {
                                        sampleSchedules.removeAll { it.id == targetEventId }
                                        sampleExceptions.removeAll { it.scheduleId == targetEventId }
                                        org.classflow.data.local.CourseDataManager.saveSchedules(context, sampleSchedules)
                                        org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                                        if (targetEventId != null) {
                                            org.classflow.data.local.CourseDataManager.addTombstone(context, targetEventId, "calendarEvent")
                                        }
                                        val curDate = try { java.time.LocalDate.parse(occurrence.date) } catch (e: Exception) { null }
                                        if (curDate != null) {
                                            val prevDateStr = curDate.minusDays(1).toString()
                                            val updatedWeeks = if (event.customWeeks.isNotEmpty()) {
                                                val curWeek = occurrence.weekNumber
                                                event.customWeeks.filter { it < curWeek }
                                            } else {
                                                event.customWeeks
                                            }
                                            if (event.customWeeks.isNotEmpty() && updatedWeeks.isEmpty()) {
                                                sampleSchedules.removeAll { it.id == targetEventId }
                                                sampleExceptions.removeAll { it.scheduleId == targetEventId }
                                                org.classflow.data.local.CourseDataManager.saveSchedules(context, sampleSchedules)
                                                org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                                                if (targetEventId != null) {
                                                    org.classflow.data.local.CourseDataManager.addTombstone(context, targetEventId, "calendarEvent")
                                                }
                                            } else {
                                                val updatedEvent = event.copy(
                                                    endDate = prevDateStr,
                                                    endRepeatType = "untilDate",
                                                    endRepeatUntil = prevDateStr,
                                                    customWeeks = updatedWeeks
                                                )
                                                val idx = sampleSchedules.indexOfFirst { it.id == targetEventId }
                                                if (idx >= 0) {
                                                    sampleSchedules[idx] = updatedEvent
                                                    org.classflow.data.local.CourseDataManager.saveSchedules(context, sampleSchedules)
                                                }
                                            }
                                        } else {
                                            sampleSchedules.removeAll { it.id == targetEventId }
                                            sampleExceptions.removeAll { it.scheduleId == targetEventId }
                                            org.classflow.data.local.CourseDataManager.saveSchedules(context, sampleSchedules)
                                            org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                                            if (targetEventId != null) {
                                                org.classflow.data.local.CourseDataManager.addTombstone(context, targetEventId, "calendarEvent")
                                            }
                                        }
                                    }
                                }
                            }
                            EditingScope.ALL -> {
                                sampleSchedules.removeAll { it.id == targetEventId }
                                sampleExceptions.removeAll { it.scheduleId == targetEventId }
                                org.classflow.data.local.CourseDataManager.saveSchedules(context, sampleSchedules)
                                org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                                if (targetEventId != null) {
                                    org.classflow.data.local.CourseDataManager.addTombstone(context, targetEventId, "calendarEvent")
                                }
                            }
                        }
                        selectedOccurrence = null
                        Toast.makeText(context, "已删除日程「${occurrence.title}」", Toast.LENGTH_SHORT).show()
                    } else {
                        // Course deletion
                        when (scope) {
                            EditingScope.THIS_ONLY -> {
                                if (schedId != null) {
                                    val ex = ScheduleException(
                                        id = UUID.randomUUID().toString(),
                                        scheduleId = schedId,
                                        originalDate = occurrence.date,
                                        type = ExceptionType.CANCEL,
                                        reason = "单次删除"
                                    )
                                    sampleExceptions.add(ex)
                                    org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                                }
                                selectedOccurrence = null
                                Toast.makeText(context, "已删除第 ${occurrence.weekNumber} 周「${occurrence.title}」课时", Toast.LENGTH_SHORT).show()
                            }
                            EditingScope.THIS_AND_FUTURE -> {
                                val course = sampleCourses.find { it.id == occurrence.courseId }
                                if (course != null && schedId != null) {
                                    val currentWeekNum = occurrence.weekNumber
                                    val updatedSchedules = course.schedules.mapNotNull { s ->
                                        if (s.id == schedId) {
                                            val existingWeeks = if (s.customWeeks.isNotEmpty()) s.customWeeks else (s.startWeek..s.endWeek).toList()
                                            val newWeeks = existingWeeks.filter { it < currentWeekNum }
                                            if (newWeeks.isEmpty()) null
                                            else s.copy(customWeeks = newWeeks, startWeek = newWeeks.first(), endWeek = newWeeks.last())
                                        } else {
                                            s
                                        }
                                    }

                                    if (updatedSchedules.isEmpty()) {
                                        sampleCourses.removeAll { it.id == course.id }
                                        org.classflow.data.local.CourseDataManager.addTombstone(context, course.id, "course")
                                        course.schedules.forEach { s ->
                                            org.classflow.data.local.CourseDataManager.addTombstone(context, s.id, "schedule")
                                        }
                                    } else {
                                        val updatedCourse = course.copy(schedules = updatedSchedules)
                                        val idx = sampleCourses.indexOfFirst { it.id == course.id }
                                        if (idx >= 0) {
                                            sampleCourses[idx] = updatedCourse
                                        }
                                    }
                                    org.classflow.data.local.CourseDataManager.saveCourses(context, sampleCourses)
                                }
                                selectedOccurrence = null
                                Toast.makeText(context, "已删除本次及随后所有「${occurrence.title}」课时", Toast.LENGTH_SHORT).show()
                            }
                            EditingScope.ALL -> {
                                val cId = occurrence.courseId
                                val targetCourse = sampleCourses.find { it.id == cId }
                                sampleCourses.removeAll { it.id == cId }
                                org.classflow.data.local.CourseDataManager.addTombstone(context, cId, "course")
                                targetCourse?.schedules?.forEach { s ->
                                    org.classflow.data.local.CourseDataManager.addTombstone(context, s.id, "schedule")
                                }
                                if (schedId != null) {
                                    sampleExceptions.removeAll { it.scheduleId == schedId }
                                    org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                                }
                                org.classflow.data.local.CourseDataManager.saveCourses(context, sampleCourses)
                                selectedOccurrence = null
                                Toast.makeText(context, "已彻底删除课程「${occurrence.title}」", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onApplyMutation = { _, newRoom ->
                    val schedId = occurrence.scheduleId ?: occurrence.calendarEventId
                    if (!schedId.isNullOrBlank()) {
                        val ex = ScheduleException(
                            id = UUID.randomUUID().toString(),
                            scheduleId = schedId,
                            originalDate = occurrence.date,
                            type = ExceptionType.ROOM_CHANGE,
                            targetClassroom = newRoom,
                            reason = "教室调整为 $newRoom"
                        )
                        sampleExceptions.add(ex)
                        org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                    }
                    selectedOccurrence = null
                    Toast.makeText(context, "已调整地点为「$newRoom」", Toast.LENGTH_SHORT).show()
                },
                onCancelCourse = { _ ->
                    val schedId = occurrence.scheduleId ?: occurrence.calendarEventId
                    if (!schedId.isNullOrBlank()) {
                        val isCancelled = occurrence.status == OccurrenceStatus.CANCELLED
                        if (isCancelled) {
                            sampleExceptions.removeAll { it.scheduleId == schedId && it.originalDate == occurrence.date && it.type == ExceptionType.CANCEL }
                            org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                            Toast.makeText(context, "已恢复正常上课/进行", Toast.LENGTH_SHORT).show()
                        } else {
                            val ex = ScheduleException(
                                id = UUID.randomUUID().toString(),
                                scheduleId = schedId,
                                originalDate = occurrence.date,
                                type = ExceptionType.CANCEL,
                                reason = "停课"
                            )
                            sampleExceptions.add(ex)
                            org.classflow.data.local.CourseDataManager.saveExceptions(context, sampleExceptions.toList())
                            Toast.makeText(context, "第 ${occurrence.weekNumber} 周已设为停课", Toast.LENGTH_SHORT).show()
                        }
                    }
                    selectedOccurrence = null
                }
            )
        }

        // Course Edit Dialog (1:1 macOS-grade parity)
        if (showCourseEditDialog && courseToEdit != null) {
            val existingNames = sampleCourses.map { it.name }.distinct()
            val existingTeachers = sampleCourses.mapNotNull { it.teacher }.distinct()
            val existingClassrooms = sampleCourses.flatMap { it.schedules }.mapNotNull { it.classroom }.distinct()

            CourseEditDialog(
                course = courseToEdit!!,
                totalWeeks = currentSemester?.totalWeeks ?: 20,
                availableSections = sectionTimes,
                existingCourseNames = existingNames,
                existingTeachers = existingTeachers,
                existingClassrooms = existingClassrooms,
                isDark = isDark,
                onDismiss = {
                    showCourseEditDialog = false
                    courseToEdit = null
                },
                onSaveCourse = { updatedCourse ->
                    val idx = sampleCourses.indexOfFirst { it.id == updatedCourse.id }
                    if (idx >= 0) {
                        sampleCourses[idx] = updatedCourse
                    } else {
                        sampleCourses.add(updatedCourse)
                    }
                    org.classflow.data.local.CourseDataManager.saveCourses(context, sampleCourses)
                    showCourseEditDialog = false
                    courseToEdit = null
                    Toast.makeText(context, "课程「${updatedCourse.name}」已更新！", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Paste JSON Import Dialog
        if (showPasteImportDialog) {
            PasteJSONImportDialog(
                isDark = isDark,
                onDismiss = { showPasteImportDialog = false },
                onImportSuccess = { importedCourses, _ ->
                    sampleCourses.addAll(importedCourses)
                    org.classflow.data.local.CourseDataManager.saveCourses(context, sampleCourses)
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
                onPickFile = {
                    showFileImportDialog = false
                    importFileLauncher.launch(arrayOf("*/*", "application/zip", "text/calendar"))
                },
                onImportDemo = {
                    val demoCourse = Course(
                        id = "c_phy_${System.currentTimeMillis()}",
                        semesterId = "sem_1",
                        name = "大学物理实验",
                        teacher = "赵教授",
                        color = "#EA4335",
                        schedules = listOf(
                            CourseSchedule(
                                dayOfWeek = 4,
                                startSection = 5,
                                endSection = 6,
                                classroom = "物理实验楼 204",
                                weekPattern = WeekPatternType.ALL,
                                customWeeks = (1..16).toList()
                            )
                        )
                    )
                    sampleCourses.add(demoCourse)
                    CourseDataManager.saveCourses(context, sampleCourses)
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
    return ClassFlowBackupManager.generateICSCalendar(occurrences, schedules)
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
    onPickFile: () -> Unit = {},
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
                OutlinedButton(
                    onClick = onImportDemo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("加载示例数据", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPickFile,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("选择系统文件", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
