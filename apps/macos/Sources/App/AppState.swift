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
    private let timelineEngine: TimelineEngine
    private let mutationCoordinator: ScheduleMutationCoordinator
    public let backupService: ClassFlowBackupService
    public let icsExporter: ICSExporter
    public let icsImporter: ICSImporter

    public init(database: LocalDatabase = LocalDatabase()) {
        self.database = database
        self.timelineEngine = TimelineEngine()
        self.mutationCoordinator = ScheduleMutationCoordinator()
        self.backupService = ClassFlowBackupService()
        self.icsExporter = ICSExporter()
        self.icsImporter = ICSImporter()
        load()
    }

    public func load() {
        let payload = database.load()
        self.semesters = payload.semesters
        self.courses = payload.courses
        self.exceptions = payload.exceptions
        self.settings = payload.settings

        if let current = semesters.first(where: { $0.isCurrent }) ?? semesters.first {
            self.currentSemesterId = current.id
            self.selectedWeek = calculateCurrentWeek(for: current)
        }
    }

    public func save() {
        let payload = LocalDatabase.DatabasePayload(
            semesters: semesters,
            courses: courses,
            exceptions: exceptions,
            settings: settings
        )
        do {
            try database.save(payload)
        } catch {
            print("[AppState] Error saving database: \(error)")
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
        // Clear related exceptions
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
