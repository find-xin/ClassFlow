import SwiftUI
import ClassFlowCore

/// Non-modal inspection popover displayed upon clicking a course occurrence,
/// designed with WakeUp's pastel aesthetics and crisp typography.
public struct CoursePopoverView: View {
    public let item: TimelineItem
    public let onEdit: () -> Void
    public let onReschedule: () -> Void
    public let onQuickCancel: () -> Void
    public let onRestore: (() -> Void)?
    public let onDelete: () -> Void
    public let onDeleteScope: ((EditingScope) -> Void)?
    public let onStartFocus: (() -> Void)?

    @State private var isConfirmingCancel: Bool = false
    @State private var isConfirmingDelete: Bool = false

    public init(
        item: TimelineItem,
        onEdit: @escaping () -> Void,
        onReschedule: @escaping () -> Void = {},
        onQuickCancel: @escaping () -> Void,
        onRestore: (() -> Void)? = nil,
        onDelete: @escaping () -> Void,
        onDeleteScope: ((EditingScope) -> Void)? = nil,
        onStartFocus: (() -> Void)? = nil
    ) {
        self.item = item
        self.onEdit = onEdit
        self.onReschedule = onReschedule
        self.onQuickCancel = onQuickCancel
        self.onRestore = onRestore
        self.onDelete = onDelete
        self.onDeleteScope = onDeleteScope
        self.onStartFocus = onStartFocus
    }

    private var themeColor: Color {
        Color(hex: item.color, defaultColor: Color(hex: "#5B7FB5"))
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Header: Title, Teacher & Color Pill
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(item.title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.primary)

                        if item.sourceType == .calendarEvent {
                            Text("日程")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(Color(hex: "#34C759"))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color(hex: "#34C759").opacity(0.12))
                                .clipShape(Capsule())
                        }
                    }

                    if let teacher = item.teacher, !teacher.isEmpty {
                        Text(teacher)
                            .font(.system(size: 12.5, weight: .medium))
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()

                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(themeColor)
                    .frame(width: 8, height: 28)
            }

            Divider()

            // Time & Location Details
            VStack(alignment: .leading, spacing: 8) {
                let timeString: String = {
                    if item.startSection > 0 && item.endSection > 0 {
                        return "第 \(item.startSection)-\(item.endSection) 节 (\(item.startTime ?? "--") ~ \(item.endTime ?? "--"))"
                    } else if let s = item.startTime, let e = item.endTime {
                        return "\(s) ~ \(e)"
                    } else {
                        return item.startTime ?? "全天"
                    }
                }()

                detailRow(
                    icon: "clock",
                    title: "时间",
                    content: timeString
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
            if item.status == .cancelled {
                HStack(spacing: 6) {
                    Image(systemName: "pause.circle.fill")
                        .foregroundColor(.orange)
                    Text("本次课程已停课")
                        .font(.caption.bold())
                        .foregroundColor(.primary)
                    Spacer()
                    if onRestore != nil {
                        Button("恢复上课") {
                            onRestore?()
                        }
                        .font(.caption.bold())
                        .foregroundColor(Color(hex: "#56A3A6"))
                    }
                }
                .padding(8)
                .background(Color.orange.opacity(0.12))
                .cornerRadius(8)
            }

            if item.hasConflict {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("该时间段与其他课程发生重叠冲突")
                        .font(.caption)
                        .foregroundColor(.primary)
                }
                .padding(8)
                .background(Color.orange.opacity(0.12))
                .cornerRadius(8)
            }

            // Quick Focus Linkage Button (Only for courses, not for calendar schedules)
            if item.sourceType == .course && onStartFocus != nil && item.status != .cancelled {
                Button(action: {
                    onStartFocus?()
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "timer")
                            .font(.system(size: 12, weight: .bold))
                        Text("开启课程专注")
                            .font(.system(size: 12, weight: .semibold))
                        Spacer()
                        Text("番茄钟")
                            .font(.system(size: 10, weight: .medium))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.white.opacity(0.2))
                            .clipShape(Capsule())
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(
                        LinearGradient(
                            colors: [Color(hex: "#FF5A5F"), Color(hex: "#FF7B54")],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .buttonStyle(.plain)
                .help("立即跳转到学习页面，针对该课程开启番茄专注时钟")
            }

            Divider()

            // Action Buttons
            HStack(spacing: 8) {
                Button(role: .destructive, action: {
                    isConfirmingDelete = true
                }) {
                    Image(systemName: "trash")
                        .foregroundColor(.red)
                }
                .buttonStyle(.bordered)
                .help(item.sourceType == .calendarEvent ? "删除此日程" : "删除此课程")

                Spacer()

                if item.sourceType == .course {
                    if item.status == .cancelled {
                        Button(action: {
                            onRestore?()
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.counterclockwise")
                                Text("恢复本次上课")
                            }
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundColor(Color(hex: "#56A3A6"))
                        }
                        .buttonStyle(.bordered)
                        .help("撤销停课标记，恢复正常上课")
                    } else {
                        Button(action: onReschedule) {
                            HStack(spacing: 3) {
                                Image(systemName: "arrow.triangle.swap")
                                Text("临时调课")
                            }
                            .font(.system(size: 11.5, weight: .medium))
                        }
                        .buttonStyle(.bordered)
                        .help("仅对本次或指定周次进行调课调整")

                        Button(action: {
                            isConfirmingCancel = true
                        }) {
                            Text("本次停课")
                                .font(.system(size: 11.5))
                        }
                        .buttonStyle(.bordered)
                        .help("停掉本次课时，停课后可随时点击恢复")
                    }

                    Button(action: onEdit) {
                        HStack(spacing: 4) {
                            Image(systemName: "pencil")
                            Text("编辑")
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 5)
                        .background(Color(hex: "#FF5A5F"))
                        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                    }
                    .buttonStyle(.plain)
                } else {
                    Button(action: onEdit) {
                        HStack(spacing: 4) {
                            Image(systemName: "pencil")
                            Text("编辑日程")
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 5)
                        .background(Color(hex: "#FF5A5F"))
                        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .help("编辑此日程的标题、时间、重复与提醒")
                }
            }
        }
        .padding(16)
        .frame(width: 330)
        .confirmationDialog("确定停掉本次课程吗？", isPresented: $isConfirmingCancel, titleVisibility: .visible) {
            Button("确认停课 (可随时恢复)", role: .destructive) {
                onQuickCancel()
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("停课仅影响本次课时，您可以在详情卡片中随时点击「恢复本次上课」改回。")
        }
        .confirmationDialog(
            item.isRecurring ? (item.sourceType == .calendarEvent ? "删除循环日程" : "删除循环课程") : (item.sourceType == .calendarEvent ? "删除日程" : "删除课程"),
            isPresented: $isConfirmingDelete,
            titleVisibility: .visible
        ) {
            if item.isRecurring {
                Button(item.sourceType == .calendarEvent ? "仅删除此日程" : "仅删除本次课时") {
                    if let onDeleteScope {
                        onDeleteScope(.thisOnly)
                    } else {
                        onQuickCancel()
                    }
                }
                Button(item.sourceType == .calendarEvent ? "删除此日程及随后的所有日程" : "删除本次及随后所有课时") {
                    if let onDeleteScope {
                        onDeleteScope(.thisAndFuture)
                    } else {
                        onDelete()
                    }
                }
                Button(item.sourceType == .calendarEvent ? "删除所有日程" : "彻底删除课程", role: .destructive) {
                    if let onDeleteScope {
                        onDeleteScope(.all)
                    } else {
                        onDelete()
                    }
                }
                Button("取消", role: .cancel) {}
            } else {
                Button("删除", role: .destructive) {
                    if let onDeleteScope {
                        onDeleteScope(.all)
                    } else {
                        onDelete()
                    }
                }
                Button("取消", role: .cancel) {}
            }
        } message: {
            if item.isRecurring {
                Text(item.sourceType == .calendarEvent ? "此日程为周期性重复安排，请选择要删除的范围。" : "此课程在学期中多次上课，请选择要删除的课时范围。")
            } else {
                Text("确定要删除「\(item.title)」吗？此操作无法撤销。")
            }
        }
    }

    private func detailRow(icon: String, title: String, content: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: icon)
                .frame(width: 14)
                .foregroundColor(.secondary)
                .font(.system(size: 11.5))
            Text(title)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.secondary)
                .frame(width: 32, alignment: .leading)
            Text(content)
                .font(.system(size: 12))
                .foregroundColor(.primary)
        }
    }
}
