package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

enum class TimetableDisplayMode {
    WEEK,
    DAY
}

/**
 * Top header matching WakeUp and macOS WakeUpHeaderView:
 * - Clickable week title with chevron, current date and semester name.
 * - Week / Day view toggle.
 * - Ghost action icons: (+) Add (Course/Schedule), (···) Quick Actions Menu.
 */
@Composable
fun WakeUpHeaderView(
    currentWeek: Int,
    selectedWeek: Int,
    totalWeeks: Int,
    semesterName: String,
    displayMode: TimetableDisplayMode,
    onDisplayModeChange: (TimetableDisplayMode) -> Unit,
    onWeekChange: (Int) -> Unit,
    onAddCourseClick: () -> Unit,
    onAddScheduleClick: () -> Unit,
    onOpenThreeDotsMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showWeekPicker by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }

    val todayWeekdayShort = remember {
        val cal = Calendar.getInstance()
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "一"
            Calendar.TUESDAY -> "二"
            Calendar.WEDNESDAY -> "三"
            Calendar.THURSDAY -> "四"
            Calendar.FRIDAY -> "五"
            Calendar.SATURDAY -> "六"
            Calendar.SUNDAY -> "日"
            else -> "一"
        }
    }

    val currentDateStr = remember {
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
    }

    val isCurrentWeek = (selectedWeek == currentWeek)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left: Clickable Week and Date Title
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { showWeekPicker = true }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "第${selectedWeek}周 周$todayWeekdayShort",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isCurrentWeek) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "非本周",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "切换周次",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = currentDateStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
                Text(
                    text = "·",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = semesterName,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        // 2. Right: View Toggle + Action Icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Week / Day Toggle
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.height(34.dp)
            ) {
                SegmentedButton(
                    selected = displayMode == TimetableDisplayMode.WEEK,
                    onClick = { onDisplayModeChange(TimetableDisplayMode.WEEK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("周", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                SegmentedButton(
                    selected = displayMode == TimetableDisplayMode.DAY,
                    onClick = { onDisplayModeChange(TimetableDisplayMode.DAY) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("日", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // (+) Add Menu
            Box {
                IconButton(
                    onClick = { showAddMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("添加课程...", fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showAddMenu = false
                            onAddCourseClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加日程 / 待办...", fontSize = 13.5.sp) },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showAddMenu = false
                            onAddScheduleClick()
                        }
                    )
                }
            }

            // (···) 3-Dots Quick Actions Menu
            IconButton(
                onClick = onOpenThreeDotsMenu,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多设置",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // Week Picker Dialog
    if (showWeekPicker) {
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("选择周次", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (!isCurrentWeek) {
                        TextButton(
                            onClick = {
                                onWeekChange(currentWeek)
                                showWeekPicker = false
                            }
                        ) {
                            Text("回到本周")
                        }
                    }
                }
            },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(totalWeeks) { index ->
                        val wk = index + 1
                        val isSelected = (wk == selectedWeek)
                        val isActualCurrent = (wk == currentWeek)

                        Box(
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isActualCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    }
                                )
                                .clickable {
                                    onWeekChange(wk)
                                    showWeekPicker = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$wk",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected || isActualCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isActualCurrent -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (isActualCurrent && !isSelected) {
                                    Text(
                                        text = "本周",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeekPicker = false }) {
                    Text("取消")
                }
            }
        )
    }
}
