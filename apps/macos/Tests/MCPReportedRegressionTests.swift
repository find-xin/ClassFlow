import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

@Suite("Reported MCP regressions")
struct MCPReportedRegressionTests {
    @Test func dailyDateQueriesIncludeUntilBoundary() throws {
        let (server, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_calendar_event", arguments: ["title":"daily", "date":"2026-10-25", "start_time":"10:00", "end_time":"10:20", "frequency":"daily", "end_repeat_type":"untilDate", "end_repeat_until":"2026-10-27", "alert_minutes_before":10])
        let handler = MCPToolHandlers(database: db, enablesAppIntegration: false)
        for date in ["2026-10-25", "2026-10-26", "2026-10-27"] {
            #expect(handler.listCalendarEvents(date: date).count == 1)
        }
        #expect(handler.listCalendarEvents(date: "2026-10-28").isEmpty)
    }
    @Test @MainActor func reminderPickerMustNotConvertTenMinutesToNone() {
        for value: Int? in [nil, 0, 5, 10, 30] {
            #expect(ScheduleItemCreateSheet.AlertOption(minutesBefore: value).minutesBefore == value)
        }
    }
}

extension MCPReportedRegressionTests {
    @Test @MainActor func arbitraryReminderSurvivesPickerSave() {
        for value: Int? in [nil, 0, 5, 10, 30, 7, 120, 10080] {
            let option = ScheduleItemCreateSheet.AlertOption(minutesBefore: value)
            #expect(option.resolvedMinutes(preserving: value) == value)
        }
    }

    @Test @MainActor func unrelatedAppExecutedOperationsAndReloadPreserveReminder() async throws {
        let (server, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_calendar_event", arguments: ["title":"anchor", "date":"2026-10-25", "alert_minutes_before":10])
        let id = try #require(db.load().calendarEvents.first?.id)
        let app = AppState(database: db, notificationService: MockNotificationService(), widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")))
        func call(_ tool: String, _ arguments: [String: Any]) async throws {
            _ = try await app.executeAutomationTool(name: tool, arguments: arguments)
            let disk = LocalDatabase(customURL: db.fileURL).load()
            #expect(disk.calendarEvents.first(where: { $0.id == id })?.alertMinutesBefore == 10)
        }
        try await call("add_course", ["name":"test"])
        let course = try #require(db.load().courses.first?.id.uuidString)
        try await call("update_course", ["course_id":course, "credit":2])
        try await call("delete_course", ["course_id":course])
        try await call("add_task", ["title":"task"])
        let task = try #require(db.load().pomodoroTasks.first?.id.uuidString)
        try await call("update_task", ["task_id":task,"priority":"high"])
        try await call("complete_task", ["task_id":task])
        try await call("delete_task", ["task_id":task])
        try await call("add_calendar_event", ["title":"other", "date":"2026-10-26"])
        let other = try #require(db.load().calendarEvents.first(where: { $0.id != id })?.id.uuidString)
        try await call("update_calendar_event", ["event_id":other, "new_title":"changed"])
        try await call("delete_calendar_event", ["event_id":other])
        let batch: [String: Any] = ["batch_id":"test-batch", "operations":[["tool":"add_task", "arguments":["title":"batch task"]]]]
        try await call("batch_edit_items", batch)
        try await call("batch_edit_items", batch)
        try await call("import_calendar_events", ["batch_id":"test-import", "events":[["title":"import", "date":"2026-10-26", "start_time":"14:00", "end_time":"15:00"]]])
        app.save()
        let reopened = AppState(database: LocalDatabase(customURL: db.fileURL), notificationService: MockNotificationService(), widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget2.json")))
        #expect(reopened.calendarEvents.first(where: { $0.id == id })?.alertMinutesBefore == 10)
        let restarted = MCPServer(handlers: MCPToolHandlers(database: LocalDatabase(customURL: db.fileURL), enablesAppIntegration: false))
        #expect(try restarted.invokeTool(name:"get_editing_context", arguments:["kind":"events"]).contains("alertMinutesBefore"))
    }

    @Test(arguments: ["none", "daily", "weekly", "biweekly", "monthly", "workdays", "weekends", "customDays", "customWeeks"])
    func dateQueryAgreesWithSharedProjector(frequency: String) throws {
        let (server, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        var args: [String: Any] = ["title":"recurring", "date":"2026-10-19", "frequency":frequency, "alert_minutes_before":10]
        if frequency == "customDays" { args["custom_days_of_week"] = [1,3,5] }
        if frequency == "customWeeks" { args["custom_weeks"] = [1,2] }
        _ = try server.invokeTool(name:"add_calendar_event", arguments:args)
        let payload = db.load(), semester = try #require(db.load().semesters.first)
        let handler = MCPToolHandlers(database:db, enablesAppIntegration:false)
        for week in 1...3 {
            let projected = CalendarEventProjector().project(events:payload.calendarEvents, semester:semester, week:week, exceptions:payload.exceptions)
            for date in WeekCalculator().dateStringsInWeek(weekNumber:week, semesterStartDate:semester.startDate) {
                #expect(handler.listCalendarEvents(date:date).count == projected.filter { $0.date == date && $0.status != .cancelled }.count)
            }
        }
    }
}

private actor InterleavedStorage: StorageProvider {
    let base = MockStorageProvider()
    var first = true
    let duringFirstRead: @Sendable () async throws -> Void
    init(_ action: @escaping @Sendable () async throws -> Void) { duringFirstRead = action }
    nonisolated func validateConfiguration() -> ConfigurationValidationResult { .valid }
    func testConnection() async throws -> ConnectionHealth { try await base.testConnection() }
    func readObject(path: String) async throws -> Data {
        if first { first = false; try await duringFirstRead() }
        return try await base.readObject(path: path)
    }
    func writeObject(path: String, data: Data, metadata: [String:String]?) async throws { try await base.writeObject(path:path,data:data,metadata:metadata) }
    func deleteObject(path: String) async throws { try await base.deleteObject(path:path) }
    func listObjects(prefix: String) async throws -> [StorageObjectSummary] { try await base.listObjects(prefix:prefix) }
    func getObjectMetadata(path: String) async throws -> StorageObjectMetadata { try await base.getObjectMetadata(path:path) }
}
extension MCPReportedRegressionTests {
    @Test @MainActor func syncMustNotOverwriteReminderAcknowledgedDuringNetworkWait() async throws {
        let (server, db, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name:"add_calendar_event", arguments:["title":"anchor", "date":"2026-10-25"])
        let id = try #require(db.load().calendarEvents.first?.id.uuidString)
        let app = AppState(database: db, notificationService: MockNotificationService(), widgetDataStore: WidgetDataStore(customURL:root.appendingPathComponent("widget.json")))
        let storage = InterleavedStorage {
            _ = try await app.executeAutomationTool(name:"update_calendar_event", arguments:["event_id":id,"alert_minutes_before":10])
        }
        _ = try await app.executeCloudSync(storage:storage)
        #expect(db.load().calendarEvents.first?.alertMinutesBefore == 10)
        _ = try await app.executeCloudSync(storage:storage)
        #expect(db.load().calendarEvents.first?.alertMinutesBefore == 10)
    }
}

extension MCPReportedRegressionTests {
    @Test func rebaseRetainsIndependentPeerFieldsAndExplicitReminderClear() throws {
        var base = CalendarEvent(title:"base", startTime:"2026-10-25T10:00:00", endTime:"2026-10-25T11:00:00", alertMinutesBefore:10)
        var local = base, remote = base
        local.alertMinutesBefore = nil
        remote.title = "peer title"
        let merged = try LocalSyncRebase.merge(baseline:[base],local:[local],remote:[remote])
        #expect(merged.first?.alertMinutesBefore == nil)
        #expect(merged.first?.title == "peer title")
        #expect(try LocalSyncRebase.merge(baseline:[base],local:[],remote:[remote]).isEmpty)
        base.id = UUID()
        #expect(try LocalSyncRebase.merge(baseline:[base],local:[base,local],remote:[base]).count == 2)
    }
}

extension MCPReportedRegressionTests {
    @Test(arguments: ["all", "this_only", "this_and_future"])
    @MainActor func scopedEditsRetainAllReminderValues(scope: String) throws {
        for value: Int? in [nil, 0, 5, 10, 30, 7] {
            let (server, db, root) = try MCPCompleteEditingTests().fixture()
            defer { try? FileManager.default.removeItem(at: root) }
            _ = try server.invokeTool(name:"add_calendar_event", arguments:["title":"series", "date":"2026-10-19", "frequency":"daily", "alert_minutes_before":value.map { $0 as Any } ?? NSNull()])
            let id = try #require(db.load().calendarEvents.first?.id.uuidString)
            _ = try server.invokeTool(name:"update_calendar_event", arguments:["event_id":id,"scope":scope,"occurrence_date":"2026-10-21","notes":"edit notes"])
            for event in db.load().calendarEvents {
                #expect(event.alertMinutesBefore == value)
                var edited = event
                edited.alertMinutesBefore = ScheduleItemCreateSheet.AlertOption(minutesBefore:event.alertMinutesBefore).resolvedMinutes(preserving:event.alertMinutesBefore)
                let reloaded = try JSONDecoder().decode(CalendarEvent.self,from:JSONEncoder().encode(edited))
                #expect(reloaded.alertMinutesBefore == value)
            }
        }
    }
    @Test @MainActor func editorFrequencyRoundTrips() {
        for code in ["none","daily","weekly","biweekly","monthly","workdays","weekends","customDays","customWeeks"] {
            #expect(ScheduleItemCreateSheet.EventFrequency(internalCode:code).internalCode == code)
        }
    }
}
