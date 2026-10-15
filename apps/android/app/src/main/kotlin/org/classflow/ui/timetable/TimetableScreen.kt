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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.CalendarEvent
import org.classflow.data.model.SectionTime
import org.classflow.data.model.TimelineItem
import org.classflow.ui.components.parseHexColor
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TimetableScreen(
    currentWeek: Int,
    selectedWeek: Int,
    totalWeeks: Int,
    semesterName: String,
    occurrences: List<TimelineItem>,
    scheduleEvents: List<CalendarEvent> = emptyList(),
    sectionTimes: List<SectionTime> = emptyList(),
    onWeekChange: (Int) -> Unit,
    onCourseClick: (TimelineItem) -> Unit,
    onScheduleClick: (CalendarEvent) -> Unit = {},
    onAddCourseClick: () -> Unit,
    onAddScheduleClick: () -> Unit,
    onAddCourseAtSlot: (dayOfWeek: Int, section: Int) -> Unit = { _, _ -> },
    onOpenThreeDotsMenu: () -> Unit
) {
    var displayMode by remember { mutableStateOf(TimetableDisplayMode.WEEK) }

    // Calculate dates for the 7 days of the selected week
    val weekDates = remember(selectedWeek) {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        // Anchor to current week calculation
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

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. WakeUp Style Top Bar
        WakeUpHeaderView(
            currentWeek = currentWeek,
            selectedWeek = selectedWeek,
            totalWeeks = totalWeeks,
            semesterName = semesterName,
            displayMode = displayMode,
            onDisplayModeChange = { displayMode = it },
            onWeekChange = onWeekChange,
            onAddCourseClick = onAddCourseClick,
            onAddScheduleClick = onAddScheduleClick,
            onOpenThreeDotsMenu = onOpenThreeDotsMenu
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

        if (displayMode == TimetableDisplayMode.WEEK) {
            // 2. Day Header Row (Month + 7 days with actual dates and WakeUp highlight)
            WakeUpDayHeaderRow(
                dateStrings = weekDates,
                todayDateString = todayDateString
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))

            // 3. Timetable Canvas (Section Axis + 7 Columns)
            TimetableCanvas(
                occurrences = occurrences,
                scheduleEvents = scheduleEvents,
                sectionTimes = sectionTimes,
                todayDateString = todayDateString,
                weekDates = weekDates,
                selectedWeek = selectedWeek,
                onCourseClick = onCourseClick,
                onScheduleClick = onScheduleClick,
                onAddAtSlot = onAddCourseAtSlot
            )
        } else {
            // Day View (日视图 - Linear Chronological Agenda)
            DayViewTimeline(
                occurrences = occurrences.filter { it.weekNumber == selectedWeek },
                scheduleEvents = scheduleEvents,
                todayDateString = todayDateString,
                onCourseClick = onCourseClick,
                onScheduleClick = onScheduleClick
            )
        }
    }
}

/**
 * WakeUp style day header row with stacked month indicator and date numbers.
 */
@Composable
fun WakeUpDayHeaderRow(
    dateStrings: List<String>,
    todayDateString: String
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Month Column: Stacked "10" and "月"
        Column(
            modifier = Modifier.width(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = monthNumber,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "月",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        // 7 Day Columns
        for ((index, day) in dayNames.withIndex()) {
            val dateStr = if (index < dateStrings.size) dateStrings[index] else ""
            val isToday = (dateStr == todayDateString)
            val dayNumber = remember(dateStr) {
                val parts = dateStr.split("-")
                if (parts.size == 3) parts[2].toIntOrNull()?.toString() ?: parts[2] else ""
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = day,
                    fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                if (isToday) {
                    // WakeUp style elegant rounded badge
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.onSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayNumber,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surface
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayNumber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Timetable grid canvas with accurate section axis, today column soft highlighting,
 * and slot click-to-add support.
 */
@Composable
fun TimetableCanvas(
    occurrences: List<TimelineItem>,
    scheduleEvents: List<CalendarEvent>,
    sectionTimes: List<SectionTime>,
    todayDateString: String,
    weekDates: List<String>,
    selectedWeek: Int,
    onCourseClick: (TimelineItem) -> Unit,
    onScheduleClick: (CalendarEvent) -> Unit,
    onAddAtSlot: (dayOfWeek: Int, section: Int) -> Unit
) {
    val totalSections = 12
    val sectionHeight = 62.dp

    // Fallback section times if not provided
    val defaultTimes = remember {
        listOf(
            "08:00\n08:45", "08:50\n09:35", "10:00\n10:45", "10:50\n11:35",
            "13:30\n14:15", "14:20\n15:05", "15:25\n16:10", "16:15\n17:00",
            "18:30\n19:15", "19:20\n20:05", "20:15\n21:00", "21:05\n21:50"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Left Section Axis with section numbers and start/end times
        Column(modifier = Modifier.width(40.dp)) {
            for (sec in 1..totalSections) {
                val secTime = sectionTimes.find { it.section == sec }
                val timeLabel = if (secTime != null) "${secTime.startTime}\n${secTime.endTime}" else defaultTimes.getOrElse(sec - 1) { "" }

                Column(
                    modifier = Modifier
                        .height(sectionHeight)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = sec.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = timeLabel,
                        fontSize = 8.5.sp,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }

        // 2. 7-day columns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(sectionHeight * totalSections)
        ) {
            for (dayIdx in 1..7) {
                val dateStr = if (dayIdx - 1 < weekDates.size) weekDates[dayIdx - 1] else ""
                val isToday = (dateStr == todayDateString)

                val dayCourses = occurrences.filter { it.dayOfWeek == dayIdx }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (isToday) {
                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                            } else Modifier
                        )
                        .padding(horizontal = 1.5.dp)
                ) {
                    // Empty grid slot click targets
                    Column(modifier = Modifier.fillMaxSize()) {
                        for (sec in 1..totalSections) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(sectionHeight)
                                    .clickable { onAddAtSlot(dayIdx, sec) }
                            )
                        }
                    }

                    // Render Course Cards
                    for (item in dayCourses) {
                        val isActiveThisWeek = (item.weekNumber == selectedWeek)
                        val topOffset = (item.startSection - 1) * 62
                        val duration = maxOf(1, item.endSection - item.startSection + 1)
                        val cardHeight = duration * 62 - 4

                        CourseCard(
                            item = item,
                            isActiveThisWeek = isActiveThisWeek,
                            modifier = Modifier
                                .padding(top = topOffset.dp)
                                .height(cardHeight.dp)
                                .fillMaxWidth()
                                .clickable { onCourseClick(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * WakeUp aesthetic course card with rounded corners, location, and non-current week dimming.
 */
@Composable
fun CourseCard(
    item: TimelineItem,
    isActiveThisWeek: Boolean,
    modifier: Modifier = Modifier
) {
    val cardColor = parseHexColor(item.color)

    val backgroundAlpha = if (isActiveThisWeek) 0.88f else 0.35f
    val contentAlpha = if (isActiveThisWeek) 1.0f else 0.75f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor.copy(alpha = backgroundAlpha))
            .then(
                if (!isActiveThisWeek) {
                    Modifier.border(
                        1.dp,
                        cardColor.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .padding(4.5.dp)
    ) {
        Column {
            if (!isActiveThisWeek) {
                Text(
                    text = "[非本周]",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            Text(
                text = item.title,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = contentAlpha),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "@${item.location}",
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = contentAlpha * 0.90f),
                    maxLines = 2,
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
    onCourseClick: (TimelineItem) -> Unit,
    onScheduleClick: (CalendarEvent) -> Unit
) {
    val sortedItems = occurrences.sortedBy { it.startSection }

    if (sortedItems.isEmpty() && scheduleEvents.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "今日无课程或日程安排",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                    fontWeight = FontWeight.Bold
                )
            }

            items(sortedItems) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCourseClick(item) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = parseHexColor(item.color).copy(alpha = 0.12f)
                    )
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item.location?.let { loc ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(loc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("第 ${item.startSection}-${item.endSection} 节 (${item.startTime})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            items(scheduleEvents) { evt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onScheduleClick(evt) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = parseHexColor(evt.color).copy(alpha = 0.10f)
                    )
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${evt.startTime} ~ ${evt.endTime}${evt.location?.let { " · $it" } ?: ""}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
