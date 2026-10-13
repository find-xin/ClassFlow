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
        return AppState(database: db)
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
}
