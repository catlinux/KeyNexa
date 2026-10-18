# Roadmap

[English](ROADMAP.md) | [Español](es/ROADMAP.md)

Legend: ✅ done · 🔄 in progress · ⏳ planned. Nothing is "done" until its acceptance criteria and tests pass.

## Phases

| Phase | Content | Target version | Status |
| --- | --- | --- | --- |
| A — Research | Environment, KDBX libraries, licenses, Android constraints | 0.1.0 | ✅ |
| B — Architecture and security | Architecture, threat model, MVP, license decision | 0.1.0 | ✅ |
| C — Project foundation | Gradle multi-module skeleton, CI, lint/format, i18n, design system, release workflow | 0.2.0 – 0.4.0 | ✅ |
| D — Secure MVP | Open/lock/edit vaults, safe save, external changes and merge, conflict copies, biometric unlock, auto-lock and screen protection, optional cloud copies, the KeyNexa keypad for master passwords | 0.5.0 – 0.33.x | ✅ |
| E — Review | Dependency and security review, interoperability tests, docs, first pre-release (0.10.2); final review in [SECURITY_REVIEW](SECURITY_REVIEW.md) (2026-10-18) | 0.10.2 – 0.33.x | ✅ |
| F — Desktop and own sync | Repository reorganised by role, Windows 10/11 client with the same functions and look as Android, local-only / cloud copies / device sync modes, synchronisation through the cloud and between devices without Syncthing ([ADR-0011](decisions/0011-repository-layout.md), [ADR-0012](decisions/0012-windows-desktop-client.md), [ADR-0013](decisions/0013-device-sync.md)) | 1.0.1 – 1.11.x | 🔄 |
| Later | Autofill, credential provider, TOTP setup (showing existing codes read-only already works), Linux client | 1.x | ⏳ |

## MVP (Android)

**In scope**

1. Open an existing KDBX 3.1/4.0/4.1 vault chosen with the system file picker (file or folder grant), with master password and/or key file.
2. Create a new KDBX 4 vault (Argon2id, AES-256 or ChaCha20).
3. Browse groups, search entries, view an entry; copy username/password with sensitive-clipboard flag and auto-clear.
4. Create, edit, move and delete entries and groups (to the recycle bin when enabled), keeping history.
5. Save with the safe-save pipeline; detect external changes; automatic merge; conflict copies merged when the vault is opened and offered for deletion, never deleted without consent.
6. Lock manually, on timeout and on screen off; `FLAG_SECURE`; no backup of app data.
7. Optional biometric unlock (Keystore-wrapped master key).
8. English and Spanish UI, light and dark themes, accessible (TalkBack labels, scalable text).

**Out of scope for the MVP:** autofill and passkeys, configuring TOTP from the app (existing codes are shown read-only), cloud providers, desktop clients, KDB (KeePass 1.x) files.

## MVP acceptance criteria

| # | Criterion | Verified by |
| --- | --- | --- |
| AC1 | Vaults created by KeePassXC and KeePass 2.x (test files, no real data) open correctly with every supported cipher/KDF combination | Automated compatibility tests with a fixture corpus |
| AC2 | A vault saved by KeyNexa opens in KeePassXC with all groups, entries, custom fields, attachments, history, icons and custom data intact | Round-trip tests + manual check recorded in the release notes |
| AC3 | Saving never leaves a truncated or undecryptable file, even if the process is killed during the write | Fault-injection tests on the safe-save pipeline with fake storage |
| AC4 | An external change made while the vault is open is merged on save without losing either side's changes | Merge unit tests + device test with Syncthing |
| AC5 | Unmergeable conflicts never overwrite the file; both versions are kept | Unit tests |
| AC6 | No secret appears in logcat, crash output or app files outside the vault and backups | Log assertions in tests + manual review |
| AC7 | Lock clears decrypted data and keys; auto-lock works on timeout and screen off | Instrumented tests |
| AC8 | All user-visible text exists in English and Spanish; no layout breaks with long strings | Lint (`HardcodedText`, missing translations) + screenshot tests |
| AC9 | Dependency license and vulnerability checks pass in CI | CI report |

## Versioning strategy

- [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`, stored in the `VERSION` file (the Android build will read it).
- During `0.y.z` the format, settings and APIs may change between minor versions; a `0.y` release is **not** production-ready, even if it compiles and passes tests.
- `1.0.0` requires: MVP acceptance criteria met, an external security review or at least a documented independent review, and no known data-loss bugs.
- **Android 1.0 status (2026-10-18):** published as `v1.0.0` on 2026-10-18; the owner declared it ready. The documented review is [SECURITY_REVIEW](SECURITY_REVIEW.md) (an AI review, not an independent one) and no data-loss bug is known. Biometrics on phones without StrongBox or on API 26–29 and Argon2 on a modest phone are postponed and do not block 1.0. It is published in both builds, `offline` and `cloud` (owner's decision of 2026-10-18), and needs the `v1.0.0` tag, which also proves the new release workflow. Next: the desktop client.
- Every finished change bumps the version and adds a [CHANGELOG](../CHANGELOG.md) entry in the same commit.

## Release criteria (every published build)

1. All automated tests and static analysis pass in CI.
2. Dependency vulnerability scan and license report reviewed.
3. CHANGELOG and documentation updated in both languages.
4. Release APK built and signed by GitHub Actions from a `vX.Y.Z` tag; checksums published.
5. Pre-1.0 releases are marked as *pre-release* on GitHub.
