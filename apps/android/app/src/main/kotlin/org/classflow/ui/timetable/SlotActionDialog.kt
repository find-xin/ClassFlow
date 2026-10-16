package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Anchored DropdownMenu matching macOS popover:
 * - 📘 添加课程 (Blue book icon, #1877F2)
 * - Subtle divider
 * - 📅 添加日程 (Red calendar plus icon, #FF5A5F)
 * Automatically adapts position based on anchor coordinates (prevents screen clipping at borders).
 */
@Composable
fun SlotActionMenu(
    expanded: Boolean,
    dayOfWeek: Int = 1,
    section: Int = 1,
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onAddCourse: (dayOfWeek: Int, section: Int) -> Unit,
    onAddSchedule: (dayOfWeek: Int, section: Int) -> Unit
) {
    val bg = if (isDark) Color(0xFF232530) else Color(0xFFFBFBFD)
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE5E7EB)

    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(14.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(14.dp)
        )
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier
                .width(165.dp)
                .background(bg, RoundedCornerShape(14.dp))
                .border(
                    0.5.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E4E9),
                    RoundedCornerShape(14.dp)
                )
        ) {
            // 1. 添加课程 (Blue icon matching macOS)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "添加课程",
                            tint = Color(0xFF1877F2),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "添加课程",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary
                        )
                    }
                },
                onClick = {
                    onDismiss()
                    onAddCourse(dayOfWeek, section)
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            )

            HorizontalDivider(
                color = dividerColor,
                thickness = 0.8.dp,
                modifier = Modifier.padding(horizontal = 10.dp)
            )

            // 2. 添加日程 (Red icon matching macOS)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "添加日程",
                            tint = Color(0xFFFF5A5F),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "添加日程",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary
                        )
                    }
                },
                onClick = {
                    onDismiss()
                    onAddSchedule(dayOfWeek, section)
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Centered fallback Dialog if caller does not have an anchor Box.
 */
@Composable
fun SlotActionDialog(
    dayOfWeek: Int = 1,
    section: Int = 1,
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onAddCourse: (dayOfWeek: Int, section: Int) -> Unit,
    onAddSchedule: (dayOfWeek: Int, section: Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(165.dp)
                .background(if (isDark) Color(0xFF232530) else Color(0xFFFBFBFD), RoundedCornerShape(14.dp))
                .border(0.5.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E4E9), RoundedCornerShape(14.dp))
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "添加课程",
                                tint = Color(0xFF1877F2),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "添加课程",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
                            )
                        }
                    },
                    onClick = {
                        onDismiss()
                        onAddCourse(dayOfWeek, section)
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                )

                HorizontalDivider(
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE5E7EB),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "添加日程",
                                tint = Color(0xFFFF5A5F),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "添加日程",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
                            )
                        }
                    },
                    onClick = {
                        onDismiss()
                        onAddSchedule(dayOfWeek, section)
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
