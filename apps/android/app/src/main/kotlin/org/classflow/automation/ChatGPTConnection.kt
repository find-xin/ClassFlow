package org.classflow.automation

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ChatGPTConnection {
    private val mutableStatus = MutableStateFlow("未连接")
    val status = mutableStatus.asStateFlow()
    fun report(value: String) { mutableStatus.value = value }
    private fun prefs(context: Context) = EncryptedSharedPreferences.create(context, "classflow_chatgpt_private",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun load(context: Context): Pair<String, String> = prefs(context).let {
        it.getString("tunnel", "")!! to it.getString("key", "")!!
    }
    fun save(context: Context, tunnel: String, key: String) {
        require(Regex("tunnel_(?:[a-z0-9]{4}_)?[a-z0-9]{32}").matches(tunnel)) { "Tunnel ID 格式无效" }
        require(key.isNotBlank()) { "请输入专用运行密钥" }
        check(prefs(context).edit().putString("tunnel", tunnel).putString("key", key).commit()) { "安全保存失败" }
    }
}
