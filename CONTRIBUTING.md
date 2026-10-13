# Contributing to ClassFlow

Thank you for your interest in contributing to ClassFlow! We welcome contributions from developers, designers, and students passionate about building high-quality, local-first timetable and calendar software.

---

## 1. Core Engineering Principles

Before opening a pull request or submitting code, ensure you align with our architecture principles:

1. **Native First**: Native Swift/SwiftUI on macOS; Kotlin/Jetpack Compose on Android. No generic WebViews or cross-platform web wrappers for primary UI.
2. **Local First & Offline First**: Local database (SQLite / Room) is the single source of truth for immediate interaction.
3. **Bring Your Own Cloud (BYOC)**: Zero mandatory official SaaS accounts. Storage providers (Qiniu Kodo, WebDAV, S3) are configured by the user.
4. **Clean Domain Separation**: Courses and Schedules are strictly differentiated from generic Calendar Events (`CalendarEvent`).
5. **Privacy First**: Cloud credentials (AK/SK) must never be logged, synced, or stored in plaintext. Use macOS Keychain and Android KeyStore / EncryptedSharedPreferences.
6. **No Fake Implementations**: Never stub or simulate unverified hardware or OS features (such as Xiaomi Super Island) with unauthorized hacks like floating overlays. If an API is unverified or requires closed platform permissions, explicitly mark it `[UNVERIFIED]` or `[BLOCKED]`.

---

## 2. Mandatory 10-Step Codex Workflow

Every contributor and agent must follow this discipline:

1. **Read & Align**: Review `AGENTS.md`, `docs/ARCHITECTURE.md`, and relevant ADRs.
2. **Inspect Existing State**: Examine current code, schema files, and test suites.
3. **Plan Minimally**: Formulate a concise plan targeting the current phase only.
4. **Implement Small & Complete**: Deliver production-ready code with complete error handling, nullability guards, and clear naming.
5. **Preserve Documentation**: Preserve existing comments, docstrings, and architectural rationale.
6. **Add Automated Tests**: Write automated tests for all business logic, data conversions, timeline calculations, and schema definitions.
7. **Execute & Verify**: Run tests locally in the target environment. Ensure clean exits without regressions.
8. **Inspect Diffs**: Check `git status` and `git diff` to ensure zero extraneous files or secret leaks.
9. **Update Documentation**: Synchronize `docs/` and tracking documents whenever schemas or interfaces evolve.
10. **Report Results**: Structure task summaries using the standard reporting format.

---

## 3. Development Setup

### macOS Requirements
- macOS 15.0+ (Apple Silicon `arm64`)
- Xcode 16.0+ / Command Line Tools (Swift 6.0+)
- Python 3.10+ (for foundation schema test suites)

```bash
# Verify foundation tests
python3 scripts/test_foundation.py

# Run Core Engine tests
cd packages/core-engine && swift test

# Run macOS Client tests
cd apps/macos && swift test
```

### Android Requirements
- JDK 17+
- Android Studio Ladybug / Koala
- Android SDK (API 26 minSdk, API 35+ targetSdk)

---

## 4. Pull Request Guidelines

1. Create a descriptive feature branch: `git checkout -b feat/your-feature-name`.
2. Follow Conventional Commits:
   - `feat(scope): ...`
   - `fix(scope): ...`
   - `test(scope): ...`
   - `docs(scope): ...`
3. Ensure 100% of automated tests pass before opening a PR.
4. Include test coverage for newly introduced components.
