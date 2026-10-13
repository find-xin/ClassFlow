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
    }

    private var emptySemesterPlaceholder: some View {
        VStack(spacing: 12) {
            Image(systemName: "calendar.badge.plus")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text("暂无学期数据")
                .font(.title3)
            Button("创建第一个学期") {
                appState.isShowingSettings = true
            }
            .buttonStyle(.borderedProminent)
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
