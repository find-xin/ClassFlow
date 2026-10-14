import SwiftUI
import ClassFlowCore

/// Visual card component rendering scheduled course occurrences with WakeUp's pastel aesthetics.
public struct CourseCardView: View {
    public let item: TimelineItem
    public let height: CGFloat
    public let cardOpacity: Double
    public let onTap: () -> Void

    public init(
        item: TimelineItem,
        height: CGFloat,
        cardOpacity: Double = 0.88,
        onTap: @escaping () -> Void
    ) {
        self.item = item
        self.height = height
        self.cardOpacity = cardOpacity
        self.onTap = onTap
    }

    private var themeColor: Color {
        Color(hex: item.color, defaultColor: Color(hex: "#5B7FB5"))
    }

    public var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .topLeading) {
                // Background
                if item.isCurrentWeek {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(themeColor.opacity(cardOpacity))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.18), lineWidth: 0.5)
                        )
                        .shadow(color: Color.black.opacity(0.08), radius: 3, x: 0, y: 1.5)
                } else {
                    // Non-current week: Translucent with dotted/faint border
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(themeColor.opacity(0.28))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(themeColor.opacity(0.5), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                        )
                }

                // Content Layout
                VStack(alignment: .leading, spacing: 3) {
                    // Course Title
                    HStack(alignment: .top, spacing: 2) {
                        Text(item.title)
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundColor(item.isCurrentWeek ? .white : .primary.opacity(0.75))
                            .lineLimit(height < 60 ? 1 : 2)
                            .strikethrough(item.status == .cancelled, color: .white.opacity(0.8))

                        Spacer(minLength: 0)

                        if item.hasConflict {
                            Image(systemName: "exclamationmark.circle.fill")
                                .font(.system(size: 10))
                                .foregroundColor(.yellow)
                        }

                        if item.status == .cancelled {
                            statusPill("已停", color: .red)
                        } else if item.status == .rescheduled {
                            statusPill("调课", color: .orange)
                        }
                    }

                    // Location: "@东九楼 D102"
                    if let location = item.location, !location.isEmpty {
                        Text("@\(location)")
                            .font(.system(size: 10.5, weight: .medium))
                            .foregroundColor(item.isCurrentWeek ? .white.opacity(0.92) : .secondary)
                            .lineLimit(1)
                    }

                    // Teacher (if height allows)
                    if height >= 95, let teacher = item.teacher, !teacher.isEmpty {
                        Text(teacher)
                            .font(.system(size: 9.5))
                            .foregroundColor(item.isCurrentWeek ? .white.opacity(0.85) : .secondary.opacity(0.8))
                            .lineLimit(1)
                    }

                    Spacer(minLength: 0)
                }
                .padding(6)
            }
            .frame(height: max(32, height))
        }
        .buttonStyle(.plain)
    }

    private func statusPill(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 8.5, weight: .bold))
            .foregroundColor(.white)
            .padding(.horizontal, 3.5)
            .padding(.vertical, 1)
            .background(color.opacity(0.9))
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}
