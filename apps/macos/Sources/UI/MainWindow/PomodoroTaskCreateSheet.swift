import SwiftUI
import ClassFlowCore

/// Card-based modal sheet for creating a new Pomodoro ToDo task,
/// with course linking, estimated pomodoro counts, and priority tags.
public struct PomodoroTaskCreateSheet: View {
    @Bindable public var appState: AppState
    public let onDismiss: () -> Void

    @State private var title: String = ""
    @State private var selectedCourseId: UUID? = nil
    @State private var targetMinutes: Int = 45
    @State private var priority: TaskPriority = .medium
    @State private var notes: String = ""

    public init(appState: AppState, onDismiss: @escaping () -> Void) {
        self.appState = appState
        self.onDismiss = onDismiss
    }

    private var currentSemesterCourses: [Course] {
        guard let currentSem = appState.currentSemester else { return appState.courses }
        return appState.courses.filter { $0.semesterId == currentSem.id }
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button("取消", action: onDismiss)
                    .buttonStyle(.plain)
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)

                Spacer()

                Text("新建专注待办")
                    .font(.system(size: 16, weight: .bold))

                Spacer()

                Button("创建") {
                    createTask()
                }
                .buttonStyle(.plain)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? .secondary.opacity(0.5) : .accentColor)
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            ScrollView {
                VStack(spacing: 16) {
                    // 1. Task Title & Course Association Card
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("待办内容")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 72, alignment: .leading)
                            TextField("例如：高等数学 习题册第4章复习", text: $title)
                                .textFieldStyle(.plain)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)

                        Divider().padding(.leading, 16)

                        HStack {
                            Text("关联课程")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 72, alignment: .leading)

                            Picker("", selection: $selectedCourseId) {
                                Text("不关联课程 (自习/通用)").tag(UUID?.none)
                                ForEach(currentSemesterCourses) { course in
                                    Text(course.name).tag(UUID?.some(course.id))
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)

                        Divider().padding(.leading, 16)

                        HStack {
                            Text("任务备注")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 72, alignment: .leading)
                            TextField("例如：重点攻克多元微分例题 (选填)", text: $notes)
                                .textFieldStyle(.plain)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                    }
                    .background(Color(nsColor: .controlBackgroundColor))
                    .cornerRadius(14)

                    // 2. Duration & Priority Card
                    VStack(alignment: .leading, spacing: 14) {
                        HStack(spacing: 6) {
                            Text("预计专注时长")
                                .font(.system(size: 14, weight: .medium))
                            Spacer()
                            TextField("45", value: $targetMinutes, format: .number)
                                .textFieldStyle(.roundedBorder)
                                .frame(width: 52)
                                .multilineTextAlignment(.center)
                                .font(.system(size: 12.5, weight: .semibold, design: .monospaced))
                            Text("分钟")
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)
                            Stepper("", value: $targetMinutes, in: 5...480, step: 15)
                                .labelsHidden()
                        }

                        HStack(spacing: 8) {
                            ForEach([15, 30, 45, 60, 90, 120], id: \.self) { mins in
                                Button(action: {
                                    targetMinutes = mins
                                }) {
                                    Text("\(mins)m")
                                        .font(.system(size: 12, weight: targetMinutes == mins ? .bold : .regular))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(
                                            Capsule()
                                                .fill(targetMinutes == mins ? Color.primary : Color(nsColor: .textBackgroundColor).opacity(0.6))
                                        )
                                        .foregroundColor(targetMinutes == mins ? (appState.settings.themeMode == "dark" ? .black : .white) : .primary)
                                }
                                .buttonStyle(.plain)
                            }
                        }

                        Divider()

                        HStack {
                            Text("优先级")
                                .font(.system(size: 14, weight: .medium))
                            Spacer()
                            Picker("", selection: $priority) {
                                ForEach(TaskPriority.allCases, id: \.self) { p in
                                    Text(p.rawValue).tag(p)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.segmented)
                            .frame(width: 260)
                        }
                    }
                    .padding(16)
                    .background(Color(nsColor: .controlBackgroundColor))
                    .cornerRadius(14)
                }
                .padding(20)
            }
        }
        .frame(width: 480, height: 420)
    }

    private func createTask() {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else { return }

        let matchedCourse = currentSemesterCourses.first(where: { $0.id == selectedCourseId })
        let task = PomodoroTask(
            title: trimmedTitle,
            courseId: selectedCourseId,
            courseName: matchedCourse?.name,
            estimatedPomodoros: max(1, (targetMinutes + 24) / 25),
            targetMinutes: targetMinutes,
            priority: priority,
            notes: notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes
        )
        appState.addPomodoroTask(task)
        appState.activePomodoroTaskId = task.id
        onDismiss()
    }
}
