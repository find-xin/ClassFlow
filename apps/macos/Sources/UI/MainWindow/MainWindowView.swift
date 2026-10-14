import SwiftUI
import ClassFlowCore

/// Root view of the primary macOS window hosting the timetable canvas, sheets, and popovers.
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
            // Top Toolbar
            MainWindowToolbar(appState: appState)
            Divider()

            // Main Canvas Area
            if let semester = appState.currentSemester {
                let weekDates = appState.timelineEngineWeekCalculator.dateStringsInWeek(
                    weekNumber: appState.selectedWeek,
                    semesterStartDate: semester.startDate
                )

                TimetableGridView(
                    semester: semester,
                    occurrences: appState.currentWeekOccurrences,
                    dateStrings: weekDates,
                    showWeekends: appState.settings.showWeekends,
                    todayDateString: todayDateString,
                    cardOpacity: appState.settings.cardOpacity,
                    onSelectOccurrence: { item in
                        appState.selectedOccurrence = item
                    }
                )
            } else {
                emptySemesterPlaceholder
            }
        }
        .frame(minWidth: 800, minHeight: 600)
        // Course Detail Popover
        .popover(item: $appState.selectedOccurrence) { item in
            CoursePopoverView(
                item: item,
                onEdit: {
                    appState.isEditingOccurrence = true
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
                onDelete: {
                    if let courseId = item.courseId {
                        appState.deleteCourse(id: courseId)
                    }
                    appState.selectedOccurrence = nil
                }
            )
        }
        // Course Edit Sheet
        .sheet(isPresented: $appState.isEditingOccurrence) {
            if let item = appState.selectedOccurrence, let semester = appState.currentSemester {
                CourseEditSheet(
                    item: item,
                    availableSections: semester.sections,
                    onSave: { scope, name, teacher, color, day, start, end, room, notes in
                        if let courseId = item.courseId, let scheduleId = item.scheduleId {
                            // Update course metadata
                            if var course = appState.courses.first(where: { $0.id == courseId }) {
                                course.name = name
                                course.teacher = teacher
                                course.color = color
                                course.notes = notes
                                appState.updateCourse(course)
                            }
                            // Apply schedule mutation
                            appState.applyMutation(
                                courseId: courseId,
                                scheduleId: scheduleId,
                                scope: scope,
                                occurrenceDate: item.date,
                                updatedDayOfWeek: day,
                                updatedStartSection: start,
                                updatedEndSection: end,
                                updatedClassroom: room
                            )
                        }
                        appState.isEditingOccurrence = false
                        appState.selectedOccurrence = nil
                    },
                    onDismiss: {
                        appState.isEditingOccurrence = false
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
                    onSave: { newCourse in
                        appState.addCourse(newCourse)
                        appState.isAddingCourse = false
                    },
                    onDismiss: {
                        appState.isAddingCourse = false
                    }
                )
            }
        }
        // Settings Sheet
        .sheet(isPresented: $appState.isShowingSettings) {
            VStack {
                HStack {
                    Spacer()
                    Button("完成") {
                        appState.isShowingSettings = false
                    }
                    .padding()
                }
                SettingsView(appState: appState)
            }
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
        // Create Semester Sheet
        .sheet(isPresented: $appState.isShowingCreateSemester) {
            SemesterCreateSheet(appState: appState)
        }
        // Manage Semesters Sheet
        .sheet(isPresented: $appState.isShowingManageSemesters) {
            SemesterManageSheet(appState: appState)
        }
        // Edit Section Times Sheet
        .sheet(isPresented: $appState.isShowingEditSectionTimes) {
            SectionTimeEditSheet(appState: appState, targetSemester: appState.editingSemesterForSections)
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

    private var emptySemesterPlaceholder: some View {
        VStack(spacing: 14) {
            Image(systemName: "calendar.badge.plus")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text("暂无课表数据")
                .font(.title3.bold())
            Text("您可以新建一个属于自己的课表，或导入已有课表文件。")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                Button("新建课表") {
                    appState.isShowingCreateSemester = true
                }
                .buttonStyle(.borderedProminent)

                Button("导入课表文件...") {
                    appState.isShowingSettings = true
                }
                .buttonStyle(.bordered)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Extension for Helper Access
extension AppState {
    public var timelineEngineWeekCalculator: WeekCalculator {
        WeekCalculator()
    }
}
