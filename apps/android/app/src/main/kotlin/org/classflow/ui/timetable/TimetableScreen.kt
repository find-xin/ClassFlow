package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.CalendarEvent
import org.classflow.data.model.SectionTime
import org.classflow.data.model.Semester
import org.classflow.data.model.TimelineItem
import org.classflow.ui.components.parseHexColor
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import org.classflow.data.model.AppSettings

private fun Color.isLight(): Boolean {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return luminance > 0.62f
}

private fun Color.deepShade(): Color {
    val factor = 0.45f
    return Color(red * factor, green * factor, blue * factor, 1f)
}

@Composable
fun TimetableScreen(
    currentWeek: Int,
    selectedWeek: Int,
    totalWeeks: Int,
    semesterName: String,
    activeSemesterId: String = "",
    semestersList: List<Semester> = emptyList(),
    occurrences: List<TimelineItem>,
    scheduleEvents: List<CalendarEvent> = emptyList(),
    sectionTimes: List<SectionTime> = emptyList(),
    cardOpacity: Float = 0.88f,
    isDark: Boolean = false,
    isWallpaperDark: Boolean = true,
    appSettings: AppSettings = AppSettings(),
    onWeekChange: (Int) -> Unit,
    onSemesterChange: (String) -> Unit = {},
    onSelectSemester: (Semester) -> Unit = {},
    onCreateSemester: (Semester) -> Unit = {},
    onUpdateSemester: (Semester) -> Unit = {},
    onDeleteSemester: (Semester) -> Unit = {},
    onCourseClick: (TimelineItem) -> Unit,
    onScheduleClick: (CalendarEvent) -> Unit = {},
    onAddCourseClick: () -> Unit,
    onAddScheduleClick: () -> Unit,
    onAddCourseAtSlot: (dayOfWeek: Int, section: Int) -> Unit = { _, _ -> },
    onAddScheduleAtSlot: (dayOfWeek: Int, section: Int) -> Unit = { _, _ -> },
    onImportClick: (ImportType) -> Unit = {},
    onExportClick: (ExportType) -> Unit = {},
    topCourseSelections: Map<String, String> = emptyMap(),
    onSelectTopCourse: (clusterKey: String, courseId: String) -> Unit = { _, _ -> },
    onOpenThreeDotsMenu: () -> Unit
) {
    var displayMode by remember { mutableStateOf("week") } // "week" or "day"
    var activeSlotForAction by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Calculate dates for the 7 days of the selected week
    val weekDates = remember(selectedWeek, currentWeek) {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        val currentWeekIdx = currentWeek
        val weekDiff = selectedWeek - currentWeekIdx
        cal.add(Calendar.WEEK_OF_YEAR, weekDiff)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        (0..6).map {
            val dateStr = df.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            dateStr
        }
    }

    val todayDateString = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    val effectiveShowSaturday = appSettings.showWeekends && appSettings.showSaturday
    val effectiveShowSunday = appSettings.showWeekends && appSettings.showSunday

    var totalDragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedWeek, totalWeeks) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragOffset = 0f },
                    onDragEnd = {
                        val threshold = 60.dp.toPx()
                        if (totalDragOffset < -threshold && selectedWeek < totalWeeks) {
                            onWeekChange(selectedWeek + 1)
                        } else if (totalDragOffset > threshold && selectedWeek > 1) {
                            onWeekChange(selectedWeek - 1)
                        }
                        totalDragOffset = 0f
                    },
                    onDragCancel = { totalDragOffset = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragOffset += dragAmount
                    }
                )
            }
    ) {
        // 1. WakeUp Style Top Bar with Week / Day View Toggle
        WakeUpHeaderView(
            currentWeek = currentWeek,
            selectedWeek = selectedWeek,
            totalWeeks = totalWeeks,
            semesterName = semesterName,
            activeSemesterId = activeSemesterId,
            semestersList = semestersList,
            displayMode = displayMode,
            isDark = isDark,
            isWallpaperDark = isWallpaperDark,
            onWeekChange = onWeekChange,
            onToggleDisplayMode = { displayMode = it },
            onSemesterChange = onSemesterChange,
            onSelectSemester = onSelectSemester,
            onCreateSemester = onCreateSemester,
            onUpdateSemester = onUpdateSemester,
            onDeleteSemester = onDeleteSemester,
            onAddCourseClick = onAddCourseClick,
            onAddScheduleClick = onAddScheduleClick,
            onImportClick = onImportClick,
            onExportClick = onExportClick,
            onOpenThreeDotsMenu = onOpenThreeDotsMenu
        )

        if (displayMode == "day") {
            // Day View (日视图时间轴)
            DayViewTimeline(
                occurrences = occurrences.filter { it.weekNumber == selectedWeek },
                scheduleEvents = scheduleEvents,
                todayDateString = todayDateString,
                isDark = isDark,
                isWallpaperDark = isWallpaperDark,
                onCourseClick = onCourseClick,
                onScheduleClick = onScheduleClick
            )
        } else {
            // 2. Day Header Row (Month + visible days matching WakeUp Image 1)
            WakeUpDayHeaderRow(
                dateStrings = weekDates,
                todayDateString = todayDateString,
                showSaturday = effectiveShowSaturday,
                showSunday = effectiveShowSunday,
                isWallpaperDark = isWallpaperDark
            )

            // 3. Timetable Canvas (Section Axis + Day Columns)
            TimetableCanvas(
                occurrences = occurrences,
                scheduleEvents = scheduleEvents,
                sectionTimes = sectionTimes,
                todayDateString = todayDateString,
                weekDates = weekDates,
                selectedWeek = selectedWeek,
                appSettings = appSettings,
                isDark = isDark,
                isWallpaperDark = isWallpaperDark,
                activeSlotForAction = activeSlotForAction,
                onDismissSlotAction = { activeSlotForAction = null },
                onCourseClick = onCourseClick,
                onScheduleClick = onScheduleClick,
                onAddAtSlot = { day, sec -> activeSlotForAction = day to sec },
                onAddCourseAtSlot = { d, s ->
                    activeSlotForAction = null
                    onAddCourseAtSlot(d, s)
                },
                onAddScheduleAtSlot = { d, s ->
                    activeSlotForAction = null
                    onAddScheduleAtSlot(d, s)
                },
                topCourseSelections = topCourseSelections,
                onSelectTopCourse = onSelectTopCourse
            )
        }
    }
}

/**
 * WakeUp style day header row with stacked month indicator and date numbers,
 * matching WakeUp Image 1.
 */
@Composable
fun WakeUpDayHeaderRow(
    dateStrings: List<String>,
    todayDateString: String,
    showSaturday: Boolean = true,
    showSunday: Boolean = true,
    isWallpaperDark: Boolean = true
) {
    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")

    // Extract current month from the first date
    val monthNumber = remember(dateStrings) {
        val first = dateStrings.firstOrNull() ?: ""
        val parts = first.split("-")
        if (parts.size >= 2) {
            parts[1].toIntOrNull()?.toString() ?: "10"
        } else "10"
    }

    val visibleDays = remember(showSaturday, showSunday) {
        buildList {
            for (i in 1..5) add(i)
            if (showSaturday) add(6)
            if (showSunday) add(7)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Month Column: Stacked "10" and "月" (width 44dp, matching WakeUp Image 1)
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = monthNumber,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWallpaperDark) Color.White else Color(0xFF1F2937)
            )
            Text(
                text = "月",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isWallpaperDark) Color.White.copy(alpha = 0.70f) else Color(0xFF6B7280)
            )
        }

        // 2. Weekday & Day Number Columns
        for (dayIdx in visibleDays) {
            val index = dayIdx - 1
            val day = dayNames.getOrElse(index) { "" }
            val dateStr = if (index < dateStrings.size) dateStrings[index] else ""
            val isToday = (dateStr == todayDateString)
            val dayNumber = remember(dateStr) {
                val parts = dateStr.split("-")
                if (parts.size == 3) {
                    parts[2].toIntOrNull()?.toString() ?: parts[2]
                } else dateStr
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = day,
                    fontSize = 12.5.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isWallpaperDark) {
                        if (isToday) Color.White else Color.White.copy(alpha = 0.65f)
                    } else {
                        if (isToday) Color(0xFF111827) else Color(0xFF9CA3AF)
                    }
                )

                if (isToday) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFF5A5F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayNumber,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayNumber,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isWallpaperDark) {
                                Color.White.copy(alpha = 0.70f)
                            } else {
                                Color(0xFF6B7280)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Represents an overlapping cluster of courses occupying overlapping sections in a single day.
 */
data class OverlapCluster(
    val key: String,
    val items: List<TimelineItem>,
    val minStartSection: Int,
    val maxEndSection: Int
)

/**
 * Determines whether two timeline items overlap in time interval or section range.
 */
fun itemsOverlap(a: TimelineItem, b: TimelineItem): Boolean {
    fun parseMinutes(t: String?): Int? {
        if (t.isNullOrBlank()) return null
        val parts = t.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return h * 60 + m
        }
        return null
    }

    val aStart = parseMinutes(a.startTime)
    val aEnd = parseMinutes(a.endTime)
    val bStart = parseMinutes(b.startTime)
    val bEnd = parseMinutes(b.endTime)

    val aRange = if (aStart != null && aEnd != null && aEnd > aStart) {
        aStart to aEnd
    } else if (aStart != null) {
        aStart to (aStart + 45)
    } else null

    val bRange = if (bStart != null && bEnd != null && bEnd > bStart) {
        bStart to bEnd
    } else if (bStart != null) {
        bStart to (bStart + 45)
    } else null

    if (aRange != null && bRange != null) {
        return aRange.first < bRange.second && bRange.first < aRange.second
    }

    return maxOf(a.startSection, b.startSection) <= minOf(a.endSection, b.endSection)
}

/**
 * Clusters courses of a day into groups of mutually overlapping courses.
 * Non-overlapping courses form 1-item clusters; overlapping courses form multi-item clusters.
 * Active current-week courses always take precedence. Non-current week courses are only
 * displayed if they do not overlap with any active course or schedule for the current week.
 */
fun clusterDayCourses(courses: List<TimelineItem>, dayOfWeek: Int): List<OverlapCluster> {
    if (courses.isEmpty()) return emptyList()

    // 1. Separate current-week active items from non-current-week items
    val activeItems = courses.filter { it.isCurrentWeek }
    val nonCurrentItems = courses.filter { !it.isCurrentWeek }

    // 2. Active items take absolute precedence for the current week view.
    // A non-current-week item is only displayed if it does NOT overlap with any active item this week.
    val displayableNonCurrent = mutableListOf<TimelineItem>()
    for (nonItem in nonCurrentItems) {
        val overlapsActive = activeItems.any { active -> itemsOverlap(nonItem, active) }
        if (!overlapsActive) {
            val overlapsExisting = displayableNonCurrent.any { existing -> itemsOverlap(nonItem, existing) }
            if (!overlapsExisting) {
                displayableNonCurrent.add(nonItem)
            }
        }
    }

    val displayCourses = (activeItems + displayableNonCurrent).sortedWith(compareBy({ it.startSection }, { it.endSection }))
    val clusters = mutableListOf<MutableList<TimelineItem>>()

    for (item in displayCourses) {
        var placed = false
        for (cluster in clusters) {
            val overlaps = cluster.any { other ->
                itemsOverlap(item, other)
            }
            if (overlaps) {
                cluster.add(item)
                placed = true
                break
            }
        }
        if (!placed) {
            clusters.add(mutableListOf(item))
        }
    }

    return clusters.map { list ->
        val sortedList = list.sortedWith(compareBy({ it.startSection }, { it.endSection }))
        val minStart = sortedList.minOf { it.startSection }
        val maxEnd = sortedList.maxOf { it.endSection }
        val stableKey = "day_${dayOfWeek}_sec_${minStart}_" + sortedList.map { it.courseId ?: it.id }.sorted().joinToString("_")
        OverlapCluster(
            key = stableKey,
            items = sortedList,
            minStartSection = minStart,
            maxEndSection = maxEnd
        )
    }
}

/**
 * Timetable grid canvas with accurate section axis, today column soft highlighting,
 * and slot click-to-add support, fully respecting user appearance settings.
 */
@Composable
fun TimetableCanvas(
    occurrences: List<TimelineItem>,
    scheduleEvents: List<CalendarEvent>,
    sectionTimes: List<SectionTime>,
    todayDateString: String,
    weekDates: List<String>,
    selectedWeek: Int,
    appSettings: AppSettings = AppSettings(),
    isDark: Boolean = false,
    isWallpaperDark: Boolean = true,
    activeSlotForAction: Pair<Int, Int>? = null,
    onDismissSlotAction: () -> Unit = {},
    onCourseClick: (TimelineItem) -> Unit,
    onScheduleClick: (CalendarEvent) -> Unit,
    onAddAtSlot: (dayOfWeek: Int, section: Int) -> Unit,
    onAddCourseAtSlot: (dayOfWeek: Int, section: Int) -> Unit = { _, _ -> },
    onAddScheduleAtSlot: (dayOfWeek: Int, section: Int) -> Unit = { _, _ -> },
    topCourseSelections: Map<String, String> = emptyMap(),
    onSelectTopCourse: (clusterKey: String, courseId: String) -> Unit = { _, _ -> }
) {
    val totalSections = remember(sectionTimes, occurrences) {
        val maxConfigSection = if (sectionTimes.isNotEmpty()) {
            maxOf(sectionTimes.size, sectionTimes.maxOf { it.section })
        } else {
            12
        }
        val maxCourseSection = occurrences.maxOfOrNull { it.endSection } ?: 0
        maxOf(1, maxOf(maxConfigSection, maxCourseSection))
    }
    val sectionHeight = appSettings.gridHeight.dp

    // Fallback section times if not provided
    val defaultTimes = remember {
        listOf(
            "08:00\n08:45", "08:50\n09:35", "10:00\n10:45", "10:50\n11:35",
            "13:30\n14:15", "14:20\n15:05", "15:25\n16:10", "16:15\n17:00",
            "18:30\n19:15", "19:20\n20:05", "20:15\n21:00", "21:05\n21:50"
        )
    }

    val visibleDays = remember(appSettings.showWeekends, appSettings.showSaturday, appSettings.showSunday) {
        buildList {
            for (i in 1..5) add(i)
            if (appSettings.showWeekends && appSettings.showSaturday) add(6)
            if (appSettings.showWeekends && appSettings.showSunday) add(7)
        }
    }

    val coursesByDay = remember(occurrences) { occurrences.groupBy { it.dayOfWeek } }
    var activePickerCluster by remember { mutableStateOf<OverlapCluster?>(null) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        // 1. Left Section Axis with section numbers and start/end times (width 44dp matching SectionAxisView.swift)
        Column(modifier = Modifier.width(44.dp)) {
            for (sec in 1..totalSections) {
                val secTime = sectionTimes.find { it.section == sec }
                val (fallbackStart, fallbackEnd) = run {
                    val dt = defaultTimes.getOrNull(sec - 1)
                    if (dt != null) {
                        Pair(dt.substringBefore("\n"), dt.substringAfter("\n"))
                    } else {
                        val prevEnd = sectionTimes.find { it.section == sec - 1 }?.endTime
                            ?: defaultTimes.lastOrNull()?.substringAfter("\n")
                            ?: "21:50"
                        val parts = prevEnd.split(":")
                        val ph = parts.getOrNull(0)?.toIntOrNull() ?: 21
                        val pm = parts.getOrNull(1)?.toIntOrNull() ?: 50
                        val startMin = (ph * 60 + pm + 10) % (24 * 60)
                        val endMin = (startMin + 45) % (24 * 60)
                        Pair(
                            String.format(java.util.Locale.getDefault(), "%02d:%02d", startMin / 60, startMin % 60),
                            String.format(java.util.Locale.getDefault(), "%02d:%02d", endMin / 60, endMin % 60)
                        )
                    }
                }
                val startT = secTime?.startTime ?: fallbackStart
                val endT = secTime?.endTime ?: fallbackEnd

                Column(
                    modifier = Modifier
                        .height(sectionHeight)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (sec <= 0) "早" else if (sec >= 90) "晚" else sec.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWallpaperDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1F2937).copy(alpha = 0.85f)
                    )
                    if (appSettings.showSectionTimeInAxis) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = startT,
                            fontSize = 8.5.sp,
                            lineHeight = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = if (isWallpaperDark) Color.White.copy(alpha = 0.70f) else Color(0xFF6B7280)
                        )
                        Text(
                            text = endT,
                            fontSize = 8.sp,
                            lineHeight = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isWallpaperDark) Color.White.copy(alpha = 0.50f) else Color(0xFF9CA3AF)
                        )
                    }
                }
            }
        }

        // 2. Day Columns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(sectionHeight * totalSections)
        ) {
            for (dayIdx in visibleDays) {
                val dateStr = if (dayIdx - 1 < weekDates.size) weekDates[dayIdx - 1] else ""
                val isToday = (dateStr == todayDateString)
                val dayCourses = coursesByDay[dayIdx] ?: emptyList()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                ) {
                    // Empty grid slot click targets & guidelines
                    Column(modifier = Modifier.fillMaxSize()) {
                        for (sec in 1..totalSections) {
                            val isSlotActive = (activeSlotForAction == (dayIdx to sec))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(sectionHeight)
                                    .clickable { onAddAtSlot(dayIdx, sec) }
                            ) {
                                if (appSettings.showGridGuideLines && sec < totalSections) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(0.8.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                if (isWallpaperDark) Color.White.copy(alpha = 0.22f)
                                                else Color.Black.copy(alpha = 0.12f)
                                            )
                                    )
                                }

                                // Anchored popover mounted on-demand only for the clicked slot
                                if (isSlotActive) {
                                    SlotActionMenu(
                                        expanded = true,
                                        dayOfWeek = dayIdx,
                                        section = sec,
                                        isDark = isDark,
                                        onDismiss = onDismissSlotAction,
                                        onAddCourse = onAddCourseAtSlot,
                                        onAddSchedule = onAddScheduleAtSlot
                                    )
                                }
                            }
                        }
                    }

                    // Render Course Clusters (handling non-overlapping single courses and user-selectable overlapping courses)
                    val clusters = remember(dayCourses, dayIdx) { clusterDayCourses(dayCourses, dayIdx) }
                    for (cluster in clusters) {
                        if (cluster.items.size == 1) {
                            val item = cluster.items.first()
                            val isActiveThisWeek = item.isCurrentWeek
                            val topOffset = (item.startSection - 1) * appSettings.gridHeight
                            val duration = maxOf(1, item.endSection - item.startSection + 1)
                            val cardHeight = duration * appSettings.gridHeight - 3

                            CourseCard(
                                item = item,
                                isActiveThisWeek = isActiveThisWeek,
                                appSettings = appSettings,
                                isDark = isDark,
                                modifier = Modifier
                                    .padding(top = topOffset.dp)
                                    .height(cardHeight.dp)
                                    .fillMaxWidth()
                                    .clickable { onCourseClick(item) }
                            )
                        } else {
                            val selectedId = topCourseSelections[cluster.key]
                                ?: cluster.items.firstOrNull { it.isCurrentWeek }?.let { it.courseId ?: it.id }
                                ?: cluster.items.first().let { it.courseId ?: it.id }
                            val topItem = cluster.items.find { (it.courseId ?: it.id) == selectedId || it.id == selectedId }
                                ?: cluster.items.first()
                            val isActiveThisWeek = topItem.isCurrentWeek
                            val topOffset = (topItem.startSection - 1) * appSettings.gridHeight
                            val duration = maxOf(1, topItem.endSection - topItem.startSection + 1)
                            val cardHeight = duration * appSettings.gridHeight - 3

                            Box(
                                modifier = Modifier
                                    .padding(top = topOffset.dp)
                                    .height(cardHeight.dp)
                                    .fillMaxWidth()
                            ) {
                                // Stacked physical card deck shadow
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(x = 2.dp, y = 2.dp)
                                        .clip(RoundedCornerShape(appSettings.cardCornerRadius.dp))
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))
                                )

                                CourseCard(
                                    item = topItem,
                                    isActiveThisWeek = isActiveThisWeek,
                                    appSettings = appSettings,
                                    isDark = isDark,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onCourseClick(topItem) }
                                )

                                // Overlap indicator badge
                                Surface(
                                    shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = appSettings.cardCornerRadius.dp),
                                    color = Color(0xDD1E202B),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .clickable { activePickerCluster = cluster }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${cluster.items.size}重叠▾",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
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

    if (activePickerCluster != null) {
        val cluster = activePickerCluster!!
        val selectedId = topCourseSelections[cluster.key]
            ?: cluster.items.firstOrNull { it.isCurrentWeek }?.let { it.courseId ?: it.id }
            ?: cluster.items.first().let { it.courseId ?: it.id }

        OverlapCoursePickerDialog(
            cluster = cluster,
            selectedCourseId = selectedId,
            isDark = isDark,
            onDismiss = { activePickerCluster = null },
            onSelectTopCourse = { courseId ->
                onSelectTopCourse(cluster.key, courseId)
                activePickerCluster = null
            },
            onCourseClick = { item ->
                activePickerCluster = null
                onCourseClick(item)
            }
        )
    }
}

/**
 * Dialog allowing users to select which course is displayed on top for overlapping slots.
 * Provides full control without relying on desktop swimlanes.
 */
@Composable
fun OverlapCoursePickerDialog(
    cluster: OverlapCluster,
    selectedCourseId: String?,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSelectTopCourse: (courseId: String) -> Unit,
    onCourseClick: (TimelineItem) -> Unit
) {
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFFA1A3AC) else Color(0xFF6B7280)
    val cardBg = if (isDark) Color(0xFF262838) else Color(0xFFF3F4F8)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
        title = {
            Column {
                Text(
                    text = "重叠课程选择 (${cluster.items.size} 门重叠)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = "第 ${cluster.minStartSection} - ${cluster.maxEndSection} 节存在时间重叠，请选择置顶展示的课程：",
                    fontSize = 11.5.sp,
                    color = textSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                cluster.items.forEach { item ->
                    val itemId = item.courseId ?: item.id
                    val isTop = (itemId == selectedCourseId || item.id == selectedCourseId)
                    val itemColor = remember(item.color) { parseHexColor(item.color) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isTop) 1.5.dp else 0.5.dp,
                                color = if (isTop) Color(0xFF4F46E5) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(itemColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.title,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (item.isCurrentWeek) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "本周有课",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF059669),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Gray.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "非本周",
                                            fontSize = 10.sp,
                                            color = textSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item.location?.let {
                                    Text("@$it", fontSize = 11.5.sp, color = textSecondary)
                                }
                                item.teacher?.let {
                                    Text(it, fontSize = 11.5.sp, color = textSecondary)
                                }
                                Text("第${item.startSection}-${item.endSection}节", fontSize = 11.5.sp, color = textSecondary)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { onCourseClick(item) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("查看详情", fontSize = 11.5.sp)
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                if (isTop) {
                                    Button(
                                        onClick = { /* already top */ },
                                        enabled = false,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            disabledContainerColor = Color(0xFF4F46E5).copy(alpha = 0.25f),
                                            disabledContentColor = Color(0xFF4F46E5)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("✓ 当前置顶", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = { onSelectTopCourse(itemId) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("置顶此课", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * WakeUp & macOS parity course card matching WakeUp Image 1:
 * - Centered vertical stack of [非本周], Title, @Classroom, Teacher
 * - Comfortable typography with compact lineHeight preventing premature ellipsis
 * - Full slot filling with subtle rounded corners
 */
@Composable
fun CourseCard(
    item: TimelineItem,
    isActiveThisWeek: Boolean,
    appSettings: AppSettings = AppSettings(),
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardColor = remember(item.color) { parseHexColor(item.color) }
    val baseOpacity = if (isActiveThisWeek) appSettings.cardOpacity else appSettings.cardOpacity * appSettings.nonCurrentWeekOpacityRatio

    val radius = appSettings.cardCornerRadius.dp
    val fontSize = maxOf(12f, appSettings.cardFontSize).sp

    val isLight = remember(cardColor) { cardColor.isLight() }
    val effectiveTextColor = when {
        appSettings.cardTextBlendCardColor -> {
            if (isLight && !isDark) cardColor.deepShade() else cardColor
        }
        appSettings.cardTextColorMode == "light" -> Color.White
        appSettings.cardTextColorMode == "dark" -> Color.Black.copy(alpha = 0.85f)
        else -> {
            // Auto contrast mode matching macOS & WakeUp
            if (isDark) {
                if (isActiveThisWeek) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.78f)
            } else {
                if (isLight) {
                    if (isActiveThisWeek) cardColor.deepShade() else Color.Black.copy(alpha = 0.65f)
                } else {
                    if (isActiveThisWeek) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.80f)
                }
            }
        }
    }

    val effectiveSecondaryTextColor = effectiveTextColor.copy(alpha = 0.85f)

    val borderStrokeColor = when (appSettings.cardBorderColorMode) {
        "cardColor" -> if (isDark) cardColor.copy(alpha = 0.85f) else cardColor
        "subtle" -> if (isDark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.35f)
        else -> {
            if (appSettings.cardBorderUseCardColor) {
                if (isDark) cardColor.copy(alpha = 0.70f) else cardColor
            } else {
                if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.25f)
            }
        }
    }

    val glassBrush = remember(isDark) {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
            } else {
                listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)
            }
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(cardColor.copy(alpha = if (isDark) minOf(baseOpacity, 0.82f) else baseOpacity))
            .border(
                width = if (appSettings.cardUseDottedBorder) 1.dp else 0.5.dp,
                color = borderStrokeColor,
                shape = RoundedCornerShape(radius)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glass top reflection gradient for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glassBrush)
        )

        // Card Content: exact WakeUp centered stack without distracting [非本周] text
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Course Title
            Text(
                text = item.title,
                fontSize = fontSize,
                lineHeight = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = effectiveTextColor,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // Location
            if (appSettings.showLocationInCard && !item.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val locText = if (appSettings.showAtPrefixForRoom) "@${item.location}" else item.location ?: ""
                Text(
                    text = locText,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = effectiveSecondaryTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Teacher
            if (appSettings.showTeacherInCard && !item.teacher.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(1.5.dp))
                Text(
                    text = item.teacher ?: "",
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = effectiveSecondaryTextColor.copy(alpha = 0.90f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Time
            if (appSettings.showTimeInCard && !item.startTime.isNullOrBlank() && !item.endTime.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(1.5.dp))
                Text(
                    text = "${item.startTime}-${item.endTime}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = effectiveSecondaryTextColor.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Notes
            if (appSettings.showNotesInCard && !item.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(1.5.dp))
                Text(
                    text = item.notes ?: "",
                    fontSize = 8.sp,
                    color = effectiveSecondaryTextColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Day View (日视图): Chronological linear timeline of courses and schedules.
 */
@Composable
fun DayViewTimeline(
    occurrences: List<TimelineItem>,
    scheduleEvents: List<CalendarEvent>,
    todayDateString: String,
    isDark: Boolean = false,
    isWallpaperDark: Boolean = false,
    onCourseClick: (TimelineItem) -> Unit,
    onScheduleClick: (CalendarEvent) -> Unit
) {
    val sortedItems = occurrences.sortedBy { it.startSection }
    val isHighContrastDark = isDark || isWallpaperDark
    val cardBg = if (isHighContrastDark) Color(0xFF1E212B).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.94f)
    val cardBorder = androidx.compose.foundation.BorderStroke(1.dp, if (isHighContrastDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
    val titleColor = if (isHighContrastDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val subtextColor = if (isHighContrastDark) Color(0xFFCBD5E1) else Color(0xFF4B5563)
    val headerColor = if (isHighContrastDark) Color.White else Color(0xFF111827)

    if (sortedItems.isEmpty() && scheduleEvents.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    tint = subtextColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "今日无课程或日程安排",
                    color = subtextColor,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "今日安排",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = headerColor
                )
            }

            items(sortedItems, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCourseClick(item) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = cardBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(parseHexColor(item.color))
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = titleColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item.location?.let { loc ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(13.dp), tint = subtextColor)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(loc, fontSize = 12.sp, color = subtextColor)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(13.dp), tint = subtextColor)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("第 ${item.startSection}-${item.endSection} 节 (${item.startTime})", fontSize = 12.sp, color = subtextColor)
                                }
                            }
                        }
                    }
                }
            }

            items(scheduleEvents, key = { it.id }) { evt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onScheduleClick(evt) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = cardBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(parseHexColor(evt.color))
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = evt.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = titleColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${evt.startTime} ~ ${evt.endTime}${evt.location?.let { " · $it" } ?: ""}",
                                fontSize = 12.sp,
                                color = subtextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
