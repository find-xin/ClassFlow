import Foundation

/// Defines the temporal blast-radius when editing or deleting a recurring course schedule.
public enum EditingScope: String, Codable, Sendable, CaseIterable {
    /// Applies changes only to the selected instance on a specific date (generates ScheduleException).
    case thisOnly
    /// Truncates prior occurrences and applies new rule starting from the selected week to the end of term.
    case thisAndFuture
    /// Applies modifications across the entire semester term.
    case all
}
