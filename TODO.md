# To do

[English](TODO.md) | [Español](docs/es/TODO.md)

Detailed steps: see the [roadmap](docs/ROADMAP.md).

## High priority (phase C — project foundation)

- [ ] Gradle multi-module skeleton (`core-vault`, `core-kdbx`, `core-sync`, `platform-android`, `app-android`) reading the version from `VERSION`.
- [ ] Release workflow: signed APK from a `vX.Y.Z` tag, published as a pre-release with checksums.
- [ ] Test-vault corpus created with KeePassXC and KeePass 2.x (fictitious data only).
- [ ] Spike: kotpass round-trip fidelity and Argon2 performance on a low-end device.
- [ ] Design system: original icon, palette, typography, spacing, components, light and dark themes.
- [ ] Enable GitHub private vulnerability reporting in the repository settings.

## Medium priority (phase D — secure MVP)

- [ ] Vault opening, locking and auto-lock.
- [ ] Safe-save pipeline and private backups.
- [ ] External-change detection, merge and conflict-file handling.
- [ ] Entry and group editing; search.
- [ ] Biometric unlock.

## Low priority (later)

- [ ] Autofill service and credential provider (passkeys).
- [ ] TOTP.
- [ ] Desktop clients (Windows, Linux).

## Discarded

- Rust core with UniFFI: KDBX writing in keepass-rs is experimental and UniFFI adds an NDK toolchain; re-evaluated before 1.0 ([ARCHITECTURE](docs/ARCHITECTURE.md#multiplatform-strategy)).
- `MANAGE_EXTERNAL_STORAGE` permission: not needed with SAF and not allowed on Google Play for this use.
- Depending on Syncthing's REST API: requires an API key and Syncthing may not be installed.
