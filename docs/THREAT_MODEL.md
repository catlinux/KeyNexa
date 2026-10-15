# Threat model

[English](THREAT_MODEL.md) | [Español](es/THREAT_MODEL.md)

> Status: **initial draft** (v0.1.0), written before any sensitive code. It will be revised at every phase. KeyNexa has not been audited; nothing here is a guarantee.

## Assets

| Asset | Where it lives |
| --- | --- |
| Decrypted vault contents (passwords, notes, attachments, TOTP secrets) | App memory while unlocked |
| Master password and key file | User's memory / user-chosen file; transiently in app memory |
| Derived keys (composite key, transformed key) | App memory while unlocked |
| Encrypted vault file (`.kdbx`) | User storage, synced folders, private backups |
| Biometric unlock material (master key wrapped by a Keystore key) | App-private storage + Android Keystore |
| Release signing key | Maintainer only, never in the repository or CI logs |

## Attackers in scope

1. Someone with physical access to a **locked** device (theft, loss).
2. Someone with brief access to an **unlocked** device.
3. A malicious app on the same device (no root): overlays, clipboard reading, fake autofill targets, a malicious keyboard.
4. Someone who obtains the encrypted `.kdbx` file (from a synced device, a backup, a cloud copy) and attacks it offline.
5. A compromised or malicious sync peer that modifies or corrupts the file.
6. A compromised dependency or build pipeline (supply chain).

**Out of scope:** a rooted or compromised operating system, hardware attacks on the Keystore/TEE, a user who reveals their master password, and malware with accessibility-service control of the device. These are documented as residual risks.

## Threats and mitigations

| # | Threat | Mitigation (planned unless stated) |
| --- | --- | --- |
| T1 | Offline brute force of the master password | KDBX 4 with Argon2id as default for new vaults, parameters calibrated per device (target: about 1 s unlock) with sane minimums; password-strength feedback when creating a vault; key-file support. Existing vaults keep their own KDF settings unless the user changes them. |
| T2 | Weak or custom cryptography | No custom crypto. All primitives come from the KDBX engine (kotpass, ported from Bouncy Castle) and the platform (`javax.crypto`, Keystore). KDBX 4 header and block HMACs are always verified. |
| T3 | Secrets in memory | Keep decrypted data only while unlocked; protected fields decrypted on demand; prefer `ByteArray`/`CharArray` and zero them after use; drop references on lock. **Limitation:** on the JVM/ART, `String` objects and copies made by the garbage collector or UI toolkit cannot be reliably wiped. |
| T4 | Secrets in logs, crash reports, temp files | No logging of secrets (lint rule + review checklist); no third-party crash reporting; no temporary plaintext files; release builds strip debug logging. |
| T5 | Screen capture, recents thumbnail | **Implemented:** `FLAG_SECURE` is applied before `setContent` when “Allow screenshots” is off; that setting is off by default and can be enabled by the user. |
| T6 | Clipboard leakage | **Implemented:** sensitive clips are marked as such (Android 13+) and all carry a KeyNexa marker; auto-clear uses the configured timeout and clears only a clip with that marker. Android only lets the app clear the clipboard while it is in front, so a due clip is cleared when the user returns if another app is open. |
| T7 | Keyboard learning or keylogging | **Implemented with a limitation:** password fields use password input types and disable autocorrect. Compose does not expose `IME_FLAG_NO_PERSONALIZED_LEARNING`, so a third-party keyboard may still learn from or see typed passwords. |
| T8 | Unattended unlocked app | **Implemented:** a `Handler` schedules the selected-timeout lock while the process is active. Deep sleep can delay the timer; elapsed time is checked on return, and screen-off locking also applies. Locking clears credentials and decrypted data. |
| T9 | Biometric unlock abuse | Master key wrapped by an Android Keystore AES key with `setUserAuthenticationRequired(true)`, per-use authentication through `BiometricPrompt` + `CryptoObject`, `BIOMETRIC_STRONG` only, invalidated when biometrics change; StrongBox when available. Opt-in, with a periodic master-password re-prompt. |
| T10 | Android backup exfiltration | `allowBackup="false"` plus explicit `dataExtractionRules` and `fullBackupContent` excluding all app data; secrets in `noBackupFilesDir`. |
| T11 | Malformed or malicious `.kdbx` | KDBX 4 header and block HMACs verified before parsing (KDBX 3.1 relies on its stream start bytes and hashed blocks); limits on sizes and KDF parameters to avoid memory exhaustion; XML external entities disabled; fuzz tests on the engine adapter. |
| T12 | Data loss on save (interruption, truncation, partial writes) | Safe-save pipeline: private backup, `"rwt"` write, read-back verification, restore on failure ([ADR-0004](decisions/0004-storage-sync-and-safe-save.md)). |
| T13 | External changes and sync conflicts overwritten | **Implemented:** fingerprint checks run before saves and on resume; external vault changes are merged automatically by UUID and timestamps. Syncthing `.sync-conflict-` copies are merged when the vault opens. The app offers to delete successfully merged copies and deletes them only after consent and a matching fingerprint check. |
| T14 | Phishing through autofill (fake package names) | Credentials matched to apps by verified signing certificate and Digital Asset Links, not by package name alone; explicit confirmation when not verified. (Phase 2.) |
| T15 | Excessive permissions | **Implemented:** the `offline` build declares no `INTERNET` permission and never accesses the network; the `cloud` build declares `INTERNET` only to reach the service the user configures, and only the `offline` build is published until the cloud features are complete; no `MANAGE_EXTERNAL_STORAGE`; SAF only. |
| T16 | Supply chain | Few dependencies, pinned in a version catalog; Gradle dependency verification (checksums); automated vulnerability and license checks in CI; reproducible-build investigation before 1.0. |
| T17 | Signing key compromise | Key kept offline by the maintainer; CI signs only through encrypted repository secrets; never committed. |
| T18 | Cloud account compromise (someone gets into the chosen service) | Only encrypted copies are uploaded, so the attacker gets ciphertext; a strong master password and an optional key file are the real protection. The copy count is limited and the consent text warns that old copies can hold old passwords. Only the `cloud` variant includes this code. |
| T19 | Theft of the local cloud credentials | Stored encrypted with a non-exportable AES-256-GCM key from the Android Keystore, in app-private storage excluded from Android backup; never logged and never shown; "Disconnect and delete" removes them. |
| T20 | Exposure of file metadata (name, size, times) | The service sees only the object name (the vault name sanitised to letters, digits, `-` and `_`), its size and its times, never its content; the user can choose a vault name that does not identify it. |
| T21 | Interception of the connection | HTTPS only in production, with the system's TLS validation; redirects are not followed, so a signed request is never re-sent to another host; URLs, headers, keys and bodies are never logged. |

## Residual risks (accepted, documented to users)

- A rooted or malware-infected device can read memory and input.
- JVM strings and UI text fields may keep copies of secrets in memory until garbage collection.
- Third-party keyboards see typed passwords.
- Syncthing on Android is a third-party app; its behaviour (for example how it replaces files) can change, and recent Android security patches (September 2026) revoke file-level URI permissions when a synced file is replaced. KeyNexa mitigates this with folder grants and a "reconnect file" flow, but cannot control it.
- KDBX security depends on the strength of the master password.
