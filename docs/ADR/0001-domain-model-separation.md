# ADR 0001: Separation of Course and CalendarEvent Models

**Status**: Accepted  
**Date**: 2026-10-14  
**Context**: ClassFlow begins as a high-performance university timetable application but aims to expand in subsequent phases into a comprehensive personal calendar and agenda tool (similar to Google Calendar).

---

## Decision

We reject the naive architectural choice of forcing `Course` to inherit from `CalendarEvent` or modeling academic courses simply as recurring calendar events with metadata blobs.

Instead:
1. `Course`, `CourseSchedule`, and `ScheduleException` are modeled as distinct academic domain entities with native support for semesters, section periods, and teaching week recurrence rules.
2. `Calendar` and `CalendarEvent` are modeled as general schedule entities supporting iCalendar RFC 5545 recurrence rules.
3. At the query boundary, a unified `TimelineRepository` projects both courses and calendar events into `TimelineItem` (Occurrence) read models.
4. When editing, the presentation layer dispatches to specific domain coordinators (`CourseService` vs `CalendarEventService`) based on the item's `sourceType`.

---

## Consequences

### Positive
- Academic concepts (such as single-day makeup classes, odd/even week patterns, and section time mapping) remain strictly typed and validated.
- The UI can render a unified timetable or agenda view without knowing whether an item originated from a university course or a calendar event.
- Prevents database bloat and eliminates complex translation layers between academic schedules and generic RFC 5545 RRULE strings.

### Negative / Trade-offs
- Requires maintaining two separate persistence tables and service coordinators.
- Projection to `TimelineItem` introduces an in-memory aggregation step during timetable queries, which must be optimized with indexed date bounds.
