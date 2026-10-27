import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow
struct CalendarEditorPreservationTests {
 @Test func saveRetainsHiddenValuesAndClockRepresentation() {
  var original = CalendarEvent(title:"test", startTime:"2026-11-01T19:00:00",endTime:"2026-11-01T20:30:00",timeMode:"time",location:"",alertMinutesBefore:10,frequency:"daily",customWeeks:[],customDaysOfWeek:[],endRepeatType:"untilDate",endRepeatUntil:"2026-11-09")
  var draft = original
  draft.startTime="2026-11-01 19:00"; draft.endTime="2026-11-01 20:30"
  draft.startSection=9; draft.endSection=11; draft.endRepeatCount=10
  draft.location=nil; draft.customWeeks=nil; draft.customDaysOfWeek=nil
  #expect(CalendarEditorPreservation.apply(original:original,draft:draft)==original)
  draft.alertMinutesBefore=0
  original.alertMinutesBefore=0
  #expect(CalendarEditorPreservation.apply(original:original,draft:draft)==original)
 }
}
