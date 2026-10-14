import SwiftUI
import ClassFlowCore

/// Header bar rendering month indicator, weekday titles, and calendar dates with WakeUp-identical layout.
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

    private var currentMonthComponents: (String, String) {
        guard let first = dateStrings.first, let month = first.split(separator: "-").dropFirst().first else {
            return ("\(Calendar.current.component(.month, from: Date()))", "月")
        }
        let mInt = Int(month) ?? 1
        return ("\(mInt)", "月")
    }

    public var body: some View {
        HStack(spacing: 4) {
            // 1. Month column (stacked "10" and "月", exact WakeUp style)
            VStack(spacing: 0) {
                Text(currentMonthComponents.0)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.primary)
                Text(currentMonthComponents.1)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
            }
            .frame(width: 44)

            // 2. Weekday & Day Number columns
            let visibleCount = showWeekends ? 7 : 5
            ForEach(0..<visibleCount, id: \.self) { index in
                let dateStr = index < dateStrings.count ? dateStrings[index] : ""
                let isToday = (dateStr == todayDateString)
                let dayNumber = formatDayNumber(dateStr)

                VStack(spacing: 4) {
                    Text(dayNames[index])
                        .font(.system(size: 12.5, weight: isToday ? .bold : .medium))
                        .foregroundColor(isToday ? .primary : .secondary.opacity(0.85))

                    if isToday {
                        // Dark rounded badge with white bold text
                        Text(dayNumber)
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 24, height: 24)
                            .background(
                                RoundedRectangle(cornerRadius: 7, style: .continuous)
                                    .fill(Color(hex: "#1F1F1F"))
                            )
                    } else {
                        Text(dayNumber)
                            .font(.system(size: 11.5, weight: .regular))
                            .foregroundColor(.secondary.opacity(0.8))
                            .frame(width: 24, height: 24)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }

    private func formatDayNumber(_ isoDate: String) -> String {
        let parts = isoDate.split(separator: "-")
        guard parts.count == 3 else { return isoDate }
        if let dInt = Int(parts[2]) {
            return "\(dInt)"
        }
        return String(parts[2])
    }
}
