import SwiftUI
import ClassFlowCore

/// Unified management sheet for all courses, schedules (calendar events), and study tasks (todos)
/// belonging to the active semester. Provides centralized viewing, searching, editing, and deletion.
public struct UnifiedEntityManagerSheet: View {
    @Bindable var appState: AppState
    @Environment(\.dismiss) private var dismiss

    public enum Tab: String, CaseIterable, Identifiable {
        case courses = "课程"
        case schedules = "日程"
        case tasks = "待办"
        public var id: String { rawValue }
    }

    @State private var selectedTab: Tab = .courses
    @State private var searchText: String = ""

    // Deletion states
    @State private var courseToDelete: Course? = nil
    @State private var isShowingDeleteCourseConfirm: Bool = false

    @State private var eventToDelete: CalendarEvent? = nil
    @State private var isShowingDeleteEventConfirm: Bool = false

    @State private var taskToDelete: PomodoroTask? = nil
    @State private var isShowingDeleteTaskConfirm: Bool = false

    public init(appState: AppState, initialTab: Tab = .courses) {
        self.appState = appState
        self._selectedTab = State(initialValue: initialTab)
    }

    private var currentSemId: UUID? {
        appState.currentSemester?.id
    }

    private var currentCourses: [Course] {
        guard let semId = currentSemId else { return [] }
        let all = appState.courses.filter { $0.semesterId == semId }
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if q.isEmpty { return all }
        return all.filter {
            $0.name.lowercased().contains(q) ||
            ($0.teacher?.lowercased().contains(q) ?? false) ||
            $0.schedules.contains { ($0.classroom?.lowercased().contains(q) ?? false) }
        }
    }

    private var currentSchedules: [CalendarEvent] {
        let defaultSemId = appState.semesters.first?.id
        let all = appState.calendarEvents.filter {
            $0.semesterId == currentSemId || ($0.semesterId == nil && currentSemId == defaultSemId)
        }
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if q.isEmpty { return all }
        return all.filter {
            $0.title.lowercased().contains(q) ||
            ($0.location?.lowercased().contains(q) ?? false) ||
            ($0.description?.lowercased().contains(q) ?? false) ||
            ($0.frequency?.lowercased().contains(q) ?? false)
        }
    }

    private var currentTasks: [PomodoroTask] {
        let all = appState.currentSemesterPomodoroTasks
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if q.isEmpty { return all }
        return all.filter {
            $0.title.lowercased().contains(q) ||
            ($0.courseName?.lowercased().contains(q) ?? false) ||
            ($0.notes?.lowercased().contains(q) ?? false)
        }
    }

    private let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]

    public var body: some View {
        VStack(spacing: 0) {
            // 1. Navigation Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("事项管理")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.primary)

                    Text("当前学期：\(appState.currentSemester?.name ?? "未指定")")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }

                Spacer()

                // Primary Add Button depending on active tab
                Button(action: handleAddNew) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                        Text(addBtnTitle)
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

            // 2. Tab Segmented Control
            HStack {
                Picker("", selection: $selectedTab) {
                    Text("课程 (\(currentCourses.count))").tag(Tab.courses)
                    Text("日程 (\(currentSchedules.count))").tag(Tab.schedules)
                    Text("待办 (\(currentTasks.count))").tag(Tab.tasks)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 6)

            // 3. Search Bar
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)
                    .font(.system(size: 13))

                TextField(searchPlaceholder, text: $searchText)
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
            .padding(.bottom, 10)

            Divider()

            // 4. Content Views
            Group {
                switch selectedTab {
                case .courses:
                    coursesListView
                case .schedules:
                    schedulesListView
                case .tasks:
                    tasksListView
                }
            }
        }
        .frame(width: 560, height: 600)
        // Confirmation dialogs
        .confirmationDialog(
            "确定要删除这门课程吗？",
            isPresented: $isShowingDeleteCourseConfirm,
            presenting: courseToDelete
        ) { target in
            Button("删除 \(target.name)", role: .destructive) {
                appState.deleteCourse(id: target.id)
                courseToDelete = nil
            }
            Button("取消", role: .cancel) { courseToDelete = nil }
        } message: { _ in
            Text("删除后将移除该课程及其所有上课时段。此操作无法撤销。")
        }
        .confirmationDialog(
            "确定要删除此日程吗？",
            isPresented: $isShowingDeleteEventConfirm,
            presenting: eventToDelete
        ) { target in
            let isRec = (target.frequency != nil && target.frequency != "none")
            if isRec {
                Button("仅删除本次日程") {
                    let todayStr = Self.isoDateFormatter.string(from: Date())
                    appState.deleteCalendarEvent(id: target.id, scope: .thisOnly, occurrenceDate: todayStr)
                    eventToDelete = nil
                }
                Button("删除此日程及以后的所有重复") {
                    let todayStr = Self.isoDateFormatter.string(from: Date())
                    appState.deleteCalendarEvent(id: target.id, scope: .thisAndFuture, occurrenceDate: todayStr)
                    eventToDelete = nil
                }
                Button("彻底删除所有重复日程", role: .destructive) {
                    appState.deleteCalendarEvent(id: target.id)
                    eventToDelete = nil
                }
            } else {
                Button("删除日程", role: .destructive) {
                    appState.deleteCalendarEvent(id: target.id)
                    eventToDelete = nil
                }
            }
            Button("取消", role: .cancel) { eventToDelete = nil }
        } message: { target in
            let isRec = (target.frequency != nil && target.frequency != "none")
            Text(isRec ? "此日程为重复日程，请选择删除范围。" : "删除后将彻底移除日程「\(target.title)」。")
        }
        .confirmationDialog(
            "确定要删除此待办吗？",
            isPresented: $isShowingDeleteTaskConfirm,
            presenting: taskToDelete
        ) { target in
            Button("删除待办", role: .destructive) {
                appState.deletePomodoroTask(id: target.id)
                taskToDelete = nil
            }
            Button("取消", role: .cancel) { taskToDelete = nil }
        } message: { target in
            Text("确定要删除待办任务「\(target.title)」吗？")
        }
    }

    private var addBtnTitle: String {
        switch selectedTab {
        case .courses: return "添加课程"
        case .schedules: return "添加日程"
        case .tasks: return "添加待办"
        }
    }

    private var searchPlaceholder: String {
        switch selectedTab {
        case .courses: return "搜索课程名称、授课教师、教室..."
        case .schedules: return "搜索日程标题、地点、频次、备注..."
        case .tasks: return "搜索待办标题、关联课程、备注..."
        }
    }

    private func handleAddNew() {
        dismiss()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
            switch selectedTab {
            case .courses:
                appState.startAddingCourse()
            case .schedules:
                appState.startAddingCalendarEvent()
            case .tasks:
                appState.startAddingCalendarEvent() // switches to task or add task sheet
            }
        }
    }

    // MARK: - 1. Courses List View

    @ViewBuilder
    private var coursesListView: some View {
        if currentCourses.isEmpty {
            emptyStateView(
                icon: "books.vertical",
                title: searchText.isEmpty ? "当前学期尚未添加任何课程" : "未找到匹配的课程",
                actionTitle: searchText.isEmpty ? "立即添加第一门课程" : nil,
                action: { handleAddNew() }
            )
        } else {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(currentCourses) { c in
                        courseCardRow(c)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
    }

    private func courseCardRow(_ c: Course) -> some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(hex: c.color, defaultColor: Color(hex: "#5B7FB5")))
                .frame(width: 5, height: 50)
                .padding(.top, 2)

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

                VStack(alignment: .leading, spacing: 3) {
                    ForEach(c.schedules) { s in
                        let dayName = (s.dayOfWeek >= 1 && s.dayOfWeek <= 7) ? dayNames[s.dayOfWeek - 1] : "周\(s.dayOfWeek)"
                        let room = (s.classroom?.isEmpty ?? true) ? "未指定教室" : "@\(s.classroom ?? "")"
                        HStack(spacing: 6) {
                            Text("\(dayName) 第\(s.startSection)-\(s.endSection)节 (\(s.weekPattern.summary))")
                                .font(.system(size: 11, weight: .medium, design: .monospaced))
                                .foregroundColor(.primary.opacity(0.8))
                            Text("•").font(.system(size: 10)).foregroundColor(.secondary)
                            Text(room).font(.system(size: 11)).foregroundColor(.secondary)
                        }
                    }
                }
                .padding(.top, 2)
            }

            Spacer()

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
                .help("编辑课程")

                Button(action: {
                    courseToDelete = c
                    isShowingDeleteCourseConfirm = true
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

    // MARK: - 2. Schedules List View

    @ViewBuilder
    private var schedulesListView: some View {
        if currentSchedules.isEmpty {
            emptyStateView(
                icon: "calendar",
                title: searchText.isEmpty ? "当前学期尚未添加任何日程" : "未找到匹配的日程",
                actionTitle: searchText.isEmpty ? "立即添加第一个日程" : nil,
                action: { handleAddNew() }
            )
        } else {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(currentSchedules) { ev in
                        scheduleCardRow(ev)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
    }

    private func scheduleCardRow(_ ev: CalendarEvent) -> some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(hex: ev.color, defaultColor: Color(hex: "#FF5A5F")))
                .frame(width: 5, height: 50)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Text(ev.title)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.primary)

                    let freqLabel = frequencyName(ev.frequency)
                    Text(freqLabel)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(Color(hex: "#34C759"))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color(hex: "#34C759").opacity(0.12))
                        .clipShape(Capsule())

                    if let notes = ev.description, !notes.isEmpty {
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
                    let timeInfo: String = {
                        if ev.isAllDay { return "全天日程" }
                        if let s = ev.startSection, let e = ev.endSection {
                            return "第\(s)-\(e)节 (\(timePart(ev.startTime)) ~ \(timePart(ev.endTime)))"
                        }
                        return "\(timePart(ev.startTime)) ~ \(timePart(ev.endTime))"
                    }()

                    Label(timeInfo, systemImage: "clock")
                        .font(.system(size: 11.5, design: .monospaced))
                        .foregroundColor(.secondary)

                    if let loc = ev.location, !loc.isEmpty {
                        Label(loc, systemImage: "mappin.and.ellipse")
                            .font(.system(size: 11.5))
                            .foregroundColor(.secondary)
                    }
                }

                HStack(spacing: 8) {
                    if let sDate = ev.startDate {
                        if let uDate = ev.endRepeatUntil ?? ev.endDate, !uDate.isEmpty, uDate != sDate {
                            Text("日期范围：\(sDate) ~ \(uDate) (已截止)")
                                .font(.system(size: 10.5))
                                .foregroundColor(.secondary.opacity(0.85))
                        } else {
                            Text("起始日期：\(sDate)")
                                .font(.system(size: 10.5))
                                .foregroundColor(.secondary.opacity(0.75))
                        }
                    } else if let uDate = ev.endRepeatUntil ?? ev.endDate, !uDate.isEmpty {
                        Text("截止日期：\(uDate)")
                            .font(.system(size: 10.5))
                            .foregroundColor(.secondary.opacity(0.85))
                    }

                    if let weeks = ev.customWeeks, !weeks.isEmpty {
                        Text("•").font(.system(size: 10)).foregroundColor(.secondary)
                        Text("指定周次：\(WeekPattern.custom(Array(weeks)).summary)")
                            .font(.system(size: 10.5))
                            .foregroundColor(.secondary.opacity(0.85))
                    }
                }
            }

            Spacer()

            HStack(spacing: 8) {
                Button(action: {
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                        appState.startEditingCalendarEvent(ev)
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
                .help("编辑日程")

                Button(action: {
                    eventToDelete = ev
                    isShowingDeleteEventConfirm = true
                }) {
                    Image(systemName: "trash")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.red.opacity(0.85))
                        .frame(width: 28, height: 28)
                        .background(Color.red.opacity(0.08))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .help("删除日程")
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor))
                .shadow(color: Color.black.opacity(0.04), radius: 4, y: 2)
        )
    }

    // MARK: - 3. Tasks List View

    @ViewBuilder
    private var tasksListView: some View {
        if currentTasks.isEmpty {
            emptyStateView(
                icon: "checklist",
                title: searchText.isEmpty ? "当前学期尚未添加任何待办" : "未找到匹配的待办",
                actionTitle: searchText.isEmpty ? "立即添加第一个待办" : nil,
                action: { handleAddNew() }
            )
        } else {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(currentTasks) { t in
                        taskCardRow(t)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
    }

    private func taskCardRow(_ t: PomodoroTask) -> some View {
        HStack(alignment: .center, spacing: 12) {
            // Checkbox for task completion
            Button(action: {
                var updated = t
                updated.isCompleted.toggle()
                if updated.isCompleted {
                    updated.completedAt = Date()
                } else {
                    updated.completedAt = nil
                }
                appState.updatePomodoroTask(updated)
            }) {
                Image(systemName: t.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 20))
                    .foregroundColor(t.isCompleted ? Color(hex: "#34C759") : .secondary)
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 8) {
                    Text(t.title)
                        .font(.system(size: 14.5, weight: .bold))
                        .strikethrough(t.isCompleted)
                        .foregroundColor(t.isCompleted ? .secondary : .primary)

                    if let course = t.courseName, !course.isEmpty {
                        Text(course)
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.primary.opacity(0.06))
                            .clipShape(Capsule())
                    }
                }

                HStack(spacing: 10) {
                    if let due = t.dueDate, !due.isEmpty {
                        Label("截止: \(due)", systemImage: "clock")
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundColor(.secondary)
                    }

                    Label("\(t.targetMinutes ?? 45) 分钟 (\(t.estimatedPomodoros) 番茄)", systemImage: "timer")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            HStack(spacing: 8) {
                Button(action: {
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                        appState.startEditingPomodoroTask(t)
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
                .help("编辑待办")

                Button(action: {
                    taskToDelete = t
                    isShowingDeleteTaskConfirm = true
                }) {
                    Image(systemName: "trash")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.red.opacity(0.85))
                        .frame(width: 28, height: 28)
                        .background(Color.red.opacity(0.08))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .help("删除待办")
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor))
                .shadow(color: Color.black.opacity(0.04), radius: 4, y: 2)
        )
    }

    // MARK: - Helpers

    private func emptyStateView(icon: String, title: String, actionTitle: String?, action: @escaping () -> Void) -> some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: icon)
                .font(.system(size: 44))
                .foregroundColor(.secondary.opacity(0.6))
            Text(title)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.secondary)
            if let actTitle = actionTitle {
                Button(action: action) {
                    Text(actTitle)
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
    }

    private func frequencyName(_ code: String?) -> String {
        switch code {
        case "daily": return "每天"
        case "workdays": return "工作日"
        case "weekends": return "周末"
        case "weekly": return "每周"
        case "monthly": return "每月"
        case "customDays": return "自定义天"
        default: return "仅一次"
        }
    }

    private func timePart(_ dt: String) -> String {
        if dt.contains(" ") {
            return String(dt.split(separator: " ").last?.prefix(5) ?? "")
        }
        if dt.contains("T") {
            return String(dt.split(separator: "T").last?.prefix(5) ?? "")
        }
        return String(dt.prefix(5))
    }

    private static let isoDateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        return df
    }()
}
