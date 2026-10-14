import SwiftUI
import AppKit
import ClassFlowCore

/// Native top toolbar containing semester switcher, week navigator, import/export, and action shortcuts.
public struct MainWindowToolbar: View {
    @Bindable public var appState: AppState
    @State private var isShowingWidgetPopover: Bool = false

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        HStack(spacing: 12) {
            // 1. Semester Selector PopUp
            if !appState.semesters.isEmpty {
                Menu {
                    ForEach(appState.semesters) { sem in
                        Button(action: { appState.selectSemester(id: sem.id) }) {
                            HStack {
                                Text(sem.name)
                                if sem.id == appState.currentSemesterId {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "books.vertical.fill")
                            .font(.system(size: 11))
                        Text(appState.currentSemester?.name ?? "选择学期")
                            .font(.system(size: 13, weight: .semibold))
                    }
                }
                .menuStyle(.borderlessButton)
            }

            Spacer()

            // 2. Week Navigation Group: [<] 第 N 周 (本周) [>] [回到本周]
            HStack(spacing: 6) {
                Button(action: { appState.previousWeek() }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 11, weight: .bold))
                }
                .buttonStyle(.bordered)
                .help("上一周 (Cmd+Left)")
                .keyboardShortcut(.leftArrow, modifiers: .command)

                // Week selector popup menu
                Menu {
                    if let sem = appState.currentSemester {
                        ForEach(1...sem.totalWeeks, id: \.self) { w in
                            Button("第 \(w) 周" + (w == appState.calculatedCurrentWeek ? " (本周)" : "")) {
                                appState.selectedWeek = w
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text("第 \(appState.selectedWeek) 周")
                            .font(.system(size: 13, weight: .bold))
                        if appState.isViewingCurrentWeek {
                            Text("当前周")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.accentColor)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 1)
                                .background(Color.accentColor.opacity(0.12))
                                .clipShape(Capsule())
                        }
                    }
                }
                .menuStyle(.borderlessButton)

                Button(action: { appState.nextWeek() }) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .bold))
                }
                .buttonStyle(.bordered)
                .help("下一周 (Cmd+Right)")
                .keyboardShortcut(.rightArrow, modifiers: .command)

                if !appState.isViewingCurrentWeek {
                    Button(action: { appState.jumpToCurrentWeek() }) {
                        Text("回到本周")
                            .font(.system(size: 11, weight: .medium))
                    }
                    .buttonStyle(.bordered)
                    .help("返回当前教学周 (Cmd+T)")
                    .keyboardShortcut("t", modifiers: .command)
                }
            }

            Spacer()

            // 3. Actions: Import/Export, Add Course [+], More Options [...]
            HStack(spacing: 8) {
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

                Button(action: { appState.isAddingCourse = true }) {
                    Label("添加课程", systemImage: "plus")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut("n", modifiers: .command)

                Button {
                    isShowingWidgetPopover.toggle()
                } label: {
                    Label("小组件", systemImage: "rectangle.3.group")
                        .font(.system(size: 12))
                }
                .buttonStyle(.bordered)
                .help("查看桌面小组件效果")
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
                        Text("提示：可在桌面右击选择「编辑小组件」常驻到 Mac 桌面。")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .padding()
                    .frame(width: 360)
                }

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
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.bar)
    }

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
