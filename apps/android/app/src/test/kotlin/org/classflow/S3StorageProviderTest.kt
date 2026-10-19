package org.classflow

import org.classflow.sync.S3StorageProvider
import org.junit.Assert.*
import org.junit.Test
import java.net.URL
import java.util.Date

class S3StorageProviderTest {

    @Test
    fun testSigV4CanonicalQueryEncoding() {
        val provider = S3StorageProvider(
            endpoint = "https://s3.cn-south-1.qiniucs.com",
            region = "cn-south-1",
            bucket = "classflow-sync",
            accessKey = "test-access-key",
            secretKey = "test-secret-key",
            pathPrefix = "classflow-sync/"
        )

        // Use reflection to test private signRequest method
        val signRequestMethod = S3StorageProvider::class.java.getDeclaredMethod(
            "signRequest",
            String::class.java,
            URL::class.java,
            Map::class.java,
            ByteArray::class.java,
            Date::class.java
        )
        signRequestMethod.isAccessible = true

        val testUrl = URL("https://s3.cn-south-1.qiniucs.com/classflow-sync?list-type=2&prefix=classflow-sync%2F")
        val fixedDate = Date(1773000000000L) // Fixed timestamp for deterministic testing
        val headers = mapOf("host" to "s3.cn-south-1.qiniucs.com")

        val result = signRequestMethod.invoke(provider, "GET", testUrl, headers, null, fixedDate)
        assertNotNull(result)

        val authHeaderField = result.javaClass.getDeclaredField("authorizationHeader")
        authHeaderField.isAccessible = true
        val authHeader = authHeaderField.get(result) as String

        assertTrue("Auth header must use AWS4-HMAC-SHA256", authHeader.startsWith("AWS4-HMAC-SHA256 Credential=test-access-key/"))
        assertTrue("Auth header must contain SignedHeaders", authHeader.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"))
        assertTrue("Auth header must contain Signature", authHeader.contains("Signature="))
    }
}
