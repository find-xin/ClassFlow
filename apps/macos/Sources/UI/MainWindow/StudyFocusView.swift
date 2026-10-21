import SwiftUI
import AppKit
import ClassFlowCore

/// Streamlined Pomodoro ToDo (番茄ToDo) companion view for the "学习" tab.
/// Integrates task-oriented study goals, course-linked ToDos, clean radial clock focus engine
/// (Pomodoro & Count-up), ambient white noise, and focus analytics without clutter.
public struct StudyFocusView: View {
    @Bindable public var appState: AppState

    // 1. Focus Engine State (番茄钟 & 正向计时)
    @State private var focusMode: FocusMode = .pomodoro
    @State private var timerState: TimerPhase = .idle
    @State private var focusDurationMinutes: Int = 25
    @State private var secondsRemaining: Int = 25 * 60
    @State private var countUpSeconds: Int = 0
    @State private var isRunning: Bool = false
    @State private var completedPomodoroCount: Int = 0

    // 3. Task Management Filter & Sheet
    public enum TaskFilter: String, CaseIterable, Identifiable {
        case inProgress = "进行中"
        case all = "全部"
        case completed = "已完成"
        public var id: String { rawValue }
    }
    @State private var taskFilter: TaskFilter = .inProgress
    @State private var isShowingCreateTaskSheet: Bool = false

    // 4. Timer Phases
    public enum TimerPhase: String {
        case idle = "准备就绪"
        case focusing = "专注中"
        case shortBreak = "短休息 (5分钟)"
        case longBreak = "长休息 (15分钟)"
    }

    // 5. Inspirational Quotes
    private let quotes = [
        "自律给自由，专注致远方。",
        "星光不问赶路人，时光不负有心人。",
        "流水不争先，争的是滔滔不绝。",
        "保持热爱，奔赴山海。",
        "所有努力的痕迹，时间都看得见。"
    ]
    @State private var quoteIndex: Int = 0

    // 6. Sub-Tab Switching (专注时钟 vs 统计与热力图)
    public enum StudySubTab: String, CaseIterable, Identifiable {
        case focusTimer = "专注时钟"
        case statistics = "统计与热力图"
        public var id: String { rawValue }
    }
    @State private var selectedSubTab: StudySubTab = .focusTimer

    private let ticker = Timer.publish(every: 1.0, on: .main, in: .common).autoconnect()

    public init(appState: AppState) {
        self.appState = appState
    }

    // MARK: - Computed Properties

    private var activeTask: PomodoroTask? {
        guard let id = appState.activePomodoroTaskId else { return nil }
        return appState.pomodoroTasks.first(where: { $0.id == id })
    }

    private var progress: Double {
        if focusMode == .countUp {
            return 1.0
        }
        let total = Double(focusDurationMinutes * 60)
        return total > 0 ? (total - Double(secondsRemaining)) / total : 0
    }

    private var displayTimeString: String {
        let totalSecs = (focusMode == .countUp) ? countUpSeconds : secondsRemaining
        let m = totalSecs / 60
        let s = totalSecs % 60
        return String(format: "%02d:%02d", m, s)
    }

    private var filteredTasks: [PomodoroTask] {
        let tasks = appState.currentSemesterPomodoroTasks
        switch taskFilter {
        case .inProgress:
            return tasks.filter { !$0.isCompleted }
        case .completed:
            return tasks.filter { $0.isCompleted }
        case .all:
            return tasks
        }
    }

    // MARK: - Body

    public var body: some View {
        VStack(spacing: 0) {
            // Clean Top Bar with Window Controls Spacing
            HStack(alignment: .center, spacing: 14) {
                Picker("", selection: $selectedSubTab) {
                    ForEach(StudySubTab.allCases) { tab in
                        Text(tab.rawValue).tag(tab)
                    }
                }
                .labelsHidden()
                .pickerStyle(.segmented)
                .frame(width: 220)

                Spacer()
            }
            .padding(.leading, 78)
            .padding(.trailing, 20)
            .padding(.top, 14)
            .padding(.bottom, 12)

            // Content Area
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    if selectedSubTab == .statistics {
                        PomodoroStatisticsView(appState: appState)
                    } else {
                        // 1. Focus Arena Card
                        focusArenaCard

                        // 2. Study ToDo Task Management (待办任务清单 - 番茄ToDo核心)
                        focusTaskListCard

                        // 3. Recent Focus Sessions Log (专注记录时间轴)
                        if !appState.pomodoroRecords.isEmpty {
                            recentRecordsCard
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 4)
                .padding(.bottom, 28)
            }
        }
        .sheet(isPresented: $isShowingCreateTaskSheet) {
            PomodoroTaskCreateSheet(appState: appState) {
                isShowingCreateTaskSheet = false
            }
        }
        .onReceive(ticker) { _ in
            if isRunning {
                handleTick()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("org.classflow.startFocusTimer"))) { notif in
            let mins = notif.userInfo?["minutes"] as? Int ?? 25
            focusDurationMinutes = mins
            secondsRemaining = mins * 60
            timerState = .focusing
            isRunning = true
            selectedSubTab = .focusTimer
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("org.classflow.stopFocusTimer"))) { _ in
            isRunning = false
            timerState = .idle
        }
    }

    // MARK: - Focus Arena Card

    // MARK: - Focus Arena Card

    private var focusArenaCard: some View {
        VStack(spacing: 18) {
            arenaTopControls
            radialClockView
            if !isRunning && focusMode == .pomodoro {
                durationPresetsBar
            }
            Text(quotes[quoteIndex])
                .font(.system(size: 12, weight: .medium, design: .serif))
                .foregroundColor(.secondary.opacity(0.85))
                .padding(.vertical, 2)
            actionButtonsBar
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.15), lineWidth: 0.5)
                )
                .shadow(color: Color.black.opacity(0.04), radius: 10, y: 4)
        )
    }

    private var arenaTopControls: some View {
        HStack(spacing: 12) {
            // Mode Switcher
            HStack(spacing: 2) {
                Button(action: {
                    guard !isRunning else { return }
                    focusMode = .pomodoro
                    resetTimerState()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "timer")
                            .font(.system(size: 11))
                        Text("番茄钟")
                            .font(.system(size: 12.5, weight: focusMode == .pomodoro ? .bold : .medium))
                    }
                    .foregroundColor(focusMode == .pomodoro ? .white : .primary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(focusMode == .pomodoro ? Color.primary : Color.clear)
                    )
                }
                .buttonStyle(.plain)

                Button(action: {
                    guard !isRunning else { return }
                    focusMode = .countUp
                    resetTimerState()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "stopwatch")
                            .font(.system(size: 11))
                        Text("正向计时")
                            .font(.system(size: 12.5, weight: focusMode == .countUp ? .bold : .medium))
                    }
                    .foregroundColor(focusMode == .countUp ? .white : .primary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(focusMode == .countUp ? Color.primary : Color.clear)
                    )
                }
                .buttonStyle(.plain)
            }
            .padding(3)
            .background(Color(nsColor: .textBackgroundColor).opacity(0.6))
            .clipShape(Capsule())

            Spacer()

            // Active Goal Pill Selector
            Menu {
                Button("自由专注 (不绑定任务)") {
                    appState.activePomodoroTaskId = nil
                }
                Divider()
                ForEach(appState.currentSemesterPomodoroTasks.filter { !$0.isCompleted }) { task in
                    Button(task.title) {
                        appState.activePomodoroTaskId = task.id
                    }
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "target")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.accentColor)

                    if let task = activeTask {
                        Text("目标：\(task.title)")
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundColor(.primary)
                            .lineLimit(1)

                        if let cName = task.courseName {
                            Text(cName)
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.accentColor)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1.5)
                                .background(Color.accentColor.opacity(0.12))
                                .clipShape(Capsule())
                        }

                        HStack(spacing: 3) {
                            Image(systemName: "clock")
                                .font(.system(size: 10))
                            Text("\(task.totalFocusMinutes)/\(task.effectiveTargetMinutes)m")
                                .font(.system(size: 11, weight: .medium, design: .monospaced))
                        }
                        .foregroundColor(.secondary)
                    } else {
                        Text("点击关联目标待办...")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.secondary)
                    }

                    Image(systemName: "chevron.down")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 5)
                .background(Color(nsColor: .textBackgroundColor).opacity(0.7))
                .clipShape(Capsule())
            }
            .menuStyle(.borderlessButton)
            .fixedSize()

            // Phase Badge
            Text(timerState.rawValue)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(timerPhaseColor)
                .padding(.horizontal, 9)
                .padding(.vertical, 4)
                .background(timerPhaseColor.opacity(0.12))
        }
    }

    private var durationPresetsBar: some View {
        HStack(spacing: 8) {
            let presets = [15, 25, 45, 60]
            ForEach(presets, id: \.self) { mins in
                Button(action: {
                    focusDurationMinutes = mins
                    secondsRemaining = mins * 60
                }) {
                    Text("\(mins)m")
                        .font(.system(size: 12, weight: focusDurationMinutes == mins ? .bold : .medium))
                        .foregroundColor(focusDurationMinutes == mins ? .white : .primary)
                        .padding(.horizontal, 11)
                        .padding(.vertical, 5)
                        .background(
                            Capsule()
                                .fill(focusDurationMinutes == mins ? Color.primary : Color(nsColor: .controlBackgroundColor))
                        )
                }
                .buttonStyle(.plain)
            }

            // Direct input textfield for custom duration
            HStack(spacing: 3) {
                TextField("45", value: $focusDurationMinutes, format: .number)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .frame(width: 38)
                    .multilineTextAlignment(.center)
                    .padding(.vertical, 4)
                    .padding(.horizontal, 4)
                    .background(
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Color.primary.opacity(0.06))
                    )
                    .onSubmit {
                        focusDurationMinutes = max(1, min(360, focusDurationMinutes))
                        secondsRemaining = focusDurationMinutes * 60
                    }

                Text("m")
                    .font(.system(size: 11.5, weight: .medium))
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal, 4)
        }
    }

    private var actionButtonsBar: some View {
        HStack(spacing: 16) {
            // Start / Pause
            Button(action: toggleTimer) {
                HStack(spacing: 8) {
                    Image(systemName: isRunning ? "pause.fill" : "play.fill")
                        .font(.system(size: 14))
                    Text(isRunning ? "暂停专注" : "开始专注")
                        .font(.system(size: 15, weight: .bold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 32)
                .padding(.vertical, 11)
                .background(
                    LinearGradient(
                        colors: [Color.accentColor, Color.accentColor.opacity(0.85)],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .clipShape(Capsule())
                .shadow(color: Color.black.opacity(0.12), radius: 6, y: 3)
            }
            .buttonStyle(.plain)

            // Harvest Pomodoro Early
            if isRunning || (focusMode == .countUp && countUpSeconds >= 60) || (focusMode == .pomodoro && secondsRemaining < focusDurationMinutes * 60) {
                Button(action: harvestPomodoro) {
                    HStack(spacing: 5) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 13))
                        Text("结算")
                            .font(.system(size: 13, weight: .bold))
                    }
                    .foregroundColor(.primary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 9)
                    .background(
                        Capsule()
                            .fill(.ultraThinMaterial)
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 0.5))
                    )
                }
                .buttonStyle(.plain)
                .help("提前结算并记录本次专注成果")

                // Reset
                Button(action: resetTimerState) {
                    Image(systemName: "arrow.counterclockwise")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.secondary)
                        .frame(width: 36, height: 36)
                        .background(Color(nsColor: .textBackgroundColor).opacity(0.6))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .help("重置当前专注")
            }
        }
    }

    // MARK: - Clock Subview (Radial)

    private var radialClockView: some View {
        ZStack {
            // Background track
            Circle()
                .stroke(Color.primary.opacity(0.06), lineWidth: 14)
                .frame(width: 220, height: 220)

            // Dynamic progress ring
            Circle()
                .trim(from: 0, to: CGFloat(progress))
                .stroke(
                    LinearGradient(
                        colors: [Color.accentColor, Color.accentColor.opacity(0.7)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    style: StrokeStyle(lineWidth: 14, lineCap: .round)
                )
                .frame(width: 220, height: 220)
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 1.0), value: progress)

            // Inner Time display
            VStack(spacing: 4) {
                Text(displayTimeString)
                    .font(.system(size: 46, weight: .bold, design: .monospaced))
                    .foregroundColor(.primary)

                Text(focusMode == .countUp ? "正向心流" : (isRunning ? "保持专注..." : "准备就绪"))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 8)
    }

    // MARK: - Task List Card

    private var focusTaskListCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("待办清单")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.primary)
                }

                Spacer()

                // Filter Segment
                Picker("", selection: $taskFilter) {
                    ForEach(TaskFilter.allCases) { f in
                        Text(f.rawValue).tag(f)
                    }
                }
                .labelsHidden()
                .pickerStyle(.segmented)
                .frame(width: 190)

                // Add Task Button (+)
                Button(action: {
                    isShowingCreateTaskSheet = true
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                        Text("新建待办")
                    }
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.primary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(.ultraThinMaterial)
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 0.5))
                    )
                }
                .buttonStyle(.plain)
            }

            if filteredTasks.isEmpty {
                VStack(spacing: 10) {
                    Image(systemName: "checkmark.circle")
                        .font(.system(size: 32))
                        .foregroundColor(.secondary.opacity(0.5))
                    Text(taskFilter == .completed ? "暂无已完成待办" : "暂无待办任务，点击上方新建添加")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
            } else {
                VStack(spacing: 10) {
                    ForEach(filteredTasks) { task in
                        taskRowView(task: task)
                    }
                }
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.15), lineWidth: 0.5)
                )
                .shadow(color: Color.black.opacity(0.03), radius: 6, y: 2)
        )
    }

    private func taskRowView(task: PomodoroTask) -> some View {
        HStack(spacing: 12) {
            // Checkbox
            Button(action: {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                    appState.toggleTaskCompletion(id: task.id)
                }
            }) {
                Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 20))
                    .foregroundColor(task.isCompleted ? Color(hex: "#56A3A6") : .secondary.opacity(0.6))
            }
            .buttonStyle(.plain)

            // Title & Info
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 8) {
                    Text(task.title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(task.isCompleted ? .secondary : .primary)
                        .strikethrough(task.isCompleted, color: .secondary.opacity(0.6))

                    if let cName = task.courseName {
                        Text(cName)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.accentColor)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.accentColor.opacity(0.12))
                            .clipShape(Capsule())
                    }

                    // Priority Pill
                    Text(task.priority.rawValue)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(Color(hex: task.priority.colorHex))
                        .padding(.horizontal, 5)
                        .padding(.vertical, 1)
                        .background(Color(hex: task.priority.colorHex).opacity(0.12))
                        .clipShape(Capsule())

                    if let d = task.dueDate, !d.isEmpty {
                        HStack(spacing: 3) {
                            Image(systemName: "calendar.badge.clock")
                                .font(.system(size: 9))
                            Text(d)
                                .font(.system(size: 9, design: .monospaced))
                        }
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 1)
                        .background(Color.primary.opacity(0.05))
                        .clipShape(Capsule())
                    }

                    if let f = task.frequency, f != "none" {
                        let label: String = {
                            switch f {
                            case "daily": return "每天"
                            case "workdays": return "工作日"
                            case "weekly": return "每周"
                            case "monthly": return "每月"
                            default: return f
                            }
                        }()
                        Text(label)
                            .font(.system(size: 9))
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .background(Color.primary.opacity(0.05))
                            .clipShape(Capsule())
                    }
                }

                if let n = task.notes, !n.isEmpty {
                    Text(n)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            // Focus Progress / Target Duration Badge
            HStack(spacing: 4) {
                Image(systemName: "clock")
                    .font(.system(size: 11))
                Text("\(task.totalFocusMinutes)/\(task.effectiveTargetMinutes)m")
                    .font(.system(size: 11.5, weight: .medium, design: .monospaced))
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Color.primary.opacity(0.06))
            .clipShape(Capsule())

            // Select as active task button / start focus button
            if !task.isCompleted {
                Button(action: {
                    appState.startFocusForTask(task)
                    if !isRunning {
                        toggleTimer()
                    }
                }) {
                    HStack(spacing: 3) {
                        Image(systemName: appState.activePomodoroTaskId == task.id && isRunning ? "timer" : "play.fill")
                            .font(.system(size: 9))
                        Text(appState.activePomodoroTaskId == task.id && isRunning ? "计时中" : "开始")
                            .font(.system(size: 11, weight: .medium))
                    }
                    .foregroundColor(appState.activePomodoroTaskId == task.id ? .white : .primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(appState.activePomodoroTaskId == task.id ? Color.accentColor : Color.primary.opacity(0.08))
                    )
                }
                .buttonStyle(.plain)
            }

            // Delete Task
            Button(role: .destructive, action: {
                appState.deletePomodoroTask(id: task.id)
            }) {
                Image(systemName: "trash")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary.opacity(0.5))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(nsColor: .textBackgroundColor).opacity(0.5))
        )
    }

    // MARK: - Recent Records Card

    private var recentRecordsCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Text("最近专注记录")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.primary)
                Spacer()
                Text("共记录 \(appState.pomodoroRecords.count) 次专注")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)

                if !appState.pomodoroRecords.isEmpty {
                    Button(role: .destructive) {
                        appState.clearPomodoroRecords()
                    } label: {
                        Text("清空历史")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.red.opacity(0.85))
                    }
                    .buttonStyle(.plain)
                }
            }

            VStack(spacing: 8) {
                ForEach(appState.pomodoroRecords.prefix(5)) { rec in
                    focusRecordRow(record: rec)
                }
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.15), lineWidth: 0.5)
                )
                .shadow(color: Color.black.opacity(0.03), radius: 6, y: 2)
        )
    }

    private func focusRecordRow(record: PomodoroRecord) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "clock.badge.checkmark.fill")
                .font(.system(size: 14))
                .foregroundColor(.accentColor)

            VStack(alignment: .leading, spacing: 2) {
                Text(record.taskTitle)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.primary)

                if let cName = record.courseName {
                    Text(cName)
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            Text(record.focusMode.rawValue)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.secondary)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.primary.opacity(0.06))
                .clipShape(Capsule())

            Text("\(record.durationMinutes) 分钟")
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundColor(.primary)

            let df = DateFormatter()
            let _ = (df.dateFormat = "HH:mm")
            Text(df.string(from: record.completedAt))
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(.secondary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color(nsColor: .textBackgroundColor).opacity(0.4))
        .cornerRadius(10)
    }

    private var timerPhaseColor: Color {
        switch timerState {
        case .focusing: return .accentColor
        case .shortBreak: return Color(hex: "#56A3A6")
        case .longBreak: return Color(hex: "#8E7DBE")
        case .idle: return .secondary
        }
    }

    // MARK: - Timer Logic

    private func toggleTimer() {
        isRunning.toggle()
        if isRunning {
            if timerState == .idle {
                timerState = .focusing
            }
        }
    }

    private func resetTimerState() {
        isRunning = false
        timerState = .idle
        secondsRemaining = focusDurationMinutes * 60
        countUpSeconds = 0
    }

    private func handleTick() {
        guard isRunning else { return }

        if Int.random(in: 0...60) == 0 {
            quoteIndex = (quoteIndex + 1) % quotes.count
        }

        if focusMode == .countUp {
            countUpSeconds += 1
        } else {
            if secondsRemaining > 0 {
                secondsRemaining -= 1
            } else {
                handleTimerCompletion()
            }
        }
    }

    private func handleTimerCompletion() {
        NSSound(named: "Hero")?.play()

        if timerState == .focusing {
            harvestPomodoro()
            completedPomodoroCount += 1

            if completedPomodoroCount % 4 == 0 {
                timerState = .longBreak
                focusDurationMinutes = 15
                secondsRemaining = 15 * 60
            } else {
                timerState = .shortBreak
                focusDurationMinutes = 5
                secondsRemaining = 5 * 60
            }
            isRunning = false
        } else {
            timerState = .idle
            focusDurationMinutes = 25
            secondsRemaining = 25 * 60
            isRunning = false
        }
    }

    private func harvestPomodoro() {
        let actualMinutes: Int
        if focusMode == .countUp {
            actualMinutes = max(1, countUpSeconds / 60)
        } else {
            actualMinutes = max(1, (focusDurationMinutes * 60 - secondsRemaining) / 60)
        }

        let taskTitle = activeTask?.title ?? "自由心流专注"
        let courseName = activeTask?.courseName
        appState.recordCompletedPomodoro(
            taskId: activeTask?.id,
            taskTitle: taskTitle,
            courseName: courseName,
            focusMode: focusMode,
            durationMinutes: actualMinutes
        )

        secondsRemaining = focusDurationMinutes * 60
        countUpSeconds = 0
        isRunning = false
        timerState = .idle
    }
}
