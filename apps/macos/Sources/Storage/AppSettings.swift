import Foundation

/// User preferences governing timetable presentation, calendar sync, notifications, and UI behavior.
public struct AppSettings: Codable, Sendable, Equatable {
    public var showWeekends: Bool
    public var showNonCurrentWeekCourses: Bool
    public var dimNonCurrentWeekCourses: Bool
    public var cardOpacity: Double
    public var customBackgroundPath: String?
    public var enableMenuBarExtra: Bool
    public var themeMode: String // "system", "light", "dark"

    // Calendar Integration Settings
    public var calendarName: String
    public var calendarReminderMinutes: Int?
    public var calendarIncludeNotes: Bool
    public var lastCalendarSyncTime: String?

    // Notification Settings
    public var enableClassAlerts: Bool
    public var classAlertMinutes: Int
    public var enableDailyBriefing: Bool
    public var dailyBriefingHour: Int
    public var dailyBriefingMinute: Int

    public init(
        showWeekends: Bool = true,
        showNonCurrentWeekCourses: Bool = true,
        dimNonCurrentWeekCourses: Bool = true,
        cardOpacity: Double = 0.88,
        customBackgroundPath: String? = nil,
        enableMenuBarExtra: Bool = true,
        themeMode: String = "system",
        calendarName: String = "ClassFlow 课程表",
        calendarReminderMinutes: Int? = 15,
        calendarIncludeNotes: Bool = true,
        lastCalendarSyncTime: String? = nil,
        enableClassAlerts: Bool = true,
        classAlertMinutes: Int = 15,
        enableDailyBriefing: Bool = true,
        dailyBriefingHour: Int = 7,
        dailyBriefingMinute: Int = 30
    ) {
        self.showWeekends = showWeekends
        self.showNonCurrentWeekCourses = showNonCurrentWeekCourses
        self.dimNonCurrentWeekCourses = dimNonCurrentWeekCourses
        self.cardOpacity = cardOpacity
        self.customBackgroundPath = customBackgroundPath
        self.enableMenuBarExtra = enableMenuBarExtra
        self.themeMode = themeMode
        self.calendarName = calendarName
        self.calendarReminderMinutes = calendarReminderMinutes
        self.calendarIncludeNotes = calendarIncludeNotes
        self.lastCalendarSyncTime = lastCalendarSyncTime
        self.enableClassAlerts = enableClassAlerts
        self.classAlertMinutes = classAlertMinutes
        self.enableDailyBriefing = enableDailyBriefing
        self.dailyBriefingHour = dailyBriefingHour
        self.dailyBriefingMinute = dailyBriefingMinute
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.showWeekends = try container.decodeIfPresent(Bool.self, forKey: .showWeekends) ?? true
        self.showNonCurrentWeekCourses = try container.decodeIfPresent(Bool.self, forKey: .showNonCurrentWeekCourses) ?? true
        self.dimNonCurrentWeekCourses = try container.decodeIfPresent(Bool.self, forKey: .dimNonCurrentWeekCourses) ?? true
        self.cardOpacity = try container.decodeIfPresent(Double.self, forKey: .cardOpacity) ?? 0.88
        self.customBackgroundPath = try container.decodeIfPresent(String.self, forKey: .customBackgroundPath)
        self.enableMenuBarExtra = try container.decodeIfPresent(Bool.self, forKey: .enableMenuBarExtra) ?? true
        self.themeMode = try container.decodeIfPresent(String.self, forKey: .themeMode) ?? "system"

        self.calendarName = try container.decodeIfPresent(String.self, forKey: .calendarName) ?? "ClassFlow 课程表"
        self.calendarReminderMinutes = try container.decodeIfPresent(Int.self, forKey: .calendarReminderMinutes) ?? 15
        self.calendarIncludeNotes = try container.decodeIfPresent(Bool.self, forKey: .calendarIncludeNotes) ?? true
        self.lastCalendarSyncTime = try container.decodeIfPresent(String.self, forKey: .lastCalendarSyncTime)

        self.enableClassAlerts = try container.decodeIfPresent(Bool.self, forKey: .enableClassAlerts) ?? true
        self.classAlertMinutes = try container.decodeIfPresent(Int.self, forKey: .classAlertMinutes) ?? 15
        self.enableDailyBriefing = try container.decodeIfPresent(Bool.self, forKey: .enableDailyBriefing) ?? true
        self.dailyBriefingHour = try container.decodeIfPresent(Int.self, forKey: .dailyBriefingHour) ?? 7
        self.dailyBriefingMinute = try container.decodeIfPresent(Int.self, forKey: .dailyBriefingMinute) ?? 30
    }
}
