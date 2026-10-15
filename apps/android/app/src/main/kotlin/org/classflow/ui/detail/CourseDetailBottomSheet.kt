package org.classflow.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.classflow.data.model.EditingScope
import org.classflow.data.model.TimelineItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailBottomSheet(
    item: TimelineItem,
    isDark: Boolean = false,
    onDismiss: () -> Unit,
    onApplyMutation: (scope: EditingScope, newRoom: String?) -> Unit,
    onCancelCourse: (scope: EditingScope) -> Unit
) {
    var selectedScope by remember { mutableStateOf(EditingScope.THIS_ONLY) }
    var isEditing by remember { mutableStateOf(false) }
    var editedRoom by remember { mutableStateOf(item.location ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1E202B) else Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details
            DetailRow(label = "上课日期", value = "${item.date}（第 ${item.weekNumber} 周）")
            DetailRow(label = "上课时间", value = "${item.startTime} - ${item.endTime}（第 ${item.startSection}-${item.endSection} 节）")
            DetailRow(label = "任课教师", value = item.teacher ?: "暂无")
            DetailRow(label = "上课教室", value = item.location ?: "未指定")

            Spacer(modifier = Modifier.height(16.dp))

            // Editing Scope Radio Options
            Text(
                text = "修改生效范围",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ScopeOption(
                    title = "仅本次",
                    selected = selectedScope == EditingScope.THIS_ONLY,
                    onSelect = { selectedScope = EditingScope.THIS_ONLY }
                )
                ScopeOption(
                    title = "后续所有",
                    selected = selectedScope == EditingScope.THIS_AND_FUTURE,
                    onSelect = { selectedScope = EditingScope.THIS_AND_FUTURE }
                )
                ScopeOption(
                    title = "全部安排",
                    selected = selectedScope == EditingScope.ALL,
                    onSelect = { selectedScope = EditingScope.ALL }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editedRoom,
                    onValueChange = { editedRoom = it },
                    label = { Text("修改上课教室") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onApplyMutation(selectedScope, editedRoom)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存调整")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("调整安排")
                    }

                    Button(
                        onClick = {
                            onCancelCourse(selectedScope)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("停课/删除")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ScopeOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = title, style = MaterialTheme.typography.bodySmall)
    }
}
