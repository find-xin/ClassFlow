import Foundation
import ClassFlowCore
import AppKit

/// Executes high-level operations on ClassFlow data on behalf of MCP tools and ChatGPT.
public struct MCPToolHandlers: Sendable {
    public let database: LocalDatabase
    public let widgetStore: WidgetDataStore
    public let timelineEngine: TimelineEngine
    public let enablesAppIntegration: Bool

    public init(
        database: LocalDatabase = LocalDatabase(),
        widgetStore: WidgetDataStore = WidgetDataStore(),
        enablesAppIntegration: Bool = true
    ) {
        self.database = database
        self.widgetStore = widgetStore
        self.timelineEngine = TimelineEngine()
        self.enablesAppIntegration = enablesAppIntegration
    }

    // MARK: - Notification & Live Reload

    /// Broadcasts real-time notification to running ClassFlow macOS app and updates desktop widgets.
    public func notifyAppAndWidgets(payload: LocalDatabase.DatabasePayload) {
        guard enablesAppIntegration else { return }
        // 1. Update WidgetKit shared data
        updateWidgetPayload(payload)

        // 2. Post distributed notification to running ClassFlow AppKit process
        DistributedNotificationCenter.default().postNotificationName(
            NSNotification.Name("org.classflow.databaseDidChange"),
            object: nil,
            userInfo: nil,
            deliverImmediately: true
        )
    }

    private func updateWidgetPayload(_ payload: LocalDatabase.DatabasePayload) {
        guard let current = payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first else { return }
        let currentWeek = calculateCurrentWeek(for: current)
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        let todayStr = df.string(from: Date())

        let currentCourses = payload.courses.filter { $0.semesterId == current.id }
        let currentEvents = payload.calendarEvents.filter {
            $0.semesterId == current.id || ($0.semesterId == nil && current.id == payload.semesters.first?.id)
        }

        var weekItems = timelineEngine.generateOccurrences(
            semester: current,
            courses: currentCourses,
            exceptions: payload.exceptions,
            forWeek: currentWeek,
            activeWeek: currentWeek,
            showNonCurrentWeekCourses: payload.settings.showNonCurrentWeekCourses
        )

        let wDates = timelineEngine.weekCalculator.dateStringsInWeek(weekNumber: currentWeek, semesterStartDate: current.startDate)
        for event in currentEvents {
            weekItems.append(contentsOf: projectCalendarEventOccurrences(event, weekDates: wDates, semester: current, selectedWeek: currentWeek))
        }

        let todayItems = weekItems.filter { $0.date == todayStr && $0.status != .cancelled && $0.isCurrentWeek }
            .sorted { $0.startSection < $1.startSection }

        let widgetToday = todayItems.map { occ in
            WidgetCourseItem(
                id: occ.id,
                title: occ.title,
                location: occ.location,
                teacher: occ.teacher,
                startTime: occ.startTime ?? "08:00",
                endTime: occ.endTime ?? "09:35",
                startSection: occ.startSection,
                endSection: occ.endSection,
                colorHex: occ.color,
                isOngoing: false
            )
        }

        let widgetPayload = WidgetTimelinePayload(
            semesterName: current.name,
            currentWeek: currentWeek,
            todayDateString: todayStr,
            nextClass: widgetToday.first,
            todayClasses: widgetToday,
            weekSchedule: [],
            lastUpdated: ISO8601DateFormatter().string(from: Date())
        )
        try? widgetStore.save(widgetPayload)
    }

    // MARK: - 1. Status & App Info

    public func getStatus() -> [String: Any] {
        let payload = database.load()
        let currentSem = payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first
        let currentWeek = currentSem != nil ? calculateCurrentWeek(for: currentSem!) : 1
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        let todayStr = df.string(from: Date())
        let weekday = Calendar.current.component(.weekday, from: Date())
        let dayOfWeek = weekday == 1 ? 7 : (weekday - 1)

        let pendingTasks = payload.pomodoroTasks.filter { !$0.isCompleted }.count
        let syncProvider = CloudSettings.defaults.string(forKey: "sync_provider_type") ?? "未配置"

        return [
            "current_semester": [
                "id": currentSem?.id.uuidString ?? "",
                "name": currentSem?.name ?? "未配置学期",
                "start_date": currentSem?.startDate ?? "",
                "total_weeks": currentSem?.totalWeeks ?? 16,
                "current_week": currentWeek
            ],
            "today": [
                "date": todayStr,
                "day_of_week": dayOfWeek,
                "day_name": ["周一", "周二", "周三", "周四", "周五", "周六", "周日"][dayOfWeek - 1]
            ],
            "counts": [
                "courses": payload.courses.count,
                "calendar_events": payload.calendarEvents.count,
                "tasks_total": payload.pomodoroTasks.count,
                "tasks_in_progress": pendingTasks,
                "focus_records": payload.pomodoroRecords.count
            ],
            "active_focus_task": payload.focusSession?.title as Any,
            "focus_running": payload.focusSession != nil && payload.focusSession?.pausedAt == nil,
            "focus_elapsed_seconds": payload.focusSession?.elapsedSeconds(at: Date()) ?? 0,
            "cloud_sync_provider": syncProvider
        ]
    }

    // MARK: - 2. Timetable Query

    public func queryTimetable(week: Int?, dayOfWeek: Int?) -> [String: Any] {
        let payload = database.load()
        guard let semester = payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first else {
            return ["error": "当前没有找到激活的学期课表。"]
        }

        let currentWeek = calculateCurrentWeek(for: semester)
        let targetWeek = week ?? currentWeek
        guard targetWeek >= 1 && targetWeek <= semester.totalWeeks else {
            return ["error": "查询周次超出学期范围（1..\(semester.totalWeeks) 周）。"]
        }

        let semCourses = payload.courses.filter { $0.semesterId == semester.id }
        let semEvents = payload.calendarEvents.filter {
            $0.semesterId == semester.id || ($0.semesterId == nil && semester.id == payload.semesters.first?.id)
        }

        var items = timelineEngine.generateOccurrences(
            semester: semester,
            courses: semCourses,
            exceptions: payload.exceptions,
            forWeek: targetWeek,
            activeWeek: currentWeek,
            showNonCurrentWeekCourses: true
        )

        let weekDates = timelineEngine.weekCalculator.dateStringsInWeek(weekNumber: targetWeek, semesterStartDate: semester.startDate)
        for event in semEvents {
            items.append(contentsOf: projectCalendarEventOccurrences(event, weekDates: weekDates, semester: semester, selectedWeek: targetWeek))
        }

        if let day = dayOfWeek {
            items = items.filter { $0.dayOfWeek == day }
        }

        items.sort {
            if $0.dayOfWeek != $1.dayOfWeek {
                return $0.dayOfWeek < $1.dayOfWeek
            }
            return $0.startSection < $1.startSection
        }

        let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
        let formatted = items.map { occ -> [String: Any] in
            var dict: [String: Any] = [
                "id": occ.id,
                "title": occ.title,
                "day_of_week": occ.dayOfWeek,
                "day_name": (occ.dayOfWeek >= 1 && occ.dayOfWeek <= 7) ? dayNames[occ.dayOfWeek - 1] : "周\(occ.dayOfWeek)",
                "date": occ.date,
                "start_section": occ.startSection,
                "end_section": occ.endSection,
                "start_time": (occ.startTime ?? "08:00") as Any,
                "end_time": (occ.endTime ?? "09:35") as Any,
                "is_current_week": occ.isCurrentWeek,
                "color": occ.color,
                "has_conflict": occ.hasConflict,
                "type": occ.calendarEventId != nil ? "calendar_event" : "course"
            ]
            if let loc = occ.location, !loc.isEmpty { dict["location"] = loc }
            if let t = occ.teacher, !t.isEmpty { dict["teacher"] = t }
            if let n = occ.notes, !n.isEmpty { dict["notes"] = n }
            if let cr = occ.credit { dict["credit"] = cr }
            return dict
        }

        return [
            "semester_name": semester.name,
            "week": targetWeek,
            "is_current_week": (targetWeek == currentWeek),
            "total_items": formatted.count,
            "items": formatted
        ]
    }

    // MARK: - 3. Add Course

    public func addCourse(arguments: [String: Any]) throws -> String {
        guard let name = arguments["name"] as? String, !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少必填参数：课程名称 (name)"])
        }
        guard let dayOfWeek = arguments["day_of_week"] as? Int, (1...7).contains(dayOfWeek) else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少有效参数：星期几 (day_of_week: 1..7)"])
        }
        guard let startSec = arguments["start_section"] as? Int, startSec >= 1 else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少有效参数：开始节次 (start_section >= 1)"])
        }
        let endSec = arguments["end_section"] as? Int ?? startSec
        let classroom = arguments["classroom"] as? String ?? ""
        let teacher = arguments["teacher"] as? String
        let creditVal = arguments["credit"] as? Double ?? (arguments["credit"] as? NSNumber)?.doubleValue
        let notes = arguments["notes"] as? String
        let colorInput = arguments["color"] as? String
        let resolvedColor = resolveColorHex(colorInput)

        var payload = database.load()
        guard let semester = payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first else {
            throw NSError(domain: "ClassFlowMCP", code: 404, userInfo: [NSLocalizedDescriptionKey: "未找到当前学期，无法添加课程。"])
        }

        let startWeek = arguments["start_week"] as? Int ?? 1
        let endWeek = arguments["end_week"] as? Int ?? semester.totalWeeks
        let rawPattern = (arguments["week_pattern"] as? String)?.lowercased() ?? "all"
        guard startWeek >= 1, endWeek >= startWeek, endWeek <= semester.totalWeeks else { throw automationError("起止周次超出学期范围。") }
        guard endSec >= startSec, semester.sections.contains(where: { $0.section == startSec }), semester.sections.contains(where: { $0.section == endSec }) else { throw automationError("起止节次必须存在于当前学期，且结束节次不得早于开始节次。") }
        if let weeks = arguments["custom_weeks"] as? [Int] {
            guard !weeks.isEmpty, weeks.allSatisfy({ (1...semester.totalWeeks).contains($0) }) else { throw automationError("自定义周次超出学期范围。") }
        }

        let weekPattern: WeekPattern
        switch rawPattern {
        case "odd", "单周":
            weekPattern = .range(start: startWeek, end: endWeek, type: .odd)
        case "even", "双周":
            weekPattern = .range(start: startWeek, end: endWeek, type: .even)
        case "custom", "自定义":
            let customWeeks = arguments["custom_weeks"] as? [Int] ?? Array(startWeek...endWeek)
            weekPattern = .custom(customWeeks)
        case "all", "每周":
            weekPattern = .range(start: startWeek, end: endWeek, type: .all)
        default:
            throw automationError("周次模式必须为 all、odd、even 或 custom。")
        }

        let schedule = CourseSchedule(
            id: UUID(),
            courseId: UUID(),
            dayOfWeek: dayOfWeek,
            startSection: startSec,
            endSection: max(startSec, endSec),
            classroom: classroom,
            teacher: teacher,
            weekPattern: weekPattern
        )

        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        var courseDesc = ""

        if let existingIdx = payload.courses.firstIndex(where: {
            $0.semesterId == semester.id && $0.name.trimmingCharacters(in: .whitespaces).lowercased() == trimmedName.lowercased()
        }) {
            // Append schedule to existing course
            var existing = payload.courses[existingIdx]
            var sCopy = schedule
            sCopy.courseId = existing.id
            existing.schedules.append(sCopy)
            if let t = teacher, !t.isEmpty { existing.teacher = t }
            if let cr = creditVal { existing.credit = cr }
            if let col = colorInput, !col.isEmpty { existing.color = resolvedColor }
            if let n = notes, !n.isEmpty { existing.notes = n }
            payload.courses[existingIdx] = existing
            courseDesc = "已为已有课程「\(existing.name)」追加上课时段（周\(dayOfWeek) 第\(startSec)-\(endSec)节 @ \(classroom.isEmpty ? "未指定教室" : classroom)）。"
        } else {
            // Create brand new course
            let courseId = UUID()
            var sCopy = schedule
            sCopy.courseId = courseId
            let newCourse = Course(
                id: courseId,
                semesterId: semester.id,
                name: trimmedName,
                teacher: teacher,
                credit: creditVal,
                color: resolvedColor,
                notes: notes,
                schedules: [sCopy]
            )
            payload.courses.append(newCourse)
            courseDesc = "已成功创建并添加新课程「\(trimmedName)」：周\(dayOfWeek) 第\(startSec)-\(endSec)节，地点：\(classroom.isEmpty ? "未指定教室" : classroom)，教师：\(teacher ?? "未指定")，学分：\(creditVal != nil ? "\(creditVal!)分" : "未指定")，周次：\(startWeek)-\(endWeek)周。"
        }

        try database.save(payload)
        notifyAppAndWidgets(payload: payload)

        return courseDesc + " ClassFlow 课表已实时刷新更新。"
    }

    // MARK: - 4. Update Course

    public func updateCourse(arguments: [String: Any]) throws -> String {
        let nameQuery = (arguments["name"] ?? arguments["course_id"] ?? arguments["id"]) as? String
        guard let name = nameQuery, !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少必填参数：要修改的课程名称 (name)"])
        }

        var payload = database.load()
        let trimmedName = name.trimmingCharacters(in: .whitespaces).lowercased()

        let idx = try uniqueIndex(payload.courses, query: trimmedName, id: { $0.id }, title: { $0.name })

        var course = payload.courses[idx]
        var changes: [String] = []

        if let newName = arguments["new_name"] as? String, !newName.trimmingCharacters(in: .whitespaces).isEmpty {
            course.name = newName.trimmingCharacters(in: .whitespaces)
            changes.append("名称改为「\(course.name)」")
        }
        if let teacher = arguments["teacher"] as? String {
            course.teacher = teacher
            for i in course.schedules.indices { course.schedules[i].teacher = teacher }
            changes.append("教师更新为「\(teacher)」")
        }
        if let credit = arguments["credit"] as? Double ?? (arguments["credit"] as? NSNumber)?.doubleValue {
            course.credit = credit
            changes.append("学分更新为 \(credit) 分")
        }
        if let colorInput = arguments["color"] as? String {
            let hex = resolveColorHex(colorInput)
            course.color = hex
            changes.append("卡片颜色更新为 \(hex)")
        }
        if let notes = arguments["notes"] as? String {
            course.notes = notes
            changes.append("备注更新为「\(notes)」")
        }
        if let classroom = arguments["classroom"] as? String {
            for i in course.schedules.indices {
                course.schedules[i].classroom = classroom
            }
            changes.append("上课教室统一更新为「\(classroom)」")
        }

        try updateCourseSchedules(&course, arguments: arguments, payload: payload)
        if ["day_of_week", "start_section", "end_section", "start_week", "end_week", "week_pattern", "custom_weeks"].contains(where: { arguments[$0] != nil }) {
            changes.append("课时安排已更新")
        }
        payload.courses[idx] = course
        try database.save(payload)
        notifyAppAndWidgets(payload: payload)

        return "已成功更新课程「\(course.name)」：" + changes.joined(separator: "，") + "。ClassFlow 已同步变动。"
    }

    // MARK: - 5. Delete Course

    public func deleteCourse(arguments: [String: Any]) throws -> String {
        let nameQuery = (arguments["name"] ?? arguments["course_id"] ?? arguments["id"]) as? String
        guard let name = nameQuery, !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少必填参数：课程名称 (name)"])
        }

        var payload = database.load()
        let trimmedName = name.trimmingCharacters(in: .whitespaces).lowercased()

        let course = payload.courses[try uniqueIndex(payload.courses, query: trimmedName, id: { $0.id }, title: { $0.name })]

        let scope = (arguments["scope"] as? String)?.lowercased() ?? "all"
        guard ["all", "全部", "this_only", "本次", "this_and_future", "本次及以后"].contains(scope) else { throw automationError("删除范围必须为 all、this_only 或 this_and_future。") }

        if scope == "all" || scope == "全部" {
            payload.courses.removeAll { $0.id == course.id }
            payload.tombstones.append(SyncTombstone(entityId: course.id.uuidString, entityType: "course"))
            for s in course.schedules {
                payload.tombstones.append(SyncTombstone(entityId: s.id.uuidString, entityType: "schedule"))
            }
            try database.save(payload)
            notifyAppAndWidgets(payload: payload)
            return "已彻底删除课程「\(course.name)」及其全部课时安排。"
        } else if scope == "this_and_future" || scope == "本次及以后" {
            let semester = payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first
            let curWeek = arguments["week"] as? Int ?? (semester != nil ? calculateCurrentWeek(for: semester!) : 1)
            guard (1...(semester?.totalWeeks ?? 30)).contains(curWeek) else {
                throw automationError("删除起始周次超出学期范围。")
            }
            var updatedCourse = course
            var remainingSchedules: [CourseSchedule] = []

            for s in course.schedules {
                let existingWeeks = s.weekPattern.customWeeks ?? Array(s.weekPattern.startWeek...s.weekPattern.endWeek)
                let newWeeks = existingWeeks.filter { $0 < curWeek }
                if !newWeeks.isEmpty {
                    var copy = s
                    copy.weekPattern = .custom(newWeeks)
                    remainingSchedules.append(copy)
                } else {
                    payload.tombstones.append(SyncTombstone(entityId: s.id.uuidString, entityType: "schedule"))
                }
            }

            if remainingSchedules.isEmpty {
                payload.courses.removeAll { $0.id == course.id }
                payload.tombstones.append(SyncTombstone(entityId: course.id.uuidString, entityType: "course"))
            } else {
                updatedCourse.schedules = remainingSchedules
                if let idx = payload.courses.firstIndex(where: { $0.id == course.id }) {
                    payload.courses[idx] = updatedCourse
                }
            }

            try database.save(payload)
            notifyAppAndWidgets(payload: payload)
            return "已删除课程「\(course.name)」自第 \(curWeek) 周及以后的所有课时。"
        } else {
            // single cancellation
            let dateStr = arguments["date"] as? String ?? {
                let df = DateFormatter()
                df.dateFormat = "yyyy-MM-dd"
                return df.string(from: Date())
            }()
            try validateDate(dateStr)
            for s in course.schedules {
                let ex = ScheduleException(
                    id: UUID(),
                    scheduleId: s.id,
                    originalDate: dateStr,
                    exceptionType: .cancel,
                    reason: "单次删除"
                )
                payload.exceptions.append(ex)
            }
            try database.save(payload)
            notifyAppAndWidgets(payload: payload)
            return "已取消课程「\(course.name)」在 \(dateStr) 的单次课时。"
        }
    }

    // MARK: - 6. List Courses

    public func listCourses() -> [[String: Any]] {
        let payload = database.load()
        let semester = payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first
        let semCourses = payload.courses.filter { semester == nil || $0.semesterId == semester?.id }

        return semCourses.map { c -> [String: Any] in
            let schedSummaries = c.schedules.map { s -> [String: Any] in
                [
                    "id": s.id.uuidString,
                    "day_of_week": s.dayOfWeek,
                    "sections": "\(s.startSection)-\(s.endSection)",
                    "classroom": s.classroom ?? "",
                    "weeks": "\(s.weekPattern.startWeek)-\(s.weekPattern.endWeek)周"
                ]
            }
            return [
                "id": c.id.uuidString,
                "name": c.name,
                "teacher": c.teacher ?? "",
                "credit": c.credit as Any,
                "color": c.color,
                "notes": c.notes ?? "",
                "schedules_count": c.schedules.count,
                "schedules": schedSummaries
            ]
        }
    }

    // MARK: - 7. Calendar Events

    public func listCalendarEvents(date: String?) -> [[String: Any]] {
        let payload = database.load()
        var events = payload.calendarEvents
        if let d = date, !d.isEmpty {
            events = events.filter { $0.startDate?.hasPrefix(d) == true || $0.startTime.hasPrefix(d) }
        }

        return events.map { ev in
            [
                "id": ev.id.uuidString,
                "title": ev.title,
                "date": ev.startDate ?? String(ev.startTime.prefix(10)),
                "start_time": ev.startTime,
                "end_time": ev.endTime,
                "is_all_day": ev.isAllDay,
                "location": ev.location ?? "",
                "frequency": ev.frequency ?? "none",
                "color": ev.color,
                "notes": ev.description ?? ""
            ]
        }
    }

    public func addCalendarEvent(arguments: [String: Any]) throws -> String {
        guard let title = arguments["title"] as? String, !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少必填参数：日程标题 (title)"])
        }
        let dateStr = arguments["date"] as? String ?? {
            let df = DateFormatter()
            df.dateFormat = "yyyy-MM-dd"
            return df.string(from: Date())
        }()
        let startTime = arguments["start_time"] as? String ?? "09:00"
        let endTime = arguments["end_time"] as? String ?? "10:00"
        let isAllDay = arguments["is_all_day"] as? Bool ?? false
        try validateDate(dateStr)
        try validateTime(startTime)
        try validateTime(endTime)
        guard isAllDay || startTime < endTime else { throw automationError("结束时间必须晚于开始时间。") }
        let location = arguments["location"] as? String
        let freq = arguments["frequency"] as? String ?? "none"
        let color = resolveColorHex(arguments["color"] as? String)
        let notes = arguments["notes"] as? String

        var payload = database.load()
        let currentSem = payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first

        let newEvent = CalendarEvent(
            id: UUID(),
            calendarId: UUID(),
            semesterId: currentSem?.id,
            title: title.trimmingCharacters(in: .whitespaces),
            startTime: "\(dateStr)T\(startTime):00",
            endTime: "\(dateStr)T\(endTime):00",
            isAllDay: isAllDay,
            location: location,
            description: notes,
            color: color,
            frequency: freq,
            startDate: dateStr,
            endDate: dateStr
        )

        payload.calendarEvents.append(newEvent)
        try database.save(payload)
        notifyAppAndWidgets(payload: payload)

        return "已成功创建日程「\(newEvent.title)」：\(dateStr) \(isAllDay ? "全天" : "\(startTime) - \(endTime)")，地点：\(location ?? "无")。ClassFlow 已同步更新。"
    }

    public func deleteCalendarEvent(arguments: [String: Any]) throws -> String {
        let titleQuery = (arguments["title"] ?? arguments["event_id"] ?? arguments["id"]) as? String
        guard let title = titleQuery, !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少日程标题或ID (title / event_id)"])
        }

        var payload = database.load()
        let trimmedTitle = title.trimmingCharacters(in: .whitespaces).lowercased()

        let ev = payload.calendarEvents[try uniqueIndex(payload.calendarEvents, query: trimmedTitle, id: { $0.id }, title: { $0.title })]

        payload.calendarEvents.removeAll { $0.id == ev.id }
        payload.tombstones.append(SyncTombstone(entityId: ev.id.uuidString, entityType: "calendarEvent"))

        try database.save(payload)
        notifyAppAndWidgets(payload: payload)

        return "已成功删除日程「\(ev.title)」。"
    }

    // MARK: - 8. Tasks (Pomodoro ToDo)

    public func listTasks(filter: String?) -> [[String: Any]] {
        let payload = database.load()
        let normFilter = filter?.lowercased() ?? "in_progress"
        var tasks = payload.pomodoroTasks

        if normFilter == "pending" || normFilter == "in_progress" || normFilter == "进行中" {
            tasks = tasks.filter { !$0.isCompleted }
        } else if normFilter == "completed" || normFilter == "已完成" {
            tasks = tasks.filter { $0.isCompleted }
        }

        tasks.sort {
            if $0.isCompleted != $1.isCompleted {
                return !$0.isCompleted
            }
            return $0.priority.rawValue < $1.priority.rawValue
        }

        return tasks.map { t in
            [
                "id": t.id.uuidString,
                "title": t.title,
                "is_completed": t.isCompleted,
                "estimated_pomodoros": t.estimatedPomodoros,
                "completed_pomodoros": t.completedPomodoros,
                "priority": t.priority.rawValue,
                "due_date": t.dueDate ?? "",
                "notes": t.notes ?? "",
                "total_focus_minutes": t.totalFocusMinutes,
                "created_at": ISO8601DateFormatter().string(from: t.createdAt)
            ]
        }
    }

    public func addTask(arguments: [String: Any]) throws -> String {
        guard let title = arguments["title"] as? String, !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少任务标题 (title)"])
        }
        let pomos = arguments["estimated_pomodoros"] as? Int ?? 1
        guard (1...10000).contains(pomos) else { throw automationError("预估番茄数必须为 1–10000。") }
        if let due = arguments["due_date"] as? String { try validateDate(due) }
        var priority: TaskPriority = .medium
        if let pInt = arguments["priority"] as? Int {
            priority = (pInt >= 2) ? .high : (pInt == 1 ? .medium : .low)
        } else if let pStr = (arguments["priority"] as? String)?.lowercased() {
            if pStr.contains("high") || pStr.contains("高") || pStr.contains("急") {
                priority = .high
            } else if pStr.contains("low") || pStr.contains("低") {
                priority = .low
            } else {
                priority = .medium
            }
        }
        let dueDate = arguments["due_date"] as? String

        var payload = database.load()
        let currentSem = payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first

        let newTask = PomodoroTask(
            id: UUID(),
            semesterId: currentSem?.id,
            title: title.trimmingCharacters(in: .whitespaces),
            courseId: nil,
            estimatedPomodoros: pomos,
            completedPomodoros: 0,
            isCompleted: false,
            priority: priority,
            notes: arguments["notes"] as? String,
            dueDate: dueDate,
            createdAt: Date()
        )

        payload.pomodoroTasks.append(newTask)
        try database.save(payload)
        notifyAppAndWidgets(payload: payload)

        return "已成功添加待办任务「\(newTask.title)」：预计 \(pomos) 个番茄钟，优先级 \(priority.rawValue)，截止日期：\(dueDate ?? "无")。"
    }

    public func completeTask(arguments: [String: Any]) throws -> String {
        let titleQuery = (arguments["title"] ?? arguments["task_title"] ?? arguments["task_id"] ?? arguments["id"]) as? String
        guard let title = titleQuery, !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少任务标题或ID (title / task_id)"])
        }

        var payload = database.load()
        let trimmedTitle = title.trimmingCharacters(in: .whitespaces).lowercased()

        let idx = try uniqueIndex(payload.pomodoroTasks, query: trimmedTitle, id: { $0.id }, title: { $0.title })

        let isCompleted = arguments["is_completed"] as? Bool ?? true
        payload.pomodoroTasks[idx].isCompleted = isCompleted
        payload.pomodoroTasks[idx].completedAt = isCompleted ? Date() : nil
        try database.save(payload)
        notifyAppAndWidgets(payload: payload)

        return isCompleted ? "已将待办任务「\(payload.pomodoroTasks[idx].title)」标记为已完成 🎉。" : "已将待办任务「\(payload.pomodoroTasks[idx].title)」重新标记为进行中。"
    }

    public func deleteTask(arguments: [String: Any]) throws -> String {
        let titleQuery = (arguments["title"] ?? arguments["task_title"] ?? arguments["task_id"] ?? arguments["id"]) as? String
        guard let title = titleQuery, !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw NSError(domain: "ClassFlowMCP", code: 400, userInfo: [NSLocalizedDescriptionKey: "缺少任务标题或ID (title / task_id)"])
        }

        var payload = database.load()
        let trimmedTitle = title.trimmingCharacters(in: .whitespaces).lowercased()

        let t = payload.pomodoroTasks[try uniqueIndex(payload.pomodoroTasks, query: trimmedTitle, id: { $0.id }, title: { $0.title })]

        payload.pomodoroTasks.removeAll { $0.id == t.id }
        payload.tombstones.append(SyncTombstone(entityId: t.id.uuidString, entityType: "pomodoroTask"))

        try database.save(payload)
        notifyAppAndWidgets(payload: payload)

        return "已成功删除待办任务「\(t.title)」。"
    }

    // MARK: - 9. Focus Timer (番茄钟与专注学习)

    public func startFocusTimer(arguments: [String: Any]) throws -> String {
        var payload = database.load()
        guard payload.focusSession == nil else { throw automationError("已有专注会话，请先结束或暂停当前会话。") }
        let minutes = arguments["duration_minutes"] as? Int ?? 25
        guard (1...1440).contains(minutes) else { throw automationError("专注时长必须在 1–1440 分钟之间。") }
        var task: PomodoroTask?
        if let id = arguments["task_id"] as? String {
            let index = try uniqueIndex(payload.pomodoroTasks, query: id, id: { $0.id }, title: { $0.title })
            task = payload.pomodoroTasks[index]
        } else if let title = arguments["task_title"] as? String, !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let matches = payload.pomodoroTasks.filter { $0.title == title && !$0.isCompleted }
            guard matches.count <= 1 else { throw automationError("同名任务不唯一，请使用 task_id。") }
            if let existing = matches.first { task = existing }
            else {
                let created = PomodoroTask(semesterId: payload.semesters.first(where: { $0.isCurrent })?.id, title: title)
                payload.pomodoroTasks.append(created)
                task = created
            }
        }
        guard task?.isCompleted != true else { throw automationError("已完成的任务不能开始专注。") }
        payload.settings.selectedTab = "study"
        payload.settings.enableStudyModule = true
        payload.focusSession = FocusSession(taskId: task?.id, title: task?.title ?? "日常专注", durationMinutes: minutes, startedAt: Date())
        try database.save(payload)
        notifyAppAndWidgets(payload: payload)
        if enablesAppIntegration { _ = NSWorkspace.shared.open(URL(string: "classflow://reload")!) }
        return "已为任务「\(payload.focusSession!.title)」保存并启动 \(minutes) 分钟专注计时。"
    }

    public func pauseFocusTimer(resume: Bool) throws -> String {
        var payload = database.load()
        guard var session = payload.focusSession else { throw automationError("当前没有专注会话。") }
        if resume, session.pausedAt != nil {
            session.accumulatedSeconds = session.elapsedSeconds(at: Date())
            session.startedAt = Date()
            session.pausedAt = nil
        } else if !resume, session.pausedAt == nil { session.pausedAt = Date() }
        payload.focusSession = session
        try database.save(payload)
        notifyAppAndWidgets(payload: payload)
        return resume ? "专注计时已继续。" : "专注计时已暂停。"
    }

    public func stopFocusTimer(arguments: [String: Any]) throws -> String {
        var payload = database.load()
        guard let session = payload.focusSession else { throw automationError("当前没有专注会话，不会生成虚假记录。") }
        let minutes = session.elapsedSeconds(at: Date()) / 60
        // The recorded duration comes from the clock; legacy actual_minutes is deliberately ignored.
        if minutes > 0 {
            payload.pomodoroRecords.append(PomodoroRecord(id: session.id, taskId: session.taskId, taskTitle: session.title,
                courseName: nil, focusMode: session.mode, durationMinutes: minutes, completedAt: Date(), tag: "日常自习"))
            if let index = payload.pomodoroTasks.firstIndex(where: { $0.id == session.taskId }) {
                payload.pomodoroTasks[index].totalFocusMinutes += minutes
                payload.pomodoroTasks[index].completedPomodoros += minutes / 25
            }
        }
        payload.focusSession = nil
        try database.save(payload)
        notifyAppAndWidgets(payload: payload)
        return "已结束专注计时，实际专注 \(minutes) 分钟。"
    }

    public func getFocusStatistics() -> [String: Any] {
        let payload = database.load()
        let cal = Calendar.current
        let today = Date()

        let todayRecords = payload.pomodoroRecords.filter { cal.isDate($0.completedAt, inSameDayAs: today) }
        let todayMinutes = todayRecords.reduce(0) { $0 + $1.durationMinutes }
        let totalPomos = todayMinutes / 25

        let weekRecords = payload.pomodoroRecords.filter {
            cal.isDate($0.completedAt, equalTo: today, toGranularity: .weekOfYear)
        }
        let weekMinutes = weekRecords.reduce(0) { $0 + $1.durationMinutes }

        return [
            "today_focus_minutes": todayMinutes,
            "today_completed_pomodoros": totalPomos,
            "week_focus_minutes": weekMinutes,
            "total_records_count": payload.pomodoroRecords.count,
            "today_records": todayRecords.map { [
                "duration": $0.durationMinutes,
                "notes": $0.taskTitle,
                "time": DateFormatter.localizedString(from: $0.completedAt, dateStyle: .none, timeStyle: .short)
            ]}
        ]
    }

    // MARK: - 10. Cloud Sync

    public func triggerCloudSync() async throws -> String {
        var payload = database.load()
        let providerType = CloudSettings.defaults.string(forKey: "sync_provider_type") ?? "webdav"

        let provider: any StorageProvider
        if providerType == "webdav" {
            let serverURL = CloudSettings.defaults.string(forKey: "webdav_url") ?? ""
            let user = CloudSettings.defaults.string(forKey: "webdav_user") ?? ""
            let pwd = CloudSettings.secret("webdav_pwd") ?? ""
            let prefix = CloudSettings.defaults.string(forKey: "webdav_path_prefix") ?? "ClassFlow/"
            guard !serverURL.isEmpty, !user.isEmpty, !pwd.isEmpty else {
                throw automationError("未配置 WebDAV 凭据或服务器地址，请先在设置中完成配置。")
            }
            let config = WebDAVStorageProvider.Configuration(serverURL: serverURL, username: user, password: pwd, pathPrefix: prefix)
            provider = WebDAVStorageProvider(config: config)
        } else {
            let endpoint = CloudSettings.defaults.string(forKey: "s3_endpoint") ?? ""
            let region = CloudSettings.defaults.string(forKey: "s3_region") ?? ""
            let bucket = CloudSettings.defaults.string(forKey: "s3_bucket") ?? ""
            let ak = CloudSettings.secret("s3_access_key") ?? ""
            let sk = CloudSettings.secret("s3_secret_key") ?? ""
            let prefix = CloudSettings.defaults.string(forKey: "s3_path_prefix") ?? "classflow-sync/"
            guard !endpoint.isEmpty, !ak.isEmpty, !sk.isEmpty, !bucket.isEmpty else {
                throw automationError("未配置 S3 凭据或 Endpoint 服务地址，请先在设置中完成配置。")
            }
            let config = S3StorageProvider.Configuration(endpoint: endpoint, region: region, bucket: bucket, accessKey: ak, secretKey: sk, pathPrefix: prefix)
            provider = S3StorageProvider(config: config)
        }

        let devId = payload.syncDeviceId ?? ("dev_mac_" + UUID().uuidString.prefix(8).lowercased())
        let seq = payload.syncSequence ?? 0
        let syncEngine = SyncEngine(deviceId: devId, deviceName: Host.current().localizedName ?? "Apple Silicon Mac", initialSequence: seq)

        var localCourses = payload.courses
        var localExceptions = payload.exceptions
        var localSemesters = payload.semesters
        var localCalendarEvents = payload.calendarEvents
        var localPomodoroTasks = payload.pomodoroTasks
        var localPomodoroRecords = payload.pomodoroRecords
        var localProfiles = payload.timetableProfiles
        var localTombstones = payload.tombstones

        let summary = try await syncEngine.executeSync(
            storage: provider,
            courses: &localCourses,
            exceptions: &localExceptions,
            semesters: &localSemesters,
            calendarEvents: &localCalendarEvents,
            pomodoroTasks: &localPomodoroTasks,
            pomodoroRecords: &localPomodoroRecords,
            timetableProfiles: &localProfiles,
            tombstones: &localTombstones
        )

        payload.courses = SyncEngine.consolidateCourses(localCourses)
        payload.exceptions = localExceptions
        payload.semesters = localSemesters
        payload.calendarEvents = localCalendarEvents
        payload.pomodoroTasks = localPomodoroTasks
        payload.pomodoroRecords = localPomodoroRecords
        payload.timetableProfiles = localProfiles
        payload.tombstones = localTombstones
        payload.syncDeviceId = syncEngine.deviceId
        payload.syncSequence = syncEngine.latestSequence

        try database.save(payload)
        notifyAppAndWidgets(payload: payload)

        return "云端同步成功完成！同步摘要：本地课程 \(localCourses.count) 门，日程 \(localCalendarEvents.count) 项，待办 \(localPomodoroTasks.count) 条，推送 \(summary.uploadedOperationsCount) 项，合并拉取 \(summary.downloadedPeerOperationsCount) 项。"
    }

    // MARK: - 11. Navigation & App Control

    public func openApp(tab: String?) -> String {
        guard enablesAppIntegration else { return "已打开 ClassFlow。" }
        var deepLink = "classflow://"
        if let t = tab?.lowercased() {
            switch t {
            case "study", "focus", "学习", "专注":
                deepLink = "classflow://tab?name=study"
            case "schedule", "日程":
                deepLink = "classflow://tab?name=schedule"
            case "timetable", "课程", "课表":
                deepLink = "classflow://tab?name=timetable"
            case "settings", "设置":
                deepLink = "classflow://tab?name=settings"
            default:
                deepLink = "classflow://tab?name=\(t)"
            }
        }
        if let url = URL(string: deepLink) {
            NSWorkspace.shared.open(url)
        }
        DistributedNotificationCenter.default().postNotificationName(
            NSNotification.Name("org.classflow.commandTriggered"),
            object: nil,
            userInfo: ["url": deepLink],
            deliverImmediately: true
        )
        return "已唤醒 ClassFlow macOS 客户端并切换至指定视图。"
    }

    public func switchSemesterOrWeek(arguments: [String: Any]) throws -> String {
        var payload = database.load()
        var changes: [String] = []

        if let semName = arguments["semester_name"] as? String, !semName.isEmpty {
            let target = payload.semesters[try uniqueIndex(payload.semesters, query: semName, id: { $0.id }, title: { $0.name })]
            do {
                for i in payload.semesters.indices {
                    payload.semesters[i].isCurrent = (payload.semesters[i].id == target.id)
                }
                changes.append("切换当前学期为「\(target.name)」")
            }
        }

        if let week = arguments["week"] as? Int {
            let maxWeek = payload.semesters.first(where: { $0.isCurrent })?.totalWeeks ?? 30
            guard (1...maxWeek).contains(week) else { throw automationError("周次超出学期范围。") }
            changes.append("查看第 \(week) 周课表")
        }

        try database.save(payload)
        notifyAppAndWidgets(payload: payload)

        let deepLink = "classflow://week?number=\(arguments["week"] as? Int ?? 1)"
        if enablesAppIntegration, let url = URL(string: deepLink) {
            NSWorkspace.shared.open(url)
        }
        return "已更新学期/周次设置：" + changes.joined(separator: "，") + "。"
    }

    // MARK: - Helpers

    private func calculateCurrentWeek(for semester: Semester) -> Int {
        let cal = Calendar.current
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        guard let sDate = df.date(from: String(semester.startDate.prefix(10))) else { return 1 }
        let now = Date()
        if now < sDate { return 1 }
        let diff = cal.dateComponents([.day], from: sDate, to: now).day ?? 0
        let w = (diff / 7) + 1
        return min(max(1, w), semester.totalWeeks)
    }

    func resolveColorHex(_ color: String?) -> String {
        guard let c = color?.trimmingCharacters(in: .whitespaces).lowercased(), !c.isEmpty else {
            return "#4F46E5" // default indigo
        }
        if c.hasPrefix("#") && (c.count == 7 || c.count == 9) {
            return c
        }
        let presets: [String: String] = [
            "blue": "#3B82F6", "蓝": "#3B82F6", "蓝色": "#3B82F6",
            "purple": "#8B5CF6", "紫": "#8B5CF6", "紫色": "#8B5CF6",
            "green": "#10B981", "绿": "#10B981", "绿色": "#10B981",
            "emerald": "#059669", "翡翠绿": "#059669",
            "amber": "#D97706", "黄": "#D97706", "黄色": "#D97706", "琥珀色": "#D97706",
            "orange": "#F97316", "橙": "#F97316", "橙色": "#F97316",
            "red": "#EF4444", "红": "#EF4444", "红色": "#EF4444",
            "rose": "#F43F5E", "玫瑰红": "#F43F5E",
            "pink": "#EC4899", "粉": "#EC4899", "粉色": "#EC4899",
            "cyan": "#06B6D4", "青": "#06B6D4", "青色": "#06B6D4",
            "indigo": "#4F46E5", "靛蓝": "#4F46E5",
            "gray": "#6B7280", "灰": "#6B7280", "灰色": "#6B7280"
        ]
        return presets[c] ?? "#4F46E5"
    }

    private func projectCalendarEventOccurrences(
        _ event: CalendarEvent,
        weekDates: [String],
        semester: Semester,
        selectedWeek: Int
    ) -> [TimelineItem] {
        var results: [TimelineItem] = []
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        let normFreq = (event.frequency ?? "none").lowercased()

        if let weeks = event.customWeeks, !weeks.isEmpty, !weeks.contains(selectedWeek) {
            return []
        }

        for (dayIdx, dateStr) in weekDates.enumerated() {
            let dayOfWeek = dayIdx + 1
            if let sDate = event.startDate, !sDate.isEmpty, dateStr < String(sDate.prefix(10)) {
                continue
            }
            if normFreq == "none" {
                if let eDate = event.endDate, !eDate.isEmpty, dateStr > String(eDate.prefix(10)) {
                    continue
                }
            } else {
                if event.endRepeatType == "untilDate", let until = event.endRepeatUntil, !until.isEmpty, dateStr > String(until.prefix(10)) {
                    continue
                }
            }

            var shouldInclude = false
            switch normFreq {
            case "daily", "每天":
                shouldInclude = true
            case "workdays", "工作日":
                shouldInclude = (dayOfWeek <= 5)
            case "weekends", "周末":
                shouldInclude = (dayOfWeek == 6 || dayOfWeek == 7)
            case "weekly", "每周":
                if let days = event.customDaysOfWeek, !days.isEmpty {
                    shouldInclude = days.contains(dayOfWeek)
                } else {
                    let baseDateStr = event.startDate ?? String(event.startTime.prefix(10))
                    if let evDate = df.date(from: baseDateStr) {
                        let evW = Calendar.current.component(.weekday, from: evDate)
                        shouldInclude = ((evW == 1 ? 7 : evW - 1) == dayOfWeek)
                    } else {
                        shouldInclude = (dayOfWeek == 1)
                    }
                }
            default:
                if let sDate = event.startDate {
                    shouldInclude = (sDate == dateStr)
                } else {
                    shouldInclude = event.startTime.hasPrefix(dateStr)
                }
            }

            if !shouldInclude { continue }

            let rawStart = event.startTime.contains("T") ? String(event.startTime.split(separator: "T").last?.prefix(5) ?? "09:00") : "09:00"
            let rawEnd = event.endTime.contains("T") ? String(event.endTime.split(separator: "T").last?.prefix(5) ?? "10:00") : "10:00"

            results.append(
                TimelineItem(
                    id: "\(event.id.uuidString)_\(dateStr)",
                    sourceType: .calendarEvent,
                    courseId: nil,
                    scheduleId: nil,
                    calendarEventId: event.id,
                    title: event.title,
                    location: event.location,
                    date: dateStr,
                    dayOfWeek: dayOfWeek,
                    startSection: 1,
                    endSection: 2,
                    startTime: rawStart,
                    endTime: rawEnd,
                    weekNumber: selectedWeek,
                    isCurrentWeek: true,
                    status: .normal,
                    color: event.color,
                    teacher: nil,
                    notes: event.description,
                    credit: nil,
                    isAllDay: event.isAllDay,
                    hasConflict: false,
                    conflictingItemIds: [],
                    isRecurring: normFreq != "none"
                )
            )
        }
        return results
    }
}
