import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

struct TimetableExtentTests {
    @Test("Late-night cards fit within the scrollable section bounds")
    @MainActor func lateNightExtent() throws {
        for end in ["23:40", "23:59", "24:00"] {
            let item = TimelineItem(id: "late", sourceType: .calendarEvent, title: "Sleep",
                date: "2026-10-27", dayOfWeek: 2, startSection: 14, endSection: 14,
                startTime: "23:30", endTime: end, weekNumber: 9, isCurrentWeek: true, status: .normal)
            let sections = TimetableGridView.expandedSections(
                base: [SectionTime(section: 14, startTime: "22:30", endTime: "23:30")], occurrences: [item])
            let last = try #require(sections.last)
            #expect(last.endTime == end)
            let start = try #require(TimetableGridView.parseTimeToMinutes(last.startTime))
            let finish = try #require(TimetableGridView.parseTimeToMinutes(last.endTime))
            #expect(finish > start)
            let bottom = TimetableGridView.yOffset(forMinutes: finish, sections: sections, periodHeight: 80, periodSpacing: 4)
            let top = TimetableGridView.yOffset(forMinutes: start, sections: sections, periodHeight: 80, periodSpacing: 4)
            #expect(bottom > top)
            #expect(bottom <= CGFloat(sections.count) * 84)
        }
    }
}
