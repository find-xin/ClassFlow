import Testing
import Foundation
@testable import ClassFlowCore

@Suite("CalendarSyncPlannerTests")
struct CalendarSyncPlannerTests {

    let semester: Semester = {
        Semester(
            name: "2026年秋季学期",
            startDate: "2026-09-07",
            endDate: "2027-01-10",
            totalWeeks: 16,
            isCurrent: true,
            sections: [
                SectionTime(section: 1, startTime: "08:00", endTime: "08:45"),
                SectionTime(section: 2, startTime: "08:50", endTime: "09:35"),
                SectionTime(section: 3, startTime: "10:00", endTime: "10:45"),
                SectionTime(section: 4, startTime: "10:50", endTime: "11:35")
            ]
        )
    }()

    let courseId = UUID()
    let scheduleId = UUID()

    func makeItem(
        date: String = "2026-09-08",
        startSec: Int = 1,
        endSec: Int = 2,
        classroom: String = "东上101",
        isCancelled: Bool = false
    ) -> TimelineItem {
        TimelineItem(
            id: "course_occ_\(scheduleId)_\(date)",
            sourceType: .course,
            courseId: courseId,
            scheduleId: scheduleId,
            title: "高等数学",
            location: classroom,
            date: date,
            dayOfWeek: 2,
            startSection: startSec,
            endSection: endSec,
            startTime: "08:00",
            endTime: "09:35",
            weekNumber: 1,
            isCurrentWeek: true,
            status: isCancelled ? .cancelled : .normal,
            color: "#4A90E2",
            teacher: "张教授"
        )
    }

    @Test("Initial sync with empty mappings queues all occurrences for creation")
    func testInitialSyncCreatesAll() {
        let planner = CalendarSyncPlanner()
        let items = [
            makeItem(date: "2026-09-08", startSec: 1, endSec: 2),
            makeItem(date: "2026-09-10", startSec: 3, endSec: 4)
        ]

        let plan = planner.generatePlan(
            occurrences: items,
            semester: semester,
            existingMappings: []
        )

        #expect(plan.toCreate.count == 2)
        #expect(plan.toUpdate.count == 0)
        #expect(plan.toDelete.count == 0)
        #expect(plan.unchangedCount == 0)
        #expect(plan.toCreate[0].title == "高等数学")
        #expect(plan.toCreate[0].location == "东上101")
        #expect(plan.toCreate[0].reminderOffsetMinutes == 15)
    }

    @Test("Subsequent sync with identical contents reports unchanged")
    func testIdenticalContentIsUnchanged() {
        let planner = CalendarSyncPlanner()
        let item = makeItem(date: "2026-09-08")

        // First dry-run to get content hash
        let initialPlan = planner.generatePlan(
            occurrences: [item],
            semester: semester,
            existingMappings: []
        )
        let candidate = initialPlan.toCreate[0]

        let existingMapping = CalendarMapping(
            semesterId: semester.id,
            courseId: courseId,
            scheduleId: scheduleId,
            occurrenceDate: "2026-09-08",
            startSection: 1,
            endSection: 2,
            externalEventId: "ek_event_12345",
            contentHash: candidate.contentHash
        )

        let secondPlan = planner.generatePlan(
            occurrences: [item],
            semester: semester,
            existingMappings: [existingMapping]
        )

        #expect(secondPlan.toCreate.isEmpty)
        #expect(secondPlan.toUpdate.isEmpty)
        #expect(secondPlan.toDelete.isEmpty)
        #expect(secondPlan.unchangedCount == 1)
    }

    @Test("Classroom modification triggers targeted update diff")
    func testModifiedClassroomTriggersUpdate() {
        let planner = CalendarSyncPlanner()
        let oldItem = makeItem(classroom: "东上101")
        let firstPlan = planner.generatePlan(occurrences: [oldItem], semester: semester, existingMappings: [])
        let hash = firstPlan.toCreate[0].contentHash

        let existingMapping = CalendarMapping(
            semesterId: semester.id,
            courseId: courseId,
            scheduleId: scheduleId,
            occurrenceDate: "2026-09-08",
            startSection: 1,
            endSection: 2,
            externalEventId: "ek_event_12345",
            contentHash: hash
        )

        // Modified room:
        let newItem = makeItem(classroom: "西教204")
        let updatedPlan = planner.generatePlan(
            occurrences: [newItem],
            semester: semester,
            existingMappings: [existingMapping]
        )

        #expect(updatedPlan.toCreate.isEmpty)
        #expect(updatedPlan.toUpdate.count == 1)
        #expect(updatedPlan.toUpdate[0].existingMapping.externalEventId == "ek_event_12345")
        #expect(updatedPlan.toUpdate[0].candidate.location == "西教204")
        #expect(updatedPlan.toDelete.isEmpty)
    }

    @Test("Cancelled or deleted occurrence marks mapping for deletion")
    func testCancelledOccurrenceQueuesDeletion() {
        let planner = CalendarSyncPlanner()
        let existingMapping = CalendarMapping(
            semesterId: semester.id,
            courseId: courseId,
            scheduleId: scheduleId,
            occurrenceDate: "2026-09-08",
            startSection: 1,
            endSection: 2,
            externalEventId: "ek_event_12345",
            contentHash: "dummy_hash"
        )

        // Cancelled occurrence:
        let cancelledItem = makeItem(isCancelled: true)
        let plan = planner.generatePlan(
            occurrences: [cancelledItem],
            semester: semester,
            existingMappings: [existingMapping]
        )

        #expect(plan.toCreate.isEmpty)
        #expect(plan.toUpdate.isEmpty)
        #expect(plan.toDelete.count == 1)
        #expect(plan.toDelete[0].externalEventId == "ek_event_12345")
    }
}
