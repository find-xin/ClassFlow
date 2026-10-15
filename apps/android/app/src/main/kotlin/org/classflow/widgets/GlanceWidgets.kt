package org.classflow.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 2x2 Glance AppWidget matching WakeUp's Next Class Widget:
 * - Frosted dark glass container with 16dp rounded corners
 * - "下节课" pill badge with start time
 * - Large bold course title, classroom tag (@东上 101), and teacher
 */
class NextClassGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(Color(0xF0181A24))
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
                                .background(Color(0xFF3F51B5))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "下节课",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color.White)
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        Text(
                            text = "10:00 开始",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = ColorProvider(Color(0xFFB0B3C2))
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
                            color = ColorProvider(Color.White)
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
                                color = ColorProvider(Color(0xFF818CF8))
                            )
                        )
                        Text(
                            text = " · 张教授",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = ColorProvider(Color(0xFFB0B3C2))
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Countdown Status
                    Text(
                        text = "第 3-4 节 · 距上课 25 分钟",
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = ColorProvider(Color(0xFF9094A6))
                        )
                    )
                }
            }
        }
    }
}

/**
 * 4x2 Glance AppWidget matching WakeUp's Today Classes Widget:
 * - Frosted dark glass container with 18dp rounded corners
 * - Header row with today date, weekday, and total courses count
 * - Linear list with left colored indicator bars, time, section, and location
 */
class TodayClassesGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(Color(0xF0181A24))
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
                                color = ColorProvider(Color.White)
                            )
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(6.dp)
                                .background(Color(0xFF2A2D3D))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "共 2 节",
                                style = TextStyle(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color(0xFF818CF8))
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(10.dp))

                    // Course Item 1
                    GlanceCourseRow(
                        title = "高等数学",
                        time = "08:00 - 09:35 (第 1-2 节)",
                        location = "@东上 101",
                        accentColor = Color(0xFF4F46E5)
                    )

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Course Item 2
                    GlanceCourseRow(
                        title = "数据结构与算法",
                        time = "10:00 - 11:35 (第 3-4 节)",
                        location = "@信息楼 302",
                        accentColor = Color(0xFF0B8043)
                    )
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
    accentColor: Color
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical accent bar
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(32.dp)
                .cornerRadius(2.dp)
                .background(accentColor)
        ) {}

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.White)
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = time,
                    style = TextStyle(
                        fontSize = 10.5.sp,
                        color = ColorProvider(Color(0xFFB0B3C2))
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = location,
                    style = TextStyle(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(Color(0xFF818CF8))
                    )
                )
            }
        }
    }
}

/**
 * 4x2 / 4x4 Glance AppWidget precisely matching WakeUp's Week Timetable Overview (Reference Image 2):
 * - Pure white container with 24dp rounded corners
 * - Top Header: Left month indicator ("10\n月") + 7 day columns ("一/12", "二/13", ... "五/16" bold)
 * - Left Section Axis: Section number (1..5) with start and end times underneath ("08:30\n09:15")
 * - Elegant pastel course cards (Mint & Peach) with top 3dp accent stripe and "非本周" tags
 */
class WeekTimetableGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(Color.White)
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
                                    color = ColorProvider(Color(0xFF1F2937))
                                )
                            )
                            Text(
                                text = "月",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color(0xFF1F2937))
                                )
                            )
                        }

                        // 7 Days: 一/12, 二/13, 三/14, 四/15, 五/16, 六/17, 日/18
                        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
                        val dayDates = listOf("12", "13", "14", "15", "16", "17", "18")

                        dayNames.forEachIndexed { idx, name ->
                            val isToday = (idx == 4) // Friday 16 is today in sample
                            val textColor = if (isToday) Color(0xFF111827) else Color(0xFF6B7280)
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
                        // Left Section Axis (5 sections)
                        Column(
                            modifier = GlanceModifier.width(36.dp).fillMaxHeight()
                        ) {
                            val sectionTimesList = listOf(
                                "1" to "08:30\n09:15",
                                "2" to "09:20\n10:05",
                                "3" to "10:15\n11:00",
                                "4" to "11:05\n11:50",
                                "5" to "14:30\n15:15"
                            )

                            sectionTimesList.forEach { (secNum, times) ->
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
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(Color(0xFF1F2937))
                                        )
                                    )
                                    Text(
                                        text = times,
                                        style = TextStyle(
                                            fontSize = 7.5.sp,
                                            color = ColorProvider(Color(0xFF6B7280))
                                        )
                                    )
                                }
                            }
                        }

                        // 7 Columns with pastel cards (Mint & Peach matching Image 2)
                        // Col 0: Mon (Section 5)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(4) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            GlancePastelCard(
                                title = "程序分析与...",
                                teacher = "文明",
                                classroom = "",
                                isNonCurrentWeek = false,
                                bgColor = Color(0xFFFFE4C4),
                                accentColor = Color(0xFFFFBD80),
                                textColor = Color(0xFF7A3E00),
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }

                        // Col 1: Tue (Section 3-4 Mint card)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(2) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            GlancePastelCard(
                                title = "认证与访问...",
                                teacher = "崔永泉",
                                bgColor = Color(0xFFC8F8E8),
                                accentColor = Color(0xFF86E3C3),
                                textColor = Color(0xFF0F5145),
                                isTopHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            GlancePastelCard(
                                classroom = "@A415",
                                isNonCurrentWeek = true,
                                bgColor = Color(0xFFC8F8E8),
                                accentColor = Color(0xFF86E3C3),
                                textColor = Color(0xFF0F5145),
                                isBottomHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }

                        // Col 2: Wed (Empty)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(5) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                        }

                        // Col 3: Thu (Section 3-4 Peach card)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(2) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            GlancePastelCard(
                                title = "程序分析与...",
                                teacher = "文明",
                                bgColor = Color(0xFFFFE4C4),
                                accentColor = Color(0xFFFFBD80),
                                textColor = Color(0xFF7A3E00),
                                isTopHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            GlancePastelCard(
                                classroom = "@A415",
                                isNonCurrentWeek = true,
                                bgColor = Color(0xFFFFE4C4),
                                accentColor = Color(0xFFFFBD80),
                                textColor = Color(0xFF7A3E00),
                                isBottomHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }

                        // Col 4: Fri (Section 3-4 Mint card)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(2) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                            GlancePastelCard(
                                title = "认证与访问...",
                                teacher = "崔永泉",
                                bgColor = Color(0xFFC8F8E8),
                                accentColor = Color(0xFF86E3C3),
                                textColor = Color(0xFF0F5145),
                                isTopHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            GlancePastelCard(
                                classroom = "@A415",
                                isNonCurrentWeek = true,
                                bgColor = Color(0xFFC8F8E8),
                                accentColor = Color(0xFF86E3C3),
                                textColor = Color(0xFF0F5145),
                                isBottomHalf = true,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }

                        // Col 5: Sat (Empty)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(5) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                        }

                        // Col 6: Sun (Empty)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)) {
                            repeat(5) { Spacer(modifier = GlanceModifier.defaultWeight()) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * WakeUp pastel card with top accent stripe and optional "非本周" pill matching Image 2.
 */
@Composable
private fun GlancePastelCard(
    title: String = "",
    teacher: String = "",
    classroom: String = "",
    isNonCurrentWeek: Boolean = false,
    bgColor: Color,
    accentColor: Color,
    textColor: Color,
    modifier: GlanceModifier = GlanceModifier,
    isTopHalf: Boolean = false,
    isBottomHalf: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .cornerRadius(8.dp)
            .background(bgColor)
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
