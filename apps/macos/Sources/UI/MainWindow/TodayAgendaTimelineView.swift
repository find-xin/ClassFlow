import SwiftUI
import ClassFlowCore

/// Dedicated agenda timeline view for the "日程" tab, designed after macOS native Calendar.
/// Showcases today's schedule, next class countdown hero card, integrated course timetable items,
/// and daily study tasks/todos with instant one-click focus linkage.
public struct TodayAgendaTimelineView: View {
    @Bindable public var appState: AppState

    @State private var selectedDayOfWeek: Int = Calendar.current.component(.weekday, from: Date()) == 1 ? 7 : (Calendar.current.component(.weekday, from: Date()) - 1)
    @State private var isShowingCreateTaskSheet: Bool = false
    @State private var isShowingWeekPicker: Bool = false
    @State private var taskFilter: TaskFilter = .inProgress

    public enum TaskFilter: String, CaseIterable, Identifiable {
        case inProgress = "待办中"
        case all = "全部"
        case completed = "已完成"
        public var id: String { rawValue }
    }

    private let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]

    public init(appState: AppState) {
        self.appState = appState
    }

    // MARK: - Computed Properties

    private var weekDates: [String] {
        guard let current = appState.currentSemester else { return [] }
        return appState.timelineEngineWeekCalculator.dateStringsInWeek(
            weekNumber: appState.selectedWeek,
            semesterStartDate: current.startDate
        )
    }

    private func dayDateLabel(_ day: Int) -> String {
        guard day >= 1 && day <= weekDates.count else { return "" }
        let dateStr = weekDates[day - 1]
        let parts = dateStr.split(separator: "-")
        guard parts.count == 3 else { return "" }
        return "\(parts[1])/\(parts[2])"
    }

    private var currentWeekItems: [TimelineItem] {
        appState.currentWeekOccurrences
    }

    private var selectedDayItems: [TimelineItem] {
        currentWeekItems
            .filter { $0.dayOfWeek == selectedDayOfWeek }
            .sorted { $0.startSection < $1.startSection }
    }

    private var todayWeekday: Int {
        let weekday = Calendar.current.component(.weekday, from: Date())
        return weekday == 1 ? 7 : (weekday - 1)
    }

    private static let titleDateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.locale = Locale(identifier: "zh_CN")
        df.dateFormat = "yyyy年M月d日 EEEE"
        return df
    }()

    private var formattedDateTitle: String {
        Self.titleDateFormatter.string(from: Date())
    }

    private var nextUpcomingItem: TimelineItem? {
        let todayItems = currentWeekItems.filter { $0.dayOfWeek == todayWeekday && $0.isCurrentWeek }
        let now = Date()
        let cal = Calendar.current
        let currentMinutes = cal.component(.hour, from: now) * 60 + cal.component(.minute, from: now)

        for item in todayItems.sorted(by: { $0.startSection < $1.startSection }) {
            if let start = item.startTime {
                let parts = start.split(separator: ":")
                if parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]) {
                    let itemMinutes = h * 60 + m
                    if itemMinutes >= currentMinutes - 20 {
                        return item
                    }
                }
            }
        }
        return todayItems.first
    }

    private var filteredTasks: [PomodoroTask] {
        switch taskFilter {
        case .inProgress:
            return appState.pomodoroTasks.filter { !$0.isCompleted }
        case .completed:
            return appState.pomodoroTasks.filter { $0.isCompleted }
        case .all:
            return appState.pomodoroTasks
        }
    }

    // MARK: - Body

    public var body: some View {
        VStack(spacing: 0) {
            // 1. Top Bar: Date title, Day Pager & New Task Action (Window Controls Safe)
            topAgendaHeader

            // 2. Scrollable Agenda Content
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 20) {
                    // Next Upcoming Class Hero Card
                    if let hero = nextUpcomingItem {
                        nextClassHeroCard(hero)
                    } else {
                        noUpcomingHeroCard
                    }

                    // Integrated Sections: Schedule Courses & Tasks
                    HStack(alignment: .top, spacing: 18) {
                        // Left Column: Course Timetable for Selected Day
                        dayCourseScheduleColumn
                            .frame(maxWidth: .infinity)

                        // Right Column: Study ToDos & Tasks for Today
                        dayTasksColumn
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 4)
                .padding(.bottom, 28)
            }
        }
        .sheet(isPresented: $isShowingCreateTaskSheet) {
            ScheduleItemCreateSheet(appState: appState) {
                isShowingCreateTaskSheet = false
            }
        }
    }

    // MARK: - Top Header (macOS Calendar-Style Week & Date Switcher)

    private var topAgendaHeader: some View {
        HStack(alignment: .center, spacing: 14) {
            // Left: Date Title
            Text(formattedDateTitle)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.primary)

            // Week Switcher with Popover
            HStack(spacing: 4) {
                Button(action: { appState.previousWeek() }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(appState.selectedWeek > 1 ? .primary : .secondary.opacity(0.4))
                }
                .buttonStyle(.plain)
                .disabled(appState.selectedWeek <= 1)

                Button(action: { isShowingWeekPicker.toggle() }) {
                    HStack(spacing: 3) {
                        Text("第 \(appState.selectedWeek) 周")
                            .font(.system(size: 12, weight: .bold))
                        Image(systemName: "chevron.down")
                            .font(.system(size: 8, weight: .semibold))
                    }
                    .foregroundColor(.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4.5)
                    .background(
                        Capsule()
                            .fill(.ultraThinMaterial)
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 0.5))
                    )
                }
                .buttonStyle(.plain)
                .popover(isPresented: $isShowingWeekPicker) {
                    weekPickerPopover
                }

                Button(action: { appState.nextWeek() }) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(appState.selectedWeek < (appState.currentSemester?.totalWeeks ?? 20) ? .primary : .secondary.opacity(0.4))
                }
                .buttonStyle(.plain)
                .disabled(appState.selectedWeek >= (appState.currentSemester?.totalWeeks ?? 20))
            }

            Spacer()

            // Center: Day Selector Pills (周一 ~ 周日 with Date badge)
            HStack(spacing: 3) {
                ForEach(1...7, id: \.self) { day in
                    let isSelected = (day == selectedDayOfWeek)
                    let isToday = (day == todayWeekday && appState.isViewingCurrentWeek)
                    let dateLabel = dayDateLabel(day)

                    Button(action: {
                        selectedDayOfWeek = day
                    }) {
                        VStack(spacing: 1) {
                            Text(dayNames[day - 1])
                                .font(.system(size: 11.5, weight: isSelected ? .bold : .medium))

                            if !dateLabel.isEmpty {
                                Text(dateLabel)
                                    .font(.system(size: 9, weight: isSelected ? .semibold : .regular, design: .monospaced))
                                    .opacity(isSelected ? 0.9 : 0.6)
                            }
                        }
                        .foregroundColor(isSelected ? .primary : (isToday ? .primary : .secondary.opacity(0.85)))
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4.5)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(isSelected ? Color.white.opacity(0.22) : (isToday ? Color.white.opacity(0.08) : Color.clear))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                        .strokeBorder(isSelected ? Color.white.opacity(0.3) : (isToday ? Color.white.opacity(0.2) : Color.clear), lineWidth: 0.5)
                                )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(3)
            .background(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .overlay(
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .strokeBorder(Color.white.opacity(0.18), lineWidth: 0.5)
                    )
            )

            // Today Button
            if selectedDayOfWeek != todayWeekday || !appState.isViewingCurrentWeek {
                Button(action: {
                    appState.jumpToCurrentWeek()
                    selectedDayOfWeek = todayWeekday
                }) {
                    Text("回到今日")
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundColor(.primary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(
                            Capsule()
                                .fill(.ultraThinMaterial)
                                .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 0.5))
                        )
                }
                .buttonStyle(.plain)
            }

            // Right: "+ 新建日程/待办" Button
            Button(action: {
                isShowingCreateTaskSheet = true
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .bold))
                    Text("新建日程/待办")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundColor(.primary)
                .padding(.horizontal, 13)
                .padding(.vertical, 6)
                .background(
                    Capsule()
                        .fill(.ultraThinMaterial)
                        .overlay(Capsule().strokeBorder(Color.white.opacity(0.3), lineWidth: 0.5))
                        .shadow(color: Color.black.opacity(0.06), radius: 4, y: 2)
                )
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 78)
        .padding(.trailing, 20)
        .padding(.top, 14)
        .padding(.bottom, 12)
    }

    private var weekPickerPopover: some View {
        let total = appState.currentSemester?.totalWeeks ?? 20
        return VStack(alignment: .leading, spacing: 10) {
            Text("跳转至周次")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.secondary)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 6) {
                ForEach(1...total, id: \.self) { w in
                    let isSelected = (w == appState.selectedWeek)
                    let isCurrent = (w == appState.calculatedCurrentWeek)

                    Button(action: {
                        appState.selectedWeek = w
                        isShowingWeekPicker = false
                    }) {
                        VStack(spacing: 1) {
                            Text("\(w)")
                                .font(.system(size: 12, weight: isSelected ? .bold : .medium, design: .monospaced))
                            if isCurrent {
                                Circle()
                                    .fill(isSelected ? Color.white : Color.accentColor)
                                    .frame(width: 3.5, height: 3.5)
                            }
                        }
                        .foregroundColor(isSelected ? .white : .primary)
                        .frame(width: 36, height: 32)
                        .background(
                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .fill(isSelected ? Color.accentColor : Color.primary.opacity(0.06))
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .frame(width: 230)
    }

    // MARK: - Upcoming Hero Card

    private func nextClassHeroCard(_ item: TimelineItem) -> some View {
        let themeColor = Color(hex: item.color, defaultColor: Color(hex: "#5B7FB5"))

        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                HStack(spacing: 6) {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 8, height: 8)
                    Text("今日即将开始 / 正在进行")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white.opacity(0.92))
                }

                Spacer()

                if let time = item.startTime {
                    Text("开课时间: \(time)")
                        .font(.system(size: 12, weight: .medium, design: .monospaced))
                        .foregroundColor(.white.opacity(0.85))
                }
            }

            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.white)

                    HStack(spacing: 12) {
                        if let room = item.location, !room.isEmpty {
                            Label(room, systemImage: "mappin.and.ellipse")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.white.opacity(0.92))
                        }

                        if let teacher = item.teacher, !teacher.isEmpty {
                            Label(teacher, systemImage: "person")
                                .font(.system(size: 13))
                                .foregroundColor(.white.opacity(0.85))
                        }

                        if item.startSection > 0 {
                            Text("第\(item.startSection)-\(item.endSection)节")
                                .font(.system(size: 12, weight: .medium, design: .monospaced))
                                .foregroundColor(.white.opacity(0.8))
                        }
                    }
                }

                Spacer()

                // Inspection button
                Button(action: {
                    appState.selectedOccurrence = item
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "info.circle")
                            .font(.system(size: 13))
                        Text("查看详情")
                            .font(.system(size: 13, weight: .bold))
                    }
                    .foregroundColor(themeColor)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.white)
                    .clipShape(Capsule())
                    .shadow(color: Color.black.opacity(0.12), radius: 6, y: 2)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [themeColor, themeColor.opacity(0.85)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .shadow(color: themeColor.opacity(0.35), radius: 10, y: 4)
        )
        .contentShape(Rectangle())
        .onTapGesture {
            appState.selectedOccurrence = item
        }
    }

    private var noUpcomingHeroCard: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color(hex: "#56A3A6").opacity(0.15))
                    .frame(width: 48, height: 48)

                Image(systemName: "sparkles")
                    .font(.system(size: 22))
                    .foregroundColor(Color(hex: "#56A3A6"))
            }

            VStack(alignment: .leading, spacing: 3) {
                Text("今日暂无未结课表日程")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.primary)
            }

            Spacer()

            Button(action: {
                isShowingCreateTaskSheet = true
            }) {
                HStack(spacing: 5) {
                    Image(systemName: "plus")
                        .font(.system(size: 11, weight: .bold))
                    Text("添加今日安排")
                        .font(.system(size: 12.5, weight: .bold))
                }
                .foregroundColor(.primary)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(
                    Capsule()
                        .fill(.ultraThinMaterial)
                        .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 0.5))
                )
            }
            .buttonStyle(.plain)
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.15), lineWidth: 0.5)
                )
        )
    }

    // MARK: - Left Column: Course Schedule for Day

    private var dayCourseScheduleColumn: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(dayNames[selectedDayOfWeek - 1]) 课程日程")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.primary)
                }

                Spacer()

                Button(action: {
                    appState.settings.selectedTab = "courses"
                }) {
                    HStack(spacing: 3) {
                        Text("全周课表")
                        Image(systemName: "chevron.right")
                    }
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.primary.opacity(0.8))
                }
                .buttonStyle(.plain)
            }

            if selectedDayItems.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "cup.and.saucer")
                        .font(.system(size: 36))
                        .foregroundColor(.secondary.opacity(0.4))
                    Text("这一天没有课表课程安排")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.secondary)
                    Text("享受自习与休息时光，或在右侧安排待办")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary.opacity(0.8))
                }
                .frame(maxWidth: .infinity, minHeight: 200)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color(nsColor: .controlBackgroundColor).opacity(0.5))
                )
            } else {
                VStack(spacing: 10) {
                    ForEach(selectedDayItems) { item in
                        agendaClassCard(item)
                    }
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
        )
    }

    private func agendaClassCard(_ item: TimelineItem) -> some View {
        let themeColor = Color(hex: item.color, defaultColor: Color(hex: "#5B7FB5"))

        return HStack(spacing: 12) {
            // Time range column
            VStack(alignment: .trailing, spacing: 2) {
                Text(item.startTime ?? "--:--")
                    .font(.system(size: 12.5, weight: .bold, design: .monospaced))
                    .foregroundColor(.primary)

                Text(item.endTime ?? "--:--")
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundColor(.secondary)
            }
            .frame(width: 48)

            // Vertical indicator
            RoundedRectangle(cornerRadius: 2.5)
                .fill(themeColor)
                .frame(width: 4, height: 42)

            // Content
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(item.title)
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundColor(.primary)

                    if item.sourceType == .calendarEvent {
                        Text("日程")
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundColor(Color(hex: "#34C759"))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1.5)
                            .background(Color(hex: "#34C759").opacity(0.12))
                            .clipShape(Capsule())
                    } else if !item.isCurrentWeek {
                        Text("非本周")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Color.primary.opacity(0.06))
                            .clipShape(Capsule())
                    }
                }

                HStack(spacing: 8) {
                    if let room = item.location, !room.isEmpty {
                        Text("@\(room)")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.secondary)
                    }

                    if let teacher = item.teacher, !teacher.isEmpty {
                        Text(teacher)
                            .font(.system(size: 11))
                            .foregroundColor(.secondary.opacity(0.85))
                    }

                    if item.startSection > 0 {
                        Text("第\(item.startSection)-\(item.endSection)节")
                            .font(.system(size: 10.5, design: .monospaced))
                            .foregroundColor(.secondary.opacity(0.7))
                    }
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(.secondary.opacity(0.45))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(nsColor: .textBackgroundColor).opacity(0.6))
        )
        .contentShape(Rectangle())
        .onTapGesture {
            appState.selectedOccurrence = item
        }
    }

    // MARK: - Right Column: Day Study ToDos & Tasks

    private var dayTasksColumn: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("今日待办")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.primary)

                Spacer()

                // Filter
                Picker("", selection: $taskFilter) {
                    ForEach(TaskFilter.allCases) { f in
                        Text(f.rawValue).tag(f)
                    }
                }
                .labelsHidden()
                .pickerStyle(.segmented)
                .frame(width: 170)
            }

            if filteredTasks.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "checklist")
                        .font(.system(size: 36))
                        .foregroundColor(.secondary.opacity(0.4))
                    Text(taskFilter == .completed ? "暂无已完成的任务" : "今日暂无待办任务")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.secondary)

                    Button(action: {
                        isShowingCreateTaskSheet = true
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                            Text("新建待办目标")
                        }
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.primary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 5)
                        .background(
                            Capsule()
                                .fill(.ultraThinMaterial)
                                .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 0.5))
                        )
                    }
                    .buttonStyle(.plain)
                }
                .frame(maxWidth: .infinity, minHeight: 200)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color(nsColor: .controlBackgroundColor).opacity(0.5))
                )
            } else {
                VStack(spacing: 9) {
                    ForEach(filteredTasks) { task in
                        agendaTaskRow(task: task)
                    }
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
        )
    }

    private func agendaTaskRow(task: PomodoroTask) -> some View {
        HStack(spacing: 10) {
            // Checkbox
            Button(action: {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                    appState.toggleTaskCompletion(id: task.id)
                }
            }) {
                Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 18))
                    .foregroundColor(task.isCompleted ? Color(hex: "#56A3A6") : .secondary.opacity(0.6))
            }
            .buttonStyle(.plain)

            // Content
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(task.title)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(task.isCompleted ? .secondary : .primary)
                        .strikethrough(task.isCompleted, color: .secondary.opacity(0.6))

                    if let cName = task.courseName {
                        Text(cName)
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundColor(.accentColor)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .background(Color.accentColor.opacity(0.12))
                            .clipShape(Capsule())
                    }

                    // Priority
                    Text(task.priority.rawValue)
                        .font(.system(size: 8.5, weight: .bold))
                        .foregroundColor(Color(hex: task.priority.colorHex))
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(Color(hex: task.priority.colorHex).opacity(0.12))
                        .clipShape(Capsule())
                }

                if let n = task.notes, !n.isEmpty {
                    Text(n)
                        .font(.system(size: 10.5))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            if appState.settings.enableStudyModule && task.totalFocusMinutes > 0 {
                // Focus minutes badge if already focused
                HStack(spacing: 3) {
                    Image(systemName: "clock")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text("\(task.totalFocusMinutes)m")
                        .font(.system(size: 11, weight: .medium, design: .monospaced))
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(Color.primary.opacity(0.06))
                .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color(nsColor: .textBackgroundColor).opacity(0.5))
        )
        .contextMenu {
            Button(task.isCompleted ? "标为待办" : "标为已完成") {
                appState.toggleTaskCompletion(id: task.id)
            }
            if appState.settings.enableStudyModule && !task.isCompleted {
                Button("开启专注") {
                    appState.startFocusForTask(task)
                }
            }
            Divider()
            Button(role: .destructive, action: {
                appState.deletePomodoroTask(id: task.id)
            }) {
                Text("删除待办")
            }
        }
    }
}
