import Foundation

/// S3-compatible StorageProvider communicating with Qiniu Kodo or standard S3 buckets via AWS SigV4.
public final class S3StorageProvider: StorageProvider, @unchecked Sendable {
    public struct Configuration: Sendable, Codable, Equatable {
        public var endpoint: String    // e.g. "https://s3.cn-east-1.qiniucs.com"
        public var region: String      // e.g. "cn-east-1"
        public var bucket: String      // e.g. "my-classflow-bucket"
        public var accessKey: String
        public var secretKey: String
        public var pathPrefix: String  // e.g. "classflow-sync/"

        public init(
            endpoint: String = "https://s3.cn-east-1.qiniucs.com",
            region: String = "cn-east-1",
            bucket: String = "",
            accessKey: String = "",
            secretKey: String = "",
            pathPrefix: String = "classflow-sync/"
        ) {
            var ep = endpoint.trimmingCharacters(in: .whitespacesAndNewlines)
            while ep.hasSuffix("/") {
                ep.removeLast()
            }
            if !ep.isEmpty && !ep.contains("://") {
                ep = "https://" + ep
            }
            self.endpoint = ep
            self.region = region.trimmingCharacters(in: .whitespacesAndNewlines)
            self.bucket = bucket.trimmingCharacters(in: .whitespacesAndNewlines)
            self.accessKey = accessKey.trimmingCharacters(in: .whitespacesAndNewlines)
            self.secretKey = secretKey.trimmingCharacters(in: .whitespacesAndNewlines)
            var prefix = pathPrefix.trimmingCharacters(in: .whitespacesAndNewlines)
            while prefix.hasPrefix("/") {
                prefix.removeFirst()
            }
            if !prefix.isEmpty && !prefix.hasSuffix("/") {
                prefix += "/"
            }
            self.pathPrefix = prefix.isEmpty ? "classflow-sync/" : prefix
        }
    }

    public let config: Configuration
    private let signer: S3SignatureV4
    private let session: URLSession

    public static func makeDefaultSession() -> URLSession {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 30
        return URLSession(configuration: configuration, delegate: BYOCSessionDelegate(), delegateQueue: nil)
    }

    public init(config: Configuration, session: URLSession? = nil) {
        self.config = config
        self.signer = S3SignatureV4(
            accessKey: config.accessKey,
            secretKey: config.secretKey,
            region: config.region,
            service: "s3"
        )
        self.session = session ?? Self.makeDefaultSession()
    }

    public func validateConfiguration() -> ConfigurationValidationResult {
        var errors: [String] = []
        if config.endpoint.isEmpty || URL(string: config.endpoint) == nil {
            errors.append("无效的云存储 Endpoint 地址")
        }
        if config.bucket.trimmingCharacters(in: .whitespaces).isEmpty {
            errors.append("存储桶 Bucket 名称不能为空")
        }
        if config.accessKey.trimmingCharacters(in: .whitespaces).isEmpty {
            errors.append("Access Key (AK) 不能为空")
        }
        if config.secretKey.trimmingCharacters(in: .whitespaces).isEmpty {
            errors.append("Secret Key (SK) 不能为空")
        }
        return errors.isEmpty ? .valid : .invalid(errors)
    }

    public func testConnection() async throws -> ConnectionHealth {
        let validation = validateConfiguration()
        guard validation.isValid else {
            return ConnectionHealth(
                isReachable: false,
                latencyMs: 0,
                bucketAccessible: false,
                message: validation.errorMessages.joined(separator: "; ")
            )
        }

        let start = Date()
        do {
            // Probe by listing objects with prefix limit
            _ = try await listObjects(prefix: config.pathPrefix)
            let elapsed = Date().timeIntervalSince(start) * 1000
            return ConnectionHealth(
                isReachable: true,
                latencyMs: elapsed,
                bucketAccessible: true,
                message: "连接成功 (延迟: \(Int(elapsed))ms)"
            )
        } catch {
            let elapsed = Date().timeIntervalSince(start) * 1000
            return ConnectionHealth(
                isReachable: false,
                latencyMs: elapsed,
                bucketAccessible: false,
                message: "连接失败: \(error.localizedDescription)"
            )
        }
    }

    public func readObject(path: String) async throws -> Data {
        let fullPath = sanitizeKey(path)
        let url = makeURL(for: fullPath)
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let host = url.host {
            request.setValue(host, forHTTPHeaderField: "Host")
        }

        let sig = signer.signRequest(
            method: "GET",
            url: url,
            headers: ["host": url.host ?? ""],
            payload: nil
        )
        request.setValue(sig.authorizationHeader, forHTTPHeaderField: "Authorization")
        request.setValue(sig.dateHeader, forHTTPHeaderField: "x-amz-date")
        request.setValue(sig.payloadHashHeader, forHTTPHeaderField: "x-amz-content-sha256")

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 500
            throw NSError(domain: "S3StorageProvider", code: status, userInfo: [
                NSLocalizedDescriptionKey: "Failed to read S3 object at \(fullPath), HTTP status: \(status)"
            ])
        }
        return data
    }

    public func writeObject(path: String, data: Data, metadata: [String: String]? = nil) async throws {
        let fullPath = sanitizeKey(path)
        let url = makeURL(for: fullPath)
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.httpBody = data
        if let host = url.host {
            request.setValue(host, forHTTPHeaderField: "Host")
        }

        var headers: [String: String] = ["host": url.host ?? ""]
        if let metadata {
            for (k, v) in metadata {
                headers["x-amz-meta-\(k.lowercased())"] = v
            }
        }

        let sig = signer.signRequest(
            method: "PUT",
            url: url,
            headers: headers,
            payload: data
        )
        request.setValue(sig.authorizationHeader, forHTTPHeaderField: "Authorization")
        request.setValue(sig.dateHeader, forHTTPHeaderField: "x-amz-date")
        request.setValue(sig.payloadHashHeader, forHTTPHeaderField: "x-amz-content-sha256")
        for (k, v) in headers where k.hasPrefix("x-amz-meta-") {
            request.setValue(v, forHTTPHeaderField: k)
        }

        let (_, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 500
            throw NSError(domain: "S3StorageProvider", code: status, userInfo: [
                NSLocalizedDescriptionKey: "Failed to write S3 object to \(fullPath), HTTP status: \(status)"
            ])
        }
    }

    public func deleteObject(path: String) async throws {
        let fullPath = sanitizeKey(path)
        let url = makeURL(for: fullPath)
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        if let host = url.host {
            request.setValue(host, forHTTPHeaderField: "Host")
        }

        let sig = signer.signRequest(
            method: "DELETE",
            url: url,
            headers: ["host": url.host ?? ""],
            payload: nil
        )
        request.setValue(sig.authorizationHeader, forHTTPHeaderField: "Authorization")
        request.setValue(sig.dateHeader, forHTTPHeaderField: "x-amz-date")
        request.setValue(sig.payloadHashHeader, forHTTPHeaderField: "x-amz-content-sha256")

        let (_, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) || http.statusCode == 404 else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 500
            throw NSError(domain: "S3StorageProvider", code: status, userInfo: [
                NSLocalizedDescriptionKey: "Failed to delete S3 object at \(fullPath), HTTP status: \(status)"
            ])
        }
    }

    public func listObjects(prefix: String) async throws -> [StorageObjectSummary] {
        let fullPrefix = sanitizeKey(prefix)
        guard URL(string: config.endpoint) != nil else {
            throw NSError(domain: "S3StorageProvider", code: 400, userInfo: [
                NSLocalizedDescriptionKey: "无效的 Endpoint 地址: \(config.endpoint)"
            ])
        }
        let encodedPrefix = S3SignatureV4.uriEncode(fullPrefix)
        let endpointClean = config.endpoint.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url = URL(string: "\(endpointClean)/\(config.bucket)?list-type=2&prefix=\(encodedPrefix)") else {
            throw NSError(domain: "S3StorageProvider", code: 400, userInfo: [
                NSLocalizedDescriptionKey: "无法构建 S3 查询 URL"
            ])
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let host = url.host {
            request.setValue(host, forHTTPHeaderField: "Host")
        }

        let sig = signer.signRequest(
            method: "GET",
            url: url,
            headers: ["host": url.host ?? ""],
            payload: nil
        )
        request.setValue(sig.authorizationHeader, forHTTPHeaderField: "Authorization")
        request.setValue(sig.dateHeader, forHTTPHeaderField: "x-amz-date")
        request.setValue(sig.payloadHashHeader, forHTTPHeaderField: "x-amz-content-sha256")

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 500
            let errorMsg: String
            if status == 403 {
                errorMsg = "鉴权或区域不匹配 (HTTP 403)：请检查 AccessKey/SecretKey，且确保 Endpoint 与 Region 和存储桶实际区域一致（如华南-广东须填 cn-south-1）"
            } else if status == 404 {
                errorMsg = "存储桶不存在 (HTTP 404)：请检查存储桶 Bucket 名称是否与云平台完全一致"
            } else {
                errorMsg = "连接云存储失败，HTTP 状态码: \(status)"
            }
            throw NSError(domain: "S3StorageProvider", code: status, userInfo: [
                NSLocalizedDescriptionKey: errorMsg
            ])
        }

        // Parse XML response
        return S3ListBucketParser.parse(xmlData: data)
    }

    public func getObjectMetadata(path: String) async throws -> StorageObjectMetadata {
        let fullPath = sanitizeKey(path)
        let url = makeURL(for: fullPath)
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        if let host = url.host {
            request.setValue(host, forHTTPHeaderField: "Host")
        }

        let sig = signer.signRequest(
            method: "HEAD",
            url: url,
            headers: ["host": url.host ?? ""],
            payload: nil
        )
        request.setValue(sig.authorizationHeader, forHTTPHeaderField: "Authorization")
        request.setValue(sig.dateHeader, forHTTPHeaderField: "x-amz-date")
        request.setValue(sig.payloadHashHeader, forHTTPHeaderField: "x-amz-content-sha256")

        let (_, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 500
            throw NSError(domain: "S3StorageProvider", code: status, userInfo: [
                NSLocalizedDescriptionKey: "Failed to head S3 object, HTTP status: \(status)"
            ])
        }

        let contentLength = http.expectedContentLength
        return StorageObjectMetadata(
            size: contentLength,
            lastModified: nil,
            contentType: http.mimeType
        )
    }

    private func sanitizeKey(_ key: String) -> String {
        let trimmed = key.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if trimmed.hasPrefix(config.pathPrefix) {
            return trimmed
        }
        return "\(config.pathPrefix)\(trimmed)"
    }

    private func makeURL(for sanitizedKey: String) -> URL {
        let base = URL(string: config.endpoint) ?? URL(string: "https://invalid-endpoint")!
        return base
            .appendingPathComponent(config.bucket)
            .appendingPathComponent(sanitizedKey)
    }
}

// MARK: - S3 ListBucket XML Parser
final class S3ListBucketParser: NSObject, XMLParserDelegate, @unchecked Sendable {
    private var summaries: [StorageObjectSummary] = []
    private var currentElement: String = ""
    private var currentKey: String = ""
    private var currentSize: Int64 = 0

    static func parse(xmlData: Data) -> [StorageObjectSummary] {
        let parser = S3ListBucketParser()
        let xml = XMLParser(data: xmlData)
        xml.delegate = parser
        xml.parse()
        return parser.summaries
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String : String] = [:]) {
        currentElement = elementName
        if elementName == "Contents" {
            currentKey = ""
            currentSize = 0
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        let str = string.trimmingCharacters(in: .whitespacesAndNewlines)
        if currentElement == "Key" {
            currentKey += str
        } else if currentElement == "Size" {
            currentSize = Int64(str) ?? currentSize
        }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if elementName == "Contents" && !currentKey.isEmpty {
            summaries.append(StorageObjectSummary(key: currentKey, size: currentSize))
        }
        currentElement = ""
    }
}

// MARK: - BYOC Session Delegate
/// Custom URLSessionDelegate that trusts server certificates for BYOC self-hosted storage
/// (e.g., self-signed MinIO, local NAS WebDAV, or corporate proxy/VPN networks).
public final class BYOCSessionDelegate: NSObject, URLSessionDelegate, Sendable {
    public override init() {
        super.init()
    }

    public func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping @Sendable (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
           let serverTrust = challenge.protectionSpace.serverTrust {
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }
}
