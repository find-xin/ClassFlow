import SwiftUI
import ClassFlowCore

/// The primary timetable canvas blending seamlessly with wallpaper and featuring WakeUp's empty prompt.
public struct TimetableGridView: View {
    public let semester: Semester
    public let occurrences: [TimelineItem]
    public let dateStrings: [String]
    public let showWeekends: Bool
    public let todayDateString: String
    public let cardOpacity: Double
    public let onSelectOccurrence: (TimelineItem) -> Void
    public let onQuickImport: () -> Void

    private let periodHeight: CGFloat = 68
    private let periodSpacing: CGFloat = 4

    public init(
        semester: Semester,
        occurrences: [TimelineItem],
        dateStrings: [String],
        showWeekends: Bool,
        todayDateString: String,
        cardOpacity: Double = 0.88,
        onSelectOccurrence: @escaping (TimelineItem) -> Void,
        onQuickImport: @escaping () -> Void
    ) {
        self.semester = semester
        self.occurrences = occurrences
        self.dateStrings = dateStrings
        self.showWeekends = showWeekends
        self.todayDateString = todayDateString
        self.cardOpacity = cardOpacity
        self.onSelectOccurrence = onSelectOccurrence
        self.onQuickImport = onQuickImport
    }

    private var visibleDayCount: Int {
        showWeekends ? 7 : 5
    }

    public var body: some View {
        ScrollView([.vertical, .horizontal], showsIndicators: false) {
            ZStack {
                HStack(alignment: .top, spacing: 4) {
                    // 1. Left Section Axis
                    SectionAxisView(sections: semester.sections, periodHeight: periodHeight)

                    // 2. Day Columns
                    ForEach(1...visibleDayCount, id: \.self) { dayOfWeek in
                        let dayItems = occurrences.filter { $0.dayOfWeek == dayOfWeek }
                        let isToday = (dayOfWeek - 1 < dateStrings.count) && (dateStrings[dayOfWeek - 1] == todayDateString)

                        dayColumn(items: dayItems, isToday: isToday)
                            .frame(minWidth: 105, maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 4)

                // 3. Empty State matching WakeUp screenshot ("无课表，通过教务导课")
                if occurrences.isEmpty {
                    VStack(spacing: 12) {
                        Text("无课表，通过教务导课")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.primary.opacity(0.75))

                        Button(action: onQuickImport) {
                            Text("一键导入")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.primary)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 8)
                                .background(
                                    Capsule()
                                        .fill(.ultraThinMaterial)
                                        .overlay(Capsule().strokeBorder(Color.white.opacity(0.3), lineWidth: 0.5))
                                        .shadow(color: Color.black.opacity(0.06), radius: 4, y: 2)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
    }

    @ViewBuilder
    private func dayColumn(items: [TimelineItem], isToday: Bool) -> some View {
        let totalSections = semester.sections.count
        let totalHeight = CGFloat(totalSections) * periodHeight + CGFloat(max(0, totalSections - 1)) * periodSpacing

        ZStack(alignment: .topLeading) {
            // Very subtle slot tracks so wallpaper shines through cleanly
            VStack(spacing: periodSpacing) {
                ForEach(semester.sections) { _ in
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isToday ? Color.white.opacity(0.12) : Color.white.opacity(0.04))
                        .frame(height: periodHeight)
                }
            }

            // Overlay Course Cards
            ForEach(items) { item in
                let topOffset = CGFloat(item.startSection - 1) * (periodHeight + periodSpacing)
                let span = CGFloat(item.sectionSpan)
                let cardHeight = span * periodHeight + (span - 1) * periodSpacing

                CourseCardView(
                    item: item,
                    height: cardHeight,
                    cardOpacity: cardOpacity,
                    onTap: { onSelectOccurrence(item) }
                )
                .offset(y: topOffset)
            }
        }
        .frame(height: totalHeight)
    }
}
