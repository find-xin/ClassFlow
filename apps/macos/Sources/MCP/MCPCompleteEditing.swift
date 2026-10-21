import Foundation
import CryptoKit
import ClassFlowCore

extension MCPToolHandlers {
    func deleteCompleteCourse(arguments: [String: Any]) throws -> String {
        var payload = database.load()
        let i = try uniqueIndex(payload.courses, query: arguments["course_id"] as? String ?? arguments["name"] as? String ?? arguments["id"] as? String ?? "", id: { $0.id }, title: { $0.name })
        var course = payload.courses[i]
        let scope = try editScope(arguments)
        if scope == .all, let id = arguments["schedule_id"] as? String {
            guard let slot = course.schedules.first(where: { $0.id.uuidString.lowercased() == id.lowercased() }) else { throw automationError("时段不属于此课程。") }
            course.schedules.removeAll { $0.id == slot.id }; removeScheduleData(slot.id, from: &payload); payload.courses[i] = course
        } else if scope == .all {
            for slot in course.schedules { removeScheduleData(slot.id, from: &payload) }
            payload.courses.remove(at: i)
            payload.tombstones.append(SyncTombstone(entityId: course.id.uuidString, entityType: "course"))
        } else {
            let semester = try editSemester(course.semesterId, payload: payload)
            let calculator = WeekCalculator(timeZoneId: TimeZone.current.identifier)
            let date = arguments["occurrence_date"] as? String ?? arguments["date"] as? String ?? formatForEdit(Date())
            try validateDate(date)
            let week = arguments["week"] as? Int ?? calculator.weekIndex(for: date, semesterStartDate: semester.startDate) ?? 0
            guard (1...semester.totalWeeks).contains(week) else { throw automationError("周次超出学期。") }
            let requested = arguments["schedule_id"] as? String
            if let requested, !course.schedules.contains(where: { $0.id.uuidString.lowercased() == requested.lowercased() }) { throw automationError("时段不属于此课程。") }
            var remaining: [CourseSchedule] = [], affected = 0
            for var slot in course.schedules {
                let matches = requested == nil || slot.id.uuidString.lowercased() == requested?.lowercased()
                if scope == .thisOnly {
                    if matches && calculator.dateString(forWeek: week, dayOfWeek: slot.dayOfWeek, semesterStartDate: semester.startDate) == date && slot.weekPattern.isActive(inWeek: week, totalWeeks: semester.totalWeeks) {
                        affected += 1
                        for ex in payload.exceptions where ex.scheduleId == slot.id && ex.originalDate == date { payload.tombstones.append(SyncTombstone(entityId: ex.id.uuidString, entityType: "exception")) }
                        payload.exceptions.removeAll { $0.scheduleId == slot.id && $0.originalDate == date }
                        payload.exceptions.append(ScheduleException(scheduleId: slot.id, originalDate: date, exceptionType: .cancel, reason: "单次删除"))
                    }
                    remaining.append(slot)
                } else if matches {
                    affected += 1
                    let prior = (1...semester.totalWeeks).filter { $0 < week && slot.weekPattern.isActive(inWeek: $0, totalWeeks: semester.totalWeeks) }
                    if prior.isEmpty { removeScheduleData(slot.id, from: &payload) }
                    else {
                        slot.weekPattern = .custom(prior); remaining.append(slot)
                        let cutoff = calculator.dateString(forWeek: week, dayOfWeek: slot.dayOfWeek, semesterStartDate: semester.startDate)
                        for ex in payload.exceptions where ex.scheduleId == slot.id && ex.originalDate >= cutoff { payload.tombstones.append(SyncTombstone(entityId: ex.id.uuidString, entityType: "exception")) }
                        payload.exceptions.removeAll { $0.scheduleId == slot.id && $0.originalDate >= cutoff }
                    }
                } else { remaining.append(slot) }
            }
            guard affected > 0 else { throw automationError("所选日期没有匹配的有效课时。") }
            course.schedules = remaining
            // Keep course metadata and history when only future occurrences are removed.
            payload.courses[i] = course
        }
        try saveEdit(payload)
        return try textJSON(["course_id": course.id.uuidString, "scope": scope.rawValue, "deleted": true, "message": "已删除课程安排"])
    }

    func editingContext(arguments: [String: Any]) throws -> String {
        let payload = database.load()
        // Deliberately exclude settings, credentials, cloud configuration and import receipts.
        struct Context: Encodable {
            var semesters: [Semester]; var courses: [Course]; var events: [CalendarEvent]
            var tasks: [PomodoroTask]; var exceptions: [ScheduleException]; var focusSession: FocusSession?
            var totals: [String: Int]; var offset: Int; var limit: Int
        }
        let kind = arguments["kind"] as? String ?? "all"
        let offset = try int("offset", arguments: arguments, old: 0, range: 0...1000000)
        let limit = try int("limit", arguments: arguments, old: 50, range: 1...500)
        func page<T>(_ values: [T], _ name: String) -> [T] { kind == "all" || kind == name ? Array(values.dropFirst(offset).prefix(limit)) : [] }
        return try editJSON(Context(semesters: page(payload.semesters, "semesters"), courses: page(payload.courses, "courses"), events: page(payload.calendarEvents, "events"),
            tasks: page(payload.pomodoroTasks, "tasks"), exceptions: page(payload.exceptions, "exceptions"), focusSession: payload.focusSession,
            totals: ["semesters": payload.semesters.count, "courses": payload.courses.count, "events": payload.calendarEvents.count, "tasks": payload.pomodoroTasks.count, "exceptions": payload.exceptions.count], offset: offset, limit: limit))
    }

    func batchEdit(arguments: [String: Any]) throws -> String {
        guard let batchID = arguments["batch_id"] as? String, !batchID.isEmpty, batchID.count <= 200,
              let operations = arguments["operations"] as? [[String: Any]], !operations.isEmpty, operations.count <= 100 else { throw automationError("需提供 batch_id 和 1–100 项 operations。") }
        let allowed: Set<String> = ["add_course", "update_course", "delete_course", "manage_course_schedule", "add_calendar_event", "update_calendar_event", "delete_calendar_event", "add_task", "update_task", "complete_task", "delete_task"]
        let data = try JSONSerialization.data(withJSONObject: operations, options: [.sortedKeys])
        let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        var payload = database.load()
        let key = "editing:" + batchID
        if let prior = payload.calendarImportReceipts?[key] {
            guard prior.digest == digest else { throw automationError("batch_id 已用于不同内容。") }
            return prior.result
        }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("ClassFlow-edit-\(UUID())", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        defer { try? FileManager.default.removeItem(at: directory) }
        let staging = LocalDatabase(customURL: directory.appendingPathComponent("database.json"))
        try staging.save(payload)
        let server = MCPServer(handlers: MCPToolHandlers(database: staging, enablesAppIntegration: false))
        var results: [String] = []
        for (index, operation) in operations.enumerated() {
            guard let name = operation["tool"] as? String, allowed.contains(name), let args = operation["arguments"] as? [String: Any] else { throw automationError("第 \(index + 1) 项操作无效或不支持批量执行。") }
            do { results.append(try server.invokeTool(name: name, arguments: args)) }
            catch { throw automationError("第 \(index + 1) 项失败，整批未写入：\(error.localizedDescription)") }
        }
        let dryRun = arguments["dry_run"] as? Bool ?? false
        let result = try textJSON(["batch_id": batchID, "dry_run": dryRun, "count": results.count, "results": results])
        if !dryRun {
            payload = staging.load()
            if payload.calendarImportReceipts == nil { payload.calendarImportReceipts = [:] }
            payload.calendarImportReceipts?[key] = CalendarImportReceipt(digest: digest, result: result)
            try saveEdit(payload)
        }
        return result
    }

    func resetFocus() throws -> String {
        var payload = database.load()
        payload.focusSession = nil
        try saveEdit(payload)
        return "已重置专注计时，本次不计入统计。"
    }
}
