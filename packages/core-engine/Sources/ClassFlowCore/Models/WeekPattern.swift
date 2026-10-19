import Foundation

/// Recurrence rule defining in which weeks of the semester a class takes place,
/// supporting start and end week ranges as well as weekly/odd/even/custom patterns.
public struct WeekPattern: Codable, Sendable, Equatable, Hashable {
    public enum PatternType: String, Codable, Sendable, CaseIterable {
        case all
        case odd
        case even
        case custom
    }

    public var type: PatternType
    public var startWeek: Int
    public var endWeek: Int
    /// Explicit week numbers (1-indexed) when type == .custom
    public var customWeeks: [Int]?

    public init(
        type: PatternType = .all,
        startWeek: Int = 1,
        endWeek: Int = 30,
        customWeeks: [Int]? = nil
    ) {
        self.type = type
        self.startWeek = max(1, startWeek)
        self.endWeek = max(self.startWeek, endWeek)
        self.customWeeks = customWeeks?.sorted()
    }

    public init(from decoder: Decoder) throws {
        if let container = try? decoder.container(keyedBy: CodingKeys.self) {
            self.type = (try? container.decode(PatternType.self, forKey: .type)) ?? .all
            self.startWeek = (try? container.decodeIfPresent(Int.self, forKey: .startWeek)) ?? 1
            self.endWeek = (try? container.decodeIfPresent(Int.self, forKey: .endWeek)) ?? 30
            self.customWeeks = try? container.decodeIfPresent([Int].self, forKey: .customWeeks)
        } else if let single = try? decoder.singleValueContainer(), let str = try? single.decode(String.self) {
            let lower = str.lowercased()
            self.type = PatternType(rawValue: lower) ?? .all
            self.startWeek = 1
            self.endWeek = 30
            self.customWeeks = nil
        } else {
            self.type = .all
            self.startWeek = 1
            self.endWeek = 30
            self.customWeeks = nil
        }
    }

    public static var all: WeekPattern {
        WeekPattern(type: .all, startWeek: 1, endWeek: 30)
    }

    public static var odd: WeekPattern {
        WeekPattern(type: .odd, startWeek: 1, endWeek: 30)
    }

    public static var even: WeekPattern {
        WeekPattern(type: .even, startWeek: 1, endWeek: 30)
    }

    public static func range(start: Int, end: Int, type: PatternType = .all) -> WeekPattern {
        WeekPattern(type: type, startWeek: start, endWeek: end)
    }

    public static func custom(_ weeks: [Int]) -> WeekPattern {
        let sorted = weeks.sorted()
        let s = sorted.first ?? 1
        let e = sorted.last ?? 30
        return WeekPattern(type: .custom, startWeek: s, endWeek: e, customWeeks: sorted)
    }

    /// Evaluates if this pattern is active for a given 1-indexed teaching week.
    public func isActive(inWeek weekNumber: Int, totalWeeks: Int = 30) -> Bool {
        if type == .custom, let customWeeks {
            return customWeeks.contains(weekNumber) && weekNumber <= totalWeeks
        }
        guard weekNumber >= startWeek && weekNumber <= min(endWeek, totalWeeks) else { return false }
        switch type {
        case .all:
            return true
        case .odd:
            return weekNumber % 2 == 1
        case .even:
            return weekNumber % 2 == 0
        case .custom:
            return true
        }
    }

    /// User-friendly summary representation of the week schedule (e.g., "1-16周", "1-4周", "1-16周 (单周)")
    public var summary: String {
        switch type {
        case .all:
            return "\(startWeek)-\(endWeek)周"
        case .odd:
            return "\(startWeek)-\(endWeek)周 (单周)"
        case .even:
            return "\(startWeek)-\(endWeek)周 (双周)"
        case .custom:
            if let customWeeks, !customWeeks.isEmpty {
                let sorted = Array(Set(customWeeks)).sorted()
                var ranges: [String] = []
                var start = sorted[0]
                var prev = sorted[0]
                for i in 1..<sorted.count {
                    let curr = sorted[i]
                    if curr == prev + 1 {
                        prev = curr
                    } else {
                        ranges.append(start == prev ? "\(start)周" : "\(start)-\(prev)周")
                        start = curr
                        prev = curr
                    }
                }
                ranges.append(start == prev ? "\(start)周" : "\(start)-\(prev)周")
                return ranges.joined(separator: ", ")
            }
            return "\(startWeek)-\(endWeek)周"
        }
    }
}
