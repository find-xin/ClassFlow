import Foundation
import SwiftUI
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
    public var isAddingCourse: Bool = false
    public var isEditingOccurrence: Bool = false
    public var isShowingSettings: Bool = false
    public var isShowingImportPreview: Bool = false
    public var stagedImportPackage: ClassFlowPackage? = nil
    public var stagedImportPreview: ImportPreview? = nil
    public var importErrorMessage: String? = nil

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
            calendarMappings: calendarMappings
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
        return timelineEngine.generateOccurrences(
            semester: current,
            courses: currentCourses,
            exceptions: exceptions,
            forWeek: selectedWeek,
            activeWeek: calculatedCurrentWeek
        )
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

    public func selectSemester(id: UUID) {
        guard let semester = semesters.first(where: { $0.id == id }) else { return }
        self.currentSemesterId = id
        for i in 0..<semesters.count {
            semesters[i].isCurrent = (semesters[i].id == id)
        }
        self.selectedWeek = calculateCurrentWeek(for: semester)
        save()
    }

    // MARK: - Course Management & Mutations

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
                activeWeek: currentWeek
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

        let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
        var weekSchedules: [WidgetDaySchedule] = []
        for dayIdx in 1...7 {
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
                    dateString: "",
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
}
