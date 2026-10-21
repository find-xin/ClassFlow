import Foundation
import ClassFlowCore

extension MCPToolHandlers {
    func automationError(_ message: String) -> NSError {
        NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: message])
    }

    /// Resolve UUID first, then exact name, then a unique partial name. Never pick an arbitrary match.
    func uniqueIndex<T>(_ values: [T], query: String, id: (T) -> UUID, title: (T) -> String) throws -> Int {
        let key = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !key.isEmpty else { throw automationError("请提供名称或 UUID。") }
        if let uuid = UUID(uuidString: key) {
            guard let i = values.firstIndex(where: { id($0) == uuid }) else { throw automationError("未找到 UUID 对应的项目。") }
            return i
        }
        let exact = values.indices.filter { title(values[$0]).lowercased() == key }
        let matches = exact.isEmpty ? values.indices.filter { title(values[$0]).lowercased().contains(key) } : exact
        guard matches.count == 1, let i = matches.first else {
            throw automationError(matches.isEmpty ? "未找到匹配项目。" : "匹配到多个项目，请先查询列表，再使用 UUID 操作。")
        }
        return i
    }

    func validateDate(_ value: String) throws {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        guard let date = formatter.date(from: value), formatter.string(from: date) == value else {
            throw automationError("日期必须是有效的 YYYY-MM-DD。")
        }
    }

    func validateTime(_ value: String) throws {
        let parts = value.split(separator: ":")
        guard value.count == 5, parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]),
              (0...23).contains(hour), (0...59).contains(minute) else {
            throw automationError("时间必须是有效的 HH:mm。")
        }
    }

    func updateCourseSchedules(_ course: inout Course, arguments: [String: Any], payload: LocalDatabase.DatabasePayload) throws {
        let fields = ["day_of_week", "start_section", "end_section", "start_week", "end_week", "week_pattern", "custom_weeks"]
        guard fields.contains(where: { arguments[$0] != nil }) else { return }
        let indices: [Int]
        if let id = arguments["schedule_id"] as? String {
            guard let uuid = UUID(uuidString: id), let i = course.schedules.firstIndex(where: { $0.id == uuid }) else { throw automationError("未找到课时 UUID。") }
            indices = [i]
        } else {
            guard course.schedules.count == 1 else { throw automationError("该课程有多个课时，请先查询并提供 schedule_id。") }
            indices = Array(course.schedules.indices)
        }
        let semester = payload.semesters.first { $0.id == course.semesterId }
        let maxWeek = semester?.totalWeeks ?? 30
        for i in indices {
            var schedule = course.schedules[i]
            schedule.dayOfWeek = arguments["day_of_week"] as? Int ?? schedule.dayOfWeek
            schedule.startSection = arguments["start_section"] as? Int ?? schedule.startSection
            schedule.endSection = arguments["end_section"] as? Int ?? schedule.endSection
            guard (1...7).contains(schedule.dayOfWeek), schedule.startSection >= 1, schedule.endSection >= schedule.startSection,
                  semester?.sections.contains(where: { $0.section == schedule.startSection }) == true,
                  semester?.sections.contains(where: { $0.section == schedule.endSection }) == true else { throw automationError("星期或起止节次无效。") }
            if fields.dropFirst(3).contains(where: { arguments[$0] != nil }) {
                let start = arguments["start_week"] as? Int ?? 1
                let end = arguments["end_week"] as? Int ?? maxWeek
                guard start >= 1, end >= start, end <= maxWeek else { throw automationError("周次超出学期范围。") }
                switch arguments["week_pattern"] as? String ?? "all" {
                case "odd": schedule.weekPattern = .range(start: start, end: end, type: .odd)
                case "even": schedule.weekPattern = .range(start: start, end: end, type: .even)
                case "all": schedule.weekPattern = .range(start: start, end: end, type: .all)
                case "custom":
                    guard let weeks = arguments["custom_weeks"] as? [Int], !weeks.isEmpty,
                          weeks.allSatisfy({ (1...maxWeek).contains($0) }) else { throw automationError("自定义周次无效。") }
                    schedule.weekPattern = .custom(weeks)
                default: throw automationError("周次模式必须为 all、odd、even 或 custom。")
                }
            }
            course.schedules[i] = schedule
        }
    }

    public func updateCalendarEvent(arguments: [String: Any]) throws -> String {
        var payload = database.load()
        let query = arguments["event_id"] as? String ?? arguments["title"] as? String ?? ""
        let i = try uniqueIndex(payload.calendarEvents, query: query, id: { $0.id }, title: { $0.title })
        var event = payload.calendarEvents[i]
        if let value = arguments["new_title"] as? String {
            guard !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw automationError("标题不能为空。") }
            event.title = value
        }
        let date = arguments["date"] as? String ?? event.startDate ?? String(event.startTime.prefix(10))
        try validateDate(date)
        let start = arguments["start_time"] as? String ?? String(event.startTime.dropFirst(11).prefix(5))
        let end = arguments["end_time"] as? String ?? String(event.endTime.dropFirst(11).prefix(5))
        try validateTime(start)
        try validateTime(end)
        if let value = arguments["is_all_day"] as? Bool { event.isAllDay = value }
        guard event.isAllDay || start < end else { throw automationError("结束时间必须晚于开始时间。") }
        event.startDate = date
        if event.frequency == nil || event.frequency == "none" { event.endDate = date }
        event.startTime = "\(date)T\(start):00"
        event.endTime = "\(date)T\(end):00"
        if let value = arguments["location"] as? String { event.location = value }
        if let value = arguments["notes"] as? String { event.description = value }
        if let value = arguments["color"] as? String { event.color = resolveColorHex(value) }
        payload.calendarEvents[i] = event
        try database.save(payload)
        notifyAppAndWidgets(payload: payload)
        return "已更新日程「\(event.title)」。"
    }

    public func updateTask(arguments: [String: Any]) throws -> String {
        var payload = database.load()
        let query = arguments["task_id"] as? String ?? arguments["title"] as? String ?? ""
        let i = try uniqueIndex(payload.pomodoroTasks, query: query, id: { $0.id }, title: { $0.title })
        var task = payload.pomodoroTasks[i]
        if let value = arguments["new_title"] as? String {
            guard !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw automationError("标题不能为空。") }
            task.title = value
        }
        if let value = arguments["notes"] as? String { task.notes = value }
        if let value = arguments["due_date"] as? String {
            if !value.isEmpty { try validateDate(value) }
            task.dueDate = value.isEmpty ? nil : value
        }
        if let value = arguments["estimated_pomodoros"] as? Int {
            guard (1...10000).contains(value) else { throw automationError("预估番茄数必须为 1–10000。") }
            task.estimatedPomodoros = value
        }
        if let value = arguments["priority"] as? String {
            guard let priority = ["high": TaskPriority.high, "medium": .medium, "low": .low][value] else {
                throw automationError("优先级必须为 high、medium 或 low。")
            }
            task.priority = priority
        }
        payload.pomodoroTasks[i] = task
        try database.save(payload)
        notifyAppAndWidgets(payload: payload)
        return "已更新待办「\(task.title)」。"
    }
}


extension MCPServer {
    func managementToolDefinitions() -> [[String: Any]] {
        func tool(_ name: String, _ description: String, _ properties: [String: Any]) -> [String: Any] {
            ["name": name, "description": description, "inputSchema": ["type": "object", "properties": properties]]
        }
        let text: [String: Any] = ["type": "string"]
        return [
            tool("update_calendar_event", "修改日程的标题、日期、时间、地点、颜色和备注。使用 event_id 或原 title 定位。", [
                "event_id": text, "title": text, "new_title": text, "date": text, "start_time": text,
                "end_time": text, "location": text, "color": text, "notes": text, "is_all_day": ["type": "boolean"]]),
            tool("update_task", "修改待办的标题、备注、截止日期、预估番茄数和优先级。使用 task_id 或原 title 定位。due_date 传空字符串清除。", [
                "task_id": text, "title": text, "new_title": text, "notes": text, "due_date": text,
                "estimated_pomodoros": ["type": "integer", "minimum": 1],
                "priority": ["type": "string", "enum": ["high", "medium", "low"]]]),
            tool("pause_focus_timer", "暂停当前专注会话，暂停时间不计入统计。", [:]),
            tool("resume_focus_timer", "继续已暂停的专注会话。", [:])
        ]
    }
}
