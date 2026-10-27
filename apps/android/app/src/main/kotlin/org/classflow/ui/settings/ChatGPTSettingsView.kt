package org.classflow.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.classflow.automation.ChatGPTConnection
import org.classflow.automation.ChatGPTService

@Composable
fun ChatGPTSettingsView() {
    val context = LocalContext.current
    var tunnel by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val status by ChatGPTConnection.status.collectAsState()
    LaunchedEffect(Unit) { runCatching { ChatGPTConnection.load(context).first }.onSuccess { tunnel = it } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ChatGPT · 此 Android 设备", style = MaterialTheme.typography.titleMedium)
        Text("Mac 关机也可使用。为本设备单独创建隧道，避免与 Mac 共用 Tunnel ID。工具参数和结果会进入所连接的聊天。")
        OutlinedTextField(tunnel, { tunnel = it }, label = { Text("Tunnel ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(key, { key = it }, label = { Text("专用运行密钥（留空保留已存密钥）") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(status)
        Button(onClick = {
            runCatching {
                val saved = if (key.isBlank()) ChatGPTConnection.load(context).second else key
                ChatGPTConnection.save(context, tunnel.trim(), saved)
                key = ""
                ContextCompat.startForegroundService(context, Intent(context, ChatGPTService::class.java))
            }.onFailure { message = "保存或启动失败，请检查连接配置及系统后台权限" }.onSuccess { message = "已请求连接；以实际连接状态为准" }
        }) { Text("保存并连接") }
        OutlinedButton(onClick = { context.stopService(Intent(context, ChatGPTService::class.java)) }) { Text("停止连接") }
        if (message.isNotBlank()) Text(message)
        Text("在 ChatGPT 中添加自定义 MCP，选择 Tunnel 并选中此设备的隧道，然后新建聊天调用 get_status 确认 platform=android。需要对应账户与工作区权限。")
        Text("支持课程与日程完整系列的批量编辑、预检和安全重试；暂不支持单次/未来实例修改、待办写入或专注控制。连接由常驻通知提示，系统休眠、断网或强制停止可能中断连接。")
    }
}
