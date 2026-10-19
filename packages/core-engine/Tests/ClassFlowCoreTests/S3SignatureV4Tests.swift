import Foundation
import Testing
@testable import ClassFlowCore

@Suite("S3SignatureV4Tests")
struct S3SignatureV4Tests {
    let signer = S3SignatureV4(
        accessKey: "AK_TEST_KEY_12345",
        secretKey: "SK_TEST_SECRET_67890",
        region: "cn-east-1",
        service: "s3"
    )

    @Test("Sign standard S3 GET request and format Authorization header")
    func testSignGetRequest() {
        let url = URL(string: "https://s3.cn-east-1.qiniucs.com/my-bucket/test.json")!
        let result = signer.signRequest(
            method: "GET",
            url: url,
            headers: ["host": "s3.cn-east-1.qiniucs.com"],
            payload: nil,
            requestDate: Date(timeIntervalSince1970: 1760400000)
        )

        #expect(result.authorizationHeader.hasPrefix("AWS4-HMAC-SHA256 Credential=AK_TEST_KEY_12345/"))
        #expect(result.authorizationHeader.contains("/cn-east-1/s3/aws4_request"))
        #expect(result.authorizationHeader.contains("SignedHeaders="))
        #expect(result.authorizationHeader.contains("Signature="))
        #expect(!result.dateHeader.isEmpty)
        #expect(!result.payloadHashHeader.isEmpty)
    }

    @Test("Sign PUT request with payload content hash")
    func testSignPutRequestWithPayload() {
        let url = URL(string: "https://s3.cn-east-1.qiniucs.com/my-bucket/data.bin")!
        let payload = "ClassFlow Data Payload".data(using: .utf8)!
        let result = signer.signRequest(
            method: "PUT",
            url: url,
            headers: ["host": "s3.cn-east-1.qiniucs.com"],
            payload: payload
        )

        #expect(result.payloadHashHeader != "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        #expect(result.authorizationHeader.contains("x-amz-content-sha256"))
    }

    @Test("Normalize endpoint trailing slash and protocol scheme")
    func testEndpointNormalization() {
        let configWithSlash = S3StorageProvider.Configuration(
            endpoint: "http://s3.cn-south-1.qiniucs.com/",
            region: " cn-south-1 ",
            bucket: " classflow-sync ",
            accessKey: " ak_test ",
            secretKey: " sk_test ",
            pathPrefix: "/classflow-sync/"
        )
        #expect(configWithSlash.endpoint == "http://s3.cn-south-1.qiniucs.com")
        #expect(configWithSlash.region == "cn-south-1")
        #expect(configWithSlash.bucket == "classflow-sync")
        #expect(configWithSlash.accessKey == "ak_test")
        #expect(configWithSlash.secretKey == "sk_test")
        #expect(configWithSlash.pathPrefix == "classflow-sync/")

        let configNoScheme = S3StorageProvider.Configuration(
            endpoint: "s3.cn-south-1.qiniucs.com",
            pathPrefix: "sync-data"
        )
        #expect(configNoScheme.endpoint == "https://s3.cn-south-1.qiniucs.com")
        #expect(configNoScheme.pathPrefix == "sync-data/")
    }
}
