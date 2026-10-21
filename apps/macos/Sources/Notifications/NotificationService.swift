import Foundation
import UserNotifications
import ClassFlowCore

/// Protocol abstraction for system notification providers.
public protocol NotificationServiceProtocol: Sendable {
    func checkAccess() async -> Bool
    func requestAccess() async throws -> Bool
    func schedulePreClassAlerts(occurrences: [TimelineItem], offsetMinutes: Int) async
    func scheduleDailyBriefing(todayOccurrences: [TimelineItem], hour: Int, minute: Int) async
    func scheduleCalendarAlerts(events: [CalendarEvent], semesters: [Semester], exceptions: [ScheduleException]) async
    func cancelClassAlerts() async
    func cancelAll() async
}

/// Native macOS service managing local scheduled notifications for pre-class warnings and daily briefings.
public final class NotificationService: @unchecked Sendable, NotificationServiceProtocol {
    private var center: UNUserNotificationCenter? {
        // UNUserNotificationCenter requires an active CFBundleIdentifier; return nil in test runners/CLI
        guard Bundle.main.bundleIdentifier != nil else { return nil }
        return UNUserNotificationCenter.current()
    }

    public init() {}

    /// Checks whether notification permission has been granted.
    public func checkAccess() async -> Bool {
        guard let center = center else { return false }
        let settings = await center.notificationSettings()
        return settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
    }

    /// Requests notification permissions (alert, sound, badge).
    public func requestAccess() async throws -> Bool {
        guard let center = center else { return false }
        return try await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    /// Schedules pre-class reminders for upcoming classes within a 7-day window.
    /// macOS limits pending local notification requests to 64.
    public func schedulePreClassAlerts(occurrences: [TimelineItem], offsetMinutes: Int) async {
        guard offsetMinutes > 0, let center = center else {
            await cancelClassAlerts()
            return
        }

        let isAuthorized = await checkAccess()
        guard isAuthorized else { return }

        // Remove previous pre-class alerts before scheduling new batch
        await cancelClassAlerts()

        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd HH:mm"
        df.timeZone = TimeZone.current

        let now = Date()
        let activeItems = occurrences.filter { $0.status != .cancelled }

        var count = 0
        let maxAllowedAlerts = 30 // Leave headroom for daily briefing

        for item in activeItems {
            guard count < maxAllowedAlerts else { break }
            let startTimeStr = item.startTime ?? "08:00"
            guard let startDate = df.date(from: "\(item.date) \(startTimeStr)") else { continue }

            let triggerDate = startDate.addingTimeInterval(-Double(offsetMinutes * 60))
            guard triggerDate > now else { continue } // Only future classes

            let content = UNMutableNotificationContent()
            content.title = "即将上课：\(item.title)"
            if let room = item.location, !room.isEmpty {
                content.subtitle = "教室：\(room)"
            }
            var bodyParts: [String] = []
            if let teacher = item.teacher, !teacher.isEmpty {
                bodyParts.append("教师：\(teacher)")
            }
            bodyParts.append("时间：\(startTimeStr)（第 \(item.startSection)-\(item.endSection) 节）")
            content.body = bodyParts.joined(separator: " | ")
            content.sound = .default
            content.userInfo = [
                "type": "class_alert",
                "occurrenceId": item.id,
                "courseId": item.courseId?.uuidString ?? ""
            ]

            let components = Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute, .second],
                from: triggerDate
            )
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(
                identifier: "classflow_alert_\(item.id)",
                content: content,
                trigger: trigger
            )

            do {
                try await center.add(request)
                count += 1
            } catch {
                print("[NotificationService] Failed to schedule alert for \(item.title): \(error)")
            }
        }
    }

    public func scheduleCalendarAlerts(events: [CalendarEvent], semesters: [Semester], exceptions: [ScheduleException]) async {
        guard let center else { return }
        let pending = await center.pendingNotificationRequests()
        center.removePendingNotificationRequests(withIdentifiers: pending.filter { $0.identifier.hasPrefix("classflow_event_") }.map { $0.identifier })
        guard await checkAccess() else { return }
        for plan in CalendarAlertPlan.make(events: events, semesters: semesters, exceptions: exceptions) {
            let content = UNMutableNotificationContent()
            content.title = "日程提醒：" + plan.title
            content.body = plan.location ?? "日程即将开始"
            content.sound = .default
            content.userInfo = ["type": "calendar_event"]
            let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: plan.fireDate)
            let request = UNNotificationRequest(identifier: plan.id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false))
            do { try await center.add(request) }
            catch { NSLog("[NotificationService] Calendar reminder scheduling failed (%@)", String(describing: type(of: error))) }
        }
    }

    /// Schedules a recurring or today morning briefing notification.
    public func scheduleDailyBriefing(todayOccurrences: [TimelineItem], hour: Int, minute: Int) async {
        guard let center = center else { return }
        let isAuthorized = await checkAccess()
        guard isAuthorized else { return }

        center.removePendingNotificationRequests(withIdentifiers: ["classflow_daily_briefing"])

        let activeToday = todayOccurrences.filter { $0.status != .cancelled }.sorted {
            ($0.startTime ?? "00:00") < ($1.startTime ?? "00:00")
        }

        let content = UNMutableNotificationContent()
        content.title = "今日课表概览"
        if activeToday.isEmpty {
            content.body = "今天没有安排课程，好好休息吧！"
        } else {
            let first = activeToday[0]
            let roomInfo = first.location.map { "在 \($0)" } ?? ""
            content.body = "今日共有 \(activeToday.count) 节课。第一节《\(first.title)》\(roomInfo)（\(first.startTime ?? "") 开始）。"
        }
        content.sound = .default
        content.userInfo = ["type": "daily_briefing"]

        var dateComponents = DateComponents()
        dateComponents.hour = hour
        dateComponents.minute = minute

        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)
        let request = UNNotificationRequest(
            identifier: "classflow_daily_briefing",
            content: content,
            trigger: trigger
        )

        do {
            try await center.add(request)
        } catch {
            print("[NotificationService] Failed to schedule daily briefing: \(error)")
        }
    }

    /// Cancels all scheduled pre-class alerts.
    public func cancelClassAlerts() async {
        guard let center = center else { return }
        let pending = await center.pendingNotificationRequests()
        let alertIds = pending.filter { $0.identifier.hasPrefix("classflow_alert_") }.map { $0.identifier }
        center.removePendingNotificationRequests(withIdentifiers: alertIds)
    }

    /// Cancels all notifications.
    public func cancelAll() async {
        guard let center = center else { return }
        center.removeAllPendingNotificationRequests()
    }
}

/// In-memory mock notification service for unit testing in unbundled environments.
public final class MockNotificationService: @unchecked Sendable, NotificationServiceProtocol {
    public var isAuthorized: Bool
    public var scheduledPreClassAlertsCount: Int = 0
    public var dailyBriefingScheduled: Bool = false

    public init(isAuthorized: Bool = true) {
        self.isAuthorized = isAuthorized
    }

    public func checkAccess() async -> Bool { isAuthorized }
    public func requestAccess() async throws -> Bool { isAuthorized }

    public func schedulePreClassAlerts(occurrences: [TimelineItem], offsetMinutes: Int) async {
        scheduledPreClassAlertsCount = occurrences.filter { $0.status != .cancelled }.count
    }

    public func scheduleDailyBriefing(todayOccurrences: [TimelineItem], hour: Int, minute: Int) async {
        dailyBriefingScheduled = true
    }

    public func scheduleCalendarAlerts(events: [CalendarEvent], semesters: [Semester], exceptions: [ScheduleException]) async {}

    public func cancelClassAlerts() async {
        scheduledPreClassAlertsCount = 0
    }

    public func cancelAll() async {
        scheduledPreClassAlertsCount = 0
        dailyBriefingScheduled = false
    }
}
