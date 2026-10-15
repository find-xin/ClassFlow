import SwiftUI
import AppKit
import ClassFlowCore

/// Header bar matching WakeUp's minimalist typography and layout:
/// - Left: Clickable week and date title (toggles week & timetable popover)
/// - Center: Clean and spacious (reverting the center pill to respect WakeUp aesthetics)
/// - Right: Delicate ghost action icons with native Popovers and Menus (dismissing automatically upon click-outside)
public struct WakeUpHeaderView: View {
    @Bindable public var appState: AppState

    @State private var isShowingWeekPopover: Bool = false
    @State private var isShowingQuickMenuPopover: Bool = false
    @State private var isShowingPasteJSONSheet: Bool = false
    @State private var pastedJSONText: String = ""
    @State private var isTitleHovered: Bool = false

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
        HStack(alignment: .center) {
            // 1. Left: Clickable Week and Date Title (Reverting back to WakeUp's top-left layout)
            Button(action: {
                isShowingWeekPopover.toggle()
            }) {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 8) {
                            Text("第\(appState.selectedWeek)周 周\(todayWeekdayShort)")
                                .font(.system(size: 21, weight: .bold))
                                .foregroundColor(.primary)

                            if !appState.isViewingCurrentWeek {
                                Text("非本周")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color(hex: "#FF5A5F"))
                                    .clipShape(Capsule())
                            }

                            Image(systemName: isShowingWeekPopover ? "chevron.up" : "chevron.down")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.secondary.opacity(0.7))
                                .padding(.leading, 2)
                        }

                        HStack(spacing: 6) {
                            Text(currentDateSlashFormat)
                                .font(.system(size: 11.5, weight: .medium, design: .monospaced))
                                .foregroundColor(.secondary.opacity(0.85))

                            if let semName = appState.currentSemester?.name {
                                Text("·")
                                    .foregroundColor(.secondary.opacity(0.5))
                                Text(semName)
                                    .font(.system(size: 11.5, weight: .medium))
                                    .foregroundColor(.secondary.opacity(0.75))
                                    .lineLimit(1)
                            }
                        }
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(isTitleHovered ? Color.primary.opacity(0.06) : Color.clear)
                )
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .onHover { h in
                withAnimation(.easeInOut(duration: 0.15)) {
                    isTitleHovered = h
                }
            }
            .popover(isPresented: $isShowingWeekPopover, arrowEdge: .bottom) {
                WakeUpWeekPopoverView(appState: appState, isPresented: $isShowingWeekPopover)
            }
            .help("点击展开周数滑块与课表切换面板")

            // 2. Center: Empty spacer preserving breathing room and aesthetic wallpaper canvas
            Spacer()

            // View Mode Toggle (周视图 vs 日视图)
            Picker("", selection: $appState.timetableDisplayMode) {
                Text("周").tag(AppState.TimetableDisplayMode.week)
                Text("日").tag(AppState.TimetableDisplayMode.day)
            }
            .pickerStyle(.segmented)
            .frame(width: 88)
            .padding(.trailing, 4)

            // 3. Right: 4 Sleek Ghost Action Icons (Add, Import, Export, More)
            HStack(spacing: 10) {
                // (+) Add Menu (Course or Event)
                Menu {
                    Button(action: {
                        appState.isAddingCourse = true
                    }) {
                        Label("添加课程...", systemImage: "book.pages")
                    }

                    Button(action: {
                        appState.startAddingCalendarEvent()
                    }) {
                        Label("添加日程 / 待办...", systemImage: "calendar.badge.plus")
                    }
                } label: {
                    GhostMenuIcon(systemName: "plus")
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
                .help("添加课程或日程待办")

                // (↓) Import Menu (Supporting .classflow, .ics, AISchedule / WakeUp JSON)
                Menu {
                    Button("从 .classflow 备份包导入...") {
                        openImportDialog(allowedExtensions: ["classflow"])
                    }
                    Button("从 .ics 日历文件导入...") {
                        openImportDialog(allowedExtensions: ["ics"])
                    }
                    Button("从 AISchedule / WakeUp JSON 导入...") {
                        openImportDialog(allowedExtensions: ["json"])
                    }
                    Divider()
                    Button("粘贴教务脚本 JSON 快速导入...") {
                        if let pb = NSPasteboard.general.string(forType: .string), !pb.isEmpty {
                            pastedJSONText = pb
                        }
                        isShowingPasteJSONSheet = true
                    }
                } label: {
                    GhostMenuIcon(systemName: "square.and.arrow.down")
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
                .help("导入课表 (支持 ClassFlow、ICS、小爱/WakeUp 规范)")

                // (↑) Export Menu
                Menu {
                    Button("导出当前课表为日历 (.ics)...") {
                        exportICSDialog()
                    }
                    Button("导出完整备份包 (.classflow)...") {
                        exportBackupDialog()
                    }
                } label: {
                    GhostMenuIcon(systemName: "square.and.arrow.up")
                }
                .menuStyle(.borderlessButton)
                .menuIndicator(.hidden)
                .fixedSize()
                .help("导出或备份课表")

                // (···) Quick Actions Menu Popover (Automatically dismisses upon click-outside)
                Button(action: {
                    isShowingQuickMenuPopover.toggle()
                }) {
                    GhostMenuIcon(systemName: "ellipsis")
                }
                .buttonStyle(.plain)
                .popover(isPresented: $isShowingQuickMenuPopover, arrowEdge: .bottom) {
                    WakeUpQuickGridPopover(appState: appState, isPresented: $isShowingQuickMenuPopover)
                }
                .help("快捷功能与设置")
            }
        }
        .sheet(isPresented: $isShowingPasteJSONSheet) {
            PasteJSONImportSheet(appState: appState, isPresented: $isShowingPasteJSONSheet, initialText: pastedJSONText)
        }
    }

    private func ghostButton(systemName: String, helpText: String, action: @escaping () -> Void) -> some View {
        GhostActionButton(systemName: systemName, helpText: helpText, action: action)
    }

    @MainActor
    private func openImportDialog(allowedExtensions: [String]) {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        if panel.runModal() == .OK, let url = panel.url {
            let ext = url.pathExtension.lowercased()
            if ext == "classflow" {
                if let data = try? Data(contentsOf: url) {
                    appState.stageClassFlowImport(from: data)
                }
            } else if ext == "ics" {
                if let content = try? String(contentsOf: url, encoding: .utf8) {
                    appState.importICS(content: content)
                }
            } else if ext == "json" {
                if let content = try? String(contentsOf: url, encoding: .utf8) {
                    appState.importOpenFormatJSON(content: content)
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

/// 8-icon shortcut grid popover replicating WakeUp's quick actions, closing automatically on click-outside.
public struct WakeUpQuickGridPopover: View {
    @Bindable public var appState: AppState
    @Binding public var isPresented: Bool

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
                    isPresented = false
                    appState.isShowingCourseList = true
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
            .frame(width: 58)
        }
        .buttonStyle(.plain)
    }
}

/// Sheet enabling users to paste AISchedule or WakeUp open format JSON directly from the clipboard.
public struct PasteJSONImportSheet: View {
    @Bindable var appState: AppState
    @Binding var isPresented: Bool
    @State private var jsonText: String
    @State private var previewInfo: String? = nil
    @State private var errorMessage: String? = nil

    public init(appState: AppState, isPresented: Binding<Bool>, initialText: String = "") {
        self.appState = appState
        self._isPresented = isPresented
        self._jsonText = State(initialValue: initialText)
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("粘贴导入教务脚本 JSON")
                    .font(.system(size: 16, weight: .bold))

                Spacer()

                Button("取消") {
                    isPresented = false
                }
                .buttonStyle(.plain)
                .foregroundColor(.secondary)
            }
            .padding(16)
            .background(.bar)

            Divider()

            VStack(alignment: .leading, spacing: 12) {
                Text("支持小爱课程表 (AISchedule) 开源适配规范与 WakeUp 课程表 JSON 格式。")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)

                TextEditor(text: $jsonText)
                    .font(.system(size: 12, design: .monospaced))
                    .padding(8)
                    .background(Color(nsColor: .controlBackgroundColor))
                    .cornerRadius(8)
                    .frame(height: 240)

                if let err = errorMessage {
                    Text(err)
                        .font(.system(size: 12))
                        .foregroundColor(.red)
                }

                HStack {
                    Button("从剪贴板粘贴") {
                        if let pb = NSPasteboard.general.string(forType: .string) {
                            jsonText = pb
                        }
                    }
                    .buttonStyle(.bordered)

                    Spacer()

                    Button("解析并导入") {
                        guard let current = appState.currentSemester else {
                            errorMessage = "请先选择或创建一个学期"
                            return
                        }
                        let importer = OpenFormatCourseImporter()
                        do {
                            let result = try importer.parse(jsonString: jsonText, semesterId: current.id)
                            for c in result.courses {
                                appState.addCourse(c)
                            }
                            if let sec = result.parsedSections, !sec.isEmpty {
                                var updated = current
                                updated.sections = sec
                                appState.updateSemester(updated)
                            }
                            isPresented = false
                        } catch {
                            errorMessage = "解析失败: \(error.localizedDescription)"
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(jsonText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .padding(16)
        }
        .frame(width: 480, height: 380)
    }
}

private struct GhostActionButton: View {
    let systemName: String
    let helpText: String
    let action: () -> Void

    @State private var isHovered: Bool = false

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(isHovered ? Color.primary.opacity(0.08) : Color.clear)
                    .frame(width: 32, height: 32)

                Image(systemName: systemName)
                    .font(.system(size: 16, weight: .regular))
                    .foregroundColor(.primary.opacity(isHovered ? 1.0 : 0.85))
            }
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .fixedSize()
        .help(helpText)
        .onHover { h in
            withAnimation(.easeInOut(duration: 0.15)) {
                isHovered = h
            }
        }
    }
}

private struct GhostMenuIcon: View {
    let systemName: String
    @State private var isHovered: Bool = false

    var body: some View {
        ZStack {
            Circle()
                .fill(isHovered ? Color.primary.opacity(0.08) : Color.clear)
                .frame(width: 32, height: 32)

            Image(systemName: systemName)
                .font(.system(size: 16, weight: .regular))
                .foregroundColor(.primary.opacity(isHovered ? 1.0 : 0.85))
        }
        .contentShape(Circle())
        .onHover { h in
            withAnimation(.easeInOut(duration: 0.15)) {
                isHovered = h
            }
        }
    }
}
