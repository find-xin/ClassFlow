import SwiftUI
import ClassFlowCore

@main
struct ClassFlowApp: App {
    @State private var appState = AppState()

    var body: some Scene {
        // 1. Primary Application Window
        WindowGroup {
            MainWindowView(appState: appState)
                .preferredColorScheme(colorScheme(for: appState.settings.themeMode))
        }
        .windowStyle(.titleBar)
        .windowToolbarStyle(.unified)
        .defaultSize(width: 1020, height: 720)

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
