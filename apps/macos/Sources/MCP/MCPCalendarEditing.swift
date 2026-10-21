import Foundation
import ClassFlowCore

extension MCPToolHandlers {
    func calendarDraft(_ original: CalendarEvent?, arguments: [String: Any], payload: LocalDatabase.DatabasePayload) throws -> CalendarEvent {
        let semester = try editSemester(original?.semesterId, payload: payload)
        var event = original ?? CalendarEvent(semesterId: semester.id, title: "", startTime: "", endTime: "")
        event.frequency = CalendarEventProjector.normalizedFrequency(event.frequency)
        if let title = arguments[original == nil ? "title" : "new_title"] as? String { event.title = title.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard !event.title.isEmpty else { throw automationError("日程标题不能为空。") }
        let startDate = arguments["date"] as? String ?? event.startDate ?? String(event.startTime.prefix(10))
        try validateDate(startDate)
        let oldDate = event.startDate ?? String(event.startTime.prefix(10))
        let oldEndDate = event.endDate ?? String(event.endTime.prefix(10))
        var endDate = try nullableString("end_date", arguments: arguments, old: (event.frequency ?? "none") == "none" && original != nil ? oldEndDate : event.endDate)
        if arguments["date"] != nil, arguments["end_date"] == nil, (event.frequency ?? "none") == "none", let first = dateForEdit(oldDate), let last = dateForEdit(oldEndDate), let newStart = dateForEdit(startDate) {
            let span = Calendar.current.dateComponents([.day], from: first, to: last).day ?? 0
            endDate = formatForEdit(Calendar.current.date(byAdding: .day, value: max(0, span), to: newStart) ?? newStart)
        }
        if let allDay = arguments["is_all_day"] as? Bool { event.isAllDay = allDay }
        if let mode = arguments["time_mode"] as? String {
            guard ["sections", "time"].contains(mode) else { throw automationError("time_mode 必须为 sections 或 time。") }; event.timeMode = mode
        }
        if arguments["time_mode"] == nil && (arguments["start_section"] != nil || arguments["end_section"] != nil) { event.timeMode = "sections" }
        if arguments["time_mode"] == nil && (arguments["start_time"] != nil || arguments["end_time"] != nil) { event.timeMode = "time" }
        var start = arguments["start_time"] as? String ?? clockForEdit(event.startTime, fallback: "09:00")
        var end = arguments["end_time"] as? String ?? clockForEdit(event.endTime, fallback: "10:00")
        if event.isAllDay { start = "00:00"; end = "23:59" }
        else if event.timeMode == "sections" {
            let s = try int("start_section", arguments: arguments, old: event.startSection ?? 1, range: 1...1000)
            let e = try int("end_section", arguments: arguments, old: event.endSection ?? s, range: s...1000)
            guard let first = semester.sectionTime(for: s), let last = semester.sectionTime(for: e) else { throw automationError("节次不存在。") }
            event.startSection = s; event.endSection = e; start = first.startTime; end = last.endTime
        } else { event.startSection = nil; event.endSection = nil }
        try validateTime(start); try validateTime(end)
        try recurrence(arguments, frequency: &event.frequency, days: &event.customDaysOfWeek, weeks: &event.customWeeks, endType: &event.endRepeatType, until: &event.endRepeatUntil, count: &event.endRepeatCount, start: startDate, semester: semester)
        // A recurrence's cap is independent of its duration. Legacy single-day endDate must not truncate newly enabled repeats.
        if arguments["frequency"] != nil, event.frequency != "none", arguments["end_date"] == nil { endDate = nil }
        if event.frequency == "none" && endDate == nil { endDate = startDate }
        if let endDate { try validateDate(endDate); guard endDate >= startDate else { throw automationError("结束日期早于开始日期。") } }
        let timeEndDate = event.frequency == "none" ? endDate ?? startDate : startDate
        guard event.isAllDay || "\(timeEndDate)T\(end)" > "\(startDate)T\(start)" else { throw automationError("结束时间必须晚于开始时间。") }
        event.startDate = startDate; event.endDate = endDate
        event.startTime = "\(startDate)T\(start):00"; event.endTime = "\(timeEndDate)T\(end):00"
        event.location = try nullableString("location", arguments: arguments, old: event.location)
        event.description = try nullableString("notes", arguments: arguments, old: event.description)
        if let color = arguments["color"] as? String { event.color = resolveColorHex(color) }
        if let value = arguments["alert_minutes_before"] { event.alertMinutesBefore = value is NSNull ? nil : try int("alert_minutes_before", arguments: arguments, old: event.alertMinutesBefore ?? 15, range: 0...10080) }
        event.intervalWeeks = try int("interval_weeks", arguments: arguments, old: event.intervalWeeks ?? 1, range: 1...52)
        return event
    }
    func dateForEdit(_ key: String) -> Date? { let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.date(from: key) }
    func formatForEdit(_ date: Date) -> String { let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.string(from: date) }
    func clockForEdit(_ value: String, fallback: String) -> String { value.count >= 16 ? String(value.dropFirst(11).prefix(5)) : value.count == 5 ? value : fallback }

    func editCalendar(arguments: [String: Any], creating: Bool = false, deleting: Bool = false) throws -> String {
        var payload = database.load()
        if creating {
            let event = try calendarDraft(nil, arguments: arguments, payload: payload)
            payload.calendarEvents.append(event); try saveEdit(payload)
            return try textJSON(["event_id": event.id.uuidString, "title": event.title, "created": true])
        }
        let i = try uniqueIndex(payload.calendarEvents, query: arguments["event_id"] as? String ?? arguments["title"] as? String ?? "", id: { $0.id }, title: { $0.title })
        let original = payload.calendarEvents[i]
        let scope = try editScope(arguments)
        let semester = try editSemester(original.semesterId, payload: payload)
        if scope == .all || (original.frequency ?? "none") == "none" {
            if deleting {
                payload.calendarEvents.remove(at: i)
                removeEventExceptions(original.id, from: &payload)
                payload.tombstones.append(SyncTombstone(entityId: original.id.uuidString, entityType: "calendarEvent"))
            } else { payload.calendarEvents[i] = try calendarDraft(original, arguments: arguments, payload: payload) }
            try saveEdit(payload); return try textJSON(["event_id": original.id.uuidString, "scope": "all", "deleted": deleting, "message": deleting ? "已删除日程" : "已更新日程"])
        }
        guard let date = arguments["occurrence_date"] as? String else { throw automationError("单次或后续范围必须提供 occurrence_date。") }
        try validateDate(date)
        let dates = CalendarEventProjector().occurrenceDates(original, semester: semester, through: date)
        guard dates.last == date else { throw automationError("该日期不是此日程的有效发生日期。") }
        var newID: UUID?
        if scope == .thisOnly {
            // Detach an edited occurrence; the original recurrence remains untouched.
            for ex in payload.exceptions where ex.scheduleId == original.id && ex.originalDate == date { payload.tombstones.append(SyncTombstone(entityId: ex.id.uuidString, entityType: "exception")) }
            payload.exceptions.removeAll { $0.scheduleId == original.id && $0.originalDate == date }
            payload.exceptions.append(ScheduleException(scheduleId: original.id, originalDate: date, exceptionType: .cancel, reason: "单次删除"))
        } else {
            if date == dates.first {
                payload.calendarEvents.remove(at: i); removeEventExceptions(original.id, from: &payload)
                payload.tombstones.append(SyncTombstone(entityId: original.id.uuidString, entityType: "calendarEvent"))
            } else {
                let previous = formatForEdit(Calendar.current.date(byAdding: .day, value: -1, to: dateForEdit(date)!)!)
                payload.calendarEvents[i].endDate = previous
                payload.calendarEvents[i].endRepeatType = "untilDate"; payload.calendarEvents[i].endRepeatUntil = previous
                let removed = payload.exceptions.filter { $0.scheduleId == original.id && $0.originalDate >= date }
                payload.exceptions.removeAll { $0.scheduleId == original.id && $0.originalDate >= date }
                for ex in removed { payload.tombstones.append(SyncTombstone(entityId: ex.id.uuidString, entityType: "exception")) }
            }
        }
        if !deleting {
            var successor = original; successor.id = UUID(); successor.startDate = date
            successor.startTime = "\(date)T\(clockForEdit(original.startTime, fallback: "09:00")):00"
            successor.endTime = "\(date)T\(clockForEdit(original.endTime, fallback: "10:00")):00"
            if scope == .thisOnly {
                successor.frequency = "none"; successor.endDate = date; successor.customDaysOfWeek = nil; successor.customWeeks = nil; successor.endRepeatType = "never"
            } else if original.endRepeatType == "count" { successor.endRepeatCount = max(1, (original.endRepeatCount ?? 1) - dates.count + 1) }
            successor = try calendarDraft(successor, arguments: arguments, payload: payload)
            payload.calendarEvents.append(successor); newID = successor.id
        }
        try saveEdit(payload)
        return try textJSON(["event_id": original.id.uuidString, "new_event_id": newID?.uuidString ?? "", "scope": scope.rawValue, "deleted": deleting])
    }
    func removeEventExceptions(_ id: UUID, from payload: inout LocalDatabase.DatabasePayload) {
        for ex in payload.exceptions where ex.scheduleId == id { payload.tombstones.append(SyncTombstone(entityId: ex.id.uuidString, entityType: "exception")) }
        payload.exceptions.removeAll { $0.scheduleId == id }
    }
}
