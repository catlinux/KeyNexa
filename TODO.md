# To do

[English](TODO.md) | [Español](docs/es/TODO.md)

Detailed steps: see the [roadmap](docs/ROADMAP.md).

## High priority (phase C — project foundation)

- [ ] First signed test release: push a tag (with maintainer approval) and check the workflow, the signature and the checksum on GitHub.
- [ ] Differential test of KeyNexa-written vaults against keepass-rs, and Jazzer fuzzing (ADR-0005; the mutation fuzzing and the keepassxc-cli comparison already run).
- [ ] Measure kotpass Argon2 on a real phone once the app opens a vault; decide on argon2kt (see `docs/BENCHMARKS.md`).
- [ ] Approve the rest of the identity proposed in `docs/DESIGN.md` (palette, font, manual light/dark switch); the mark is decided.

- [ ] Contribute the kotpass fixes upstream: four patches are ready in `.agents/plans/kotpass-upstream/`; publishing needs the maintainer's approval and tests inside kotpass.
- [ ] First pre-release on GitHub once phases D5, D6 and the review (E1) are done; the release workflow has never run.

## Medium priority (phase D — secure MVP)

- [ ] Vault opening, locking and auto-lock.
- [ ] Offer `*.sync-conflict-*` copies for merge in the UI.
- [ ] When a save fails because the grant was lost, offer to reconnect the folder without closing the vault; offer to restore the private unsaved copy after a restart.
- [ ] Detect external changes when the app returns to the foreground (without waiting for the next save).
- [ ] Entry and group editing; search.
- [ ] Biometric unlock.

## Low priority (later)

- [ ] Windows and Linux clients, once the Android app is functional (they reuse the `core-*` modules).
- [ ] Autofill service and credential provider (passkeys).
- [ ] TOTP.
- [ ] Desktop clients (Windows, Linux).

## Under evaluation (decision pending)

- Built-in synchronisation that does not depend on Syncthing (for example a desktop client acting as the relay), keeping Syncthing as an option.
- Bringing the cryptographic primitives fully inside the project (they are already vendored with kotpass) instead of depending on external binaries.

## Discarded

- Rust core with UniFFI: KDBX writing in keepass-rs is experimental and UniFFI adds an NDK toolchain; re-evaluated before 1.0 ([ARCHITECTURE](docs/ARCHITECTURE.md#multiplatform-strategy)).
- `MANAGE_EXTERNAL_STORAGE` permission: not needed with SAF and not allowed on Google Play for this use.
- Depending on Syncthing's REST API: requires an API key and Syncthing may not be installed.
