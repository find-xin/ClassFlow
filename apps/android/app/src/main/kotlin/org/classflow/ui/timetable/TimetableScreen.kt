package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
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
    onSyncClick: () -> Unit
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
                    IconButton(onClick = onSyncClick) {
                        Icon(Icons.Default.Sync, contentDescription = "云同步")
                    }
                    IconButton(onClick = onAddCourseClick) {
                        Icon(Icons.Default.Add, contentDescription = "添加课程")
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

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Section index header blank space
        Box(modifier = Modifier.width(36.dp))

        for (day in dayNames) {
            Text(
                text = "周$day",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
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

    val alpha = if (item.isCurrentWeek) 0.92f else 0.45f

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
