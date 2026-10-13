import Foundation
import CryptoKit

/// Configuration options governing projection of timetable occurrences to system calendar events.
public struct CalendarSyncOptions: Codable, Sendable, Equatable {
    public var calendarName: String
    public var reminderMinutes: Int?
    public var includeTeacherAndNotes: Bool

    public init(
        calendarName: String = "ClassFlow 课程表",
        reminderMinutes: Int? = 15,
        includeTeacherAndNotes: Bool = true
    ) {
        self.calendarName = calendarName
        self.reminderMinutes = reminderMinutes
        self.includeTeacherAndNotes = includeTeacherAndNotes
    }
}

/// Projected event ready for insertion or comparison against OS Calendar store.
public struct CalendarEventCandidate: Sendable, Equatable {
    public var logicalKey: String
    public var semesterId: UUID
    public var courseId: UUID
    public var scheduleId: UUID
    public var occurrenceDate: String
    public var startSection: Int
    public var endSection: Int
    public var title: String
    public var location: String?
    public var notes: String?
    public var startDate: Date
    public var endDate: Date
    public var reminderOffsetMinutes: Int?
    public var contentHash: String

    public init(
        semesterId: UUID,
        courseId: UUID,
        scheduleId: UUID,
        occurrenceDate: String,
        startSection: Int,
        endSection: Int,
        title: String,
        location: String?,
        notes: String?,
        startDate: Date,
        endDate: Date,
        reminderOffsetMinutes: Int?,
        contentHash: String
    ) {
        self.logicalKey = "\(courseId.uuidString)_\(occurrenceDate)_\(startSection)_\(endSection)"
        self.semesterId = semesterId
        self.courseId = courseId
        self.scheduleId = scheduleId
        self.occurrenceDate = occurrenceDate
        self.startSection = startSection
        self.endSection = endSection
        self.title = title
        self.location = location
        self.notes = notes
        self.startDate = startDate
        self.endDate = endDate
        self.reminderOffsetMinutes = reminderOffsetMinutes
        self.contentHash = contentHash
    }
}

public struct CalendarEventUpdate: Sendable, Equatable {
    public var existingMapping: CalendarMapping
    public var candidate: CalendarEventCandidate

    public init(existingMapping: CalendarMapping, candidate: CalendarEventCandidate) {
        self.existingMapping = existingMapping
        self.candidate = candidate
    }
}

/// Calculated diff instructions for syncing timetable occurrences into platform calendar.
public struct CalendarSyncPlan: Sendable, Equatable {
    public var toCreate: [CalendarEventCandidate]
    public var toUpdate: [CalendarEventUpdate]
    public var toDelete: [CalendarMapping]
    public var unchangedCount: Int
    public var totalProcessed: Int

    public init(
        toCreate: [CalendarEventCandidate] = [],
        toUpdate: [CalendarEventUpdate] = [],
        toDelete: [CalendarMapping] = [],
        unchangedCount: Int = 0,
        totalProcessed: Int = 0
    ) {
        self.toCreate = toCreate
        self.toUpdate = toUpdate
        self.toDelete = toDelete
        self.unchangedCount = unchangedCount
        self.totalProcessed = totalProcessed
    }
}

/// Pure engine for computing surgical calendar synchronization diffs.
public struct CalendarSyncPlanner: Sendable {
    public init() {}

    /// Generates a deterministic synchronization plan comparing current occurrences against existing mappings.
    public func generatePlan(
        occurrences: [TimelineItem],
        semester: Semester,
        existingMappings: [CalendarMapping],
        options: CalendarSyncOptions = CalendarSyncOptions()
    ) -> CalendarSyncPlan {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd HH:mm"
        dateFormatter.timeZone = TimeZone.current

        // Map existing records by logical key
        var mappingDict: [String: CalendarMapping] = [:]
        for mapping in existingMappings where mapping.semesterId == semester.id {
            mappingDict[mapping.logicalKey] = mapping
        }

        var toCreate: [CalendarEventCandidate] = []
        var toUpdate: [CalendarEventUpdate] = []
        var matchedKeys = Set<String>()
        var unchangedCount = 0

        // Only process active, non-cancelled occurrences
        let activeItems = occurrences.filter { $0.status != .cancelled }

        for item in activeItems {
            let courseId = item.courseId ?? UUID()
            let scheduleId = item.scheduleId ?? UUID()
            let key = "\(courseId.uuidString)_\(item.date)_\(item.startSection)_\(item.endSection)"
            matchedKeys.insert(key)

            // Parse start and end date
            let startTimeStr = item.startTime ?? "08:00"
            let endTimeStr = item.endTime ?? "08:45"
            let startStr = "\(item.date) \(startTimeStr)"
            let endStr = "\(item.date) \(endTimeStr)"

            guard let startDate = dateFormatter.date(from: startStr),
                  let endDate = dateFormatter.date(from: endStr) else {
                continue
            }

            // Prepare notes
            var notesLines: [String] = []
            if options.includeTeacherAndNotes {
                if let teacher = item.teacher, !teacher.isEmpty {
                    notesLines.append("任课教师：\(teacher)")
                }
                notesLines.append("节次：第 \(item.startSection) - \(item.endSection) 节")
                notesLines.append("学期：\(semester.name)")
            }
            if let customNotes = item.notes, !customNotes.isEmpty {
                notesLines.append("备注：\(customNotes)")
            }
            let notes = notesLines.isEmpty ? nil : notesLines.joined(separator: "\n")

            // Compute deterministic SHA-256 content hash
            let rawContent = [
                item.title,
                item.location ?? "",
                notes ?? "",
                startStr,
                endStr,
                String(options.reminderMinutes ?? -1)
            ].joined(separator: "|")
            let contentHash = sha256Hex(rawContent)

            let candidate = CalendarEventCandidate(
                semesterId: semester.id,
                courseId: courseId,
                scheduleId: scheduleId,
                occurrenceDate: item.date,
                startSection: item.startSection,
                endSection: item.endSection,
                title: item.title,
                location: item.location,
                notes: notes,
                startDate: startDate,
                endDate: endDate,
                reminderOffsetMinutes: options.reminderMinutes,
                contentHash: contentHash
            )

            if let existing = mappingDict[key] {
                if existing.contentHash == contentHash {
                    unchangedCount += 1
                } else {
                    toUpdate.append(CalendarEventUpdate(existingMapping: existing, candidate: candidate))
                }
            } else {
                toCreate.append(candidate)
            }
        }

        // Any mapping that was not matched in the current active set should be deleted
        var toDelete: [CalendarMapping] = []
        for (key, mapping) in mappingDict {
            if !matchedKeys.contains(key) {
                toDelete.append(mapping)
            }
        }

        return CalendarSyncPlan(
            toCreate: toCreate,
            toUpdate: toUpdate,
            toDelete: toDelete,
            unchangedCount: unchangedCount,
            totalProcessed: activeItems.count
        )
    }

    private func sha256Hex(_ string: String) -> String {
        let data = Data(string.utf8)
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
