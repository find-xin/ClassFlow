package org.classflow.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android S3-compatible Storage Provider supporting AWS Signature Version 4 (SigV4).
 * Fully compatible with Qiniu Kodo S3, AWS S3, Cloudflare R2, MinIO, and Aliyun OSS S3 APIs.
 * Matches packages/core-engine/Sources/ClassFlowCore/Sync/S3StorageProvider.swift.
 */
class S3StorageProvider(
    private val endpoint: String,
    private val region: String = "cn-east-1",
    private val bucket: String,
    private val accessKey: String,
    private val secretKey: String,
    pathPrefix: String = "classflow-sync/"
) {
    private val normalizedEndpoint = run {
        var ep = endpoint.trim().trimEnd('/')
        if (ep.isNotEmpty() && !ep.startsWith("http://") && !ep.startsWith("https://")) {
            ep = "https://$ep"
        }
        ep
    }
    private val normalizedPrefix = run {
        var p = pathPrefix.trim().trimStart('/')
        if (p.isNotEmpty() && !p.endsWith("/")) {
            p = "$p/"
        }
        if (p.isEmpty()) "classflow-sync/" else p
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = sha256(bytes)
        val sb = StringBuilder()
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val signingKey = SecretKeySpec(key, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(signingKey)
        return mac.doFinal(data)
    }

    private fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
        val raw = hmacSha256(key, data)
        val sb = StringBuilder()
        for (b in raw) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun rfc3986Encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private data class SigV4Result(
        val authorizationHeader: String,
        val amzDate: String,
        val payloadHash: String
    )

    private fun signRequest(
        method: String,
        url: URL,
        headers: Map<String, String>,
        payload: ByteArray?,
        requestDate: Date = Date()
    ): SigV4Result {
        val dfTime = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dfDate = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val amzDate = dfTime.format(requestDate)
        val dateStamp = dfDate.format(requestDate)

        val payloadBytes = payload ?: ByteArray(0)
        val payloadHash = sha256Hex(payloadBytes)

        val allHeaders = headers.toMutableMap()
        allHeaders["x-amz-date"] = amzDate
        allHeaders["x-amz-content-sha256"] = payloadHash
        if (!allHeaders.containsKey("host")) {
            allHeaders["host"] = url.host
        }

        val sortedKeys = allHeaders.keys.map { it.lowercase() }.sorted()
        val canonicalHeaders = sortedKeys.joinToString("") { key ->
            val value = allHeaders.entries.first { it.key.lowercase() == key }.value.trim()
            "$key:$value\n"
        }
        val signedHeaders = sortedKeys.joinToString(";")

        val canonicalUri = if (url.path.isNullOrEmpty()) "/" else url.path
        val canonicalQuery = if (url.query.isNullOrEmpty()) {
            ""
        } else {
            url.query.split("&")
                .filter { it.isNotEmpty() }
                .map { param ->
                    val eqIdx = param.indexOf('=')
                    val rawKey = if (eqIdx >= 0) param.substring(0, eqIdx) else param
                    val rawVal = if (eqIdx >= 0) param.substring(eqIdx + 1) else ""
                    val decodedKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name())
                    val decodedVal = URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
                    rfc3986Encode(decodedKey) to rfc3986Encode(decodedVal)
                }
                .sortedBy { it.first }
                .joinToString("&") { "${it.first}=${it.second}" }
        }

        val canonicalRequest = """
            |$method
            |$canonicalUri
            |$canonicalQuery
            |$canonicalHeaders
            |$signedHeaders
            |$payloadHash
        """.trimMargin()

        val canonicalRequestHash = sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
        val credentialScope = "$dateStamp/$region/s3/aws4_request"
        val stringToSign = """
            |AWS4-HMAC-SHA256
            |$amzDate
            |$credentialScope
            |$canonicalRequestHash
        """.trimMargin()

        val kSecret = ("AWS4$secretKey").toByteArray(StandardCharsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp.toByteArray(StandardCharsets.UTF_8))
        val kRegion = hmacSha256(kDate, region.toByteArray(StandardCharsets.UTF_8))
        val kService = hmacSha256(kRegion, "s3".toByteArray(StandardCharsets.UTF_8))
        val kSigning = hmacSha256(kService, "aws4_request".toByteArray(StandardCharsets.UTF_8))

        val signature = hmacSha256Hex(kSigning, stringToSign.toByteArray(StandardCharsets.UTF_8))
        val authHeader = "AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        return SigV4Result(
            authorizationHeader = authHeader,
            amzDate = amzDate,
            payloadHash = payloadHash
        )
    }

    private fun resolveObjectUrl(relativePath: String): URL {
        val cleanKey = relativePath.trimStart('/')
        val fullKey = if (cleanKey.startsWith(normalizedPrefix)) cleanKey else "$normalizedPrefix$cleanKey"
        return URL("$normalizedEndpoint/$bucket/$fullKey")
    }

    /**
     * Tests connectivity to the configured S3 / Qiniu Kodo endpoint.
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (bucket.isBlank()) return@withContext Result.failure(Exception("存储桶 Bucket 不能为空"))
            if (accessKey.isBlank() || secretKey.isBlank()) return@withContext Result.failure(Exception("AK/SK 不能为空"))

            val listResult = listObjects("")
            if (listResult.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(listResult.exceptionOrNull() ?: Exception("云存储连接失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads object bytes from the S3 bucket.
     */
    suspend fun readObject(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = resolveObjectUrl(path)
            val sig = signRequest("GET", url, mapOf("host" to url.host), null)

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Host", url.host)
            conn.setRequestProperty("x-amz-date", sig.amzDate)
            conn.setRequestProperty("x-amz-content-sha256", sig.payloadHash)
            conn.setRequestProperty("Authorization", sig.authorizationHeader)
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                Result.success(bytes)
            } else {
                conn.disconnect()
                Result.failure(Exception("读取对象失败 HTTP $code: $path"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Writes object bytes to the S3 bucket.
     */
    suspend fun writeObject(path: String, data: ByteArray, contentType: String = "application/json"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = resolveObjectUrl(path)
            val headers = mapOf(
                "host" to url.host,
                "content-type" to contentType
            )
            val sig = signRequest("PUT", url, headers, data)

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Host", url.host)
            conn.setRequestProperty("Content-Type", contentType)
            conn.setRequestProperty("x-amz-date", sig.amzDate)
            conn.setRequestProperty("x-amz-content-sha256", sig.payloadHash)
            conn.setRequestProperty("Authorization", sig.authorizationHeader)
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            conn.outputStream.use { os ->
                os.write(data)
                os.flush()
            }

            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception("写入对象失败 HTTP $code: $path"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lists object keys under a given prefix.
     */
    suspend fun listObjects(prefix: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val cleanPrefix = prefix.trimStart('/')
            val fullPrefix = "$normalizedPrefix$cleanPrefix"
            val encodedPrefix = rfc3986Encode(fullPrefix)
            val url = URL("$normalizedEndpoint/$bucket?list-type=2&prefix=$encodedPrefix")

            val sig = signRequest("GET", url, mapOf("host" to url.host), null)

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Host", url.host)
            conn.setRequestProperty("x-amz-date", sig.amzDate)
            conn.setRequestProperty("x-amz-content-sha256", sig.payloadHash)
            conn.setRequestProperty("Authorization", sig.authorizationHeader)
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code in 200..299) {
                val xml = conn.inputStream.use { String(it.readBytes(), StandardCharsets.UTF_8) }
                conn.disconnect()

                // Parse <Key>...</Key>
                val keys = mutableListOf<String>()
                val regex = Regex("<Key>([^<]+)</Key>")
                regex.findAll(xml).forEach { match ->
                    val fullKey = match.groupValues[1]
                    val relativeKey = if (fullKey.startsWith(normalizedPrefix)) {
                        fullKey.removePrefix(normalizedPrefix)
                    } else fullKey
                    keys.add(relativeKey)
                }
                Result.success(keys)
            } else {
                val errorBody = try {
                    conn.errorStream?.use { String(it.readBytes(), StandardCharsets.UTF_8) }
                } catch (_: Exception) { null }
                conn.disconnect()
                val msg = when (code) {
                    403 -> "鉴权或区域不匹配 (HTTP 403)：请检查 AccessKey/SecretKey，且确保 Endpoint 与 Region 和存储桶实际区域一致（如华南-广东须填 cn-south-1）"
                    404 -> "存储桶不存在 (HTTP 404)：请检查存储桶 Bucket 名称是否与云平台完全一致"
                    else -> "列出对象失败 HTTP $code" + (if (!errorBody.isNullOrBlank()) ": $errorBody" else "")
                }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
