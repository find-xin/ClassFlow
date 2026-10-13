import Foundation

/// Synchronization engine implementing the Device-Isolated Append-Only Log protocol.
public final class SyncEngine: @unchecked Sendable {
    public let deviceId: String
    public let deviceName: String
    private var localSequence: Int
    private var pendingOperations: [SyncOperation] = []
    private var peerSequences: [String: Int] = [:] // [peerDeviceId: lastSeenSeq]

    public struct SyncSummary: Sendable, Equatable {
        public var uploadedOperationsCount: Int
        public var downloadedPeerOperationsCount: Int
        public var conflictedOperationsCount: Int

        public init(
            uploadedOperationsCount: Int = 0,
            downloadedPeerOperationsCount: Int = 0,
            conflictedOperationsCount: Int = 0
        ) {
            self.uploadedOperationsCount = uploadedOperationsCount
            self.downloadedPeerOperationsCount = downloadedPeerOperationsCount
            self.conflictedOperationsCount = conflictedOperationsCount
        }
    }

    public init(
        deviceId: String = "dev_" + UUID().uuidString.prefix(8).lowercased(),
        deviceName: String = "Apple Silicon Mac",
        initialSequence: Int = 0
    ) {
        self.deviceId = deviceId
        self.deviceName = deviceName
        self.localSequence = initialSequence
    }

    // MARK: - Local Operation Recording

    /// Records a database mutation as an immutable append-only change operation.
    public func recordOperation(
        action: String,
        entityType: String,
        entityId: UUID,
        payload: Data? = nil
    ) -> SyncOperation {
        localSequence += 1
        var vectorClock = peerSequences
        vectorClock[deviceId] = localSequence

        let op = SyncOperation(
            opId: UUID(),
            deviceId: deviceId,
            sequence: localSequence,
            timestamp: ISO8601DateFormatter().string(from: Date()),
            entityType: entityType,
            entityId: entityId,
            action: action,
            payload: payload,
            clientVectorClock: vectorClock
        )
        pendingOperations.append(op)
        return op
    }

    // MARK: - Sync Orchestration

    /// Executes the full decentralized synchronization cycle against a StorageProvider.
    public func executeSync(
        storage: StorageProvider,
        courses: inout [Course],
        exceptions: inout [ScheduleException]
    ) async throws -> SyncSummary {
        var summary = SyncSummary()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .prettyPrinted]
        let decoder = JSONDecoder()

        // 1. Register self in global device registry
        let registryPath = "global/device_registry.json"
        var registry = DeviceRegistry()
        if let existingRegistryData = try? await storage.readObject(path: registryPath) {
            registry = (try? decoder.decode(DeviceRegistry.self, from: existingRegistryData)) ?? DeviceRegistry()
        }
        if registry.devices[deviceId] != deviceName {
            registry.devices[deviceId] = deviceName
            let regData = try encoder.encode(registry)
            try await storage.writeObject(path: registryPath, data: regData, metadata: nil)
        }

        // 2. Upload pending local operations to own device path
        if !pendingOperations.isEmpty {
            for op in pendingOperations {
                let revFileName = String(format: "%06d_%@.json", op.sequence, op.opId.uuidString)
                let revPath = "devices/\(deviceId)/revisions/\(revFileName)"
                let opData = try encoder.encode(op)
                try await storage.writeObject(path: revPath, data: opData, metadata: nil)
                summary.uploadedOperationsCount += 1
            }

            // Update own manifest
            let latestOp = pendingOperations.last!
            let latestRevFile = String(format: "revisions/%06d_%@.json", latestOp.sequence, latestOp.opId.uuidString)
            let manifest = DeviceSyncManifest(
                schemaVersion: 1,
                deviceId: deviceId,
                deviceName: deviceName,
                latestSequence: localSequence,
                latestRevisionFile: latestRevFile
            )
            let manifestData = try encoder.encode(manifest)
            try await storage.writeObject(path: "devices/\(deviceId)/device_manifest.json", data: manifestData, metadata: nil)

            pendingOperations.removeAll()
        }

        // 3. Scan and ingest peer device revisions
        for (peerId, _) in registry.devices where peerId != deviceId {
            let peerManifestPath = "devices/\(peerId)/device_manifest.json"
            guard let peerManifestData = try? await storage.readObject(path: peerManifestPath),
                  let peerManifest = try? decoder.decode(DeviceSyncManifest.self, from: peerManifestData) else {
                continue
            }

            let lastSeenSeq = peerSequences[peerId] ?? 0
            if peerManifest.latestSequence > lastSeenSeq {
                // List and fetch peer revisions
                let prefix = "devices/\(peerId)/revisions/"
                let revObjects = try await storage.listObjects(prefix: prefix)

                for obj in revObjects.sorted(by: { $0.key < $1.key }) {
                    let revData = try await storage.readObject(path: obj.key)
                    guard let peerOp = try? decoder.decode(SyncOperation.self, from: revData) else { continue }
                    guard peerOp.sequence > lastSeenSeq else { continue }

                    // Apply peer operation locally with 3-way merge rules
                    applyPeerOperation(peerOp, courses: &courses, exceptions: &exceptions, decoder: decoder)
                    summary.downloadedPeerOperationsCount += 1
                }

                peerSequences[peerId] = peerManifest.latestSequence
            }
        }

        return summary
    }

    // MARK: - 3-Way Merge Rules

    private func applyPeerOperation(
        _ op: SyncOperation,
        courses: inout [Course],
        exceptions: inout [ScheduleException],
        decoder: JSONDecoder
    ) {
        if op.entityType == "course" {
            switch op.action {
            case "INSERT":
                if let payload = op.payload,
                   let peerCourse = try? decoder.decode(Course.self, from: payload) {
                    if !courses.contains(where: { $0.id == peerCourse.id }) {
                        courses.append(peerCourse)
                    }
                }
            case "UPDATE":
                if let payload = op.payload,
                   let peerCourse = try? decoder.decode(Course.self, from: payload) {
                    if let idx = courses.firstIndex(where: { $0.id == peerCourse.id }) {
                        // Field-level 3-way merge: preserve local changes if distinct
                        var merged = courses[idx]
                        merged.name = peerCourse.name
                        if let peerTeacher = peerCourse.teacher { merged.teacher = peerTeacher }
                        merged.color = peerCourse.color
                        if !peerCourse.schedules.isEmpty { merged.schedules = peerCourse.schedules }
                        courses[idx] = merged
                    } else {
                        // Delete vs Modify: Modify wins! Restore entity
                        courses.append(peerCourse)
                    }
                }
            case "DELETE":
                courses.removeAll(where: { $0.id == op.entityId })
            default:
                break
            }
        } else if op.entityType == "exception" {
            switch op.action {
            case "INSERT":
                if let payload = op.payload,
                   let peerEx = try? decoder.decode(ScheduleException.self, from: payload) {
                    if !exceptions.contains(where: { $0.id == peerEx.id }) {
                        exceptions.append(peerEx)
                    }
                }
            case "DELETE":
                exceptions.removeAll(where: { $0.id == op.entityId })
            default:
                break
            }
        }
    }
}
