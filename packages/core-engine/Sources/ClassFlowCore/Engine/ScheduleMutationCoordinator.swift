import Foundation

/// Coordinates course schedule modifications across different temporal blast-radii (EditingScope).
public struct ScheduleMutationCoordinator: Sendable {
    public init() {}

    public struct MutationResult: Sendable, Equatable {
        public var updatedCourse: Course
        public var newExceptions: [ScheduleException]

        public init(updatedCourse: Course, newExceptions: [ScheduleException]) {
            self.updatedCourse = updatedCourse
            self.newExceptions = newExceptions
        }
    }

    /// Mutates a course schedule according to the user-selected EditingScope.
    public func applyScheduleMutation(
        course: Course,
        scheduleId: UUID,
        scope: EditingScope,
        currentWeek: Int,
        totalWeeks: Int,
        occurrenceDate: String,
        updatedDayOfWeek: Int,
        updatedStartSection: Int,
        updatedEndSection: Int,
        updatedClassroom: String?
    ) -> MutationResult {
        var mutableCourse = course
        var exceptions: [ScheduleException] = []

        guard let scheduleIndex = mutableCourse.schedules.firstIndex(where: { $0.id == scheduleId }) else {
            return MutationResult(updatedCourse: course, newExceptions: [])
        }

        let originalSchedule = mutableCourse.schedules[scheduleIndex]

        switch scope {
        case .thisOnly:
            // Does not touch CourseSchedule; emits a ScheduleException
            let isDayOrTimeChanged = (originalSchedule.dayOfWeek != updatedDayOfWeek) ||
                                     (originalSchedule.startSection != updatedStartSection) ||
                                     (originalSchedule.endSection != updatedEndSection)
            let isRoomChanged = (originalSchedule.classroom != updatedClassroom)

            let exceptionType: ScheduleException.ExceptionType
            if isDayOrTimeChanged {
                exceptionType = .reschedule
            } else if isRoomChanged {
                exceptionType = .room_change
            } else {
                exceptionType = .reschedule
            }

            let ex = ScheduleException(
                id: UUID(),
                scheduleId: originalSchedule.id,
                originalDate: occurrenceDate,
                exceptionType: exceptionType,
                targetDate: occurrenceDate,
                targetDayOfWeek: updatedDayOfWeek,
                targetStartSection: updatedStartSection,
                targetEndSection: updatedEndSection,
                targetClassroom: updatedClassroom,
                reason: "单次课调课"
            )
            exceptions.append(ex)

        case .thisAndFuture:
            // Truncate original schedule to weeks prior to currentWeek
            let priorWeeks = (1..<currentWeek).filter {
                originalSchedule.weekPattern.isActive(inWeek: $0, totalWeeks: totalWeeks)
            }

            if priorWeeks.isEmpty {
                // If currentWeek is week 1, thisAndFuture effectively updates the whole schedule
                mutableCourse.schedules[scheduleIndex].dayOfWeek = updatedDayOfWeek
                mutableCourse.schedules[scheduleIndex].startSection = updatedStartSection
                mutableCourse.schedules[scheduleIndex].endSection = updatedEndSection
                mutableCourse.schedules[scheduleIndex].classroom = updatedClassroom
            } else {
                // Truncate prior rule
                mutableCourse.schedules[scheduleIndex].weekPattern = .custom(Array(priorWeeks))

                // Create new successor rule from currentWeek through totalWeeks
                let futureWeeks = (currentWeek...totalWeeks).filter {
                    originalSchedule.weekPattern.isActive(inWeek: $0, totalWeeks: totalWeeks)
                }
                let newSchedule = CourseSchedule(
                    id: UUID(),
                    courseId: course.id,
                    dayOfWeek: updatedDayOfWeek,
                    startSection: updatedStartSection,
                    endSection: updatedEndSection,
                    classroom: updatedClassroom,
                    weekPattern: .custom(Array(futureWeeks))
                )
                mutableCourse.schedules.append(newSchedule)
            }

        case .all:
            // Update the underlying schedule directly
            mutableCourse.schedules[scheduleIndex].dayOfWeek = updatedDayOfWeek
            mutableCourse.schedules[scheduleIndex].startSection = updatedStartSection
            mutableCourse.schedules[scheduleIndex].endSection = updatedEndSection
            mutableCourse.schedules[scheduleIndex].classroom = updatedClassroom
        }

        return MutationResult(updatedCourse: mutableCourse, newExceptions: exceptions)
    }

    /// Cancels a course session under the specified scope.
    public func cancelSchedule(
        course: Course,
        scheduleId: UUID,
        scope: EditingScope,
        currentWeek: Int,
        totalWeeks: Int,
        occurrenceDate: String
    ) -> MutationResult {
        var mutableCourse = course
        var exceptions: [ScheduleException] = []

        guard let scheduleIndex = mutableCourse.schedules.firstIndex(where: { $0.id == scheduleId }) else {
            return MutationResult(updatedCourse: course, newExceptions: [])
        }

        let originalSchedule = mutableCourse.schedules[scheduleIndex]

        switch scope {
        case .thisOnly:
            let ex = ScheduleException(
                id: UUID(),
                scheduleId: originalSchedule.id,
                originalDate: occurrenceDate,
                exceptionType: .cancel,
                reason: "单次停课"
            )
            exceptions.append(ex)

        case .thisAndFuture:
            let priorWeeks = (1..<currentWeek).filter {
                originalSchedule.weekPattern.isActive(inWeek: $0, totalWeeks: totalWeeks)
            }
            if priorWeeks.isEmpty {
                // Delete schedule entirely
                mutableCourse.schedules.remove(at: scheduleIndex)
            } else {
                mutableCourse.schedules[scheduleIndex].weekPattern = .custom(Array(priorWeeks))
            }

        case .all:
            mutableCourse.schedules.remove(at: scheduleIndex)
        }

        return MutationResult(updatedCourse: mutableCourse, newExceptions: exceptions)
    }
}
