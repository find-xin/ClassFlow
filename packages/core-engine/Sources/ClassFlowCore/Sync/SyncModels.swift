import Foundation

/// High-level status of the cloud synchronization state machine.
public enum SyncStatus: Sendable, Equatable {
    case unconfigured
    case synced(lastSyncedAt: Date)
    case syncing
    case waitingNetwork
    case conflictDetected(message: String)
    case syncFailed(error: String)

    public var displayText: String {
        switch self {
        case .unconfigured: return "未配置云同步"
        case .synced(let date):
            let df = DateFormatter()
            df.dateFormat = "HH:mm"
            return "已同步 (\(df.string(from: date)))"
        case .syncing: return "正在同步中..."
        case .waitingNetwork: return "等待网络连接"
        case .conflictDetected(let msg): return "检测到数据冲突: \(msg)"
        case .syncFailed(let err): return "同步失败: \(err)"
        }
    }
}

/// An immutable operational change event logged by a device.
public struct SyncOperation: Codable, Sendable, Identifiable, Equatable {
    public var id: UUID { opId }
    public var opId: UUID
    public var deviceId: String
    public var sequence: Int
    public var timestamp: String // ISO 8601
    public var entityType: String // "course", "schedule", "exception", "semester"
    public var entityId: UUID
    public var action: String // "INSERT", "UPDATE", "DELETE"
    public var payload: Data?
    public var clientVectorClock: [String: Int]

    public init(
        opId: UUID = UUID(),
        deviceId: String,
        sequence: Int,
        timestamp: String = ISO8601DateFormatter().string(from: Date()),
        entityType: String,
        entityId: UUID,
        action: String,
        payload: Data? = nil,
        clientVectorClock: [String: Int] = [:]
    ) {
        self.opId = opId
        self.deviceId = deviceId
        self.sequence = sequence
        self.timestamp = timestamp
        self.entityType = entityType
        self.entityId = entityId
        self.action = action
        self.payload = payload
        self.clientVectorClock = clientVectorClock
    }
}

/// Checkpoint manifest tracking a single device's published revision head.
public struct DeviceSyncManifest: Codable, Sendable, Equatable {
    public var schemaVersion: Int
    public var deviceId: String
    public var deviceName: String
    public var clientVersion: String
    public var lastUpdated: String
    public var latestSequence: Int
    public var latestRevisionFile: String
    public var snapshotFile: String?

    public init(
        schemaVersion: Int = 1,
        deviceId: String,
        deviceName: String,
        clientVersion: String = "1.0.0",
        lastUpdated: String = ISO8601DateFormatter().string(from: Date()),
        latestSequence: Int,
        latestRevisionFile: String,
        snapshotFile: String? = nil
    ) {
        self.schemaVersion = schemaVersion
        self.deviceId = deviceId
        self.deviceName = deviceName
        self.clientVersion = clientVersion
        self.lastUpdated = lastUpdated
        self.latestSequence = latestSequence
        self.latestRevisionFile = latestRevisionFile
        self.snapshotFile = snapshotFile
    }
}

/// Registry of known active client devices participating in bucket sync.
public struct DeviceRegistry: Codable, Sendable, Equatable {
    public var devices: [String: String] // [deviceId: deviceName]

    public init(devices: [String: String] = [:]) {
        self.devices = devices
    }
}
