package org.classflow.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.EditingScope
import org.classflow.data.model.TimelineItem
import org.classflow.ui.components.parseHexColor

/**
 * 1:1 Pixel-level alignment with WakeUp course detail bottom sheet (Reference Image 2):
 * - Top: Course title, teacher name, right vertical color indicator pill
 * - Divider line
 * - Detail metadata rows: Time, Classroom, Date/Week, Notes
 * - Big Coral Pomodoro Action Button: "⏱️ 开启课程专注" [番茄钟]
 * - Bottom action bar:
 *   - Red trash can square button
 *   - "临时调课" pill
 *   - "本次停课" pill
 *   - Coral "✏️ 编辑" pill
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailBottomSheet(
    item: TimelineItem,
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onEditCourse: () -> Unit = {},
    onStartFocus: (title: String, location: String?) -> Unit = { _, _ -> },
    onApplyMutation: (scope: EditingScope, newRoom: String?) -> Unit = { _, _ -> },
    onCancelCourse: (scope: EditingScope) -> Unit = {},
    onDeleteCourse: (scope: EditingScope) -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showRescheduleDialog by remember { mutableStateOf(false) }
    var newRoomInput by remember { mutableStateOf(item.location ?: "") }

    val isEvent = item.calendarEventId != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            // 1. Top Row: Title + Teacher/Type, Right Color Indicator Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isEvent) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF34C759).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "日程",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34C759),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isEvent) (item.notes ?: "自定义日程安排") else (item.teacher ?: "暂无任课教师"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(parseHexColor(item.color))
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 14.dp)
            )

            // 2. Info Rows: Time, Room, Date, Notes
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "时间",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp)
                    )
                    val timeStr = if (item.startTime != null && item.endTime != null) {
                        if (item.startSection > 0 && item.endSection > 0) {
                            "第 ${item.startSection}-${item.endSection} 节 (${item.startTime} ~ ${item.endTime})"
                        } else {
                            "${item.startTime} ~ ${item.endTime}"
                        }
                    } else if (item.isAllDay) {
                        "全天"
                    } else {
                        "第 ${item.startSection}-${item.endSection} 节"
                    }
                    Text(
                        text = timeStr,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Classroom / Location
                if (!item.location.isNullOrBlank() || !isEvent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isEvent) "地点" else "教室",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text = item.location ?: "未指定",
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Credit
                if (item.credit != null && item.credit > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "学分",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp)
                        )
                        val cr = item.credit
                        val crStr = if (cr % 1.0 == 0.0) "${cr.toInt()} 学分" else "$cr 学分"
                        Text(
                            text = crStr,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Date & Week
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "日期",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp)
                    )
                    Text(
                        text = "${item.date} (第 ${item.weekNumber} 周)",
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Notes
                if (!item.notes.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "备注",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text = item.notes,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Coral Pomodoro Focus Big Button (Only for courses, not for calendar schedules)
            if (!isEvent) {
                Surface(
                    onClick = {
                        onStartFocus(item.title, item.location)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFF5A5F),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "开启课程专注",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "番茄钟",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 14.dp)
            )

            // 4. Bottom Action Bar: [Trash Can] [临时调课] [本次停课] [✏️ 编辑]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trash Button
                Surface(
                    onClick = { showDeleteConfirm = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = if (isEvent) "删除该日程" else "删除该课程",
                            tint = Color(0xFFFF5A5F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (!isEvent) {
                    // 临时调课 Pill
                    Surface(
                        onClick = { showRescheduleDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "临时调课",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 本次停课 / 恢复上课 Pill (Only for Courses, never for Events)
                    val isCancelled = item.status == org.classflow.data.model.OccurrenceStatus.CANCELLED
                    Surface(
                        onClick = {
                            if (isCancelled) {
                                onCancelCourse(EditingScope.THIS_ONLY)
                                onDismiss()
                            } else {
                                showCancelConfirm = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isCancelled) "恢复上课" else "本次停课",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCancelled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // ✏️ 编辑 Pill (Both Courses and Events)
                Surface(
                    onClick = {
                        onEditCourse()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF5A5F),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEvent) "编辑日程" else "编辑",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    // Confirm Delete Dialog (Google Calendar style 3 options for recurring items)
    if (showDeleteConfirm) {
        if (item.isRecurring) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(if (isEvent) "删除循环日程" else "删除循环课程", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isEvent) "该日程为周期性重复安排，请选择要删除的范围：" else "该课程在学期中多次上课，请选择要删除的课时范围：",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Option 1: THIS_ONLY
                        OutlinedButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDeleteCourse(EditingScope.THIS_ONLY)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isEvent) "仅删除此日程" else "仅删除本次课时")
                        }

                        // Option 2: THIS_AND_FUTURE
                        OutlinedButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDeleteCourse(EditingScope.THIS_AND_FUTURE)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isEvent) "删除此日程及随后的所有日程" else "删除本次及随后所有课时")
                        }

                        // Option 3: ALL
                        Button(
                            onClick = {
                                showDeleteConfirm = false
                                onDeleteCourse(EditingScope.ALL)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isEvent) "删除所有日程" else "彻底删除整个课程", color = Color.White)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(if (isEvent) "删除日程" else "删除课程", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = { Text("确定要删除「${item.title}」吗？此操作无法撤销。") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDeleteCourse(EditingScope.ALL)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("删除", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }

    // Confirm Cancel Dialog
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("本次停课", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text(if (isEvent) "确定要停掉「${item.title}」在 ${item.date} 的日程吗？" else "确定要停掉第 ${item.weekNumber} 周「${item.title}」这节课吗？后续其他周次不受影响。") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        onCancelCourse(EditingScope.THIS_ONLY)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("确定停课", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Reschedule Classroom Dialog
    if (showRescheduleDialog) {
        AlertDialog(
            onDismissRequest = { showRescheduleDialog = false },
            title = { Text("临时调课 / 更换教室", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("仅修改第 ${item.weekNumber} 周本次上课地点：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newRoomInput,
                        onValueChange = { newRoomInput = it },
                        label = { Text("上课教室") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRescheduleDialog = false
                        onApplyMutation(EditingScope.THIS_ONLY, newRoomInput.trim())
                        onDismiss()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescheduleDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
