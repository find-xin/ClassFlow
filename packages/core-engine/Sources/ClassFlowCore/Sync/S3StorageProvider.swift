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
            self.endpoint = endpoint
            self.region = region
            self.bucket = bucket
            self.accessKey = accessKey
            self.secretKey = secretKey
            self.pathPrefix = pathPrefix.hasSuffix("/") ? pathPrefix : pathPrefix + "/"
        }
    }

    public let config: Configuration
    private let signer: S3SignatureV4
    private let session: URLSession

    public init(config: Configuration, session: URLSession = .shared) {
        self.config = config
        self.signer = S3SignatureV4(
            accessKey: config.accessKey,
            secretKey: config.secretKey,
            region: config.region,
            service: "s3"
        )
        self.session = session
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
        var components = URLComponents(url: URL(string: config.endpoint)!.appendingPathComponent(config.bucket), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "list-type", value: "2"),
            URLQueryItem(name: "prefix", value: fullPrefix)
        ]

        let url = components.url!
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

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
                NSLocalizedDescriptionKey: "Failed to list S3 objects, HTTP status: \(status)"
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
        URL(string: config.endpoint)!
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
