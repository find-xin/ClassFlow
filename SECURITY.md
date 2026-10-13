# Security Policy

## 1. Supported Versions

| Version | Supported          |
| :---    | :---               |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## 2. Security Philosophy & Threat Model

ClassFlow adheres to a strict **Privacy First** and **Bring Your Own Cloud (BYOC)** philosophy:

1. **No Central SaaS Database**: ClassFlow does not maintain an official server storing user schedules, credentials, or telemetry.
2. **Hardware-Backed Secret Storage**:
   - macOS: Cloud credentials (AK/SK) and sync passwords are encrypted via `SecItemAdd` in the macOS Keychain.
   - Android: Credentials are stored using AES256-GCM via `EncryptedSharedPreferences` backed by the Android KeyStore.
3. **No Plaintext Logging**: AK/SK strings are strictly prohibited from logs, crash reports, export archives (`.classflow`), and sync revision logs.
4. **Isolated Sync Paths**: Sync engines write only to device-isolated prefixes (`devices/{deviceId}/revisions/`), preventing unauthorized file overwrites.

---

## 3. Reporting a Vulnerability

If you discover a security vulnerability in ClassFlow:

- **Do NOT** report vulnerabilities in public GitHub issues or discussions.
- Please email the maintainers privately at `security@classflow.org` (or submit a GitHub Private Vulnerability Report).
- Include detailed reproduction steps, target platform, and affected versions.
- We will acknowledge receipt within 48 hours and provide an estimated mitigation timeline.
