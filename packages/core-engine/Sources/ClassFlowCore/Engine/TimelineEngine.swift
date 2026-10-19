import Foundation

/// Primary calculation engine that projects courses, recurring schedules, and exceptions
/// into a unified occurrence stream (TimelineItem) for timetable and agenda views.
public struct TimelineEngine: Sendable {
    public let weekCalculator: WeekCalculator
    public let conflictDetector: ConflictDetector

    public init(timeZoneId: String = "Asia/Shanghai") {
        self.weekCalculator = WeekCalculator(timeZoneId: timeZoneId)
        self.conflictDetector = ConflictDetector()
    }

    /// Generates all TimelineItem occurrences for a given teaching week.
    public func generateOccurrences(
        semester: Semester,
        courses: [Course],
        exceptions: [ScheduleException],
        forWeek weekNumber: Int,
        activeWeek: Int? = nil,
        showNonCurrentWeekCourses: Bool = true
    ) -> [TimelineItem] {
        guard weekNumber >= 1 && weekNumber <= semester.totalWeeks else { return [] }

        var items: [TimelineItem] = []

        // Exception lookups
        let exceptionByOrigDateAndSchedule = Dictionary(
            grouping: exceptions,
            by: { "\($0.scheduleId)_\($0.originalDate)" }
        )
        let exceptionByTargetDate = Dictionary(
            grouping: exceptions,
            by: { $0.targetDate ?? "" }
        )

        // Pre-compute the 7 date strings of the week once
        let weekDates = weekCalculator.dateStringsInWeek(weekNumber: weekNumber, semesterStartDate: semester.startDate)
        let datesInWeek = Set(weekDates)

        // 1. Process regular schedules
        for course in courses {
            for schedule in course.schedules {
                let isActiveInWeek = schedule.weekPattern.isActive(
                    inWeek: weekNumber,
                    totalWeeks: semester.totalWeeks
                )

                // If not active this week:
                if !isActiveInWeek {
                    if !showNonCurrentWeekCourses {
                        continue
                    }
                    // A course schedule that has been truncated or configured with custom weeks
                    // (e.g. truncated via thisAndFuture deletion) must not linger as a ghost card
                    // in weeks past its last active custom week.
                    if schedule.weekPattern.type == .custom,
                       let maxWeek = schedule.weekPattern.customWeeks?.max(),
                       weekNumber > maxWeek {
                        continue
                    }
                }

                let dayIdx = schedule.dayOfWeek - 1
                let dateStr: String
                if dayIdx >= 0 && dayIdx < weekDates.count {
                    dateStr = weekDates[dayIdx]
                } else {
                    dateStr = weekCalculator.dateString(
                        forWeek: weekNumber,
                        dayOfWeek: schedule.dayOfWeek,
                        semesterStartDate: semester.startDate
                    )
                }

                let startTime = schedule.customStartTime ?? semester.sectionTime(for: schedule.startSection)?.startTime
                let endTime = schedule.customEndTime ?? semester.sectionTime(for: schedule.endSection)?.endTime

                var item = TimelineItem(
                    id: "course_occ_\(schedule.id)_\(dateStr)",
                    sourceType: .course,
                    courseId: course.id,
                    scheduleId: schedule.id,
                    title: course.name,
                    location: schedule.classroom,
                    date: dateStr,
                    dayOfWeek: schedule.dayOfWeek,
                    startSection: schedule.startSection,
                    endSection: schedule.endSection,
                    startTime: startTime,
                    endTime: endTime,
                    weekNumber: weekNumber,
                    isCurrentWeek: isActiveInWeek,
                    status: .normal,
                    color: course.color,
                    teacher: schedule.teacher ?? course.teacher,
                    notes: course.notes,
                    isRecurring: true
                )

                // Apply exceptions matching this original occurrence
                let lookupKey = "\(schedule.id)_\(dateStr)"
                if let exList = exceptionByOrigDateAndSchedule[lookupKey], let ex = exList.first {
                    switch ex.exceptionType {
                    case .cancel:
                        if ex.reason == "单次删除" {
                            continue
                        }
                        item.status = .cancelled
                    case .reschedule:
                        // Original slot is marked cancelled / rescheduled away
                        item.status = .cancelled
                        if let reason = ex.reason {
                            item.notes = (item.notes.map { "\($0) (\(reason))" }) ?? reason
                        }
                    case .room_change:
                        if let newRoom = ex.targetClassroom {
                            item.location = newRoom
                        }
                    case .makeup:
                        break
                    }
                }

                items.append(item)
            }
        }

        // 2. Process rescheduled arrivals & makeup sessions targeting dates in this week
        let scheduleMap: [UUID: (Course, CourseSchedule)] = courses.reduce(into: [:]) { result, course in
            for s in course.schedules {
                result[s.id] = (course, s)
            }
        }

        for (targetDate, exList) in exceptionByTargetDate where datesInWeek.contains(targetDate) {
            for ex in exList {
                guard let (course, schedule) = scheduleMap[ex.scheduleId] else { continue }
                guard let targetDayOfWeek = ex.targetDayOfWeek,
                      let targetStart = ex.targetStartSection,
                      let targetEnd = ex.targetEndSection else { continue }

                let startTime = semester.sectionTime(for: targetStart)?.startTime
                let endTime = semester.sectionTime(for: targetEnd)?.endTime

                let targetItem = TimelineItem(
                    id: "course_occ_\(ex.id)_\(targetDate)",
                    sourceType: .course,
                    courseId: course.id,
                    scheduleId: schedule.id,
                    title: course.name,
                    location: ex.targetClassroom ?? schedule.classroom,
                    date: targetDate,
                    dayOfWeek: targetDayOfWeek,
                    startSection: targetStart,
                    endSection: targetEnd,
                    startTime: startTime,
                    endTime: endTime,
                    weekNumber: weekNumber,
                    isCurrentWeek: true,
                    status: ex.exceptionType == .makeup ? .makeup : .rescheduled,
                    color: course.color,
                    teacher: course.teacher,
                    notes: ex.reason ?? course.notes
                )
                items.append(targetItem)
            }
        }

        // 3. Annotate pairwise conflicts
        conflictDetector.annotateConflicts(in: &items)

        // 4. Stable sort: by date, startSection, then title
        items.sort {
            if $0.date != $1.date { return $0.date < $1.date }
            if $0.startSection != $1.startSection { return $0.startSection < $1.startSection }
            return $0.title < $1.title
        }

        return items
    }
}
