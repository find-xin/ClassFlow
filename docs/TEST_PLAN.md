# ClassFlow Comprehensive Test Plan

**Version**: 1.0.0 | **Phase**: 1 | **Status**: Active Design

---

## 1. Test Strategy & Pyramid

ClassFlow enforces rigorous test coverage across all domain and protocol layers. Because student timetables directly govern daily attendance, calculation errors or lost schedule exceptions are considered catastrophic failures.

```
          / \
         /   \       E2E & UI Integration (Platform-specific)
        /  ^  \      macOS AppKit/SwiftUI + Android Compose
       /       \     
      /---------\    Protocol & Sync Simulation
     /     ^     \   Multi-Device Revisions, 3-Way Merge, Idempotency
    /-------------\  Domain & Algorithm Unit Tests
   /       ^       \ Timetable Engine, Exceptions, Conflicts, Schemas
  /-----------------\
```

---

## 2. Test Suites & Verification Matrix

### 2.1 Core Timetable Engine (Unit Tests)
- **Week Calculation**:
  - Semester starting on Monday vs non-Monday.
  - Leap year boundaries, month rollover, cross-year terms.
  - Querying date before semester start or after semester end.
- **Recurrence Patterns**:
  - `all`: active every week `1..totalWeeks`.
  - `odd`: active only on odd weeks (`1, 3, 5...`).
  - `even`: active only on even weeks (`2, 4, 6...`).
  - `custom`: exact match against arbitrary lists (e.g. `[1, 2, 3, 5, 8]`).
- **Schedule Exception Resolution**:
  - Single cancellation (`cancel`) completely removes the occurrence for that day.
  - Rescheduling (`reschedule`) moves the occurrence to target date and section.
  - Room change (`room_change`) preserves time while swapping location.
  - Multiple exceptions on the same course on different dates do not collide.
- **Editing Scope Mechanics**:
  - `thisOnly`: generates a single `ScheduleException`.
  - `thisAndFuture`: correctly truncates prior rule and creates a new sibling schedule.
  - `all`: updates root schedule without corrupting unrelated semesters.
- **Conflict Detection**:
  - Partial section overlap (e.g. 1–2 vs 2–3) triggers conflict flag.
  - Exact section match triggers conflict flag.
  - Adjacent sections (e.g. 1–2 and 3–4) do NOT trigger conflict flag.
  - Different rooms or same course warnings are structured with item IDs.

### 2.2 Schema & Data Serialization Tests
- **JSON Schema Validation**:
  - Validates `packages/fixtures/sample_semester.json` against `semester.schema.json`.
  - Validates `packages/fixtures/sample_course.json` against `course.schema.json`.
  - Validates `packages/fixtures/sample_backup.classflow.json` against `classflow_backup.schema.json`.
  - Negative tests: Missing mandatory fields (`id`, `name`, `startDate`) must fail validation.
- **Deterministic Serialization**:
  - Canonical JSON ordering (keys sorted alphabetically) ensuring reproducible SHA-256 hashes.

### 2.3 BYOC Sync Protocol Simulation Tests
- **Device-Isolated Writes**:
  - Device A and Device B write concurrently to isolated paths; verify zero mutual overwrite.
- **3-Way Merge Engine**:
  - Disjoint course edits auto-merge cleanly.
  - Concurrent field edits (different attributes of same course) merge cleanly.
  - Concurrent edit on same attribute: latest HLC timestamp preserved; soft conflict recorded.
  - Delete vs. Modify: modify preserved with restorative notice.
- **Network Resilience**:
  - Simulated network failure during upload resumes from last known sequence without creating duplicates.

### 2.4 System Calendar & EventKit Tests
- **Idempotent Sync**:
  - Syncing a semester twice yields exact same external calendar events (no duplicates).
  - Deleting a course removes all corresponding `CalendarMapping` instances from the target calendar.

---

## 3. Test Execution Commands & Automation

| Target | Command | Prerequisites |
| :--- | :--- | :--- |
| **Foundation Schemas & Projections** | `python3 scripts/test_foundation.py` | Python 3.9+ (Installed) |
| **Swift Domain Engine** | `swift test --module-cache-path .cache/clang` | Swift 6.1+ (Installed) |
| **Android Unit Tests** | `./gradlew testDebugUnitTest` | JDK 17+ (Phase 4) |
| **macOS Native UI Tests** | `xcodebuild test -scheme ClassFlow` | Xcode.app (Phase 3) |
