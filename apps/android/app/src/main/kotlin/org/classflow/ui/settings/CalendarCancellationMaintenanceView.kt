package org.classflow.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.classflow.data.local.CalendarCancellationCleanup
import org.classflow.data.local.CourseDataManager

@Composable
fun CalendarCancellationMaintenanceView(onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<CalendarCancellationCleanup.Plan?>(null) }
    var removeRedundant by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("重复日程取消记录维护", style = MaterialTheme.typography.titleMedium)
            Text("取消日期永久保存在系列中；保留课程停课、调课和冲突记录。执行前自动备份。")
            Row { Checkbox(checked = removeRedundant, enabled = !busy, onCheckedChange = { removeRedundant = it; plan = null }); Text("迁移后清理冗余取消记录") }
            Button(enabled = !busy, onClick = {
                plan = CalendarCancellationCleanup.preview(CourseDataManager.loadSchedules(context), CourseDataManager.loadExceptions(context),
                    CourseDataManager.loadCourses(context).flatMap { it.schedules }.map { it.id }.toSet(), removeRedundant = removeRedundant)
                message = null
            }) { Text("预览批量清理") }
            plan?.let { preview ->
                Text("涉及 ${preview.affectedSeries.size} 个系列；新增 ${preview.migratedDates} 个排除日期；清理 ${preview.removed.size} 条记录（重复 ${preview.duplicateRecords} 条）；保留 ${preview.skippedRecords} 条受保护或需核对的取消记录。")
                preview.affectedSeries.forEach { event ->
                    Text("${event.title}：${event.excludedDates.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                }
                Button(enabled = !busy && (preview.changedSeries > 0 || preview.removed.isNotEmpty()), onClick = {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { CourseDataManager.applyCalendarCancellationCleanup(context, preview) }
                            onSaved(); message = "已保存永久排除日期并完成清理。"
                        } catch (error: Exception) { onSaved(); message = error.message ?: "清理失败，请重新预览。" }
                        plan = null; busy = false
                    }
                }) { Text("执行上述迁移与清理") }
            }
            message?.let { Text(it) }
        }
    }
}
