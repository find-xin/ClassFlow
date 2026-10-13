import Foundation

/// Generates RFC 5545 compliant iCalendar (.ics) files from timetable occurrences.
public struct ICSExporter: Sendable {
    public let timelineEngine: TimelineEngine

    public init(timeZoneId: String = "Asia/Shanghai") {
        self.timelineEngine = TimelineEngine(timeZoneId: timeZoneId)
    }

    /// Exports an entire semester's course occurrences into an RFC 5545 iCalendar string.
    public func exportToICS(
        semester: Semester,
        courses: [Course],
        exceptions: [ScheduleException]
    ) -> String {
        var lines: [String] = []

        lines.append("BEGIN:VCALENDAR")
        lines.append("VERSION:2.0")
        lines.append("PRODID:-//ClassFlow//ClassFlow Timetable 1.0//CN")
        lines.append("CALSCALE:GREGORIAN")
        lines.append("METHOD:PUBLISH")
        lines.append("X-WR-CALNAME:\(escapeICS(semester.name))")
        lines.append("X-WR-TIMEZONE:\(semester.timeZone)")

        let dtstamp = formatICSDate(Date())

        // Iterate through all teaching weeks
        for weekNum in 1...semester.totalWeeks {
            let occurrences = timelineEngine.generateOccurrences(
                semester: semester,
                courses: courses,
                exceptions: exceptions,
                forWeek: weekNum
            )

            for occ in occurrences {
                // Skip cancelled occurrences or output CANCELLED status
                guard occ.status != .cancelled else { continue }
                guard let startTime = occ.startTime, let endTime = occ.endTime else { continue }

                lines.append("BEGIN:VEVENT")
                lines.append("UID:\(occ.id)@classflow.org")
                lines.append("DTSTAMP:\(dtstamp)")

                let dtStart = formatDateTime(date: occ.date, time: startTime)
                let dtEnd = formatDateTime(date: occ.date, time: endTime)
                lines.append("DTSTART;TZID=\(semester.timeZone):\(dtStart)")
                lines.append("DTEND;TZID=\(semester.timeZone):\(dtEnd)")

                lines.append("SUMMARY:\(escapeICS(occ.title))")

                if let loc = occ.location, !loc.isEmpty {
                    lines.append("LOCATION:\(escapeICS(loc))")
                }

                var descriptionParts: [String] = []
                if let teacher = occ.teacher, !teacher.isEmpty {
                    descriptionParts.append("教师: \(teacher)")
                }
                descriptionParts.append("周次: 第 \(occ.weekNumber) 周 (第 \(occ.startSection)-\(occ.endSection) 节)")
                if let notes = occ.notes, !notes.isEmpty {
                    descriptionParts.append("备注: \(notes)")
                }
                lines.append("DESCRIPTION:\(escapeICS(descriptionParts.joined(separator: "\\n")))")

                lines.append("CATEGORIES:课程,Education")
                lines.append("STATUS:CONFIRMED")
                lines.append("END:VEVENT")
            }
        }

        lines.append("END:VCALENDAR")
        return lines.joined(separator: "\r\n") + "\r\n"
    }

    private func formatDateTime(date: String, time: String) -> String {
        // "2026-10-19" + "08:00" -> "20261019T080000"
        let d = date.replacingOccurrences(of: "-", with: "")
        let t = time.replacingOccurrences(of: ":", with: "")
        return "\(d)T\(t)00"
    }

    private func formatICSDate(_ date: Date) -> String {
        let df = DateFormatter()
        df.dateFormat = "yyyyMMdd'T'HHmmss'Z'"
        df.timeZone = TimeZone(secondsFromGMT: 0)
        df.locale = Locale(identifier: "en_US_POSIX")
        return df.string(from: date)
    }

    private func escapeICS(_ string: String) -> String {
        var str = string.replacingOccurrences(of: "\\", with: "\\\\")
        str = str.replacingOccurrences(of: ";", with: "\\;")
        str = str.replacingOccurrences(of: ",", with: "\\,")
        str = str.replacingOccurrences(of: "\n", with: "\\n")
        return str
    }
}
