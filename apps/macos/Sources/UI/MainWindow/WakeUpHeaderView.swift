import SwiftUI
import AppKit
import ClassFlowCore

/// Header bar replicating WakeUp's minimalist typography and ghost action icons.
public struct WakeUpHeaderView: View {
    @Bindable public var appState: AppState
    @Binding public var isShowingWeekDrawer: Bool
    @Binding public var isShowingQuickMenu: Bool

    @State private var isShowingExportAlert: Bool = false
    @State private var exportMessage: String = ""

    public init(
        appState: AppState,
        isShowingWeekDrawer: Binding<Bool>,
        isShowingQuickMenu: Binding<Bool>
    ) {
        self.appState = appState
        self._isShowingWeekDrawer = isShowingWeekDrawer
        self._isShowingQuickMenu = isShowingQuickMenu
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
            // 1. Left: Clickable Week and Date Title (Toggles Week & Timetable Drawer)
            Button(action: {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                    isShowingWeekDrawer.toggle()
                }
            }) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text("第\(appState.selectedWeek)周 周\(todayWeekdayShort)")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.primary)

                        Image(systemName: "chevron.down")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)
                            .rotationEffect(.degrees(isShowingWeekDrawer ? 180 : 0))
                    }

                    Text(currentDateSlashFormat)
                        .font(.system(size: 11, weight: .medium, design: .monospaced))
                        .foregroundColor(.secondary.opacity(0.85))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .help("点击展开周数滑块与课表切换面板")

            Spacer()

            // 2. Right: 4 Sleek Ghost Action Icons
            HStack(spacing: 14) {
                // (+) Add Course
                ghostButton(systemName: "plus", helpText: "添加课程") {
                    appState.isAddingCourse = true
                }

                // Import / Export
                Menu {
                    Button("从 .classflow / .ics 文件导入...") {
                        openImportDialog()
                    }
                    Divider()
                    Button("导出当前课表为日历 (.ics)...") {
                        exportICSDialog()
                    }
                    Button("导出完整备份包 (.classflow)...") {
                        exportBackupDialog()
                    }
                } label: {
                    ghostIcon(systemName: "square.and.arrow.down")
                }
                .menuStyle(.borderlessButton)
                .help("导入或导出课表文件")

                // Share / Quick Export
                ghostButton(systemName: "square.and.arrow.up", helpText: "分享与导出当前课表") {
                    exportICSDialog()
                }

                // (...) More / Quick Menu Drawer Toggle
                ghostButton(systemName: "ellipsis", helpText: "快捷功能菜单") {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        isShowingQuickMenu.toggle()
                    }
                }
            }
        }
    }

    private func ghostButton(systemName: String, helpText: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ghostIcon(systemName: systemName)
        }
        .buttonStyle(.plain)
        .help(helpText)
    }

    private func ghostIcon(systemName: String) -> some View {
        ZStack {
            Circle()
                .fill(Color.primary.opacity(0.06))
                .frame(width: 34, height: 34)

            Image(systemName: systemName)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(.primary)
        }
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
