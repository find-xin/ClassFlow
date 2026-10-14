import SwiftUI
import ClassFlowCore

/// Floating bottom-right quick actions drawer replicating WakeUp's 8-button shortcut grid.
public struct WakeUpQuickMenuDrawer: View {
    @Bindable public var appState: AppState
    @Binding public var isPresented: Bool

    @State private var isShowingCourseList: Bool = false

    public init(appState: AppState, isPresented: Binding<Bool>) {
        self.appState = appState
        self._isPresented = isPresented
    }

    public var body: some View {
        VStack(spacing: 16) {
            // Row 1
            HStack(spacing: 24) {
                gridItem(icon: "clock", label: "上课时间") {
                    isPresented = false
                    appState.editingSemesterForSections = appState.currentSemester
                    appState.isShowingEditSectionTimes = true
                }

                gridItem(icon: "slider.horizontal.3", label: "课表设置") {
                    isPresented = false
                    appState.isShowingSettings = true
                }

                gridItem(icon: "archivebox", label: "已选课程") {
                    isShowingCourseList = true
                }

                gridItem(icon: "iphone", label: "小组件") {
                    isPresented = false
                    appState.isShowingSettings = true
                }
            }

            // Row 2
            HStack(spacing: 24) {
                gridItem(icon: "bubble.left.and.bubble.right", label: "联系我们") {
                    isPresented = false
                    appState.isShowingSettings = true
                }

                gridItem(icon: "bell", label: "上课提醒") {
                    isPresented = false
                    appState.isShowingSettings = true
                }

                gridItem(icon: appState.settings.themeMode == "dark" ? "sun.max" : "moon", label: "夜间模式") {
                    if appState.settings.themeMode == "dark" {
                        appState.settings.themeMode = "light"
                    } else {
                        appState.settings.themeMode = "dark"
                    }
                    appState.save()
                }

                gridItem(icon: "rectangle.grid.2x2", label: "简洁模式") {
                    appState.settings.isCompactMode.toggle()
                    appState.save()
                }
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 18)
        .frame(width: 380)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.2), lineWidth: 0.5)
                )
                .shadow(color: Color.black.opacity(0.12), radius: 16, x: 0, y: 6)
        )
        .sheet(isPresented: $isShowingCourseList) {
            WakeUpCourseListSheet(appState: appState)
        }
    }

    private func gridItem(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(Color.primary.opacity(0.06))
                        .frame(width: 44, height: 44)

                    Image(systemName: icon)
                        .font(.system(size: 18, weight: .regular))
                        .foregroundColor(.primary)
                }

                Text(label)
                    .font(.system(size: 11, weight: .regular))
                    .foregroundColor(.primary.opacity(0.85))
            }
            .frame(width: 60)
        }
        .buttonStyle(.plain)
    }
}

/// Simple sheet to browse and manage all selected courses for the current semester.
public struct WakeUpCourseListSheet: View {
    @Bindable var appState: AppState
    @Environment(\.dismiss) private var dismiss

    private var currentCourses: [Course] {
        guard let semId = appState.currentSemesterId else { return [] }
        return appState.courses.filter { $0.semesterId == semId }
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("已选课程 (\(currentCourses.count)门)")
                    .font(.headline)
                Spacer()
                Button(action: {
                    dismiss()
                    appState.isAddingCourse = true
                }) {
                    Label("添加课程", systemImage: "plus")
                }
                .buttonStyle(.borderedProminent)

                Button("完成") {
                    dismiss()
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .background(.bar)

            Divider()

            if currentCourses.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "book.closed")
                        .font(.system(size: 40))
                        .foregroundColor(.secondary)
                    Text("当前学期尚未添加任何课程")
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(currentCourses) { c in
                        HStack(spacing: 12) {
                            Circle()
                                .fill(Color(hex: c.color))
                                .frame(width: 12, height: 12)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(c.name)
                                    .font(.system(size: 14, weight: .semibold))

                                HStack(spacing: 8) {
                                    if let teacher = c.teacher, !teacher.isEmpty {
                                        Text("教师: \(teacher)")
                                    }
                                    Text("\(c.schedules.count) 个上课时段")
                                }
                                .font(.caption)
                                .foregroundColor(.secondary)
                            }

                            Spacer()

                            Button(action: {
                                appState.deleteCourse(id: c.id)
                            }) {
                                Image(systemName: "trash")
                                    .foregroundColor(.red)
                            }
                            .buttonStyle(.borderless)
                        }
                        .padding(.vertical, 4)
                    }
                }
                .listStyle(.inset(alternatesRowBackgrounds: true))
            }
        }
        .frame(width: 480, height: 420)
    }
}
