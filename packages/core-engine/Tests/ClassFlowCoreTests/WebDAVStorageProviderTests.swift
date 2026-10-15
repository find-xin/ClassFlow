import Foundation
import Testing
@testable import ClassFlowCore

@Suite("WebDAVStorageProviderTests")
struct WebDAVStorageProviderTests {

    @Test("Validate configuration with various URLs and credentials")
    func testConfigurationValidation() {
        // Missing URL
        let invalid1 = WebDAVStorageProvider.Configuration(serverURL: "", username: "user", password: "pwd")
        let provider1 = WebDAVStorageProvider(config: invalid1)
        #expect(!provider1.validateConfiguration().isValid)

        // Invalid scheme
        let invalid2 = WebDAVStorageProvider.Configuration(serverURL: "ftp://example.com/dav/", username: "user", password: "pwd")
        let provider2 = WebDAVStorageProvider(config: invalid2)
        #expect(!provider2.validateConfiguration().isValid)

        // Missing username
        let invalid3 = WebDAVStorageProvider.Configuration(serverURL: "https://dav.jianguoyun.com/dav/", username: "", password: "pwd")
        let provider3 = WebDAVStorageProvider(config: invalid3)
        #expect(!provider3.validateConfiguration().isValid)

        // Valid configuration
        let valid = WebDAVStorageProvider.Configuration(
            serverURL: "https://dav.jianguoyun.com/dav/",
            username: "student@university.edu.cn",
            password: "application-password-1234",
            pathPrefix: "ClassFlow/"
        )
        let providerValid = WebDAVStorageProvider(config: valid)
        let res = providerValid.validateConfiguration()
        #expect(res.isValid)
        #expect(res.errorMessages.isEmpty)
    }

    @Test("Test Mock URLSession WebDAV CRUD operations")
    func testMockURLSessionWebDAVOperations() async throws {
        // Setup mock URLProtocol
        MockURLProtocol.reset()

        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: config)

        let providerConfig = WebDAVStorageProvider.Configuration(
            serverURL: "https://dav.example.com/dav/",
            username: "alice",
            password: "secretpassword",
            pathPrefix: "ClassFlow/"
        )
        let provider = WebDAVStorageProvider(config: providerConfig, session: session)

        // 1. Test testConnection()
        MockURLProtocol.requestHandler = { request in
            #expect(request.httpMethod == "PROPFIND")
            #expect(request.value(forHTTPHeaderField: "Depth") == "0")
            let auth = request.value(forHTTPHeaderField: "Authorization") ?? ""
            #expect(auth.starts(with: "Basic "))

            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 207,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, Data())
        }

        let health = try await provider.testConnection()
        #expect(health.isReachable)
        #expect(health.bucketAccessible)

        // 2. Test writeObject() PUT
        let testPayload = "hello world classflow".data(using: .utf8)!
        MockURLProtocol.requestHandler = { request in
            #expect(request.httpMethod == "PUT")
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 201,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, Data())
        }

        try await provider.writeObject(path: "manifest.json", data: testPayload, metadata: nil)

        // 3. Test readObject() GET
        MockURLProtocol.requestHandler = { request in
            #expect(request.httpMethod == "GET")
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, testPayload)
        }

        let downloaded = try await provider.readObject(path: "manifest.json")
        #expect(downloaded == testPayload)

        // 4. Test listObjects() PROPFIND
        let propfindXML = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:">
            <D:response>
                <D:href>/dav/ClassFlow/</D:href>
                <D:propstat>
                    <D:prop>
                        <D:resourcetype><D:collection/></D:resourcetype>
                    </D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
            </D:response>
            <D:response>
                <D:href>/dav/ClassFlow/manifest.json</D:href>
                <D:propstat>
                    <D:prop>
                        <D:getcontentlength>1280</D:getcontentlength>
                        <D:getlastmodified>Wed, 14 Oct 2026 12:00:00 GMT</D:getlastmodified>
                        <D:getetag>"abcd1234efgh"</D:getetag>
                    </D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
            </D:response>
        </D:multistatus>
        """

        MockURLProtocol.requestHandler = { request in
            #expect(request.httpMethod == "PROPFIND")
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 207,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, propfindXML.data(using: .utf8)!)
        }

        let objects = try await provider.listObjects(prefix: "ClassFlow/")
        #expect(objects.count == 1)
        #expect(objects.first?.size == 1280)
        #expect(objects.first?.etag == "\"abcd1234efgh\"")
        #expect(objects.first?.key.contains("manifest.json") == true)
    }
}

// MARK: - Mock URL Protocol for WebDAV Tests
private final class MockURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    static func reset() {
        requestHandler = nil
    }

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let handler = MockURLProtocol.requestHandler else {
            client?.urlProtocol(self, didFailWithError: NSError(domain: "MockURLProtocol", code: 500))
            return
        }

        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}
