import SwiftUI
import ClassFlowCore

/// Comprehensive Timetable / Semester Management Sheet for macOS.
/// Supports switching active timetable, editing semester information,
/// customizing section times, creating new timetables, and deleting timetables with safe guards.
public struct SemesterManageSheet: View {
    @Bindable var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var editingSemester: Semester? = nil
    @State private var semesterToDelete: Semester? = nil
    @State private var isShowingDeleteConfirm: Bool = false

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header Bar
            HStack {
                Text("课表管理")
                    .font(.system(size: 16, weight: .bold))

                Spacer()

                Button(action: {
                    appState.isShowingCreateSemester = true
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                        Text("新建课表")
                    }
                    .font(.system(size: 12, weight: .semibold))
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

            // List of Semesters
            List {
                ForEach(appState.semesters) { sem in
                    let isCurrent = (sem.id == appState.currentSemesterId)
                    let courseCount = appState.courses.filter { $0.semesterId == sem.id }.count

                    HStack(alignment: .center, spacing: 12) {
                        Image(systemName: isCurrent ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 18))
                            .foregroundColor(isCurrent ? Color(hex: "#FF5A5F") : .secondary)

                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Text(sem.name)
                                    .font(.system(size: 14, weight: .semibold))

                                if isCurrent {
                                    Text("当前主课表")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(Color(hex: "#FF5A5F"))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color(hex: "#FF5A5F").opacity(0.12))
                                        .clipShape(Capsule())
                                }
                            }

                            HStack(spacing: 8) {
                                Text("开学: \(sem.startDate)")
                                Text("•")
                                Text("共 \(sem.totalWeeks) 周")
                                Text("•")
                                Text("\(courseCount) 门课程")
                                Text("•")
                                Text("\(sem.sections.count) 节/天")
                            }
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                        }

                        Spacer()

                        // Action buttons
                        HStack(spacing: 6) {
                            if !isCurrent {
                                Button("设为主课表") {
                                    appState.selectSemester(id: sem.id)
                                }
                                .buttonStyle(.bordered)
                                .font(.system(size: 11))
                            }

                            Button("上课时间") {
                                appState.editingSemesterForSections = sem
                                appState.isShowingEditSectionTimes = true
                            }
                            .buttonStyle(.bordered)
                            .font(.system(size: 11))

                            Button(action: {
                                editingSemester = sem
                            }) {
                                Image(systemName: "pencil")
                            }
                            .buttonStyle(.bordered)
                            .help("编辑基本信息")

                            Button(action: {
                                semesterToDelete = sem
                                isShowingDeleteConfirm = true
                            }) {
                                Image(systemName: "trash")
                                    .foregroundColor(appState.semesters.count <= 1 ? .secondary.opacity(0.4) : .red)
                            }
                            .buttonStyle(.bordered)
                            .disabled(appState.semesters.count <= 1)
                            .help(appState.semesters.count <= 1 ? "至少保留一个课表" : "删除课表")
                        }
                    }
                    .padding(.vertical, 6)
                }
            }
            .listStyle(.inset(alternatesRowBackgrounds: true))

            Divider()

            // Footer info
            HStack {
                Text("共 \(appState.semesters.count) 个课表。点击「设为主课表」即可在主视图显示对应学期课程。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Button("关闭") {
                    dismiss()
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .background(.bar)
        }
        .frame(width: 600, height: 440)
        .sheet(item: $editingSemester) { sem in
            SemesterEditInlineSheet(semester: sem, appState: appState)
        }
        .confirmationDialog(
            "确定要删除课表「\(semesterToDelete?.name ?? "")」吗？",
            isPresented: $isShowingDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button("删除课表及其全部课程", role: .destructive) {
                if let sem = semesterToDelete {
                    appState.deleteSemester(id: sem.id)
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("该课表下的所有课程安排与调课记录都将被移除，此操作不可撤销。")
        }
    }
}

/// Inline sheet to edit name, start date, and total weeks of an existing semester.
private struct SemesterEditInlineSheet: View {
    @Bindable var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var semesterId: UUID
    @State private var name: String
    @State private var startDate: Date
    @State private var totalWeeks: Int

    init(semester: Semester, appState: AppState) {
        self.appState = appState
        self._semesterId = State(initialValue: semester.id)
        self._name = State(initialValue: semester.name)
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        self._startDate = State(initialValue: df.date(from: semester.startDate) ?? Date())
        self._totalWeeks = State(initialValue: semester.totalWeeks)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("编辑课表信息")
                    .font(.headline)
                Spacer()
                Button("取消") { dismiss() }
            }
            .padding()
            .background(.bar)

            Divider()

            Form {
                TextField("课表名称", text: $name)
                    .textFieldStyle(.roundedBorder)

                DatePicker("开学日期 (第一周周一)", selection: $startDate, displayedComponents: [.date])

                Stepper("总教学周数: \(totalWeeks) 周", value: $totalWeeks, in: 1...35)
            }
            .formStyle(.grouped)
            .padding()

            Divider()

            HStack {
                Spacer()
                Button("取消") { dismiss() }
                Button("保存修改") {
                    saveChanges()
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            .background(.bar)
        }
        .frame(width: 420, height: 280)
    }

    private func saveChanges() {
        guard let idx = appState.semesters.firstIndex(where: { $0.id == semesterId }) else { return }
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        let dateStr = df.string(from: startDate)

        var sem = appState.semesters[idx]
        sem.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        sem.startDate = dateStr
        sem.totalWeeks = totalWeeks
        appState.updateSemester(sem)
        dismiss()
    }
}
