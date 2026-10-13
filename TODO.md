# To do

[English](TODO.md) | [Español](docs/es/TODO.md)

Detailed steps: see the [roadmap](docs/ROADMAP.md).

## High priority (phase C — project foundation)

- [ ] First signed test release: push a tag (with maintainer approval) and check the workflow, the signature and the checksum on GitHub.
- [ ] Differential test of KeyNexa-written vaults against keepass-rs, and Jazzer fuzzing (ADR-0005; the mutation fuzzing and the keepassxc-cli comparison already run).
- [ ] Measure kotpass Argon2 on a real phone once the app opens a vault; decide on argon2kt (see `docs/BENCHMARKS.md`).
- [ ] Approve or change the visual identity proposed in `docs/DESIGN.md` (mark, palette, font, manual light/dark switch).

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
