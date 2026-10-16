import SwiftUI
import ClassFlowCore

/// Comprehensive creation modal for the Schedule tab, modeled after macOS native Calendar.
/// Supports both "日程 (Event)" (with exact start/end time or period range, recurrence, reminders, and tags)
/// and "待办 (Task)" (todos, priorities, and optional focus estimation).
public struct ScheduleItemCreateSheet: View {
    @Bindable public var appState: AppState
    public var onDismiss: () -> Void

    public enum ItemMode: String, CaseIterable, Identifiable {
        case event = "日程"
        case task = "待办"
        public var id: String { rawValue }
    }

    public enum EventTimeMode: String, CaseIterable, Identifiable {
        case bySection = "按作息节次"
        case byTime = "按具体时间"
        public var id: String { rawValue }
    }

    public enum EventFrequency: String, CaseIterable, Identifiable {
        case none = "不重复"
        case daily = "每天"
        case workdays = "工作日 (周一至周五)"
        case customDays = "自定义每周某几天"
        case weekly = "每周"
        case biweekly = "每两周"
        case monthly = "每月"
        case customWeeks = "学期指定周次"
        public var id: String { rawValue }

        public var internalCode: String {
            switch self {
            case .none: return "none"
            case .daily: return "daily"
            case .workdays: return "workdays"
            case .customDays: return "customDays"
            case .weekly: return "weekly"
            case .biweekly: return "biweekly"
            case .monthly: return "monthly"
            case .customWeeks: return "customWeeks"
            }
        }
    }

    public enum AlertOption: String, CaseIterable, Identifiable {
        case none = "无提醒"
        case atTime = "日程开始时"
        case min5 = "提前 5 分钟"
        case min15 = "提前 15 分钟"
        case min30 = "提前 30 分钟"
        case hour1 = "提前 1 小时"
        case day1 = "提前 1 天"
        public var id: String { rawValue }

        public var minutesBefore: Int? {
            switch self {
            case .none: return nil
            case .atTime: return 0
            case .min5: return 5
            case .min15: return 15
            case .min30: return 30
            case .hour1: return 60
            case .day1: return 1440
            }
        }
    }

    @State private var mode: ItemMode = .event

    // MARK: - Event Fields
    @State private var eventTitle: String = ""
    @State private var eventDate: Date = Date()
    @State private var eventEndDate: Date = Date()
    @State private var isAllDay: Bool = false
    @State private var timeMode: EventTimeMode = .bySection
    @State private var startSection: Int = 1
    @State private var endSection: Int = 2
    @State private var startTimeStr: String = "09:00"
    @State private var endTimeStr: String = "10:30"
    @State private var frequency: EventFrequency = .none
    @State private var intervalWeeks: Int = 1
    @State private var customDaysOfWeek: Set<Int> = [1, 2, 3, 4, 5]
    @State private var customWeeks: Set<Int> = Set(1...16)
    @State private var endRepeatType: String = "never" // "never", "untilDate", "count"
    @State private var endRepeatUntil: Date = Calendar.current.date(byAdding: .month, value: 3, to: Date()) ?? Date()
    @State private var endRepeatCount: Int = 10
    @State private var location: String = ""
    @State private var alertOption: AlertOption = .min15
    @State private var eventColor: String = "#FF5A5F"
    @State private var eventNotes: String = ""

    // MARK: - Task Fields
    @State private var taskTitle: String = ""
    @State private var selectedCourseId: UUID? = nil
    @State private var hasDueDate: Bool = true
    @State private var taskDueDate: Date = Date()
    @State private var taskPriority: TaskPriority = .medium
    @State private var targetMinutes: Int = 45
    @State private var taskNotes: String = ""

    private let colors = [
        "#FF5A5F", "#5B7FB5", "#56A3A6", "#E37B75",
        "#8E7DBE", "#E8A87C", "#7BB38E", "#457B9D"
    ]

    private var availableSections: [SectionTime] {
        appState.currentSemester?.sections ?? []
    }

    public init(
        appState: AppState,
        initialMode: ItemMode = .event,
        prefilledDayOfWeek: Int? = nil,
        prefilledSection: Int? = nil,
        onDismiss: @escaping () -> Void
    ) {
        self.appState = appState
        self._mode = State(initialValue: initialMode)
        self.onDismiss = onDismiss

        if let sec = prefilledSection {
            self._startSection = State(initialValue: sec)
            self._endSection = State(initialValue: sec)
            self._timeMode = State(initialValue: .bySection)
        }

        if let dow = prefilledDayOfWeek, let sem = appState.currentSemester {
            let weekDates = appState.timelineEngine.weekCalculator.dateStringsInWeek(
                weekNumber: appState.selectedWeek,
                semesterStartDate: sem.startDate
            )
            if dow - 1 < weekDates.count {
                let df = DateFormatter()
                df.dateFormat = "yyyy-MM-dd"
                if let targetDate = df.date(from: weekDates[dow - 1]) {
                    self._eventDate = State(initialValue: targetDate)
                    self._eventEndDate = State(initialValue: targetDate)
                }
            }
        }
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

                // Mode Picker (日程 vs 待办)
                Picker("", selection: $mode) {
                    ForEach(ItemMode.allCases) { m in
                        Text(m.rawValue).tag(m)
                    }
                }
                .labelsHidden()
                .pickerStyle(.segmented)
                .frame(width: 140)

                Spacer()

                Button("添加", action: handleSave)
                    .buttonStyle(.plain)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(canSave ? .accentColor : .secondary.opacity(0.4))
                    .disabled(!canSave)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            ScrollView {
                VStack(spacing: 16) {
                    if mode == .event {
                        eventFormView
                    } else {
                        taskFormView
                    }
                }
                .padding(20)
            }
        }
        .frame(width: 480, height: 580)
    }

    private var canSave: Bool {
        if mode == .event {
            return !eventTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        } else {
            return !taskTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }

    // MARK: - Event Form (Mac Native Calendar Inspired)

    private var eventFormView: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Title & Color
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    TextField("日程标题 (如：学院例会 / 期中考试)", text: $eventTitle)
                        .textFieldStyle(.plain)
                        .font(.system(size: 15, weight: .medium))

                    Circle()
                        .fill(Color(hex: eventColor))
                        .frame(width: 18, height: 18)
                }
                .padding(14)

                Divider()

                // Color Picker Row
                HStack(spacing: 8) {
                    Text("标记颜色")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    Spacer()
                    CompactColorPickerRow(selectedHex: $eventColor, presets: colors)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)

            // Date & Time Configuration
            VStack(spacing: 0) {
                // Date & All-Day Checkbox on same line (Native macOS Calendar style)
                HStack {
                    Text("开始")
                        .font(.system(size: 13, weight: .medium))
                    DatePicker("", selection: $eventDate, displayedComponents: .date)
                        .labelsHidden()
                    Spacer()
                    Toggle("全天日程", isOn: $isAllDay)
                        .toggleStyle(.checkbox)
                        .font(.system(size: 13))
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)

                Divider()

                HStack {
                    Text("结束")
                        .font(.system(size: 13, weight: .medium))
                    DatePicker("", selection: $eventEndDate, displayedComponents: .date)
                        .labelsHidden()
                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)

                if !isAllDay {
                    Divider()

                    // Time Mode Picker (按作息节次 vs 按具体时间)
                    HStack {
                        Text("时间模式")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                        Spacer()
                        Picker("", selection: $timeMode) {
                            ForEach(EventTimeMode.allCases) { tm in
                                Text(tm.rawValue).tag(tm)
                            }
                        }
                        .labelsHidden()
                        .pickerStyle(.segmented)
                        .frame(width: 170)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)

                    Divider()

                    if timeMode == .bySection {
                        // By Section
                        HStack(spacing: 8) {
                            Text("节次范围")
                                .font(.system(size: 13))
                            Spacer()
                            Picker("", selection: $startSection) {
                                ForEach(availableSections) { s in
                                    Text("第 \(s.section) 节 (\(s.startTime))").tag(s.section)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)

                            Text("至")
                                .font(.caption)
                                .foregroundColor(.secondary)

                            Picker("", selection: $endSection) {
                                ForEach(availableSections.filter { $0.section >= startSection }) { s in
                                    Text("第 \(s.section) 节 (\(s.endTime))").tag(s.section)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                    } else {
                        // By Exact Time
                        HStack(spacing: 8) {
                            Text("具体时间")
                                .font(.system(size: 13))
                            Spacer()
                            TextField("09:00", text: $startTimeStr)
                                .textFieldStyle(.roundedBorder)
                                .frame(width: 72)
                            Text("至")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            TextField("10:30", text: $endTimeStr)
                                .textFieldStyle(.roundedBorder)
                                .frame(width: 72)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                    }
                }
            }
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)

            // Recurrence & Reminder Card (Mac Calendar inspired)
            VStack(spacing: 0) {
                // Frequency
                HStack {
                    Text("重复频次")
                        .font(.system(size: 13, weight: .medium))
                    Spacer()
                    Picker("", selection: $frequency) {
                        ForEach(EventFrequency.allCases) { f in
                            Text(f.rawValue).tag(f)
                        }
                    }
                    .labelsHidden()
                    .pickerStyle(.menu)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)

                if frequency == .customDays || frequency == .weekly || frequency == .biweekly {
                    Divider()
                    VStack(alignment: .leading, spacing: 10) {
                        // 每 N 周 (matching macOS Calendar Image 5)
                        HStack(spacing: 8) {
                            Text("每")
                                .font(.system(size: 13))
                            TextField("1", value: $intervalWeeks, format: .number)
                                .textFieldStyle(.roundedBorder)
                                .frame(width: 44)
                                .multilineTextAlignment(.center)
                            Text("周:")
                                .font(.system(size: 13))
                            Stepper("", value: $intervalWeeks, in: 1...10)
                                .labelsHidden()
                        }

                        // 生效星期 (日..六 segmented strip matching Image 5)
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
                                }
                            }
                        }

                        // 学期生效周次 (进一步自定义周次，彻底解决组合自定义诉求)
                        VStack(alignment: .leading, spacing: 6) {
                            Text("学期生效周次")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                            WeekMatrixPickerView(selectedWeeks: $customWeeks, maxWeeks: 25, accentColor: Color(hex: eventColor))
                        }

                        // 结束重复设置
                        HStack {
                            Text("结束重复")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                            Spacer()
                            Picker("", selection: $endRepeatType) {
                                Text("永不").tag("never")
                                Text("于指定日期").tag("untilDate")
                                Text("达到次数").tag("count")
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                        }

                        if endRepeatType == "untilDate" {
                            HStack {
                                Text("截止日期:")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                                Spacer()
                                DatePicker("", selection: $endRepeatUntil, displayedComponents: .date)
                                    .labelsHidden()
                            }
                        } else if endRepeatType == "count" {
                            HStack {
                                Text("重复次数:")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                                Spacer()
                                Stepper("\(endRepeatCount) 次后结束", value: $endRepeatCount, in: 1...100)
                                    .font(.system(size: 12))
                            }
                        }
                    }
                    .padding(12)
                }

                if frequency == .customWeeks {
                    Divider()
                    VStack(alignment: .leading, spacing: 6) {
                        Text("选择活动周次")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                        WeekMatrixPickerView(selectedWeeks: $customWeeks, maxWeeks: 25, accentColor: Color(hex: eventColor))
                    }
                    .padding(12)
                }

                Divider()

                // Alert Reminder
                HStack {
                    Text("提醒通知")
                        .font(.system(size: 13, weight: .medium))
                    Spacer()
                    Picker("", selection: $alertOption) {
                        ForEach(AlertOption.allCases) { opt in
                            Text(opt.rawValue).tag(opt)
                        }
                    }
                    .labelsHidden()
                    .pickerStyle(.menu)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)

            // Location & Notes
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    Image(systemName: "mappin.and.ellipse")
                        .foregroundColor(.secondary)
                        .font(.system(size: 12))
                    TextField("地点 / 线上会议房间号 (选填)", text: $location)
                        .textFieldStyle(.plain)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)

                Divider()

                HStack(spacing: 8) {
                    Image(systemName: "note.text")
                        .foregroundColor(.secondary)
                        .font(.system(size: 12))
                    TextField("备注说明 (选填)", text: $eventNotes)
                        .textFieldStyle(.plain)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)
        }
    }

    // MARK: - Task Form (Tasks / ToDo)

    private var taskFormView: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Task Title
            VStack(spacing: 0) {
                TextField("待办事项名称 (如：完成编译原理大作业)", text: $taskTitle)
                    .textFieldStyle(.plain)
                    .font(.system(size: 15, weight: .medium))
                    .padding(14)
            }
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)

            // Course Linkage & Priority
            VStack(spacing: 0) {
                HStack {
                    Text("关联课程")
                        .font(.system(size: 13, weight: .medium))
                    Spacer()
                    Picker("", selection: $selectedCourseId) {
                        Text("无关联课程").tag(UUID?.none)
                        ForEach(appState.courses) { c in
                            Text(c.name).tag(UUID?.some(c.id))
                        }
                    }
                    .labelsHidden()
                    .pickerStyle(.menu)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)

                Divider()

                HStack {
                    Text("优先级")
                        .font(.system(size: 13, weight: .medium))
                    Spacer()
                    Picker("", selection: $taskPriority) {
                        Text("普通").tag(TaskPriority.low)
                        Text("重要").tag(TaskPriority.medium)
                        Text("紧急").tag(TaskPriority.high)
                    }
                    .labelsHidden()
                    .pickerStyle(.segmented)
                    .frame(width: 170)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)

                HStack {
                    Toggle("设置截止时间", isOn: $hasDueDate)
                        .toggleStyle(.checkbox)
                        .font(.system(size: 13, weight: .medium))
                    Spacer()
                    if hasDueDate {
                        DatePicker("", selection: $taskDueDate, displayedComponents: [.date, .hourAndMinute])
                            .labelsHidden()
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
            }
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)

            // Focus Estimation (Only shown if study module is enabled)
            if appState.settings.enableStudyModule {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 6) {
                        Text("预计专注时长")
                            .font(.system(size: 13, weight: .medium))
                        Spacer()
                        TextField("45", value: $targetMinutes, format: .number)
                            .textFieldStyle(.roundedBorder)
                            .frame(width: 52)
                            .multilineTextAlignment(.center)
                            .font(.system(size: 12, weight: .semibold, design: .monospaced))
                        Text("分钟")
                            .font(.system(size: 12.5))
                            .foregroundColor(.secondary)
                        Stepper("", value: $targetMinutes, in: 5...480, step: 15)
                            .labelsHidden()
                    }

                    HStack(spacing: 8) {
                        ForEach([15, 30, 45, 60, 90, 120], id: \.self) { mins in
                            Button(action: { targetMinutes = mins }) {
                                Text("\(mins)m")
                                    .font(.system(size: 11.5, weight: targetMinutes == mins ? .bold : .regular))
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 4)
                                    .background(
                                        Capsule()
                                            .fill(targetMinutes == mins ? Color.primary : Color(nsColor: .textBackgroundColor).opacity(0.6))
                                    )
                                    .foregroundColor(targetMinutes == mins ? (appState.settings.themeMode == "dark" ? .black : .white) : .primary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(14)
                .background(Color(nsColor: .controlBackgroundColor))
                .cornerRadius(12)
            }

            // Task Notes
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    Image(systemName: "note.text")
                        .foregroundColor(.secondary)
                        .font(.system(size: 12))
                    TextField("待办详情与备忘说明 (选填)", text: $taskNotes)
                        .textFieldStyle(.plain)
                }
                .padding(14)
            }
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)
        }
    }

    // MARK: - Save Handling

    private func handleSave() {
        if mode == .event {
            saveCalendarEvent()
        } else {
            saveTask()
        }
        onDismiss()
    }

    private func saveCalendarEvent() {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        let datePart = df.string(from: eventDate)

        let resolvedStart: String
        let resolvedEnd: String
        var resolvedStartSec = startSection
        var resolvedEndSec = endSection

        if isAllDay {
            resolvedStart = "\(datePart) 00:00"
            resolvedEnd = "\(datePart) 23:59"
            resolvedStartSec = 1
            resolvedEndSec = max(1, availableSections.count)
        } else if timeMode == .bySection {
            let sTime = availableSections.first(where: { $0.section == startSection })?.startTime ?? "08:00"
            let eTime = availableSections.first(where: { $0.section == endSection })?.endTime ?? "09:35"
            resolvedStart = "\(datePart) \(sTime)"
            resolvedEnd = "\(datePart) \(eTime)"
        } else {
            resolvedStart = "\(datePart) \(startTimeStr)"
            resolvedEnd = "\(datePart) \(endTimeStr)"
            // Dynamically match start & end sections for accurate section-based queries
            let startMatch = availableSections.first(where: { $0.endTime >= startTimeStr })?.section ?? 1
            let endMatch = availableSections.last(where: { $0.startTime <= endTimeStr })?.section ?? startMatch
            resolvedStartSec = min(startMatch, endMatch)
            resolvedEndSec = max(startMatch, endMatch)
        }

        let newEvent = CalendarEvent(
            id: UUID(),
            calendarId: UUID(),
            title: eventTitle.trimmingCharacters(in: .whitespacesAndNewlines),
            startTime: resolvedStart,
            endTime: resolvedEnd,
            isAllDay: isAllDay,
            location: location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : location,
            description: eventNotes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : eventNotes,
            recurrenceRule: nil,
            color: eventColor,
            startSection: resolvedStartSec,
            endSection: resolvedEndSec,
            alertMinutesBefore: alertOption.minutesBefore,
            frequency: frequency.internalCode,
            customWeeks: (frequency == .customWeeks || frequency == .customDays || frequency == .weekly || frequency == .biweekly) ? customWeeks : nil,
            customDaysOfWeek: (frequency == .customDays || frequency == .weekly || frequency == .biweekly) ? customDaysOfWeek : nil,
            startDate: datePart,
            endDate: df.string(from: eventEndDate),
            intervalWeeks: intervalWeeks,
            endRepeatType: endRepeatType,
            endRepeatUntil: df.string(from: endRepeatUntil),
            endRepeatCount: endRepeatCount
        )

        appState.addCalendarEvent(newEvent)
    }

    private func saveTask() {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd HH:mm"
        let dueStr = hasDueDate ? df.string(from: taskDueDate) : nil

        let matchedCourse = appState.courses.first(where: { $0.id == selectedCourseId })
        var fullNotes: String? = nil
        let trimmedNotes = taskNotes.trimmingCharacters(in: .whitespacesAndNewlines)
        if hasDueDate, let due = dueStr {
            let dueText = "截止: \(due)"
            fullNotes = trimmedNotes.isEmpty ? dueText : "\(dueText)\n\(trimmedNotes)"
        } else {
            fullNotes = trimmedNotes.isEmpty ? nil : trimmedNotes
        }

        let newTask = PomodoroTask(
            id: UUID(),
            title: taskTitle.trimmingCharacters(in: .whitespacesAndNewlines),
            courseId: selectedCourseId,
            courseName: matchedCourse?.name,
            estimatedPomodoros: max(1, (targetMinutes + 24) / 25),
            targetMinutes: targetMinutes,
            completedPomodoros: 0,
            totalFocusMinutes: 0,
            isCompleted: false,
            priority: taskPriority,
            notes: fullNotes
        )

        appState.addPomodoroTask(newTask)
    }
}
