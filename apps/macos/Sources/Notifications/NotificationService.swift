import Foundation
import UserNotifications
import ClassFlowCore

public struct NotificationRuntimeStatus: Codable, Sendable {
    public var authorization: Int
    public var pendingCount: Int
    public var calendarAlertCount: Int
    public var nextCalendarAlerts: [String]
    public var plannedCalendarAlertCount: Int?
    public var acceptedCalendarAlertCount: Int?
}

/// Protocol abstraction for system notification providers.
public protocol NotificationServiceProtocol: Sendable {
    func checkAccess() async -> Bool
    func requestAccess() async throws -> Bool
    func schedulePreClassAlerts(occurrences: [TimelineItem], offsetMinutes: Int) async
    func scheduleDailyBriefing(todayOccurrences: [TimelineItem], hour: Int, minute: Int) async
    func scheduleCalendarAlerts(events: [CalendarEvent], semesters: [Semester], exceptions: [ScheduleException]) async
    func cancelClassAlerts() async
    func cancelAll() async
    func sendDiagnosticAlert() async -> Bool
    func diagnosticStatus() async -> NotificationRuntimeStatus?
    func clearDeliveredAlerts() async
}

public extension NotificationServiceProtocol {
    func sendDiagnosticAlert() async -> Bool { false }
    func diagnosticStatus() async -> NotificationRuntimeStatus? { nil }
    func clearDeliveredAlerts() async {}
}

/// Native macOS service managing local scheduled notifications for pre-class warnings and daily briefings.
public final class NotificationService: @unchecked Sendable, NotificationServiceProtocol {
    private var center: UNUserNotificationCenter? {
        // UNUserNotificationCenter requires an active CFBundleIdentifier; return nil in test runners/CLI
        guard Bundle.main.bundleIdentifier != nil else { return nil }
        return UNUserNotificationCenter.current()
    }

    private let planLock = NSLock()
    private var lastCalendarPlan: (planned: Int, accepted: Int)?

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
        let activeItems = CourseNotificationPlan.eligible(occurrences)

        var count = 0
        let maxAllowedAlerts = 27 // Leave headroom for daily briefing

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
        let obsolete = pending.filter { $0.identifier.hasPrefix("classflow_event_") }.map { $0.identifier }
        center.removePendingNotificationRequests(withIdentifiers: obsolete)
        await waitForRemoval(obsolete, center: center)
        guard await checkAccess() else { return }
        let plans = CalendarAlertPlan.make(events: events, semesters: semesters, exceptions: exceptions, limit: 27)
        var accepted = 0
        for plan in plans {
            let content = UNMutableNotificationContent()
            content.title = "日程提醒：" + plan.title
            content.body = plan.location ?? "日程即将开始"
            content.sound = .default
            content.userInfo = ["type": "calendar_event"]
            let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: plan.fireDate)
            let request = UNNotificationRequest(identifier: plan.id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false))
            do { try await center.add(request); accepted += 1 }
            catch { NSLog("[NotificationService] Calendar reminder scheduling failed (%@)", String(describing: type(of: error))) }
        }
        planLock.withLock { lastCalendarPlan = (plans.count, accepted) }
    }

    /// Schedules a recurring or today morning briefing notification.
    public func scheduleDailyBriefing(todayOccurrences: [TimelineItem], hour: Int, minute: Int) async {
        guard let center = center else { return }
        let isAuthorized = await checkAccess()
        guard isAuthorized else { return }

        center.removePendingNotificationRequests(withIdentifiers: ["classflow_daily_briefing"])

        let pending = await center.pendingNotificationRequests()
        center.removePendingNotificationRequests(withIdentifiers: pending.filter { $0.identifier.hasPrefix("classflow_daily_briefing") }.map(\.identifier))
        await waitForRemoval(pending.filter { $0.identifier.hasPrefix("classflow_daily_briefing") }.map(\.identifier), center: center)
        for plan in CourseNotificationPlan.briefings(todayOccurrences, hour: hour, minute: minute) {
            let activeToday = plan.items

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

            let dateComponents = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: plan.date)
            let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: false)
            let request = UNNotificationRequest(
                identifier: plan.id,
                content: content,
                trigger: trigger
            )

            do {
                try await center.add(request)
            } catch {
                print("[NotificationService] Failed to schedule daily briefing: \(error)")
            }
        }
    }


    /// Cancels all scheduled pre-class alerts.
    public func cancelClassAlerts() async {
        guard let center = center else { return }
        let pending = await center.pendingNotificationRequests()
        let alertIds = pending.filter { $0.identifier.hasPrefix("classflow_alert_") }.map { $0.identifier }
        center.removePendingNotificationRequests(withIdentifiers: alertIds)
        await waitForRemoval(alertIds, center: center)
    }

    /// Cancels all notifications.
    public func cancelAll() async {
        guard let center = center else { return }
        // A full replacement also clears legacy requests that a prior build registered.
        // Let the daemon process removal before submitting the next snapshot.
        center.removeAllPendingNotificationRequests()
        for _ in 0..<20 {
            try? await Task.sleep(for: .milliseconds(50))
            if await center.pendingNotificationRequests().isEmpty { return }
        }
    }

    /// Uses a unique notification only; never inserts test events into the business database.
    public func sendDiagnosticAlert() async -> Bool {
        guard let center, await checkAccess() else { return false }
        let id = "classflow_diagnostic_" + UUID().uuidString
        let content = UNMutableNotificationContent()
        content.title = "ClassFlow 通知验收"
        content.body = "隔离测试通知，不修改课表数据"
        do {
            try await center.add(UNNotificationRequest(identifier: id, content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false)))
            try await Task.sleep(for: .seconds(9))
            let delivered = await center.deliveredNotifications().contains { $0.request.identifier == id }
            center.removePendingNotificationRequests(withIdentifiers: [id])
            center.removeDeliveredNotifications(withIdentifiers: [id])
            return delivered
        } catch {
            center.removePendingNotificationRequests(withIdentifiers: [id])
            return false
        }
    }

    private func waitForRemoval(_ ids: [String], center: UNUserNotificationCenter) async {
        let removed = Set(ids)
        guard !removed.isEmpty else { return }
        for _ in 0..<20 {
            if await center.pendingNotificationRequests().allSatisfy({ !removed.contains($0.identifier) }) { return }
            try? await Task.sleep(for: .milliseconds(25))
        }
    }

    public func diagnosticStatus() async -> NotificationRuntimeStatus? {
        guard let center else { return nil }
        let settings = await center.notificationSettings()
        let pending = await center.pendingNotificationRequests()
        let events = pending.filter { $0.identifier.hasPrefix("classflow_event_") }
        let dates = events.compactMap { ($0.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate() }.sorted()
        let lastPlan = planLock.withLock { lastCalendarPlan }
        return NotificationRuntimeStatus(authorization: settings.authorizationStatus.rawValue,
            pendingCount: pending.count, calendarAlertCount: events.count,
            nextCalendarAlerts: dates.prefix(5).map { ISO8601DateFormatter().string(from: $0) },
            plannedCalendarAlertCount: lastPlan?.planned, acceptedCalendarAlertCount: lastPlan?.accepted)
    }

    public func clearDeliveredAlerts() async {
        center?.removeAllDeliveredNotifications()
    }
}

/// In-memory mock notification service for unit testing in unbundled environments.
public final class MockNotificationService: @unchecked Sendable, NotificationServiceProtocol {
    public var isAuthorized: Bool
    public var scheduledPreClassAlertsCount: Int = 0
    public var dailyBriefingScheduled: Bool = false
    public var calendarAlertInput: [CalendarEvent] = []

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

    public func scheduleCalendarAlerts(events: [CalendarEvent], semesters: [Semester], exceptions: [ScheduleException]) async { calendarAlertInput = events }

    public func cancelClassAlerts() async {
        scheduledPreClassAlertsCount = 0
    }

    public func cancelAll() async {
        scheduledPreClassAlertsCount = 0
        dailyBriefingScheduled = false
    }
}
