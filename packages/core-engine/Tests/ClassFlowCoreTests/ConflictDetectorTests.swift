import Foundation
import Testing
@testable import ClassFlowCore

@Suite("ConflictDetectorTests")
struct ConflictDetectorTests {
    let detector = ConflictDetector()

    @Test("Detect exact period overlap on same date")
    func testExactPeriodOverlap() {
        let item1 = TimelineItem(
            id: "item1",
            title: "高等数学",
            date: "2026-10-19",
            dayOfWeek: 1,
            startSection: 1,
            endSection: 2,
            weekNumber: 7
        )
        let item2 = TimelineItem(
            id: "item2",
            title: "大学物理 (冲突)",
            date: "2026-10-19",
            dayOfWeek: 1,
            startSection: 1,
            endSection: 2,
            weekNumber: 7
        )

        let warnings = detector.detectConflicts(in: [item1, item2])
        #expect(warnings.count == 1)
        #expect(warnings[0].overlappingSections == [1, 2])
        #expect(warnings[0].itemAId == "item1")
        #expect(warnings[0].itemBId == "item2")
    }

    @Test("Detect partial period overlap on same date")
    func testPartialPeriodOverlap() {
        let item1 = TimelineItem(
            id: "item1",
            title: "高等数学 (1-3节)",
            date: "2026-10-19",
            dayOfWeek: 1,
            startSection: 1,
            endSection: 3,
            weekNumber: 7
        )
        let item2 = TimelineItem(
            id: "item2",
            title: "学术讲座 (3-4节)",
            date: "2026-10-19",
            dayOfWeek: 1,
            startSection: 3,
            endSection: 4,
            weekNumber: 7
        )

        let warnings = detector.detectConflicts(in: [item1, item2])
        #expect(warnings.count == 1)
        #expect(warnings[0].overlappingSections == [3])
    }

    @Test("Adjacent non-overlapping periods yield zero conflicts")
    func testAdjacentPeriodsNoConflict() {
        let item1 = TimelineItem(
            id: "item1",
            title: "高等数学 (1-2节)",
            date: "2026-10-19",
            dayOfWeek: 1,
            startSection: 1,
            endSection: 2,
            weekNumber: 7
        )
        let item2 = TimelineItem(
            id: "item2",
            title: "程序设计 (3-4节)",
            date: "2026-10-19",
            dayOfWeek: 1,
            startSection: 3,
            endSection: 4,
            weekNumber: 7
        )

        let warnings = detector.detectConflicts(in: [item1, item2])
        #expect(warnings.isEmpty)
    }

    @Test("Cancelled items are excluded from conflict warnings")
    func testCancelledItemsIgnored() {
        let item1 = TimelineItem(
            id: "item1",
            title: "高等数学 (已停课)",
            date: "2026-10-19",
            dayOfWeek: 1,
            startSection: 1,
            endSection: 2,
            weekNumber: 7,
            status: .cancelled
        )
        let item2 = TimelineItem(
            id: "item2",
            title: "临时借用教室活动",
            date: "2026-10-19",
            dayOfWeek: 1,
            startSection: 1,
            endSection: 2,
            weekNumber: 7,
            status: .normal
        )

        let warnings = detector.detectConflicts(in: [item1, item2])
        #expect(warnings.isEmpty)
    }

    @Test("Annotate items in-place with conflict flags")
    func testAnnotateConflictsInPlace() {
        var items = [
            TimelineItem(
                id: "item1",
                title: "课A",
                date: "2026-10-19",
                dayOfWeek: 1,
                startSection: 1,
                endSection: 2,
                weekNumber: 7
            ),
            TimelineItem(
                id: "item2",
                title: "课B",
                date: "2026-10-19",
                dayOfWeek: 1,
                startSection: 2,
                endSection: 3,
                weekNumber: 7
            ),
            TimelineItem(
                id: "item3",
                title: "课C (无冲突)",
                date: "2026-10-19",
                dayOfWeek: 1,
                startSection: 5,
                endSection: 6,
                weekNumber: 7
            )
        ]

        detector.annotateConflicts(in: &items)
        #expect(items[0].hasConflict == true)
        #expect(items[0].conflictingItemIds == ["item2"])
        #expect(items[1].hasConflict == true)
        #expect(items[1].conflictingItemIds == ["item1"])
        #expect(items[2].hasConflict == false)
        #expect(items[2].conflictingItemIds.isEmpty)
    }
}
