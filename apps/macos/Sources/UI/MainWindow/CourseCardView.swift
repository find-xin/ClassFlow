import SwiftUI
import ClassFlowCore

/// Visual card component rendering scheduled course occurrences with WakeUp's pastel aesthetics
/// and deep customization options (Images 3 & 4).
public struct CourseCardView: View {
    public let item: TimelineItem
    public let height: CGFloat
    public let settings: AppSettings
    public let onTap: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var isHovered: Bool = false

    private var isDarkMode: Bool {
        settings.themeMode == "dark" || (settings.themeMode == "system" && colorScheme == .dark)
    }

    public init(
        item: TimelineItem,
        height: CGFloat,
        settings: AppSettings = AppSettings(),
        onTap: @escaping () -> Void
    ) {
        self.item = item
        self.height = height
        self.settings = settings
        self.onTap = onTap
    }

    private var themeColor: Color {
        Color(hex: item.color, defaultColor: Color(hex: "#5B7FB5"))
    }

    private var effectiveTextColor: Color {
        if settings.cardTextBlendCardColor {
            return (themeColor.isLight && !isDarkMode) ? themeColor.deepShade : themeColor
        }
        switch settings.cardTextColorMode {
        case "light":
            return .white
        case "dark":
            return Color.black.opacity(0.85)
        default:
            // Auto contrast mode: dynamically check pastel luminance and dark mode
            if isDarkMode {
                return item.isCurrentWeek ? Color.white.opacity(0.96) : Color.white.opacity(0.78)
            } else {
                if themeColor.isLight {
                    return item.isCurrentWeek ? themeColor.deepShade : Color.black.opacity(0.65)
                } else {
                    return item.isCurrentWeek ? Color.white.opacity(0.96) : Color.white.opacity(0.80)
                }
            }
        }
    }

    private var borderStrokeColor: Color {
        switch settings.cardBorderColorMode {
        case "cardColor":
            return isDarkMode ? themeColor.opacity(0.85) : themeColor
        case "subtle":
            return isDarkMode ? Color.white.opacity(0.20) : Color.white.opacity(0.35)
        default:
            return settings.cardBorderUseCardColor
                ? (isDarkMode ? themeColor.opacity(0.7) : themeColor)
                : (isDarkMode ? Color.white.opacity(0.18) : Color.white.opacity(0.25))
        }
    }

    private var effectiveSecondaryTextColor: Color {
        effectiveTextColor.opacity(0.82)
    }

    public var body: some View {
        Button(action: onTap) {
            ZStack(alignment: settings.cardTextVerticalCenter ? .center : .topLeading) {
                // Background & Border
                let radius = CGFloat(settings.cardCornerRadius)
                let baseOpacity = item.isCurrentWeek ? settings.cardOpacity : (settings.cardOpacity * settings.nonCurrentWeekOpacityRatio)

                ZStack {
                    // Card background tint
                    RoundedRectangle(cornerRadius: radius, style: .continuous)
                        .fill(isDarkMode ? themeColor.opacity(min(baseOpacity, 0.82)) : themeColor.opacity(baseOpacity))

                    // Glass top reflection gradient for depth
                    RoundedRectangle(cornerRadius: radius, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: isDarkMode
                                    ? [Color.white.opacity(0.08), Color.white.opacity(0.0)]
                                    : [Color.white.opacity(0.18), Color.white.opacity(0.0)],
                                startPoint: .top,
                                endPoint: .center
                            )
                        )

                    // Subtle border stroke
                    RoundedRectangle(cornerRadius: radius, style: .continuous)
                        .strokeBorder(
                            borderStrokeColor,
                            style: settings.cardUseDottedBorder ? StrokeStyle(lineWidth: 1.2, dash: [3, 3]) : StrokeStyle(lineWidth: 0.5)
                        )
                }
                .shadow(
                    color: Color.black.opacity(isHovered ? 0.16 : (item.isCurrentWeek ? 0.08 : 0.02)),
                    radius: isHovered ? 6 : 3,
                    x: 0,
                    y: isHovered ? 3 : 1.5
                )

                // Content Layout
                VStack(
                    alignment: settings.cardTextHorizontalCenter ? .center : .leading,
                    spacing: 3
                ) {
                    // Course Title & Status Pill
                    HStack(alignment: .top, spacing: 3) {
                        Text(item.title)
                            .font(.system(size: CGFloat(settings.cardFontSize), weight: .bold))
                            .foregroundColor(effectiveTextColor)
                            .multilineTextAlignment(settings.cardTextHorizontalCenter ? .center : .leading)
                            // Taller cards let SwiftUI wrap the title into the available height.
                            // Compact cards retain one line so their content stays inside the time slot.
                            .lineLimit(height < 55 ? 1 : nil)
                            .strikethrough(item.status == .cancelled, color: effectiveTextColor.opacity(0.8))

                        if !settings.cardTextHorizontalCenter {
                            Spacer(minLength: 0)
                        }

                        if item.hasConflict {
                            Image(systemName: "exclamationmark.circle.fill")
                                .font(.system(size: 9))
                                .foregroundColor(.yellow)
                        }

                        if item.status == .cancelled {
                            statusPill("已停", color: .red)
                        } else if item.status == .rescheduled {
                            statusPill("调课", color: .orange)
                        }
                    }

                    // Location
                    if settings.showLocationInCard, let location = item.location, !location.isEmpty {
                        let prefix = settings.showAtPrefixForRoom ? "@" : ""
                        Text("\(prefix)\(location)")
                            .font(.system(size: max(9, CGFloat(settings.cardFontSize) - 2.5), weight: .medium))
                            .foregroundColor(effectiveSecondaryTextColor)
                            .lineLimit(1)
                    }

                    // Teacher
                    if settings.showTeacherInCard, height >= 75, let teacher = item.teacher, !teacher.isEmpty {
                        Text(teacher)
                            .font(.system(size: max(8.5, CGFloat(settings.cardFontSize) - 3.5), weight: .regular))
                            .foregroundColor(effectiveSecondaryTextColor.opacity(0.9))
                            .lineLimit(1)
                    }

                    // Time in card
                    if settings.showTimeInCard, height >= 85, let sTime = item.startTime, let eTime = item.endTime {
                        Text("\(sTime)-\(eTime)")
                            .font(.system(size: 8.5, weight: .medium, design: .monospaced))
                            .foregroundColor(effectiveSecondaryTextColor.opacity(0.85))
                            .lineLimit(1)
                    }

                    // Notes
                    if settings.showNotesInCard, height >= 95, let notes = item.notes, !notes.isEmpty {
                        Text(notes)
                            .font(.system(size: 8.5))
                            .foregroundColor(effectiveSecondaryTextColor.opacity(0.75))
                            .lineLimit(1)
                    }

                    if !settings.cardTextVerticalCenter {
                        Spacer(minLength: 0)
                    }
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 6)
            }
            .frame(height: max(28, height))
            .scaleEffect(isHovered ? 1.015 : 1.0)
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.spring(response: 0.25, dampingFraction: 0.75)) {
                isHovered = hovering
            }
        }
    }

    private func statusPill(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 8.5, weight: .bold))
            .foregroundColor(.white)
            .padding(.horizontal, 4)
            .padding(.vertical, 1.5)
            .background(color)
            .clipShape(Capsule())
    }
}
