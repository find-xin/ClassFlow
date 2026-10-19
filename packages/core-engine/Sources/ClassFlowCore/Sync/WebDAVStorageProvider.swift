import Foundation

/// WebDAV RFC 4918 StorageProvider supporting mainstream personal cloud storage providers
/// such as Jianguoyun (坚果云), Nextcloud, ownCloud, and local NAS WebDAV servers.
/// Demystifies cloud synchronization by eliminating AK/SK key acquisition hurdles.
public final class WebDAVStorageProvider: StorageProvider, @unchecked Sendable {
    public struct Configuration: Sendable, Codable, Equatable {
        public var serverURL: String    // e.g. "https://dav.jianguoyun.com/dav/"
        public var username: String     // e.g. user@domain.com
        public var password: String     // e.g. application-specific password
        public var pathPrefix: String   // e.g. "ClassFlow/"

        public init(
            serverURL: String = "https://dav.jianguoyun.com/dav/",
            username: String = "",
            password: String = "",
            pathPrefix: String = "ClassFlow/"
        ) {
            var url = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
            if !url.isEmpty && !url.contains("://") {
                url = "https://" + url
            }
            if !url.isEmpty && !url.hasSuffix("/") {
                url += "/"
            }
            self.serverURL = url
            self.username = username.trimmingCharacters(in: .whitespacesAndNewlines)
            self.password = password.trimmingCharacters(in: .whitespacesAndNewlines)
            var prefix = pathPrefix.trimmingCharacters(in: .whitespacesAndNewlines)
            while prefix.hasPrefix("/") {
                prefix.removeFirst()
            }
            if !prefix.isEmpty && !prefix.hasSuffix("/") {
                prefix += "/"
            }
            self.pathPrefix = prefix.isEmpty ? "ClassFlow/" : prefix
        }
    }

    public let config: Configuration
    private let session: URLSession

    public static func makeDefaultSession() -> URLSession {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 30
        return URLSession(configuration: configuration, delegate: BYOCSessionDelegate(), delegateQueue: nil)
    }

    public init(config: Configuration, session: URLSession? = nil) {
        self.config = config
        self.session = session ?? Self.makeDefaultSession()
    }

    // MARK: - Validation & Connectivity

    public func validateConfiguration() -> ConfigurationValidationResult {
        var errors: [String] = []
        guard let url = URL(string: config.serverURL),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https" else {
            errors.append("无效的 WebDAV 服务器 URL (必须以 http:// 或 https:// 开头)")
            return .invalid(errors)
        }
        if config.username.trimmingCharacters(in: .whitespaces).isEmpty {
            errors.append("WebDAV 用户名/账号不能为空")
        }
        if config.password.trimmingCharacters(in: .whitespaces).isEmpty {
            errors.append("WebDAV 密码/应用授权密码不能为空")
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
            let fullURL = buildURL(for: config.pathPrefix)
            var request = URLRequest(url: fullURL)
            request.httpMethod = "PROPFIND"
            request.setValue("0", forHTTPHeaderField: "Depth")
            applyAuthorization(to: &request)

            let (_, response) = try await session.data(for: request)
            let elapsed = Date().timeIntervalSince(start) * 1000

            if let httpResp = response as? HTTPURLResponse {
                if (200...299).contains(httpResp.statusCode) || httpResp.statusCode == 207 {
                    return ConnectionHealth(
                        isReachable: true,
                        latencyMs: elapsed,
                        bucketAccessible: true,
                        message: "WebDAV 服务器连接成功，授权有效 (HTTP \(httpResp.statusCode))"
                    )
                } else if httpResp.statusCode == 404 {
                    // Try to create base folder
                    try await createDirectory(path: config.pathPrefix)
                    return ConnectionHealth(
                        isReachable: true,
                        latencyMs: elapsed,
                        bucketAccessible: true,
                        message: "WebDAV 服务器连接成功，已自动初始化数据目录"
                    )
                } else if httpResp.statusCode == 401 || httpResp.statusCode == 403 {
                    return ConnectionHealth(
                        isReachable: true,
                        latencyMs: elapsed,
                        bucketAccessible: false,
                        message: "WebDAV 账号或密码认证失败 (HTTP \(httpResp.statusCode))"
                    )
                } else {
                    return ConnectionHealth(
                        isReachable: true,
                        latencyMs: elapsed,
                        bucketAccessible: false,
                        message: "WebDAV 服务器响应异常 (HTTP \(httpResp.statusCode))"
                    )
                }
            }

            return ConnectionHealth(
                isReachable: false,
                latencyMs: elapsed,
                bucketAccessible: false,
                message: "未知的网络响应类型"
            )
        } catch {
            return ConnectionHealth(
                isReachable: false,
                latencyMs: 0,
                bucketAccessible: false,
                message: error.localizedDescription
            )
        }
    }

    // MARK: - CRUD Operations

    public func readObject(path: String) async throws -> Data {
        let fullURL = buildURL(for: path)
        var request = URLRequest(url: fullURL)
        request.httpMethod = "GET"
        applyAuthorization(to: &request)

        let (data, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse else {
            throw NSError(domain: "WebDAVStorageProvider", code: 500, userInfo: [NSLocalizedDescriptionKey: "无效的网络响应"])
        }
        guard (200...299).contains(httpResp.statusCode) else {
            throw NSError(
                domain: "WebDAVStorageProvider",
                code: httpResp.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "读取 WebDAV 对象失败: HTTP \(httpResp.statusCode)"]
            )
        }
        return data
    }

    public func writeObject(path: String, data: Data, metadata: [String: String]?) async throws {
        let fullURL = buildURL(for: path)
        var request = URLRequest(url: fullURL)
        request.httpMethod = "PUT"
        request.httpBody = data
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        applyAuthorization(to: &request)

        let (_, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse else {
            throw NSError(domain: "WebDAVStorageProvider", code: 500, userInfo: [NSLocalizedDescriptionKey: "无效的网络响应"])
        }

        // If 404 or 409 parent conflict, create parent folders recursively and retry
        if httpResp.statusCode == 404 || httpResp.statusCode == 409 {
            let parentDir = parentPath(of: path)
            if !parentDir.isEmpty {
                try await createDirectory(path: parentDir)
                // Retry PUT
                let (_, retryResp) = try await session.data(for: request)
                if let retryHttp = retryResp as? HTTPURLResponse, (200...299).contains(retryHttp.statusCode) {
                    return
                }
            }
        }

        guard (200...299).contains(httpResp.statusCode) || httpResp.statusCode == 201 || httpResp.statusCode == 204 else {
            throw NSError(
                domain: "WebDAVStorageProvider",
                code: httpResp.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "写入 WebDAV 对象失败: HTTP \(httpResp.statusCode)"]
            )
        }
    }

    public func deleteObject(path: String) async throws {
        let fullURL = buildURL(for: path)
        var request = URLRequest(url: fullURL)
        request.httpMethod = "DELETE"
        applyAuthorization(to: &request)

        let (_, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse else {
            throw NSError(domain: "WebDAVStorageProvider", code: 500, userInfo: [NSLocalizedDescriptionKey: "无效的网络响应"])
        }
        // 404 is considered successfully deleted / idempotent
        guard (200...299).contains(httpResp.statusCode) || httpResp.statusCode == 404 else {
            throw NSError(
                domain: "WebDAVStorageProvider",
                code: httpResp.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "删除 WebDAV 对象失败: HTTP \(httpResp.statusCode)"]
            )
        }
    }

    public func listObjects(prefix: String) async throws -> [StorageObjectSummary] {
        let fullURL = buildURL(for: prefix)
        var request = URLRequest(url: fullURL)
        request.httpMethod = "PROPFIND"
        request.setValue("1", forHTTPHeaderField: "Depth")
        request.setValue("application/xml; charset=utf-8", forHTTPHeaderField: "Content-Type")
        applyAuthorization(to: &request)

        let (data, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse else {
            throw NSError(domain: "WebDAVStorageProvider", code: 500, userInfo: [NSLocalizedDescriptionKey: "无效的网络响应"])
        }
        if httpResp.statusCode == 404 {
            return []
        }
        guard (200...299).contains(httpResp.statusCode) || httpResp.statusCode == 207 else {
            throw NSError(
                domain: "WebDAVStorageProvider",
                code: httpResp.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "列举 WebDAV 对象失败: HTTP \(httpResp.statusCode)"]
            )
        }

        guard let xmlString = String(data: data, encoding: .utf8) else {
            return []
        }

        return parseWebDAVPropfindXML(xmlString, basePath: prefix)
    }

    public func getObjectMetadata(path: String) async throws -> StorageObjectMetadata {
        let fullURL = buildURL(for: path)
        var request = URLRequest(url: fullURL)
        request.httpMethod = "HEAD"
        applyAuthorization(to: &request)

        let (_, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse else {
            throw NSError(domain: "WebDAVStorageProvider", code: 500, userInfo: [NSLocalizedDescriptionKey: "无效的网络响应"])
        }
        guard (200...299).contains(httpResp.statusCode) else {
            throw NSError(
                domain: "WebDAVStorageProvider",
                code: httpResp.statusCode,
                userInfo: [NSLocalizedDescriptionKey: "获取 WebDAV 元数据失败: HTTP \(httpResp.statusCode)"]
            )
        }

        let length = httpResp.expectedContentLength
        let contentType = httpResp.value(forHTTPHeaderField: "Content-Type")
        var lastModified: Date? = nil
        if let lmStr = httpResp.value(forHTTPHeaderField: "Last-Modified") {
            let df = DateFormatter()
            df.locale = Locale(identifier: "en_US_POSIX")
            df.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
            lastModified = df.date(from: lmStr)
        }

        return StorageObjectMetadata(
            size: max(0, length),
            lastModified: lastModified,
            contentType: contentType,
            customMetadata: [:]
        )
    }

    // MARK: - Directory Helper (MKCOL)

    public func createDirectory(path: String) async throws {
        let fullURL = buildURL(for: path)
        var request = URLRequest(url: fullURL)
        request.httpMethod = "MKCOL"
        applyAuthorization(to: &request)

        let (_, response) = try await session.data(for: request)
        if let httpResp = response as? HTTPURLResponse {
            // 201 Created, 405 Method Not Allowed (already exists) are ok
            if httpResp.statusCode == 201 || httpResp.statusCode == 405 {
                return
            }
        }
    }

    // MARK: - Internal Helpers

    private func applyAuthorization(to request: inout URLRequest) {
        let authString = "\(config.username):\(config.password)"
        guard let authData = authString.data(using: .utf8) else { return }
        let base64 = authData.base64EncodedString()
        request.setValue("Basic \(base64)", forHTTPHeaderField: "Authorization")
    }

    private func buildURL(for relativePath: String) -> URL {
        let cleanBase = config.serverURL.hasSuffix("/") ? config.serverURL : config.serverURL + "/"
        let cleanRel = relativePath.hasPrefix("/") ? String(relativePath.dropFirst()) : relativePath
        return URL(string: cleanBase + cleanRel) ?? URL(string: cleanBase)!
    }

    private func parentPath(of path: String) -> String {
        let components = path.split(separator: "/")
        guard components.count > 1 else { return "" }
        return components.dropLast().joined(separator: "/") + "/"
    }

    /// Fast regex-based XML parser for standard RFC 4918 multistatus responses.
    private func parseWebDAVPropfindXML(_ xml: String, basePath: String) -> [StorageObjectSummary] {
        var summaries: [StorageObjectSummary] = []

        // Split by <D:response> or <d:response> or <response>
        let responsePattern = "(?s)<(?:[a-zA-Z0-9_]+:)?response>(.*?)</(?:[a-zA-Z0-9_]+:)?response>"
        guard let regex = try? NSRegularExpression(pattern: responsePattern, options: .caseInsensitive) else {
            return []
        }

        let nsString = xml as NSString
        let matches = regex.matches(in: xml, options: [], range: NSRange(location: 0, length: nsString.length))

        for match in matches {
            guard match.numberOfRanges > 1 else { continue }
            let block = nsString.substring(with: match.range(at: 1))

            // Ignore collection / directory responses
            if block.contains("collection/>") || block.contains("<iscollection>1") {
                continue
            }

            // Extract href
            let href = extractXMLTag(tag: "href", from: block) ?? ""
            if href.isEmpty { continue }

            // Extract content length
            let lengthStr = extractXMLTag(tag: "getcontentlength", from: block) ?? "0"
            let size = Int64(lengthStr) ?? 0

            // Extract last modified
            var lastModified: Date? = nil
            if let lmStr = extractXMLTag(tag: "getlastmodified", from: block) {
                let df = DateFormatter()
                df.locale = Locale(identifier: "en_US_POSIX")
                df.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
                lastModified = df.date(from: lmStr)
            }

            // Clean key name relative to basePath
            let key = (URL(string: href)?.path ?? href).removingPercentEncoding ?? href

            summaries.append(StorageObjectSummary(
                key: key,
                size: size,
                lastModified: lastModified,
                etag: extractXMLTag(tag: "getetag", from: block)
            ))
        }

        return summaries
    }

    private func extractXMLTag(tag: String, from text: String) -> String? {
        let pattern = "<(?:[a-zA-Z0-9_]+:)?\(tag)[^>]*>(.*?)</(?:[a-zA-Z0-9_]+:)?\(tag)>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else {
            return nil
        }
        let ns = text as NSString
        if let match = regex.firstMatch(in: text, options: [], range: NSRange(location: 0, length: ns.length)), match.numberOfRanges > 1 {
            return ns.substring(with: match.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return nil
    }
}
