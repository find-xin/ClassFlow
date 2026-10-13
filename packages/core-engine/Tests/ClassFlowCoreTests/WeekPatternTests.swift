import Foundation
import Testing
@testable import ClassFlowCore

@Suite("WeekPatternTests")
struct WeekPatternTests {
    @Test("Verify .all pattern active every week")
    func testAllPattern() {
        let pattern = WeekPattern.all
        for w in 1...20 {
            #expect(pattern.isActive(inWeek: w, totalWeeks: 20) == true)
        }
        #expect(pattern.isActive(inWeek: 0, totalWeeks: 20) == false)
        #expect(pattern.isActive(inWeek: 21, totalWeeks: 20) == false)
    }

    @Test("Verify .odd pattern active on odd weeks only")
    func testOddPattern() {
        let pattern = WeekPattern.odd
        #expect(pattern.isActive(inWeek: 1) == true)
        #expect(pattern.isActive(inWeek: 2) == false)
        #expect(pattern.isActive(inWeek: 3) == true)
        #expect(pattern.isActive(inWeek: 4) == false)
    }

    @Test("Verify .even pattern active on even weeks only")
    func testEvenPattern() {
        let pattern = WeekPattern.even
        #expect(pattern.isActive(inWeek: 1) == false)
        #expect(pattern.isActive(inWeek: 2) == true)
        #expect(pattern.isActive(inWeek: 3) == false)
        #expect(pattern.isActive(inWeek: 4) == true)
    }

    @Test("Verify .custom pattern active on explicit weeks")
    func testCustomPattern() {
        let pattern = WeekPattern.custom([1, 2, 5, 8, 10])
        #expect(pattern.isActive(inWeek: 1) == true)
        #expect(pattern.isActive(inWeek: 2) == true)
        #expect(pattern.isActive(inWeek: 3) == false)
        #expect(pattern.isActive(inWeek: 4) == false)
        #expect(pattern.isActive(inWeek: 5) == true)
        #expect(pattern.isActive(inWeek: 8) == true)
        #expect(pattern.isActive(inWeek: 9) == false)
        #expect(pattern.isActive(inWeek: 10) == true)
    }
}
