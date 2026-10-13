# AGENTS.md - ClassFlow Contributor & Agent Guidelines

## 1. Project Context & Philosophy

ClassFlow is a high-quality, open-source, native cross-platform timetable and calendar application built for macOS and Android.

### Core Tenets
1. **Native First**: Native Swift/SwiftUI/AppKit on macOS; Kotlin/Jetpack Compose on Android. No generic WebViews or cross-platform web wrappers for primary UI.
2. **Local First & Offline First**: Local database (SQLite/Room) is the single source of truth for immediate interaction. Network is asynchronous and additive.
3. **Bring Your Own Cloud (BYOC)**: Zero mandatory official SaaS accounts. Storage providers (e.g., Qiniu Kodo, WebDAV, S3) are configured by the user.
4. **Clean Domain Separation**: Courses and Schedules are strictly differentiated from generic Calendar Events (`CalendarEvent`). Unified queries project into `TimelineItem` / `Occurrence` models.
5. **Privacy & Security First**: Cloud credentials (AK/SK) must never be logged, synced, or stored in plaintext. Use macOS Keychain and Android KeyStore / EncryptedSharedPreferences.
6. **No Fake Implementations**: Never stub or simulate unverified hardware or OS features (such as Xiaomi Super Island) with unauthorized hacks like floating overlays. If an API is unverified or requires closed platform permissions, explicitly mark it `[UNVERIFIED]` or `[BLOCKED]`.

---

## 2. Mandatory Agent Workflow (Codex Procedure)

Every engineering cycle or task must execute the following 10-step discipline:

1. **Read & Align**: Review `AGENTS.md`, `docs/ARCHITECTURE.md`, and relevant ADRs before making architectural or code decisions.
2. **Inspect Existing State**: Examine current code, schema files, and test suites. Do not guess directory layouts or dependencies.
3. **Plan Minimally**: Formulate a concise, focused plan targeting the current phase only. Avoid premature optimization or future-phase scope creep.
4. **Implement Small & Complete**: Deliver production-ready code with complete error handling, nullability guards, and clear naming. No empty dummy placeholders.
5. **Preserve Documentation**: Preserve existing code comments, docstrings, and architectural rationale unless specifically commanded to revise them.
6. **Add Automated Tests**: Write automated tests for all business logic, data conversions, timeline calculations, and schema definitions.
7. **Execute & Verify**: Run tests locally in the target environment. Ensure clean exits without regressions.
8. **Inspect Diffs**: Check `git status` and `git diff` to ensure clean changes with zero extraneous files or secret leaks.
9. **Update Documentation**: Synchronize `docs/` and tracking documents whenever schemas or interfaces evolve.
10. **Report Results**: Structure task summaries using the standard reporting format:
    ```markdown
    Completed:
    <Summary of completed work>

    Files Changed:
    <List of modified and added files>

    Tests:
    <Executed commands and test pass status>

    Limitations:
    <Known gaps, unverified APIs, or blocked features>

    Next:
    <Immediate recommended steps>
    ```

---

## 3. Engineering Rules & Platform Guardrails

### macOS Guidelines
- Minimum Deployment Target: macOS 15.8 (Sequoia).
- UI: Native SwiftUI + AppKit integration. Adhere to macOS 15 Human Interface Guidelines.
- Avoid retro-futuristic or unsupported UI gimmicks (e.g., macOS 26 liquid glass).
- Primary window: Full-width timetable grid with left section axis and top toolbar. No permanent sidebar eating screen width.
- Popover / sheets for course inspection; separate native Settings window.
- System Calendar: EventKit read/write with stable external mapping IDs.

### Android Guidelines
- Baseline: Target modern Android (API 34+) with standard Jetpack Compose, Room, Glance, and standard `NotificationCompat`.
- HyperOS 3 (Android 16) / HyperOS 4 (Android 17) adaptation:
  - Isolate HyperOS capabilities in an adaptation module (`apps/android/hyperos-adapter`).
  - Rely exclusively on public Android / HyperOS APIs.
  - Do NOT assume API availability based on marketing OS names; verify against runtime SDK capabilities.
  - Super Island: Requires Xiaomi Developer Platform registered App ID and scenario whitelisting. Flagged as `[BLOCKED]`. Fallback gracefully to standard persistent notification. Never use `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` to simulate dynamic islands.

### Data & Cloud Sync Protocol
- Independent Storage Provider interface: Decouple storage read/write from synchronization orchestration.
- Avoid naive Last-Write-Wins (LWW) file overwrites.
- Use device-isolated write paths (`devices/{deviceId}/revisions/...`) to prevent race conditions on storage providers lacking atomic multi-file transactions.
- Maintain stable UUIDs for all entities and occurrences.
- All file exports (`.classflow`) must be versioned ZIP packages containing deterministic JSON manifests.

---

## 4. Status Tracking Tags
When reviewing or writing code/docs, maintain explicit status tags:
- `[VERIFIED]`: Tested and operational on local runtime.
- `[UNVERIFIED]`: Designed according to public specs but not yet executed on physical target OS.
- `[BLOCKED]`: Requires external enterprise verification, proprietary developer portal approval, or hardware unavailable in current environment.
