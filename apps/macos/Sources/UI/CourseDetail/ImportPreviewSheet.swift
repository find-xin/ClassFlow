import SwiftUI
import ClassFlowCore

/// Sheet displaying pre-commit diagnostics for imported .classflow packages.
public struct ImportPreviewSheet: View {
    public let preview: ImportPreview
    public let onConfirm: (_ mode: ImportMode) -> Void
    public let onDismiss: () -> Void

    public enum ImportMode: String, CaseIterable {
        case merge = "合并导入 (推荐: 保留现有数据)"
        case replace = "替换导入 (覆盖冲突学期与课程)"
    }

    @State private var selectedMode: ImportMode = .merge

    public init(
        preview: ImportPreview,
        onConfirm: @escaping (_ mode: ImportMode) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.preview = preview
        self.onConfirm = onConfirm
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("导入课表文件预览")
                        .font(.headline)
                    Text("检测到合法的 ClassFlow 原生备份包")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Button("取消", action: onDismiss)
                    .keyboardShortcut(.cancelAction)
            }
            .padding()
            Divider()

            // Diagnostics Summary
            VStack(alignment: .leading, spacing: 14) {
                GroupBox("数据概览") {
                    VStack(alignment: .leading, spacing: 8) {
                        statRow(title: "包含学期", value: preview.semesterNames.joined(separator: ", "))
                        statRow(title: "课程总数", value: "\(preview.totalCoursesCount) 门")
                        statRow(title: "新课程", value: "\(preview.newCoursesCount) 门")
                        if preview.conflictingCoursesCount > 0 {
                            statRow(title: "同名冲突课程", value: "\(preview.conflictingCoursesCount) 门 (同名已存在)")
                        }
                        statRow(title: "例外规则", value: "\(preview.exceptionsCount) 条调课/停课记录")
                    }
                    .padding(4)
                }

                // Mode Selection
                VStack(alignment: .leading, spacing: 6) {
                    Text("导入策略")
                        .font(.subheadline)
                        .bold()

                    Picker("", selection: $selectedMode) {
                        ForEach(ImportMode.allCases, id: \.self) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                    .pickerStyle(.radioGroup)
                }
            }
            .padding()

            Spacer()
            Divider()

            // Actions
            HStack {
                Spacer()
                Button("开始导入") {
                    onConfirm(selectedMode)
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
            }
            .padding()
        }
        .frame(width: 440, height: 380)
    }

    private func statRow(title: String, value: String) -> some View {
        HStack {
            Text(title)
                .foregroundColor(.secondary)
                .font(.system(size: 12))
            Spacer()
            Text(value)
                .font(.system(size: 12, weight: .medium))
        }
    }
}
