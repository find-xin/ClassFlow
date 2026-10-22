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
        var payload = database.load()
        let totals = ["semesters": payload.semesters.count, "courses": payload.courses.count, "events": payload.calendarEvents.count, "tasks": payload.pomodoroTasks.count, "exceptions": payload.exceptions.count]
        // Deliberately exclude settings, credentials, cloud configuration and import receipts.
        struct Context: Encodable {
            var semesters: [Semester]; var courses: [Course]; var events: [CalendarEvent]
            var tasks: [PomodoroTask]; var exceptions: [ScheduleException]; var focusSession: FocusSession?
            var totals: [String: Int]; var matching_totals: [String: Int]; var offset: Int; var limit: Int
        }
        let kind = arguments["kind"] as? String ?? "all"
        if let selection = arguments["selection"] as? [String: Any] {
            guard selection["kind"] as? String == kind else { throw automationError("selection.kind 必须与 kind 一致。") }
            let ids = Set(try selectedIDs(selection, payload: payload))
            if kind == "events" { payload.calendarEvents.removeAll { !ids.contains($0.id) } }
            if kind == "courses" { payload.courses.removeAll { !ids.contains($0.id) } }
            if kind == "tasks" { payload.pomodoroTasks.removeAll { !ids.contains($0.id) } }
        }
        let offset = try int("offset", arguments: arguments, old: 0, range: 0...1000000)
        let limit = try int("limit", arguments: arguments, old: 50, range: 1...1000)
        func page<T>(_ values: [T], _ name: String) -> [T] { kind == "all" || kind == name ? Array(values.dropFirst(offset).prefix(limit)) : [] }
        return try editJSON(Context(semesters: page(payload.semesters, "semesters"), courses: page(payload.courses, "courses"), events: page(payload.calendarEvents, "events"),
            tasks: page(payload.pomodoroTasks, "tasks"), exceptions: page(payload.exceptions, "exceptions"), focusSession: payload.focusSession,
            totals: totals, matching_totals: ["semesters": payload.semesters.count, "courses": payload.courses.count, "events": payload.calendarEvents.count, "tasks": payload.pomodoroTasks.count, "exceptions": payload.exceptions.count], offset: offset, limit: limit))
    }

    func resetFocus() throws -> String {
        var payload = database.load()
        payload.focusSession = nil
        try saveEdit(payload)
        return "已重置专注计时，本次不计入统计。"
    }
}
