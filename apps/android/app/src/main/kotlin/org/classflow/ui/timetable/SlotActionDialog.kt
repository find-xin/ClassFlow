package org.classflow.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * macOS Popover 1:1 Action Menu matching media_1792136267556_46b6f9f4.png:
 * - 📘 添加课程 (Blue book icon, #1877F2)
 * - Subtle divider
 * - 📅 添加日程 (Red calendar plus icon, #FF5A5F)
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
    val bg = if (isDark) Color(0xFF232530) else Color(0xFFFBFBFD)
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE5E7EB)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(170.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. 添加课程 (Blue icon matching macOS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onAddCourse(dayOfWeek, section)
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "添加课程",
                        tint = Color(0xFF1877F2),
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "添加课程",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary
                    )
                }

                HorizontalDivider(
                    color = dividerColor,
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                // 2. 添加日程 (Red icon matching macOS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onAddSchedule(dayOfWeek, section)
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "添加日程",
                        tint = Color(0xFFFF5A5F),
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "添加日程",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary
                    )
                }
            }
        }
    }
}
