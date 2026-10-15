import SwiftUI
import ClassFlowCore

/// Sheet displaying pre-commit diagnostics for imported .classflow packages,
/// matching WakeUp's serene card design language.
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
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.primary)
                    Text("检测到合法的 ClassFlow 格式备份包")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
                Spacer()
                Button("取消", action: onDismiss)
                    .buttonStyle(.plain)
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            ScrollView {
                VStack(spacing: 16) {
                    // 1. Diagnostics Overview Card
                    VStack(alignment: .leading, spacing: 10) {
                        Text("数据概览")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)

                        VStack(spacing: 8) {
                            statRow(title: "包含学期", value: preview.semesterNames.joined(separator: ", "))
                            Divider()
                            statRow(title: "课程总数", value: "\(preview.totalCoursesCount) 门")
                            Divider()
                            statRow(title: "新增课程", value: "\(preview.newCoursesCount) 门")
                            if preview.conflictingCoursesCount > 0 {
                                Divider()
                                statRow(title: "同名冲突课程", value: "\(preview.conflictingCoursesCount) 门 (已存在同名)")
                            }
                            Divider()
                            statRow(title: "例外规则", value: "\(preview.exceptionsCount) 条调课/停课记录")
                        }
                        .padding(14)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(Color(nsColor: .controlBackgroundColor))
                        )
                    }

                    // 2. Import Strategy Radio Cards
                    VStack(alignment: .leading, spacing: 10) {
                        Text("导入策略")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.secondary)

                        VStack(spacing: 10) {
                            strategyCard(
                                mode: .merge,
                                title: "合并导入 (推荐)",
                                subtitle: "保留当前所有课表，仅增量补充新学期与新课程"
                            )

                            strategyCard(
                                mode: .replace,
                                title: "覆盖导入",
                                subtitle: "同名冲突学期与课程将被新备份包内容完全覆盖"
                            )
                        }
                    }
                }
                .padding(20)
            }

            Divider()

            // Footer Action
            HStack {
                Spacer()
                Button(action: {
                    onConfirm(selectedMode)
                }) {
                    Text("确认开始导入")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 22)
                        .padding(.vertical, 8)
                        .background(Color(hex: "#FF5A5F"))
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .frame(width: 480, height: 460)
    }

    private func statRow(title: String, value: String) -> some View {
        HStack {
            Text(title)
                .foregroundColor(.secondary)
                .font(.system(size: 13))
            Spacer()
            Text(value)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.primary)
        }
    }

    private func strategyCard(mode: ImportMode, title: String, subtitle: String) -> some View {
        let isSelected = (selectedMode == mode)

        return Button(action: {
            selectedMode = mode
        }) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .stroke(isSelected ? Color(hex: "#FF5A5F") : Color.secondary.opacity(0.35), lineWidth: 1.5)
                        .frame(width: 20, height: 20)

                    if isSelected {
                        Circle()
                            .fill(Color(hex: "#FF5A5F"))
                            .frame(width: 10, height: 10)
                    }
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundColor(.primary)

                    Text(subtitle)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }

                Spacer()
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(nsColor: .controlBackgroundColor))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(isSelected ? Color(hex: "#FF5A5F").opacity(0.5) : Color.clear, lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }
}
