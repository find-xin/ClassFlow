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
 * 4x4 Glance AppWidget matching WakeUp's Week Timetable Overview:
 * - A real 7-day mini timetable grid!
 * - Displays 7 weekday columns (一 二 三 四 五 六 日)
 * - Renders colored miniature course blocks with course title and classroom
 * - Highlights today's column softly
 */
class WeekTimetableGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(Color(0xF0181A24))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "第 7 周课表 · 14 门课",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color.White)
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = "10/16 周五",
                            style = TextStyle(
                                fontSize = 10.5.sp,
                                color = ColorProvider(Color(0xFF818CF8))
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // 7-day columns mini grid
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.Top
                    ) {
                        val days = listOf("一", "二", "三", "四", "五", "六", "日")
                        days.forEachIndexed { idx, day ->
                            val isToday = (idx == 4) // Friday in sample
                            Column(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight()
                                    .padding(horizontal = 1.dp)
                                    .then(
                                        if (isToday) GlanceModifier.background(Color(0x334F46E5)).cornerRadius(4.dp)
                                        else GlanceModifier
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = day,
                                    style = TextStyle(
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = ColorProvider(if (isToday) Color(0xFF818CF8) else Color(0xFFB0B3C2))
                                    )
                                )

                                Spacer(modifier = GlanceModifier.height(4.dp))

                                // Mini course blocks on specific days
                                when (idx) {
                                    0 -> { // Mon
                                        MiniCourseBlock("英语", "@教三", Color(0xFF039BE5))
                                    }
                                    2 -> { // Wed
                                        MiniCourseBlock("高数", "@东上", Color(0xFF4F46E5))
                                        Spacer(modifier = GlanceModifier.height(2.dp))
                                        MiniCourseBlock("算法", "@信302", Color(0xFF0B8043))
                                    }
                                    3 -> { // Thu
                                        MiniCourseBlock("计网", "@实401", Color(0xFFF59E0B))
                                    }
                                    4 -> { // Fri (Today)
                                        MiniCourseBlock("高数", "@东上", Color(0xFF4F46E5))
                                        Spacer(modifier = GlanceModifier.height(2.dp))
                                        MiniCourseBlock("算法", "@信302", Color(0xFF0B8043))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCourseBlock(title: String, location: String, color: Color) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(4.dp)
            .background(color.copy(alpha = 0.85f))
            .padding(2.dp)
    ) {
        Column {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.White)
                )
            )
            Text(
                text = location,
                style = TextStyle(
                    fontSize = 7.5.sp,
                    color = ColorProvider(Color(0xEEFFFFFF))
                )
            )
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
