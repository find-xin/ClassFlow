import SwiftUI
import ClassFlowCore

/// Floating bottom-right quick actions drawer replicating WakeUp's 8-button shortcut grid.
public struct WakeUpQuickMenuDrawer: View {
    @Bindable public var appState: AppState
    @Binding public var isPresented: Bool

    @State private var isShowingCourseList: Bool = false

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

                gridItem(icon: "archivebox", label: "事项管理") {
                    isShowingCourseList = true
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
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.2), lineWidth: 0.5)
                )
                .shadow(color: Color.black.opacity(0.12), radius: 16, x: 0, y: 6)
        )
        .sheet(isPresented: $isShowingCourseList) {
            UnifiedEntityManagerSheet(appState: appState)
        }
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
            .frame(width: 60)
        }
        .buttonStyle(.plain)
    }
}

