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
    @State private var hasStartDate: Bool = false
    @State private var startDate: Date = Date()
    @State private var hasDueDate: Bool = false
    @State private var dueDate: Date = Calendar.current.date(byAdding: .hour, value: 4, to: Date()) ?? Date()
    @State private var frequency: ScheduleItemCreateSheet.EventFrequency = .none
    @State private var customDaysOfWeek: Set<Int> = [1, 2, 3, 4, 5]
    @State private var customWeeks: Set<Int> = Set(1...16)
    @State private var endRepeatType: String = "never"
    @State private var endRepeatUntil: Date = Calendar.current.date(byAdding: .month, value: 3, to: Date()) ?? Date()
    @State private var endRepeatCount: Int = 10
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

                        Divider()

                        HStack {
                            Toggle("设置开始日期", isOn: $hasStartDate)
                                .toggleStyle(.checkbox)
                                .font(.system(size: 13, weight: .medium))
                            Spacer()
                            if hasStartDate {
                                DatePicker("", selection: $startDate, displayedComponents: .date)
                                    .labelsHidden()
                            }
                        }

                        Divider()

                        HStack {
                            Toggle("设置截止时间", isOn: $hasDueDate)
                                .toggleStyle(.checkbox)
                                .font(.system(size: 13, weight: .medium))
                            Spacer()
                            if hasDueDate {
                                DatePicker("", selection: $dueDate, displayedComponents: [.date, .hourAndMinute])
                                    .labelsHidden()
                            }
                        }

                        Divider()

                        HStack {
                            Text("重复频次")
                                .font(.system(size: 13, weight: .medium))
                            Spacer()
                            Picker("", selection: $frequency) {
                                ForEach(ScheduleItemCreateSheet.EventFrequency.allCases) { freq in
                                    Text(freq.rawValue).tag(freq)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                        }

                        if frequency == .customDays || frequency == .customWeeks {
                            Divider()
                            VStack(alignment: .leading, spacing: 10) {
                                if frequency == .customDays {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text("生效星期")
                                            .font(.system(size: 11))
                                            .foregroundColor(.secondary)
                                        HStack(spacing: 4) {
                                            let dayLabels = [
                                                (7, "日"), (1, "一"), (2, "二"), (3, "三"),
                                                (4, "四"), (5, "五"), (6, "六")
                                            ]
                                            ForEach(dayLabels, id: \.0) { day, name in
                                                let isSelected = customDaysOfWeek.contains(day)
                                                Button(action: {
                                                    if isSelected {
                                                        if customDaysOfWeek.count > 1 {
                                                            customDaysOfWeek.remove(day)
                                                        }
                                                    } else {
                                                        customDaysOfWeek.insert(day)
                                                    }
                                                }) {
                                                    Text(name)
                                                        .font(.system(size: 12, weight: isSelected ? .bold : .regular))
                                                        .frame(maxWidth: .infinity)
                                                        .padding(.vertical, 6)
                                                        .background(
                                                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                                                .fill(isSelected ? Color(nsColor: .selectedControlColor).opacity(0.8) : Color(nsColor: .textBackgroundColor).opacity(0.6))
                                                        )
                                                        .foregroundColor(isSelected ? .white : .primary)
                                                }
                                                .buttonStyle(.plain)
                                                .contentShape(Rectangle())
                                            }
                                        }
                                    }
                                } else if frequency == .customWeeks {
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack {
                                            Text("学期生效周次 (共 \(appState.currentSemester?.totalWeeks ?? 20) 周)")
                                                .font(.system(size: 11))
                                                .foregroundColor(.secondary)
                                            Spacer()
                                            Text("已选 \(customWeeks.count) 周")
                                                .font(.system(size: 11, design: .monospaced))
                                                .foregroundColor(.secondary)
                                        }
                                        WeekMatrixPickerView(selectedWeeks: $customWeeks, maxWeeks: appState.currentSemester?.totalWeeks ?? 20, accentColor: .accentColor)
                                    }
                                }
                            }
                            .padding(.top, 4)
                        }

                        if frequency != .none {
                            Divider()
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    Text("重复终止条件")
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)
                                    Spacer()
                                    Picker("", selection: $endRepeatType) {
                                        Text("从不终止 (一直重复)").tag("never")
                                        Text("按日期终止 (指定最后一天)").tag("untilDate")
                                        Text("按次数终止 (达到次数后)").tag("count")
                                    }
                                    .labelsHidden()
                                    .pickerStyle(.menu)
                                }

                                if endRepeatType == "untilDate" {
                                    HStack {
                                        Text("最后重复日期:")
                                            .font(.system(size: 12))
                                            .foregroundColor(.secondary)
                                        Spacer()
                                        DatePicker("", selection: $endRepeatUntil, displayedComponents: .date)
                                            .labelsHidden()
                                    }
                                } else if endRepeatType == "count" {
                                    HStack {
                                        Text("发生次数:")
                                            .font(.system(size: 12))
                                            .foregroundColor(.secondary)
                                        Spacer()
                                        Stepper("共重复 \(endRepeatCount) 次后停止", value: $endRepeatCount, in: 1...100)
                                            .font(.system(size: 12))
                                    }
                                }
                            }
                            .padding(.top, 4)
                        }
                    }
                    .padding(16)
                    .background(Color(nsColor: .controlBackgroundColor))
                    .cornerRadius(14)
                }
                .padding(20)
            }
        }
        .frame(width: 480, height: 560)
    }

    private func createTask() {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else { return }

        let dfDate = DateFormatter()
        dfDate.dateFormat = "yyyy-MM-dd"
        let dfDue = DateFormatter()
        dfDue.dateFormat = "yyyy-MM-dd HH:mm"

        let startStr = hasStartDate ? dfDate.string(from: startDate) : nil
        let dueStr = hasDueDate ? dfDue.string(from: dueDate) : nil

        let matchedCourse = currentSemesterCourses.first(where: { $0.id == selectedCourseId })
        let task = PomodoroTask(
            semesterId: appState.currentSemester?.id,
            title: trimmedTitle,
            courseId: selectedCourseId,
            courseName: matchedCourse?.name,
            estimatedPomodoros: max(1, (targetMinutes + 24) / 25),
            targetMinutes: targetMinutes,
            priority: priority,
            notes: notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes,
            startDate: startStr,
            dueDate: dueStr,
            frequency: frequency.internalCode,
            customDaysOfWeek: (frequency == .customDays) ? customDaysOfWeek : nil,
            customWeeks: (frequency == .customWeeks) ? customWeeks : nil,
            endRepeatType: (frequency != .none) ? endRepeatType : "never",
            endRepeatUntil: (frequency != .none && endRepeatType == "untilDate") ? dfDate.string(from: endRepeatUntil) : nil,
            endRepeatCount: (frequency != .none && endRepeatType == "count") ? endRepeatCount : nil
        )
        appState.addPomodoroTask(task)
        appState.activePomodoroTaskId = task.id
        onDismiss()
    }
}
