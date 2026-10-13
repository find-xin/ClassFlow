# ClassFlow

> **Native First, Local First, Bring Your Own Cloud.**  
> A high-performance, open-source native cross-platform timetable and calendar application built for university students and professionals.

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![macOS 15.0+](https://img.shields.io/badge/macOS-15.0%2B%20%28Apple%20Silicon%29-black?logo=apple)](#target-platforms)
[![Android API 26+](https://img.shields.io/badge/Android-API%2026%2B%20%2F%20HyperOS-green?logo=android)](#target-platforms)

---

## 1. Vision & Core Principles

ClassFlow is engineered from the ground up to replace clunky, ad-ridden timetable tools with a clean, privacy-respecting, native software experience.

### Phase 1 Vision
Deliver an intuitive, full-featured academic timetable matching and exceeding best-in-class timetable software (such as WakeUp 课程表), with support for semesters, custom section times, teaching weeks, odd/even schedules, single-class reschedules/cancellations, desktop widgets, and system calendar synchronization.

### Long-Term Vision
Gracefully scale into an extensible calendar and agenda management suite (similar to Google Calendar) without sacrificing the agility or academic focus of its timetable roots.

### Architectural Principles
1. **Native First**: Built with Swift, SwiftUI, and AppKit on macOS; Kotlin, Jetpack Compose, and Room on Android. Zero sluggish WebViews or cross-platform framework overhead.
2. **Local First & Offline First**: Instant UI response via local database (SQLite/Room). Fully functional with zero network connectivity.
3. **Bring Your Own Cloud (BYOC)**: No vendor lock-in. No mandatory proprietary cloud servers. Sync across devices using your own object storage (Qiniu Cloud Kodo, S3-compatible storage, WebDAV).
4. **Privacy First**: Sensitive credentials (AK/SK, passwords) are guarded in platform-native secure enclaves (macOS Keychain, Android KeyStore).
5. **Decoupled Domain Architecture**: Courses (`Course`) and schedules (`CourseSchedule`) are modeled cleanly from generic calendar events (`CalendarEvent`), united at runtime via a shared `TimelineItem` projection.
6. **Transparent Open Source**: Community-driven, modular, and built on auditable protocols.

---

## 2. Target Platforms

| Platform | Minimum Target | Core Technologies | Primary UX Paradigm |
| :--- | :--- | :--- | :--- |
| **macOS** | macOS 15.8 (Sequoia) | Swift 6, SwiftUI, AppKit, SQLite, EventKit, WidgetKit, UserNotifications | Native macOS window, 7-column timetable canvas, menu bar mini-status, native Settings window, desktop widgets. |
| **Android** | Android 16 (HyperOS 3) & Android 17 (HyperOS 4) | Kotlin, Jetpack Compose, Room, Android Calendar Provider, Glance / AppWidget | Material 3 + HyperOS visual integration, 7-column timetable, bottom sheet details, agenda timeline, lockscreen & launcher widgets. |

---

## 3. License Evaluation

Before officially finalizing the repository license, we evaluate the trade-offs across three leading open-source licenses:

| Criterion | MIT | Apache License 2.0 | GNU GPLv3 |
| :--- | :--- | :--- | :--- |
| **Permissiveness** | Highly permissive, minimal restrictions | Permissive with express patent grant | Strong copyleft; derivatives must remain open source |
| **Patent Protection** | None explicit | Explicit patent rights grant from contributors | Explicit patent retaliation clause |
| **Commercial Adaptation** | Allowed freely without code release | Allowed freely with notice retention | Modifications in redistributed binaries must be published |
| **Ecosystem Fit** | Great for libraries, higher risk of commercial repackaging without attribution | **Recommended**: Protects authors via patent grants and trademark clauses while welcoming broad community contribution | Strong defense against closed forks, but may complicate enterprise adoption or App Store integration |

> **Recommendation**: **Apache License 2.0** provides the ideal balance for a community-driven cross-platform client with extensible plugin architecture, safeguarding contributors through patent retaliation clauses while preserving open collaboration. *Final selection will be finalized upon project maintainer confirmation.*

---

## 4. Repository Structure

```
ClassFlow/
├── AGENTS.md                  # Working rules & Codex development procedure
├── README.md                  # Project overview and architecture roadmap
├── LICENSE.md                 # License specification and evaluation
├── docs/                      # Technical specifications and design records
│   ├── FEASIBILITY.md         # Environment checks & platform API research
│   ├── ARCHITECTURE.md        # System architecture and layered design
│   ├── DATA_MODEL.md          # Domain entities, schemas, and occurrences
│   ├── SYNC_PROTOCOL.md       # BYOC storage provider & append-only sync protocol
│   ├── DESIGN_SYSTEM.md       # macOS & Android UI/UX design tokens
│   ├── ROADMAP.md             # Development roadmap (Phases 0-10)
│   ├── TEST_PLAN.md           # Cross-platform test and verification plan
│   └── ADR/                   # Architecture Decision Records
├── apps/
│   ├── macos/                 # Native macOS application (SwiftUI/AppKit)
│   └── android/               # Native Android application (Compose/Room)
├── packages/
│   ├── schemas/               # Canonical JSON Schemas (Course, Semester, Backup)
│   └── fixtures/              # Test fixtures and reference data
└── scripts/                   # Verification tools & automation scripts
```

---

## 5. Development Phases

- **Phase 0**: Technical Pre-Research & Environment Check *(Current)*
- **Phase 1**: Project Initialization & Architectural Design *(Current)*
- **Phase 2**: Core Timetable Engine & Schema Validation
- **Phase 3**: macOS Native Client
- **Phase 4**: Android / HyperOS Native Client
- **Phase 5**: File Import/Export (`.classflow`, `.ics`)
- **Phase 6**: BYOC Cloud Sync (Qiniu Kodo / S3 Engine)
- **Phase 7**: System Calendar Integration (EventKit / Calendar Provider)
- **Phase 8**: Notifications & Desktop Widgets
- **Phase 9**: Xiaomi Super Island Adaptation
- **Phase 10**: Hardening, Quality Assurance & Open Source Release

Refer to [`docs/ROADMAP.md`](file:///Users/xin/Desktop/Schedule/docs/ROADMAP.md) for full phase milestones and criteria.
