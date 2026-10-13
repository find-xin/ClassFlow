import Testing
import ClassFlowCore

@Suite("ClassFlowVersionTests")
struct ClassFlowVersionTests {
    @Test("Verify version string")
    func testVersion() {
        #expect(ClassFlowVersion.version == "1.0.0")
    }
}
