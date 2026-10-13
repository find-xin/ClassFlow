import SwiftUI
import ClassFlowCore

/// The primary 7-column timetable canvas with left section axis and card layout.
public struct TimetableGridView: View {
    public let semester: Semester
    public let occurrences: [TimelineItem]
    public let dateStrings: [String]
    public let showWeekends: Bool
    public let todayDateString: String
    public let cardOpacity: Double
    public let onSelectOccurrence: (TimelineItem) -> Void

    private let periodHeight: CGFloat = 64
    private let periodSpacing: CGFloat = 4

    public init(
        semester: Semester,
        occurrences: [TimelineItem],
        dateStrings: [String],
        showWeekends: Bool,
        todayDateString: String,
        cardOpacity: Double = 0.88,
        onSelectOccurrence: @escaping (TimelineItem) -> Void
    ) {
        self.semester = semester
        self.occurrences = occurrences
        self.dateStrings = dateStrings
        self.showWeekends = showWeekends
        self.todayDateString = todayDateString
        self.cardOpacity = cardOpacity
        self.onSelectOccurrence = onSelectOccurrence
    }

    private var visibleDayCount: Int {
        showWeekends ? 7 : 5
    }

    public var body: some View {
        VStack(spacing: 0) {
            // 1. Top Weekday Header
            DayHeaderView(
                dateStrings: dateStrings,
                showWeekends: showWeekends,
                todayDateString: todayDateString
            )
            Divider()

            // 2. Scrollable Timetable Canvas
            ScrollView([.vertical, .horizontal], showsIndicators: true) {
                HStack(alignment: .top, spacing: 4) {
                    // Left Section Axis
                    SectionAxisView(sections: semester.sections, periodHeight: periodHeight)

                    // Day Columns (1..5 or 1..7)
                    ForEach(1...visibleDayCount, id: \.self) { dayOfWeek in
                        let dayItems = occurrences.filter { $0.dayOfWeek == dayOfWeek }
                        let isToday = (dayOfWeek - 1 < dateStrings.count) && (dateStrings[dayOfWeek - 1] == todayDateString)

                        dayColumn(items: dayItems, isToday: isToday)
                            .frame(minWidth: 110, maxWidth: .infinity)
                    }
                }
                .padding(8)
            }
        }
        .background(Color(NSColor.windowBackgroundColor))
    }

    /// Renders a single vertical day column with section tracks and positioned cards.
    @ViewBuilder
    private func dayColumn(items: [TimelineItem], isToday: Bool) -> some View {
        let totalSections = semester.sections.count
        let totalHeight = CGFloat(totalSections) * periodHeight + CGFloat(max(0, totalSections - 1)) * periodSpacing

        ZStack(alignment: .topLeading) {
            // Background period slot tracks
            VStack(spacing: periodSpacing) {
                ForEach(semester.sections) { _ in
                    RoundedRectangle(cornerRadius: 6)
                        .fill(isToday ? Color.accentColor.opacity(0.04) : Color.primary.opacity(0.02))
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .strokeBorder(Color.primary.opacity(0.04), lineWidth: 0.5)
                        )
                        .frame(height: periodHeight)
                }
            }

            // Overlay Course Cards positioned by startSection
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
