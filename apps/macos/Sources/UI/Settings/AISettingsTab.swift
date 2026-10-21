import SwiftUI

/// Native connection settings for ChatGPT integration.
public struct AISettingsTab: View {
    @Bindable public var appState: AppState

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        Form {
            TunnelSettingsSection()
        }
    }
}
