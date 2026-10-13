import Foundation

/// User preferences governing timetable presentation and UI behavior.
public struct AppSettings: Codable, Sendable, Equatable {
    public var showWeekends: Bool
    public var showNonCurrentWeekCourses: Bool
    public var dimNonCurrentWeekCourses: Bool
    public var cardOpacity: Double
    public var customBackgroundPath: String?
    public var enableMenuBarExtra: Bool
    public var themeMode: String // "system", "light", "dark"

    public init(
        showWeekends: Bool = true,
        showNonCurrentWeekCourses: Bool = true,
        dimNonCurrentWeekCourses: Bool = true,
        cardOpacity: Double = 0.88,
        customBackgroundPath: String? = nil,
        enableMenuBarExtra: Bool = true,
        themeMode: String = "system"
    ) {
        self.showWeekends = showWeekends
        self.showNonCurrentWeekCourses = showNonCurrentWeekCourses
        self.dimNonCurrentWeekCourses = dimNonCurrentWeekCourses
        self.cardOpacity = cardOpacity
        self.customBackgroundPath = customBackgroundPath
        self.enableMenuBarExtra = enableMenuBarExtra
        self.themeMode = themeMode
    }
}
