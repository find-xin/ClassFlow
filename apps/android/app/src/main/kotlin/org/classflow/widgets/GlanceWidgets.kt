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
import androidx.glance.appwidget.appWidgetBackground
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
                        isSchedule = occ.isSchedule,
                        occurrenceId = occ.id, week = occ.weekNumber
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
                    .appWidgetBackground()
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
                            isSchedule = occ.isSchedule,
                        occurrenceId = occ.id, week = occ.weekNumber
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
                    .appWidgetBackground()
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
    val isSchedule: Boolean = false,
    val occurrenceId: String? = null,
    val week: Int = 0
)

@Composable
private fun GlanceWakeUpItemRow(
    item: GlanceCardItem,
    titleColor: Color,
    subtextColor: Color,
    isCompact: Boolean = false
) {
    val context = LocalContext.current
    val target = item.occurrenceId?.let { id ->
        Intent(context, MainActivity::class.java)
            .setAction("org.classflow.WIDGET_OCCURRENCE")
            .setData(android.net.Uri.parse("classflow://widget/" + android.net.Uri.encode(id)))
            .putExtra("widget_occurrence_id", id)
            .putExtra("widget_week", item.week)
    }
    Row(
        modifier = if (target == null) GlanceModifier.fillMaxWidth()
            else GlanceModifier.fillMaxWidth().clickable(actionStartActivity(target)),
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
            val containerBg = if (isDark) Color(0xF0181A24) else Color.White
            val headerMonthColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF1F2937)
            val secNumColor = if (isDark) Color(0xFFE5E7EB) else Color(0xFF1F2937)

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
                    .appWidgetBackground()
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

                    val layout = androidx.compose.runtime.remember(widgetData) {
                        WidgetWeekLayout.build(widgetData.copy(sectionTimes = allSectionTimes))
                    }
                    WeekTimelineGrid(context, layout,
                        width = (LocalSize.current.width.value - 16f).coerceAtLeast(44f),
                        height = LocalSize.current.height.value - 60f,
                        axisColor = secNumColor)
                }
            }
        }
    }
}

internal fun widgetOccurrenceIntent(context: Context, occurrence: WidgetOccurrence): Intent =
    Intent(context, MainActivity::class.java)
        .setAction("org.classflow.WIDGET_OCCURRENCE")
        .setData(android.net.Uri.parse("classflow://widget/" + android.net.Uri.encode(occurrence.id)))
        .putExtra("widget_occurrence_id", occurrence.id)
        .putExtra("widget_week", occurrence.weekNumber)

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
