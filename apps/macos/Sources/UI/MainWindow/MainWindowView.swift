import SwiftUI
import AppKit
import ClassFlowCore

/// Root view of the primary macOS window hosting the WakeUp-inspired aesthetic layout, floating drawers, and sheets.
public struct MainWindowView: View {
    @Bindable public var appState: AppState

    public init(appState: AppState) {
        self.appState = appState
    }

    private var todayDateString: String {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: Date())
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Dynamic Content Canvas based on selectedTab
            Group {
                switch appState.settings.selectedTab {
                case "schedule":
                    TodayAgendaTimelineView(appState: appState)
                case "study":
                    if appState.settings.enableStudyModule {
                        StudyFocusView(appState: appState)
                    } else {
                        timetableContent
                    }
                default:
                    timetableContent
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Bottom Navigation Bar (日程, 课程, [学习])
            WakeUpTabBar(
                selectedTab: $appState.settings.selectedTab,
                showStudyTab: appState.settings.enableStudyModule,
                onTabSelected: { tab in
                    handleTabSelection(tab)
                }
            )
            .padding(.top, 4)
            .padding(.bottom, 8)
        }
        .frame(minWidth: 780, minHeight: 560)
        .background {
            WallpaperBackgroundView(settings: appState.settings)
        }
        .background {
            Group {
                Button("") { appState.previousWeek() }
                    .keyboardShortcut("[", modifiers: .command)
                Button("") { appState.nextWeek() }
                    .keyboardShortcut("]", modifiers: .command)
                Button("") { appState.previousWeek() }
                    .keyboardShortcut(.leftArrow, modifiers: .option)
                Button("") { appState.nextWeek() }
                    .keyboardShortcut(.rightArrow, modifiers: .option)
                Button("") { appState.jumpToCurrentWeek() }
                    .keyboardShortcut("t", modifiers: .command)
                Button("") { appState.startAddingCourse() }
                    .keyboardShortcut("n", modifiers: .command)
            }
            .opacity(0)
            .allowsHitTesting(false)
        }
        // Course Detail Popover
        .popover(item: $appState.selectedOccurrence) { item in
            CoursePopoverView(
                item: item,
                onEdit: {
                    if let courseId = item.courseId, let course = appState.courses.first(where: { $0.id == courseId }) {
                        appState.selectedOccurrence = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                            appState.editingCourseInitialMode = .fullCourse
                            appState.editingOccurrence = item
                            appState.editingCourse = course
                        }
                    }
                },
                onReschedule: {
                    if let courseId = item.courseId, let course = appState.courses.first(where: { $0.id == courseId }) {
                        appState.selectedOccurrence = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                            appState.editingCourseInitialMode = .singleOccurrence
                            appState.editingOccurrence = item
                            appState.editingCourse = course
                        }
                    }
                },
                onQuickCancel: {
                    guard let courseId = item.courseId, let scheduleId = item.scheduleId else { return }
                    appState.cancelSchedule(
                        courseId: courseId,
                        scheduleId: scheduleId,
                        scope: .thisOnly,
                        occurrenceDate: item.date
                    )
                    appState.selectedOccurrence = nil
                },
                onRestore: {
                    if let courseId = item.courseId, let scheduleId = item.scheduleId {
                        appState.restoreCancelledOccurrence(
                            courseId: courseId,
                            scheduleId: scheduleId,
                            occurrenceDate: item.date
                        )
                    }
                    appState.selectedOccurrence = nil
                },
                onDelete: {
                    if let courseId = item.courseId {
                        appState.deleteCourse(id: courseId)
                    } else if let eventId = item.calendarEventId {
                        appState.deleteCalendarEvent(id: eventId)
                    }
                    appState.selectedOccurrence = nil
                },
                onStartFocus: appState.settings.enableStudyModule ? {
                    appState.startFocusForCourse(item)
                } : nil
            )
        }
        // Course Edit Sheet (Supports both full course and single-occurrence 临时调课)
        .sheet(item: $appState.editingCourse) { course in
            if let semester = appState.currentSemester {
                CourseEditSheet(
                    course: course,
                    occurrence: appState.editingOccurrence,
                    initialMode: appState.editingCourseInitialMode,
                    availableSections: semester.sections,
                    courseNameSuggestions: existingCourseNames,
                    teacherSuggestions: existingTeachers,
                    classroomSuggestions: existingClassrooms,
                    onSaveCourse: { updatedCourse in
                        appState.updateCourse(updatedCourse)
                        appState.editingCourse = nil
                        appState.editingOccurrence = nil
                    },
                    onApplyMutation: { scope, day, start, end, room in
                        if let occ = appState.editingOccurrence, let courseId = occ.courseId, let scheduleId = occ.scheduleId {
                            appState.applyMutation(
                                courseId: courseId,
                                scheduleId: scheduleId,
                                scope: scope,
                                occurrenceDate: occ.date,
                                updatedDayOfWeek: day,
                                updatedStartSection: start,
                                updatedEndSection: end,
                                updatedClassroom: room
                            )
                        }
                        appState.editingCourse = nil
                        appState.editingOccurrence = nil
                    },
                    onDismiss: {
                        appState.editingCourse = nil
                        appState.editingOccurrence = nil
                    }
                )
            }
        }
        // Add Course Sheet
        .sheet(isPresented: $appState.isAddingCourse) {
            if let semester = appState.currentSemester {
                CourseAddSheet(
                    semesterId: semester.id,
                    availableSections: semester.sections,
                    initialSlot: appState.prefilledSlotForAdd,
                    courseNameSuggestions: existingCourseNames,
                    teacherSuggestions: existingTeachers,
                    classroomSuggestions: existingClassrooms,
                    onSave: { newCourse in
                        appState.addCourse(newCourse)
                        appState.isAddingCourse = false
                        appState.prefilledSlotForAdd = nil
                    },
                    onDismiss: {
                        appState.isAddingCourse = false
                        appState.prefilledSlotForAdd = nil
                    }
                )
            }
        }
        // Add Calendar Event Sheet (from Timetable, Quick Menu, or Toolbar)
        .sheet(isPresented: $appState.isAddingCalendarEvent) {
            ScheduleItemCreateSheet(
                appState: appState,
                initialMode: .event,
                prefilledDayOfWeek: appState.prefilledSlotForEvent?.dayOfWeek,
                prefilledSection: appState.prefilledSlotForEvent?.startSection,
                onDismiss: {
                    appState.isAddingCalendarEvent = false
                    appState.prefilledSlotForEvent = nil
                }
            )
        }
        // Settings Sheet
        .sheet(isPresented: $appState.isShowingSettings) {
            SettingsView(appState: appState, onDismiss: {
                appState.isShowingSettings = false
            })
        }
        // Create Semester Sheet
        .sheet(isPresented: $appState.isShowingCreateSemester) {
            SemesterCreateSheet(appState: appState)
        }
        // Manage Semesters Sheet
        .sheet(isPresented: $appState.isShowingManageSemesters) {
            SemesterManageSheet(appState: appState)
        }

        // Timetable Profiles Sheet ("设置时间表" - Image 1)
        .sheet(isPresented: $appState.isShowingSectionProfiles) {
            TimetableProfilesSheet(
                appState: appState,
                isPresented: $appState.isShowingSectionProfiles
            )
        }
        // Appearance Settings Sheet ("课表外观" - Images 3 & 4)
        .sheet(isPresented: $appState.isShowingAppearance) {
            TimetableAppearanceSheet(
                appState: appState,
                isPresented: $appState.isShowingAppearance
            )
        }
        // Widgets Sheet (Direct Live Previews & Status)
        .sheet(isPresented: $appState.isShowingWidgets) {
            VStack(spacing: 0) {
                HStack {
                    Text("桌面与通知中心小组件")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.primary)
                    Spacer()
                    Button("完成") {
                        appState.isShowingWidgets = false
                    }
                    .buttonStyle(.plain)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 5)
                    .background(Color(hex: "#FF5A5F"))
                    .clipShape(Capsule())
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
                .background(.bar)

                Divider()

                WidgetSettingsTab(appState: appState)
            }
            .frame(width: 580, height: 620)
        }
        // Course List Sheet (已选课程管理)
        .sheet(isPresented: $appState.isShowingCourseList) {
            WakeUpCourseListSheet(appState: appState)
        }
        // Edit Section Times Sheet
        .sheet(isPresented: $appState.isShowingEditSectionTimes) {
            SectionTimeEditSheet(appState: appState, targetSemester: appState.editingSemesterForSections)
        }
        // Import Preview Sheet
        .sheet(isPresented: $appState.isShowingImportPreview) {
            if let preview = appState.stagedImportPreview {
                ImportPreviewSheet(
                    preview: preview,
                    onConfirm: { mode in
                        appState.commitClassFlowImport(mode: mode)
                    },
                    onDismiss: {
                        appState.isShowingImportPreview = false
                        appState.stagedImportPackage = nil
                        appState.stagedImportPreview = nil
                    }
                )
            }
        }
        .alert("导入失败", isPresented: .init(
            get: { appState.importErrorMessage != nil },
            set: { if !$0 { appState.importErrorMessage = nil } }
        )) {
            Button("好", role: .cancel) { appState.importErrorMessage = nil }
        } message: {
            Text(appState.importErrorMessage ?? "")
        }
    }

    @ViewBuilder
    private var timetableContent: some View {
        VStack(spacing: 0) {
            WakeUpHeaderView(appState: appState)
                .padding(.leading, 78)
                .padding(.trailing, 18)
                .padding(.top, 14)
                .padding(.bottom, 6)

            if let semester = appState.currentSemester {
                let weekDates = appState.timelineEngineWeekCalculator.dateStringsInWeek(
                    weekNumber: appState.selectedWeek,
                    semesterStartDate: semester.startDate
                )

                TimetableGridView(
                    semester: semester,
                    occurrences: appState.currentWeekOccurrences,
                    dateStrings: weekDates,
                    todayDateString: todayDateString,
                    displayMode: appState.timetableDisplayMode,
                    selectedDayOfWeek: appState.selectedDayOfWeek,
                    settings: appState.settings,
                    onSelectOccurrence: { item in
                        appState.selectedOccurrence = item
                    },
                    onSelectDayOfWeek: { day in
                        appState.selectedDayOfWeek = day
                    },
                    onAddCourseAt: { dayOfWeek, section in
                        appState.startAddingCourse(dayOfWeek: dayOfWeek, startSection: section)
                    },
                    onAddCalendarEventAt: { dayOfWeek, section in
                        appState.startAddingCalendarEvent(dayOfWeek: dayOfWeek, section: section)
                    },
                    onQuickImport: {
                        openImportDialog()
                    },
                    onPreviousWeek: {
                        appState.previousWeek()
                    },
                    onNextWeek: {
                        appState.nextWeek()
                    },
                    onPreviousDay: {
                        appState.selectPreviousDay()
                    },
                    onNextDay: {
                        appState.selectNextDay()
                    },
                    onToday: {
                        appState.selectToday()
                    }
                )
            } else {
                emptySemesterPlaceholder
            }
        }
    }

    private func handleTabSelection(_ tab: String) {
        if tab == "study" && !appState.settings.enableStudyModule {
            appState.settings.selectedTab = "courses"
            return
        }
        if tab == "schedule" {
            appState.jumpToCurrentWeek()
        }
    }

    private var emptySemesterPlaceholder: some View {
        VStack(spacing: 14) {
            Text("无课表，通过教务导课")
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.primary.opacity(0.8))

            HStack(spacing: 12) {
                Button("一键导入") {
                    openImportDialog()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Capsule().fill(.ultraThinMaterial))

                Button("新建课表") {
                    appState.isShowingCreateSemester = true
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @MainActor
    private func openImportDialog() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        if panel.runModal() == .OK, let url = panel.url {
            let ext = url.pathExtension.lowercased()
            if ext == "classflow" {
                if let data = try? Data(contentsOf: url) {
                    appState.stageClassFlowImport(from: data)
                }
            } else if ext == "ics" {
                if let content = try? String(contentsOf: url, encoding: .utf8) {
                    appState.importICS(content: content)
                }
            } else if ext == "json" {
                if let content = try? String(contentsOf: url, encoding: .utf8) {
                    appState.importOpenFormatJSON(content: content)
                }
            }
        }
    }

    // MARK: - Autocomplete Search Candidates
    private var existingCourseNames: [String] {
        Array(Set(appState.courses.map { $0.name.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })).sorted()
    }

    private var existingTeachers: [String] {
        let courseTeachers = appState.courses.compactMap { $0.teacher?.trimmingCharacters(in: .whitespacesAndNewlines) }
        let scheduleTeachers = appState.courses.flatMap { $0.schedules.compactMap { $0.teacher?.trimmingCharacters(in: .whitespacesAndNewlines) } }
        return Array(Set((courseTeachers + scheduleTeachers).filter { !$0.isEmpty })).sorted()
    }

    private var existingClassrooms: [String] {
        let rooms = appState.courses.flatMap { $0.schedules.compactMap { $0.classroom?.trimmingCharacters(in: .whitespacesAndNewlines) } }
        return Array(Set(rooms.filter { !$0.isEmpty })).sorted()
    }
}

// MARK: - Extension for Helper Access
extension AppState {
    public var timelineEngineWeekCalculator: WeekCalculator {
        WeekCalculator()
    }
}
