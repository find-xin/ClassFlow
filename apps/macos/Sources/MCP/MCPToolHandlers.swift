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

        weekItems.append(contentsOf: CalendarEventProjector().project(
            events: currentEvents, semester: current, week: currentWeek, exceptions: payload.exceptions))

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

        items.append(contentsOf: CalendarEventProjector().project(
            events: semEvents, semester: semester, week: targetWeek, exceptions: payload.exceptions))

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
        return try editCourse(arguments: arguments, creating: true)
    }

    // MARK: - 4. Update Course

    public func updateCourse(arguments: [String: Any]) throws -> String {
        return try editCourse(arguments: arguments)
    }

    // MARK: - 5. Delete Course

    public func deleteCourse(arguments: [String: Any]) throws -> String {
        return try deleteCompleteCourse(arguments: arguments)
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
                "credit": c.credit.map { $0 as Any } ?? NSNull(),
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
        return try editCalendar(arguments: arguments, creating: true)
    }

    public func deleteCalendarEvent(arguments: [String: Any]) throws -> String {
        return try editCalendar(arguments: arguments, deleting: true)
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
        return try editTask(arguments: arguments, creating: true)
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
        let wasCompleted = payload.pomodoroTasks[idx].isCompleted
        payload.pomodoroTasks[idx].isCompleted = isCompleted
        payload.pomodoroTasks[idx].completedAt = isCompleted ? Date() : nil
        if isCompleted && !wasCompleted, let semester = try? editSemester(payload.pomodoroTasks[idx].semesterId, payload: payload),
           let next = TaskRecurrence.next(after: payload.pomodoroTasks[idx], semester: semester), !payload.pomodoroTasks.contains(where: { $0.id == next.id }) { payload.pomodoroTasks.append(next) }
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
        return try startCompleteFocus(arguments: arguments)
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
                courseName: session.courseName, focusMode: session.mode, durationMinutes: minutes, completedAt: Date(), tag: session.courseName == nil ? "日常自习" : "课程专注"))
            if let index = payload.pomodoroTasks.firstIndex(where: { $0.id == session.taskId }) {
                payload.pomodoroTasks[index].totalFocusMinutes += minutes
                payload.pomodoroTasks[index].completedPomodoros += session.mode == .pomodoro ? minutes / 25 : 0
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
        let totalPomos = todayRecords.filter { $0.focusMode == .pomodoro }.reduce(0) { $0 + $1.durationMinutes / 25 }

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

}
