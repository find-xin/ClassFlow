import SwiftUI
import AppKit
import ClassFlowCore

@main
struct ClassFlowApp: App {
    @State private var appState = AppState()

    var body: some Scene {
        // 1. Primary Application Window
        WindowGroup {
            MainWindowView(appState: appState)
                .preferredColorScheme(colorScheme(for: appState.settings.themeMode))
                .onOpenURL { url in
                    appState.handleDeepLink(url)
                }
        }
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: 1020, height: 720)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("新建课程...") {
                    appState.isAddingCourse = true
                }
                .keyboardShortcut("n", modifiers: .command)

                Button("新建日程 / 待办...") {
                    appState.startAddingCalendarEvent()
                }
                .keyboardShortcut("d", modifiers: [.command, .shift])

                Button("新建学期课表...") {
                    appState.isShowingCreateSemester = true
                }
                .keyboardShortcut("n", modifiers: [.command, .shift])
            }

            CommandMenu("课表") {
                Button("作息时间表设置...") {
                    appState.isShowingSectionProfiles = true
                }
                .keyboardShortcut("t", modifiers: .command)

                Button("已选课程列表...") {
                    appState.isShowingCourseList = true
                }
                .keyboardShortcut("l", modifiers: .command)



                Divider()

                Button("课表外观设置...") {
                    appState.isShowingAppearance = true
                }

                Button("桌面与通知中心小组件...") {
                    appState.isShowingWidgets = true
                }

                Divider()

                Button("上一周") {
                    appState.previousWeek()
                }
                .keyboardShortcut("[", modifiers: .command)

                Button("下一周") {
                    appState.nextWeek()
                }
                .keyboardShortcut("]", modifiers: .command)

                Button("回到本周") {
                    appState.jumpToCurrentWeek()
                }
                .keyboardShortcut("0", modifiers: .command)
            }
        }

        // 2. Menu Bar Companion
        MenuBarExtra("ClassFlow", systemImage: "calendar.badge.clock", isInserted: .constant(appState.settings.enableMenuBarExtra)) {
            MenuBarCompanionView(appState: appState)
        }
        .menuBarExtraStyle(.window)

        // 3. Native macOS Settings Window (Cmd+,)
        Settings {
            SettingsView(appState: appState)
        }
    }

    private func colorScheme(for theme: String) -> ColorScheme? {
        switch theme {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }
}
