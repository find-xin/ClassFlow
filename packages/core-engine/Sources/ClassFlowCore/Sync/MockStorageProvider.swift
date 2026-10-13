import Foundation

/// Thread-safe in-memory StorageProvider implementation for deterministic testing and simulation.
public actor MockStorageProvider: StorageProvider {
    private var storage: [String: Data] = [:]
    private var metadataStore: [String: [String: String]] = [:]

    public init() {}

    nonisolated public func validateConfiguration() -> ConfigurationValidationResult {
        .valid
    }

    public func testConnection() async throws -> ConnectionHealth {
        ConnectionHealth(
            isReachable: true,
            latencyMs: 1.0,
            bucketAccessible: true,
            message: "Mock bucket accessible"
        )
    }

    public func readObject(path: String) async throws -> Data {
        guard let data = storage[path] else {
            throw NSError(domain: "MockStorageProvider", code: 404, userInfo: [
                NSLocalizedDescriptionKey: "Object not found at \(path)"
            ])
        }
        return data
    }

    public func writeObject(path: String, data: Data, metadata: [String: String]?) async throws {
        storage[path] = data
        if let metadata {
            metadataStore[path] = metadata
        }
    }

    public func deleteObject(path: String) async throws {
        storage.removeValue(forKey: path)
        metadataStore.removeValue(forKey: path)
    }

    public func listObjects(prefix: String) async throws -> [StorageObjectSummary] {
        storage.keys
            .filter { $0.hasPrefix(prefix) }
            .sorted()
            .map { key in
                StorageObjectSummary(
                    key: key,
                    size: Int64(storage[key]?.count ?? 0),
                    lastModified: Date()
                )
            }
    }

    public func getObjectMetadata(path: String) async throws -> StorageObjectMetadata {
        guard let data = storage[path] else {
            throw NSError(domain: "MockStorageProvider", code: 404, userInfo: nil)
        }
        return StorageObjectMetadata(
            size: Int64(data.count),
            lastModified: Date(),
            customMetadata: metadataStore[path] ?? [:]
        )
    }
}
