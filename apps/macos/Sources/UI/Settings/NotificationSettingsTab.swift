import SwiftUI
import ClassFlowCore

/// Settings tab for managing macOS local notifications and class reminder alerts.
public struct NotificationSettingsTab: View {
    @Bindable public var appState: AppState

    @State private var isAuthorized: Bool = false
    @State private var statusMessage: String? = nil

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        Form {
            Section("系统通知权限") {
                HStack {
                    Text("通知状态")
                    Spacer()
                    if isAuthorized {
                        HStack(spacing: 4) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                            Text("已允许发送通知")
                                .foregroundStyle(.green)
                        }
                    } else {
                        HStack(spacing: 4) {
                            Image(systemName: "exclamationmark.circle.fill")
                                .foregroundStyle(.yellow)
                            Text("未授权或已禁用")
                                .foregroundStyle(.yellow)
                        }
                    }
                }

                if !isAuthorized {
                    Button("请求系统通知权限") {
                        Task {
                            let granted = (try? await appState.notificationService.requestAccess()) ?? false
                            await refreshStatus()
                            if granted {
                                statusMessage = "通知权限已获取！"
                            } else {
                                statusMessage = "请前往系统设置 > 通知 > ClassFlow 中开启通知权限。"
                            }
                        }
                    }
                }
            }

            Section("即将上课提醒") {
                Toggle("开启上课前提醒", isOn: $appState.settings.enableClassAlerts)

                if appState.settings.enableClassAlerts {
                    Picker("提前提醒时间", selection: $appState.settings.classAlertMinutes) {
                        Text("提前 5 分钟").tag(5)
                        Text("提前 10 分钟").tag(10)
                        Text("提前 15 分钟").tag(15)
                        Text("提前 30 分钟").tag(30)
                    }
                }
            }

            Section("每日课表早间摘要") {
                Toggle("推送每日早间课表摘要", isOn: $appState.settings.enableDailyBriefing)

                if appState.settings.enableDailyBriefing {
                    HStack {
                        Text("推送时间")
                        Spacer()
                        Picker("小时", selection: $appState.settings.dailyBriefingHour) {
                            ForEach(5...10, id: \.self) { h in
                                Text(String(format: "%02d 点", h)).tag(h)
                            }
                        }
                        .labelsHidden()
                        .frame(width: 80)

                        Text(":")

                        Picker("分钟", selection: $appState.settings.dailyBriefingMinute) {
                            ForEach([0, 15, 30, 45], id: \.self) { m in
                                Text(String(format: "%02d 分", m)).tag(m)
                            }
                        }
                        .labelsHidden()
                        .frame(width: 80)
                    }
                }
            }

            Section("提醒调度操作") {
                HStack(spacing: 12) {
                    Button("测试系统通知") {
                        Task {
                            statusMessage = "测试通知将在5秒后触发，正在等待系统送达回执…"
                            let delivered = await appState.notificationService.sendDiagnosticAlert()
                            statusMessage = delivered ? "系统已确认测试通知送达。" : "未收到送达确认，请检查系统权限与专注模式。"
                        }
                    }
                    Button("立即更新待发送提醒") {
                        Task {
                            await appState.scheduleNotifications()
                            if let status = await appState.notificationService.diagnosticStatus(),
                               let planned = status.plannedCalendarAlertCount {
                                statusMessage = status.calendarAlertCount == planned
                                    ? "系统待发送日程提醒：\(status.calendarAlertCount)条。"
                                    : "计划\(planned)条日程提醒，系统待发送\(status.calendarAlertCount)条，尚未全部确认。"
                            } else {
                                statusMessage = "已请求更新提醒；尚未获得系统队列确认。"
                            }
                        }
                    }
                    .buttonStyle(.borderedProminent)

                    Button("取消全部提醒", role: .destructive) {
                        Task {
                            await appState.notificationService.cancelAll()
                            statusMessage = "已取消所有待发送提醒。"
                        }
                    }
                }

                if let message = statusMessage {
                    Text(message)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .formStyle(.grouped)
        .task {
            await refreshStatus()
        }
    }

    private func refreshStatus() async {
        isAuthorized = await appState.notificationService.checkAccess()
    }
}
