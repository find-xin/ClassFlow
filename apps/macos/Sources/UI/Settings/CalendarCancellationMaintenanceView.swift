import SwiftUI
import ClassFlowCore

struct CalendarCancellationMaintenanceView: View {
    @Bindable var appState: AppState
    @State private var plan: CalendarCancellationCleanup.Plan?
    @State private var removeRedundant = true
    @State private var message: String?
    @State private var applying = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("重复日程取消记录维护").font(.headline)
            Text("将历史取消记录转为系列的永久排除日期。课程停课、调课及冲突记录会保留。执行前自动备份。")
                .font(.caption).foregroundStyle(.secondary)
            Toggle("迁移后清理冗余取消记录", isOn: $removeRedundant).onChange(of: removeRedundant) { plan = nil }
            Button("预览批量清理") { plan = appState.previewCalendarCancellationCleanup(removeRedundant: removeRedundant); message = nil }
            if let plan {
                Text("涉及 \(plan.affectedSeries.count) 个系列；新增 \(plan.migratedDates) 个排除日期；清理 \(plan.removed.count) 条记录（重复 \(plan.duplicateRecords) 条）；保留 \(plan.skippedRecords) 条受保护或需核对的取消记录。")
                    .font(.caption).textSelection(.enabled)
                ScrollView {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(plan.affectedSeries) { event in
                            Text("\(event.title)：\(event.excludedDates.joined(separator: "、"))").font(.caption)
                        }
                    }
                }.frame(maxHeight: 110)
                Button("执行上述迁移与清理") {
                    applying = true
                    Task {
                        do { try await appState.applyCalendarCancellationCleanup(plan); message = "已保存永久排除日期并完成清理。"; self.plan = nil }
                        catch { message = error.localizedDescription; self.plan = nil }
                        applying = false
                    }
                }.disabled(applying || (plan.changedSeries == 0 && plan.removed.isEmpty))
            }
            if let message { Text(message).font(.caption).textSelection(.enabled) }
        }.padding(14).background(.background, in: RoundedRectangle(cornerRadius: 12))
    }
}
