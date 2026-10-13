# Technical Feasibility & Environment Verification Report

**Phase 0 Output** | **Project**: ClassFlow | **Date**: 2026-10-14  
**Status Tags**: `[VERIFIED]`, `[UNVERIFIED]`, `[BLOCKED]`

---

## 1. Local Development Toolchain Inventory

| Tool / Environment | Detected Status | Version / Details | Feasibility Assessment |
| :--- | :--- | :--- | :--- |
| **Operating System** | `[VERIFIED]` | macOS 15.8.1 (Build 24H32, arm64) | Meets minimum target (macOS 15.8+). Fully supported. |
| **Swift Compiler** | `[VERIFIED]` | Apple Swift 6.1.2 (swiftlang-6.1.2.1.2) | Target `arm64-apple-macosx15.0`. Modern concurrency & Swift 6 language mode available. |
| **Swift Package Manager** | `[VERIFIED]` | SPM 6.1.0 | Operational. Clang module cache configured locally to ensure sandbox execution. |
| **Xcode IDE (`xcodebuild`)** | `[UNVERIFIED]` | Command Line Tools active; `/Applications/Xcode.app` not detected | SPM builds and command-line test suites succeed. Full AppKit/SwiftUI graphical bundle compilation and `.app` packaging require Xcode installation for Phase 3. |
| **Java / JDK** | `[BLOCKED]` | No active runtime detected (`Unable to locate Java Runtime`) | Required for Android build tools and Gradle daemon. JDK 17 or JDK 21 must be installed before Phase 4. |
| **Android SDK / Studio** | `[BLOCKED]` | Not found in `/Applications` or `~/Library/Android` | Required for local Android compilation. Can run schema validations and protocol tests in the interim. |
| **Python** | `[VERIFIED]` | Python 3.9.6 (`/usr/bin/python3`) | Available for schema validation, cross-platform fixtures, and protocol simulation scripts. |
| **Git** | `[VERIFIED]` | Git 2.39+ (`/usr/bin/git`) | Operational for repository revision tracking and commit history. |

---

## 2. Xiaomi HyperOS 3 & HyperOS 4 Compatibility

### 2.1 OS Baseline & Android API Levels
Research into Xiaomi HyperOS release engineering indicates:
- **HyperOS 2**: Android 15 (API 35).
- **HyperOS 3**: Android 16 (API 36), initial rollout Q4 2025.
- **HyperOS 4**: Android 17 (API 37), beta August 2026, rollout Q4 2026.

### 2.2 System Capabilities & Adaptation Strategy
- **Baseline Architecture**: Standard Android Jetpack libraries (Compose, Room, Android Calendar Provider, Glance AppWidget) are fully compatible across stock Android and HyperOS.
- **System Navigation & Window Insets**: Must handle predictive back gestures and dynamic edge-to-edge window insets in Android 15/16/17 using `WindowInsetsCompat`.
- **System Theme & Font**: Integrate `MiSans` dynamic font weights and Material You dynamic color adaptation.
- **Feasibility Status**:
  - Standard Android features: `[VERIFIED]` via standard Android API specifications.
  - HyperOS 3/4 physical device validation: `[UNVERIFIED]` (requires physical Xiaomi device running HyperOS 3/4).

---

## 3. Xiaomi Super Island (超级岛) & Focus Notification Evaluation

### 3.1 Official Integration Mechanics
HyperOS exposes dynamic status surfaces through two layers:
1. **Focus Notification (焦点通知)**: Introduced in HyperOS 2 (status bar ticker, lock screen capsule, AOD, and cover screens).
2. **Super Island (超级岛)**: Upgraded in HyperOS 3/4 to include interactive island summary capsules and expanded island states.

### 3.2 Technical Requirements & Ingestion Channels
- **Client-Side Native Path**: Extending Android `Notification.Builder` with `addExtras(miuiFocusBundle)` carrying the `miui.focus.param` JSON schema.
- **Status & Permission Probe**: Validated via ContentProvider call:
  `content://miui.statusbar.notification.public` with method `canShowFocus`.
- **Cloud Path**: Xiaomi Cloud Push (MiPush OpenAPI `vip.api.xmpush.xiaomi.com`).

### 3.3 Feasibility & Access Barriers for Open Source Software
> [!CAUTION]
> **Super Island Official Access Status: `[BLOCKED]`**  
> 1. Super Island requires a registered `APP_ID` on the Xiaomi Developer Platform (`dev.mi.com`).  
> 2. Xiaomi enforces strict business scenario pre-approval (limited primarily to ride-hailing, food delivery, flight boarding, and official services).  
> 3. Standard open-source distributions and community-built debug APKs lack signed whitelisting.

### 3.4 Engineering Resolution
- **Strict Prohibition**: In compliance with Project Principles, ClassFlow will **never** simulate Super Island using floating windows (`TYPE_APPLICATION_OVERLAY`) or private undocumented hooks.
- **Standard Fallback**: Provide high-priority standard Android `NotificationCompat` with `Ongoing=true` and custom expanded layouts as the guaranteed default.
- **Pluggable Adapter**: Abstract island interactions through a `SuperIslandAdapter` interface. When a validated App ID and system permission are detected, the adapter injects `miui.focus.param`; otherwise, it cleanly falls back without error.

---

## 4. Qiniu Cloud (Kodo) & S3-Compatible BYOC Feasibility

### 4.1 Storage Protocol & Compatibility
- **S3 API Compatibility**: Qiniu Kodo offers native AWS S3-compatible endpoints (e.g., `s3.cn-east-1.qiniucs.com`).
- **Signature Mechanism**: Fully supports AWS Signature Version 4 (SigV4) headers (`Authorization: AWS4-HMAC-SHA256`) and Path-Style / Virtual-Hosted bucket access.
- **Architectural Benefit**: Implementing an AWS S3 SigV4 StorageProvider enables ClassFlow to natively support Qiniu Kodo as well as any standard S3-compatible provider (MinIO, Cloudflare R2, AWS S3, Aliyun OSS) using a single, unified protocol engine.

### 4.2 Security & Credential Hygiene
- **Threat Vector**: Hardcoding or storing unencrypted Access Key (AK) and Secret Key (SK) in local configuration files or sync manifests exposes user cloud accounts to exfiltration.
- **Platform Enclaves**:
  - macOS: Apple Keychain Services (`SecItemAdd`, `SecItemCopyMatching`).
  - Android: Android KeyStore + `EncryptedSharedPreferences`.
- **Credential Broker Pattern**: For advanced deployments, design an optional self-hosted STS (Security Token Service) credential broker in `services/credential-broker` providing short-lived session tokens.

### 4.3 Storage Concurrency & Overwrite Hazards
- **Challenge**: Object storage services like Qiniu Kodo do not offer ACID cross-file transactions or distributed locks.
- **Hazard**: Multiple devices concurrently overwriting a single `timetable.json` leads to silent data loss (Last-Write-Wins hazard).
- **Solution**: Design a **Device-Isolated Append-Only Log** protocol:
  - Each device only writes revisions to its own path: `devices/{deviceId}/revisions/{revId}.json`.
  - Checkpoint manifests indicate valid revision heads.
  - Conflict resolution is performed deterministically on client devices via 3-way merge or CRDT sets.
- **Feasibility Status**: `[VERIFIED]` in protocol design; `[UNVERIFIED]` on live Qiniu bucket until test credentials are provided.

---

## 5. Summary Feasibility Status Matrix

| Component / Subsystem | Architectural Feasibility | Operational Readiness | Current Blockers |
| :--- | :--- | :--- | :--- |
| **macOS 15 Timetable Engine** | Feasible (Swift 6 + AppKit/SwiftUI) | Ready for Phase 2 Engine / Phase 3 UI | Xcode.app needed for `.app` bundle assembly |
| **Android Timetable Engine** | Feasible (Kotlin + Compose + Room) | Blocked on local toolchain | JDK and Android SDK setup needed |
| **System Calendar (macOS EventKit)** | Feasible (Native EventKit API) | Ready for Phase 7 implementation | None |
| **System Calendar (Android Provider)** | Feasible (Android Calendar Provider) | Ready for Phase 7 implementation | Runtime permission handling |
| **BYOC Qiniu Kodo Sync Engine** | Feasible (S3 SigV4 compatible) | Ready for Phase 1 Protocol / Phase 6 Code | Requires user bucket credentials |
| **Xiaomi Super Island** | Feasible only with OEM approval | `[BLOCKED]` for open builds; standard fallback operational | Platform developer portal whitelist |
