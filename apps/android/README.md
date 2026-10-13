# ClassFlow Android & HyperOS Project Architecture Specification

**Minimum SDK**: API 26 (Android 8.0)  
**Target SDK**: API 36 / 37 (Android 16 / 17 - HyperOS 3 / 4)  
**Primary Language**: Kotlin 2.x  
**UI Paradigm**: Jetpack Compose + Material 3

---

## Directory & Module Layout

```
apps/android/
├── build.gradle.kts                      # Root Gradle build script
├── settings.gradle.kts                   # Module definitions
├── app/                                  # Main application module
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/org/classflow/
│   │   │   ├── ClassFlowApplication.kt   # Application class
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt       # Single activity host
│   │   │   │   ├── timetable/            # 7-column timetable screen & gestures
│   │   │   │   ├── agenda/               # Chronological agenda stream
│   │   │   │   ├── profile/              # Settings, semester management, sync UI
│   │   │   │   └── detail/               # BottomSheet course detail & editor
│   │   │   ├── data/
│   │   │   │   ├── local/                # Room database, entities, and DAOs
│   │   │   │   └── security/             # Android KeyStore & EncryptedSharedPreferences
│   │   │   ├── calendar/                 # Android CalendarContract provider sync
│   │   │   └── widgets/                  # Jetpack Glance widgets (2x2, 4x2, 4x4)
│   │   └── res/                          # Themes, icons, adaptive icons
│   └── src/test/                         # Android unit tests
└── hyperos-adapter/                      # Isolated HyperOS adaptation module
    ├── src/main/
    │   └── kotlin/org/classflow/hyperos/
    │       ├── SuperIslandAdapter.kt     # miui.focus.param injector (permission-checked)
    │       ├── FocusNotificationProbe.kt # content://miui.statusbar.notification.public probe
    │       └── HyperOSFontProvider.kt    # MiSans dynamic weight integration
    └── src/test/
```

---

## Engineering Guardrails
1. **Separation of HyperOS Capabilities**: All Xiaomi-specific integrations remain quarantined in `:hyperos-adapter`. The `:app` module interacts only via an abstract interface with standard Android fallback.
2. **No Overlay Simulation**: Super Island must never be simulated with `TYPE_APPLICATION_OVERLAY`. If developer platform permission is unverified or blocked, standard `NotificationCompat` is displayed.
3. **Database**: Room database with SQLite foreign keys and WAL mode.
