import Foundation
import ClassFlowCore

/// Lightweight serialized data model representing a course for widget display.
public struct WidgetCourseItem: Codable, Sendable, Identifiable, Equatable {
    public var id: String
    public var title: String
    public var location: String?
    public var teacher: String?
    public var startTime: String
    public var endTime: String
    public var startSection: Int
    public var endSection: Int
    public var colorHex: String
    public var isOngoing: Bool
    public var minutesUntilStart: Int?

    public init(
        id: String,
        title: String,
        location: String?,
        teacher: String?,
        startTime: String,
        endTime: String,
        startSection: Int,
        endSection: Int,
        colorHex: String,
        isOngoing: Bool = false,
        minutesUntilStart: Int? = nil
    ) {
        self.id = id
        self.title = title
        self.location = location
        self.teacher = teacher
        self.startTime = startTime
        self.endTime = endTime
        self.startSection = startSection
        self.endSection = endSection
        self.colorHex = colorHex
        self.isOngoing = isOngoing
        self.minutesUntilStart = minutesUntilStart
    }
}

/// Day bundle containing courses for week overview widgets.
public struct WidgetDaySchedule: Codable, Sendable, Identifiable, Equatable {
    public var id: Int // 1 (Mon) - 7 (Sun)
    public var dayName: String
    public var dateString: String
    public var courses: [WidgetCourseItem]

    public init(id: Int, dayName: String, dateString: String, courses: [WidgetCourseItem]) {
        self.id = id
        self.dayName = dayName
        self.dateString = dateString
        self.courses = courses
    }
}

/// Complete snapshot payload serialized for WidgetKit extensions.
public struct WidgetTimelinePayload: Codable, Sendable, Equatable {
    public var semesterName: String
    public var currentWeek: Int
    public var todayDateString: String
    public var nextClass: WidgetCourseItem?
    public var todayClasses: [WidgetCourseItem]
    public var weekSchedule: [WidgetDaySchedule]
    public var lastUpdated: String

    public init(
        semesterName: String = "ClassFlow",
        currentWeek: Int = 1,
        todayDateString: String = "",
        nextClass: WidgetCourseItem? = nil,
        todayClasses: [WidgetCourseItem] = [],
        weekSchedule: [WidgetDaySchedule] = [],
        lastUpdated: String = ISO8601DateFormatter().string(from: Date())
    ) {
        self.semesterName = semesterName
        self.currentWeek = currentWeek
        self.todayDateString = todayDateString
        self.nextClass = nextClass
        self.todayClasses = todayClasses
        self.weekSchedule = weekSchedule
        self.lastUpdated = lastUpdated
    }
}

/// Shared data manager handling disk synchronization between main app and widget extensions.
public struct WidgetDataStore: Sendable {
    public let fileURL: URL

    public init(customURL: URL? = nil) {
        if let customURL {
            self.fileURL = customURL
        } else {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            let dir = appSupport.appendingPathComponent("ClassFlow", isDirectory: true)
            self.fileURL = dir.appendingPathComponent("widget_data.json")
        }
    }

    public func save(_ payload: WidgetTimelinePayload) throws {
        let folder = fileURL.deletingLastPathComponent()
        if !FileManager.default.fileExists(atPath: folder.path) {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(payload)
        try data.write(to: fileURL, options: .atomic)
    }

    public func load() -> WidgetTimelinePayload {
        guard FileManager.default.fileExists(atPath: fileURL.path),
              let data = try? Data(contentsOf: fileURL),
              let payload = try? JSONDecoder().decode(WidgetTimelinePayload.self, from: data) else {
            return WidgetTimelinePayload()
        }
        return payload
    }
}
