# Changelog

All notable changes to the ClassFlow project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-10-21

### Fixed
- **Timetable Grid Interval Conflict Resolution (macOS & Android)**:
  - Resolved layout issue where non-current week courses appeared side-by-side with active current-week calendar events and schedules.
  - Implemented interval-based minute range and section overlap detection (`itemsOverlap`): non-current week courses are only displayed when the time slot is completely free of any active arrangements.
  - Added dynamic section inference for `CalendarEvent` occurrences without explicit `startSection` based on event start/end times and semester section timetable.

### Added
- **Core Calculation Engine (`packages/core-engine`)**:
  - Pure-Swift timetable calculation engine with teaching week calculator, occurrence projection, conflict detector, and mutation coordinator.
  - Multi-pattern recurrence (`all`, `odd`, `even`, `custom` weeks) and surgical `EditingScope` support (`thisOnly`, `thisAndFuture`, `all`).
- **macOS Desktop Client (`apps/macos`)**:
  - Native SwiftUI + AppKit 7-column timetable canvas with section axis, current week highlights, and custom card opacity.
  - Top toolbar navigation with semester switcher, week selector, and quick jump buttons.
  - Non-modal course inspector card and editing popover sheets.
  - Menu bar companion (`MenuBarExtraController`) with countdown to upcoming classes.
  - Native macOS settings window with General, Timetable, Semesters, Calendar, Notifications, Sync, and About tabs.
- **Import & Export Engine (`packages/core-engine`)**:
  - PKZip archive encoder/decoder (`ZipArchive`) with CRC-32 and zlib deflate.
  - `.classflow` native backup container with SHA-256 integrity checksums and preview diff modal.
  - RFC 5545 iCalendar (`.ics`) two-way export and import parser.
- **BYOC Cloud Sync (`packages/core-engine` & `apps/macos`)**:
  - AWS SigV4 authorization engine (`S3SignatureV4`) supporting Qiniu Kodo and generic S3 buckets.
  - Decentralized sync engine (`SyncEngine`) with device-isolated append-only logs (`devices/{deviceId}/revisions/`) and 3-way merge rules.
  - Hardware-backed Keychain credential storage (`KeychainService`).
- **System Calendar Integration (`EventKit`)**:
  - Native macOS `EventKitCalendarService` with dedicated "ClassFlow 课程表" container creation and pre-class alarm scheduling.
  - `CalendarMapping` tracking table preventing duplicate events.
- **Notifications & Desktop Widgets (`WidgetKit`)**:
  - Native `NotificationService` for pre-class warnings and daily morning briefings.
  - WidgetKit extensions: Small (Next Class countdown), Medium (Today's classes), Large (Week Overview timetable).
- **Android Architecture & HyperOS Adapter (`apps/android`)**:
  - Native Jetpack Compose 7-column timetable canvas, Agenda stream, Profile screen, and Bottom Sheet editor.
  - Room database with DAOs and type converters.
  - Android KeyStore / EncryptedSharedPreferences security manager.
  - Android `CalendarContract` synchronization engine.
  - Jetpack Glance widgets (2x2, 4x2, 4x4).
  - Quarantined `hyperos-adapter` module with strict verification checks and standard notification fallback.
- **Foundation & Open Source Readiness**:
  - JSON Schema definitions, fixtures, and Python verification test suite.
  - GitHub Actions CI workflow, issue templates, Apache-2.0 license, and contributing guidelines.
