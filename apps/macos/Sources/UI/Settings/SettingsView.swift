import SwiftUI
import ClassFlowCore

/// Native macOS settings window with tabbed categories.
public struct SettingsView: View {
    @Bindable public var appState: AppState

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        TabView {
            generalTab
                .tabItem {
                    Label("通用", systemImage: "gearshape")
                }

            timetableTab
                .tabItem {
                    Label("课程表", systemImage: "calendar")
                }

            semestersTab
                .tabItem {
                    Label("学期管理", systemImage: "books.vertical")
                }

            CalendarSettingsTab(appState: appState)
                .tabItem {
                    Label("日历", systemImage: "calendar.badge.clock")
                }

            NotificationSettingsTab(appState: appState)
                .tabItem {
                    Label("通知", systemImage: "bell.badge")
                }

            SyncSettingsTab(appState: appState)
                .tabItem {
                    Label("云同步", systemImage: "icloud.and.arrow.up")
                }

            aboutTab
                .tabItem {
                    Label("关于", systemImage: "info.circle")
                }
        }
        .frame(width: 520, height: 400)
        .padding()
    }

    private var generalTab: some View {
        Form {
            Section("界面外观与系统") {
                Picker("主题色彩", selection: $appState.settings.themeMode) {
                    Text("跟随系统").tag("system")
                    Text("浅色模式").tag("light")
                    Text("深色模式").tag("dark")
                }

                Toggle("在系统菜单栏显示 ClassFlow 常驻状态", isOn: $appState.settings.enableMenuBarExtra)
            }
        }
        .formStyle(.grouped)
    }

    private var timetableTab: some View {
        Form {
            Section("课表视图选项") {
                Toggle("显示周六和周日 (7列视图)", isOn: $appState.settings.showWeekends)
                Toggle("在课表中显示非本周课程", isOn: $appState.settings.showNonCurrentWeekCourses)
                Toggle("淡化显示非本周课程 (半透明)", isOn: $appState.settings.dimNonCurrentWeekCourses)

                Slider(value: $appState.settings.cardOpacity, in: 0.5...1.0, step: 0.05) {
                    Text("课程卡片不透明度: \(Int(appState.settings.cardOpacity * 100))%")
                }
            }
        }
        .formStyle(.grouped)
    }

    private var semestersTab: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("学期列表")
                .font(.headline)

            List(appState.semesters) { sem in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(sem.name)
                            .font(.system(size: 13, weight: .medium))
                        Text("共 \(sem.totalWeeks) 周 | \(sem.startDate) 起")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    if sem.isCurrent {
                        Text("当前学期")
                            .font(.caption)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.accentColor.opacity(0.15))
                            .foregroundColor(.accentColor)
                            .clipShape(Capsule())
                    } else {
                        Button("设为当前") {
                            appState.selectSemester(id: sem.id)
                        }
                        .buttonStyle(.bordered)
                        .font(.caption)
                    }
                }
                .padding(.vertical, 2)
            }
        }
        .padding()
    }

    private var aboutTab: some View {
        VStack(spacing: 12) {
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 44))
                .foregroundColor(.accentColor)

            Text("ClassFlow for macOS")
                .font(.title2)
                .bold()

            Text("版本 1.0.0 (Phase 3 Native Release)")
                .font(.subheadline)
                .foregroundColor(.secondary)

            Text("ClassFlow 是一款面向大学生的高质量开源、原生跨平台课程表。\n坚持 Native First、Local First 与 Bring Your Own Cloud 原则。")
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
                .padding(.horizontal)

            Spacer()
        }
        .padding()
    }
}
