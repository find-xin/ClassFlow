import SwiftUI
import ClassFlowCore

/// Non-modal inspection popover displayed upon clicking a course occurrence.
public struct CoursePopoverView: View {
    public let item: TimelineItem
    public let onEdit: () -> Void
    public let onQuickCancel: () -> Void
    public let onDelete: () -> Void

    public init(
        item: TimelineItem,
        onEdit: @escaping () -> Void,
        onQuickCancel: @escaping () -> Void,
        onDelete: @escaping () -> Void
    ) {
        self.item = item
        self.onEdit = onEdit
        self.onQuickCancel = onQuickCancel
        self.onDelete = onDelete
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header: Title & Badges
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.title)
                        .font(.headline)
                        .foregroundColor(.primary)

                    if let teacher = item.teacher, !teacher.isEmpty {
                        Text(teacher)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()

                Circle()
                    .fill(Color(hex: item.color) ?? .accentColor)
                    .frame(width: 12, height: 12)
            }

            Divider()

            // Time & Location Details
            VStack(alignment: .leading, spacing: 6) {
                detailRow(
                    icon: "clock",
                    title: "时间",
                    content: "第 \(item.startSection)-\(item.endSection) 节 (\(item.startTime ?? "--") ~ \(item.endTime ?? "--"))"
                )

                if let location = item.location, !location.isEmpty {
                    detailRow(
                        icon: "mappin.and.ellipse",
                        title: "教室",
                        content: location
                    )
                }

                detailRow(
                    icon: "calendar",
                    title: "日期",
                    content: "\(item.date) (第 \(item.weekNumber) 周)"
                )

                if let notes = item.notes, !notes.isEmpty {
                    detailRow(
                        icon: "note.text",
                        title: "备注",
                        content: notes
                    )
                }
            }

            // Conflict warning banner if applicable
            if item.hasConflict {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("该时间段与其他课程发生重叠冲突")
                        .font(.caption)
                        .foregroundColor(.primary)
                }
                .padding(8)
                .background(Color.orange.opacity(0.1))
                .cornerRadius(6)
            }

            Divider()

            // Action Buttons
            HStack(spacing: 8) {
                Button(role: .destructive, action: onDelete) {
                    Label("删除", systemImage: "trash")
                }
                .buttonStyle(.bordered)

                Spacer()

                if item.status != .cancelled {
                    Button(action: onQuickCancel) {
                        Text("本次停课")
                    }
                    .buttonStyle(.bordered)
                }

                Button(action: onEdit) {
                    Label("编辑", systemImage: "pencil")
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(14)
        .frame(width: 280)
    }

    private func detailRow(icon: String, title: String, content: String) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Image(systemName: icon)
                .frame(width: 14)
                .foregroundColor(.secondary)
                .font(.system(size: 11))
            Text(title + ":")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
            Text(content)
                .font(.system(size: 12))
                .foregroundColor(.primary)
        }
    }
}
