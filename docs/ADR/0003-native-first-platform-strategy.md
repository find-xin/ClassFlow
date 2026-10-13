# ADR 0003: Native First Platform Strategy

**Status**: Accepted  
**Date**: 2026-10-14  
**Context**: Modern cross-platform frameworks (Electron, Flutter, React Native, WebView-based shells) promise code reuse across macOS and Android. However, student timetable and calendar applications demand immediate responsiveness, deep OS widget integration, native calendar provider bridging, low energy consumption, and high visual fidelity adhering to platform HIG.

---

## Decision

We reject generic WebViews, Electron shells, and unified cross-platform rendering engines for ClassFlow's primary user interface.

Instead:
1. **macOS Client**: Developed with native Swift 6, SwiftUI, AppKit, SQLite, EventKit, WidgetKit, and UserNotifications.
2. **Android Client**: Developed with native Kotlin, Jetpack Compose, Room, Android Calendar Provider, and Jetpack Glance.
3. **HyperOS Capabilities**: Isolated into a dedicated adaptation module (`apps/android/hyperos-adapter`) using public OEM APIs only, with graceful fallback to standard Android notifications and widgets.
4. **Shared Assets & Specifications**: Code sharing occurs at the level of formal data schemas (`packages/schemas/`), file interchange specifications (`.classflow`, `.ics`), test fixtures, and protocol documentation.

---

## Consequences

### Positive
- Instant cold-start launch times (< 300ms) and minimal memory footprints (< 40MB).
- First-class support for native desktop features (macOS menu bar extra, native popovers, traffic light buttons, trackpad gestures).
- First-class support for mobile features (Material You, Glance widgets, quick setting tiles, dynamic font sizing).
- Zero third-party runtime bloat or security vulnerabilities common to embedded browser shells.

### Negative / Trade-offs
- Requires maintaining two distinct native UI codebases (SwiftUI and Jetpack Compose).
- Domain calculation algorithms must be ported or shared via common test suites and schema validators.
