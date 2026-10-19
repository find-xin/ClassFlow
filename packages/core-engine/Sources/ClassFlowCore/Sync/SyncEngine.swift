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
        public var activeCoursesCount: Int
        public var snapshotPublished: Bool

        public init(
            uploadedOperationsCount: Int = 0,
            downloadedPeerOperationsCount: Int = 0,
            conflictedOperationsCount: Int = 0,
            activeCoursesCount: Int = 0,
            snapshotPublished: Bool = false
        ) {
            self.uploadedOperationsCount = uploadedOperationsCount
            self.downloadedPeerOperationsCount = downloadedPeerOperationsCount
            self.conflictedOperationsCount = conflictedOperationsCount
            self.activeCoursesCount = activeCoursesCount
            self.snapshotPublished = snapshotPublished
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

    public static let tombstoneRetentionDays: Int = 90
    public static let maxTombstonesCount: Int = 1000

    public static func garbageCollectTombstones(_ tombstones: [SyncTombstone], referenceDate: Date = Date()) -> [SyncTombstone] {
        let cutoff = Int64((referenceDate.timeIntervalSince1970 - Double(tombstoneRetentionDays * 86400)) * 1000)
        var active = tombstones.filter { $0.deletedAt >= cutoff }
        if active.count > maxTombstonesCount {
            active = Array(active.sorted(by: { $0.deletedAt > $1.deletedAt }).prefix(maxTombstonesCount))
        }
        return active
    }

    public var latestSequence: Int {
        localSequence
    }

    public var pendingCount: Int {
        pendingOperations.count
    }

    // MARK: - Local Operation Recording

    /// Queues initial snapshot data as INSERT operations if this client device has zero prior sequence history.
    public func queueInitialDataIfNeeded(
        courses: [Course],
        exceptions: [ScheduleException] = [],
        semesters: [Semester] = [],
        calendarEvents: [CalendarEvent] = [],
        pomodoroTasks: [PomodoroTask] = [],
        pomodoroRecords: [PomodoroRecord] = [],
        timetableProfiles: [TimetableProfile] = []
    ) {
        guard localSequence == 0 && pendingOperations.isEmpty else { return }
        let encoder = JSONEncoder()

        for s in semesters {
            if let data = try? encoder.encode(s) {
                _ = recordOperation(action: "INSERT", entityType: "semester", entityId: s.id, payload: data)
            }
        }
        for c in courses {
            if let data = try? encoder.encode(c) {
                _ = recordOperation(action: "INSERT", entityType: "course", entityId: c.id, payload: data)
            }
        }
        for e in exceptions {
            if let data = try? encoder.encode(e) {
                _ = recordOperation(action: "INSERT", entityType: "exception", entityId: e.id, payload: data)
            }
        }
        for ev in calendarEvents {
            if let data = try? encoder.encode(ev) {
                _ = recordOperation(action: "INSERT", entityType: "calendarEvent", entityId: ev.id, payload: data)
            }
        }
        for pt in pomodoroTasks {
            if let data = try? encoder.encode(pt) {
                _ = recordOperation(action: "INSERT", entityType: "pomodoroTask", entityId: pt.id, payload: data)
            }
        }
        for pr in pomodoroRecords {
            if let data = try? encoder.encode(pr) {
                _ = recordOperation(action: "INSERT", entityType: "pomodoroRecord", entityId: pr.id, payload: data)
            }
        }
        for pp in timetableProfiles {
            if let data = try? encoder.encode(pp) {
                _ = recordOperation(action: "INSERT", entityType: "timetableProfile", entityId: pp.id, payload: data)
            }
        }
    }

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

    /// Executes decentralized synchronization for courses and exceptions (backward-compatible overload).
    public func executeSync(
        storage: StorageProvider,
        courses: inout [Course],
        exceptions: inout [ScheduleException]
    ) async throws -> SyncSummary {
        var dummySemesters: [Semester] = []
        var dummyEvents: [CalendarEvent] = []
        var dummyTasks: [PomodoroTask] = []
        var dummyRecords: [PomodoroRecord] = []
        var dummyProfiles: [TimetableProfile] = []
        return try await executeSync(
            storage: storage,
            courses: &courses,
            exceptions: &exceptions,
            semesters: &dummySemesters,
            calendarEvents: &dummyEvents,
            pomodoroTasks: &dummyTasks,
            pomodoroRecords: &dummyRecords,
            timetableProfiles: &dummyProfiles
        )
    }

    /// Executes decentralized synchronization for courses, exceptions, semesters, and calendar events (backward-compatible overload).
    public func executeSync(
        storage: StorageProvider,
        courses: inout [Course],
        exceptions: inout [ScheduleException],
        semesters: inout [Semester],
        calendarEvents: inout [CalendarEvent]
    ) async throws -> SyncSummary {
        var dummyTasks: [PomodoroTask] = []
        var dummyRecords: [PomodoroRecord] = []
        var dummyProfiles: [TimetableProfile] = []
        return try await executeSync(
            storage: storage,
            courses: &courses,
            exceptions: &exceptions,
            semesters: &semesters,
            calendarEvents: &calendarEvents,
            pomodoroTasks: &dummyTasks,
            pomodoroRecords: &dummyRecords,
            timetableProfiles: &dummyProfiles
        )
    }

    /// Executes the full decentralized synchronization cycle against a StorageProvider for all domain entities.
    public func executeSync(
        storage: StorageProvider,
        courses: inout [Course],
        exceptions: inout [ScheduleException],
        semesters: inout [Semester],
        calendarEvents: inout [CalendarEvent],
        pomodoroTasks: inout [PomodoroTask],
        pomodoroRecords: inout [PomodoroRecord],
        timetableProfiles: inout [TimetableProfile],
        tombstones: inout [SyncTombstone]
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
        var latestRevFile = "revisions/latest.json"
        if !pendingOperations.isEmpty {
            for op in pendingOperations {
                let revFileName = String(format: "%06d_%@.json", op.sequence, op.opId.uuidString)
                let revPath = "devices/\(deviceId)/revisions/\(revFileName)"
                let opData = try encoder.encode(op)
                try await storage.writeObject(path: revPath, data: opData, metadata: nil)
                summary.uploadedOperationsCount += 1
            }
            let latestOp = pendingOperations.last!
            latestRevFile = String(format: "revisions/%06d_%@.json", latestOp.sequence, latestOp.opId.uuidString)
            pendingOperations.removeAll()
        } else {
            localSequence += 1
        }

        // Always publish latest authoritative snapshot and device manifest
        let snapshotFile = "snapshots/snapshot.json"
        let manifest = DeviceSyncManifest(
            schemaVersion: 1,
            deviceId: deviceId,
            deviceName: deviceName,
            latestSequence: localSequence,
            latestRevisionFile: latestRevFile,
            snapshotFile: snapshotFile
        )
        let manifestData = try encoder.encode(manifest)
        try await storage.writeObject(path: "devices/\(deviceId)/device_manifest.json", data: manifestData, metadata: nil)

        // Write unified snapshot for fast bootstrapping & complete multi-module backup (with Tombstone GC)
        tombstones = Self.garbageCollectTombstones(tombstones)
        let snapshot = DeviceSnapshot(
            deviceId: deviceId,
            sequence: localSequence,
            courses: courses,
            exceptions: exceptions,
            semesters: semesters,
            calendarEvents: calendarEvents,
            pomodoroTasks: pomodoroTasks,
            pomodoroRecords: pomodoroRecords,
            timetableProfiles: timetableProfiles,
            tombstones: tombstones
        )
        let snapData = try encoder.encode(snapshot)
        try await storage.writeObject(path: "devices/\(deviceId)/\(snapshotFile)", data: snapData, metadata: nil)

        // 3. Scan and ingest peer device revisions & snapshots
        for (peerId, _) in registry.devices where peerId != deviceId {
            let peerManifestPath = "devices/\(peerId)/device_manifest.json"
            var peerManifest: DeviceSyncManifest? = nil
            if let peerManifestData = try? await storage.readObject(path: peerManifestPath) {
                peerManifest = try? decoder.decode(DeviceSyncManifest.self, from: peerManifestData)
            }

            let hasNeverSyncedWithPeer = (peerSequences[peerId] == nil)
            var lastSeenSeq = peerSequences[peerId] ?? 0
            let peerSeq = peerManifest?.latestSequence ?? (hasNeverSyncedWithPeer ? 1 : 0)

            if peerSeq > lastSeenSeq || hasNeverSyncedWithPeer {
                // Ingest latest authoritative snapshot whenever peer has updated sequence or never synced
                let snapRelPath = peerManifest?.snapshotFile ?? "snapshots/snapshot.json"
                let snapFullPath = "devices/\(peerId)/\(snapRelPath)"
                if let snapData = try? await storage.readObject(path: snapFullPath),
                   let peerSnapshot = try? decoder.decode(DeviceSnapshot.self, from: snapData) {
                    applyPeerSnapshot(
                        peerSnapshot,
                        courses: &courses,
                        exceptions: &exceptions,
                        semesters: &semesters,
                            calendarEvents: &calendarEvents,
                            pomodoroTasks: &pomodoroTasks,
                            pomodoroRecords: &pomodoroRecords,
                            timetableProfiles: &timetableProfiles,
                            tombstones: &tombstones
                        )
                        summary.downloadedPeerOperationsCount += 1
                        lastSeenSeq = max(lastSeenSeq, peerSnapshot.sequence)
                    }

                // List and fetch peer revisions for any incremental operations
                let prefix = "devices/\(peerId)/revisions/"
                if let revObjects = try? await storage.listObjects(prefix: prefix) {
                    for obj in revObjects.sorted(by: { $0.key < $1.key }) {
                        let revData = try await storage.readObject(path: obj.key)
                        guard let peerOp = try? decoder.decode(SyncOperation.self, from: revData) else { continue }
                        guard peerOp.sequence > lastSeenSeq else { continue }

                        // Apply peer operation locally with 3-way merge rules
                        applyPeerOperation(
                            peerOp,
                            courses: &courses,
                            exceptions: &exceptions,
                            semesters: &semesters,
                            calendarEvents: &calendarEvents,
                            pomodoroTasks: &pomodoroTasks,
                            pomodoroRecords: &pomodoroRecords,
                            timetableProfiles: &timetableProfiles,
                            tombstones: &tombstones,
                            decoder: decoder
                        )
                        summary.downloadedPeerOperationsCount += 1
                    }
                }

                peerSequences[peerId] = peerManifest?.latestSequence ?? max(lastSeenSeq, 1)
            }
        }

        if summary.downloadedPeerOperationsCount > 0 {
            localSequence += 1
            let updatedSnapshot = DeviceSnapshot(
                deviceId: deviceId,
                sequence: localSequence,
                courses: courses,
                exceptions: exceptions,
                semesters: semesters,
                calendarEvents: calendarEvents,
                pomodoroTasks: pomodoroTasks,
                pomodoroRecords: pomodoroRecords,
                timetableProfiles: timetableProfiles,
                tombstones: tombstones
            )
            let snapData = try encoder.encode(updatedSnapshot)
            try await storage.writeObject(path: "devices/\(deviceId)/\(snapshotFile)", data: snapData, metadata: nil)

            let updatedManifest = DeviceSyncManifest(
                schemaVersion: 1,
                deviceId: deviceId,
                deviceName: deviceName,
                latestSequence: localSequence,
                latestRevisionFile: latestRevFile,
                snapshotFile: snapshotFile
            )
            let manifestData = try encoder.encode(updatedManifest)
            try await storage.writeObject(path: "devices/\(deviceId)/device_manifest.json", data: manifestData, metadata: nil)
        }

        summary.activeCoursesCount = courses.count
        summary.snapshotPublished = true
        return summary
    }

    /// Forces complete local state replacement from the most comprehensive cloud/peer snapshot.
    /// Bypasses all local tombstones.
    public func executeForcePull(
        storage: StorageProvider,
        courses: inout [Course],
        exceptions: inout [ScheduleException],
        semesters: inout [Semester],
        calendarEvents: inout [CalendarEvent],
        pomodoroTasks: inout [PomodoroTask],
        pomodoroRecords: inout [PomodoroRecord],
        timetableProfiles: inout [TimetableProfile],
        tombstones: inout [SyncTombstone]
    ) async throws -> SyncSummary {
        let decoder = JSONDecoder()
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .prettyPrinted]

        let registryPath = "global/device_registry.json"
        guard let existingRegistryData = try? await storage.readObject(path: registryPath),
              let registry = try? decoder.decode(DeviceRegistry.self, from: existingRegistryData) else {
            throw NSError(domain: "SyncEngine", code: 404, userInfo: [NSLocalizedDescriptionKey: "无法读取云端设备注册表"])
        }

        var bestSnapshot: DeviceSnapshot? = nil
        var bestCourseCount = 0

        for (peerId, _) in registry.devices where peerId != deviceId {
            let snapPath = "devices/\(peerId)/snapshots/snapshot.json"
            if let snapData = try? await storage.readObject(path: snapPath),
               let snap = try? decoder.decode(DeviceSnapshot.self, from: snapData) {
                if snap.courses.count > bestCourseCount || bestSnapshot == nil {
                    bestSnapshot = snap
                    bestCourseCount = snap.courses.count
                }
            }
        }

        guard let targetSnapshot = bestSnapshot else {
            throw NSError(domain: "SyncEngine", code: 404, userInfo: [NSLocalizedDescriptionKey: "未在云端找到其他设备的有效快照"])
        }

        tombstones.removeAll()

        courses = targetSnapshot.courses
        exceptions = targetSnapshot.exceptions
        if !targetSnapshot.semesters.isEmpty {
            semesters = targetSnapshot.semesters
        }
        calendarEvents = targetSnapshot.calendarEvents
        pomodoroTasks = targetSnapshot.pomodoroTasks
        pomodoroRecords = targetSnapshot.pomodoroRecords
        if !targetSnapshot.timetableProfiles.isEmpty {
            timetableProfiles = targetSnapshot.timetableProfiles
        }

        localSequence += 1
        let snapFile = "snapshots/snapshot.json"
        let snapshot = DeviceSnapshot(
            deviceId: deviceId,
            sequence: localSequence,
            courses: courses,
            exceptions: exceptions,
            semesters: semesters,
            calendarEvents: calendarEvents,
            pomodoroTasks: pomodoroTasks,
            pomodoroRecords: pomodoroRecords,
            timetableProfiles: timetableProfiles,
            tombstones: []
        )
        let snapData = try encoder.encode(snapshot)
        try await storage.writeObject(path: "devices/\(deviceId)/\(snapFile)", data: snapData, metadata: nil)

        var summary = SyncSummary()
        summary.uploadedOperationsCount = 1
        summary.downloadedPeerOperationsCount = courses.count
        summary.activeCoursesCount = courses.count
        summary.snapshotPublished = true
        return summary
    }

    /// Backward-compatible overload for executeSync without tombstones.
    public func executeSync(
        storage: StorageProvider,
        courses: inout [Course],
        exceptions: inout [ScheduleException],
        semesters: inout [Semester],
        calendarEvents: inout [CalendarEvent],
        pomodoroTasks: inout [PomodoroTask],
        pomodoroRecords: inout [PomodoroRecord],
        timetableProfiles: inout [TimetableProfile]
    ) async throws -> SyncSummary {
        var dummyTombstones: [SyncTombstone] = []
        return try await executeSync(
            storage: storage,
            courses: &courses,
            exceptions: &exceptions,
            semesters: &semesters,
            calendarEvents: &calendarEvents,
            pomodoroTasks: &pomodoroTasks,
            pomodoroRecords: &pomodoroRecords,
            timetableProfiles: &timetableProfiles,
            tombstones: &dummyTombstones
        )
    }

    // MARK: - Snapshot & 3-Way Merge Rules

    private func applyPeerSnapshot(
        _ snapshot: DeviceSnapshot,
        courses: inout [Course],
        exceptions: inout [ScheduleException],
        semesters: inout [Semester],
        calendarEvents: inout [CalendarEvent],
        pomodoroTasks: inout [PomodoroTask],
        pomodoroRecords: inout [PomodoroRecord],
        timetableProfiles: inout [TimetableProfile],
        tombstones: inout [SyncTombstone]
    ) {
        // 1. Ingest & merge peer tombstones with GC filtering
        let validPeerTombstones = Self.garbageCollectTombstones(snapshot.tombstones)
        for pt in validPeerTombstones {
            if let idx = tombstones.firstIndex(where: { $0.entityId == pt.entityId }) {
                if pt.deletedAt > tombstones[idx].deletedAt {
                    tombstones[idx] = pt
                }
            } else {
                tombstones.append(pt)
            }
        }
        tombstones = Self.garbageCollectTombstones(tombstones)

        // If local timetable has been cleared but peer has active entities, clear conflicting tombstones
        if courses.isEmpty && !snapshot.courses.isEmpty {
            let peerCourseIds = Set(snapshot.courses.map { $0.id.uuidString })
            tombstones.removeAll(where: { peerCourseIds.contains($0.entityId) })
        }
        if calendarEvents.isEmpty && !snapshot.calendarEvents.isEmpty {
            let peerSchedIds = Set(snapshot.calendarEvents.map { $0.id.uuidString })
            tombstones.removeAll(where: { peerSchedIds.contains($0.entityId) })
        }
        if pomodoroTasks.isEmpty && !snapshot.pomodoroTasks.isEmpty {
            let peerTaskIds = Set(snapshot.pomodoroTasks.map { $0.id.uuidString })
            tombstones.removeAll(where: { peerTaskIds.contains($0.entityId) })
        }
        let tombstoneIds = Set(tombstones.map { $0.entityId })

        // 2. Purge local entities marked by tombstones
        courses.removeAll(where: { tombstoneIds.contains($0.id.uuidString) })
        exceptions.removeAll(where: { tombstoneIds.contains($0.id.uuidString) || tombstoneIds.contains($0.scheduleId.uuidString) })
        calendarEvents.removeAll(where: { tombstoneIds.contains($0.id.uuidString) })
        pomodoroTasks.removeAll(where: { tombstoneIds.contains($0.id.uuidString) })
        pomodoroRecords.removeAll(where: { tombstoneIds.contains($0.id.uuidString) })
        timetableProfiles.removeAll(where: { tombstoneIds.contains($0.id.uuidString) })

        // 3. Merge Semesters first to establish peer-to-local semesterId mapping (by id or by name)
        var semesterIdMap: [UUID: UUID] = [:]
        for ps in snapshot.semesters {
            guard !tombstoneIds.contains(ps.id.uuidString) else { continue }
            let psName = ps.name.trimmingCharacters(in: .whitespacesAndNewlines)
            let standardSections = [
                SectionTime(section: 1, startTime: "08:30", endTime: "09:15"),
                SectionTime(section: 2, startTime: "09:20", endTime: "10:05"),
                SectionTime(section: 3, startTime: "10:15", endTime: "11:00"),
                SectionTime(section: 4, startTime: "11:05", endTime: "11:50"),
                SectionTime(section: 5, startTime: "14:30", endTime: "15:15"),
                SectionTime(section: 6, startTime: "15:20", endTime: "16:05"),
                SectionTime(section: 7, startTime: "16:15", endTime: "17:00"),
                SectionTime(section: 8, startTime: "17:05", endTime: "17:50"),
                SectionTime(section: 9, startTime: "18:30", endTime: "19:15"),
                SectionTime(section: 10, startTime: "19:20", endTime: "20:05"),
                SectionTime(section: 11, startTime: "20:15", endTime: "21:00"),
                SectionTime(section: 12, startTime: "21:05", endTime: "21:50")
            ]
            if let idx = semesters.firstIndex(where: { $0.id == ps.id }) {
                semesterIdMap[ps.id] = ps.id
                var merged = semesters[idx]
                merged.totalWeeks = max(merged.totalWeeks, ps.totalWeeks)
                let isCurDefault = merged.sections.isEmpty || merged.sections == standardSections
                if !ps.sections.isEmpty && (isCurDefault || ps.sections != merged.sections) {
                    merged.sections = ps.sections
                }
                semesters[idx] = merged
            } else if let idx = semesters.firstIndex(where: { $0.name.trimmingCharacters(in: .whitespacesAndNewlines) == psName }) {
                // Same semester name on peer device -> merge into existing local semester
                let localSem = semesters[idx]
                semesterIdMap[ps.id] = localSem.id
                var merged = localSem
                merged.totalWeeks = max(merged.totalWeeks, ps.totalWeeks)
                let isCurDefault = merged.sections.isEmpty || merged.sections == standardSections
                if !ps.sections.isEmpty && (isCurDefault || ps.sections != merged.sections) {
                    merged.sections = ps.sections
                }
                semesters[idx] = merged
            } else {
                // New distinct course table
                semesters.append(ps)
                semesterIdMap[ps.id] = ps.id
            }
        }

        // 4. Ingest peer courses, mapped to resolved semester ID
        for pc in snapshot.courses {
            guard !tombstoneIds.contains(pc.id.uuidString) else { continue }
            var resolvedSemId = pc.semesterId
            if let mapped = semesterIdMap[pc.semesterId] {
                resolvedSemId = mapped
            } else if !semesters.contains(where: { $0.id == resolvedSemId }), let defaultSem = semesters.first {
                resolvedSemId = defaultSem.id
            }
            let pcName = pc.name.trimmingCharacters(in: .whitespacesAndNewlines)

            if let idx = courses.firstIndex(where: { $0.id == pc.id }) {
                var merged = courses[idx]
                merged.semesterId = resolvedSemId
                merged.name = pc.name
                if let pt = pc.teacher { merged.teacher = pt }
                merged.color = pc.color
                if let pn = pc.notes { merged.notes = pn }
                var scheduleMap: [UUID: CourseSchedule] = [:]
                for s in merged.schedules { scheduleMap[s.id] = s }
                for s in pc.schedules {
                    if let existingSchedIdx = merged.schedules.firstIndex(where: {
                        $0.dayOfWeek == s.dayOfWeek && max($0.startSection, s.startSection) <= min($0.endSection, s.endSection)
                    }) {
                        var existingSched = merged.schedules[existingSchedIdx]
                        existingSched.startSection = min(existingSched.startSection, s.startSection)
                        existingSched.endSection = max(existingSched.endSection, s.endSection)
                        if existingSched.classroom == nil || existingSched.classroom!.isEmpty {
                            existingSched.classroom = s.classroom
                        }
                        if let w1 = existingSched.weekPattern.customWeeks, let w2 = s.weekPattern.customWeeks {
                            existingSched.weekPattern.customWeeks = Array(Set(w1 + w2)).sorted()
                        } else if s.weekPattern.customWeeks != nil {
                            existingSched.weekPattern.customWeeks = s.weekPattern.customWeeks
                        }
                        merged.schedules[existingSchedIdx] = existingSched
                    } else if scheduleMap[s.id] == nil {
                        merged.schedules.append(s)
                    }
                }
                courses[idx] = merged
            } else if let idx = courses.firstIndex(where: {
                $0.semesterId == resolvedSemId && $0.name.trimmingCharacters(in: .whitespacesAndNewlines) == pcName
            }) {
                // Same course name in same semester -> merge schedules
                var merged = courses[idx]
                if let pt = pc.teacher, (merged.teacher == nil || merged.teacher!.isEmpty) { merged.teacher = pt }
                if let pn = pc.notes, (merged.notes == nil || merged.notes!.isEmpty) { merged.notes = pn }
                var scheduleMap: [UUID: CourseSchedule] = [:]
                for s in merged.schedules { scheduleMap[s.id] = s }
                for s in pc.schedules {
                    if let existingSchedIdx = merged.schedules.firstIndex(where: {
                        $0.dayOfWeek == s.dayOfWeek && max($0.startSection, s.startSection) <= min($0.endSection, s.endSection)
                    }) {
                        var existingSched = merged.schedules[existingSchedIdx]
                        existingSched.startSection = min(existingSched.startSection, s.startSection)
                        existingSched.endSection = max(existingSched.endSection, s.endSection)
                        if existingSched.classroom == nil || existingSched.classroom!.isEmpty {
                            existingSched.classroom = s.classroom
                        }
                        if let w1 = existingSched.weekPattern.customWeeks, let w2 = s.weekPattern.customWeeks {
                            existingSched.weekPattern.customWeeks = Array(Set(w1 + w2)).sorted()
                        } else if s.weekPattern.customWeeks != nil {
                            existingSched.weekPattern.customWeeks = s.weekPattern.customWeeks
                        }
                        merged.schedules[existingSchedIdx] = existingSched
                    } else if scheduleMap[s.id] == nil {
                        merged.schedules.append(s)
                    }
                }
                courses[idx] = merged
            } else {
                var newC = pc
                newC.semesterId = resolvedSemId
                courses.append(newC)
            }
        }

        // 5. Ingest peer exceptions
        for pe in snapshot.exceptions {
            guard !tombstoneIds.contains(pe.id.uuidString) && !tombstoneIds.contains(pe.scheduleId.uuidString) else { continue }
            if !exceptions.contains(where: { $0.id == pe.id }) {
                exceptions.append(pe)
            }
        }

        // 6. Ingest peer calendar events (schedules)
        for pev in snapshot.calendarEvents {
            guard !tombstoneIds.contains(pev.id.uuidString) else { continue }
            var resolvedSemId = pev.semesterId
            if let pid = pev.semesterId, let mapped = semesterIdMap[pid] {
                resolvedSemId = mapped
            } else if let pid = pev.semesterId, !semesters.contains(where: { $0.id == pid }), let defaultSem = semesters.first {
                resolvedSemId = defaultSem.id
            }
            let pevTitle = pev.title.trimmingCharacters(in: .whitespacesAndNewlines)

            if let idx = calendarEvents.firstIndex(where: { $0.id == pev.id }) {
                var merged = pev
                merged.semesterId = resolvedSemId
                calendarEvents[idx] = merged
            } else if let idx = calendarEvents.firstIndex(where: {
                $0.semesterId == resolvedSemId && $0.title.trimmingCharacters(in: .whitespacesAndNewlines) == pevTitle && $0.startTime == pev.startTime
            }) {
                var merged = calendarEvents[idx]
                merged.endTime = pev.endTime
                merged.frequency = pev.frequency
                merged.location = pev.location ?? merged.location
                calendarEvents[idx] = merged
            } else {
                var newEv = pev
                newEv.semesterId = resolvedSemId
                calendarEvents.append(newEv)
            }
        }

        // 7. Ingest peer pomodoro tasks
        for pt in snapshot.pomodoroTasks {
            guard !tombstoneIds.contains(pt.id.uuidString) else { continue }
            var resolvedSemId = pt.semesterId
            if let pid = pt.semesterId, let mapped = semesterIdMap[pid] {
                resolvedSemId = mapped
            } else if let pid = pt.semesterId, !semesters.contains(where: { $0.id == pid }), let defaultSem = semesters.first {
                resolvedSemId = defaultSem.id
            }
            let ptTitle = pt.title.trimmingCharacters(in: .whitespacesAndNewlines)

            if let idx = pomodoroTasks.firstIndex(where: { $0.id == pt.id }) {
                var merged = pomodoroTasks[idx]
                merged.semesterId = resolvedSemId
                if pt.isCompleted {
                    merged.isCompleted = true
                    merged.completedAt = pt.completedAt
                }
                merged.completedPomodoros = max(merged.completedPomodoros, pt.completedPomodoros)
                merged.totalFocusMinutes = max(merged.totalFocusMinutes, pt.totalFocusMinutes)
                pomodoroTasks[idx] = merged
            } else if let idx = pomodoroTasks.firstIndex(where: {
                $0.semesterId == resolvedSemId && $0.title.trimmingCharacters(in: .whitespacesAndNewlines) == ptTitle
            }) {
                var merged = pomodoroTasks[idx]
                if pt.isCompleted {
                    merged.isCompleted = true
                    merged.completedAt = pt.completedAt
                }
                merged.completedPomodoros = max(merged.completedPomodoros, pt.completedPomodoros)
                merged.totalFocusMinutes = max(merged.totalFocusMinutes, pt.totalFocusMinutes)
                pomodoroTasks[idx] = merged
            } else {
                var newT = pt
                newT.semesterId = resolvedSemId
                pomodoroTasks.append(newT)
            }
        }

        // 8. Ingest pomodoro records
        for pr in snapshot.pomodoroRecords {
            guard !tombstoneIds.contains(pr.id.uuidString) else { continue }
            if !pomodoroRecords.contains(where: { $0.id == pr.id }) {
                pomodoroRecords.append(pr)
            }
        }

        // 9. Ingest timetable profiles
        for pp in snapshot.timetableProfiles {
            guard !tombstoneIds.contains(pp.id.uuidString) else { continue }
            if let idx = timetableProfiles.firstIndex(where: { $0.id == pp.id }) {
                timetableProfiles[idx] = pp
            } else {
                timetableProfiles.append(pp)
            }
        }
    }

    private func applyPeerOperation(
        _ op: SyncOperation,
        courses: inout [Course],
        exceptions: inout [ScheduleException],
        semesters: inout [Semester],
        calendarEvents: inout [CalendarEvent],
        pomodoroTasks: inout [PomodoroTask],
        pomodoroRecords: inout [PomodoroRecord],
        timetableProfiles: inout [TimetableProfile],
        tombstones: inout [SyncTombstone],
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
                tombstones.append(SyncTombstone(entityId: op.entityId.uuidString, entityType: "course"))
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
                tombstones.append(SyncTombstone(entityId: op.entityId.uuidString, entityType: "exception"))
            default:
                break
            }
        } else if op.entityType == "semester" {
            switch op.action {
            case "INSERT":
                if let payload = op.payload,
                   let peerSem = try? decoder.decode(Semester.self, from: payload) {
                    if !semesters.contains(where: { $0.id == peerSem.id }) {
                        semesters.append(peerSem)
                    }
                }
            case "UPDATE":
                if let payload = op.payload,
                   let peerSem = try? decoder.decode(Semester.self, from: payload) {
                    if let idx = semesters.firstIndex(where: { $0.id == peerSem.id }) {
                        semesters[idx] = peerSem
                    } else {
                        semesters.append(peerSem)
                    }
                }
            case "DELETE":
                semesters.removeAll(where: { $0.id == op.entityId })
                tombstones.append(SyncTombstone(entityId: op.entityId.uuidString, entityType: "semester"))
            default:
                break
            }
        } else if op.entityType == "calendarEvent" {
            switch op.action {
            case "INSERT":
                if let payload = op.payload,
                   let peerEvent = try? decoder.decode(CalendarEvent.self, from: payload) {
                    if !calendarEvents.contains(where: { $0.id == peerEvent.id }) {
                        calendarEvents.append(peerEvent)
                    }
                }
            case "UPDATE":
                if let payload = op.payload,
                   let peerEvent = try? decoder.decode(CalendarEvent.self, from: payload) {
                    if let idx = calendarEvents.firstIndex(where: { $0.id == peerEvent.id }) {
                        calendarEvents[idx] = peerEvent
                    } else {
                        calendarEvents.append(peerEvent)
                    }
                }
            case "DELETE":
                calendarEvents.removeAll(where: { $0.id == op.entityId })
                tombstones.append(SyncTombstone(entityId: op.entityId.uuidString, entityType: "calendarEvent"))
            default:
                break
            }
        } else if op.entityType == "pomodoroTask" {
            switch op.action {
            case "INSERT":
                if let payload = op.payload,
                   let peerTask = try? decoder.decode(PomodoroTask.self, from: payload) {
                    if !pomodoroTasks.contains(where: { $0.id == peerTask.id }) {
                        pomodoroTasks.append(peerTask)
                    }
                }
            case "UPDATE":
                if let payload = op.payload,
                   let peerTask = try? decoder.decode(PomodoroTask.self, from: payload) {
                    if let idx = pomodoroTasks.firstIndex(where: { $0.id == peerTask.id }) {
                        var merged = pomodoroTasks[idx]
                        if peerTask.isCompleted {
                            merged.isCompleted = true
                            merged.completedAt = peerTask.completedAt
                        }
                        merged.completedPomodoros = max(merged.completedPomodoros, peerTask.completedPomodoros)
                        merged.totalFocusMinutes = max(merged.totalFocusMinutes, peerTask.totalFocusMinutes)
                        pomodoroTasks[idx] = merged
                    } else {
                        pomodoroTasks.append(peerTask)
                    }
                }
            case "DELETE":
                pomodoroTasks.removeAll(where: { $0.id == op.entityId })
                tombstones.append(SyncTombstone(entityId: op.entityId.uuidString, entityType: "pomodoroTask"))
            default:
                break
            }
        } else if op.entityType == "pomodoroRecord" {
            if op.action == "INSERT", let payload = op.payload,
               let peerRecord = try? decoder.decode(PomodoroRecord.self, from: payload) {
                if !pomodoroRecords.contains(where: { $0.id == peerRecord.id }) {
                    pomodoroRecords.append(peerRecord)
                }
            }
        } else if op.entityType == "timetableProfile" {
            switch op.action {
            case "INSERT", "UPDATE":
                if let payload = op.payload,
                   let peerProfile = try? decoder.decode(TimetableProfile.self, from: payload) {
                    if let idx = timetableProfiles.firstIndex(where: { $0.id == peerProfile.id }) {
                        timetableProfiles[idx] = peerProfile
                    } else {
                        timetableProfiles.append(peerProfile)
                    }
                }
            case "DELETE":
                timetableProfiles.removeAll(where: { $0.id == op.entityId })
                tombstones.append(SyncTombstone(entityId: op.entityId.uuidString, entityType: "timetableProfile"))
            default:
                break
            }
        }
    }

    public static func consolidateSchedules(_ schedules: [CourseSchedule]) -> [CourseSchedule] {
        if schedules.count <= 1 { return schedules }
        let grouped = Dictionary(grouping: schedules, by: { $0.dayOfWeek })
        var result: [CourseSchedule] = []

        for (_, dayScheds) in grouped {
            var pending = dayScheds.sorted(by: { $0.startSection < $1.startSection })
            var i = 0
            while i < pending.count {
                var current = pending[i]
                var mergedAny = false
                var j = i + 1
                while j < pending.count {
                    let candidate = pending[j]
                    let overlaps = max(current.startSection, candidate.startSection) <= min(current.endSection, candidate.endSection)
                    if overlaps {
                        let w1 = current.weekPattern.customWeeks ?? []
                        let w2 = candidate.weekPattern.customWeeks ?? []
                        let mergedWeeks = Array(Set(w1 + w2)).sorted()
                        current.startSection = min(current.startSection, candidate.startSection)
                        current.endSection = max(current.endSection, candidate.endSection)
                        if current.classroom == nil || current.classroom!.isEmpty {
                            current.classroom = candidate.classroom
                        }
                        if !mergedWeeks.isEmpty {
                            current.weekPattern.customWeeks = mergedWeeks
                        }
                        pending.remove(at: j)
                        pending[i] = current
                        mergedAny = true
                    } else {
                        j += 1
                    }
                }
                if !mergedAny {
                    result.append(current)
                    i += 1
                }
            }
        }
        return result.sorted(by: { $0.dayOfWeek == $1.dayOfWeek ? $0.startSection < $1.startSection : $0.dayOfWeek < $1.dayOfWeek })
    }

    public static func consolidateCourses(_ courses: [Course]) -> [Course] {
        if courses.count <= 1 {
            return courses.map {
                var c = $0
                c.schedules = consolidateSchedules(c.schedules)
                return c
            }
        }
        var grouped: [String: [Course]] = [:]
        for c in courses {
            let key = "\(c.semesterId.uuidString)___\(c.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())"
            grouped[key, default: []].append(c)
        }

        var result: [Course] = []
        for (_, group) in grouped {
            if group.count == 1 {
                var c = group[0]
                c.schedules = consolidateSchedules(c.schedules)
                result.append(c)
            } else {
                var first = group[0]
                let allSchedules = group.flatMap { $0.schedules }
                first.schedules = consolidateSchedules(allSchedules)
                if first.teacher == nil || first.teacher!.isEmpty {
                    first.teacher = group.first(where: { $0.teacher != nil && !$0.teacher!.isEmpty })?.teacher
                }
                if first.notes == nil || first.notes!.isEmpty {
                    first.notes = group.first(where: { $0.notes != nil && !$0.notes!.isEmpty })?.notes
                }
                result.append(first)
            }
        }
        return result
    }
}
