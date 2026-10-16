import Foundation
import SwiftUI
import WidgetKit
import Observation
import ClassFlowCore

@Observable
@MainActor
public final class AppState {
    public var semesters: [Semester] = []
    public var courses: [Course] = []
    public var exceptions: [ScheduleException] = []
    public var settings: AppSettings = AppSettings()
    public var calendarMappings: [CalendarMapping] = []

    public var currentSemesterId: UUID?
    public var selectedWeek: Int = 1

    // UI Presentation state
    public var selectedOccurrence: TimelineItem? = nil
    public var editingCourse: Course? = nil
    public var editingOccurrence: TimelineItem? = nil
    public var editingCourseInitialMode: CourseEditSheet.EditMode = .fullCourse
    public var isAddingCourse: Bool = false
    public var prefilledSlotForAdd: (dayOfWeek: Int, startSection: Int, endSection: Int)? = nil
    public var isAddingCalendarEvent: Bool = false
    public var prefilledSlotForEvent: (dayOfWeek: Int, startSection: Int, endSection: Int)? = nil
    public var isEditingOccurrence: Bool = false
    public var isShowingSettings: Bool = false
    public var isShowingImportPreview: Bool = false
    public var stagedImportPackage: ClassFlowPackage? = nil
    public var stagedImportPreview: ImportPreview? = nil
    public var importErrorMessage: String? = nil

    public var isShowingCreateSemester: Bool = false
    public var isShowingManageSemesters: Bool = false
    public var isShowingEditSectionTimes: Bool = false
    public var editingSemesterForSections: Semester? = nil
    public var isShowingSectionProfiles: Bool = false
    public var editingProfile: TimetableProfile? = nil
    public var isShowingAppearance: Bool = false
    public var isShowingWidgets: Bool = false
    public var isShowingCourseList: Bool = false

    public var timetableProfiles: [TimetableProfile] = []
    public var activeProfileId: UUID? = nil

    // Timetable Display Mode (Week vs Day View)
    public enum TimetableDisplayMode: String, CaseIterable, Identifiable {
        case week = "周"
        case day = "日"
        public var id: String { rawValue }
    }
    public var timetableDisplayMode: TimetableDisplayMode = .week
    public var selectedDayOfWeek: Int = {
        let weekday = Calendar.current.component(.weekday, from: Date())
        return weekday == 1 ? 7 : (weekday - 1)
    }()

    // Calendar Events (通用日程) & Scheduling State
    public var calendarEvents: [CalendarEvent] = []
    public var isShowingCreateScheduleItem: Bool = false
    public var selectedScheduleItemTab: String = "event" // "event" vs "task"

    // Pomodoro ToDo (番茄ToDo) State
    public var pomodoroTasks: [PomodoroTask] = []
    public var pomodoroRecords: [PomodoroRecord] = []
    public var activePomodoroTaskId: UUID? = nil
    public var isShowingNewTaskSheet: Bool = false

    private let database: LocalDatabase
    public let timelineEngine: TimelineEngine
    private let mutationCoordinator: ScheduleMutationCoordinator
    public let backupService: ClassFlowBackupService
    public let icsExporter: ICSExporter
    public let icsImporter: ICSImporter

    // Calendar, Notifications & Widgets Services
    public let calendarService: EventKitCalendarService
    public let calendarPlanner: CalendarSyncPlanner
    public let notificationService: any NotificationServiceProtocol
    public let widgetDataStore: WidgetDataStore

    public init(
        database: LocalDatabase = LocalDatabase(),
        calendarService: EventKitCalendarService = EventKitCalendarService(),
        notificationService: any NotificationServiceProtocol = NotificationService(),
        widgetDataStore: WidgetDataStore = WidgetDataStore()
    ) {
        self.database = database
        self.timelineEngine = TimelineEngine()
        self.mutationCoordinator = ScheduleMutationCoordinator()
        self.backupService = ClassFlowBackupService()
        self.icsExporter = ICSExporter()
        self.icsImporter = ICSImporter()
        self.calendarService = calendarService
        self.calendarPlanner = CalendarSyncPlanner()
        self.notificationService = notificationService
        self.widgetDataStore = widgetDataStore
        load()
    }

    public func load() {
        let payload = database.load()
        self.semesters = payload.semesters
        self.courses = payload.courses
        self.exceptions = payload.exceptions
        self.settings = payload.settings
        self.calendarMappings = payload.calendarMappings
        self.timetableProfiles = payload.timetableProfiles
        self.activeProfileId = payload.activeProfileId ?? timetableProfiles.first?.id
        self.pomodoroTasks = payload.pomodoroTasks
        self.pomodoroRecords = payload.pomodoroRecords
        self.calendarEvents = payload.calendarEvents
        if activePomodoroTaskId == nil {
            activePomodoroTaskId = pomodoroTasks.first(where: { !$0.isCompleted })?.id
        }

        if let current = semesters.first(where: { $0.isCurrent }) ?? semesters.first {
            self.currentSemesterId = current.id
            self.selectedWeek = calculateCurrentWeek(for: current)
        }
        updateWidgetData()
    }

    public func save() {
        let payload = LocalDatabase.DatabasePayload(
            semesters: semesters,
            courses: courses,
            exceptions: exceptions,
            settings: settings,
            calendarMappings: calendarMappings,
            timetableProfiles: timetableProfiles,
            activeProfileId: activeProfileId,
            pomodoroTasks: pomodoroTasks,
            pomodoroRecords: pomodoroRecords,
            calendarEvents: calendarEvents
        )
        do {
            try database.save(payload)
        } catch {
            print("[AppState] Error saving database: \(error)")
        }
        updateWidgetData()
        Task {
            await scheduleNotifications()
        }
    }

    // MARK: - Pomodoro ToDo Management (番茄ToDo)

    public func addPomodoroTask(_ task: PomodoroTask, setAsActive: Bool = true) {
        pomodoroTasks.insert(task, at: 0)
        if setAsActive || activePomodoroTaskId == nil {
            activePomodoroTaskId = task.id
        }
        save()
    }

    public func startFocusForCourse(_ item: TimelineItem) {
        selectedOccurrence = nil
        if let existing = pomodoroTasks.first(where: { !$0.isCompleted && ($0.courseName == item.title || $0.courseId == item.courseId) }) {
            activePomodoroTaskId = existing.id
        } else {
            let newTask = PomodoroTask(
                title: "\(item.title) 课前预习与复习",
                courseId: item.courseId,
                courseName: item.title,
                estimatedPomodoros: 2,
                priority: .medium
            )
            addPomodoroTask(newTask, setAsActive: true)
        }
        settings.selectedTab = "study"
    }

    public func startFocusForTask(_ task: PomodoroTask) {
        activePomodoroTaskId = task.id
        settings.selectedTab = "study"
    }

    public func updatePomodoroTask(_ task: PomodoroTask) {
        if let idx = pomodoroTasks.firstIndex(where: { $0.id == task.id }) {
            pomodoroTasks[idx] = task
            save()
        }
    }

    public func toggleTaskCompletion(id: UUID) {
        if let idx = pomodoroTasks.firstIndex(where: { $0.id == id }) {
            pomodoroTasks[idx].isCompleted.toggle()
            pomodoroTasks[idx].completedAt = pomodoroTasks[idx].isCompleted ? Date() : nil
            save()
        }
    }

    public func deletePomodoroTask(id: UUID) {
        pomodoroTasks.removeAll(where: { $0.id == id })
        if activePomodoroTaskId == id {
            activePomodoroTaskId = pomodoroTasks.first(where: { !$0.isCompleted })?.id
        }
        save()
    }

    public func recordCompletedPomodoro(
        taskId: UUID?,
        taskTitle: String,
        courseName: String?,
        focusMode: FocusMode,
        durationMinutes: Int,
        tag: String = "日常自习"
    ) {
        let record = PomodoroRecord(
            taskId: taskId,
            taskTitle: taskTitle,
            courseName: courseName,
            focusMode: focusMode,
            durationMinutes: durationMinutes,
            completedAt: Date(),
            tag: tag
        )
        pomodoroRecords.insert(record, at: 0)

        if let tId = taskId, let idx = pomodoroTasks.firstIndex(where: { $0.id == tId }) {
            pomodoroTasks[idx].completedPomodoros += 1
            pomodoroTasks[idx].totalFocusMinutes += durationMinutes
            if pomodoroTasks[idx].completedPomodoros >= pomodoroTasks[idx].estimatedPomodoros && !pomodoroTasks[idx].isCompleted {
                pomodoroTasks[idx].isCompleted = true
                pomodoroTasks[idx].completedAt = Date()
            }
        }
        save()
    }

    // MARK: - Timetable Profile Management (Images 1 & 2)

    public func selectTimetableProfile(id: UUID) {
        guard let profile = timetableProfiles.first(where: { $0.id == id }) else { return }
        self.activeProfileId = id
        if let currentSem = currentSemester {
            updateSectionTimes(semesterId: currentSem.id, sections: profile.sections)
        }
        save()
    }

    public func saveTimetableProfile(_ profile: TimetableProfile) {
        if let idx = timetableProfiles.firstIndex(where: { $0.id == profile.id }) {
            timetableProfiles[idx] = profile
        } else {
            timetableProfiles.append(profile)
        }
        if activeProfileId == profile.id || activeProfileId == nil {
            activeProfileId = profile.id
            if let currentSem = currentSemester {
                updateSectionTimes(semesterId: currentSem.id, sections: profile.sections)
            }
        }
        save()
    }

    public func deleteTimetableProfile(id: UUID) {
        guard timetableProfiles.count > 1 else { return }
        timetableProfiles.removeAll(where: { $0.id == id })
        if activeProfileId == id {
            activeProfileId = timetableProfiles.first?.id
            if let first = timetableProfiles.first, let currentSem = currentSemester {
                updateSectionTimes(semesterId: currentSem.id, sections: first.sections)
            }
        }
        save()
    }

    // MARK: - Computed Properties

    public var currentSemester: Semester? {
        guard let id = currentSemesterId else { return semesters.first }
        return semesters.first(where: { $0.id == id })
    }

    public var calculatedCurrentWeek: Int {
        guard let current = currentSemester else { return 1 }
        return calculateCurrentWeek(for: current)
    }

    public var isViewingCurrentWeek: Bool {
        selectedWeek == calculatedCurrentWeek
    }

    public var currentWeekOccurrences: [TimelineItem] {
        guard let current = currentSemester else { return [] }
        let currentCourses = courses.filter { $0.semesterId == current.id }
        var items = timelineEngine.generateOccurrences(
            semester: current,
            courses: currentCourses,
            exceptions: exceptions,
            forWeek: selectedWeek,
            activeWeek: calculatedCurrentWeek,
            showNonCurrentWeekCourses: settings.showNonCurrentWeekCourses
        )

        let weekDates = timelineEngine.weekCalculator.dateStringsInWeek(
            weekNumber: selectedWeek,
            semesterStartDate: current.startDate
        )

        for event in calendarEvents {
            let eventItems = projectCalendarEvent(event, weekDates: weekDates, semester: current)
            items.append(contentsOf: eventItems)
        }

        return items
    }

    private func projectCalendarEvent(_ event: CalendarEvent, weekDates: [String], semester: Semester) -> [TimelineItem] {
        var results: [TimelineItem] = []
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        let cal = Calendar.current

        // 1. Check week matrix filtering: if customWeeks is configured, must contain selectedWeek
        if let weeks = event.customWeeks, !weeks.isEmpty {
            if !weeks.contains(selectedWeek) {
                return []
            }
        }

        // 2. Check every N weeks interval
        if let interval = event.intervalWeeks, interval > 1 {
            let startWeek: Int
            if let startDStr = event.startDate ?? event.startTime.split(separator: "T").first.map(String.init) ?? event.startTime.split(separator: " ").first.map(String.init),
               let sDate = df.date(from: startDStr),
               let semDate = df.date(from: semester.startDate) {
                let diffDays = cal.dateComponents([.day], from: semDate, to: sDate).day ?? 0
                startWeek = max(1, (diffDays / 7) + 1)
            } else {
                startWeek = 1
            }
            if selectedWeek >= startWeek && ((selectedWeek - startWeek) % interval != 0) {
                return []
            }
        }

        for (dayIdx, dateStr) in weekDates.enumerated() {
            let dayOfWeek = dayIdx + 1 // 1 = Monday ... 7 = Sunday

            // 3. Check start and end date boundaries
            if let sDate = event.startDate, !sDate.isEmpty, dateStr < sDate {
                continue
            }
            if let eDate = event.endDate, !eDate.isEmpty, dateStr > eDate {
                continue
            }

            // 4. Check recurrence termination condition
            if event.endRepeatType == "untilDate", let until = event.endRepeatUntil, !until.isEmpty, dateStr > until {
                continue
            }

            var shouldInclude = false

            let freq = event.frequency ?? "none"
            switch freq {
            case "daily":
                shouldInclude = true
            case "workdays":
                shouldInclude = (dayOfWeek <= 5)
            case "weekly":
                if let days = event.customDaysOfWeek, !days.isEmpty {
                    shouldInclude = days.contains(dayOfWeek)
                } else if let eventDate = df.date(from: String(event.startTime.prefix(10))) {
                    let evWeekday = cal.component(.weekday, from: eventDate)
                    let evDayOfWeek = evWeekday == 1 ? 7 : (evWeekday - 1)
                    if evDayOfWeek == dayOfWeek {
                        shouldInclude = true
                    }
                } else {
                    shouldInclude = true
                }
            case "biweekly":
                if selectedWeek % 2 == 1 {
                    if let days = event.customDaysOfWeek, !days.isEmpty {
                        shouldInclude = days.contains(dayOfWeek)
                    } else if let eventDate = df.date(from: String(event.startTime.prefix(10))) {
                        let evWeekday = cal.component(.weekday, from: eventDate)
                        let evDayOfWeek = evWeekday == 1 ? 7 : (evWeekday - 1)
                        if evDayOfWeek == dayOfWeek {
                            shouldInclude = true
                        }
                    }
                }
            case "customWeeks":
                if let weeks = event.customWeeks, weeks.contains(selectedWeek) {
                    if let days = event.customDaysOfWeek, !days.isEmpty {
                        shouldInclude = days.contains(dayOfWeek)
                    } else if let eventDate = df.date(from: String(event.startTime.prefix(10))) {
                        let evWeekday = cal.component(.weekday, from: eventDate)
                        let evDayOfWeek = evWeekday == 1 ? 7 : (evWeekday - 1)
                        if evDayOfWeek == dayOfWeek {
                            shouldInclude = true
                        }
                    } else {
                        shouldInclude = true
                    }
                }
            case "customDays":
                if let days = event.customDaysOfWeek, days.contains(dayOfWeek) {
                    shouldInclude = true
                }
            case "monthly":
                if let eventDate = df.date(from: String(event.startTime.prefix(10))),
                   let curDate = df.date(from: dateStr) {
                    let evDay = cal.component(.day, from: eventDate)
                    let curDay = cal.component(.day, from: curDate)
                    if evDay == curDay {
                        shouldInclude = true
                    }
                }
            default: // "none"
                if event.startTime.hasPrefix(dateStr) {
                    shouldInclude = true
                }
            }

            if shouldInclude {
                let startSec = event.startSection ?? 1
                let endSec = event.endSection ?? startSec
                let timeOnlyStart: String
                if event.startTime.contains("T") {
                    timeOnlyStart = String(event.startTime.split(separator: "T").last?.prefix(5) ?? "")
                } else if event.startTime.contains(" ") {
                    timeOnlyStart = String(event.startTime.split(separator: " ").last?.prefix(5) ?? "")
                } else {
                    timeOnlyStart = event.startTime
                }

                let timeOnlyEnd: String
                if event.endTime.contains("T") {
                    timeOnlyEnd = String(event.endTime.split(separator: "T").last?.prefix(5) ?? "")
                } else if event.endTime.contains(" ") {
                    timeOnlyEnd = String(event.endTime.split(separator: " ").last?.prefix(5) ?? "")
                } else {
                    timeOnlyEnd = event.endTime
                }

                let item = TimelineItem(
                    id: "event_occ_\(event.id)_\(dateStr)",
                    sourceType: .calendarEvent,
                    calendarEventId: event.id,
                    title: event.title,
                    location: event.location,
                    date: dateStr,
                    dayOfWeek: dayOfWeek,
                    startSection: startSec,
                    endSection: endSec,
                    startTime: timeOnlyStart.isEmpty ? nil : timeOnlyStart,
                    endTime: timeOnlyEnd.isEmpty ? nil : timeOnlyEnd,
                    weekNumber: selectedWeek,
                    isCurrentWeek: true,
                    status: .normal,
                    color: event.color,
                    teacher: nil,
                    notes: event.description,
                    isAllDay: event.isAllDay
                )
                results.append(item)
            }
        }
        return results
    }

    // MARK: - Calendar Event CRUD

    public func addCalendarEvent(_ event: CalendarEvent) {
        calendarEvents.append(event)
        save()
    }

    public func updateCalendarEvent(_ event: CalendarEvent) {
        if let idx = calendarEvents.firstIndex(where: { $0.id == event.id }) {
            calendarEvents[idx] = event
            save()
        }
    }

    public func deleteCalendarEvent(id: UUID) {
        calendarEvents.removeAll(where: { $0.id == id })
        save()
    }

    public func calculateCurrentWeek(for semester: Semester) -> Int {
        let todayStr = timelineEngine.weekCalculator.formatDate(Date())
        let week = timelineEngine.weekCalculator.weekIndex(for: todayStr, semesterStartDate: semester.startDate) ?? 1
        return max(1, min(week, semester.totalWeeks))
    }

    // MARK: - Navigation

    public func nextWeek() {
        guard let semester = currentSemester else { return }
        if selectedWeek < semester.totalWeeks {
            selectedWeek += 1
        }
    }

    public func previousWeek() {
        if selectedWeek > 1 {
            selectedWeek -= 1
        }
    }

    public func jumpToCurrentWeek() {
        selectedWeek = calculatedCurrentWeek
    }

    public func selectPreviousDay() {
        if selectedDayOfWeek > 1 {
            selectedDayOfWeek -= 1
        } else {
            previousWeek()
            selectedDayOfWeek = 7
        }
    }

    public func selectNextDay() {
        if selectedDayOfWeek < 7 {
            selectedDayOfWeek += 1
        } else {
            nextWeek()
            selectedDayOfWeek = 1
        }
    }

    public func selectToday() {
        jumpToCurrentWeek()
        let weekday = Calendar.current.component(.weekday, from: Date())
        selectedDayOfWeek = weekday == 1 ? 7 : (weekday - 1)
    }

    public func selectSemester(id: UUID) {
        guard let semester = semesters.first(where: { $0.id == id }) else { return }
        self.currentSemesterId = id
        for i in 0..<semesters.count {
            semesters[i].isCurrent = (semesters[i].id == id)
        }
        self.selectedWeek = calculateCurrentWeek(for: semester)
        save()
    }

    // MARK: - Semester Management

    @discardableResult
    public func createSemester(
        name: String,
        startDate: String,
        totalWeeks: Int = 20,
        sections: [SectionTime]? = nil
    ) -> Semester {
        let defaultSections = sections ?? defaultSectionTimes()
        let cal = Calendar.current
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        let startD = df.date(from: startDate) ?? Date()
        let endD = cal.date(byAdding: .weekOfYear, value: totalWeeks, to: startD) ?? startD
        let endDateStr = df.string(from: endD)

        let newSemester = Semester(
            id: UUID(),
            name: name,
            startDate: startDate,
            endDate: endDateStr,
            totalWeeks: totalWeeks,
            isCurrent: true,
            sections: defaultSections
        )

        for i in 0..<semesters.count {
            semesters[i].isCurrent = false
        }
        semesters.append(newSemester)
        self.currentSemesterId = newSemester.id
        self.selectedWeek = calculateCurrentWeek(for: newSemester)
        save()
        return newSemester
    }

    public func updateSemester(_ semester: Semester) {
        if let idx = semesters.firstIndex(where: { $0.id == semester.id }) {
            semesters[idx] = semester
            save()
        }
    }

    public func deleteSemester(id: UUID) {
        guard semesters.count > 1 else { return }
        semesters.removeAll(where: { $0.id == id })
        courses.removeAll(where: { $0.semesterId == id })
        if currentSemesterId == id {
            if let first = semesters.first {
                selectSemester(id: first.id)
            }
        } else {
            save()
        }
    }

    public func updateSectionTimes(semesterId: UUID, sections: [SectionTime]) {
        if let idx = semesters.firstIndex(where: { $0.id == semesterId }) {
            semesters[idx].sections = sections.sorted(by: { $0.section < $1.section })
            save()
        }
    }

    public static func standardSectionTimes() -> [SectionTime] {
        let defaultSlots: [(start: (Int, Int), end: (Int, Int))] = [
            ((8, 30), (9, 15)),
            ((9, 20), (10, 05)),
            ((10, 15), (11, 00)),
            ((11, 05), (11, 50)),
            ((14, 30), (15, 15)),
            ((15, 20), (16, 05)),
            ((16, 15), (17, 00)),
            ((17, 05), (17, 50)),
            ((18, 30), (19, 15)),
            ((19, 20), (20, 05)),
            ((20, 15), (21, 00)),
            ((21, 05), (21, 50))
        ]
        return defaultSlots.enumerated().map { index, slot in
            let sStr = String(format: "%02d:%02d", slot.start.0, slot.start.1)
            let eStr = String(format: "%02d:%02d", slot.end.0, slot.end.1)
            return SectionTime(section: index + 1, startTime: sStr, endTime: eStr)
        }
    }

    public static func earlySectionTimes() -> [SectionTime] {
        let defaultSlots: [(start: (Int, Int), end: (Int, Int))] = [
            ((8, 00), (8, 45)),
            ((8, 50), (9, 35)),
            ((10, 05), (10, 50)),
            ((10, 55), (11, 40)),
            ((14, 00), (14, 45)),
            ((14, 50), (15, 35)),
            ((15, 55), (16, 40)),
            ((16, 45), (17, 30)),
            ((18, 30), (19, 15)),
            ((19, 20), (20, 05)),
            ((20, 10), (20, 55)),
            ((21, 00), (21, 45))
        ]
        return defaultSlots.enumerated().map { index, slot in
            let sStr = String(format: "%02d:%02d", slot.start.0, slot.start.1)
            let eStr = String(format: "%02d:%02d", slot.end.0, slot.end.1)
            return SectionTime(section: index + 1, startTime: sStr, endTime: eStr)
        }
    }

    public func defaultSectionTimes() -> [SectionTime] {
        Self.standardSectionTimes()
    }

    // MARK: - Course Management & Mutations

    public func startAddingCourse(dayOfWeek: Int? = nil, startSection: Int? = nil, endSection: Int? = nil) {
        if let day = dayOfWeek, let start = startSection {
            let maxSec = currentSemester?.sections.count ?? 12
            let end = endSection ?? min(start + 1, maxSec)
            self.prefilledSlotForAdd = (day, start, end)
        } else {
            self.prefilledSlotForAdd = nil
        }
        self.isAddingCourse = true
    }

    public func startAddingCalendarEvent(dayOfWeek: Int? = nil, section: Int? = nil) {
        if let day = dayOfWeek, let sec = section {
            self.prefilledSlotForEvent = (day, sec, sec)
        } else {
            self.prefilledSlotForEvent = nil
        }
        self.isAddingCalendarEvent = true
    }

    public func addCourse(_ course: Course) {
        courses.append(course)
        save()
    }

    public func updateCourse(_ course: Course) {
        if let idx = courses.firstIndex(where: { $0.id == course.id }) {
            courses[idx] = course
            save()
        }
    }

    public func deleteCourse(id: UUID) {
        courses.removeAll(where: { $0.id == id })
        save()
    }

    public func applyMutation(
        courseId: UUID,
        scheduleId: UUID,
        scope: EditingScope,
        occurrenceDate: String,
        updatedDayOfWeek: Int,
        updatedStartSection: Int,
        updatedEndSection: Int,
        updatedClassroom: String?
    ) {
        guard let course = courses.first(where: { $0.id == courseId }),
              let semester = currentSemester else { return }

        let result = mutationCoordinator.applyScheduleMutation(
            course: course,
            scheduleId: scheduleId,
            scope: scope,
            currentWeek: selectedWeek,
            totalWeeks: semester.totalWeeks,
            occurrenceDate: occurrenceDate,
            updatedDayOfWeek: updatedDayOfWeek,
            updatedStartSection: updatedStartSection,
            updatedEndSection: updatedEndSection,
            updatedClassroom: updatedClassroom
        )

        updateCourse(result.updatedCourse)
        exceptions.append(contentsOf: result.newExceptions)
        save()
    }

    public func cancelSchedule(
        courseId: UUID,
        scheduleId: UUID,
        scope: EditingScope,
        occurrenceDate: String
    ) {
        guard let course = courses.first(where: { $0.id == courseId }),
              let semester = currentSemester else { return }

        let result = mutationCoordinator.cancelSchedule(
            course: course,
            scheduleId: scheduleId,
            scope: scope,
            currentWeek: selectedWeek,
            totalWeeks: semester.totalWeeks,
            occurrenceDate: occurrenceDate
        )

        updateCourse(result.updatedCourse)
        exceptions.append(contentsOf: result.newExceptions)
        save()
    }

    public func restoreCancelledOccurrence(
        courseId: UUID,
        scheduleId: UUID,
        occurrenceDate: String
    ) {
        exceptions.removeAll { ex in
            ex.scheduleId == scheduleId && ex.originalDate == occurrenceDate && ex.exceptionType == .cancel
        }
        save()
    }

    // MARK: - Calendar Synchronization (EventKit)

    public func allSemesterOccurrences(for semester: Semester) -> [TimelineItem] {
        let semesterCourses = courses.filter { $0.semesterId == semester.id }
        var allItems: [TimelineItem] = []
        let currentWeek = calculatedCurrentWeek
        for week in 1...semester.totalWeeks {
            let weekItems = timelineEngine.generateOccurrences(
                semester: semester,
                courses: semesterCourses,
                exceptions: exceptions,
                forWeek: week,
                activeWeek: currentWeek,
                showNonCurrentWeekCourses: false
            )
            allItems.append(contentsOf: weekItems)
        }
        return allItems
    }

    public func syncToSystemCalendar() async throws -> CalendarSyncExecutionResult {
        guard let current = currentSemester else {
            throw NSError(domain: "ClassFlow", code: 400, userInfo: [NSLocalizedDescriptionKey: "未选择当前学期"])
        }

        let allOccurrences = allSemesterOccurrences(for: current)
        let options = CalendarSyncOptions(
            calendarName: settings.calendarName,
            reminderMinutes: settings.calendarReminderMinutes,
            includeTeacherAndNotes: settings.calendarIncludeNotes
        )

        let plan = calendarPlanner.generatePlan(
            occurrences: allOccurrences,
            semester: current,
            existingMappings: calendarMappings,
            options: options
        )

        let result = try await calendarService.executePlan(plan, inCalendarNamed: settings.calendarName)

        // Prune deleted mappings
        let deletedKeys = Set(plan.toDelete.map { $0.logicalKey })
        calendarMappings.removeAll(where: { deletedKeys.contains($0.logicalKey) })

        // Apply new/updated mappings
        let updatedDict = Dictionary(uniqueKeysWithValues: result.newMappings.map { ($0.logicalKey, $0) })
        for i in 0..<calendarMappings.count {
            if let updated = updatedDict[calendarMappings[i].logicalKey] {
                calendarMappings[i] = updated
            }
        }
        for newMapping in result.newMappings {
            if !calendarMappings.contains(where: { $0.logicalKey == newMapping.logicalKey }) {
                calendarMappings.append(newMapping)
            }
        }

        settings.lastCalendarSyncTime = ISO8601DateFormatter().string(from: Date())
        save()
        return result
    }

    public func clearSystemCalendar() async throws -> Int {
        let count = try calendarService.clearCalendar(named: settings.calendarName)
        calendarMappings.removeAll()
        settings.lastCalendarSyncTime = nil
        save()
        return count
    }

    // MARK: - Notifications

    public func scheduleNotifications() async {
        guard let current = currentSemester else { return }
        let currentWeek = calculatedCurrentWeek
        let weekOccurrences = timelineEngine.generateOccurrences(
            semester: current,
            courses: courses.filter { $0.semesterId == current.id },
            exceptions: exceptions,
            forWeek: currentWeek,
            activeWeek: currentWeek
        )

        if settings.enableClassAlerts {
            await notificationService.schedulePreClassAlerts(
                occurrences: weekOccurrences,
                offsetMinutes: settings.classAlertMinutes
            )
        } else {
            await notificationService.cancelClassAlerts()
        }

        let todayStr = timelineEngine.weekCalculator.formatDate(Date())
        let todayItems = weekOccurrences.filter { $0.date == todayStr }
        if settings.enableDailyBriefing {
            await notificationService.scheduleDailyBriefing(
                todayOccurrences: todayItems,
                hour: settings.dailyBriefingHour,
                minute: settings.dailyBriefingMinute
            )
        }
    }

    // MARK: - Desktop Widgets

    public func updateWidgetData() {
        guard let current = currentSemester else { return }
        let now = Date()
        let todayStr = timelineEngine.weekCalculator.formatDate(now)
        let weekNum = calculatedCurrentWeek

        let weekOccurrences = timelineEngine.generateOccurrences(
            semester: current,
            courses: courses.filter { $0.semesterId == current.id },
            exceptions: exceptions,
            forWeek: weekNum,
            activeWeek: weekNum
        )

        let dfTime = DateFormatter()
        dfTime.dateFormat = "HH:mm"
        dfTime.timeZone = TimeZone.current
        let currentTimeStr = dfTime.string(from: now)

        let todayItems = weekOccurrences.filter { $0.date == todayStr && $0.status != .cancelled }.sorted {
            ($0.startTime ?? "00:00") < ($1.startTime ?? "00:00")
        }

        let todayWidgetItems: [WidgetCourseItem] = todayItems.map { item in
            let start = item.startTime ?? "00:00"
            let end = item.endTime ?? "23:59"
            let isOngoing = currentTimeStr >= start && currentTimeStr <= end

            var minutesUntil: Int? = nil
            let dfFull = DateFormatter()
            dfFull.dateFormat = "yyyy-MM-dd HH:mm"
            if let startDate = dfFull.date(from: "\(todayStr) \(start)") {
                let diff = startDate.timeIntervalSince(now) / 60.0
                if diff > 0 {
                    minutesUntil = Int(diff)
                }
            }

            return WidgetCourseItem(
                id: item.id,
                title: item.title,
                location: item.location,
                teacher: item.teacher,
                startTime: start,
                endTime: end,
                startSection: item.startSection,
                endSection: item.endSection,
                colorHex: item.color,
                isOngoing: isOngoing,
                minutesUntilStart: minutesUntil
            )
        }

        let nextItem = todayWidgetItems.first(where: { $0.isOngoing || ($0.minutesUntilStart != nil && $0.minutesUntilStart! >= 0) })

        let weekDates = timelineEngineWeekCalculator.dateStringsInWeek(
            weekNumber: weekNum,
            semesterStartDate: current.startDate
        )
        let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
        var weekSchedules: [WidgetDaySchedule] = []
        for dayIdx in 1...7 {
            let dateStr = (dayIdx <= weekDates.count) ? weekDates[dayIdx - 1] : ""
            let dayCourses = weekOccurrences.filter { $0.dayOfWeek == dayIdx && $0.status != .cancelled }.map { item in
                WidgetCourseItem(
                    id: item.id,
                    title: item.title,
                    location: item.location,
                    teacher: item.teacher,
                    startTime: item.startTime ?? "",
                    endTime: item.endTime ?? "",
                    startSection: item.startSection,
                    endSection: item.endSection,
                    colorHex: item.color
                )
            }
            weekSchedules.append(
                WidgetDaySchedule(
                    id: dayIdx,
                    dayName: dayNames[dayIdx - 1],
                    dateString: dateStr,
                    courses: dayCourses
                )
            )
        }

        let payload = WidgetTimelinePayload(
            semesterName: current.name,
            currentWeek: weekNum,
            todayDateString: todayStr,
            nextClass: nextItem,
            todayClasses: todayWidgetItems,
            weekSchedule: weekSchedules
        )

        try? widgetDataStore.save(payload)
        WidgetCenter.shared.reloadAllTimelines()
    }

    // MARK: - Deep Link Routing

    public func handleDeepLink(_ url: URL) {
        guard url.scheme == "classflow" else { return }
        let host = url.host ?? ""
        let path = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let targetId = path.isEmpty ? (url.query?.components(separatedBy: "id=").last ?? "") : path

        if host == "occurrence", !targetId.isEmpty {
            // 1. Search in current week's occurrences
            if let occ = currentWeekOccurrences.first(where: { $0.id == targetId }) {
                selectedOccurrence = occ
                return
            }
            // 2. Search across entire semester
            if let semester = currentSemester {
                let currentCourses = courses.filter { $0.semesterId == semester.id }
                for w in 1...semester.totalWeeks {
                    let occs = timelineEngine.generateOccurrences(
                        semester: semester,
                        courses: currentCourses,
                        exceptions: exceptions,
                        forWeek: w,
                        activeWeek: calculatedCurrentWeek,
                        showNonCurrentWeekCourses: true
                    )
                    if let occ = occs.first(where: { $0.id == targetId }) {
                        selectedWeek = w
                        selectedOccurrence = occ
                        return
                    }
                }
            }
        } else if host == "add" {
            startAddingCourse()
        } else if host == "today" || host == "schedule" {
            jumpToCurrentWeek()
        }
    }

    // MARK: - Import & Export Operations

    public func exportClassFlowBackup() throws -> Data {
        try backupService.exportBackup(
            semesters: semesters,
            courses: courses,
            exceptions: exceptions,
            settings: [
                "showWeekends": String(settings.showWeekends),
                "themeMode": settings.themeMode
            ]
        )
    }

    public func exportCurrentSemesterICS() -> String? {
        guard let current = currentSemester else { return nil }
        let currentCourses = courses.filter { $0.semesterId == current.id }
        return icsExporter.exportToICS(
            semester: current,
            courses: currentCourses,
            exceptions: exceptions
        )
    }

    public func stageClassFlowImport(from data: Data) {
        do {
            let package = try backupService.importBackup(from: data)
            let preview = backupService.generateImportPreview(package: package, existingCourses: courses)
            self.stagedImportPackage = package
            self.stagedImportPreview = preview
            self.isShowingImportPreview = true
        } catch {
            self.importErrorMessage = error.localizedDescription
        }
    }

    public func commitClassFlowImport(mode: ImportPreviewSheet.ImportMode) {
        guard let package = stagedImportPackage else { return }

        switch mode {
        case .merge:
            for s in package.semesters {
                if !semesters.contains(where: { $0.id == s.id }) {
                    semesters.append(s)
                }
            }
            for c in package.courses {
                if !courses.contains(where: { $0.id == c.id }) {
                    courses.append(c)
                }
            }
            exceptions.append(contentsOf: package.exceptions)

        case .replace:
            self.semesters = package.semesters
            self.courses = package.courses
            self.exceptions = package.exceptions
            if let firstSem = semesters.first {
                self.currentSemesterId = firstSem.id
                self.selectedWeek = calculateCurrentWeek(for: firstSem)
            }
        }

        save()
        self.stagedImportPackage = nil
        self.stagedImportPreview = nil
        self.isShowingImportPreview = false
    }

    public func importICS(content: String) {
        guard let current = currentSemester else { return }
        let events = icsImporter.parseICS(content: content)
        let candidateCourses = icsImporter.convertToCourses(events: events, semester: current)
        for c in candidateCourses {
            addCourse(c)
        }
    }

    public func importOpenFormatJSON(content: String) {
        guard let current = currentSemester else {
            self.importErrorMessage = "请先创建或选择一个学期后再导入课程"
            return
        }
        let importer = OpenFormatCourseImporter()
        do {
            let result = try importer.parse(jsonString: content, semesterId: current.id)
            for c in result.courses {
                addCourse(c)
            }
            if let sections = result.parsedSections, !sections.isEmpty {
                var updated = current
                updated.sections = sections
                updateSemester(updated)
            }
        } catch {
            self.importErrorMessage = "解析失败: \(error.localizedDescription)"
        }
    }
}
