package org.classflow.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Native Android WebDAV Storage Provider.
 * Fully compatible with Jianguoyun (坚果云 https://dav.jianguoyun.com/dav/) and standard RFC 4918 WebDAV servers.
 * Implements device-isolated multi-device synchronization without mandatory official cloud accounts.
 */
class WebDAVStorageProvider(
    private val serverUrl: String,
    private val username: String,
    private val appPassword: String,
    private val rootDirectory: String = "ClassFlow"
) {
    private val normalizedServerUrl: String = serverUrl.trimEnd('/')
    private val normalizedRootDir: String = rootDirectory.trim('/')

    private fun getBasicAuthHeader(): String {
        val credentials = "$username:$appPassword"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
    }

    private fun resolveUrl(relativePath: String): URL {
        val cleanPath = relativePath.trimStart('/')
        val fullPath = if (normalizedRootDir.isEmpty()) {
            "$normalizedServerUrl/$cleanPath"
        } else {
            "$normalizedServerUrl/$normalizedRootDir/$cleanPath"
        }
        return URL(fullPath)
    }

    /**
     * Diagnoses connection to the WebDAV server by executing a depth-0 PROPFIND on the root.
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = resolveUrl("")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PROPFIND"
            conn.setRequestProperty("Authorization", getBasicAuthHeader())
            conn.setRequestProperty("Depth", "0")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299 || code == 404) {
                // If 404, directory might not exist yet, which is fine
                Result.success(true)
            } else if (code == 401) {
                Result.failure(Exception("认证失败 (401)：用户名或坚果云应用密码错误"))
            } else {
                Result.failure(Exception("连接失败，服务器返回 HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a directory (MKCOL) if it does not exist.
     */
    suspend fun createDirectory(relativePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = resolveUrl(relativePath)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "MKCOL"
            conn.setRequestProperty("Authorization", getBasicAuthHeader())
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299 || code == 405) { // 405 Method Not Allowed typically means already exists
                Result.success(true)
            } else {
                Result.failure(Exception("创建目录失败 HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads file contents (PUT) to the WebDAV destination.
     */
    suspend fun uploadFile(relativePath: String, content: ByteArray): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Ensure parent directory exists
            val parentPath = relativePath.substringBeforeLast('/', "")
            if (parentPath.isNotEmpty()) {
                createDirectory(parentPath)
            }

            val url = resolveUrl(relativePath)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", getBasicAuthHeader())
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            conn.outputStream.use { os ->
                os.write(content)
                os.flush()
            }

            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299 || code == 201 || code == 204) {
                Result.success(true)
            } else {
                Result.failure(Exception("上传失败 HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads file content (GET) from the WebDAV destination.
     */
    suspend fun downloadFile(relativePath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = resolveUrl(relativePath)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", getBasicAuthHeader())
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                Result.success(bytes)
            } else {
                conn.disconnect()
                Result.failure(Exception("下载文件失败 HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
