import Foundation
import ClassFlowCore

extension MCPToolHandlers {
    func editTask(arguments: [String: Any], creating: Bool = false) throws -> String {
        var payload = database.load()
        let index: Int? = creating ? nil : try uniqueIndex(payload.pomodoroTasks, query: arguments["task_id"] as? String ?? arguments["title"] as? String ?? "", id: { $0.id }, title: { $0.title })
        var task = index.map { payload.pomodoroTasks[$0] } ?? PomodoroTask(semesterId: payload.semesters.first(where: { $0.isCurrent })?.id, title: "", estimatedPomodoros: 1)
        if let title = arguments[creating ? "title" : "new_title"] as? String { task.title = title.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard !task.title.isEmpty else { throw automationError("待办标题不能为空。") }
        let semester = try editSemester(task.semesterId, payload: payload)
        task.notes = try nullableString("notes", arguments: arguments, old: task.notes)
        if let value = arguments["course_id"] {
            if value is NSNull || value as? String == "" { task.courseId = nil; task.courseName = nil }
            else {
                guard let id = value as? String, let uuid = UUID(uuidString: id), let course = payload.courses.first(where: { $0.id == uuid }) else { throw automationError("关联课程 UUID 不存在。") }
                task.courseId = course.id; task.courseName = course.name; task.semesterId = course.semesterId
            }
        }
        if let priority = arguments["priority"] {
            if let number = priority as? Int {
                guard (0...3).contains(number) else { throw automationError("优先级数字为 0–3。") }; task.priority = number >= 2 ? .high : number == 1 ? .medium : .low
            } else if let string = priority as? String, let value = ["high": TaskPriority.high, "medium": .medium, "low": .low][string] { task.priority = value }
            else { throw automationError("优先级为 high/medium/low 或 0–3。") }
        }
        if arguments["target_minutes"] != nil {
            task.targetMinutes = try int("target_minutes", arguments: arguments, old: task.effectiveTargetMinutes, range: 1...100000)
            task.estimatedPomodoros = (task.targetMinutes! + 24) / 25
        } else if arguments["estimated_pomodoros"] != nil {
            task.estimatedPomodoros = try int("estimated_pomodoros", arguments: arguments, old: task.estimatedPomodoros, range: 1...10000)
            task.targetMinutes = task.estimatedPomodoros * 25
        }
        task.startDate = try nullableString("start_date", arguments: arguments, old: task.startDate)
        task.dueDate = try nullableString("due_date", arguments: arguments, old: task.dueDate)
        if let start = task.startDate { try validateDate(start) }
        if let due = task.dueDate { try validateDateTime(due) }
        if let start = task.startDate, let due = task.dueDate, due < start { throw automationError("截止时间不能早于开始日期。") }
        var frequency = task.frequency
        try recurrence(arguments, frequency: &frequency, days: &task.customDaysOfWeek, weeks: &task.customWeeks, endType: &task.endRepeatType, until: &task.endRepeatUntil, count: &task.endRepeatCount,
            start: task.startDate ?? String(task.dueDate?.prefix(10) ?? Substring(formatForEdit(task.createdAt))), semester: semester)
        task.frequency = frequency
        if let index { payload.pomodoroTasks[index] = task } else { payload.pomodoroTasks.append(task) }
        try saveEdit(payload)
        return try textJSON(["task_id": task.id.uuidString, "title": task.title, "target_minutes": task.effectiveTargetMinutes, "updated": true])
    }
    func startCompleteFocus(arguments: [String: Any]) throws -> String {
        var payload = database.load()
        guard payload.focusSession == nil else { throw automationError("已有专注会话，请先结束或暂停。") }
        var task: PomodoroTask?
        if let id = arguments["task_id"] as? String {
            task = payload.pomodoroTasks[try uniqueIndex(payload.pomodoroTasks, query: id, id: { $0.id }, title: { $0.title })]
        }
        var course: Course?
        if let id = arguments["course_id"] as? String {
            course = payload.courses[try uniqueIndex(payload.courses, query: id, id: { $0.id }, title: { $0.name })]
            if let task, task.courseId != course?.id { throw automationError("待办与指定课程不一致。") }
            if task == nil {
                task = payload.pomodoroTasks.first { $0.courseId == course?.id && !$0.isCompleted }
                if task == nil {
                    let created = PomodoroTask(semesterId: course?.semesterId, title: "\(course!.name) 课前预习与复习", courseId: course?.id, courseName: course?.name)
                    payload.pomodoroTasks.append(created); task = created
                }
            }
        }
        if task == nil, let title = arguments["task_title"] as? String, !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let found = payload.pomodoroTasks.filter { $0.title == title && !$0.isCompleted }
            guard found.count <= 1 else { throw automationError("同名待办不唯一，请使用 task_id。") }
            task = found.first
            if task == nil { let created = PomodoroTask(semesterId: payload.semesters.first(where: { $0.isCurrent })?.id, title: title); payload.pomodoroTasks.append(created); task = created }
        }
        guard task?.isCompleted != true else { throw automationError("已完成待办不能开始专注。") }
        let modeName = arguments["mode"] as? String ?? "pomodoro"
        guard let mode = ["pomodoro": FocusMode.pomodoro, "count_up": .countUp, "countdown": .countdown, "番茄钟": .pomodoro, "正向计时": .countUp, "定目标": .countdown][modeName] else { throw automationError("mode 为 pomodoro/count_up/countdown。") }
        let minutes = try int("duration_minutes", arguments: arguments, old: mode == .countdown ? task?.effectiveTargetMinutes ?? 25 : 25, range: 1...1440)
        if course == nil, let id = task?.courseId { course = payload.courses.first { $0.id == id } }
        let schedule: CourseSchedule?
        if let id = arguments["schedule_id"] as? String {
            guard let found = course?.schedules.first(where: { $0.id.uuidString.lowercased() == id.lowercased() }) else { throw automationError("时段不属于专注课程。") }; schedule = found
        } else { schedule = course?.schedules.count == 1 ? course?.schedules.first : nil }
        payload.focusSession = FocusSession(taskId: task?.id, title: task?.title ?? course?.name ?? "日常专注", mode: mode, durationMinutes: minutes, startedAt: Date(),
            courseId: course?.id, courseName: course?.name ?? task?.courseName, location: schedule?.classroom)
        payload.settings.selectedTab = "study"; payload.settings.enableStudyModule = true
        try saveEdit(payload)
        return try textJSON(["message": "已为任务开启 \(minutes) 分钟专注", "session_id": payload.focusSession!.id.uuidString, "mode": modeName, "duration_minutes": minutes, "course_id": course?.id.uuidString ?? ""])
    }
}
