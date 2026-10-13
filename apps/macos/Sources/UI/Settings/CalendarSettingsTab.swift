import SwiftUI
import ClassFlowCore

/// Settings tab for macOS EventKit Calendar synchronization.
public struct CalendarSettingsTab: View {
    @Bindable public var appState: AppState

    @State private var accessStatus: CalendarAccessStatus = .notDetermined
    @State private var isExecuting: Bool = false
    @State private var statusMessage: String? = nil
    @State private var isShowingClearAlert: Bool = false

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        Form {
            Section("系统日历授权状态") {
                HStack {
                    Text("日历访问权限")
                    Spacer()
                    switch accessStatus {
                    case .fullAccess:
                        HStack(spacing: 4) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                            Text("已完整授权 (Full Access)")
                                .foregroundStyle(.green)
                        }
                    case .denied:
                        HStack(spacing: 4) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(.red)
                            Text("已拒绝访问")
                                .foregroundStyle(.red)
                        }
                    case .restricted:
                        HStack(spacing: 4) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(.yellow)
                            Text("权限受限")
                                .foregroundStyle(.yellow)
                        }
                    case .notDetermined:
                        HStack(spacing: 4) {
                            Image(systemName: "questionmark.circle.fill")
                                .foregroundStyle(.secondary)
                            Text("未设置")
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                if accessStatus != .fullAccess {
                    Button("请求系统日历权限") {
                        Task {
                            let granted = (try? await appState.calendarService.requestAccess()) ?? false
                            await refreshStatus()
                            if granted {
                                statusMessage = "已成功获取系统日历权限！"
                            } else {
                                statusMessage = "未能获取系统日历权限，请前往系统设置 > 隐私与安全性 > 日历中开启。"
                            }
                        }
                    }
                }
            }

            Section("同步配置选项") {
                TextField("目标日历名称", text: $appState.settings.calendarName)

                Picker("上课前提醒", selection: Binding(
                    get: { appState.settings.calendarReminderMinutes ?? 0 },
                    set: { appState.settings.calendarReminderMinutes = $0 == 0 ? nil : $0 }
                )) {
                    Text("不提醒").tag(0)
                    Text("提前 5 分钟").tag(5)
                    Text("提前 10 分钟").tag(10)
                    Text("提前 15 分钟").tag(15)
                    Text("提前 30 分钟").tag(30)
                }

                Toggle("在日历事件备注中包含教师与节次信息", isOn: $appState.settings.calendarIncludeNotes)

                if let lastSync = appState.settings.lastCalendarSyncTime {
                    HStack {
                        Text("上次同步时间")
                        Spacer()
                        Text(lastSync)
                            .foregroundStyle(.secondary)
                    }
                }

                HStack {
                    Text("已映射同步日程数")
                    Spacer()
                    Text("\(appState.calendarMappings.count) 个")
                        .foregroundStyle(.secondary)
                }
            }

            Section("日历同步操作") {
                HStack(spacing: 12) {
                    Button {
                        performCalendarSync()
                    } label: {
                        HStack {
                            if isExecuting {
                                ProgressView()
                                    .controlSize(.small)
                            }
                            Text("同步课程到系统日历")
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isExecuting || accessStatus != .fullAccess)

                    Button("清空已同步日程", role: .destructive) {
                        isShowingClearAlert = true
                    }
                    .disabled(isExecuting || appState.calendarMappings.isEmpty)
                }

                if let message = statusMessage {
                    Text(message)
                        .font(.caption)
                        .foregroundStyle(message.contains("失败") || message.contains("未能") ? .red : .secondary)
                }
            }
        }
        .formStyle(.grouped)
        .task {
            await refreshStatus()
        }
        .alert("确认清空已同步日历？", isPresented: $isShowingClearAlert) {
            Button("取消", role: .cancel) {}
            Button("确认清空", role: .destructive) {
                performClearCalendar()
            }
        } message: {
            Text("将从系统日历「\(appState.settings.calendarName)」中移除所有 ClassFlow 相关的课程日程。")
        }
    }

    private func refreshStatus() async {
        accessStatus = await appState.calendarService.checkAccess()
    }

    private func performCalendarSync() {
        isExecuting = true
        statusMessage = "正在同步课程到系统日历..."
        Task {
            do {
                let result = try await appState.syncToSystemCalendar()
                await refreshStatus()
                isExecuting = false
                statusMessage = "同步成功：新增 \(result.createdCount) 个，更新 \(result.updatedCount) 个，删除 \(result.deletedCount) 个日程。"
            } catch {
                isExecuting = false
                statusMessage = "同步失败：\(error.localizedDescription)"
            }
        }
    }

    private func performClearCalendar() {
        isExecuting = true
        statusMessage = "正在清空系统日历..."
        Task {
            do {
                let count = try await appState.clearSystemCalendar()
                await refreshStatus()
                isExecuting = false
                statusMessage = "已成功移除 \(count) 个已同步的日程。"
            } catch {
                isExecuting = false
                statusMessage = "清空失败：\(error.localizedDescription)"
            }
        }
    }
}
