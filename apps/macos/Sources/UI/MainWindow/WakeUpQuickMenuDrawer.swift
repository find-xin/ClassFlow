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
                    appState.isShowingSectionProfiles = true
                }

                gridItem(icon: "slider.horizontal.3", label: "课表设置") {
                    isPresented = false
                    appState.isShowingSettings = true
                }

                gridItem(icon: "archivebox", label: "已选课程") {
                    isShowingCourseList = true
                }
            }

            // Row 2
            HStack(spacing: 24) {
                gridItem(icon: "paintbrush", label: "课表外观") {
                    isPresented = false
                    appState.isShowingAppearance = true
                }

                gridItem(icon: "iphone", label: "小组件") {
                    isPresented = false
                    appState.isShowingWidgets = true
                }

                gridItem(icon: appState.settings.themeMode == "dark" ? "sun.max" : "moon", label: "夜间模式") {
                    if appState.settings.themeMode == "dark" {
                        appState.settings.themeMode = "light"
                    } else {
                        appState.settings.themeMode = "dark"
                    }
                    appState.save()
                }
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 18)
        .frame(width: 300)
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

/// Polished sheet to browse, search, inspect, and manage all courses in the current semester.
public struct WakeUpCourseListSheet: View {
    @Bindable var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var searchText: String = ""
    @State private var courseToDelete: Course? = nil
    @State private var isShowingDeleteConfirm: Bool = false

    private var currentCourses: [Course] {
        guard let semId = appState.currentSemesterId else { return [] }
        let all = appState.courses.filter { $0.semesterId == semId }
        if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return all
        }
        let q = searchText.lowercased()
        return all.filter {
            $0.name.lowercased().contains(q) ||
            ($0.teacher?.lowercased().contains(q) ?? false) ||
            $0.schedules.contains { ($0.classroom?.lowercased().contains(q) ?? false) }
        }
    }

    private let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]

    public var body: some View {
        VStack(spacing: 0) {
            // 1. Navigation Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("已选课程")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.primary)

                    Text("当前学期共 \(appState.courses.filter { $0.semesterId == appState.currentSemesterId }.count) 门课程")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }

                Spacer()

                Button(action: {
                    dismiss()
                    appState.isAddingCourse = true
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                        Text("添加课程")
                    }
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color(hex: "#FF5A5F"))
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)

                Button("完成") {
                    dismiss()
                }
                .buttonStyle(.plain)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.secondary)
                .padding(.leading, 8)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            // 2. Search Bar
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)
                    .font(.system(size: 13))

                TextField("搜索课程名称、授课教师、教室...", text: $searchText)
                    .textFieldStyle(.plain)
                    .font(.system(size: 13))

                if !searchText.isEmpty {
                    Button(action: { searchText = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                            .font(.system(size: 13))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(10)
            .padding(.horizontal, 20)
            .padding(.top, 14)
            .padding(.bottom, 6)

            // 3. Course Cards List
            if currentCourses.isEmpty {
                VStack(spacing: 14) {
                    Spacer()
                    Image(systemName: "books.vertical")
                        .font(.system(size: 44))
                        .foregroundColor(.secondary.opacity(0.6))

                    Text(searchText.isEmpty ? "当前学期尚未添加任何课程" : "未找到匹配的课程")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.secondary)

                    if searchText.isEmpty {
                        Button(action: {
                            dismiss()
                            appState.isAddingCourse = true
                        }) {
                            Text("立即添加第一门课程")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(Color(hex: "#FF5A5F"))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(spacing: 12) {
                        ForEach(currentCourses) { c in
                            courseCardRow(c)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                }
            }
        }
        .frame(width: 520, height: 560)
        .confirmationDialog(
            "确定要删除这门课程吗？",
            isPresented: $isShowingDeleteConfirm,
            presenting: courseToDelete
        ) { target in
            Button("删除 \(target.name)", role: .destructive) {
                appState.deleteCourse(id: target.id)
                courseToDelete = nil
            }
            Button("取消", role: .cancel) {
                courseToDelete = nil
            }
        } message: { target in
            Text("删除后将移除该课程及其所有上课时段。此操作无法撤销。")
        }
    }

    private func courseCardRow(_ c: Course) -> some View {
        HStack(alignment: .top, spacing: 12) {
            // Color indicator capsule
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(hex: c.color, defaultColor: Color(hex: "#5B7FB5")))
                .frame(width: 5, height: 50)
                .padding(.top, 2)

            // Info column
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Text(c.name)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.primary)

                    if let notes = c.notes, !notes.isEmpty {
                        Text(notes)
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.primary.opacity(0.06))
                            .clipShape(Capsule())
                            .lineLimit(1)
                    }
                }

                HStack(spacing: 10) {
                    if let teacher = c.teacher, !teacher.isEmpty {
                        Label(teacher, systemImage: "person")
                            .font(.system(size: 11.5))
                            .foregroundColor(.secondary)
                    }

                    Text("\(c.schedules.count) 个上课时段")
                        .font(.system(size: 11.5))
                        .foregroundColor(.secondary.opacity(0.85))
                }

                // Schedule details pills
                VStack(alignment: .leading, spacing: 3) {
                    ForEach(c.schedules) { s in
                        let dayName = (s.dayOfWeek >= 1 && s.dayOfWeek <= 7) ? dayNames[s.dayOfWeek - 1] : "周\(s.dayOfWeek)"
                        let room = (s.classroom?.isEmpty ?? true) ? "未指定教室" : "@\(s.classroom ?? "")"

                        HStack(spacing: 6) {
                            Text("\(dayName) 第\(s.startSection)-\(s.endSection)节")
                                .font(.system(size: 11, weight: .medium, design: .monospaced))
                                .foregroundColor(.primary.opacity(0.8))

                            Text("•")
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)

                            Text(room)
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .padding(.top, 2)
            }

            Spacer()

            // Actions
            HStack(spacing: 8) {
                Button(action: {
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                        appState.editingCourse = c
                    }
                }) {
                    Image(systemName: "pencil")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.secondary)
                        .frame(width: 28, height: 28)
                        .background(Color.primary.opacity(0.05))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .help("编辑整门课程与时段")

                Button(action: {
                    courseToDelete = c
                    isShowingDeleteConfirm = true
                }) {
                    Image(systemName: "trash")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.red.opacity(0.85))
                        .frame(width: 28, height: 28)
                        .background(Color.red.opacity(0.08))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .help("删除课程")
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor))
                .shadow(color: Color.black.opacity(0.04), radius: 4, y: 2)
        )
    }
}
