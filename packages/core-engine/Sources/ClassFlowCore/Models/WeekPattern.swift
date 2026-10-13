import Foundation

/// Recurrence rule defining in which weeks of the semester a class takes place.
public struct WeekPattern: Codable, Sendable, Equatable, Hashable {
    public enum PatternType: String, Codable, Sendable, CaseIterable {
        case all
        case odd
        case even
        case custom
    }

    public var type: PatternType
    /// Explicit week numbers (1-indexed) when type == .custom
    public var customWeeks: [Int]?

    public init(type: PatternType, customWeeks: [Int]? = nil) {
        self.type = type
        self.customWeeks = customWeeks?.sorted()
    }

    public static var all: WeekPattern {
        WeekPattern(type: .all)
    }

    public static var odd: WeekPattern {
        WeekPattern(type: .odd)
    }

    public static var even: WeekPattern {
        WeekPattern(type: .even)
    }

    public static func custom(_ weeks: [Int]) -> WeekPattern {
        WeekPattern(type: .custom, customWeeks: weeks)
    }

    /// Evaluates if this pattern is active for a given 1-indexed teaching week.
    public func isActive(inWeek weekNumber: Int, totalWeeks: Int = 30) -> Bool {
        guard weekNumber >= 1 && weekNumber <= totalWeeks else { return false }
        switch type {
        case .all:
            return true
        case .odd:
            return weekNumber % 2 == 1
        case .even:
            return weekNumber % 2 == 0
        case .custom:
            guard let customWeeks else { return false }
            return customWeeks.contains(weekNumber)
        }
    }
}
