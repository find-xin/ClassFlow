import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

@Suite("Complete MCP editing")
struct MCPCompleteEditingTests {
    func fixture() throws -> (MCPServer, LocalDatabase, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let db = LocalDatabase(customURL: root.appendingPathComponent("db.json"))
        var payload = db.load(); payload.courses = []; payload.calendarEvents = []; payload.pomodoroTasks = []; payload.exceptions = []
        payload.semesters = [Semester(name: "Test", startDate: "2026-10-19", endDate: "2027-02-07", totalWeeks: 16, isCurrent: true)]
        try db.save(payload)
        return (MCPServer(handlers: MCPToolHandlers(database: db, enablesAppIntegration: false)), db, root)
    }
    @Test("Every course field and independent time slot round trips")
    func courses() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_course", arguments: ["name": "Math", "credit": 1.5, "notes": "Exam", "color": "#123456", "schedules": [
            ["day_of_week": 2, "start_section": 1, "end_section": 2, "classroom": "A", "teacher": "T1", "custom_weeks": [1, 3, 5]],
            ["day_of_week": 4, "time_mode": "time", "custom_start_time": "19:30", "custom_end_time": "20:45", "teacher": "T2"]]])
        var course = try #require(db.load().courses.first)
        #expect(course.credit == 1.5 && course.schedules.count == 2)
        let first = course.schedules[0], second = course.schedules[1]
        _ = try server.invokeTool(name: "update_course", arguments: ["course_id": course.id.uuidString, "schedule_id": first.id.uuidString, "classroom": "B", "notes": NSNull()])
        course = try #require(db.load().courses.first)
        #expect(course.notes == nil && course.schedules[0].classroom == "B" && course.schedules[1] == second)
        _ = try server.invokeTool(name: "manage_course_schedule", arguments: ["course_id": course.id.uuidString, "schedule_id": first.id.uuidString, "action": "reschedule", "occurrence_date": "2026-10-20", "target_date": "2026-10-23", "start_section": 3, "end_section": 4, "classroom": "C"])
        let semester = try #require(db.load().semesters.first)
        let items = TimelineEngine().generateOccurrences(semester: semester, courses: db.load().courses, exceptions: db.load().exceptions, forWeek: 1, activeWeek: 1)
        #expect(items.contains { $0.date == "2026-10-23" && $0.location == "C" && $0.status == .rescheduled })
        _ = try server.invokeTool(name: "manage_course_schedule", arguments: ["course_id": course.id.uuidString, "schedule_id": first.id.uuidString, "action": "restore", "occurrence_date": "2026-10-20"])
        #expect(db.load().exceptions.isEmpty)
        _ = try server.invokeTool(name: "manage_course_schedule", arguments: ["course_id": course.id.uuidString, "schedule_id": second.id.uuidString, "action": "reschedule", "scope": "this_and_future", "occurrence_date": "2026-10-29", "day_of_week": 5])
        course = try #require(db.load().courses.first)
        #expect(course.schedules.count == 3)
        #expect(course.schedules.last?.customStartTime == "19:30")
        #expect(course.schedules.first { $0.id == second.id }?.weekPattern.customWeeks == [1])
        _ = try server.invokeTool(name: "manage_course_schedule", arguments: ["course_id": course.id.uuidString, "schedule_id": first.id.uuidString, "action": "cancel", "occurrence_date": "2026-10-20"])
        _ = try server.invokeTool(name: "delete_course", arguments: ["course_id": course.id.uuidString])
        #expect(db.load().courses.isEmpty && db.load().exceptions.isEmpty)
        #expect(db.load().tombstones.contains { $0.entityId == first.id.uuidString })
    }
    @Test("Event recurrence, reminder, section binding and scoped editing")
    func events() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_calendar_event", arguments: ["title": "Meeting", "date": "2026-10-19", "time_mode": "sections", "start_section": 1, "end_section": 2, "frequency": "daily", "end_repeat_type": "count", "end_repeat_count": 4, "alert_minutes_before": 0, "notes": "Agenda", "location": "Room"])
        let event = try #require(db.load().calendarEvents.first), semester = try #require(db.load().semesters.first)
        #expect(event.timeMode == "sections" && event.alertMinutesBefore == 0)
        #expect(CalendarEventProjector().project(event, semester: semester, week: 1).count == 4)
        _ = try server.invokeTool(name: "update_calendar_event", arguments: ["event_id": event.id.uuidString, "scope": "this_only", "occurrence_date": "2026-10-20", "start_time": "14:30", "end_time": "16:00", "new_title": "Changed", "alert_minutes_before": NSNull()])
        let all = db.load().calendarEvents.flatMap { CalendarEventProjector().project($0, semester: semester, week: 1, exceptions: db.load().exceptions) }
        #expect(all.count == 4 && all.filter { $0.title == "Changed" }.first?.startTime == "14:30")
        _ = try server.invokeTool(name: "delete_calendar_event", arguments: ["event_id": event.id.uuidString, "scope": "this_and_future", "occurrence_date": "2026-10-21"])
        let remaining = CalendarEventProjector().project(try #require(db.load().calendarEvents.first), semester: semester, week: 1, exceptions: db.load().exceptions)
        #expect(remaining.count == 1)
        #expect(throws: Error.self) { try server.invokeTool(name: "update_calendar_event", arguments: ["event_id": event.id.uuidString, "end_repeat_count": true]) }
    }
    @Test("Task full fields, repeat completion and all real focus modes")
    func tasksAndFocus() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_course", arguments: ["name": "Physics"])
        let course = try #require(db.load().courses.first)
        _ = try server.invokeTool(name: "add_task", arguments: ["title": "Review", "course_id": course.id.uuidString, "priority": "high", "target_minutes": 45, "start_date": "2026-10-19", "due_date": "2026-10-19 18:00", "frequency": "daily", "end_repeat_type": "count", "end_repeat_count": 2])
        let task = try #require(db.load().pomodoroTasks.first)
        #expect(task.targetMinutes == 45 && task.courseId == course.id)
        for mode in ["pomodoro", "count_up", "countdown"] {
            _ = try server.invokeTool(name: "start_focus_timer", arguments: ["task_id": task.id.uuidString, "mode": mode])
            #expect(db.load().focusSession?.courseName == "Physics")
            _ = try server.invokeTool(name: "pause_focus_timer", arguments: [:])
            _ = try server.invokeTool(name: "resume_focus_timer", arguments: [:])
            _ = try server.invokeTool(name: "reset_focus_timer", arguments: [:])
        }
        #expect(db.load().pomodoroRecords.isEmpty)
        _ = try server.invokeTool(name: "complete_task", arguments: ["task_id": task.id.uuidString])
        _ = try server.invokeTool(name: "complete_task", arguments: ["task_id": task.id.uuidString, "is_completed": false])
        _ = try server.invokeTool(name: "complete_task", arguments: ["task_id": task.id.uuidString])
        #expect(db.load().pomodoroTasks.count == 2)
        #expect(db.load().pomodoroTasks.last?.dueDate == "2026-10-20 18:00")
        _ = try server.invokeTool(name: "update_task", arguments: ["task_id": task.id.uuidString, "course_id": NSNull(), "due_date": NSNull(), "start_date": NSNull(), "notes": "Details", "frequency": "none"])
        #expect(db.load().pomodoroTasks.first?.courseId == nil)
    }
    @Test("Atomic batch preflight, rollback and replay; schema rejects ignored fields")
    func batch() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        let rows: [[String: Any]] = [["tool": "add_task", "arguments": ["title": "First", "target_minutes": 45]], ["tool": "add_calendar_event", "arguments": ["title": "Second", "date": "2026-10-20"]]]
        _ = try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "one", "dry_run": true, "operations": rows])
        #expect(db.load().pomodoroTasks.isEmpty)
        let first = try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "one", "operations": rows])
        #expect(try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "one", "operations": rows]) == first)
        #expect(db.load().pomodoroTasks.count == 1 && db.load().calendarEvents.count == 1)
        let invalid: [[String: Any]] = [rows[0], ["tool": "add_task", "arguments": ["title": "Bad", "target_minutes": -1]]]
        #expect(throws: Error.self) { try server.invokeTool(name: "batch_edit_items", arguments: ["batch_id": "bad", "operations": invalid]) }
        #expect(db.load().pomodoroTasks.count == 1)
        #expect(throws: Error.self) { try server.invokeTool(name: "update_task", arguments: ["title": "First", "reminder": 15]) }
        let context = try server.invokeTool(name: "get_editing_context", arguments: [:])
        #expect(context.contains("targetMinutes") && !context.contains("syncDeviceId"))
    }
}

extension MCPCompleteEditingTests {
    @Test("Deleting future odd-week classes preserves parity; slot replacement cleans exceptions")
    func scopedDeletion() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_course", arguments: ["name": "Odd", "day_of_week": 1, "week_pattern": "odd", "start_week": 1, "end_week": 9])
        let course = try #require(db.load().courses.first), slot = try #require(course.schedules.first)
        _ = try server.invokeTool(name: "delete_course", arguments: ["course_id": course.id.uuidString, "scope": "this_and_future", "week": 5])
        #expect(db.load().courses.first?.schedules.first?.weekPattern.customWeeks == [1,3])
        _ = try server.invokeTool(name: "manage_course_schedule", arguments: ["course_id": course.id.uuidString, "schedule_id": slot.id.uuidString, "action": "cancel", "occurrence_date": "2026-10-19"])
        _ = try server.invokeTool(name: "update_course", arguments: ["course_id": course.id.uuidString, "schedules": []])
        #expect(db.load().exceptions.isEmpty && db.load().courses.first?.schedules.isEmpty == true)
    }
    @Test("Future recurring edit preserves history and remaining count; series metadata reads are paged")
    func futureEvents() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_calendar_event", arguments: ["title": "Daily", "date": "2026-10-19", "frequency": "daily", "end_repeat_type": "count", "end_repeat_count": 5])
        let event = try #require(db.load().calendarEvents.first), semester = try #require(db.load().semesters.first)
        _ = try server.invokeTool(name: "update_calendar_event", arguments: ["event_id": event.id.uuidString, "scope": "this_and_future", "occurrence_date": "2026-10-21", "new_title": "Future", "start_time": "11:00", "end_time": "12:00"])
        let payload = db.load()
        let all = payload.calendarEvents.flatMap { CalendarEventProjector().project($0, semester: semester, week: 1, exceptions: payload.exceptions) }
        #expect(all.count == 5 && all.filter { $0.title == "Daily" }.count == 2 && all.filter { $0.title == "Future" }.count == 3)
        let raw = try server.invokeTool(name: "get_editing_context", arguments: ["kind": "events", "offset": 1, "limit": 1])
        let response = try #require(JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [String: Any])
        #expect((response["events"] as? [Any])?.count == 1 && (response["courses"] as? [Any])?.isEmpty == true)
    }
}

extension MCPCompleteEditingTests {
    @Test("Count-up minutes do not invent completed pomodoros")
    func focusStatistics() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_task", arguments: ["title": "Focus"])
        let task = try #require(db.load().pomodoroTasks.first)
        _ = try server.invokeTool(name: "start_focus_timer", arguments: ["task_id": task.id.uuidString, "mode": "count_up"])
        var payload = db.load(); payload.focusSession?.startedAt = Date().addingTimeInterval(-1800); try db.save(payload)
        _ = try server.invokeTool(name: "stop_focus_timer", arguments: [:])
        #expect(db.load().pomodoroTasks.first?.totalFocusMinutes == 30 && db.load().pomodoroTasks.first?.completedPomodoros == 0)
        let raw = try server.invokeTool(name: "get_focus_statistics", arguments: [:])
        let result = try #require(JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [String: Any])
        #expect(result["today_completed_pomodoros"] as? Int == 0)
    }
}

extension MCPCompleteEditingTests {
    @Test("Same-name add appends new slots; clearing teacher and single-room override is explicit")
    func appendAndClear() throws {
        let (server, db, root) = try fixture(); defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_course", arguments: ["name": "Same", "teacher": "Teacher", "classroom": "Room", "day_of_week": 1])
        let course = try #require(db.load().courses.first), slot = try #require(course.schedules.first)
        _ = try server.invokeTool(name: "add_course", arguments: ["name": "Same", "schedules": [["day_of_week": 2]]])
        #expect(db.load().courses.count == 1 && db.load().courses.first?.schedules.count == 2)
        _ = try server.invokeTool(name: "update_course", arguments: ["course_id": course.id.uuidString, "schedule_id": slot.id.uuidString, "teacher": NSNull()])
        _ = try server.invokeTool(name: "manage_course_schedule", arguments: ["course_id": course.id.uuidString, "schedule_id": slot.id.uuidString, "action": "reschedule", "occurrence_date": "2026-10-19", "classroom": NSNull()])
        let payload = db.load(), semester = try #require(payload.semesters.first)
        let item = try #require(TimelineEngine().generateOccurrences(semester: semester, courses: payload.courses, exceptions: payload.exceptions, forWeek: 1, activeWeek: 1).first { $0.scheduleId == slot.id })
        #expect(item.location == "" && item.teacher == "")
    }
}
