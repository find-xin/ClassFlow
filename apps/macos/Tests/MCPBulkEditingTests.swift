import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

@Suite("Bulk automation performance and atomicity")
struct MCPBulkEditingTests {
    func fixture(events count: Int = 0) throws -> (MCPServer, LocalDatabase, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let db = LocalDatabase(customURL: root.appendingPathComponent("database.json"))
        var payload = db.makeBootstrapData()
        let calendar = UUID()
        payload.calendarEvents = (0..<count).map { i in
            CalendarEvent(calendarId: calendar, title: "Event \(i)", startTime: "2026-10-22T09:00:00", endTime: "2026-10-22T10:00:00", frequency: "none", startDate: "2026-10-22")
        }
        payload.pomodoroTasks = [PomodoroTask(title: "Keep task")]
        try db.save(payload)
        return (MCPServer(handlers: MCPToolHandlers(database: db, enablesAppIntegration: false)), db, root)
    }
    func json(_ text: String) throws -> [String: Any] { try #require(JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any]) }

    @Test("One request deletes 5000 events atomically, keeps other domains and safely replays")
    func deleteAll() throws {
        let (server, db, root) = try fixture(events: 5000); defer { try? FileManager.default.removeItem(at: root) }
        var payload = db.load()
        let event = try #require(payload.calendarEvents.first)
        payload.exceptions = [ScheduleException(scheduleId: event.id, originalDate: "2026-10-22", exceptionType: .cancel)]
        try db.save(payload)
        let original = try Data(contentsOf: db.fileURL)
        let args: [String: Any] = ["batch_id": "delete-all", "selection": ["kind": "events", "all": true], "action": "delete"]
        var preview = args; preview["dry_run"] = true
        #expect(try json(server.invokeTool(name: "batch_edit_items", arguments: preview))["matched"] as? Int == 5000)
        #expect(try Data(contentsOf: db.fileURL) == original)
        let start = Date()
        let result = try server.invokeTool(name: "batch_edit_items", arguments: args)
        print("BENCHMARK bulk_delete_5000_seconds=\(Date().timeIntervalSince(start))")
        #expect(result.utf8.count < 2000)
        #expect(db.load().calendarEvents.isEmpty && db.load().exceptions.isEmpty)
        #expect(db.load().pomodoroTasks.count == 1)
        #expect(db.load().tombstones.filter { $0.entityType == "calendarEvent" }.count == 5000)
        #expect(db.load().tombstones.filter { $0.entityType == "exception" }.count == 1)
        _ = try server.invokeTool(name: "add_calendar_event", arguments: ["title": "Added after delete", "date": "2026-10-23"])
        #expect(try server.invokeTool(name: "batch_edit_items", arguments: args) == result)
        #expect(db.load().calendarEvents.count == 1)
        var different = args; different["selection"] = ["kind": "tasks", "all": true]
        #expect(throws: Error.self) { try server.invokeTool(name: "batch_edit_items", arguments: different) }
    }

    @Test("1000 mixed operations use one transaction; a late error rolls everything back")
    func thousandOperations() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        let rows: [[String: Any]] = (0..<1000).map { i in
            i % 2 == 0 ? ["tool": "add_task", "arguments": ["title": "Task \(i)"]] : ["tool": "add_calendar_event", "arguments": ["title": "Event \(i)", "date": "2026-10-22"]]
        }
        let original = try Data(contentsOf: db.fileURL)
        var invalid = rows; invalid[999] = ["tool": "add_task", "arguments": ["title": "Invalid", "target_minutes": -1]]
        #expect(throws: Error.self) { try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "rollback", "operations": invalid]) }
        #expect(try Data(contentsOf: db.fileURL) == original)
        let start = Date()
        let result = try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "thousand", "operations": rows])
        print("BENCHMARK mixed_edit_1000_seconds=\(Date().timeIntervalSince(start))")
        #expect(try json(result)["count"] as? Int == 1000)
        #expect(try json(result)["results"] == nil)
        #expect(db.load().calendarEvents.count == 500 && db.load().pomodoroTasks.count == 501)
        #expect(throws: Error.self) { try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "too-large", "operations": rows + [rows[0]]]) }
    }

    @Test("Filtered editing, context paging and task completion keep selection boundaries")
    func filteredUpdate() throws {
        let (server, db, root) = try fixture(events: 8); defer { try? FileManager.default.removeItem(at: root) }
        let selection: [String: Any] = ["kind": "events", "title_contains": "Event 1", "date_from": "2026-10-22", "date_to": "2026-10-22"]
        _ = try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "color", "selection": selection, "action": "update", "changes": ["color": "#123456", "notes": "Batch note", "alert_minutes_before": 10]])
        let changed = db.load().calendarEvents.filter { $0.color == "#123456" }
        #expect(changed.count == 1 && changed[0].description == "Batch note" && changed[0].alertMinutesBefore == 10)
        let context = try json(server.invokeTool(name: "get_editing_context", arguments: ["kind": "events", "selection": selection, "limit": 1000]))
        #expect((context["events"] as? [Any])?.count == 1)
        #expect((context["matching_totals"] as? [String: Int])?["events"] == 1)
        _ = try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "complete", "selection": ["kind": "tasks", "is_completed": false], "action": "complete"])
        #expect(db.load().pomodoroTasks.allSatisfy { $0.isCompleted })
        _ = try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "uncomplete", "selection": ["kind": "tasks", "all": true], "action": "complete", "changes": ["is_completed": false]])
        #expect(db.load().pomodoroTasks.allSatisfy { !$0.isCompleted })
        let full = try json(server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "delete-full", "selection": selection, "action": "delete", "result_mode": "full"]))
        #expect((full["results"] as? [String])?.count == 1)
        #expect(throws: Error.self) { try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "conflict", "selection": selection, "operations": [], "action": "delete"]) }
    }

    @Test("Ambiguous, invalid or excessive selectors fail without changing storage")
    func rejectedSelections() throws {
        let (server, db, root) = try fixture(events: 5001); defer { try? FileManager.default.removeItem(at: root) }
        let original = try Data(contentsOf: db.fileURL)
        let invalid: [[String: Any]] = [
            ["kind": "events"], ["kind": "events", "all": true],
            ["kind": "events", "all": true, "title_contains": "Event"],
            ["kind": "events", "title_contains": ""], ["kind": "events", "ids": [UUID().uuidString]],
            ["kind": "events", "date_from": "invalid"], ["kind": "events", "is_completed": true],
            ["kind": "courses", "date_to": "2026-10-22"], ["kind": "tasks", "unknown": true]
        ]
        for selection in invalid {
            #expect(throws: Error.self) { try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "invalid", "selection": selection, "action": "delete"]) }
        }
        #expect(try Data(contentsOf: db.fileURL) == original)
    }

    @Test("Import accepts 2000 rows and rejects overflow atomically")
    func largerImport() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        let rows = (0..<2000).map { ["title": "Import \($0)", "date": "2026-10-22", "start_time": "09:00", "end_time": "10:00"] }
        _ = try server.invokeTool(name: "import_calendar_events", arguments: ["batch_id": "two-thousand", "events": rows])
        #expect(db.load().calendarEvents.count == 2000)
        #expect(throws: Error.self) { try server.invokeTool(name: "import_calendar_events", arguments: ["batch_id": "overflow", "events": rows + [rows[0]]]) }
        #expect(db.load().calendarEvents.count == 2000)
    }
}

extension MCPBulkEditingTests {
    @MainActor @Test("App bulk commits refresh UI and journal every change; dry runs do neither")
    func appTransaction() async throws {
        let (_, db, root) = try fixture(events: 258); defer { try? FileManager.default.removeItem(at: root) }
        let app = AppState(database: db, notificationService: MockNotificationService(), widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")))
        let before = app.syncEngine.latestSequence
        let original = try Data(contentsOf: db.fileURL)
        let args: [String: Any] = ["batch_id": "app-delete", "selection": ["kind": "events", "all": true], "action": "delete"]
        var preview = args; preview["dry_run"] = true
        _ = try await app.executeAutomationTool(name: "batch_edit_items", arguments: preview)
        #expect(try Data(contentsOf: db.fileURL) == original)
        #expect(app.calendarEvents.count == 258 && app.syncEngine.latestSequence == before)
        let start = Date()
        _ = try await app.executeAutomationTool(name: "batch_edit_items", arguments: args)
        print("BENCHMARK app_delete_258_seconds=\(Date().timeIntervalSince(start))")
        #expect(app.calendarEvents.isEmpty && db.load().calendarEvents.isEmpty)
        #expect(app.syncEngine.latestSequence == before + 258)
        #expect(app.pomodoroTasks.count == 1)
        _ = try await app.executeAutomationTool(name: "batch_edit_items", arguments: args)
        #expect(app.syncEngine.latestSequence == before + 258)
        let committed = try Data(contentsOf: db.fileURL)
        let invalid: [[String: Any]] = [["tool": "add_task", "arguments": ["title": "Rollback"]], ["tool": "update_task", "arguments": ["task_id": UUID().uuidString, "new_title": "Missing"]]]
        await #expect(throws: Error.self) { try await app.executeAutomationTool(name: "batch_edit_items", arguments: ["batch_id": "app-invalid", "operations": invalid]) }
        #expect(try Data(contentsOf: db.fileURL) == committed)
    }

    @Test("Bulk course changes and deletes preserve other domains and schedule tombstones")
    func bulkCourses() throws {
        let (server, db, root) = try fixture(events: 1); defer { try? FileManager.default.removeItem(at: root) }
        for name in ["Math", "Physics"] { _ = try server.invokeTool(name: "add_course", arguments: ["name": name]) }
        var payload = db.load()
        let course = try #require(payload.courses.first), slot = try #require(course.schedules.first)
        payload.exceptions.append(ScheduleException(scheduleId: slot.id, originalDate: "2026-10-22", exceptionType: .cancel))
        try db.save(payload)
        _ = try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "courses-color", "selection": ["kind": "courses", "all": true], "action": "update", "changes": ["color": "#123456", "credit": 2.5]])
        #expect(db.load().courses.allSatisfy { $0.color == "#123456" && $0.credit == 2.5 })
        _ = try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "associate", "selection": ["kind": "tasks", "all": true], "action": "update", "changes": ["course_id": course.id.uuidString]])
        #expect(db.load().pomodoroTasks.first?.courseId == course.id)
        _ = try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "courses-delete", "selection": ["kind": "courses", "all": true], "action": "delete"])
        #expect(db.load().courses.isEmpty && db.load().exceptions.isEmpty)
        #expect(db.load().calendarEvents.count == 1 && db.load().pomodoroTasks.count == 1)
        #expect(db.load().tombstones.contains { $0.entityId == slot.id.uuidString && $0.entityType == "schedule" })
    }
}
