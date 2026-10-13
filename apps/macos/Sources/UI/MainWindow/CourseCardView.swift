import SwiftUI
import ClassFlowCore

/// Visual card component rendering a scheduled course occurrence.
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
        Color(hex: item.color) ?? Color.accentColor
    }

    public var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .topLeading) {
                // Background Frosted Material
                FrostedCardBackground(
                    color: item.status == .cancelled ? Color.gray : themeColor,
                    opacity: item.status == .cancelled ? 0.35 : cardOpacity,
                    cornerRadius: 8
                )

                // Card Content
                VStack(alignment: .leading, spacing: 3) {
                    // Header row: Title + Badges
                    HStack(alignment: .top, spacing: 4) {
                        Text(item.title)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white)
                            .lineLimit(2)
                            .strikethrough(item.status == .cancelled, color: .white.opacity(0.8))

                        Spacer(minLength: 0)

                        if item.hasConflict {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 10))
                                .foregroundColor(.yellow)
                        }

                        if item.status == .cancelled {
                            statusPill("已停", color: .red)
                        } else if item.status == .rescheduled {
                            statusPill("调课", color: .orange)
                        } else if item.status == .makeup {
                            statusPill("补课", color: .green)
                        }
                    }

                    // Location / Room
                    if let location = item.location, !location.isEmpty {
                        HStack(spacing: 2) {
                            Image(systemName: "mappin.and.ellipse")
                                .font(.system(size: 8))
                            Text(location)
                                .font(.system(size: 10))
                                .lineLimit(1)
                        }
                        .foregroundColor(.white.opacity(0.92))
                    }

                    // Teacher (if height allows, e.g. multi-period span)
                    if height >= 100, let teacher = item.teacher, !teacher.isEmpty {
                        HStack(spacing: 2) {
                            Image(systemName: "person.fill")
                                .font(.system(size: 8))
                            Text(teacher)
                                .font(.system(size: 10))
                                .lineLimit(1)
                        }
                        .foregroundColor(.white.opacity(0.85))
                    }

                    Spacer(minLength: 0)
                }
                .padding(6)
            }
            .frame(height: max(32, height))
            .shadow(color: Color.black.opacity(0.08), radius: 3, x: 0, y: 1)
        }
        .buttonStyle(.plain)
    }

    private func statusPill(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 8, weight: .bold))
            .foregroundColor(.white)
            .padding(.horizontal, 3)
            .padding(.vertical, 1)
            .background(color.opacity(0.85))
            .clipShape(RoundedRectangle(cornerRadius: 3))
    }
}

// MARK: - Color Hex Initializer
extension Color {
    public init?(hex: String) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if hexSanitized.hasPrefix("#") {
            hexSanitized.removeFirst()
        }

        guard hexSanitized.count == 6 || hexSanitized.count == 8 else { return nil }

        var rgb: UInt64 = 0
        guard Scanner(string: hexSanitized).scanHexInt64(&rgb) else { return nil }

        if hexSanitized.count == 6 {
            let r = Double((rgb & 0xFF0000) >> 16) / 255.0
            let g = Double((rgb & 0x00FF00) >> 8) / 255.0
            let b = Double(rgb & 0x0000FF) / 255.0
            self.init(red: r, green: g, blue: b)
        } else {
            let r = Double((rgb & 0xFF000000) >> 24) / 255.0
            let g = Double((rgb & 0x00FF0000) >> 16) / 255.0
            let b = Double((rgb & 0x0000FF00) >> 8) / 255.0
            let a = Double(rgb & 0x000000FF) / 255.0
            self.init(red: r, green: g, blue: b, opacity: a)
        }
    }
}
