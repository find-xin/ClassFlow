import Foundation

/// Metadata summary of an object stored in the BYOC bucket.
public struct StorageObjectSummary: Codable, Sendable, Equatable, Hashable {
    public var key: String
    public var size: Int64
    public var lastModified: Date?
    public var etag: String?

    public init(key: String, size: Int64, lastModified: Date? = nil, etag: String? = nil) {
        self.key = key
        self.size = size
        self.lastModified = lastModified
        self.etag = etag
    }
}

/// Detailed metadata of a specific stored object.
public struct StorageObjectMetadata: Codable, Sendable, Equatable {
    public var size: Int64
    public var lastModified: Date?
    public var contentType: String?
    public var customMetadata: [String: String]

    public init(
        size: Int64,
        lastModified: Date? = nil,
        contentType: String? = nil,
        customMetadata: [String: String] = [:]
    ) {
        self.size = size
        self.lastModified = lastModified
        self.contentType = contentType
        self.customMetadata = customMetadata
    }
}

/// Result of local configuration validation (syntactic checking of endpoint, bucket, keys).
public struct ConfigurationValidationResult: Sendable, Equatable {
    public var isValid: Bool
    public var errorMessages: [String]

    public init(isValid: Bool, errorMessages: [String] = []) {
        self.isValid = isValid
        self.errorMessages = errorMessages
    }

    public static var valid: ConfigurationValidationResult {
        ConfigurationValidationResult(isValid: true)
    }

    public static func invalid(_ errors: [String]) -> ConfigurationValidationResult {
        ConfigurationValidationResult(isValid: false, errorMessages: errors)
    }
}

/// Health and connectivity check report returned by testConnection().
public struct ConnectionHealth: Sendable, Equatable {
    public var isReachable: Bool
    public var latencyMs: Double
    public var bucketAccessible: Bool
    public var message: String

    public init(
        isReachable: Bool,
        latencyMs: Double,
        bucketAccessible: Bool,
        message: String
    ) {
        self.isReachable = isReachable
        self.latencyMs = latencyMs
        self.bucketAccessible = bucketAccessible
        self.message = message
    }
}

/// Abstract storage provider interface decoupling raw cloud transport from synchronization logic.
public protocol StorageProvider: Sendable {
    /// Validates local credentials and endpoint URLs prior to initiating network I/O.
    func validateConfiguration() -> ConfigurationValidationResult

    /// Performs an active probe verifying bucket reachability and read/write authorization.
    func testConnection() async throws -> ConnectionHealth

    /// Downloads and returns object data at the specified path.
    func readObject(path: String) async throws -> Data

    /// Uploads object data to the specified path with optional metadata headers.
    func writeObject(path: String, data: Data, metadata: [String: String]?) async throws

    /// Deletes an object at the specified path.
    func deleteObject(path: String) async throws

    /// Lists objects matching the given key prefix.
    func listObjects(prefix: String) async throws -> [StorageObjectSummary]

    /// Fetches metadata attributes for an object without downloading its entire body.
    func getObjectMetadata(path: String) async throws -> StorageObjectMetadata
}
