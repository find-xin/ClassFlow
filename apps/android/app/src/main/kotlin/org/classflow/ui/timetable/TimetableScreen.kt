package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
import org.classflow.data.model.TimelineItem
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    currentWeek: Int,
    totalWeeks: Int,
    semesterName: String,
    occurrences: List<TimelineItem>,
    onWeekChange: (Int) -> Unit,
    onCourseClick: (TimelineItem) -> Unit,
    onAddCourseClick: () -> Unit,
    onOpenThreeDotsMenu: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = semesterName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (currentWeek > 1) onWeekChange(currentWeek - 1) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一周", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "第 $currentWeek 周",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { if (currentWeek < totalWeeks) onWeekChange(currentWeek + 1) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一周", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onAddCourseClick) {
                        Icon(Icons.Default.Add, contentDescription = "添加课程")
                    }
                    IconButton(onClick = onOpenThreeDotsMenu) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多设置")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Day Header Row (Mon-Sun)
            DayHeaderRow()

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Timetable Canvas (Section Axis + 7 Columns)
            TimetableCanvas(
                occurrences = occurrences,
                onCourseClick = onCourseClick
            )
        }
    }
}

@Composable
fun DayHeaderRow() {
    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
    val cal = Calendar.getInstance()
    val todayDayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Section index header blank space
        Box(modifier = Modifier.width(36.dp))

        for ((index, day) in dayNames.withIndex()) {
            val isToday = (index + 1 == todayDayOfWeek)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "周$day",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun TimetableCanvas(
    occurrences: List<TimelineItem>,
    onCourseClick: (TimelineItem) -> Unit
) {
    val totalSections = 12
    val sectionHeight = 60.dp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Left Section Axis
        Column(modifier = Modifier.width(36.dp)) {
            for (sec in 1..totalSections) {
                Box(
                    modifier = Modifier
                        .height(sectionHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sec.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 7-day columns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(sectionHeight * totalSections)
        ) {
            for (dayIdx in 1..7) {
                val dayCourses = occurrences.filter { it.dayOfWeek == dayIdx }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.5.dp)
                ) {
                    for (item in dayCourses) {
                        val topOffset = (item.startSection - 1) * 60
                        val cardHeight = (item.endSection - item.startSection + 1) * 60 - 4

                        CourseCard(
                            item = item,
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

@Composable
fun CourseCard(
    item: TimelineItem,
    modifier: Modifier = Modifier
) {
    val cardColor = try {
        Color(android.graphics.Color.parseColor(item.color))
    } catch (e: Throwable) {
        MaterialTheme.colorScheme.primary
    }

    val alpha = if (item.isCurrentWeek) 0.90f else 0.40f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(cardColor.copy(alpha = alpha))
            .padding(4.dp)
    ) {
        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "@${item.location}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
