import SwiftUI
import ClassFlowCore

/// Header bar rendering month indicator, weekday titles, and calendar dates with WakeUp-inspired today highlight.
public struct DayHeaderView: View {
    public let dateStrings: [String] // 7 dates
    public let showWeekends: Bool
    public let todayDateString: String

    public init(dateStrings: [String], showWeekends: Bool, todayDateString: String) {
        self.dateStrings = dateStrings
        self.showWeekends = showWeekends
        self.todayDateString = todayDateString
    }

    private let dayNames = ["一", "二", "三", "四", "五", "六", "日"]

    private var currentMonthString: String {
        guard let first = dateStrings.first, let month = first.split(separator: "-").dropFirst().first else {
            return "\(Calendar.current.component(.month, from: Date()))月"
        }
        let mInt = Int(month) ?? 1
        return "\(mInt)月"
    }

    public var body: some View {
        HStack(spacing: 4) {
            // 1. Month column (aligned with section axis)
            VStack(spacing: 1) {
                Text(currentMonthString)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.primary)
            }
            .frame(width: 48)

            // 2. Weekday & Day Number columns
            let visibleCount = showWeekends ? 7 : 5
            ForEach(0..<visibleCount, id: \.self) { index in
                let dateStr = index < dateStrings.count ? dateStrings[index] : ""
                let isToday = (dateStr == todayDateString)
                let dayNumber = formatDayNumber(dateStr)

                VStack(spacing: 3) {
                    Text(dayNames[index])
                        .font(.system(size: 12, weight: isToday ? .bold : .medium))
                        .foregroundColor(isToday ? .accentColor : .secondary)

                    if isToday {
                        Text(dayNumber)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 22, height: 22)
                            .background(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(Color.primary)
                            )
                    } else {
                        Text(dayNumber)
                            .font(.system(size: 11, weight: .regular))
                            .foregroundColor(.secondary)
                            .frame(width: 22, height: 22)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(.bar)
    }

    private func formatDayNumber(_ isoDate: String) -> String {
        // "2026-10-19" -> "19"
        let parts = isoDate.split(separator: "-")
        guard parts.count == 3 else { return isoDate }
        if let dInt = Int(parts[2]) {
            return "\(dInt)"
        }
        return String(parts[2])
    }
}
