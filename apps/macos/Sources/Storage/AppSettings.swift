import Foundation

/// User preferences governing timetable presentation, calendar sync, notifications, and UI appearance.
public struct AppSettings: Codable, Sendable, Equatable {
    public var showWeekends: Bool
    public var showNonCurrentWeekCourses: Bool
    public var dimNonCurrentWeekCourses: Bool
    public var cardOpacity: Double
    public var customBackgroundPath: String?
    public var wallpaperPreset: String // "default", "aurora", "midnight", "warm_cream", "mint", "sky"
    public var isCompactMode: Bool
    public var selectedTab: String // "schedule", "timetable", "study", "profile"
    public var enableMenuBarExtra: Bool
    public var themeMode: String // "system", "light", "dark"
    public var enableStudyModule: Bool

    // Timetable Interface Settings (Images 3 & 4)
    public var usePureColorBackground: Bool
    public var headerColorHex: String
    public var showGridGuideLines: Bool
    public var showSectionTimeInAxis: Bool
    public var showSaturday: Bool
    public var showSunday: Bool

    // Course Card Grid Settings (Image 4)
    public var cardTextColorMode: String // "auto", "light", "dark"
    public var cardBorderColorMode: String // "none", "cardColor", "subtle"
    public var gridHeight: Double // 50 to 90
    public var cardCornerRadius: Double // 0 to 16
    public var nonCurrentWeekOpacityRatio: Double // 0.1 to 1.0
    public var cardFontSize: Double // 11 to 18
    public var showTimeInCard: Bool
    public var showLocationInCard: Bool
    public var showAtPrefixForRoom: Bool
    public var showTeacherInCard: Bool
    public var showNotesInCard: Bool
    public var cardTextHorizontalCenter: Bool
    public var cardTextVerticalCenter: Bool
    public var cardUseDottedBorder: Bool
    public var cardBorderUseCardColor: Bool
    public var cardTextBlendCardColor: Bool

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

    // Cloud Sync Settings (Remote Save Two-Step Control)
    public var syncOnStartup: Bool
    public var autoSyncInterval: String // "off", "15m", "30m", "1h", "2h", "6h"

    public init(
        showWeekends: Bool = true,
        showNonCurrentWeekCourses: Bool = true,
        dimNonCurrentWeekCourses: Bool = true,
        cardOpacity: Double = 0.88,
        customBackgroundPath: String? = nil,
        wallpaperPreset: String = "default",
        isCompactMode: Bool = false,
        selectedTab: String = "timetable",
        enableMenuBarExtra: Bool = true,
        themeMode: String = "system",
        enableStudyModule: Bool = true,
        usePureColorBackground: Bool = false,
        headerColorHex: String = "#1F1F1F",
        showGridGuideLines: Bool = false,
        showSectionTimeInAxis: Bool = true,
        showSaturday: Bool = true,
        showSunday: Bool = true,
        cardTextColorMode: String = "auto",
        cardBorderColorMode: String = "none",
        gridHeight: Double = 64.0,
        cardCornerRadius: Double = 6.0,
        nonCurrentWeekOpacityRatio: Double = 0.50,
        cardFontSize: Double = 13.0,
        showTimeInCard: Bool = false,
        showLocationInCard: Bool = true,
        showAtPrefixForRoom: Bool = true,
        showTeacherInCard: Bool = true,
        showNotesInCard: Bool = false,
        cardTextHorizontalCenter: Bool = false,
        cardTextVerticalCenter: Bool = false,
        cardUseDottedBorder: Bool = false,
        cardBorderUseCardColor: Bool = true,
        cardTextBlendCardColor: Bool = false,
        calendarName: String = "ClassFlow 课程表",
        calendarReminderMinutes: Int? = 15,
        calendarIncludeNotes: Bool = true,
        lastCalendarSyncTime: String? = nil,
        enableClassAlerts: Bool = true,
        classAlertMinutes: Int = 15,
        enableDailyBriefing: Bool = true,
        dailyBriefingHour: Int = 7,
        dailyBriefingMinute: Int = 30,
        syncOnStartup: Bool = false,
        autoSyncInterval: String = "off"
    ) {
        self.showWeekends = showWeekends
        self.showNonCurrentWeekCourses = showNonCurrentWeekCourses
        self.dimNonCurrentWeekCourses = dimNonCurrentWeekCourses
        self.cardOpacity = cardOpacity
        self.customBackgroundPath = customBackgroundPath
        self.wallpaperPreset = wallpaperPreset
        self.isCompactMode = isCompactMode
        self.selectedTab = selectedTab
        self.enableMenuBarExtra = enableMenuBarExtra
        self.themeMode = themeMode
        self.enableStudyModule = enableStudyModule
        self.usePureColorBackground = usePureColorBackground
        self.headerColorHex = headerColorHex
        self.showGridGuideLines = showGridGuideLines
        self.showSectionTimeInAxis = showSectionTimeInAxis
        self.showSaturday = showSaturday
        self.showSunday = showSunday
        self.cardTextColorMode = cardTextColorMode
        self.cardBorderColorMode = cardBorderColorMode
        self.gridHeight = gridHeight
        self.cardCornerRadius = cardCornerRadius
        self.nonCurrentWeekOpacityRatio = nonCurrentWeekOpacityRatio
        self.cardFontSize = cardFontSize
        self.showTimeInCard = showTimeInCard
        self.showLocationInCard = showLocationInCard
        self.showAtPrefixForRoom = showAtPrefixForRoom
        self.showTeacherInCard = showTeacherInCard
        self.showNotesInCard = showNotesInCard
        self.cardTextHorizontalCenter = cardTextHorizontalCenter
        self.cardTextVerticalCenter = cardTextVerticalCenter
        self.cardUseDottedBorder = cardUseDottedBorder
        self.cardBorderUseCardColor = cardBorderUseCardColor
        self.cardTextBlendCardColor = cardTextBlendCardColor
        self.calendarName = calendarName
        self.calendarReminderMinutes = calendarReminderMinutes
        self.calendarIncludeNotes = calendarIncludeNotes
        self.lastCalendarSyncTime = lastCalendarSyncTime
        self.enableClassAlerts = enableClassAlerts
        self.classAlertMinutes = classAlertMinutes
        self.enableDailyBriefing = enableDailyBriefing
        self.dailyBriefingHour = dailyBriefingHour
        self.dailyBriefingMinute = dailyBriefingMinute
        self.syncOnStartup = syncOnStartup
        self.autoSyncInterval = autoSyncInterval
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.showWeekends = try container.decodeIfPresent(Bool.self, forKey: .showWeekends) ?? true
        self.showNonCurrentWeekCourses = try container.decodeIfPresent(Bool.self, forKey: .showNonCurrentWeekCourses) ?? true
        self.dimNonCurrentWeekCourses = try container.decodeIfPresent(Bool.self, forKey: .dimNonCurrentWeekCourses) ?? true
        self.cardOpacity = try container.decodeIfPresent(Double.self, forKey: .cardOpacity) ?? 0.88
        self.customBackgroundPath = try container.decodeIfPresent(String.self, forKey: .customBackgroundPath)
        self.wallpaperPreset = try container.decodeIfPresent(String.self, forKey: .wallpaperPreset) ?? "default"
        self.isCompactMode = try container.decodeIfPresent(Bool.self, forKey: .isCompactMode) ?? false
        self.selectedTab = try container.decodeIfPresent(String.self, forKey: .selectedTab) ?? "timetable"
        self.enableMenuBarExtra = try container.decodeIfPresent(Bool.self, forKey: .enableMenuBarExtra) ?? true
        self.themeMode = try container.decodeIfPresent(String.self, forKey: .themeMode) ?? "system"
        self.enableStudyModule = try container.decodeIfPresent(Bool.self, forKey: .enableStudyModule) ?? true

        self.usePureColorBackground = try container.decodeIfPresent(Bool.self, forKey: .usePureColorBackground) ?? false
        self.headerColorHex = try container.decodeIfPresent(String.self, forKey: .headerColorHex) ?? "#1F1F1F"
        self.showGridGuideLines = try container.decodeIfPresent(Bool.self, forKey: .showGridGuideLines) ?? false
        self.showSectionTimeInAxis = try container.decodeIfPresent(Bool.self, forKey: .showSectionTimeInAxis) ?? true
        self.showSaturday = try container.decodeIfPresent(Bool.self, forKey: .showSaturday) ?? true
        self.showSunday = try container.decodeIfPresent(Bool.self, forKey: .showSunday) ?? true

        self.cardTextColorMode = try container.decodeIfPresent(String.self, forKey: .cardTextColorMode) ?? "auto"
        self.cardBorderColorMode = try container.decodeIfPresent(String.self, forKey: .cardBorderColorMode) ?? "none"
        self.gridHeight = try container.decodeIfPresent(Double.self, forKey: .gridHeight) ?? 64.0
        self.cardCornerRadius = try container.decodeIfPresent(Double.self, forKey: .cardCornerRadius) ?? 6.0
        self.nonCurrentWeekOpacityRatio = try container.decodeIfPresent(Double.self, forKey: .nonCurrentWeekOpacityRatio) ?? 0.50
        self.cardFontSize = try container.decodeIfPresent(Double.self, forKey: .cardFontSize) ?? 13.0
        self.showTimeInCard = try container.decodeIfPresent(Bool.self, forKey: .showTimeInCard) ?? false
        self.showLocationInCard = try container.decodeIfPresent(Bool.self, forKey: .showLocationInCard) ?? true
        self.showAtPrefixForRoom = try container.decodeIfPresent(Bool.self, forKey: .showAtPrefixForRoom) ?? true
        self.showTeacherInCard = try container.decodeIfPresent(Bool.self, forKey: .showTeacherInCard) ?? true
        self.showNotesInCard = try container.decodeIfPresent(Bool.self, forKey: .showNotesInCard) ?? false
        self.cardTextHorizontalCenter = try container.decodeIfPresent(Bool.self, forKey: .cardTextHorizontalCenter) ?? false
        self.cardTextVerticalCenter = try container.decodeIfPresent(Bool.self, forKey: .cardTextVerticalCenter) ?? false
        self.cardUseDottedBorder = try container.decodeIfPresent(Bool.self, forKey: .cardUseDottedBorder) ?? false
        self.cardBorderUseCardColor = try container.decodeIfPresent(Bool.self, forKey: .cardBorderUseCardColor) ?? true
        self.cardTextBlendCardColor = try container.decodeIfPresent(Bool.self, forKey: .cardTextBlendCardColor) ?? false

        self.calendarName = try container.decodeIfPresent(String.self, forKey: .calendarName) ?? "ClassFlow 课程表"
        self.calendarReminderMinutes = try container.decodeIfPresent(Int.self, forKey: .calendarReminderMinutes) ?? 15
        self.calendarIncludeNotes = try container.decodeIfPresent(Bool.self, forKey: .calendarIncludeNotes) ?? true
        self.lastCalendarSyncTime = try container.decodeIfPresent(String.self, forKey: .lastCalendarSyncTime)

        self.enableClassAlerts = try container.decodeIfPresent(Bool.self, forKey: .enableClassAlerts) ?? true
        self.classAlertMinutes = try container.decodeIfPresent(Int.self, forKey: .classAlertMinutes) ?? 15
        self.enableDailyBriefing = try container.decodeIfPresent(Bool.self, forKey: .enableDailyBriefing) ?? true
        self.dailyBriefingHour = try container.decodeIfPresent(Int.self, forKey: .dailyBriefingHour) ?? 7
        self.dailyBriefingMinute = try container.decodeIfPresent(Int.self, forKey: .dailyBriefingMinute) ?? 30
        self.syncOnStartup = try container.decodeIfPresent(Bool.self, forKey: .syncOnStartup) ?? false
        self.autoSyncInterval = try container.decodeIfPresent(String.self, forKey: .autoSyncInterval) ?? "off"
    }
}
