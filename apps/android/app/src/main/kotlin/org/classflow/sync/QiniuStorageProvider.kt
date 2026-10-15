package org.classflow.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android Qiniu Kodo & S3 Compatible Storage Provider.
 * Allows users to bring their own cloud storage (BYOC) without mandatory SaaS accounts.
 * Signs HTTP PUT/GET requests with standard HMAC-SHA1 Authorization headers.
 */
class QiniuStorageProvider(
    private val accessKey: String,
    private val secretKey: String,
    private val bucketName: String,
    private val endpointUrl: String = "https://s3.cn-east-1.qiniucs.com"
) {
    private val normalizedEndpoint = endpointUrl.trimEnd('/')

    private fun signHmacSha1(data: String, key: String): String {
        val signingKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(signingKey)
        val rawHmac = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(rawHmac)
    }

    private fun getRfc822Date(): String {
        val df = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        df.timeZone = TimeZone.getTimeZone("GMT")
        return df.format(Date())
    }

    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val dateStr = getRfc822Date()
            val canonicalResource = "/$bucketName/"
            val stringToSign = "GET\n\n\n$dateStr\n$canonicalResource"
            val signature = signHmacSha1(stringToSign, secretKey)
            val authHeader = "AWS $accessKey:$signature"

            val url = URL("$normalizedEndpoint/$bucketName?max-keys=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Date", dateStr)
            conn.setRequestProperty("Authorization", authHeader)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299) {
                Result.success(true)
            } else if (code == 403) {
                Result.failure(Exception("鉴权失败 (403)：AccessKey 或 SecretKey 错误"))
            } else {
                Result.failure(Exception("连接七牛云失败 HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(key: String, content: ByteArray): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanKey = key.trimStart('/')
            val dateStr = getRfc822Date()
            val contentType = "application/json"
            val canonicalResource = "/$bucketName/$cleanKey"
            val stringToSign = "PUT\n\n$contentType\n$dateStr\n$canonicalResource"
            val signature = signHmacSha1(stringToSign, secretKey)
            val authHeader = "AWS $accessKey:$signature"

            val url = URL("$normalizedEndpoint/$bucketName/$cleanKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Date", dateStr)
            conn.setRequestProperty("Content-Type", contentType)
            conn.setRequestProperty("Authorization", authHeader)
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            conn.outputStream.use { os ->
                os.write(content)
                os.flush()
            }

            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception("上传七牛云失败 HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(key: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val cleanKey = key.trimStart('/')
            val dateStr = getRfc822Date()
            val canonicalResource = "/$bucketName/$cleanKey"
            val stringToSign = "GET\n\n\n$dateStr\n$canonicalResource"
            val signature = signHmacSha1(stringToSign, secretKey)
            val authHeader = "AWS $accessKey:$signature"

            val url = URL("$normalizedEndpoint/$bucketName/$cleanKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Date", dateStr)
            conn.setRequestProperty("Authorization", authHeader)
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                Result.success(bytes)
            } else {
                conn.disconnect()
                Result.failure(Exception("下载七牛云文件失败 HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
