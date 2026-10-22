import Foundation
import Testing
@testable import ClassFlow

@Suite("MCPBatchImportTests")
struct MCPBatchImportTests {
    private func fixture() -> (MCPToolHandlers, LocalDatabase, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let db = LocalDatabase(customURL: root.appendingPathComponent("database.json"))
        return (MCPToolHandlers(database: db, widgetStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")), enablesAppIntegration: false), db, root)
    }
    private func row(_ i: Int) -> [String: Any] {
        ["title": "Event \(i)", "date": "2026-10-21", "start_time": "09:00", "end_time": "10:00", "notes": "Original notes", "color": "#123456"]
    }
    private func result(_ text: String) throws -> [String: Any] {
        try #require(JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any])
    }

    @Test("258 rows adopt 35 previous imports and insert exactly 223, preserving existing UUIDs")
    func migration258() throws {
        let (handler, db, root) = fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        for i in 0..<35 { _ = try handler.addCalendarEvent(arguments: row(i)) }
        let original = db.load().calendarEvents
        let response = try result(handler.importCalendarEvents(arguments: ["batch_id": "migration", "events": (0..<258).map(row)]))
        #expect(response["requested"] as? Int == 258)
        #expect(response["inserted"] as? Int == 223)
        #expect(response["skipped_duplicates"] as? Int == 35)
        #expect(db.load().calendarEvents.count == 258)
        #expect(Array(db.load().calendarEvents.prefix(35)) == original)
        #expect(db.load().calendarEvents.last?.description == "Original notes")
        #expect(db.load().calendarEvents.last?.color == "#123456")
    }

    @Test("A malformed last row rolls back the entire batch")
    func atomicValidation() throws {
        let (handler, db, root) = fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try handler.addCalendarEvent(arguments: row(0))
        let before = try Data(contentsOf: db.fileURL)
        var rows = (1..<258).map(row)
        rows.append(["title": "Bad", "date": "2026-02-30", "start_time": "09:00", "end_time": "10:00"])
        #expect(throws: Error.self) { try handler.importCalendarEvents(arguments: ["batch_id": "bad", "events": rows]) }
        #expect(try Data(contentsOf: db.fileURL) == before)
    }

    @Test("Dry run never creates a database or receipt")
    func dryRun() throws {
        let (handler, db, root) = fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let response = try result(handler.importCalendarEvents(arguments: ["batch_id": "preview", "events": [row(0)], "dry_run": true]))
        #expect(response["inserted"] as? Int == 1)
        #expect(!FileManager.default.fileExists(atPath: db.fileURL.path))
    }

    @Test("A replay survives restart and manual deletion without resurrecting an imported event")
    func durableReplay() throws {
        let (handler, db, root) = fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let args: [String: Any] = ["batch_id": "once", "events": [row(0), row(1)]]
        _ = try handler.importCalendarEvents(arguments: args)
        var payload = db.load()
        payload.calendarEvents.removeAll()
        try db.save(payload)
        let restarted = MCPToolHandlers(database: db, enablesAppIntegration: false)
        let response = try result(restarted.importCalendarEvents(arguments: args))
        #expect(response["replayed"] as? Bool == true)
        #expect(db.load().calendarEvents.isEmpty)
        #expect(throws: Error.self) { try handler.importCalendarEvents(arguments: ["batch_id": "once", "events": [row(3)]]) }
    }

    @Test("Duplicates inside one batch and across distinct batches are skipped")
    func contentDuplicates() throws {
        let (handler, db, root) = fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try handler.importCalendarEvents(arguments: ["batch_id": "a", "events": [row(0), row(0)]])
        var changed = row(0)
        changed["notes"] = "Different notes must not overwrite the existing import"
        let response = try result(handler.importCalendarEvents(arguments: ["batch_id": "b", "events": [changed]]))
        #expect(response["inserted"] as? Int == 0)
        #expect(db.load().calendarEvents.count == 1)
        #expect(db.load().calendarEvents[0].description == "Original notes")
    }

    @Test("Limits, time order, invalid colors and unsupported recurrences fail closed")
    func guards() throws {
        let (handler, db, root) = fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        for rows in [[], (0..<2001).map(row)] {
            #expect(throws: Error.self) { try handler.importCalendarEvents(arguments: ["batch_id": "invalid", "events": rows]) }
        }
        #expect(throws: Error.self) { try handler.importCalendarEvents(arguments: ["batch_id": "bad-preview", "events": [row(0)], "dry_run": "true"]) }
        for (key, value) in [("end_time", "08:00"), ("color", "red"), ("frequency", "weekly")] {
            var invalid = row(0)
            invalid[key] = value
            #expect(throws: Error.self) { try handler.importCalendarEvents(arguments: ["batch_id": "invalid", "events": [invalid]]) }
        }
        #expect(!FileManager.default.fileExists(atPath: db.fileURL.path))
    }

    @Test("Cross-midnight and multi-day all-day events preserve end dates")
    func crossDay() throws {
        let (handler, db, root) = fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let rows: [[String: Any]] = [
            ["title": "Overnight", "date": "2026-10-21", "end_date": "2026-10-22", "start_time": "23:00", "end_time": "01:00"],
            ["title": "Trip", "date": "2026-10-21", "end_date": "2026-10-23", "start_time": "00:00", "end_time": "23:59", "is_all_day": true]
        ]
        _ = try handler.importCalendarEvents(arguments: ["batch_id": "cross-day", "events": rows])
        #expect(db.load().calendarEvents[0].endTime == "2026-10-22T01:00:00")
        #expect(db.load().calendarEvents[1].endDate == "2026-10-23")
    }

    @MainActor @Test("App executor journals every inserted event and ordinary saves retain replay receipts")
    func appExecutor() async throws {
        let (_, db, root) = fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let app = AppState(database: db, widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")))
        let before = app.syncEngine.latestSequence
        let args: [String: Any] = ["batch_id": "app", "events": (0..<10).map(row)]
        _ = try await app.executeAutomationTool(name: "import_calendar_events", arguments: args)
        #expect(app.calendarEvents.count == 10)
        #expect(app.syncEngine.latestSequence == before + 10)
        app.save()
        #expect(db.load().calendarImportReceipts?["app"] != nil)
        _ = try await app.executeAutomationTool(name: "import_calendar_events", arguments: args)
        #expect(app.calendarEvents.count == 10)
        #expect(app.syncEngine.latestSequence == before + 10)
    }
}
