import SwiftUI
import WidgetKit
import ClassFlowCore

/// Settings tab for previewing and configuring macOS desktop and Notification Center widgets.
public struct WidgetSettingsTab: View {
    @Bindable public var appState: AppState

    @State private var selectedPreviewTab: Int = 0 // 0: Small, 1: Medium, 2: Large
    @State private var isRefreshing: Bool = false
    @State private var statusMessage: String? = nil

    public init(appState: AppState) {
        self.appState = appState
    }

    private var currentPayload: WidgetTimelinePayload {
        let payload = appState.widgetDataStore.load()
        return payload.todayDateString.isEmpty ? makeSamplePayload() : payload
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // 1. Live Interactive Preview
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("小组件实时效果预览")
                            .font(.headline)
                        Spacer()
                        Picker("小组件规格", selection: $selectedPreviewTab) {
                            Text("下节 (S)").tag(0)
                            Text("今日 (M)").tag(1)
                            Text("周课表 (L)").tag(2)
                            Text("巨幕周表 (XL)").tag(3)
                        }
                        .pickerStyle(.segmented)
                        .frame(width: 360)
                    }

                    HStack {
                        Spacer()
                        Group {
                            switch selectedPreviewTab {
                            case 0:
                                NextClassWidgetView(payload: currentPayload)
                                    .frame(width: 155, height: 155)
                            case 1:
                                TodayClassesWidgetView(payload: currentPayload)
                                    .frame(width: 325, height: 155)
                            case 2:
                                WeekOverviewWidgetView(payload: currentPayload, explicitFamily: .systemLarge)
                                    .frame(width: 325, height: 325)
                            default:
                                WeekOverviewWidgetView(payload: currentPayload, explicitFamily: .systemExtraLarge)
                                    .frame(width: 480, height: 280)
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .shadow(color: Color.black.opacity(0.20), radius: 14, x: 0, y: 6)
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .stroke(Color.white.opacity(0.25), lineWidth: 1)
                        )
                        .padding(.vertical, 14)
                        Spacer()
                    }
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [Color.blue.opacity(0.18), Color.purple.opacity(0.15), Color.orange.opacity(0.10)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                    )
                }
                .padding()
                .background(Color(nsColor: .controlBackgroundColor).opacity(0.6))
                .clipShape(RoundedRectangle(cornerRadius: 12))

                // 2. Refresh & Data Status
                VStack(alignment: .leading, spacing: 10) {
                    Text("小组件数据状态")
                        .font(.headline)

                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text("本地存储位置：~/Library/Application Support/ClassFlow/widget_data.json")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text("最近更新时间：\(currentPayload.lastUpdated)")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button {
                            refreshWidgets()
                        } label: {
                            HStack(spacing: 5) {
                                if isRefreshing {
                                    ProgressView().controlSize(.small)
                                } else {
                                    Image(systemName: "arrow.clockwise")
                                }
                                Text("立即刷新小组件")
                            }
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 6)
                            .background(Color(hex: "#FF5A5F"))
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .disabled(isRefreshing)
                    }

                    if let msg = statusMessage {
                        Text(msg)
                            .font(.caption)
                            .foregroundStyle(.green)
                    }
                }
                .padding()
                .background(Color(nsColor: .controlBackgroundColor).opacity(0.6))
                .clipShape(RoundedRectangle(cornerRadius: 12))

                // 3. User Guide on Adding Widgets to Desktop / Notification Center
                VStack(alignment: .leading, spacing: 12) {
                    Text("如何将小组件添加到 Mac 桌面或通知中心")
                        .font(.headline)

                    VStack(alignment: .leading, spacing: 8) {
                        GuideStepRow(
                            step: "1",
                            title: "打开小组件库",
                            desc: "在 Mac 桌面任意空白处右键（或双指点按），点击「编辑小组件…」；或者点击屏幕右上角时间打开通知中心，滑到最底部点击「编辑小组件」。"
                        )

                        GuideStepRow(
                            step: "2",
                            title: "在左侧找到 ClassFlow",
                            desc: "在小组件列表侧边栏中搜索或点击「ClassFlow 课程表」。"
                        )

                        GuideStepRow(
                            step: "3",
                            title: "拖拽放置到桌面",
                            desc: "浏览「下节课程」、「今日课程」与「周课程表」，直接将其拖拽至桌面上喜欢的位置即可常驻显示。"
                        )
                    }
                }
                .padding()
                .background(Color(nsColor: .controlBackgroundColor).opacity(0.6))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding()
        }
    }

    private func refreshWidgets() {
        isRefreshing = true
        appState.updateWidgetData()
        WidgetCenter.shared.reloadAllTimelines()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            isRefreshing = false
            statusMessage = "小组件数据已成功刷新并写入共享存储！"
        }
    }

    private func makeSamplePayload() -> WidgetTimelinePayload {
        WidgetTimelinePayload(
            semesterName: appState.currentSemester?.name ?? "2026年秋季学期",
            currentWeek: appState.calculatedCurrentWeek,
            todayDateString: appState.timelineEngine.weekCalculator.formatDate(Date()),
            nextClass: WidgetCourseItem(
                id: "preview_next",
                title: "高等数学",
                location: "东上 101",
                teacher: "张教授",
                startTime: "10:00",
                endTime: "11:35",
                startSection: 3,
                endSection: 4,
                colorHex: "#4A90E2",
                isOngoing: false,
                minutesUntilStart: 25
            ),
            todayClasses: [
                WidgetCourseItem(
                    id: "preview_1",
                    title: "大学英语",
                    location: "外文楼 201",
                    teacher: "李老师",
                    startTime: "08:00",
                    endTime: "09:35",
                    startSection: 1,
                    endSection: 2,
                    colorHex: "#50E3C2"
                ),
                WidgetCourseItem(
                    id: "preview_2",
                    title: "高等数学",
                    location: "东上 101",
                    teacher: "张教授",
                    startTime: "10:00",
                    endTime: "11:35",
                    startSection: 3,
                    endSection: 4,
                    colorHex: "#4A90E2"
                )
            ]
        )
    }
}

private struct GuideStepRow: View {
    let step: String
    let title: String
    let desc: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(step)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 22, height: 22)
                .background(Color.accentColor)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.bold())
                Text(desc)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
