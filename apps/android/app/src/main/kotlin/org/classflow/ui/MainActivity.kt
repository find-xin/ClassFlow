package org.classflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.classflow.data.model.*
import org.classflow.ui.agenda.AgendaScreen
import org.classflow.ui.detail.CourseDetailBottomSheet
import org.classflow.ui.study.StudyScreen
import org.classflow.ui.timetable.*

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
        mutableStateOf(AppSettings(themeMode = "system", enableStudyModule = true))
    }

    val isDark = when (appSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val darkColors = darkColorScheme(
        primary = Color(0xFF6B8AFD),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF283593),
        onPrimaryContainer = Color(0xFFE8EAF6),
        surface = Color(0xFF141416),
        onSurface = Color(0xFFEEEEEE),
        surfaceVariant = Color(0xFF212226),
        onSurfaceVariant = Color(0xFFB0B2B8),
        background = Color(0xFF101012),
        onBackground = Color(0xFFEEEEEE),
        outlineVariant = Color(0xFF33353A)
    )

    val lightColors = lightColorScheme(
        primary = Color(0xFF4F46E5),
        onPrimary = Color.White,
        surface = Color(0xFFFBFBFC),
        onSurface = Color(0xFF1F2937),
        surfaceVariant = Color(0xFFF1F3F6),
        onSurfaceVariant = Color(0xFF6B7280),
        background = Color(0xFFF8F9FA),
        onBackground = Color(0xFF1F2937),
        outlineVariant = Color(0xFFE5E7EB)
    )

    MaterialTheme(
        colorScheme = if (isDark) darkColors else lightColors
    ) {
        MainScreenHost(
            appSettings = appSettings,
            onUpdateSettings = { appSettings = it }
        )
    }
}

@Composable
fun MainScreenHost(
    appSettings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.TIMETABLE) }
    var currentWeek by remember { mutableStateOf(1) }
    val totalWeeks = 16
    val semesterName = "2026-2027 秋季学期"

    var selectedOccurrence by remember { mutableStateOf<TimelineItem?>(null) }
    var showThreeDotsMenu by remember { mutableStateOf(false) }
    var activeQuickActionSheet by remember { mutableStateOf(QuickActionSheetType.NONE) }
    var showAddCourseDialog by remember { mutableStateOf(false) }

    // Sample Section Times
    val sectionTimes = remember {
        listOf(
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
                color = "#059669",
                schedules = listOf(
                    CourseSchedule(dayOfWeek = 3, startSection = 3, endSection = 4, classroom = "信息楼 302")
                )
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
                color = "#059669",
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
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.AGENDA,
                    onClick = { selectedTab = NavigationTab.AGENDA },
                    icon = { Icon(Icons.Default.ViewTimeline, contentDescription = "日程") },
                    label = { Text("日程") }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.TIMETABLE,
                    onClick = { selectedTab = NavigationTab.TIMETABLE },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "课程") },
                    label = { Text("课程") }
                )
                if (appSettings.enableStudyModule) {
                    NavigationBarItem(
                        selected = selectedTab == NavigationTab.STUDY,
                        onClick = { selectedTab = NavigationTab.STUDY },
                        icon = { Icon(Icons.Default.Timer, contentDescription = "学习") },
                        label = { Text("学习") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
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
                    totalWeeks = totalWeeks,
                    semesterName = semesterName,
                    occurrences = sampleOccurrences,
                    onWeekChange = { currentWeek = it },
                    onCourseClick = { selectedOccurrence = it },
                    onAddCourseClick = { showAddCourseDialog = true },
                    onOpenThreeDotsMenu = { showThreeDotsMenu = true }
                )

                NavigationTab.STUDY -> StudyScreen()
            }
        }

        // 3-Dots Quick Actions Menu
        if (showThreeDotsMenu) {
            ThreeDotsMenuDialog(
                currentSettings = appSettings,
                onDismiss = { showThreeDotsMenu = false },
                onOpenAction = { action ->
                    showThreeDotsMenu = false
                    activeQuickActionSheet = action
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
                AppearanceSettingsDialog(
                    appSettings = appSettings,
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

        // Add Course Dialog
        if (showAddCourseDialog) {
            val existingNames = sampleCourses.map { it.name }.distinct()
            val existingTeachers = sampleCourses.mapNotNull { it.teacher }.distinct()
            val existingClassrooms = sampleCourses.flatMap { it.schedules }.mapNotNull { it.classroom }.distinct()

            CourseAddDialog(
                semesterId = "sem_1",
                existingCourseNames = existingNames,
                existingTeachers = existingTeachers,
                existingClassrooms = existingClassrooms,
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
                                color = newCourse.color,
                                teacher = newCourse.teacher
                            )
                        )
                    }
                    showAddCourseDialog = false
                }
            )
        }

        // Inspection / Mutation Bottom Sheet
        selectedOccurrence?.let { occurrence ->
            CourseDetailBottomSheet(
                item = occurrence,
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
    }
}
