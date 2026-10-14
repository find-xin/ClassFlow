import SwiftUI
import AppKit
import ClassFlowCore

/// Root view of the primary macOS window hosting the WakeUp-inspired aesthetic layout, floating drawers, and sheets.
public struct MainWindowView: View {
    @Bindable public var appState: AppState

    @State private var isShowingWeekDrawer: Bool = false
    @State private var isShowingQuickMenu: Bool = false

    public init(appState: AppState) {
        self.appState = appState
    }

    private var todayDateString: String {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: Date())
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            // 1. Seamless Full-Window Aesthetic Wallpaper Background
            WallpaperBackgroundView(settings: appState.settings)
                .ignoresSafeArea()

            // 2. Primary Layout Canvas
            VStack(spacing: 0) {
                // A. Minimalist Top Header (Clickable title + 4 ghost action icons)
                WakeUpHeaderView(
                    appState: appState,
                    isShowingWeekDrawer: $isShowingWeekDrawer,
                    isShowingQuickMenu: $isShowingQuickMenu
                )
                .padding(.horizontal, 18)
                .padding(.top, 14)
                .padding(.bottom, 6)

                // B. Date Bar (10月, 三 14 with dark badge, 四 15...)
                if let semester = appState.currentSemester {
                    let weekDates = appState.timelineEngineWeekCalculator.dateStringsInWeek(
                        weekNumber: appState.selectedWeek,
                        semesterStartDate: semester.startDate
                    )

                    DayHeaderView(
                        dateStrings: weekDates,
                        showWeekends: appState.settings.showWeekends,
                        todayDateString: todayDateString
                    )
                    .padding(.horizontal, 10)
                    .padding(.bottom, 4)

                    // C. Main Timetable Canvas (Transparent grid floating over wallpaper)
                    TimetableGridView(
                        semester: semester,
                        occurrences: appState.currentWeekOccurrences,
                        dateStrings: weekDates,
                        showWeekends: appState.settings.showWeekends,
                        todayDateString: todayDateString,
                        cardOpacity: appState.settings.cardOpacity,
                        onSelectOccurrence: { item in
                            appState.selectedOccurrence = item
                        },
                        onQuickImport: {
                            openImportDialog()
                        }
                    )
                } else {
                    emptySemesterPlaceholder
                }

                Spacer(minLength: 0)

                // D. Bottom Navigation Bar (日程, 课程, 学习, 我的)
                WakeUpTabBar(
                    selectedTab: $appState.settings.selectedTab,
                    onTabSelected: { tab in
                        handleTabSelection(tab)
                    }
                )
                .padding(.top, 4)
                .padding(.bottom, 8)
            }

            // 3. Floating Overlay Drawers at the Bottom (Week & Timetable, Quick Actions)
            HStack(alignment: .bottom, spacing: 14) {
                if isShowingWeekDrawer {
                    WakeUpWeekAndTimetableDrawer(
                        appState: appState,
                        isPresented: $isShowingWeekDrawer
                    )
                    .transition(.asymmetric(
                        insertion: .scale(scale: 0.95).combined(with: .opacity).combined(with: .move(edge: .bottom)),
                        removal: .opacity.combined(with: .move(edge: .bottom))
                    ))
                }

                Spacer()

                if isShowingQuickMenu {
                    WakeUpQuickMenuDrawer(
                        appState: appState,
                        isPresented: $isShowingQuickMenu
                    )
                    .transition(.asymmetric(
                        insertion: .scale(scale: 0.95).combined(with: .opacity).combined(with: .move(edge: .bottom)),
                        removal: .opacity.combined(with: .move(edge: .bottom))
                    ))
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 44) // Floats comfortably above bottom tab bar
        }
        .frame(minWidth: 840, minHeight: 640)
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
                            if var course = appState.courses.first(where: { $0.id == courseId }) {
                                course.name = name
                                course.teacher = teacher
                                course.color = color
                                course.notes = notes
                                appState.updateCourse(course)
                            }
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

    private func handleTabSelection(_ tab: String) {
        switch tab {
        case "schedule":
            // In schedule tab, jump to current week
            appState.jumpToCurrentWeek()
        case "timetable":
            // Active timetable
            break
        case "profile":
            appState.isShowingSettings = true
        default:
            break
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
            if url.pathExtension.lowercased() == "classflow" {
                if let data = try? Data(contentsOf: url) {
                    appState.stageClassFlowImport(from: data)
                }
            } else if url.pathExtension.lowercased() == "ics" {
                if let content = try? String(contentsOf: url, encoding: .utf8) {
                    appState.importICS(content: content)
                }
            }
        }
    }
}

// MARK: - Extension for Helper Access
extension AppState {
    public var timelineEngineWeekCalculator: WeekCalculator {
        WeekCalculator()
    }
}
