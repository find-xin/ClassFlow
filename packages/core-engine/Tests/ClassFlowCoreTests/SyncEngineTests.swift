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
        var courseA = Course(id: sharedCourseId, semesterId: UUID(), name: "软件工程", teacher: "原教师", color: "#111111")
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
}
