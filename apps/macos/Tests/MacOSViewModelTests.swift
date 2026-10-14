import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

@Suite("MacOSViewModelTests")
struct MacOSViewModelTests {
    @MainActor
    func makeIsolatedAppState() -> AppState {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let dbURL = tempDir.appendingPathComponent("test_db.json")
        let db = LocalDatabase(customURL: dbURL)
        return AppState(database: db, notificationService: MockNotificationService())
    }

    @Test("AppState bootstraps default semester and courses")
    @MainActor
    func testBootstrapLoading() {
        let state = makeIsolatedAppState()
        #expect(!state.semesters.isEmpty)
        #expect(!state.courses.isEmpty)
        #expect(state.currentSemester != nil)
        #expect(state.currentSemester?.name == "2026-2027 秋季学期")
    }

    @Test("Week navigation operations")
    @MainActor
    func testWeekNavigation() {
        let state = makeIsolatedAppState()
        let initialWeek = state.selectedWeek

        state.nextWeek()
        #expect(state.selectedWeek == initialWeek + 1)

        state.previousWeek()
        #expect(state.selectedWeek == initialWeek)

        state.jumpToCurrentWeek()
        #expect(state.selectedWeek == state.calculatedCurrentWeek)
    }

    @Test("Add and delete course in AppState")
    @MainActor
    func testAddAndDeleteCourse() {
        let state = makeIsolatedAppState()
        let initialCount = state.courses.count

        let newCourse = Course(
            id: UUID(),
            semesterId: state.currentSemesterId!,
            name: "软件架构设计",
            teacher: "陈教授",
            color: "#059669",
            schedules: [
                CourseSchedule(
                    dayOfWeek: 4,
                    startSection: 1,
                    endSection: 2,
                    classroom: "信息楼 101"
                )
            ]
        )

        state.addCourse(newCourse)
        #expect(state.courses.count == initialCount + 1)

        state.deleteCourse(id: newCourse.id)
        #expect(state.courses.count == initialCount)
    }

    @Test("Apply single-occurrence mutation via AppState")
    @MainActor
    func testApplySingleOccurrenceMutation() {
        let state = makeIsolatedAppState()
        let mathCourse = state.courses.first(where: { $0.name.contains("高等数学") })!
        let mathSched = mathCourse.schedules[0]

        state.applyMutation(
            courseId: mathCourse.id,
            scheduleId: mathSched.id,
            scope: .thisOnly,
            occurrenceDate: "2026-10-19",
            updatedDayOfWeek: 2,
            updatedStartSection: 3,
            updatedEndSection: 4,
            updatedClassroom: "新教室 A101"
        )

        #expect(!state.exceptions.isEmpty)
        let ex = state.exceptions.last!
        #expect(ex.scheduleId == mathSched.id)
        #expect(ex.targetDayOfWeek == 2)
        #expect(ex.targetClassroom == "新教室 A101")
    }

    @Test("Export and import .classflow backup via AppState")
    @MainActor
    func testExportAndImportBackupInAppState() throws {
        let state1 = makeIsolatedAppState()
        let backupData = try state1.exportClassFlowBackup()
        #expect(backupData.count > 0)

        // New isolated instance
        let state2 = makeIsolatedAppState()
        state2.courses.removeAll() // Clear courses
        #expect(state2.courses.isEmpty)

        state2.stageClassFlowImport(from: backupData)
        #expect(state2.isShowingImportPreview == true)
        #expect(state2.stagedImportPreview != nil)

        state2.commitClassFlowImport(mode: .merge)
        #expect(!state2.courses.isEmpty)
        #expect(state2.isShowingImportPreview == false)
    }

    @Test("Export and import ICS calendar via AppState")
    @MainActor
    func testExportAndImportICSInAppState() {
        let state = makeIsolatedAppState()
        guard let icsText = state.exportCurrentSemesterICS() else {
            Issue.record("Failed to export ICS")
            return
        }

        #expect(icsText.contains("BEGIN:VCALENDAR"))
        #expect(icsText.contains("高等数学"))

        let countBefore = state.courses.count
        state.importICS(content: icsText)
        #expect(state.courses.count >= countBefore)
    }

    @Test("KeychainService save, read, and delete operations")
    func testKeychainService() {
        let keychain = KeychainService(serviceName: "org.classflow.test.\(UUID().uuidString)")
        let account = "test_key"
        let secret = "SuperSecretKey_12345"

        let saved = keychain.saveSecret(account: account, secret: secret)
        if saved {
            let readValue = keychain.readSecret(account: account)
            #expect(readValue == secret)

            keychain.deleteSecret(account: account)
            let afterDelete = keychain.readSecret(account: account)
            #expect(afterDelete == nil)
        }
    }

    @Test("Widget data generation produces valid timeline payload")
    @MainActor
    func testWidgetDataGeneration() {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let dbURL = tempDir.appendingPathComponent("test_db.json")
        let widgetURL = tempDir.appendingPathComponent("test_widget.json")
        let db = LocalDatabase(customURL: dbURL)
        let widgetStore = WidgetDataStore(customURL: widgetURL)
        let state = AppState(database: db, notificationService: MockNotificationService(), widgetDataStore: widgetStore)

        state.updateWidgetData()

        let payload = widgetStore.load()
        #expect(!payload.semesterName.isEmpty)
        #expect(payload.weekSchedule.count == 7)
        #expect(payload.currentWeek >= 1)
    }

    @Test("Calendar sync planning across semester occurrences")
    @MainActor
    func testCalendarSyncPlanGeneration() {
        let state = makeIsolatedAppState()
        guard let sem = state.currentSemester else {
            Issue.record("Missing current semester")
            return
        }

        let occurrences = state.allSemesterOccurrences(for: sem)
        #expect(!occurrences.isEmpty)

        let plan = state.calendarPlanner.generatePlan(
            occurrences: occurrences,
            semester: sem,
            existingMappings: []
        )

        #expect(plan.toCreate.count == occurrences.filter { $0.status != .cancelled }.count)
        #expect(plan.toUpdate.isEmpty)
        #expect(plan.toDelete.isEmpty)
    }

    @Test("Create, switch, and delete semesters via AppState")
    @MainActor
    func testSemesterCreationAndManagement() {
        let state = makeIsolatedAppState()
        let initialSemCount = state.semesters.count

        let newSem = state.createSemester(
            name: "大二下新学期",
            startDate: "2027-02-22",
            totalWeeks: 18,
            sections: AppState.standardSectionTimes()
        )

        #expect(state.semesters.count == initialSemCount + 1)
        #expect(state.currentSemesterId == newSem.id)
        #expect(state.currentSemester?.name == "大二下新学期")
        #expect(state.currentSemester?.totalWeeks == 18)
        #expect(state.currentSemester?.sections.count == 12)

        // Try deleting a semester
        state.deleteSemester(id: newSem.id)
        #expect(state.semesters.count == initialSemCount)
        #expect(state.currentSemesterId != newSem.id)

        // Guard against deleting last semester
        while state.semesters.count > 1 {
            state.deleteSemester(id: state.semesters.last!.id)
        }
        #expect(state.semesters.count == 1)
        state.deleteSemester(id: state.semesters.first!.id)
        #expect(state.semesters.count == 1) // Must not delete last semester
    }

    @Test("Custom section times modification and persistence")
    @MainActor
    func testCustomSectionTimes() {
        let state = makeIsolatedAppState()
        guard let current = state.currentSemester else {
            Issue.record("Missing current semester")
            return
        }

        let customTimes: [SectionTime] = [
            SectionTime(section: 1, startTime: "08:30", endTime: "09:15"),
            SectionTime(section: 2, startTime: "09:20", endTime: "10:05"),
            SectionTime(section: 3, startTime: "10:15", endTime: "11:00"),
            SectionTime(section: 4, startTime: "11:05", endTime: "11:50"),
            SectionTime(section: 5, startTime: "14:30", endTime: "15:15")
        ]

        state.updateSectionTimes(semesterId: current.id, sections: customTimes)

        let updatedSem = state.currentSemester!
        #expect(updatedSem.sections.count == 5)
        #expect(updatedSem.sections[0].startTime == "08:30")
        #expect(updatedSem.sections[0].endTime == "09:15")
        #expect(updatedSem.sections[4].startTime == "14:30")
    }
}

