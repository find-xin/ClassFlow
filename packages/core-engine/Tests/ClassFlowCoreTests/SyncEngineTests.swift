import Foundation
import Testing
@testable import ClassFlowCore

@Suite("SyncEngineTests")
struct SyncEngineTests {
    @Test("Multi-device decentralized sync simulation")
    func testMultiDeviceSyncSimulation() async throws {
        let storage = MockStorageProvider()
        let encoder = JSONEncoder()

        // Device A (Mac)
        let engineA = SyncEngine(deviceId: "dev_mac_01", deviceName: "MacBook Pro")
        var coursesA: [Course] = []
        var exceptionsA: [ScheduleException] = []

        // Device B (Android)
        let engineB = SyncEngine(deviceId: "dev_droid_02", deviceName: "Xiaomi 15")
        var coursesB: [Course] = []
        var exceptionsB: [ScheduleException] = []

        // 1. Device A creates Course 1 offline
        let course1Id = UUID()
        let course1 = Course(
            id: course1Id,
            semesterId: UUID(),
            name: "高等数学 (Mac创建)",
            teacher: "张教授",
            color: "#4F46E5"
        )
        coursesA.append(course1)
        _ = engineA.recordOperation(
            action: "INSERT",
            entityType: "course",
            entityId: course1Id,
            payload: try encoder.encode(course1)
        )

        // 2. Device B creates Course 2 offline
        let course2Id = UUID()
        let course2 = Course(
            id: course2Id,
            semesterId: UUID(),
            name: "大学物理 (手机创建)",
            teacher: "李教授",
            color: "#0D9488"
        )
        coursesB.append(course2)
        _ = engineB.recordOperation(
            action: "INSERT",
            entityType: "course",
            entityId: course2Id,
            payload: try encoder.encode(course2)
        )

        // 3. Device A syncs first
        let summaryA1 = try await engineA.executeSync(storage: storage, courses: &coursesA, exceptions: &exceptionsA)
        #expect(summaryA1.uploadedOperationsCount == 1)
        #expect(summaryA1.downloadedPeerOperationsCount == 0)

        // 4. Device B syncs: uploads Course 2, downloads Course 1
        let summaryB1 = try await engineB.executeSync(storage: storage, courses: &coursesB, exceptions: &exceptionsB)
        #expect(summaryB1.uploadedOperationsCount == 1)
        #expect(summaryB1.downloadedPeerOperationsCount == 1)

        // Device B now has BOTH courses!
        #expect(coursesB.count == 2)
        #expect(coursesB.contains(where: { $0.id == course1Id }))
        #expect(coursesB.contains(where: { $0.id == course2Id }))

        // 5. Device A syncs again: downloads Course 2
        let summaryA2 = try await engineA.executeSync(storage: storage, courses: &coursesA, exceptions: &exceptionsA)
        #expect(summaryA2.uploadedOperationsCount == 0)
        #expect(summaryA2.downloadedPeerOperationsCount == 1)

        // Device A now has BOTH courses!
        #expect(coursesA.count == 2)
        #expect(coursesA.contains(where: { $0.id == course1Id }))
        #expect(coursesA.contains(where: { $0.id == course2Id }))
    }

    @Test("Field-level 3-way merge on concurrent edits")
    func testFieldLevel3WayMerge() async throws {
        let storage = MockStorageProvider()
        let encoder = JSONEncoder()

        let engineA = SyncEngine(deviceId: "dev_mac_01", deviceName: "Mac")
        let engineB = SyncEngine(deviceId: "dev_droid_02", deviceName: "Phone")

        let sharedCourseId = UUID()
        let courseA = Course(id: sharedCourseId, semesterId: UUID(), name: "软件工程", teacher: "原教师", color: "#111111")
        var courseB = courseA

        var coursesA = [courseA]
        var coursesB = [courseB]
        var exceptionsA: [ScheduleException] = []
        var exceptionsB: [ScheduleException] = []

        // Device B modifies teacher offline
        courseB.teacher = "新教师王工"
        coursesB[0] = courseB
        _ = engineB.recordOperation(
            action: "UPDATE",
            entityType: "course",
            entityId: sharedCourseId,
            payload: try encoder.encode(courseB)
        )
        _ = try await engineB.executeSync(storage: storage, courses: &coursesB, exceptions: &exceptionsB)

        // Device A syncs and merges teacher update
        _ = try await engineA.executeSync(storage: storage, courses: &coursesA, exceptions: &exceptionsA)
        #expect(coursesA[0].teacher == "新教师王工")
    }

    @Test("Delete vs Modify conflict retains entity")
    func testDeleteVsModifyConflict() async throws {
        let storage = MockStorageProvider()
        let encoder = JSONEncoder()

        let engineA = SyncEngine(deviceId: "dev_mac_01", deviceName: "Mac")
        let engineB = SyncEngine(deviceId: "dev_droid_02", deviceName: "Phone")

        let targetId = UUID()
        let course = Course(id: targetId, semesterId: UUID(), name: "现代密码学", color: "#222222")

        var coursesA = [course]
        var coursesB = [course]
        var exceptionsA: [ScheduleException] = []
        var exceptionsB: [ScheduleException] = []

        // Device A deletes course offline
        coursesA.removeAll()
        _ = engineA.recordOperation(action: "DELETE", entityType: "course", entityId: targetId)

        // Device B modifies course offline
        var updatedCourse = course
        updatedCourse.name = "现代密码学 (高级班)"
        coursesB[0] = updatedCourse
        _ = engineB.recordOperation(action: "UPDATE", entityType: "course", entityId: targetId, payload: try encoder.encode(updatedCourse))

        // Both sync
        _ = try await engineA.executeSync(storage: storage, courses: &coursesA, exceptions: &exceptionsA)
        _ = try await engineB.executeSync(storage: storage, courses: &coursesB, exceptions: &exceptionsB)

        // Device A pulls Device B's modification: modify wins over delete!
        _ = try await engineA.executeSync(storage: storage, courses: &coursesA, exceptions: &exceptionsA)
        #expect(coursesA.count == 1)
        #expect(coursesA[0].name == "现代密码学 (高级班)")
    }

    @Test("Complete multi-module sync for courses, todos, records, and timetable profiles")
    func testFullMultiModuleSyncAcrossDevices() async throws {
        let storage = MockStorageProvider()

        let engineA = SyncEngine(deviceId: "dev_mac_01", deviceName: "Mac")
        let engineB = SyncEngine(deviceId: "dev_droid_02", deviceName: "Phone")

        var coursesA: [Course] = [Course(id: UUID(), semesterId: UUID(), name: "操作系统", color: "#4F46E5")]
        var exceptionsA: [ScheduleException] = []
        var semestersA: [Semester] = [Semester(id: UUID(), name: "秋季学期", startDate: "2026-09-01", endDate: "2027-01-20")]
        var eventsA: [CalendarEvent] = [CalendarEvent(id: UUID(), title: "实验室研讨会", startTime: "14:00", endTime: "16:00")]
        var tasksA: [PomodoroTask] = [PomodoroTask(title: "复习OS调度算法", estimatedPomodoros: 3)]
        var recordsA: [PomodoroRecord] = [PomodoroRecord(taskTitle: "复习OS调度算法", durationMinutes: 25, completedAt: Date())]
        var profilesA: [TimetableProfile] = [TimetableProfile.standardDefault()]

        var coursesB: [Course] = []
        var exceptionsB: [ScheduleException] = []
        var semestersB: [Semester] = []
        var eventsB: [CalendarEvent] = []
        var tasksB: [PomodoroTask] = []
        var recordsB: [PomodoroRecord] = []
        var profilesB: [TimetableProfile] = []

        // 1. Device A syncs first with zero pending operations (existing database items)
        let summaryA = try await engineA.executeSync(
            storage: storage,
            courses: &coursesA,
            exceptions: &exceptionsA,
            semesters: &semestersA,
            calendarEvents: &eventsA,
            pomodoroTasks: &tasksA,
            pomodoroRecords: &recordsA,
            timetableProfiles: &profilesA
        )
        // Verify snapshot and manifest are published to storage
        let snapData = try await storage.readObject(path: "devices/dev_mac_01/snapshots/snapshot.json")
        #expect(snapData.count > 0)
        let manifestData = try await storage.readObject(path: "devices/dev_mac_01/device_manifest.json")
        #expect(manifestData.count > 0)

        // 2. Device B syncs and bootstraps from Device A's snapshot
        let summaryB = try await engineB.executeSync(
            storage: storage,
            courses: &coursesB,
            exceptions: &exceptionsB,
            semesters: &semestersB,
            calendarEvents: &eventsB,
            pomodoroTasks: &tasksB,
            pomodoroRecords: &recordsB,
            timetableProfiles: &profilesB
        )
        #expect(summaryB.downloadedPeerOperationsCount == 1)
        #expect(coursesB.count == 1)
        #expect(coursesB[0].name == "操作系统")
        #expect(semestersB.count == 1)
        #expect(eventsB.count == 1)
        #expect(eventsB[0].title == "实验室研讨会")
        #expect(tasksB.count == 1)
        #expect(tasksB[0].title == "复习OS调度算法")
        #expect(recordsB.count == 1)
        #expect(profilesB.count == 1)
    }

    @Test("Tombstone deletion propagation across devices with zero resurrection")
    func testTombstoneDeletionPropagationAndNoResurrection() async throws {
        let storage = MockStorageProvider()

        let engineA = SyncEngine(deviceId: "mac_01", deviceName: "MacBook Pro")
        let engineB = SyncEngine(deviceId: "android_02", deviceName: "Xiaomi Phone")

        let c1Id = UUID()
        let c2Id = UUID()
        let course1 = Course(id: c1Id, semesterId: UUID(), name: "高数", color: "#4F46E5")
        let course2 = Course(id: c2Id, semesterId: UUID(), name: "英语", color: "#0D9488")

        var coursesA = [course1, course2]
        var tombstonesA: [SyncTombstone] = []
        var dummyExA: [ScheduleException] = []
        var dummySemA: [Semester] = []
        var dummyEvA: [CalendarEvent] = []
        var dummyTaskA: [PomodoroTask] = []
        var dummyRecA: [PomodoroRecord] = []
        var dummyProfA: [TimetableProfile] = []

        var coursesB: [Course] = []
        var tombstonesB: [SyncTombstone] = []
        var dummyExB: [ScheduleException] = []
        var dummySemB: [Semester] = []
        var dummyEvB: [CalendarEvent] = []
        var dummyTaskB: [PomodoroTask] = []
        var dummyRecB: [PomodoroRecord] = []
        var dummyProfB: [TimetableProfile] = []

        // 1. Device A uploads snapshot with 2 courses
        _ = try await engineA.executeSync(
            storage: storage,
            courses: &coursesA,
            exceptions: &dummyExA,
            semesters: &dummySemA,
            calendarEvents: &dummyEvA,
            pomodoroTasks: &dummyTaskA,
            pomodoroRecords: &dummyRecA,
            timetableProfiles: &dummyProfA,
            tombstones: &tombstonesA
        )

        // 2. Device B downloads snapshot
        _ = try await engineB.executeSync(
            storage: storage,
            courses: &coursesB,
            exceptions: &dummyExB,
            semesters: &dummySemB,
            calendarEvents: &dummyEvB,
            pomodoroTasks: &dummyTaskB,
            pomodoroRecords: &dummyRecB,
            timetableProfiles: &dummyProfB,
            tombstones: &tombstonesB
        )
        #expect(coursesB.count == 2)

        // 3. Device A deletes Course 1 and creates a tombstone
        coursesA.removeAll(where: { $0.id == c1Id })
        tombstonesA.append(SyncTombstone(entityId: c1Id.uuidString, entityType: "course"))
        #expect(coursesA.count == 1)

        // 4. Device A syncs: uploads new snapshot containing tombstone
        _ = try await engineA.executeSync(
            storage: storage,
            courses: &coursesA,
            exceptions: &dummyExA,
            semesters: &dummySemA,
            calendarEvents: &dummyEvA,
            pomodoroTasks: &dummyTaskA,
            pomodoroRecords: &dummyRecA,
            timetableProfiles: &dummyProfA,
            tombstones: &tombstonesA
        )

        // 5. Device B syncs: ingests Device A's updated snapshot & tombstones
        _ = try await engineB.executeSync(
            storage: storage,
            courses: &coursesB,
            exceptions: &dummyExB,
            semesters: &dummySemB,
            calendarEvents: &dummyEvB,
            pomodoroTasks: &dummyTaskB,
            pomodoroRecords: &dummyRecB,
            timetableProfiles: &dummyProfB,
            tombstones: &tombstonesB
        )

        // Course 1 must be purged from Device B!
        #expect(coursesB.count == 1)
        #expect(coursesB[0].id == c2Id)
        #expect(tombstonesB.contains(where: { $0.entityId == c1Id.uuidString }))

        // 6. Device B syncs again (simulating subsequent sync cycles)
        _ = try await engineB.executeSync(
            storage: storage,
            courses: &coursesB,
            exceptions: &dummyExB,
            semesters: &dummySemB,
            calendarEvents: &dummyEvB,
            pomodoroTasks: &dummyTaskB,
            pomodoroRecords: &dummyRecB,
            timetableProfiles: &dummyProfB,
            tombstones: &tombstonesB
        )

        // 7. Device A syncs again
        _ = try await engineA.executeSync(
            storage: storage,
            courses: &coursesA,
            exceptions: &dummyExA,
            semesters: &dummySemA,
            calendarEvents: &dummyEvA,
            pomodoroTasks: &dummyTaskA,
            pomodoroRecords: &dummyRecA,
            timetableProfiles: &dummyProfA,
            tombstones: &tombstonesA
        )

        // VERIFY: Course 1 NEVER resurrected!
        #expect(coursesA.count == 1)
        #expect(coursesA[0].id == c2Id)
        #expect(coursesB.count == 1)
        #expect(coursesB[0].id == c2Id)
    }

    @Test("Tombstone Garbage Collection prunes expired records and caps maximum capacity")
    func testTombstoneGarbageCollection() {
        let now = Date()
        let nowMs = Int64(now.timeIntervalSince1970 * 1000)
        let dayMs: Int64 = 86_400_000

        // 1. Fresh tombstone (1 day ago)
        let freshTombstone = SyncTombstone(entityId: "fresh", entityType: "course", deletedAt: nowMs - dayMs)
        // 2. Expired tombstone (95 days ago)
        let expiredTombstone = SyncTombstone(entityId: "expired", entityType: "course", deletedAt: nowMs - (95 * dayMs))

        let gced = SyncEngine.garbageCollectTombstones([freshTombstone, expiredTombstone], referenceDate: now)
        #expect(gced.count == 1)
        #expect(gced[0].entityId == "fresh")

        // 3. Overflow test: over maxTombstonesCount
        var manyTombstones: [SyncTombstone] = []
        for i in 0..<1100 {
            manyTombstones.append(SyncTombstone(entityId: "item_\(i)", entityType: "course", deletedAt: nowMs - Int64(i * 1000)))
        }
        let capped = SyncEngine.garbageCollectTombstones(manyTombstones, referenceDate: now)
        #expect(capped.count == 1000)
        #expect(capped[0].entityId == "item_0")
    }

    @Test("Cross-platform Android snapshot with non-standard IDs and missing sections successfully decoded and ingested")
    func testCrossPlatformAndroidSnapshotIngestion() async throws {
        let androidJson = """
        {
          "deviceId": "android_test_dev",
          "sequence": 5,
          "semesters": [
            {
              "id": "sem_main",
              "name": "2026-2027 秋季学期",
              "startDate": "2026-09-01",
              "endDate": "2027-01-20",
              "totalWeeks": 16,
              "isCurrent": true,
              "timeZone": "Asia/Shanghai"
            }
          ],
          "courses": [
            {
              "id": "c_math",
              "semesterId": "sem_main",
              "name": "考试",
              "teacher": "张老师",
              "color": "#FF5A5F",
              "notes": "重要期末考试",
              "schedules": [
                {
                  "id": "s_1",
                  "dayOfWeek": 1,
                  "startSection": 1,
                  "endSection": 1,
                  "classroom": "东101",
                  "weekPattern": "ALL",
                  "customWeeks": [1, 2, 3]
                }
              ]
            }
          ],
          "schedules": [
            {
              "id": "ev_1",
              "semesterId": "sem_main",
              "title": "晨考",
              "startTime": "08:00",
              "endTime": "09:00",
              "isAllDay": false,
              "location": "一教",
              "description": "准时参加",
              "color": "#FF5A5F",
              "frequency": "none"
            }
          ],
          "studyTasks": [
            {
              "id": "task_1",
              "semesterId": "sem_main",
              "title": "复习高数",
              "courseName": "考试",
              "targetMinutes": 45,
              "completedMinutes": 20,
              "isCompleted": false
            }
          ]
        }
        """

        let data = androidJson.data(using: .utf8)!
        let decoder = JSONDecoder()
        let snapshot = try decoder.decode(DeviceSnapshot.self, from: data)

        #expect(snapshot.deviceId == "android_test_dev")
        #expect(snapshot.semesters.count == 1)
        #expect(snapshot.courses.count == 1)
        #expect(snapshot.calendarEvents.count == 1)
        #expect(snapshot.pomodoroTasks.count == 1)
        #expect(snapshot.courses[0].name == "考试")
        #expect(snapshot.courses[0].semesterId == snapshot.semesters[0].id)

        // Now test ingest into empty Mac state
        let engine = SyncEngine(deviceId: "mac_test_dev", deviceName: "MacBook Pro")
        var localCourses: [Course] = []
        var localExceptions: [ScheduleException] = []
        var localSemesters: [Semester] = []
        var localCalendarEvents: [CalendarEvent] = []
        var localPomodoroTasks: [PomodoroTask] = []
        var localPomodoroRecords: [PomodoroRecord] = []
        var localProfiles: [TimetableProfile] = []
        var localTombstones: [SyncTombstone] = []

        let mockStorage = MockStorageProvider()
        // Register android device
        var registry = DeviceRegistry()
        registry.devices["android_test_dev"] = "Android Phone"
        registry.devices["mac_test_dev"] = "MacBook Pro"
        try await mockStorage.writeObject(path: "global/device_registry.json", data: try JSONEncoder().encode(registry), metadata: nil)

        // Write android manifest & snapshot
        let androidManifest = DeviceSyncManifest(
            schemaVersion: 1,
            deviceId: "android_test_dev",
            deviceName: "Android Phone",
            latestSequence: 5,
            latestRevisionFile: "revisions/000005_test.json",
            snapshotFile: "snapshots/snapshot.json"
        )
        try await mockStorage.writeObject(path: "devices/android_test_dev/device_manifest.json", data: try JSONEncoder().encode(androidManifest), metadata: nil)
        try await mockStorage.writeObject(path: "devices/android_test_dev/snapshots/snapshot.json", data: data, metadata: nil)

        // Execute Mac sync
        let summary = try await engine.executeSync(
            storage: mockStorage,
            courses: &localCourses,
            exceptions: &localExceptions,
            semesters: &localSemesters,
            calendarEvents: &localCalendarEvents,
            pomodoroTasks: &localPomodoroTasks,
            pomodoroRecords: &localPomodoroRecords,
            timetableProfiles: &localProfiles,
            tombstones: &localTombstones
        )

        #expect(summary.downloadedPeerOperationsCount > 0)
        #expect(localCourses.count == 1)
        #expect(localCourses[0].name == "考试")
        #expect(localSemesters.count == 1)
        #expect(localSemesters[0].name == "2026-2027 秋季学期")
        #expect(localCalendarEvents.count == 1)
        #expect(localCalendarEvents[0].title == "晨考")
        #expect(localPomodoroTasks.count == 1)
        #expect(localPomodoroTasks[0].title == "复习高数")
    }

    @Test("Overlapping course sessions on same day are merged rather than duplicated into 2-overlap cards")
    func testScheduleOverlapMergeOnSync() async throws {
        let semId = UUID()
        let c1Id = UUID()
        let s1 = CourseSchedule(dayOfWeek: 5, startSection: 1, endSection: 1, classroom: "A101")
        let localCourse = Course(id: c1Id, semesterId: semId, name: "考试", schedules: [s1])

        let s2 = CourseSchedule(dayOfWeek: 5, startSection: 1, endSection: 2, classroom: "A101")
        let peerCourse = Course(id: UUID(), semesterId: semId, name: "考试", schedules: [s2])

        var courses = [localCourse]
        var exceptions: [ScheduleException] = []
        var semesters = [Semester(id: semId, name: "秋季学期", startDate: "2026-09-01", endDate: "2027-01-20")]
        var calendarEvents: [CalendarEvent] = []
        var tasks: [PomodoroTask] = []
        var records: [PomodoroRecord] = []
        var profiles: [TimetableProfile] = []
        var tombstones: [SyncTombstone] = []

        let peerSnapshot = DeviceSnapshot(
            deviceId: "peer_dev",
            sequence: 1,
            courses: [peerCourse],
            semesters: semesters
        )

        let mockStorage = MockStorageProvider()
        var registry = DeviceRegistry()
        registry.devices["peer_dev"] = "Peer Phone"
        registry.devices["my_mac"] = "MacBook"
        try await mockStorage.writeObject(path: "global/device_registry.json", data: try JSONEncoder().encode(registry), metadata: nil)

        let peerManifest = DeviceSyncManifest(
            schemaVersion: 1,
            deviceId: "peer_dev",
            deviceName: "Peer Phone",
            latestSequence: 1,
            latestRevisionFile: "revisions/000001_test.json",
            snapshotFile: "snapshots/snapshot.json"
        )
        try await mockStorage.writeObject(path: "devices/peer_dev/device_manifest.json", data: try JSONEncoder().encode(peerManifest), metadata: nil)
        try await mockStorage.writeObject(path: "devices/peer_dev/snapshots/snapshot.json", data: try JSONEncoder().encode(peerSnapshot), metadata: nil)

        let engine = SyncEngine(deviceId: "my_mac", deviceName: "MacBook")
        _ = try await engine.executeSync(
            storage: mockStorage,
            courses: &courses,
            exceptions: &exceptions,
            semesters: &semesters,
            calendarEvents: &calendarEvents,
            pomodoroTasks: &tasks,
            pomodoroRecords: &records,
            timetableProfiles: &profiles,
            tombstones: &tombstones
        )

        #expect(courses.count == 1)
        // VERIFY: schedules on Friday merged into 1 continuous span 1..2 instead of 2 distinct schedules
        #expect(courses[0].schedules.count == 1)
        #expect(courses[0].schedules[0].dayOfWeek == 5)
        #expect(courses[0].schedules[0].startSection == 1)
        #expect(courses[0].schedules[0].endSection == 2)
    }
}

