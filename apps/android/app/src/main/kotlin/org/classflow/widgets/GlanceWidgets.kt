package org.classflow.widgets

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.classflow.R
import org.classflow.data.local.SettingsManager
import org.classflow.data.local.WidgetDataManager
import org.classflow.data.local.WidgetOccurrence
import org.classflow.ui.MainActivity
import java.util.Calendar

fun extractTime(timeStr: String?): String {
    if (timeStr.isNullOrBlank()) return ""
    val trimmed = timeStr.trim()
    return when {
        trimmed.contains("T") -> trimmed.substringAfter("T").take(5)
        trimmed.contains(" ") -> trimmed.substringAfter(" ").take(5)
        trimmed.length >= 5 -> trimmed.take(5)
        else -> trimmed
    }
}

/**
 * 2x2 Glance AppWidget matching WakeUp's Today Course & Schedule Widget (Image 1, left):
 * - Dynamic Day/Night mode: follows ClassFlow timetable theme mode & Android system mode
 * - Top Header: Semester name on left, Date & Weekday on right
 * - Vertical item rows: Left thin theme-colored stripe, bold title, location, and time
 * - Shows actual real courses and calendar schedule events
 * - Filters out past/expired events based on real-time clock
 * - Click directly opens app timetable view!
 */
class NextClassGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsManager.loadSettings(context)
        val widgetData = WidgetDataManager.loadWidgetData(context)
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (settings.themeMode) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }

        provideContent {
            val cardBg = if (isDark) Color(0xF0181A24) else Color(0xF8FFFFFF)
            val semesterColor = if (isDark) Color(0xFFD1D5DB) else Color(0xFF4B5563)
            val dateColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
            val weekdayColor = if (isDark) Color(0xFF818CF8) else Color(0xFF2563EB)
            val titleColor = if (isDark) Color.White else Color(0xFF111827)
            val subtextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

            val cal = Calendar.getInstance()
            val todayWeekday = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
            val todayDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val todayDateStr = todayDateFormat.format(cal.time)
            val month = cal.get(Calendar.MONTH) + 1
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val weekdayName = when (todayWeekday) {
                1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; else -> "周日"
            }

            val curHour = cal.get(Calendar.HOUR_OF_DAY)
            val curMin = cal.get(Calendar.MINUTE)
            val currentTimeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", curHour, curMin)

            val hasDatesInCache = widgetData.items.any { it.date.isNotBlank() }
            val rawTodayItems = if (hasDatesInCache) {
                widgetData.items.filter { it.date == todayDateStr && it.isCurrentWeek }.sortedBy { it.startSection }
            } else {
                val byWeek = widgetData.items.filter { (it.weekNumber == widgetData.currentWeek || it.weekNumber == 0) && it.dayOfWeek == todayWeekday && it.isCurrentWeek }
                if (byWeek.isNotEmpty()) byWeek.sortedBy { it.startSection }
                else widgetData.items.filter { it.dayOfWeek == todayWeekday && it.isCurrentWeek }.sortedBy { it.startSection }
            }

            // Filter out past/expired events (where endTime <= currentTimeStr)
            val upcomingTodayItems = rawTodayItems.filter { occ ->
                val endT = extractTime(occ.endTime)
                if (endT.isNotBlank()) {
                    endT > currentTimeStr
                } else {
                    val startT = extractTime(occ.startTime)
                    if (startT.isNotBlank()) startT >= currentTimeStr
                    else true
                }
            }

            val todayItems = if (upcomingTodayItems.isNotEmpty()) {
                upcomingTodayItems.map { occ ->
                    val locationText = listOfNotNull(occ.teacher, occ.classroom).filter { it.isNotBlank() }.joinToString(" ")
                    val fallbackLocation = if (occ.isSchedule && locationText.isBlank()) "日程安排" else locationText
                    GlanceCardItem(
                        title = occ.title,
                        location = fallbackLocation,
                        time = if (occ.startTime.isNotBlank()) "${occ.startTime} - ${occ.endTime}" else "第${occ.startSection}-${occ.endSection}节",
                        accentColor = try {
                            Color(android.graphics.Color.parseColor(occ.colorHex))
                        } catch (_: Throwable) {
                            Color(0xFF4F46E5)
                        },
                        isSchedule = occ.isSchedule
                    )
                }
            } else if (rawTodayItems.isNotEmpty()) {
                listOf(
                    GlanceCardItem(
                        title = "今日课程与日程已结束",
                        location = "早点休息，明天加油",
                        time = "全天已完成",
                        accentColor = Color(0xFF10B981),
                        isSchedule = false
                    )
                )
            } else {
                listOf(
                    GlanceCardItem(
                        title = "今日暂无课程与日程",
                        location = "享受属于你的自由时光",
                        time = "全天自由",
                        accentColor = Color(0xFF10B981),
                        isSchedule = false
                    )
                )
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(20.dp)
                    .background(cardBg)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header Row: [ 学期名                   10.17 周六 ]
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = widgetData.semesterName.ifEmpty { "ClassFlow" },
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(semesterColor)
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$month.$dayOfMonth ",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(dateColor)
                                )
                            )
                            Text(
                                text = weekdayName,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(weekdayColor)
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(10.dp))

                    // Items List: Left vertical color strip + Title, Location, Time
                    todayItems.take(2).forEachIndexed { index, item ->
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.height(8.dp))
                        }
                        GlanceWakeUpItemRow(
                            item = item,
                            titleColor = titleColor,
                            subtextColor = subtextColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4x2 / 4x3 / 4x4 Glance AppWidget matching WakeUp's Recent Course & Schedule Widget (Image 1, center-left):
 * - Dynamic Day/Night mode
 * - Header Row: Semester on left, Date & Week number & Weekday on right
 * - Two Columns: "今天" (Today) on left, "明天" (Tomorrow) on right
 * - Blends real courses and calendar schedule events with left vertical color stripes
 * - Click directly opens app timetable view!
 */
class TodayClassesGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsManager.loadSettings(context)
        val widgetData = WidgetDataManager.loadWidgetData(context)
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (settings.themeMode) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }

        provideContent {
            val cardBg = if (isDark) Color(0xF0181A24) else Color(0xF8FFFFFF)
            val semesterColor = if (isDark) Color(0xFFD1D5DB) else Color(0xFF4B5563)
            val dateColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
            val weekdayColor = if (isDark) Color(0xFF818CF8) else Color(0xFF2563EB)
            val columnHeaderColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
            val titleColor = if (isDark) Color.White else Color(0xFF111827)
            val subtextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

            val cal = Calendar.getInstance()
            val todayWeekday = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
            val todayDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val todayDateStr = todayDateFormat.format(cal.time)

            val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val tomorrowWeekday = when (tomorrowCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
            val tomorrowDateStr = todayDateFormat.format(tomorrowCal.time)
            val month = cal.get(Calendar.MONTH) + 1
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val weekdayName = when (todayWeekday) {
                1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; else -> "周日"
            }
            val curHour = cal.get(Calendar.HOUR_OF_DAY)
            val curMin = cal.get(Calendar.MINUTE)
            val currentTimeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", curHour, curMin)

            fun findItemsFor(dateStr: String, weekday: Int, isTomorrow: Boolean): List<WidgetOccurrence> {
                // 1. Precise calendar date match (both courses and schedules that run on this date)
                val byDate = widgetData.items.filter { it.date == dateStr && it.isCurrentWeek }
                if (byDate.isNotEmpty()) return byDate

                // If cache contains date metadata, do not fall back to other days' items!
                val hasDatesInCache = widgetData.items.any { it.date.isNotBlank() }
                if (hasDatesInCache) return emptyList()

                // 2. Week number + Day of week match (legacy cache without dates)
                val targetWeek = if (isTomorrow && weekday == 1) widgetData.currentWeek + 1 else widgetData.currentWeek
                val byWeek = widgetData.items.filter { (it.weekNumber == targetWeek || it.weekNumber == 0) && it.dayOfWeek == weekday && it.isCurrentWeek }
                if (byWeek.isNotEmpty()) return byWeek

                return emptyList()
            }

            fun mapToGlanceItems(raw: List<WidgetOccurrence>, emptyTitle: String): List<GlanceCardItem> {
                return if (raw.isNotEmpty()) {
                    raw.sortedBy { it.startSection }.map { occ ->
                        val locationText = listOfNotNull(occ.teacher, occ.classroom).filter { it.isNotBlank() }.joinToString(" ")
                        val fallbackLocation = if (occ.isSchedule && locationText.isBlank()) "日程安排" else locationText
                        GlanceCardItem(
                            title = occ.title,
                            location = fallbackLocation,
                            time = if (occ.startTime.isNotBlank()) "${occ.startTime} - ${occ.endTime}" else "第${occ.startSection}-${occ.endSection}节",
                            accentColor = try {
                                Color(android.graphics.Color.parseColor(occ.colorHex))
                            } catch (_: Throwable) {
                                Color(0xFF4F46E5)
                            },
                            isSchedule = occ.isSchedule
                        )
                    }
                } else {
                    listOf(
                        GlanceCardItem(
                            title = emptyTitle,
                            location = "无安排",
                            time = "全天",
                            accentColor = Color(0xFF10B981),
                            isSchedule = false
                        )
                    )
                }
            }

            val rawToday = findItemsFor(todayDateStr, todayWeekday, false)
            val upcomingToday = rawToday.filter { occ ->
                val endT = extractTime(occ.endTime)
                if (endT.isNotBlank()) endT > currentTimeStr
                else {
                    val startT = extractTime(occ.startTime)
                    if (startT.isNotBlank()) startT >= currentTimeStr
                    else true
                }
            }

            val todayItems = if (upcomingToday.isNotEmpty()) {
                mapToGlanceItems(upcomingToday, "今日无课")
            } else if (rawToday.isNotEmpty()) {
                listOf(
                    GlanceCardItem(
                        title = "今日安排已结束",
                        location = "全部课程与日程完成",
                        time = "已结束",
                        accentColor = Color(0xFF10B981),
                        isSchedule = false
                    )
                )
            } else {
                mapToGlanceItems(emptyList(), "今日无课")
            }

            val tomorrowItems = mapToGlanceItems(findItemsFor(tomorrowDateStr, tomorrowWeekday, true), "明日无课")

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(20.dp)
                    .background(cardBg)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                    .padding(14.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header Row: [ 学期名                   10.17 第1周 周六 ]
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = widgetData.semesterName.ifEmpty { "ClassFlow" },
                            style = TextStyle(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(semesterColor)
                            )
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$month.$dayOfMonth 第${widgetData.currentWeek}周 ",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(dateColor)
                                )
                            )
                            Text(
                                text = weekdayName,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(weekdayColor)
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Two Columns: 今天 on left, 明天 on right
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    ) {
                        // Left Column: 今天
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            Text(
                                text = "今天",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(columnHeaderColor)
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(5.dp))
                            todayItems.take(2).forEachIndexed { idx, item ->
                                if (idx > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                                GlanceWakeUpItemRow(
                                    item = item,
                                    titleColor = titleColor,
                                    subtextColor = subtextColor,
                                    isCompact = true
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(12.dp))

                        // Right Column: 明天
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            Text(
                                text = "明天",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(columnHeaderColor)
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(5.dp))
                            tomorrowItems.take(2).forEachIndexed { idx, item ->
                                if (idx > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                                GlanceWakeUpItemRow(
                                    item = item,
                                    titleColor = titleColor,
                                    subtextColor = subtextColor,
                                    isCompact = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class GlanceCardItem(
    val title: String,
    val location: String,
    val time: String,
    val accentColor: Color,
    val isSchedule: Boolean = false
)

@Composable
private fun GlanceWakeUpItemRow(
    item: GlanceCardItem,
    titleColor: Color,
    subtextColor: Color,
    isCompact: Boolean = false
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical Theme Color Stripe matching WakeUp
        Box(
            modifier = GlanceModifier
                .width(3.5.dp)
                .height(if (isCompact) 36.dp else 42.dp)
                .cornerRadius(2.dp)
                .background(item.accentColor)
        ) {}

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.title,
                style = TextStyle(
                    fontSize = if (isCompact) 12.5.sp else 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(titleColor)
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(1.dp))
            Text(
                text = item.location,
                style = TextStyle(
                    fontSize = if (isCompact) 9.5.sp else 10.5.sp,
                    color = ColorProvider(subtextColor)
                ),
                maxLines = 1
            )
            Text(
                text = item.time,
                style = TextStyle(
                    fontSize = if (isCompact) 9.sp else 10.sp,
                    color = ColorProvider(subtextColor)
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * 4x2 / 4x3 / 4x4 / Stretched Full Week Timetable Widget (WakeUp 课程视图风格):
 * - Fully data-driven by actual ClassFlow courses and timetable settings
 * - Dynamic Day/Night palette following ClassFlow theme mode & Android system mode
 * - Dynamically computes real current week calendar dates for 7 columns (Mon-Sun)
 * - Highlights today's weekday column
 * - No fake courses, no hardcoded sample artifacts
 * - Non-current week courses appear faded/translucent without extra tags
 */
class WeekTimetableGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsManager.loadSettings(context)
        val widgetData = WidgetDataManager.loadWidgetData(context)
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (settings.themeMode) {
            "dark" -> true
            "light" -> false
            else -> systemDark
        }

        provideContent {
            val size = LocalSize.current
            val heightDp = size.height.value

            // Adaptive visible section count based on widget stretched height
            val visibleSections = when {
                heightDp < 115f -> 4
                heightDp < 145f -> 6
                heightDp < 175f -> 8
                heightDp < 205f -> 10
                else -> 12
            }

            val containerBg = if (isDark) Color(0xF0181A24) else Color.White
            val headerMonthColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF1F2937)
            val secNumColor = if (isDark) Color(0xFFE5E7EB) else Color(0xFF1F2937)
            val secTimeColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

            // Section time definitions from saved sectionTimes or standard fallbacks
            val allSectionTimes = if (widgetData.sectionTimes.isNotEmpty()) {
                widgetData.sectionTimes
            } else {
                listOf(
                    "1" to "08:30\n09:15",
                    "2" to "09:20\n10:05",
                    "3" to "10:15\n11:00",
                    "4" to "11:05\n11:50",
                    "5" to "14:30\n15:15",
                    "6" to "15:20\n16:05",
                    "7" to "16:20\n17:05",
                    "8" to "17:10\n17:55",
                    "9" to "19:00\n19:45",
                    "10" to "19:55\n20:40",
                    "11" to "20:45\n21:30",
                    "12" to "21:35\n22:20"
                )
            }
            val activeSectionTimes = allSectionTimes.take(visibleSections)

            val secNumFontSize = if (visibleSections > 8) 11.sp else 13.sp
            val secTimeFontSize = if (visibleSections > 8) 6.5.sp else 7.sp

            // Dynamically calculate current week's Monday-Sunday dates
            val cal = Calendar.getInstance()
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val monthStr = (cal.get(Calendar.MONTH) + 1).toString()
            val dayDates = (0..6).map {
                val d = cal.get(Calendar.DAY_OF_MONTH).toString()
                cal.add(Calendar.DAY_OF_MONTH, 1)
                d
            }

            val nowCal = Calendar.getInstance()
            val todayIdx = when (nowCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(containerBg)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                    .padding(8.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // 1. Top Header Row: "M月" on left + 7 day columns
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().height(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Month Column
                        Column(
                            modifier = GlanceModifier.width(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monthStr,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(headerMonthColor)
                                )
                            )
                            Text(
                                text = "月",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(headerMonthColor)
                                )
                            )
                        }

                        // 7 Days: 一, 二, 三, 四, 五, 六, 日
                        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")

                        dayNames.forEachIndexed { idx, name ->
                            val isToday = (idx == todayIdx)
                            val textColor = if (isToday) {
                                if (isDark) Color.White else Color(0xFF111827)
                            } else {
                                if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                            }
                            val fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium

                            Column(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = fontWeight,
                                        color = ColorProvider(textColor)
                                    )
                                )
                                Text(
                                    text = dayDates[idx],
                                    style = TextStyle(
                                        fontSize = 11.5.sp,
                                        fontWeight = fontWeight,
                                        color = ColorProvider(textColor)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    // 2. Timetable Grid: Left section axis + 7 Columns
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    ) {
                        // Left Section Axis
                        Column(
                            modifier = GlanceModifier.width(36.dp).fillMaxHeight()
                        ) {
                            activeSectionTimes.forEach { (secNum, times) ->
                                Column(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .defaultWeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = secNum,
                                        style = TextStyle(
                                            fontSize = secNumFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(secNumColor)
                                        )
                                    )
                                    Text(
                                        text = times,
                                        style = TextStyle(
                                            fontSize = secTimeFontSize,
                                            color = ColorProvider(secTimeColor)
                                        )
                                    )
                                }
                            }
                        }

                        // 7 Columns matching actual ClassFlow courses
                        for (day in 1..7) {
                            val dayCourses = widgetData.items
                                .filter { (it.weekNumber == widgetData.currentWeek || it.weekNumber == 0) && it.dayOfWeek == day }
                                .sortedBy { it.startSection }
                            GlanceDayColumn(
                                visibleSections = visibleSections,
                                dayItems = dayCourses,
                                isDark = isDark,
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceDayColumn(
    visibleSections: Int,
    dayItems: List<WidgetOccurrence>,
    isDark: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier) {
        var currentSec = 1
        while (currentSec <= visibleSections) {
            val occ = dayItems.firstOrNull { it.startSection == currentSec }
            if (occ == null) {
                Spacer(modifier = GlanceModifier.defaultWeight())
                currentSec++
            } else {
                val start = occ.startSection
                val end = minOf(occ.endSection, visibleSections)
                val span = maxOf(1, end - start + 1)
                val theme = resolveThemeForColor(occ.colorHex)
                val isNonCurrent = !occ.isCurrentWeek

                if (span == 1) {
                    GlancePastelCard(
                        title = occ.title,
                        teacher = occ.teacher ?: "",
                        classroom = occ.classroom ?: "",
                        isNonCurrentWeek = isNonCurrent,
                        colorTheme = theme,
                        isDark = isDark,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    currentSec++
                } else if (span == 2) {
                    GlancePastelCard(
                        title = occ.title,
                        colorTheme = theme,
                        isNonCurrentWeek = isNonCurrent,
                        isDark = isDark,
                        isTopHalf = true,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    GlancePastelCard(
                        teacher = occ.teacher ?: "",
                        classroom = occ.classroom ?: "",
                        colorTheme = theme,
                        isNonCurrentWeek = isNonCurrent,
                        isDark = isDark,
                        isBottomHalf = true,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    currentSec += 2
                } else {
                    GlancePastelCard(
                        title = occ.title,
                        colorTheme = theme,
                        isNonCurrentWeek = isNonCurrent,
                        isDark = isDark,
                        isTopHalf = true,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    for (m in 1 until span - 1) {
                        GlancePastelCard(
                            colorTheme = theme,
                            isNonCurrentWeek = isNonCurrent,
                            isDark = isDark,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    GlancePastelCard(
                        teacher = occ.teacher ?: "",
                        classroom = occ.classroom ?: "",
                        colorTheme = theme,
                        isNonCurrentWeek = isNonCurrent,
                        isDark = isDark,
                        isBottomHalf = true,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    currentSec += span
                }
            }
        }
    }
}

private fun resolveThemeForColor(colorHex: String): String {
    val clean = colorHex.removePrefix("#").trim()
    val rgb = clean.toLongOrNull(16) ?: return "sky"
    val r = (rgb shr 16 and 0xFF).toInt()
    val g = (rgb shr 8 and 0xFF).toInt()
    val b = (rgb and 0xFF).toInt()
    return when {
        g > r && g > b -> "mint"
        r > g && r > b && g > b -> "peach"
        r > g && b > g -> "lavender"
        r > 180 && g < 100 && b < 100 -> "peach"
        b > r && b > g -> "sky"
        else -> "sky"
    }
}

/**
 * Resolve exact XML Drawable resource for course card:
 * - Top card uses bg_widget_card_*_top (top corners rounded 8dp, bottom corners sharp 0dp)
 * - Bottom card uses bg_widget_card_*_bottom (top corners sharp 0dp, bottom corners rounded 8dp)
 * - Single card uses bg_widget_card_*_single (all 4 corners rounded 8dp)
 * - Zero waist ("葫芦状") indentation because left/right edges are 100% straight vertical lines!
 */
private fun resolveDrawableRes(
    colorTheme: String,
    isNonCurrentWeek: Boolean,
    isDark: Boolean,
    isTopHalf: Boolean,
    isBottomHalf: Boolean
): Int {
    val theme = when (colorTheme.lowercase()) {
        "mint" -> "mint"
        "peach" -> "peach"
        "lavender" -> "lavender"
        else -> "sky"
    }
    return when {
        isTopHalf -> {
            when {
                isNonCurrentWeek && isDark -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_ghost_top_night
                    "peach" -> R.drawable.bg_widget_card_peach_ghost_top_night
                    "lavender" -> R.drawable.bg_widget_card_lavender_ghost_top_night
                    else -> R.drawable.bg_widget_card_sky_ghost_top_night
                }
                isNonCurrentWeek && !isDark -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_ghost_top
                    "peach" -> R.drawable.bg_widget_card_peach_ghost_top
                    "lavender" -> R.drawable.bg_widget_card_lavender_ghost_top
                    else -> R.drawable.bg_widget_card_sky_ghost_top
                }
                !isNonCurrentWeek && isDark -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_top_night
                    "peach" -> R.drawable.bg_widget_card_peach_top_night
                    "lavender" -> R.drawable.bg_widget_card_lavender_top_night
                    else -> R.drawable.bg_widget_card_sky_top_night
                }
                else -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_top
                    "peach" -> R.drawable.bg_widget_card_peach_top
                    "lavender" -> R.drawable.bg_widget_card_lavender_top
                    else -> R.drawable.bg_widget_card_sky_top
                }
            }
        }
        isBottomHalf -> {
            when {
                isNonCurrentWeek && isDark -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_ghost_bottom_night
                    "peach" -> R.drawable.bg_widget_card_peach_ghost_bottom_night
                    "lavender" -> R.drawable.bg_widget_card_lavender_ghost_bottom_night
                    else -> R.drawable.bg_widget_card_sky_ghost_bottom_night
                }
                isNonCurrentWeek && !isDark -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_ghost_bottom
                    "peach" -> R.drawable.bg_widget_card_peach_ghost_bottom
                    "lavender" -> R.drawable.bg_widget_card_lavender_ghost_bottom
                    else -> R.drawable.bg_widget_card_sky_ghost_bottom
                }
                !isNonCurrentWeek && isDark -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_bottom_night
                    "peach" -> R.drawable.bg_widget_card_peach_bottom_night
                    "lavender" -> R.drawable.bg_widget_card_lavender_bottom_night
                    else -> R.drawable.bg_widget_card_sky_bottom_night
                }
                else -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_bottom
                    "peach" -> R.drawable.bg_widget_card_peach_bottom
                    "lavender" -> R.drawable.bg_widget_card_lavender_bottom
                    else -> R.drawable.bg_widget_card_sky_bottom
                }
            }
        }
        else -> {
            when {
                isNonCurrentWeek && isDark -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_ghost_single_night
                    "peach" -> R.drawable.bg_widget_card_peach_ghost_single_night
                    "lavender" -> R.drawable.bg_widget_card_lavender_ghost_single_night
                    else -> R.drawable.bg_widget_card_sky_ghost_single_night
                }
                isNonCurrentWeek && !isDark -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_ghost_single
                    "peach" -> R.drawable.bg_widget_card_peach_ghost_single
                    "lavender" -> R.drawable.bg_widget_card_lavender_ghost_single
                    else -> R.drawable.bg_widget_card_sky_ghost_single
                }
                !isNonCurrentWeek && isDark -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_single_night
                    "peach" -> R.drawable.bg_widget_card_peach_single_night
                    "lavender" -> R.drawable.bg_widget_card_lavender_single_night
                    else -> R.drawable.bg_widget_card_sky_single_night
                }
                else -> when (theme) {
                    "mint" -> R.drawable.bg_widget_card_mint_single
                    "peach" -> R.drawable.bg_widget_card_peach_single
                    "lavender" -> R.drawable.bg_widget_card_lavender_single
                    else -> R.drawable.bg_widget_card_sky_single
                }
            }
        }
    }
}

/**
 * Modern course card matching the app's timetable course view style:
 * - High-contrast solid palette in Dark mode (dark saturated cards with crisp pure white title)
 * - Soft pastel palette in Light mode (pale pastel cards with deep dark title)
 * - Seamless top and bottom halves using precision XML drawables without cornerRadius clipping
 * - Clean vertical stack: Top half dedicated to Title (up to 3 lines, never truncated);
 *   Bottom half dedicated to Teacher and Classroom (up to 2 lines, filling blank space)
 * - Faded/translucent "虚的" appearance for non-current-week courses without ugly "[非本周]" pills
 */
@Composable
private fun GlancePastelCard(
    title: String = "",
    teacher: String = "",
    classroom: String = "",
    isNonCurrentWeek: Boolean = false,
    colorTheme: String = "mint", // "mint", "peach", "lavender", "sky"
    accentColor: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    isDark: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
    isTopHalf: Boolean = false,
    isBottomHalf: Boolean = false
) {
    val (resolvedTitle, resolvedSub) = if (isDark) {
        when (colorTheme) {
            "mint" -> Pair(Color(0xFFFFFFFF), Color(0xFFA7F3D0))
            "peach" -> Pair(Color(0xFFFFFFFF), Color(0xFFFED7AA))
            "lavender" -> Pair(Color(0xFFFFFFFF), Color(0xFFC7D2FE))
            else -> Pair(Color(0xFFFFFFFF), Color(0xFFBAE6FD))
        }
    } else {
        when (colorTheme) {
            "mint" -> Pair(Color(0xFF14532D), Color(0xFF166534))
            "peach" -> Pair(Color(0xFF7C2D12), Color(0xFF9A3412))
            "lavender" -> Pair(Color(0xFF312E81), Color(0xFF4338CA))
            else -> Pair(Color(0xFF0C4A6E), Color(0xFF0369A1))
        }
    }

    val actualTitleColor = if (isNonCurrentWeek) resolvedTitle.copy(alpha = 0.45f) else resolvedTitle
    val actualSubColor = if (isNonCurrentWeek) resolvedSub.copy(alpha = 0.45f) else resolvedSub

    val bgDrawableRes = resolveDrawableRes(
        colorTheme = colorTheme,
        isNonCurrentWeek = isNonCurrentWeek,
        isDark = isDark,
        isTopHalf = isTopHalf,
        isBottomHalf = isBottomHalf
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 1.dp,
                end = 1.dp,
                top = if (isBottomHalf) 0.dp else 1.dp,
                bottom = if (isTopHalf) 0.dp else 1.dp
            )
            .background(ImageProvider(bgDrawableRes))
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    start = 2.dp,
                    end = 2.dp,
                    top = if (isTopHalf) 2.dp else 1.dp,
                    bottom = if (isBottomHalf) 2.dp else 1.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(actualTitleColor)
                    ),
                    maxLines = 3
                )
            }
            if (teacher.isNotEmpty()) {
                Text(
                    text = teacher,
                    style = TextStyle(
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(actualSubColor)
                    ),
                    maxLines = 1
                )
            }
            if (classroom.isNotEmpty()) {
                Text(
                    text = classroom,
                    style = TextStyle(
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(actualSubColor)
                    ),
                    maxLines = 2
                )
            }
        }
    }
}

// Glance Receivers
class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassGlanceWidget()
}

class TodayClassesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayClassesGlanceWidget()
}

class WeekTimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekTimetableGlanceWidget()
}
