package org.classflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.classflow.data.model.*
import org.classflow.ui.agenda.AgendaScreen
import org.classflow.ui.detail.CourseDetailBottomSheet
import org.classflow.ui.profile.ProfileScreen
import org.classflow.ui.timetable.TimetableScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreenHost()
            }
        }
    }
}

enum class NavigationTab {
    TIMETABLE,
    AGENDA,
    PROFILE
}

@Composable
fun MainScreenHost() {
    var selectedTab by remember { mutableStateOf(NavigationTab.TIMETABLE) }
    var currentWeek by remember { mutableStateOf(1) }
    val totalWeeks = 16
    val semesterName = "2026-2027 秋季学期"

    var selectedOccurrence by remember { mutableStateOf<TimelineItem?>(null) }

    // Sample initial occurrences
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.TIMETABLE,
                    onClick = { selectedTab = NavigationTab.TIMETABLE },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "课程表") },
                    label = { Text("课程表") }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.AGENDA,
                    onClick = { selectedTab = NavigationTab.AGENDA },
                    icon = { Icon(Icons.Default.ViewTimeline, contentDescription = "日程") },
                    label = { Text("日程") }
                )
                NavigationBarItem(
                    selected = selectedTab == NavigationTab.PROFILE,
                    onClick = { selectedTab = NavigationTab.PROFILE },
                    icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                    label = { Text("我的") }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.TIMETABLE -> TimetableScreen(
                    currentWeek = currentWeek,
                    totalWeeks = totalWeeks,
                    semesterName = semesterName,
                    occurrences = sampleOccurrences,
                    onWeekChange = { currentWeek = it },
                    onCourseClick = { selectedOccurrence = it },
                    onAddCourseClick = { /* Add course modal */ },
                    onSyncClick = { /* Trigger sync */ }
                )

                NavigationTab.AGENDA -> AgendaScreen(
                    todayOccurrences = sampleOccurrences,
                    onCourseClick = { selectedOccurrence = it }
                )

                NavigationTab.PROFILE -> ProfileScreen(
                    currentSemesterName = semesterName,
                    onSyncNow = { /* Sync calendar */ },
                    onSaveQiniuCredentials = { _, _, _, _ -> }
                )
            }
        }

        // Inspection / Mutation Bottom Sheet
        selectedOccurrence?.let { occurrence ->
            CourseDetailBottomSheet(
                item = occurrence,
                onDismiss = { selectedOccurrence = null },
                onApplyMutation = { scope, newRoom ->
                    // Apply mutation logic
                    selectedOccurrence = null
                },
                onCancelCourse = { scope ->
                    sampleOccurrences.removeAll { it.id == occurrence.id }
                    selectedOccurrence = null
                }
            )
        }
    }
}
