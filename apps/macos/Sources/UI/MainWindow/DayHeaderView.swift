import SwiftUI
import ClassFlowCore

/// Header bar rendering weekday titles and calendar dates with today highlight.
public struct DayHeaderView: View {
    public let dateStrings: [String] // 7 dates
    public let showWeekends: Bool
    public let todayDateString: String

    public init(dateStrings: [String], showWeekends: Bool, todayDateString: String) {
        self.dateStrings = dateStrings
        self.showWeekends = showWeekends
        self.todayDateString = todayDateString
    }

    private let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]

    public var body: some View {
        HStack(spacing: 4) {
            // Space reserved for left section axis
            Spacer()
                .frame(width: 48)

            let visibleCount = showWeekends ? 7 : 5
            ForEach(0..<visibleCount, id: \.self) { index in
                let dateStr = index < dateStrings.count ? dateStrings[index] : ""
                let isToday = (dateStr == todayDateString)

                VStack(spacing: 2) {
                    Text(dayNames[index])
                        .font(.system(size: 13, weight: isToday ? .bold : .medium))
                        .foregroundColor(isToday ? .accentColor : .primary)

                    Text(formatShortDate(dateStr))
                        .font(.system(size: 10, weight: .regular, design: .monospaced))
                        .foregroundColor(isToday ? .accentColor : .secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(isToday ? Color.accentColor.opacity(0.12) : Color.clear)
                )
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(.bar)
    }

    private func formatShortDate(_ isoDate: String) -> String {
        // "2026-10-19" -> "10/19"
        let parts = isoDate.split(separator: "-")
        guard parts.count == 3 else { return isoDate }
        return "\(parts[1])/\(parts[2])"
    }
}
