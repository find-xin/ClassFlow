import SwiftUI
import AppKit
import ClassFlowCore

/// Native top toolbar containing WakeUp-inspired week selector popover, timetable switcher, and action shortcuts.
public struct MainWindowToolbar: View {
    @Bindable public var appState: AppState
    @State private var isShowingWeekPopover: Bool = false
    @State private var isShowingWidgetPopover: Bool = false

    public init(appState: AppState) {
        self.appState = appState
    }

    private var todayWeekdayShort: String {
        let weekday = Calendar.current.component(.weekday, from: Date())
        let dayNames = ["日", "一", "二", "三", "四", "五", "六"]
        return dayNames[(weekday - 1) % 7]
    }

    private var currentDateSlashFormat: String {
        let df = DateFormatter()
        df.dateFormat = "yyyy/MM/dd"
        return df.string(from: Date())
    }

    public var body: some View {
        HStack(spacing: 14) {
            // 1. WakeUp Style Clickable Week Title (Opens Week & Timetable Popover)
            Button(action: {
                isShowingWeekPopover.toggle()
            }) {
                HStack(alignment: .center, spacing: 6) {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text("第\(appState.selectedWeek)周")
                                .font(.system(size: 16, weight: .bold))
                            Text("周\(todayWeekdayShort)")
                                .font(.system(size: 15, weight: .semibold))
                        }

                        HStack(spacing: 4) {
                            Text(currentDateSlashFormat)
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundColor(.secondary)

                            if let sem = appState.currentSemester {
                                Text("• \(sem.name)")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }

                    Image(systemName: "chevron.down")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.secondary)
                        .padding(.leading, 2)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .help("点击切换周次与管理课表")
            .popover(isPresented: $isShowingWeekPopover, arrowEdge: .bottom) {
                weekAndTimetablePopover
            }

            Spacer()

            // 2. Right Action Shortcuts: 上课时间, 导入/导出, 添加课程 [+], 小组件, 更多 [...]
            HStack(spacing: 8) {
                // 上课时间设置
                Button(action: {
                    appState.editingSemesterForSections = appState.currentSemester
                    appState.isShowingEditSectionTimes = true
                }) {
                    Label("上课时间", systemImage: "clock")
                        .font(.system(size: 12))
                }
                .buttonStyle(.bordered)
                .help("自定义每节课的上下课作息时间")

                // 导入/导出
                Menu {
                    Button("导入课表文件 (.classflow / .ics)...") {
                        openImportDialog()
                    }
                    Divider()
                    Button("导出当前学期为日历 (.ics)...") {
                        exportICSDialog()
                    }
                    Button("导出完整备份包 (.classflow)...") {
                        exportBackupDialog()
                    }
                } label: {
                    Label("导入/导出", systemImage: "arrow.up.arrow.down")
                        .font(.system(size: 12))
                }
                .menuStyle(.borderlessButton)

                // 新建课程 / 日程
                Menu {
                    Button(action: { appState.isAddingCourse = true }) {
                        Label("新建课程...", systemImage: "book.pages")
                    }
                    Button(action: { appState.startAddingCalendarEvent() }) {
                        Label("新建日程 / 待办...", systemImage: "calendar.badge.plus")
                    }
                } label: {
                    Label("新建", systemImage: "plus")
                        .font(.system(size: 12, weight: .semibold))
                }
                .menuStyle(.borderedButton)

                // 桌面小组件预览
                Button {
                    isShowingWidgetPopover.toggle()
                } label: {
                    Label("小组件", systemImage: "rectangle.3.group")
                        .font(.system(size: 12))
                }
                .buttonStyle(.bordered)
                .help("查看桌面小组件效果与常驻设置")
                .popover(isPresented: $isShowingWidgetPopover) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("ClassFlow 桌面小组件")
                                .font(.headline)
                            Spacer()
                            Button("前往设置") {
                                isShowingWidgetPopover = false
                                appState.isShowingSettings = true
                            }
                            .font(.caption)
                        }
                        Divider()
                        TodayClassesWidgetView(payload: appState.widgetDataStore.load())
                            .frame(width: 325, height: 155)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .shadow(radius: 4)
                        Text("提示：小组件已内置在应用中，可右击桌面选择「编辑小组件」常驻到 Mac 桌面。")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .padding()
                    .frame(width: 360)
                }

                // 更多菜单
                Menu {
                    Toggle("显示周末", isOn: $appState.settings.showWeekends)
                    Divider()
                    Button("偏好设置...") {
                        appState.isShowingSettings = true
                    }
                    .keyboardShortcut(",", modifiers: .command)
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.system(size: 14))
                }
                .menuStyle(.borderlessButton)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(.bar)
    }

    // MARK: - WakeUp Style Popover (Week Slider & Timetable Cards)

    private var weekAndTimetablePopover: some View {
        VStack(alignment: .leading, spacing: 14) {
            // 1. Week Slider Header
            HStack {
                Text("周数")
                    .font(.system(size: 14, weight: .bold))

                Spacer()

                if !appState.isViewingCurrentWeek {
                    Button("回到本周") {
                        appState.jumpToCurrentWeek()
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.accentColor)
                    .buttonStyle(.plain)
                }
            }

            // 2. Week Slider with Numeric Badge
            let totalWeeks = max(1, appState.currentSemester?.totalWeeks ?? 20)
            VStack(spacing: 6) {
                HStack(spacing: 8) {
                    Button(action: { appState.previousWeek() }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 11, weight: .semibold))
                    }
                    .buttonStyle(.bordered)
                    .disabled(appState.selectedWeek <= 1)

                    Slider(
                        value: Binding(
                            get: { Double(appState.selectedWeek) },
                            set: { appState.selectedWeek = Int($0) }
                        ),
                        in: 1...Double(totalWeeks),
                        step: 1
                    )
                    .accentColor(.accentColor)

                    Button(action: { appState.nextWeek() }) {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 11, weight: .semibold))
                    }
                    .buttonStyle(.bordered)
                    .disabled(appState.selectedWeek >= totalWeeks)
                }

                HStack {
                    Text("第 1 周")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("第 \(appState.selectedWeek) 周" + (appState.isViewingCurrentWeek ? " (本周)" : ""))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.accentColor)
                    Spacer()
                    Text("第 \(totalWeeks) 周")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 2)

            Divider()

            // 3. Timetable Switcher Header (with 新建课表 & 管理课表 actions)
            HStack {
                Text("切换课表")
                    .font(.system(size: 14, weight: .bold))

                Spacer()

                Button("新建课表") {
                    isShowingWeekPopover = false
                    appState.isShowingCreateSemester = true
                }
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.accentColor)
                .buttonStyle(.plain)


            }

            // 4. Timetable Cards ScrollView (Matching WakeUp cards with checkmark)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(appState.semesters) { sem in
                        let isSelected = (sem.id == appState.currentSemesterId)

                        Button(action: {
                            appState.selectSemester(id: sem.id)
                        }) {
                            VStack(spacing: 6) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(isSelected ? Color.accentColor.opacity(0.18) : Color.primary.opacity(0.05))
                                        .frame(width: 84, height: 74)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .strokeBorder(isSelected ? Color.accentColor : Color.clear, lineWidth: 2)
                                        )

                                    if isSelected {
                                        Image(systemName: "checkmark")
                                            .font(.system(size: 20, weight: .bold))
                                            .foregroundColor(.accentColor)
                                    } else {
                                        Image(systemName: "calendar")
                                            .font(.system(size: 22))
                                            .foregroundColor(.secondary)
                                    }
                                }

                                Text(sem.name)
                                    .font(.system(size: 11, weight: isSelected ? .bold : .regular))
                                    .foregroundColor(isSelected ? .primary : .secondary)
                                    .frame(width: 84)
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                    }

                    // Quick "+ 新建课表" card button
                    Button(action: {
                        isShowingWeekPopover = false
                        appState.isShowingCreateSemester = true
                    }) {
                        VStack(spacing: 6) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(Color.secondary.opacity(0.4), style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                                    .frame(width: 84, height: 74)

                                Image(systemName: "plus")
                                    .font(.system(size: 20, weight: .medium))
                                    .foregroundColor(.secondary)
                            }

                            Text("新建课表")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 4)
            }
        }
        .padding(14)
        .frame(width: 380)
    }

    // MARK: - Dialog Handlers

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

    @MainActor
    private func exportICSDialog() {
        guard let icsText = appState.exportCurrentSemesterICS() else { return }
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "\(appState.currentSemester?.name ?? "ClassFlow").ics"
        if panel.runModal() == .OK, let url = panel.url {
            try? icsText.write(to: url, atomically: true, encoding: .utf8)
        }
    }

    @MainActor
    private func exportBackupDialog() {
        guard let data = try? appState.exportClassFlowBackup() else { return }
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "ClassFlow_Backup.classflow"
        if panel.runModal() == .OK, let url = panel.url {
            try? data.write(to: url, options: .atomic)
        }
    }
}
