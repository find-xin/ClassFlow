import Foundation

/// Unified read-only projection representing a scheduled occurrence on the timetable canvas.
public struct TimelineItem: Codable, Sendable, Identifiable, Equatable, Hashable {
    public enum SourceType: String, Codable, Sendable {
        case course
        case calendarEvent
    }

    public enum OccurrenceStatus: String, Codable, Sendable {
        case normal
        case cancelled
        case rescheduled
        case makeup
    }

    /// Deterministic identifier (e.g. "course_occ_<scheduleId>_<date>")
    public var id: String
    /// Originating domain source
    public var sourceType: SourceType

    public var courseId: UUID?
    public var scheduleId: UUID?
    public var calendarEventId: UUID?

    /// Display title (e.g. course name or event title)
    public var title: String
    /// Classroom location or venue
    public var location: String?
    /// Occurrence date string ("YYYY-MM-DD")
    public var date: String
    /// 1 = Monday ... 7 = Sunday
    public var dayOfWeek: Int
    /// 1-indexed start period
    public var startSection: Int
    /// 1-indexed end period
    public var endSection: Int
    /// Formatted start time (e.g. "08:00")
    public var startTime: String?
    /// Formatted end time (e.g. "09:35")
    public var endTime: String?
    /// Semester teaching week number (1-indexed)
    public var weekNumber: Int
    /// Whether this occurrence belongs to the actively selected week
    public var isCurrentWeek: Bool
    /// Occurrence lifecycle status
    public var status: OccurrenceStatus
    /// Visual theme hex color
    public var color: String
    /// Instructor or speaker name
    public var teacher: String?
    /// Additional remarks
    public var notes: String?
    /// Whether this occurrence is an all-day event
    public var isAllDay: Bool
    /// Flag indicating overlap with another occurrence
    public var hasConflict: Bool
    /// IDs of overlapping items for conflict inspection
    public var conflictingItemIds: [String]

    public init(
        id: String,
        sourceType: SourceType = .course,
        courseId: UUID? = nil,
        scheduleId: UUID? = nil,
        calendarEventId: UUID? = nil,
        title: String,
        location: String? = nil,
        date: String,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        startTime: String? = nil,
        endTime: String? = nil,
        weekNumber: Int,
        isCurrentWeek: Bool = true,
        status: OccurrenceStatus = .normal,
        color: String = "#4F46E5",
        teacher: String? = nil,
        notes: String? = nil,
        isAllDay: Bool = false,
        hasConflict: Bool = false,
        conflictingItemIds: [String] = []
    ) {
        self.id = id
        self.sourceType = sourceType
        self.courseId = courseId
        self.scheduleId = scheduleId
        self.calendarEventId = calendarEventId
        self.title = title
        self.location = location
        self.date = date
        self.dayOfWeek = dayOfWeek
        self.startSection = startSection
        self.endSection = endSection
        self.startTime = startTime
        self.endTime = endTime
        self.weekNumber = weekNumber
        self.isCurrentWeek = isCurrentWeek
        self.status = status
        self.color = color
        self.teacher = teacher
        self.notes = notes
        self.isAllDay = isAllDay
        self.hasConflict = hasConflict
        self.conflictingItemIds = conflictingItemIds
    }

    /// Number of periods spanned by this occurrence.
    public var sectionSpan: Int {
        max(1, endSection - startSection + 1)
    }
}
