import SwiftUI
import ClassFlowCore

/// Comprehensive user profile and settings hub for the "我的" tab,
/// offering centralized access to appearance, timetables, calendar sync, backup, and app preferences.
public struct UserProfileView: View {
    @Bindable public var appState: AppState

    @State private var isShowingCourseList: Bool = false

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Clean Top Bar with Window Controls Spacing
            HStack {
                Text("个人中心与设置")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.primary)

                Spacer()
            }
            .padding(.leading, 78)
            .padding(.trailing, 20)
            .padding(.top, 14)
            .padding(.bottom, 12)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    // 1. Security & Offline-First App Info Header
                    HStack(spacing: 16) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(
                                    LinearGradient(
                                        colors: [Color(hex: "#FF5A5F"), Color(hex: "#5B7FB5")],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 52, height: 52)
                                .shadow(color: Color(hex: "#FF5A5F").opacity(0.3), radius: 6, y: 3)

                            Image(systemName: "checkmark.shield.fill")
                                .font(.system(size: 24))
                                .foregroundColor(.white)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Text("ClassFlow")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.primary)
                                Text("本地优先")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color(hex: "#56A3A6"))
                                    .clipShape(Capsule())
                            }

                            Text("数据离线单机运行 · 零后台强制追踪 · 支持个人私有云安全漫游")
                                .font(.system(size: 11.5))
                                .foregroundColor(.secondary)
                        }

                        Spacer()
                    }
                    .padding(18)
                    .background(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                            .overlay(
                                RoundedRectangle(cornerRadius: 18, style: .continuous)
                                    .strokeBorder(Color.white.opacity(0.15), lineWidth: 0.5)
                            )
                            .shadow(color: Color.black.opacity(0.03), radius: 6, y: 2)
                    )

                    // 2. Timetable Management (课表与课程配置)
                    VStack(alignment: .leading, spacing: 10) {
                        Text("课表与课程管理")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 4)

                        VStack(spacing: 0) {


                            profileRow(icon: "clock", title: "作息时间表设置", desc: "设置各节次上下课时段、大节课联动、多时间表管理") {
                                appState.isShowingSectionProfiles = true
                            }
                            Divider().padding(.leading, 46)

                            profileRow(icon: "books.vertical", title: "已选课程库", desc: "集中查看当前学期所有课程详情与节次安排") {
                                isShowingCourseList = true
                            }
                            Divider().padding(.leading, 46)

                            profileRow(icon: "arrow.triangle.2.circlepath", title: "系统日历双向同步", desc: "将课表日程同步至 macOS 系统日历与提醒事项") {
                                appState.isShowingSettings = true
                            }
                        }
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                                .shadow(color: Color.black.opacity(0.03), radius: 6, y: 2)
                        )
                    }

                    // 3. Appearance & Desktop (外观与小组件)
                    VStack(alignment: .leading, spacing: 10) {
                        Text("外观与桌面组件")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 4)

                        VStack(spacing: 0) {
                            profileRow(icon: "paintbrush", title: "课表外观与皮肤", desc: "毛玻璃半透明壁纸、网格圆角、文字对比度增强") {
                                appState.isShowingAppearance = true
                            }
                            Divider().padding(.leading, 46)

                            profileRow(icon: "iphone", title: "桌面小组件", desc: "配置 macOS 桌面与通知中心实时课表卡片") {
                                appState.isShowingWidgets = true
                            }
                            Divider().padding(.leading, 46)

                            HStack {
                                ZStack {
                                    Circle()
                                        .fill(Color.primary.opacity(0.06))
                                        .frame(width: 32, height: 32)
                                    Image(systemName: "circle.lefthalf.filled")
                                        .font(.system(size: 14))
                                        .foregroundColor(.primary)
                                }

                                Text("界面主题模式")
                                    .font(.system(size: 14, weight: .medium))

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

                            Divider().padding(.leading, 46)

                            HStack {
                                ZStack {
                                    Circle()
                                        .fill(Color.primary.opacity(0.06))
                                        .frame(width: 32, height: 32)
                                    Image(systemName: "menubar.arrow.up.rectangle")
                                        .font(.system(size: 14))
                                        .foregroundColor(.primary)
                                }

                                Text("在系统菜单栏常驻显示 ClassFlow")
                                    .font(.system(size: 14, weight: .medium))

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

                            Divider().padding(.leading, 46)

                            HStack {
                                ZStack {
                                    Circle()
                                        .fill(Color(hex: "#FF5A5F").opacity(0.12))
                                        .frame(width: 32, height: 32)
                                    Image(systemName: "timer")
                                        .font(.system(size: 14))
                                        .foregroundColor(Color(hex: "#FF5A5F"))
                                }

                                VStack(alignment: .leading, spacing: 2) {
                                    Text("启用「学习与番茄钟」模块")
                                        .font(.system(size: 14, weight: .medium))
                                    Text("关闭后停用番茄专注时钟与热力统计，完全释放内存与资源")
                                        .font(.system(size: 11))
                                        .foregroundColor(.secondary)
                                }

                                Spacer()

                                Toggle("", isOn: $appState.settings.enableStudyModule)
                                    .labelsHidden()
                                    .toggleStyle(.switch)
                                    .onChange(of: appState.settings.enableStudyModule) { _, enabled in
                                        if !enabled && appState.settings.selectedTab == "study" {
                                            appState.settings.selectedTab = "courses"
                                        }
                                        appState.save()
                                    }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                        }
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                                .shadow(color: Color.black.opacity(0.03), radius: 6, y: 2)
                        )
                    }

                    // 4. Cloud & Multi-Device Sync (云端漫游与生态互联)
                    VStack(alignment: .leading, spacing: 10) {
                        Text("多端与云端同步")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 4)

                        VStack(spacing: 0) {
                            profileRow(icon: "cloud.fill", title: "WebDAV 私有云同步 (坚果云等)", desc: "配置个人云存储，多设备无缝自动同步课表与番茄记录") {
                                appState.isShowingSettings = true
                            }
                            Divider().padding(.leading, 46)

                            profileRow(icon: "externaldrive.badge.icloud", title: "七牛云 Kodo / S3 兼容对象存储", desc: "使用个人 AK/SK 密钥进行加密异地增量备份") {
                                appState.isShowingSettings = true
                            }
                            Divider().padding(.leading, 46)

                            profileRow(icon: "applewatch", title: "智能穿戴联动 (小米手环 10 等)", desc: "快应用一键传输，手环即时查看下节课与今日日程") {
                                appState.isShowingSettings = true
                            }
                        }
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                                .shadow(color: Color.black.opacity(0.03), radius: 6, y: 2)
                        )
                    }

                    // 5. About & Version
                    VStack(spacing: 6) {
                        Text("ClassFlow for macOS")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.primary.opacity(0.8))

                        Text("版本 1.0.0 · Native First & Bring Your Own Cloud")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                    .padding(.top, 4)
                }
                .padding(.horizontal, 24)
                .padding(.top, 4)
                .padding(.bottom, 28)
            }
        }
        .sheet(isPresented: $isShowingCourseList) {
            WakeUpCourseListSheet(appState: appState)
        }
    }

    private func profileRow(icon: String, title: String, desc: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(Color.primary.opacity(0.06))
                        .frame(width: 32, height: 32)
                    Image(systemName: icon)
                        .font(.system(size: 14))
                        .foregroundColor(.primary)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.primary)
                    Text(desc)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.secondary.opacity(0.5))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
