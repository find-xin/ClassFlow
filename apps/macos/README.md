# ClassFlow macOS Project Architecture Specification

**Minimum Deployment Target**: macOS 15.8 (Sequoia)  
**Primary Language**: Swift 6 (Strict Concurrency)  
**UI Paradigm**: SwiftUI 6 + AppKit

---

## Directory & Module Layout

```
apps/macos/
├── ClassFlow.xcodeproj / Package.swift   # Project definition
├── Sources/
│   ├── App/
│   │   ├── ClassFlowApp.swift            # App entry point (@main)
│   │   ├── AppDelegate.swift             # AppKit lifecycle & window delegates
│   │   └── MenuBarExtraController.swift  # Menu bar companion view
│   ├── UI/
│   │   ├── MainWindow/
│   │   │   ├── MainWindowToolbar.swift   # Top toolbar (semester, week nav, action buttons)
│   │   │   ├── TimetableGridView.swift   # 7-column timetable canvas
│   │   │   ├── SectionAxisView.swift     # Left section time index
│   │   │   └── CourseCardView.swift      # Frosted glass card with custom color
│   │   ├── CourseDetail/
│   │   │   ├── CoursePopoverView.swift   # Non-modal course inspection popover
│   │   │   └── CourseEditSheet.swift     # Course editor with scope selection
│   │   └── Settings/
│   │       ├── SettingsView.swift        # Native macOS Settings tabs
│   │       ├── TimetableSettingsTab.swift
│   │       ├── SyncSettingsTab.swift
│   │       └── AppearanceSettingsTab.swift
│   ├── Services/
│   │   ├── macOSKeychainService.swift    # Secure credential storage via SecItem
│   │   ├── EventKitSyncService.swift     # System Calendar integration
│   │   └── NotificationScheduler.swift   # UserNotifications integration
│   └── Adapters/
│       └── SQLiteDriver.swift            # Local SQLite database driver
└── Tests/
    └── TimetableViewModelTests.swift     # Native UI / State unit tests
```

---

## Build & Tooling Requirements
- **Compiler**: Apple Swift 6.1+
- **Build Tool**: Swift Package Manager / Xcode 16+
- **Packaging**: Universal binary (arm64 + x86_64), hardened runtime with sandboxing and Keychain entitlement.
