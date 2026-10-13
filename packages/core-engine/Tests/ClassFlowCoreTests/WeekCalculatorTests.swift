import Foundation
import Testing
@testable import ClassFlowCore

@Suite("WeekCalculatorTests")
struct WeekCalculatorTests {
    let calculator = WeekCalculator(timeZoneId: "Asia/Shanghai")
    let semesterStart = "2026-09-07" // Monday

    @Test("Calculate teaching week index from start date")
    func testWeekIndexCalculation() {
        #expect(calculator.weekIndex(for: "2026-09-07", semesterStartDate: semesterStart) == 1)
        #expect(calculator.weekIndex(for: "2026-09-13", semesterStartDate: semesterStart) == 1) // Sunday of week 1
        #expect(calculator.weekIndex(for: "2026-09-14", semesterStartDate: semesterStart) == 2) // Monday of week 2
        #expect(calculator.weekIndex(for: "2026-10-19", semesterStartDate: semesterStart) == 7) // Week 7
        #expect(calculator.weekIndex(for: "2026-10-25", semesterStartDate: semesterStart) == 7) // Sunday of week 7
        #expect(calculator.weekIndex(for: "2026-10-26", semesterStartDate: semesterStart) == 8) // Week 8
    }

    @Test("Verify day of week mapping 1 (Mon) through 7 (Sun)")
    func testDayOfWeekMapping() {
        let mon = calculator.parseDate("2026-09-07")!
        let wed = calculator.parseDate("2026-09-09")!
        let sun = calculator.parseDate("2026-09-13")!

        #expect(calculator.dayOfWeek(for: mon) == 1)
        #expect(calculator.dayOfWeek(for: wed) == 3)
        #expect(calculator.dayOfWeek(for: sun) == 7)
    }

    @Test("Date string generation for week and day of week")
    func testDateForWeekAndDay() {
        #expect(calculator.dateString(forWeek: 1, dayOfWeek: 1, semesterStartDate: semesterStart) == "2026-09-07")
        #expect(calculator.dateString(forWeek: 1, dayOfWeek: 7, semesterStartDate: semesterStart) == "2026-09-13")
        #expect(calculator.dateString(forWeek: 7, dayOfWeek: 1, semesterStartDate: semesterStart) == "2026-10-19")
        #expect(calculator.dateString(forWeek: 7, dayOfWeek: 6, semesterStartDate: semesterStart) == "2026-10-24")
    }

    @Test("Generate all 7 date strings in a week")
    func testDateStringsInWeek() {
        let weekDates = calculator.dateStringsInWeek(weekNumber: 1, semesterStartDate: semesterStart)
        #expect(weekDates.count == 7)
        #expect(weekDates[0] == "2026-09-07")
        #expect(weekDates[6] == "2026-09-13")
    }
}
