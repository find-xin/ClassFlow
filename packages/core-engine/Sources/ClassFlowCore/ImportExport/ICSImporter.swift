import Foundation

/// Parsed calendar event representation extracted from an RFC 5545 iCalendar stream.
public struct ICSEvent: Sendable, Identifiable, Equatable {
    public var id: String { uid }
    public var uid: String
    public var summary: String
    public var location: String?
    public var description: String?
    public var dateString: String // "YYYY-MM-DD"
    public var startTime: String  // "HH:mm"
    public var endTime: String    // "HH:mm"
    public var dayOfWeek: Int     // 1..7
    public var recurrenceRule: String?

    public init(
        uid: String,
        summary: String,
        location: String? = nil,
        description: String? = nil,
        dateString: String,
        startTime: String,
        endTime: String,
        dayOfWeek: Int,
        recurrenceRule: String? = nil
    ) {
        self.uid = uid
        self.summary = summary
        self.location = location
        self.description = description
        self.dateString = dateString
        self.startTime = startTime
        self.endTime = endTime
        self.dayOfWeek = dayOfWeek
        self.recurrenceRule = recurrenceRule
    }
}

/// Parses iCalendar (.ics) files and converts them into timetable course candidates or calendar events.
public struct ICSImporter: Sendable {
    public let weekCalculator: WeekCalculator

    public init(timeZoneId: String = "Asia/Shanghai") {
        self.weekCalculator = WeekCalculator(timeZoneId: timeZoneId)
    }

    /// Parses an RFC 5545 iCalendar string into structured ICSEvent records.
    public func parseICS(content: String) -> [ICSEvent] {
        let unfoldedLines = unfoldLines(content)
        var events: [ICSEvent] = []

        var inEvent = false
        var currentUid = ""
        var currentSummary = ""
        var currentLocation: String? = nil
        var currentDescription: String? = nil
        var currentDTStart = ""
        var currentDTEnd = ""
        var currentRRule: String? = nil

        for line in unfoldedLines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed == "BEGIN:VEVENT" {
                inEvent = true
                currentUid = UUID().uuidString
                currentSummary = ""
                currentLocation = nil
                currentDescription = nil
                currentDTStart = ""
                currentDTEnd = ""
                currentRRule = nil
            } else if trimmed == "END:VEVENT" {
                if inEvent && !currentSummary.isEmpty && !currentDTStart.isEmpty {
                    if let (dStr, sTime, eTime, dOfWeek) = parseDateTimes(dtStart: currentDTStart, dtEnd: currentDTEnd) {
                        let event = ICSEvent(
                            uid: currentUid,
                            summary: currentSummary,
                            location: currentLocation,
                            description: currentDescription,
                            dateString: dStr,
                            startTime: sTime,
                            endTime: eTime,
                            dayOfWeek: dOfWeek,
                            recurrenceRule: currentRRule
                        )
                        events.append(event)
                    }
                }
                inEvent = false
            } else if inEvent {
                if trimmed.hasPrefix("UID:") {
                    currentUid = String(trimmed.dropFirst(4))
                } else if trimmed.hasPrefix("SUMMARY:") {
                    currentSummary = unescapeICS(String(trimmed.dropFirst(8)))
                } else if trimmed.hasPrefix("LOCATION:") {
                    currentLocation = unescapeICS(String(trimmed.dropFirst(9)))
                } else if trimmed.hasPrefix("DESCRIPTION:") {
                    currentDescription = unescapeICS(String(trimmed.dropFirst(12)))
                } else if trimmed.contains("DTSTART") {
                    if let value = trimmed.split(separator: ":").last {
                        currentDTStart = String(value)
                    }
                } else if trimmed.contains("DTEND") {
                    if let value = trimmed.split(separator: ":").last {
                        currentDTEnd = String(value)
                    }
                } else if trimmed.hasPrefix("RRULE:") {
                    currentRRule = String(trimmed.dropFirst(6))
                }
            }
        }

        return events
    }

    /// Converts parsed ICS events into Course candidates for a given target semester.
    public func convertToCourses(
        events: [ICSEvent],
        semester: Semester
    ) -> [Course] {
        // Group events by normalized (summary, dayOfWeek, startTime, endTime)
        let grouped = Dictionary(grouping: events) { ev in
            "\(ev.summary.trimmingCharacters(in: .whitespacesAndNewlines))_\(ev.dayOfWeek)_\(ev.startTime)_\(ev.endTime)"
        }

        var candidateCourses: [Course] = []
        let colors = ["#4F46E5", "#0D9488", "#0284C7", "#E11D48", "#D97706", "#059669", "#7C3AED"]
        var colorIdx = 0

        for (_, cluster) in grouped {
            guard let first = cluster.first else { continue }
            let courseId = UUID()

            // Derive active weeks from dates in this cluster
            let activeWeeks = cluster.compactMap { ev -> Int? in
                weekCalculator.weekIndex(for: ev.dateString, semesterStartDate: semester.startDate)
            }.filter { $0 >= 1 && $0 <= semester.totalWeeks }

            let uniqueWeeks = Array(Set(activeWeeks)).sorted()
            let pattern: WeekPattern
            if uniqueWeeks.count >= semester.totalWeeks - 2 {
                pattern = .all
            } else {
                pattern = .custom(uniqueWeeks)
            }

            // Approximate section numbers from section times
            let startSec = semester.sections.first(where: { $0.startTime == first.startTime })?.section ?? 1
            let endSec = semester.sections.first(where: { $0.endTime == first.endTime })?.section ?? startSec

            let schedule = CourseSchedule(
                id: UUID(),
                courseId: courseId,
                dayOfWeek: first.dayOfWeek,
                startSection: startSec,
                endSection: max(startSec, endSec),
                classroom: first.location,
                weekPattern: pattern
            )

            let course = Course(
                id: courseId,
                semesterId: semester.id,
                name: first.summary,
                teacher: extractTeacher(from: first.description),
                color: colors[colorIdx % colors.count],
                notes: first.description,
                schedules: [schedule]
            )
            candidateCourses.append(course)
            colorIdx += 1
        }

        return candidateCourses
    }

    // MARK: - Internal Helpers

    private func unfoldLines(_ content: String) -> [String] {
        var result: [String] = []
        let rawLines = content.components(separatedBy: .newlines)

        for line in rawLines {
            if line.hasPrefix(" ") || line.hasPrefix("\t") {
                if !result.isEmpty {
                    result[result.count - 1].append(String(line.dropFirst()))
                }
            } else {
                result.append(line)
            }
        }
        return result
    }

    private func parseDateTimes(dtStart: String, dtEnd: String) -> (String, String, String, Int)? {
        // e.g. "20261019T080000" or "20261019T080000Z"
        let cleanStart = dtStart.replacingOccurrences(of: "Z", with: "")
        let cleanEnd = dtEnd.replacingOccurrences(of: "Z", with: "")

        guard cleanStart.count >= 15 else { return nil }

        let datePart = String(cleanStart.prefix(8)) // "20261019"
        let year = datePart.prefix(4)
        let month = datePart.dropFirst(4).prefix(2)
        let day = datePart.dropFirst(6).prefix(2)
        let dateString = "\(year)-\(month)-\(day)"

        let startHour = cleanStart.dropFirst(9).prefix(2)
        let startMin = cleanStart.dropFirst(11).prefix(2)
        let startTime = "\(startHour):\(startMin)"

        let endHour = cleanEnd.count >= 15 ? cleanEnd.dropFirst(9).prefix(2) : startHour
        let endMin = cleanEnd.count >= 15 ? cleanEnd.dropFirst(11).prefix(2) : startMin
        let endTime = "\(endHour):\(endMin)"

        guard let parsedDate = weekCalculator.parseDate(dateString) else { return nil }
        let dOfWeek = weekCalculator.dayOfWeek(for: parsedDate)

        return (dateString, startTime, endTime, dOfWeek)
    }

    private func extractTeacher(from description: String?) -> String? {
        guard let description else { return nil }
        let lines = description.components(separatedBy: "\n")
        for line in lines {
            if line.hasPrefix("教师:") || line.hasPrefix("任课教师:") {
                let parts = line.split(separator: ":")
                if parts.count >= 2 {
                    return String(parts[1]).trimmingCharacters(in: .whitespaces)
                }
            }
        }
        return nil
    }

    private func unescapeICS(_ string: String) -> String {
        var str = string.replacingOccurrences(of: "\\n", with: "\n")
        str = str.replacingOccurrences(of: "\\,", with: ",")
        str = str.replacingOccurrences(of: "\\;", with: ";")
        str = str.replacingOccurrences(of: "\\\\", with: "\\")
        return str
    }
}
