import Foundation

/// Represents a reusable timetable schedule profile (e.g. "默认", "夏季作息")
/// containing section times and duration preferences.
public struct TimetableProfile: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: UUID
    public var name: String
    public var sections: [SectionTime]
    public var fixedDuration: Bool
    public var sectionDurationMinutes: Int

    public init(
        id: UUID = UUID(),
        name: String,
        sections: [SectionTime],
        fixedDuration: Bool = true,
        sectionDurationMinutes: Int = 45
    ) {
        self.id = id
        self.name = name
        self.sections = sections
        self.fixedDuration = fixedDuration
        self.sectionDurationMinutes = sectionDurationMinutes
    }

    /// Default schedule matching WakeUp standard university periods.
    public static func standardDefault() -> TimetableProfile {
        let defaultSlots: [(start: (Int, Int), end: (Int, Int))] = [
            ((8, 30), (9, 15)),
            ((9, 20), (10, 5)),
            ((10, 15), (11, 0)),
            ((11, 5), (11, 50)),
            ((14, 30), (15, 15)),
            ((15, 20), (16, 5)),
            ((16, 15), (17, 0)),
            ((17, 5), (17, 50)),
            ((18, 30), (19, 15)),
            ((19, 20), (20, 5)),
            ((20, 15), (21, 0)),
            ((21, 5), (21, 50)),
            ((21, 35), (22, 20))
        ]
        let sections = defaultSlots.enumerated().map { index, slot in
            let sStr = String(format: "%02d:%02d", slot.start.0, slot.start.1)
            let eStr = String(format: "%02d:%02d", slot.end.0, slot.end.1)
            return SectionTime(section: index + 1, startTime: sStr, endTime: eStr)
        }
        return TimetableProfile(name: "默认", sections: sections, fixedDuration: true, sectionDurationMinutes: 45)
    }

    /// Alternative schedule starting at 8:00 AM.
    public static func earlyDefault() -> TimetableProfile {
        let defaultSlots: [(start: (Int, Int), end: (Int, Int))] = [
            ((8, 0), (8, 45)),
            ((8, 50), (9, 35)),
            ((10, 5), (10, 50)),
            ((10, 55), (11, 40)),
            ((14, 0), (14, 45)),
            ((14, 50), (15, 35)),
            ((15, 55), (16, 40)),
            ((16, 45), (17, 30)),
            ((18, 30), (19, 15)),
            ((19, 20), (20, 5)),
            ((20, 10), (20, 55)),
            ((21, 0), (21, 45))
        ]
        let sections = defaultSlots.enumerated().map { index, slot in
            let sStr = String(format: "%02d:%02d", slot.start.0, slot.start.1)
            let eStr = String(format: "%02d:%02d", slot.end.0, slot.end.1)
            return SectionTime(section: index + 1, startTime: sStr, endTime: eStr)
        }
        return TimetableProfile(name: "早读作息 (08:00 开始)", sections: sections, fixedDuration: true, sectionDurationMinutes: 45)
    }
}
