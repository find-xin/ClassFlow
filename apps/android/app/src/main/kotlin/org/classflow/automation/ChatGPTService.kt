package org.classflow.automation

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.classflow.ui.MainActivity
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Outbound-only implementation of OpenAI's published tunnel poll/response protocol. */
class ChatGPTService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var active: HttpsURLConnection? = null
    private var job: Job? = null
    private val instanceId = java.util.UUID.randomUUID().toString()
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        if (job?.isActive == true) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("chatgpt", "ChatGPT 连接", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(4201, NotificationCompat.Builder(this, "chatgpt").setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("ClassFlow · ChatGPT 独立连接").setContentText("正在连接；可随时停止")
            .setContentIntent(open).setOngoing(true).addAction(0, "停止", stop).build())
        job = scope.launch {
            try {
                val (tunnel, key) = ChatGPTConnection.load(this@ChatGPTService)
                require(tunnel.isNotBlank() && key.isNotBlank())
                val executor = AndroidAutomation(AndroidAutomationStore(this@ChatGPTService), AutomationContract(assets.open("android_mcp_tools.json").bufferedReader().use { it.readText() }))
                var failures = 0
                while (isActive) {
                    try {
                        ChatGPTConnection.report("正在连接")
                        val poll = request(tunnel, key, "poll?channel=main&limit=1&timeout_ms=15000")
                        ChatGPTConnection.report("云端轮询正常 · 等待 ChatGPT 调用")
                        failures = 0
                        val receivedAt = System.nanoTime()
                        val commands = poll?.optJSONArray("commands")
                        if (commands != null) for (i in 0 until commands.length()) {
                            val command = commands.getJSONObject(i)
                            if (command.optString("channel", "main") != "main" || command.optString("command_type", "jsonrpc") !in setOf("jsonrpc", "session_termination")) continue
                            val deadline = timeoutNanos(command.opt("response_timeout"))?.let { receivedAt + it }
                            if (deadline != null && System.nanoTime() >= deadline) continue
                            val result = withContext(Dispatchers.Main.immediate) {
                                if (deadline != null && System.nanoTime() >= deadline) null
                                else when (command.optString("command_type", "jsonrpc")) {
                                    "jsonrpc" -> AutomationGate.execute { executor.rpc(command.getJSONObject("jsonrpc")) }
                                    "session_termination" -> null
                                    else -> null
                                }
                            }
                            if (deadline != null && System.nanoTime() >= deadline) continue
                            val type = if (command.optString("command_type") == "session_termination") "session_termination_response" else if (result == null) "notify_ack" else "jsonrpc_response"
                            val response = JSONObject().put("request_id", command.getString("request_id"))
                                .put("channel", command.optString("channel", "main")).put("resp_code", if (result == null) 204 else 200).put("resp_type", type)
                            if (result != null) response.put("resp_json", result)
                            // A failed POST never re-executes the operation. Mutation retries use persistent batch receipts.
                            request(tunnel, key, "response", response, command.getString("shard_token"))
                            ChatGPTConnection.report("已响应 ChatGPT 调用")
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        failures++
                        ChatGPTConnection.report("连接失败或中断 · 将重试；请检查网络、Tunnel ID 与权限")
                        delay(minOf(60000L, 2000L * failures))
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { ChatGPTConnection.report("无法启动：请检查安全连接配置") }
            finally { stopSelf() }
        }
        return START_NOT_STICKY
    }
    private fun request(tunnel: String, key: String, path: String, body: JSONObject? = null, shard: String? = null): JSONObject? {
        require(Regex("tunnel_(?:[a-z0-9]{4}_)?[a-z0-9]{32}").matches(tunnel))
        val connection = URL("https://api.openai.com/v1/tunnels/$tunnel/$path").openConnection() as HttpsURLConnection
        active = connection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("X-Tunnel-Client-Instance-Id", instanceId)
            connection.setRequestProperty("X-Tunnel-Client-Name", "classflow-android")
            connection.setRequestProperty("X-Tunnel-Client-Version", "1.2.0-android.1")
            connection.setRequestProperty("X-Tunnel-MCP-Server-Info", "{\"version\":1,\"channels\":[{\"name\":\"main\",\"proc_affinity\":true}]}")
            if (shard != null) connection.setRequestProperty("X-Tunnel-Shard-Token", shard)
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            check(code in 200..299)
            if (code == 204) return null
            val bytes = connection.inputStream.use { it.readBytesBounded() }
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect(); active = null }
    }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val count = read(buffer); if (count < 0) break; require(out.size() + count <= 8 * 1024 * 1024); out.write(buffer, 0, count) }
        return out.toByteArray()
    }
    override fun onDestroy() {
        active?.disconnect(); scope.cancel(); ChatGPTConnection.report("已停止"); super.onDestroy()
    }
    companion object {
        fun timeoutNanos(value: Any?): Long? {
            if (value !is String) return null
            val match = Regex("([0-9]+)(ns|us|ms|s|m|h)").matchEntire(value) ?: return null
            val factor = mapOf("ns" to 1L, "us" to 1000L, "ms" to 1000000L, "s" to 1000000000L, "m" to 60000000000L, "h" to 3600000000000L).getValue(match.groupValues[2])
            return runCatching { Math.multiplyExact(match.groupValues[1].toLong(), factor) }.getOrNull()
        }
    }
}
