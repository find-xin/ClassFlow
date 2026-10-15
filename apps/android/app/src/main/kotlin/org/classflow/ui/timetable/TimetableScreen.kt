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

import androidx.compose.ui.graphics.Brush
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
    occurrences: List<TimelineItem>,
    scheduleEvents: List<CalendarEvent> = emptyList(),
    sectionTimes: List<SectionTime> = emptyList(),
    cardOpacity: Float = 0.88f,
    isDark: Boolean = false,
    isWallpaperDark: Boolean = true,
    appSettings: AppSettings = AppSettings(),
    onWeekChange: (Int) -> Unit,
    onCourseClick: (TimelineItem) -> Unit,
    onScheduleClick: (CalendarEvent) -> Unit = {},
    onAddCourseClick: () -> Unit,
    onAddScheduleClick: () -> Unit,
    onAddCourseAtSlot: (dayOfWeek: Int, section: Int) -> Unit = { _, _ -> },
    onImportClick: (ImportType) -> Unit = {},
    onExportClick: (ExportType) -> Unit = {},
    onOpenThreeDotsMenu: () -> Unit
) {
    // Calculate dates for the 7 days of the selected week
    val weekDates = remember(selectedWeek) {
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

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. WakeUp Style Top Bar
        WakeUpHeaderView(
            currentWeek = currentWeek,
            selectedWeek = selectedWeek,
            totalWeeks = totalWeeks,
            semesterName = semesterName,
            isDark = isDark,
            isWallpaperDark = isWallpaperDark,
            onWeekChange = onWeekChange,
            onAddCourseClick = onAddCourseClick,
            onAddScheduleClick = onAddScheduleClick,
            onImportClick = onImportClick,
            onExportClick = onExportClick,
            onOpenThreeDotsMenu = onOpenThreeDotsMenu
        )

        HorizontalDivider(color = if (isWallpaperDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE5E7EB))

        // 2. Day Header Row (Month + visible days with actual dates and WakeUp highlight)
        WakeUpDayHeaderRow(
            dateStrings = weekDates,
            todayDateString = todayDateString,
            showSaturday = appSettings.showSaturday,
            showSunday = appSettings.showSunday,
            isWallpaperDark = isWallpaperDark
        )

        HorizontalDivider(color = if (isWallpaperDark) Color.White.copy(alpha = 0.10f) else Color(0xFFE5E7EB))

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
            onCourseClick = onCourseClick,
            onScheduleClick = onScheduleClick,
            onAddAtSlot = onAddCourseAtSlot
        )
    }
}

/**
 * WakeUp style day header row with stacked month indicator and date numbers,
 * matching macOS DayHeaderView 1:1.
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Month Column: Stacked "10" and "月" (width 44dp, exact macOS DayHeaderView style)
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = monthNumber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWallpaperDark) Color.White else Color(0xFF1F2937)
            )
            Text(
                text = "月",
                fontSize = 11.sp,
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
                } else ""
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = day,
                    fontSize = 12.5.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isWallpaperDark) {
                        if (isToday) Color.White else Color.White.copy(alpha = 0.85f)
                    } else {
                        if (isToday) Color(0xFF1F2937) else Color(0xFF6B7280)
                    }
                )

                if (isToday) {
                    // Rounded coral badge with white bold text (exact macOS DayHeaderView: #FF5A5F 24x24dp 7dp corner)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
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
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayNumber,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isWallpaperDark) Color.White.copy(alpha = 0.75f) else Color(0xFF6B7280)
                        )
                    }
                }
            }
        }
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
    onCourseClick: (TimelineItem) -> Unit,
    onScheduleClick: (CalendarEvent) -> Unit,
    onAddAtSlot: (dayOfWeek: Int, section: Int) -> Unit
) {
    val totalSections = 12
    val sectionHeight = appSettings.gridHeight.dp

    // Fallback section times if not provided
    val defaultTimes = remember {
        listOf(
            "08:00\n08:45", "08:50\n09:35", "10:00\n10:45", "10:50\n11:35",
            "13:30\n14:15", "14:20\n15:05", "15:25\n16:10", "16:15\n17:00",
            "18:30\n19:15", "19:20\n20:05", "20:15\n21:00", "21:05\n21:50"
        )
    }

    val visibleDays = remember(appSettings.showSaturday, appSettings.showSunday) {
        buildList {
            for (i in 1..5) add(i)
            if (appSettings.showSaturday) add(6)
            if (appSettings.showSunday) add(7)
        }
    }

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
                val startT = secTime?.startTime ?: defaultTimes.getOrNull(sec - 1)?.substringBefore("\n") ?: ""
                val endT = secTime?.endTime ?: defaultTimes.getOrNull(sec - 1)?.substringAfter("\n") ?: ""

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
                        Text(
                            text = startT,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isWallpaperDark) Color.White.copy(alpha = 0.70f) else Color(0xFF6B7280)
                        )
                        Text(
                            text = endT,
                            fontSize = 8.sp,
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
                val dayCourses = occurrences.filter { it.dayOfWeek == dayIdx }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (isToday) {
                                Modifier.background(if (isWallpaperDark) Color.White.copy(alpha = 0.05f) else Color(0xFF4F46E5).copy(alpha = 0.04f))
                            } else Modifier
                        )
                        .padding(horizontal = 1.5.dp)
                ) {
                    // Empty grid slot click targets & guidelines
                    Column(modifier = Modifier.fillMaxSize()) {
                        for (sec in 1..totalSections) {
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
                                            .height(0.5.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                if (isWallpaperDark) Color.White.copy(alpha = 0.07f)
                                                else Color.Black.copy(alpha = 0.05f)
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Render Course Cards
                    for (item in dayCourses) {
                        val isActiveThisWeek = (item.weekNumber == selectedWeek)
                        val topOffset = (item.startSection - 1) * appSettings.gridHeight
                        val duration = maxOf(1, item.endSection - item.startSection + 1)
                        val cardHeight = duration * appSettings.gridHeight - 4

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
                    }
                }
            }
        }
    }
}

/**
 * WakeUp & macOS parity course card with rounded corners, glass reflection, dynamic contrast text,
 * and deep customizable options matching macOS CourseCardView.
 */
@Composable
fun CourseCard(
    item: TimelineItem,
    isActiveThisWeek: Boolean,
    appSettings: AppSettings = AppSettings(),
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardColor = parseHexColor(item.color)
    val baseOpacity = if (isActiveThisWeek) appSettings.cardOpacity else appSettings.cardOpacity * appSettings.nonCurrentWeekOpacityRatio

    val radius = appSettings.cardCornerRadius.dp
    val fontSize = appSettings.cardFontSize.sp

    val isLight = cardColor.isLight()
    val effectiveTextColor = when {
        appSettings.cardTextBlendCardColor -> {
            if (isLight && !isDark) cardColor.deepShade() else cardColor
        }
        appSettings.cardTextColorMode == "light" -> Color.White
        appSettings.cardTextColorMode == "dark" -> Color.Black.copy(alpha = 0.85f)
        else -> {
            // Auto contrast mode
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

    val effectiveSecondaryTextColor = effectiveTextColor.copy(alpha = 0.82f)

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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(cardColor.copy(alpha = if (isDark) minOf(baseOpacity, 0.82f) else baseOpacity))
            .border(
                width = if (appSettings.cardUseDottedBorder) 1.dp else 0.5.dp,
                color = borderStrokeColor,
                shape = RoundedCornerShape(radius)
            ),
        contentAlignment = if (appSettings.cardTextVerticalCenter) Alignment.Center else Alignment.TopStart
    ) {
        // Glass top reflection gradient for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                        } else {
                            listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)
                        }
                    )
                )
        )

        // Card Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = if (appSettings.cardTextHorizontalCenter) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = if (appSettings.cardTextVerticalCenter) Arrangement.Center else Arrangement.Top
        ) {
            // Header Row: Title & Status Pills
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = if (appSettings.cardTextHorizontalCenter) Arrangement.Center else Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.title,
                    fontSize = fontSize,
                    lineHeight = (fontSize.value + 2.5f).sp,
                    fontWeight = FontWeight.Bold,
                    color = effectiveTextColor,
                    textAlign = if (appSettings.cardTextHorizontalCenter) TextAlign.Center else TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (!appSettings.cardTextHorizontalCenter) Modifier.weight(1f, fill = false) else Modifier
                )

                if (!isActiveThisWeek) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "非本周",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Location
            if (appSettings.showLocationInCard && !item.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                val locText = if (appSettings.showAtPrefixForRoom) "@${item.location}" else item.location ?: ""
                Text(
                    text = locText,
                    fontSize = maxOf(8.5f, fontSize.value - 2.5f).sp,
                    lineHeight = maxOf(10.5f, fontSize.value - 0.5f).sp,
                    fontWeight = FontWeight.Medium,
                    color = effectiveSecondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Teacher
            if (appSettings.showTeacherInCard && !item.teacher.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.teacher ?: "",
                    fontSize = maxOf(8.0f, fontSize.value - 3.5f).sp,
                    lineHeight = maxOf(10.0f, fontSize.value - 1.5f).sp,
                    fontWeight = FontWeight.Normal,
                    color = effectiveSecondaryTextColor.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Time
            if (appSettings.showTimeInCard && !item.startTime.isNullOrBlank() && !item.endTime.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.startTime}-${item.endTime}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = effectiveSecondaryTextColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Notes
            if (appSettings.showNotesInCard && !item.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.notes ?: "",
                    fontSize = 8.sp,
                    color = effectiveSecondaryTextColor.copy(alpha = 0.75f),
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
