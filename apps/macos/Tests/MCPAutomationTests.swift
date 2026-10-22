import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

@Suite("MCPAutomationTests")
struct MCPAutomationTests {
    func isolated() -> (MCPToolHandlers, LocalDatabase, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let db = LocalDatabase(customURL: root.appendingPathComponent("database.json"))
        let widgets = WidgetDataStore(customURL: root.appendingPathComponent("widget.json"))
        return (MCPToolHandlers(database: db, widgetStore: widgets, enablesAppIntegration: false), db, root)
    }

    @Test("Large timetable queries preserve event identity, exceptions and source data")
    func largeTimetableQuery() throws {
        let (handler, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        var payload = db.load()
        let semester = try #require(payload.semesters.first)
        let date = WeekCalculator().dateStringsInWeek(weekNumber: 1, semesterStartDate: semester.startDate)[0]
        payload.calendarEvents = (0..<500).map { index in
            CalendarEvent(semesterId: semester.id, title: "Event \(index)",
                startTime: "\(date)T09:00:00", endTime: "\(date)T10:00:00", frequency: "none")
        }
        payload.exceptions = [ScheduleException(scheduleId: payload.calendarEvents[0].id,
            originalDate: date, exceptionType: .cancel, reason: "单次删除")]
        try db.save(payload)
        let before = try Data(contentsOf: db.fileURL)
        let start = ContinuousClock.now
        let result = handler.queryTimetable(week: 1, dayOfWeek: 1)
        print("PERF timetable query, 500 events: \(start.duration(to: .now))")
        let items = try #require(result["items"] as? [[String: Any]])
        #expect(items.count == 499)
        #expect(Set(items.compactMap { $0["id"] as? String }) == Set(payload.calendarEvents.dropFirst().map { "event_occ_\($0.id)_\(date)" }))
        #expect(try Data(contentsOf: db.fileURL) == before)
    }

    @Test("Event updates preserve UUID, notes and color and reject invalid dates")
    func eventUpdates() throws {
        let (handler, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try handler.addCalendarEvent(arguments: ["title": "Meeting", "date": "2026-10-21"])
        let event = try #require(db.load().calendarEvents.first)
        _ = try handler.updateCalendarEvent(arguments: ["event_id": event.id.uuidString, "new_title": "Exam", "notes": "Bring ID", "color": "#123456", "date": "2026-10-22", "start_time": "14:00", "end_time": "16:00"])
        let updated = try #require(db.load().calendarEvents.first)
        #expect(updated.id == event.id)
        #expect(updated.title == "Exam")
        #expect(updated.description == "Bring ID")
        #expect(updated.color == "#123456")
        #expect(updated.startTime == "2026-10-22T14:00:00")
        #expect(throws: Error.self) { try handler.updateCalendarEvent(arguments: ["event_id": event.id.uuidString, "date": "2026-02-30"]) }
        #expect(throws: Error.self) { try handler.addCalendarEvent(arguments: ["title": "Invalid", "date": "2026-10-21", "start_time": "18:00", "end_time": "17:00"]) }
        #expect(db.load().calendarEvents.count == 1)
    }

    @Test("Task notes, edits and completion timestamps persist without changing timetable profile")
    func taskUpdates() throws {
        let (handler, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        let before = db.load().activeProfileId
        _ = try handler.addTask(arguments: ["title": "Essay", "notes": "Draft first"])
        let task = try #require(db.load().pomodoroTasks.first { $0.title == "Essay" })
        #expect(task.notes == "Draft first")
        _ = try handler.updateTask(arguments: ["task_id": task.id.uuidString, "notes": "Final draft", "priority": "high", "estimated_pomodoros": 3, "due_date": "2026-10-25"])
        _ = try handler.completeTask(arguments: ["task_id": task.id.uuidString])
        var updated = try #require(db.load().pomodoroTasks.first { $0.id == task.id })
        #expect(updated.completedAt != nil)
        #expect(updated.notes == "Final draft")
        #expect(updated.priority == .high)
        _ = try handler.completeTask(arguments: ["task_id": task.id.uuidString, "is_completed": false])
        updated = try #require(db.load().pomodoroTasks.first { $0.id == task.id })
        #expect(updated.completedAt == nil)
        #expect(db.load().activeProfileId == before)
        #expect(throws: Error.self) { try handler.updateTask(arguments: ["task_id": task.id.uuidString, "estimated_pomodoros": -1]) }
    }

    @Test("Ambiguous task and course names cannot delete arbitrary records")
    func ambiguousMatches() throws {
        let (handler, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try handler.addTask(arguments: ["title": "Duplicate"])
        _ = try handler.addTask(arguments: ["title": "Duplicate"])
        let count = db.load().pomodoroTasks.count
        #expect(throws: Error.self) { try handler.deleteTask(arguments: ["title": "Duplicate"]) }
        #expect(db.load().pomodoroTasks.count == count)
        var payload = db.load()
        let semester = try #require(payload.semesters.first)
        payload.courses = [Course(semesterId: semester.id, name: "Math"), Course(semesterId: semester.id, name: "Math")]
        try db.save(payload)
        #expect(throws: Error.self) { try handler.deleteCourse(arguments: ["name": "Math"]) }
        #expect(db.load().courses.count == 2)
    }

    @Test("Focus counts actual time, excludes pauses, and cannot be stopped twice")
    func focusLifecycle() throws {
        let (handler, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        let before = db.load()
        _ = try handler.startFocusTimer(arguments: ["task_title": "Study", "duration_minutes": 25])
        #expect(db.load().activeProfileId == before.activeProfileId)
        #expect(throws: Error.self) { try handler.startFocusTimer(arguments: [:]) }
        var payload = db.load()
        payload.focusSession?.startedAt = Date().addingTimeInterval(-120)
        try db.save(payload)
        _ = try handler.pauseFocusTimer(resume: false)
        let paused = try #require(db.load().focusSession)
        #expect(paused.elapsedSeconds(at: Date().addingTimeInterval(600)) == 120)
        _ = try handler.pauseFocusTimer(resume: true)
        _ = try handler.stopFocusTimer(arguments: ["actual_minutes": 999])
        let after = db.load()
        #expect(after.focusSession == nil)
        #expect(after.pomodoroRecords.count == before.pomodoroRecords.count + 1)
        #expect(after.pomodoroRecords.last?.durationMinutes == 2)
        #expect(after.pomodoroTasks.first { $0.title == "Study" }?.totalFocusMinutes == 2)
        #expect(throws: Error.self) { try handler.stopFocusTimer(arguments: [:]) }
        #expect(db.load().pomodoroRecords.count == after.pomodoroRecords.count)
    }

    @Test("Short focus sessions do not invent completed minutes or pomodoros")
    func shortFocus() throws {
        let (handler, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        let count = db.load().pomodoroRecords.count
        _ = try handler.startFocusTimer(arguments: [:])
        _ = try handler.stopFocusTimer(arguments: [:])
        #expect(db.load().pomodoroRecords.count == count)
        #expect(throws: Error.self) { try handler.startFocusTimer(arguments: ["duration_minutes": 0]) }
    }

    @Test("Legacy payloads remain readable and focus session round trips")
    func serialization() throws {
        let (_, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        var payload = db.load()
        #expect(payload.focusSession == nil)
        payload.focusSession = FocusSession(taskId: nil, title: "Test", durationMinutes: 25, startedAt: Date(timeIntervalSince1970: 1000))
        try db.save(payload)
        #expect(db.load().focusSession == payload.focusSession)
        let encoded = try JSONEncoder().encode(payload)
        var legacy = try #require(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        legacy.removeValue(forKey: "focusSession")
        let decoded = try JSONDecoder().decode(LocalDatabase.DatabasePayload.self, from: JSONSerialization.data(withJSONObject: legacy))
        #expect(decoded.focusSession == nil)
    }

    @Test("Invalid schedule ranges fail without crashing the MCP process")
    func scheduleValidation() throws {
        let (handler, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        #expect(throws: Error.self) { try handler.addCourse(arguments: ["name": "Invalid", "day_of_week": 3, "start_section": 1, "start_week": 12, "end_week": 1]) }
        _ = try handler.addCourse(arguments: ["name": "TestCourse", "day_of_week": 3, "start_section": 1, "end_section": 2])
        let course = try #require(db.load().courses.first { $0.name == "TestCourse" })
        _ = try handler.updateCourse(arguments: ["course_id": course.id.uuidString, "day_of_week": 5, "start_section": 3, "end_section": 4])
        #expect(db.load().courses.first { $0.id == course.id }?.schedules.first?.dayOfWeek == 5)
        #expect(throws: Error.self) { try handler.updateCourse(arguments: ["course_id": course.id.uuidString, "day_of_week": 8]) }
        #expect(throws: Error.self) { try handler.deleteCourse(arguments: ["course_id": course.id.uuidString, "scope": "this_and_future", "week": 0]) }
        #expect(db.load().courses.contains { $0.id == course.id })
    }

    @Test("App executor refreshes UI state and records sync operations for MCP changes")
    @MainActor
    func appExecution() async throws {
        let (_, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        let app = AppState(database: db, notificationService: MockNotificationService(), widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")))
        _ = try await app.executeAutomationTool(name: "add_task", arguments: ["title": "Bridge task", "notes": "Visible in UI"])
        #expect(app.pomodoroTasks.contains { $0.title == "Bridge task" && $0.notes == "Visible in UI" })
        #expect(app.syncEngine.pendingCount > 0)
        #expect(db.load().syncSequence == app.syncEngine.latestSequence)
        #expect(db.load().syncDeviceId == app.syncEngine.deviceId)
        _ = try await app.executeAutomationTool(name: "switch_semester_or_week", arguments: ["week": 2])
        #expect(app.selectedWeek == 2)
        _ = try await app.executeAutomationTool(name: "get_status", arguments: [:])
        #expect(app.selectedWeek == 2)
    }

    @Test("Automation refuses a corrupt database rather than overwriting it with samples")
    func corruptStorage() throws {
        let (handler, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let bytes = Data("corrupt".utf8)
        try bytes.write(to: db.fileURL)
        let server = MCPServer(handlers: handler)
        #expect(throws: Error.self) { try server.invokeTool(name: "add_task", arguments: ["title": "Do not write"]) }
        #expect(try Data(contentsOf: db.fileURL) == bytes)
    }
    @Test("Desktop mailbox acknowledges once and rejects expired requests")
    @MainActor
    func mailboxAcknowledgement() async throws {
        let (_, db, root) = isolated()
        defer { try? FileManager.default.removeItem(at: root) }
        let mailbox = root.appendingPathComponent("mailbox")
        try FileManager.default.createDirectory(at: mailbox, withIntermediateDirectories: true)
        let app = AppState(database: db, notificationService: MockNotificationService(), widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")))
        let receiver = MCPDesktopCommandReceiver(app: app, directory: mailbox)
        defer { receiver.stop() }
        let id = UUID()
        let request = MCPDesktopBridge.Request(id: id, tool: "add_task", arguments: try JSONSerialization.data(withJSONObject: ["title": "Mailbox task"]), expiresAt: Date().addingTimeInterval(10))
        let file = mailbox.appendingPathComponent("\(id).request.json")
        let response = mailbox.appendingPathComponent("\(id).response.json")
        try JSONEncoder().encode(request).write(to: file)
        receiver.drain()
        for _ in 0..<100 {
            if FileManager.default.fileExists(atPath: response.path) { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        let result = try JSONDecoder().decode(MCPDesktopBridge.Response.self, from: Data(contentsOf: response))
        #expect(result.error == nil)
        #expect(result.result?.contains("Mailbox task") == true)
        receiver.drain()
        #expect(app.pomodoroTasks.filter { $0.title == "Mailbox task" }.count == 1)
        let expiredId = UUID()
        let expired = MCPDesktopBridge.Request(id: expiredId, tool: "add_task", arguments: try JSONSerialization.data(withJSONObject: ["title": "Expired task"]), expiresAt: Date().addingTimeInterval(-1))
        try JSONEncoder().encode(expired).write(to: mailbox.appendingPathComponent("\(expiredId).request.json"))
        receiver.drain()
        #expect(!app.pomodoroTasks.contains { $0.title == "Expired task" })
    }

    @Test("GUI preferences use the application domain without force-unwrapping a nil suite")
    func nativePreferencesDomain() {
        var attemptedSuite = false
        let preferences = CloudSettings.preferences(bundleIdentifier: CloudSettings.applicationDomain) { _ in
            attemptedSuite = true
            return nil
        }
        #expect(preferences === UserDefaults.standard)
        #expect(!attemptedSuite)
    }

    @Test("Unavailable companion preference suite fails safely without crashing")
    func unavailablePreferencesDomain() {
        let preferences = CloudSettings.preferences(bundleIdentifier: "classflow-mcp") { _ in nil }
        #expect(preferences === UserDefaults.standard)
    }

}
