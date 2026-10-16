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

    @Test("Timetable profile management and switching")
    @MainActor
    func testTimetableProfiles() {
        let state = makeIsolatedAppState()
        #expect(!state.timetableProfiles.isEmpty)

        // Create new profile
        let newProfile = TimetableProfile(
            name: "夏季作息时间表",
            sections: [
                SectionTime(section: 1, startTime: "08:00", endTime: "08:45"),
                SectionTime(section: 2, startTime: "08:55", endTime: "09:40")
            ],
            fixedDuration: true,
            sectionDurationMinutes: 45
        )
        state.saveTimetableProfile(newProfile)
        #expect(state.timetableProfiles.contains(where: { $0.id == newProfile.id }))

        // Select profile
        state.selectTimetableProfile(id: newProfile.id)
        #expect(state.activeProfileId == newProfile.id)
        #expect(state.currentSemester?.sections.count == 2)
        #expect(state.currentSemester?.sections[0].startTime == "08:00")
    }

    @Test("Appearance settings persistence")
    @MainActor
    func testAppearanceSettings() {
        let state = makeIsolatedAppState()
        state.settings.wallpaperPreset = "aurora"
        state.settings.gridHeight = 72.0
        state.settings.cardCornerRadius = 8.0
        state.settings.showTimeInCard = true
        state.settings.showAtPrefixForRoom = false
        state.save()

        #expect(state.settings.wallpaperPreset == "aurora")
        #expect(state.settings.gridHeight == 72.0)
        #expect(state.settings.cardCornerRadius == 8.0)
        #expect(state.settings.showTimeInCard == true)
        #expect(state.settings.showAtPrefixForRoom == false)
    }

    @Test("Start adding course with prefilled slot")
    @MainActor
    func testStartAddingCourseWithPrefilledSlot() {
        let state = makeIsolatedAppState()
        state.startAddingCourse(dayOfWeek: 3, startSection: 5, endSection: 6)
        #expect(state.isAddingCourse == true)
        #expect(state.prefilledSlotForAdd?.dayOfWeek == 3)
        #expect(state.prefilledSlotForAdd?.startSection == 5)
        #expect(state.prefilledSlotForAdd?.endSection == 6)
    }

    @Test("Current week occurrences respect weekPattern start/end and showNonCurrentWeekCourses")
    @MainActor
    func testCurrentWeekOccurrencesWeekRanges() {
        let state = makeIsolatedAppState()
        let semId = state.currentSemesterId!

        // Course active only weeks 1..4
        let earlyCourse = Course(
            id: UUID(),
            semesterId: semId,
            name: "早期短学时讲座",
            schedules: [
                CourseSchedule(
                    dayOfWeek: 1,
                    startSection: 1,
                    endSection: 2,
                    classroom: "A101",
                    weekPattern: WeekPattern(type: .all, startWeek: 1, endWeek: 4)
                )
            ]
        )
        state.courses = [earlyCourse]

        // When viewing week 2 (active)
        state.selectedWeek = 2
        var items = state.currentWeekOccurrences
        #expect(items.count == 1)
        #expect(items[0].isCurrentWeek == true)

        // When viewing week 6 (inactive for earlyCourse)
        state.selectedWeek = 6
        state.settings.showNonCurrentWeekCourses = true
        items = state.currentWeekOccurrences
        #expect(items.count == 1)
        #expect(items[0].isCurrentWeek == false)

        state.settings.showNonCurrentWeekCourses = false
        items = state.currentWeekOccurrences
        #expect(items.isEmpty)
    }

    @Test("Pomodoro ToDo task lifecycle and focus record logging")
    @MainActor
    func testPomodoroTaskManagementAndSessionRecording() {
        let state = makeIsolatedAppState()

        // 1. Create task linked to course
        let task = PomodoroTask(
            title: "微积分期中重点突破",
            courseName: "高等数学",
            estimatedPomodoros: 2,
            priority: .high
        )
        state.addPomodoroTask(task)
        #expect(state.pomodoroTasks.contains(where: { $0.id == task.id }))
        #expect(state.activePomodoroTaskId == task.id)

        // 2. Record a 25min completed pomodoro
        state.recordCompletedPomodoro(
            taskId: task.id,
            taskTitle: task.title,
            courseName: task.courseName,
            focusMode: .pomodoro,
            durationMinutes: 25,
            tag: "期中复习"
        )

        let updatedTask = state.pomodoroTasks.first(where: { $0.id == task.id })!
        #expect(updatedTask.completedPomodoros == 1)
        #expect(updatedTask.totalFocusMinutes == 25)
        #expect(updatedTask.isCompleted == false)
        #expect(!state.pomodoroRecords.isEmpty)
        #expect(state.pomodoroRecords[0].taskTitle == "微积分期中重点突破")

        // 3. Record second pomodoro -> auto-completes task
        state.recordCompletedPomodoro(
            taskId: task.id,
            taskTitle: task.title,
            courseName: task.courseName,
            focusMode: .pomodoro,
            durationMinutes: 25,
            tag: "期中复习"
        )
        let finishedTask = state.pomodoroTasks.first(where: { $0.id == task.id })!
        #expect(finishedTask.completedPomodoros == 2)
        #expect(finishedTask.isCompleted == true)
        #expect(finishedTask.completedAt != nil)

        // 4. Toggle completion
        state.toggleTaskCompletion(id: task.id)
        #expect(state.pomodoroTasks.first(where: { $0.id == task.id })!.isCompleted == false)

        // 5. Delete task
        state.deletePomodoroTask(id: task.id)
        #expect(!state.pomodoroTasks.contains(where: { $0.id == task.id }))
    }

    @Test("CalendarEvent lifecycle and occurrence projection")
    @MainActor
    func testCalendarEventLifecycle() {
        let state = makeIsolatedAppState()
        let initialEventsCount = state.calendarEvents.count

        let newEvent = CalendarEvent(
            id: UUID(),
            title: "学术讲座：量子计算前沿",
            startTime: "2026-10-15T09:00:00",
            endTime: "2026-10-15T11:00:00",
            location: "学术交流厅 201",
            description: "请携带笔记本",
            color: "#34C759",
            startSection: 1,
            endSection: 2,
            frequency: "daily"
        )

        state.addCalendarEvent(newEvent)
        #expect(state.calendarEvents.count == initialEventsCount + 1)

        let occurrences = state.currentWeekOccurrences
        let projected = occurrences.first(where: { $0.calendarEventId == newEvent.id })
        #expect(projected != nil)
        #expect(projected?.sourceType == .calendarEvent)
        #expect(projected?.title == "学术讲座：量子计算前沿")

        state.deleteCalendarEvent(id: newEvent.id)
        #expect(state.calendarEvents.count == initialEventsCount)
        #expect(!state.currentWeekOccurrences.contains(where: { $0.calendarEventId == newEvent.id }))
    }

    @Test("TimetableDisplayMode and Day View navigation")
    @MainActor
    func testTimetableDisplayModeAndDayNavigation() {
        let state = makeIsolatedAppState()
        #expect(state.timetableDisplayMode == .week)

        // Switch to Day View
        state.timetableDisplayMode = .day
        #expect(state.timetableDisplayMode == .day)

        state.selectedDayOfWeek = 3
        state.selectNextDay()
        #expect(state.selectedDayOfWeek == 4)

        state.selectPreviousDay()
        #expect(state.selectedDayOfWeek == 3)

        state.selectToday()
        #expect(state.selectedDayOfWeek >= 1 && state.selectedDayOfWeek <= 7)
    }

    @Test("CalendarEvent with custom days of week recurrence")
    @MainActor
    func testCalendarEventCustomDaysOfWeek() {
        let state = makeIsolatedAppState()
        let event = CalendarEvent(
            id: UUID(),
            title: "实验室例会 (周一与周四)",
            startTime: "2026-10-12T14:00:00",
            endTime: "2026-10-12T15:30:00",
            frequency: "customDays",
            customDaysOfWeek: [1, 4]
        )
        state.addCalendarEvent(event)

        let occurrences = state.currentWeekOccurrences.filter { $0.calendarEventId == event.id }
        let days = Set(occurrences.map { $0.dayOfWeek })
        #expect(days.contains(1))
        #expect(days.contains(4))
        #expect(!days.contains(2))
        #expect(!days.contains(3))
        #expect(!days.contains(5))
    }

    @Test("All-day and out-of-bounds early/late schedule events projection")
    @MainActor
    func testAllDayAndOutOfBoundsEvents() {
        let state = makeIsolatedAppState()

        // 1. All-Day Event
        let allDayEvent = CalendarEvent(
            id: UUID(),
            title: "全天科研创新实践展",
            startTime: "2026-10-14T00:00:00",
            endTime: "2026-10-14T23:59:59",
            isAllDay: true
        )
        state.addCalendarEvent(allDayEvent)

        // 2. Early morning event (06:30 - 07:30, earlier than Section 1 08:00)
        let earlyMorningEvent = CalendarEvent(
            id: UUID(),
            title: "晨间长跑与体能训练",
            startTime: "2026-10-14T06:30:00",
            endTime: "2026-10-14T07:30:00",
            isAllDay: false
        )
        state.addCalendarEvent(earlyMorningEvent)

        // 3. Late night event (22:00 - 23:00, later than Section 12 21:30)
        let lateNightEvent = CalendarEvent(
            id: UUID(),
            title: "实验室夜间数据复盘会",
            startTime: "2026-10-14T22:00:00",
            endTime: "2026-10-14T23:00:00",
            isAllDay: false
        )
        state.addCalendarEvent(lateNightEvent)

        let occurrences = state.currentWeekOccurrences
        let projectedAllDay = occurrences.first(where: { $0.calendarEventId == allDayEvent.id })
        #expect(projectedAllDay != nil)
        #expect(projectedAllDay?.isAllDay == true)

        let projectedEarly = occurrences.first(where: { $0.calendarEventId == earlyMorningEvent.id })
        #expect(projectedEarly != nil)
        #expect(projectedEarly?.startTime == "06:30")
        #expect(projectedEarly?.isAllDay == false)

        let projectedLate = occurrences.first(where: { $0.calendarEventId == lateNightEvent.id })
        #expect(projectedLate != nil)
        #expect(projectedLate?.startTime == "22:00")
        #expect(projectedLate?.isAllDay == false)

        // Test SectionTime supporting 0 (早) and 99 (晚)
        let earlySection = SectionTime(section: 0, startTime: "06:30", endTime: "08:00")
        #expect(earlySection.section == 0)

        let lateSection = SectionTime(section: 99, startTime: "21:30", endTime: "23:00")
        #expect(lateSection.section == 99)
    }

    @Test("Course autocompletion search candidate extraction and filtering")
    @MainActor
    func testAutocompleteCandidateExtractionAndFiltering() {
        let state = makeIsolatedAppState()

        // 1. Verify candidate extraction from bootstrapped courses
        let courseNames = Array(Set(state.courses.map { $0.name.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })).sorted()
        let courseTeachers = state.courses.compactMap { $0.teacher?.trimmingCharacters(in: .whitespacesAndNewlines) }
        let scheduleTeachers = state.courses.flatMap { $0.schedules.compactMap { $0.teacher?.trimmingCharacters(in: .whitespacesAndNewlines) } }
        let teachers = Array(Set((courseTeachers + scheduleTeachers).filter { !$0.isEmpty })).sorted()
        let classrooms = Array(Set(state.courses.flatMap { $0.schedules.compactMap { $0.classroom?.trimmingCharacters(in: .whitespacesAndNewlines) } }.filter { !$0.isEmpty })).sorted()

        #expect(!courseNames.isEmpty)
        #expect(courseNames.contains(where: { $0.contains("高等数学") }))
        #expect(!classrooms.isEmpty)
        #expect(!teachers.isEmpty)

        // 2. Test filtering logic
        func filterSuggestions(_ query: String, candidates: [String]) -> [String] {
            let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty { return candidates }
            return candidates.filter { $0.localizedCaseInsensitiveContains(trimmed) }
        }

        // Empty query yields all candidates
        #expect(filterSuggestions("", candidates: courseNames) == courseNames)

        // Substring query filters matching candidates
        let mathMatches = filterSuggestions("数学", candidates: courseNames)
        #expect(!mathMatches.isEmpty)
        #expect(mathMatches.allSatisfy { $0.contains("数学") })

        // Query not present returns empty (allowing arbitrary custom typing)
        let unlistedMatches = filterSuggestions("全新自创实验课", candidates: courseNames)
        #expect(unlistedMatches.isEmpty)
    }

    @Test("CalendarEvent with custom days of week and custom weeks matrix linkage")
    @MainActor
    func testCalendarEventCustomDaysAndWeeksMatrixLinkage() {
        let state = makeIsolatedAppState()
        state.selectedWeek = 1

        let event = CalendarEvent(
            id: UUID(),
            title: "前三周周二周四专项研讨",
            startTime: "2026-09-08T14:00:00",
            endTime: "2026-09-08T15:30:00",
            frequency: "customDays",
            customWeeks: [1, 2, 3],
            customDaysOfWeek: [2, 4],
            startDate: "2026-09-01",
            endDate: "2026-11-30"
        )
        state.addCalendarEvent(event)

        // Week 1: should contain Tuesday (2) and Thursday (4)
        let week1Items = state.currentWeekOccurrences.filter { $0.calendarEventId == event.id }
        let week1Days = Set(week1Items.map { $0.dayOfWeek })
        #expect(week1Days.contains(2))
        #expect(week1Days.contains(4))
        #expect(!week1Days.contains(1))
        #expect(!week1Days.contains(3))

        // Switch to Week 4 (outside customWeeks [1, 2, 3]): should be completely empty
        state.selectedWeek = 4
        let week4Items = state.currentWeekOccurrences.filter { $0.calendarEventId == event.id }
        #expect(week4Items.isEmpty)
    }
}


