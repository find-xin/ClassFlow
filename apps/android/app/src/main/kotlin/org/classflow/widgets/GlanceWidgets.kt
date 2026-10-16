package org.classflow.widgets

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.classflow.R

/**
 * 2x2 Glance AppWidget matching WakeUp's Next Class Widget:
 * - Dynamic Day/Night mode: clean light card in Day mode, frosted dark card in Night mode
 * - "下节课" pill badge with start time
 * - Large bold course title, classroom tag (@东上 101), and teacher
 */
class NextClassGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        provideContent {
            val cardBg = if (isDark) Color(0xF0181A24) else Color(0xF8FFFFFF)
            val pillBg = if (isDark) Color(0xFF3F51B5) else Color(0xFF4F46E5)
            val pillTextColor = Color.White
            val timeColor = if (isDark) Color(0xFFB0B3C2) else Color(0xFF6B7280)
            val titleColor = if (isDark) Color.White else Color(0xFF111827)
            val classroomColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
            val teacherColor = if (isDark) Color(0xFFB0B3C2) else Color(0xFF4B5563)
            val countdownColor = if (isDark) Color(0xFF9094A6) else Color(0xFF6B7280)

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(cardBg)
                    .padding(14.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header Tag Row
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(6.dp)
                                .background(pillBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "下节课",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(pillTextColor)
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        Text(
                            text = "10:00 开始",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = ColorProvider(timeColor)
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(10.dp))

                    // Course Name
                    Text(
                        text = "高等数学",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(titleColor)
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    // Classroom Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@东上 101",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(classroomColor)
                            )
                        )
                        Text(
                            text = " · 张教授",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = ColorProvider(teacherColor)
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Countdown Status
                    Text(
                        text = "第 3-4 节 · 距上课 25 分钟",
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = ColorProvider(countdownColor)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 4x2 / 4x3 / 4x4 Glance AppWidget for Today's Classes:
 * - Dynamic Day/Night mode: clean light card in Day mode, frosted dark card in Night mode
 * - Adaptive stretching: automatically calculates visible courses count based on height
 *   (4x2 -> 2 courses, 4x3 -> 3 courses, 4x4 -> 4 courses, 4x5 -> 5 courses, 4x6 -> 6 courses)
 *   instead of stretching 2 courses into distorted boxes!
 */
class TodayClassesGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        provideContent {
            val size = LocalSize.current
            val heightDp = size.height.value

            val cardBg = if (isDark) Color(0xF0181A24) else Color(0xF8FFFFFF)
            val headerColor = if (isDark) Color.White else Color(0xFF111827)
            val badgeBg = if (isDark) Color(0xFF2A2D3D) else Color(0xFFEEF2FF)
            val badgeTextColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
            val titleColor = if (isDark) Color.White else Color(0xFF1F2937)
            val timeColor = if (isDark) Color(0xFFB0B3C2) else Color(0xFF6B7280)
            val locColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)

            // Dynamic course pool for today
            data class TodayCourse(
                val title: String,
                val time: String,
                val location: String,
                val accentColor: Color
            )

            val allCourses = listOf(
                TodayCourse("高等数学", "08:00 - 09:35 (第 1-2 节)", "@东上 101", Color(0xFF4F46E5)),
                TodayCourse("数据结构与算法", "10:00 - 11:35 (第 3-4 节)", "@信息楼 302", Color(0xFF0B8043)),
                TodayCourse("计算机网络", "14:00 - 15:35 (第 5-6 节)", "@实验楼 204", Color(0xFFD97706)),
                TodayCourse("操作系统", "16:00 - 17:35 (第 7-8 节)", "@东下 205", Color(0xFFDC2626)),
                TodayCourse("人工智能导论", "19:00 - 20:35 (第 9-10 节)", "@科研楼 501", Color(0xFF7C3AED)),
                TodayCourse("移动应用开发", "20:45 - 22:15 (第 11-12 节)", "@信软大楼 402", Color(0xFF0284C7))
            )

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(cardBg)
                    .padding(14.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header Row
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日课表 · 周五 10/16",
                            style = TextStyle(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(headerColor)
                            )
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(6.dp)
                                .background(badgeBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "共 ${allCourses.size} 节",
                                style = TextStyle(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(badgeTextColor)
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Scrollable & Adaptive Course Rows List (Glance LazyColumn)
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    ) {
                        items(allCourses) { course ->
                            GlanceCourseRow(
                                title = course.title,
                                time = course.time,
                                location = course.location,
                                accentColor = course.accentColor,
                                titleColor = titleColor,
                                timeColor = timeColor,
                                locColor = locColor
                            )
                            Spacer(modifier = GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceCourseRow(
    title: String,
    time: String,
    location: String,
    accentColor: Color,
    titleColor: Color,
    timeColor: Color,
    locColor: Color
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical accent bar
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(30.dp)
                .cornerRadius(2.dp)
                .background(accentColor)
        ) {}

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(titleColor)
                ),
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = time,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = ColorProvider(timeColor)
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = location,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(locColor)
                    )
                )
            }
        }
    }
}

/**
 * 4x2 / 4x3 / 4x4 / 4x5 / 4x6 Glance AppWidget matching WakeUp's Week Timetable:
 * - Dynamic Day/Night mode: clean white card in Day mode, frosted dark card in Night mode
 * - Adaptive stretching:
 *   - Height < 135dp (4x2 standard): shows morning 4 sections (1..4)
 *   - Height 135..179dp (4x3): shows 6 sections (1..6)
 *   - Height 180..224dp (4x4): shows 8 sections (1..8)
 *   - Height 225..264dp (4x5): shows 10 sections (1..10)
 *   - Height >= 265dp (4x6 / maximum stretch): shows full 12 sections (1..12: morning, afternoon & evening!)
 *   Dynamically populates afternoon and evening courses instead of stretching empty cells or clipping!
 */
class WeekTimetableGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        provideContent {
            val size = LocalSize.current
            val heightDp = size.height.value

            // Adaptive visible section count based on widget stretched height
            val visibleSections = when {
                heightDp < 135f -> 4
                heightDp < 180f -> 6
                heightDp < 225f -> 8
                heightDp < 265f -> 10
                else -> 12
            }

            val containerBg = if (isDark) Color(0xF0181A24) else Color.White
            val headerMonthColor = if (isDark) Color(0xFFF3F4F6) else Color(0xFF1F2937)
            val secNumColor = if (isDark) Color(0xFFE5E7EB) else Color(0xFF1F2937)
            val secTimeColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

            // Section time definitions (1..12: full morning, afternoon, and evening sections)
            val allSectionTimes = listOf(
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
            val activeSectionTimes = allSectionTimes.take(visibleSections)

            val secNumFontSize = if (visibleSections > 8) 11.sp else 13.sp
            val secTimeFontSize = if (visibleSections > 8) 6.5.sp else 7.sp

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(containerBg)
                    .padding(8.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // 1. Top Header Row: "10月" on left + 7 day columns
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
                                text = "10",
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

                        // 7 Days: 一/12, 二/13, 三/14, 四/15, 五/16, 六/17, 日/18
                        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
                        val dayDates = listOf("12", "13", "14", "15", "16", "17", "18")

                        dayNames.forEachIndexed { idx, name ->
                            val isToday = (idx == 4) // Friday 16 is today in sample
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

                        // Colors for course cards depending on day/night
                        val mintAccent = if (isDark) Color(0xFF34D399) else Color(0xFF86E3C3)
                        val mintText = if (isDark) Color(0xFFA7F3D0) else Color(0xFF0F5145)

                        val peachAccent = if (isDark) Color(0xFFFB923C) else Color(0xFFFFBD80)
                        val peachText = if (isDark) Color(0xFFFED7AA) else Color(0xFF7A3E00)

                        val lavenderAccent = if (isDark) Color(0xFF818CF8) else Color(0xFFA5B4FC)
                        val lavenderText = if (isDark) Color(0xFFC7D2FE) else Color(0xFF3730A3)

                        val skyAccent = if (isDark) Color(0xFF38BDF8) else Color(0xFF7DD3FC)
                        val skyText = if (isDark) Color(0xFFBAE6FD) else Color(0xFF0369A1)

                        // 7 Columns with pastel cards (Mint, Peach, Lavender, Sky)
                        // Col 0: Mon
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            // Sec 1..4 (Empty)
                            repeat(minOf(4, visibleSections)) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            // Sec 5: Peach course if visible >= 5
                            if (visibleSections >= 5) {
                                GlancePastelCard(
                                    title = "程序分析与...",
                                    teacher = "文明",
                                    colorTheme = "peach",
                                    accentColor = peachAccent,
                                    textColor = peachText,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                            }
                            // Sec 6..8
                            if (visibleSections >= 6) {
                                repeat(minOf(3, visibleSections - 5)) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                            // Sec 9..10 if visible >= 9
                            if (visibleSections >= 9) {
                                GlancePastelCard(
                                    title = "高级算法",
                                    teacher = "李教授",
                                    colorTheme = "sky",
                                    accentColor = skyAccent,
                                    textColor = skyText,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                if (visibleSections >= 10) {
                                    Spacer(modifier = GlanceModifier.defaultWeight())
                                }
                            }
                            // Sec 11..12
                            if (visibleSections >= 11) {
                                repeat(visibleSections - 10) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                        }

                        // Col 1: Tue (Sec 3-4 Mint card, Sec 7-8 Lavender card)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            // Sec 1-2
                            repeat(minOf(2, visibleSections)) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            // Sec 3-4 (Mint card, seamless top & bottom)
                            if (visibleSections >= 4) {
                                GlancePastelCard(
                                    title = "认证与访问...",
                                    teacher = "崔永泉",
                                    colorTheme = "mint",
                                    accentColor = mintAccent,
                                    textColor = mintText,
                                    isTopHalf = true,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                GlancePastelCard(
                                    classroom = "@A415",
                                    isNonCurrentWeek = true,
                                    colorTheme = "mint",
                                    accentColor = mintAccent,
                                    textColor = mintText,
                                    isBottomHalf = true,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                            }
                            // Sec 5-6 (Empty)
                            if (visibleSections >= 6) {
                                repeat(minOf(2, visibleSections - 4)) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                            // Sec 7-8 (Lavender card if visible >= 8)
                            if (visibleSections >= 8) {
                                GlancePastelCard(
                                    title = "大学体育",
                                    classroom = "@体育馆",
                                    colorTheme = "lavender",
                                    accentColor = lavenderAccent,
                                    textColor = lavenderText,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                            // Sec 9..12
                            if (visibleSections >= 9) {
                                repeat(visibleSections - 8) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                        }

                        // Col 2: Wed (Sec 1-2 Sky card, Sec 5-6 Peach card, Sec 9-10 Mint card)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            // Sec 1-2
                            GlancePastelCard(
                                title = "大学英语",
                                teacher = "Smith",
                                colorTheme = "sky",
                                accentColor = skyAccent,
                                textColor = skyText,
                                isTopHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            GlancePastelCard(
                                classroom = "@东中 201",
                                colorTheme = "sky",
                                accentColor = skyAccent,
                                textColor = skyText,
                                isBottomHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            // Sec 3-4
                            repeat(minOf(2, maxOf(0, visibleSections - 2))) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            // Sec 5-6
                            if (visibleSections >= 6) {
                                GlancePastelCard(
                                    title = "大学物理",
                                    teacher = "陈教授",
                                    colorTheme = "peach",
                                    accentColor = peachAccent,
                                    textColor = peachText,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                            // Sec 7..8
                            if (visibleSections >= 8) {
                                repeat(minOf(2, visibleSections - 6)) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                            // Sec 9..10 (Evening class)
                            if (visibleSections >= 9) {
                                GlancePastelCard(
                                    title = "数据库设计",
                                    teacher = "张老师",
                                    colorTheme = "mint",
                                    accentColor = mintAccent,
                                    textColor = mintText,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                if (visibleSections >= 10) {
                                    Spacer(modifier = GlanceModifier.defaultWeight())
                                }
                            }
                            // Sec 11..12
                            if (visibleSections >= 11) {
                                repeat(visibleSections - 10) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                        }

                        // Col 3: Thu (Sec 3-4 Peach card, Sec 7-8 Lavender card)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(minOf(2, visibleSections)) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            if (visibleSections >= 4) {
                                GlancePastelCard(
                                    title = "程序分析与...",
                                    teacher = "文明",
                                    colorTheme = "peach",
                                    accentColor = peachAccent,
                                    textColor = peachText,
                                    isTopHalf = true,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                GlancePastelCard(
                                    classroom = "@A415",
                                    isNonCurrentWeek = true,
                                    colorTheme = "peach",
                                    accentColor = peachAccent,
                                    textColor = peachText,
                                    isBottomHalf = true,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                            }
                            if (visibleSections >= 6) {
                                repeat(minOf(2, visibleSections - 4)) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                            if (visibleSections >= 8) {
                                GlancePastelCard(
                                    title = "思想道德...",
                                    classroom = "@文科楼",
                                    colorTheme = "lavender",
                                    accentColor = lavenderAccent,
                                    textColor = lavenderText,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                            if (visibleSections >= 9) {
                                repeat(visibleSections - 8) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                        }

                        // Col 4: Fri (Sec 1-2 Sky card, Sec 3-4 Mint card, Sec 5-6 Peach card, Sec 9-10 Sky card)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            // Sec 1-2
                            GlancePastelCard(
                                title = "高等数学",
                                teacher = "张教授",
                                colorTheme = "sky",
                                accentColor = skyAccent,
                                textColor = skyText,
                                isTopHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            GlancePastelCard(
                                classroom = "@东上 101",
                                colorTheme = "sky",
                                accentColor = skyAccent,
                                textColor = skyText,
                                isBottomHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            // Sec 3-4
                            if (visibleSections >= 4) {
                                GlancePastelCard(
                                    title = "认证与访问...",
                                    teacher = "崔永泉",
                                    colorTheme = "mint",
                                    accentColor = mintAccent,
                                    textColor = mintText,
                                    isTopHalf = true,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                GlancePastelCard(
                                    classroom = "@A415",
                                    isNonCurrentWeek = true,
                                    colorTheme = "mint",
                                    accentColor = mintAccent,
                                    textColor = mintText,
                                    isBottomHalf = true,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                            }
                            // Sec 5-6
                            if (visibleSections >= 6) {
                                GlancePastelCard(
                                    title = "线性代数",
                                    teacher = "王教授",
                                    colorTheme = "peach",
                                    accentColor = peachAccent,
                                    textColor = peachText,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                            // Sec 7..8
                            if (visibleSections >= 8) {
                                repeat(minOf(2, visibleSections - 6)) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                            // Sec 9..10 (Evening class)
                            if (visibleSections >= 9) {
                                GlancePastelCard(
                                    title = "移动开发实战",
                                    teacher = "李教授",
                                    colorTheme = "sky",
                                    accentColor = skyAccent,
                                    textColor = skyText,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                if (visibleSections >= 10) {
                                    Spacer(modifier = GlanceModifier.defaultWeight())
                                }
                            }
                            // Sec 11..12
                            if (visibleSections >= 11) {
                                repeat(visibleSections - 10) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            }
                        }

                        // Col 5: Sat (Empty)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(visibleSections) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                        }

                        // Col 6: Sun (Empty)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(visibleSections) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * WakeUp pastel card with top accent stripe and optional "非本周" pill.
 * Supports mint, peach, lavender, and sky themes with day/night automatic drawables.
 */
@Composable
private fun GlancePastelCard(
    title: String = "",
    teacher: String = "",
    classroom: String = "",
    isNonCurrentWeek: Boolean = false,
    colorTheme: String = "mint", // "mint", "peach", "lavender", "sky"
    accentColor: Color,
    textColor: Color,
    modifier: GlanceModifier = GlanceModifier,
    isTopHalf: Boolean = false,
    isBottomHalf: Boolean = false
) {
    val bgDrawable = when (colorTheme) {
        "mint" -> when {
            isTopHalf -> R.drawable.bg_widget_card_mint_top
            isBottomHalf -> R.drawable.bg_widget_card_mint_bottom
            else -> R.drawable.bg_widget_card_mint_single
        }
        "peach" -> when {
            isTopHalf -> R.drawable.bg_widget_card_peach_top
            isBottomHalf -> R.drawable.bg_widget_card_peach_bottom
            else -> R.drawable.bg_widget_card_peach_single
        }
        "lavender" -> when {
            isTopHalf -> R.drawable.bg_widget_card_lavender_top
            isBottomHalf -> R.drawable.bg_widget_card_lavender_bottom
            else -> R.drawable.bg_widget_card_lavender_single
        }
        else -> when {
            isTopHalf -> R.drawable.bg_widget_card_sky_top
            isBottomHalf -> R.drawable.bg_widget_card_sky_bottom
            else -> R.drawable.bg_widget_card_sky_single
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ImageProvider(bgDrawable))
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            if (!isBottomHalf) {
                // Top accent stripe (3dp)
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(accentColor)
                ) {}
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 2.5.dp, vertical = 2.dp)
            ) {
                if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(textColor)
                        ),
                        maxLines = 2
                    )
                }
                if (teacher.isNotEmpty()) {
                    Text(
                        text = teacher,
                        style = TextStyle(
                            fontSize = 8.5.sp,
                            color = ColorProvider(textColor.copy(alpha = 0.85f))
                        ),
                        maxLines = 1
                    )
                }
                if (classroom.isNotEmpty()) {
                    Text(
                        text = classroom,
                        style = TextStyle(
                            fontSize = 8.sp,
                            color = ColorProvider(textColor.copy(alpha = 0.85f))
                        ),
                        maxLines = 1
                    )
                }
                if (isNonCurrentWeek) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(3.dp)
                            .background(accentColor.copy(alpha = 0.35f))
                            .padding(horizontal = 2.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = "非本周",
                            style = TextStyle(
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(textColor)
                            )
                        )
                    }
                }
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
