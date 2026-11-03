import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

struct CalendarCancellationMaintenanceTests {
    @Test func atomicCleanupBackupRestartQueryAndStalePreview() throws {
        let (server, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        var payload = db.load()
        let event = CalendarEvent(semesterId: payload.semesters[0].id, title: "Series", startTime: "2026-10-19T09:00:00", endTime: "2026-10-19T10:00:00", frequency: "daily", endRepeatType: "count", endRepeatCount: 3)
        let cancel = ScheduleException(scheduleId: event.id, originalDate: "2026-10-20", exceptionType: .cancel, reason: "单次删除")
        payload.calendarEvents = [event]; payload.exceptions = [cancel]
        try db.save(payload)
        let plan = CalendarCancellationCleanup.preview(events: payload.calendarEvents, exceptions: payload.exceptions, courseScheduleIDs: [])
        #expect(db.load().exceptions == [cancel])
        try db.applyCalendarCancellationCleanup(plan)
        let reopened = LocalDatabase(customURL: db.fileURL)
        #expect(reopened.load().exceptions.isEmpty && reopened.load().calendarEvents[0].excludedDates == ["2026-10-20"])
        #expect(reopened.load().tombstones.contains { $0.entityId == cancel.id.uuidString && $0.entityType == "exception" })
        let backupFolder = root.appendingPathComponent("maintenance-backups")
        let backupURL = try #require(FileManager.default.contentsOfDirectory(at: backupFolder, includingPropertiesForKeys: nil).first)
        let backup = try JSONDecoder().decode(LocalDatabase.DatabasePayload.self, from: Data(contentsOf: backupURL))
        #expect(backup.calendarEvents == [event] && backup.exceptions == [cancel])
        #expect(throws: (any Error).self) { try reopened.applyCalendarCancellationCleanup(plan) }
        let response = try server.invokeTool(name: "list_calendar_events", arguments: ["date": "2026-10-20"])
        #expect(!response.contains("Series"))
        let context = try server.invokeTool(name: "get_editing_context", arguments: ["kind": "events"])
        #expect(context.contains("excludedDates") && context.contains("2026-10-20"))
        #expect(CalendarEventProjector().project(reopened.load().calendarEvents[0], semester: payload.semesters[0], week: 1).map(\.date) == ["2026-10-19", "2026-10-21"])
    }

    @Test func mcpOneOffExclusionPreservesRescheduleAndFutureCount() throws {
        let (server, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_calendar_event", arguments: ["title": "Series", "date": "2026-10-19", "frequency": "daily", "end_repeat_type": "count", "end_repeat_count": 4])
        let event = try #require(db.load().calendarEvents.first)
        var payload = db.load()
        let moved = ScheduleException(scheduleId: event.id, originalDate: "2026-10-20", exceptionType: .reschedule, targetDate: "2026-10-25")
        payload.exceptions = [moved]; try db.save(payload)
        _ = try server.invokeTool(name: "delete_calendar_event", arguments: ["event_id": event.id.uuidString, "scope": "this_only", "occurrence_date": "2026-10-20"])
        #expect(db.load().exceptions == [moved])
        #expect(db.load().calendarEvents[0].excludedDates == ["2026-10-20"])
        _ = try server.invokeTool(name: "update_calendar_event", arguments: ["event_id": event.id.uuidString, "scope": "this_and_future", "occurrence_date": "2026-10-21", "new_title": "Future"])
        let successor = try #require(db.load().calendarEvents.first { $0.title == "Future" })
        #expect(successor.endRepeatCount == 2)
    }

    @Test func rebaseUnionsConcurrentExclusions() throws {
        let base = CalendarEvent(title: "Series", startTime: "09:00", endTime: "10:00")
        var local = base, remote = base
        local.excludedDates = ["2026-11-02"]; remote.excludedDates = ["2026-11-03"]
        let merged = try LocalSyncRebase.merge(baseline: [base], local: [local], remote: [remote])
        #expect(merged[0].excludedDates == ["2026-11-02", "2026-11-03"])
        #expect(CalendarEditorPreservation.apply(original: local, draft: base).excludedDates == local.excludedDates)
    }

    @Test @MainActor func ordinaryEditRestartAndOldPeerSyncCannotReviveExcludedDate() async throws {
        let (_, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        var payload = db.load()
        let event = CalendarEvent(semesterId: payload.semesters[0].id, title: "Series", startTime: "2026-10-19T09:00:00", endTime: "2026-10-19T10:00:00", frequency: "daily", endRepeatType: "count", endRepeatCount: 3)
        payload.calendarEvents = [event]
        payload.exceptions = [ScheduleException(scheduleId: event.id, originalDate: "2026-10-20", exceptionType: .cancel)]
        try db.save(payload)
        let app = AppState(database: db, notificationService: MockNotificationService(), widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")))
        try await app.applyCalendarCancellationCleanup(app.previewCalendarCancellationCleanup())
        app.updateCalendarEvent(event) // stale editor, predating migration
        #expect(db.load().calendarEvents[0].excludedDates == ["2026-10-20"])
        let storage = MockStorageProvider(), peer = SyncEngine(deviceId: "old-calendar-peer", deviceName: "Old client", initialSequence: 1)
        var events = [event], courses: [Course] = [], exceptions: [ScheduleException] = [], semesters = payload.semesters
        _ = try await peer.executeSync(storage: storage, courses: &courses, exceptions: &exceptions, semesters: &semesters, calendarEvents: &events)
        _ = try await app.executeCloudSync(storage: storage)
        #expect(db.load().calendarEvents[0].excludedDates == ["2026-10-20"])
        app.load()
        #expect(!app.currentWeekOccurrences.contains { $0.calendarEventId == event.id && $0.date == "2026-10-20" })
    }
}

extension CalendarCancellationMaintenanceTests {
    @Test func failedBackupLeavesDatabaseUntouched() throws {
        let (_, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        var payload = db.load()
        let event = CalendarEvent(title: "Series", startTime: "2026-10-19T09:00:00", endTime: "2026-10-19T10:00:00", frequency: "daily")
        payload.calendarEvents = [event]
        payload.exceptions = [ScheduleException(scheduleId: event.id, originalDate: "2026-10-20", exceptionType: .cancel)]
        try db.save(payload)
        let original = try Data(contentsOf: db.fileURL)
        // A regular file blocks creation of the backup directory, simulating an I/O failure.
        try Data("blocked".utf8).write(to: root.appendingPathComponent("maintenance-backups"))
        let plan = CalendarCancellationCleanup.preview(events: payload.calendarEvents, exceptions: payload.exceptions, courseScheduleIDs: [])
        #expect(throws: (any Error).self) { try db.applyCalendarCancellationCleanup(plan) }
        #expect(try Data(contentsOf: db.fileURL) == original)
    }
}

extension CalendarCancellationMaintenanceTests {
    @Test @MainActor func nativeICSImportRetainsSeriesIdentityInsteadOfCreatingCourses() throws {
        let (_, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let app = AppState(database: db, notificationService: MockNotificationService(), widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")))
        let event = CalendarEvent(semesterId: app.currentSemester?.id, title: "Series", startTime: "2026-10-19T09:00:00", endTime: "2026-10-19T10:00:00", frequency: "daily", endRepeatType: "count", endRepeatCount: 3, excludedDates: ["2026-10-20"])
        app.addCalendarEvent(event)
        let ics = try #require(app.exportCurrentSemesterICS())
        app.calendarEvents = []; app.save()
        app.importICS(content: ics)
        app.importICS(content: ics)
        #expect(app.calendarEvents.count == 1 && app.calendarEvents[0].id == event.id)
        #expect(app.calendarEvents[0].excludedDates == ["2026-10-20"] && app.courses.isEmpty)
        #expect(!app.currentWeekOccurrences.contains { $0.date == "2026-10-20" && $0.calendarEventId == event.id })
    }
}

extension CalendarCancellationMaintenanceTests {
    @Test @MainActor func mcpCleanupRequiresCurrentConfirmedPreview() async throws {
        let (_, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        var payload = db.load()
        let event = CalendarEvent(semesterId: payload.semesters[0].id, title: "Series", startTime: "2026-10-19T09:00:00", endTime: "2026-10-19T10:00:00", frequency: "daily")
        payload.calendarEvents = [event]
        payload.exceptions = [ScheduleException(scheduleId: event.id, originalDate: "2026-10-20", exceptionType: .cancel)]
        try db.save(payload)
        let app = AppState(database: db, notificationService: MockNotificationService(), widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")))
        func preview() async throws -> String {
            let json = try await app.executeAutomationTool(name: "preview_calendar_cancellation_cleanup", arguments: [:])
            let result = try JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
            return try #require(result?["preview_token"] as? String)
        }
        let token = try await preview()
        #expect(db.load().exceptions.count == 1)
        await #expect(throws: (any Error).self) {
            try await app.executeAutomationTool(name: "apply_calendar_cancellation_cleanup", arguments: ["preview_token": token, "confirmed": false])
        }
        payload.calendarEvents[0].title = "Changed"
        try db.save(payload)
        await #expect(throws: (any Error).self) {
            try await app.executeAutomationTool(name: "apply_calendar_cancellation_cleanup", arguments: ["preview_token": token, "confirmed": true])
        }
        #expect(db.load().exceptions.count == 1)
        let current = try await preview()
        _ = try await app.executeAutomationTool(name: "apply_calendar_cancellation_cleanup", arguments: ["preview_token": current, "confirmed": true])
        #expect(db.load().exceptions.isEmpty)
        #expect(db.load().calendarEvents[0].excludedDates == ["2026-10-20"])
    }
}
