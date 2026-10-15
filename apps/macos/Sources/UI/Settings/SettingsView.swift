import SwiftUI
import ClassFlowCore

/// Modern card-based settings hub for macOS, replacing crude forms with
/// serene WakeUp-style cards, live toggles, and unified coral accents.
public struct SettingsView: View {
    @Bindable public var appState: AppState
    public var onDismiss: (() -> Void)? = nil

    @State private var selectedCategory: String = "general"

    public init(appState: AppState, onDismiss: (() -> Void)? = nil) {
        self.appState = appState
        self.onDismiss = onDismiss
    }

    private let categories: [(id: String, name: String, icon: String)] = [
        ("general", "通用", "gearshape"),
        ("calendar", "日历", "calendar.badge.clock"),
        ("notifications", "通知", "bell.badge"),
        ("sync", "云同步", "icloud.and.arrow.up"),
        ("about", "关于", "info.circle")
    ]

    public var body: some View {
        VStack(spacing: 0) {
            // 1. Navigation Header
            HStack {
                Text("偏好设置")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.primary)

                Spacer()

                if let dismissAction = onDismiss {
                    Button("完成") {
                        dismissAction()
                    }
                    .buttonStyle(.plain)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 5)
                    .background(Color(hex: "#FF5A5F"))
                    .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            // 2. Segmented Category Bar
            HStack(spacing: 6) {
                ForEach(categories, id: \.id) { cat in
                    let isSelected = (selectedCategory == cat.id)

                    Button(action: {
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                            selectedCategory = cat.id
                        }
                    }) {
                        HStack(spacing: 5) {
                            Image(systemName: cat.icon)
                                .font(.system(size: 12))
                            Text(cat.name)
                                .font(.system(size: 12.5, weight: isSelected ? .bold : .medium))
                        }
                        .foregroundColor(isSelected ? .white : .primary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            Capsule()
                                .fill(isSelected ? Color.primary : Color.clear)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .background(Color(nsColor: .controlBackgroundColor).opacity(0.5))

            Divider()

            // 3. Tab Content
            ScrollView {
                VStack(spacing: 16) {
                    switch selectedCategory {
                    case "general":
                        generalCategoryView
                    case "calendar":
                        CalendarSettingsTab(appState: appState)
                    case "notifications":
                        NotificationSettingsTab(appState: appState)
                    case "sync":
                        SyncSettingsTab(appState: appState)
                    default:
                        aboutCategoryView
                    }
                }
                .padding(20)
            }
        }
        .frame(width: 580, height: 520)
    }

    // MARK: - General Tab

    private var generalCategoryView: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Appearance Card
            VStack(alignment: .leading, spacing: 10) {
                Text("外观与显示")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 4)

                VStack(spacing: 0) {
                    HStack {
                        Text("主题色彩")
                            .font(.system(size: 14))
                        Spacer()
                        Picker("", selection: $appState.settings.themeMode) {
                            Text("跟随系统").tag("system")
                            Text("浅色模式").tag("light")
                            Text("深色模式").tag("dark")
                        }
                        .labelsHidden()
                        .pickerStyle(.segmented)
                        .frame(width: 200)
                        .onChange(of: appState.settings.themeMode) { _, _ in
                            appState.save()
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)

                    Divider().padding(.leading, 16)

                    HStack {
                        Text("系统菜单栏图标常驻")
                            .font(.system(size: 14))
                        Spacer()
                        Toggle("", isOn: $appState.settings.enableMenuBarExtra)
                            .labelsHidden()
                            .toggleStyle(.switch)
                            .onChange(of: appState.settings.enableMenuBarExtra) { _, _ in
                                appState.save()
                            }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .background(Color(nsColor: .controlBackgroundColor))
                .cornerRadius(12)
            }

            // Quick Jump to Appearance and Profiles
            VStack(alignment: .leading, spacing: 10) {
                Text("快速定制")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 4)

                VStack(spacing: 0) {
                    Button(action: {
                        onDismiss?()
                        appState.isShowingAppearance = true
                    }) {
                        HStack {
                            Label("课表外观与壁纸皮肤深度设置", systemImage: "paintbrush")
                                .font(.system(size: 14))
                                .foregroundColor(.primary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                    }
                    .buttonStyle(.plain)

                    Divider().padding(.leading, 16)

                    Button(action: {
                        onDismiss?()
                        appState.isShowingSectionProfiles = true
                    }) {
                        HStack {
                            Label("作息时间表设置 (节次时长与联动)", systemImage: "clock")
                                .font(.system(size: 14))
                                .foregroundColor(.primary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                    }
                    .buttonStyle(.plain)
                }
                .background(Color(nsColor: .controlBackgroundColor))
                .cornerRadius(12)
            }
        }
    }

    // MARK: - About Tab

    private var aboutCategoryView: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color(hex: "#FF5A5F"), Color(hex: "#FF8A65")],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 64, height: 64)

                Image(systemName: "calendar.badge.clock")
                    .font(.system(size: 30))
                    .foregroundColor(.white)
            }

            VStack(spacing: 4) {
                Text("ClassFlow for macOS")
                    .font(.system(size: 18, weight: .bold))

                Text("版本 1.0.0 (Native Release)")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("核心设计原则：")
                    .font(.system(size: 13, weight: .bold))

                Text("• Native First：全面采用原生 Swift / SwiftUI 开发，拒绝 Webview 性能损耗。\n• Local First：本地数据库驱动，离线即时交互，无缝平滑。\n• Bring Your Own Cloud：尊重隐私，用户自主配置存储服务，零强制账号依赖。")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .lineSpacing(4)
            }
            .padding(16)
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)
        }
        .padding(.top, 10)
    }
}
