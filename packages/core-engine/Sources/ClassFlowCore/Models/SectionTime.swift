import Foundation

/// Represents the daily start and end times for a specific period/section (e.g. 第1节 08:00 - 08:45).
public struct SectionTime: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: Int { section }

    /// 1-indexed section number (1, 2, 3...)
    public var section: Int
    /// 24-hour start time in "HH:mm" format (e.g. "08:00")
    public var startTime: String
    /// 24-hour end time in "HH:mm" format (e.g. "08:45")
    public var endTime: String

    public init(section: Int, startTime: String, endTime: String) {
        precondition(section >= 0, "Section number must be non-negative (>= 0)")
        self.section = section
        self.startTime = startTime
        self.endTime = endTime
    }
}
