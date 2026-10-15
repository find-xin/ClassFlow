package org.classflow.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.classflow.data.model.AppSettings

enum class QuickActionSheetType {
    NONE,
    SECTION_TIMES,
    TIMETABLE_SETTINGS,
    COURSE_LIST,
    APPEARANCE,
    WIDGETS,
    THEME_MODE
}

@Composable
fun ThreeDotsMenuDialog(
    currentSettings: AppSettings,
    onDismiss: () -> Unit,
    onOpenAction: (QuickActionSheetType) -> Unit,
    onToggleTheme: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("课表快捷功能", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 3 x 2 Quick Grid matching macOS WakeUpQuickGridPopover
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickGridItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccessTime,
                        title = "上课时间",
                        onClick = { onOpenAction(QuickActionSheetType.SECTION_TIMES) }
                    )
                    QuickGridItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Settings,
                        title = "课表设置",
                        onClick = { onOpenAction(QuickActionSheetType.TIMETABLE_SETTINGS) }
                    )
                    QuickGridItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ListAlt,
                        title = "已选课程",
                        onClick = { onOpenAction(QuickActionSheetType.COURSE_LIST) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickGridItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Palette,
                        title = "课表外观",
                        onClick = { onOpenAction(QuickActionSheetType.APPEARANCE) }
                    )
                    QuickGridItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Widgets,
                        title = "小组件",
                        onClick = { onOpenAction(QuickActionSheetType.WIDGETS) }
                    )
                    QuickGridItem(
                        modifier = Modifier.weight(1f),
                        icon = if (currentSettings.themeMode == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                        title = if (currentSettings.themeMode == "dark") "深色模式" else "浅色模式",
                        onClick = onToggleTheme
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@Composable
fun QuickGridItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
