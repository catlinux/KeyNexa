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

- [ ] Offer `*.sync-conflict-*` copies for merge in the UI.
- [ ] Cheaper look for outside changes: compare size and modification time first and read the whole file only when they differ; look less often after minutes without activity. Measure the battery cost on a real phone before and after.
- [ ] When a save fails because the grant was lost, offer to reconnect the folder without closing the vault; offer to restore the private unsaved copy after a restart.
- [ ] Fully reliable clipboard clearing (Android only lets the app in front change the clipboard).
- [ ] Biometric unlock.

## Low priority (later)

- [ ] Windows and Linux clients, once the Android app is functional (they reuse the `core-*` modules).
- [ ] Autofill service and credential provider (passkeys).
- [ ] TOTP.
- [ ] Desktop clients (Windows, Linux).

## Under evaluation (decision pending)

- Built-in synchronisation that does not depend on Syncthing (for example a desktop client acting as the relay), keeping Syncthing as an option.
- Bringing the cryptographic primitives fully inside the project (they are already vendored with kotpass) instead of depending on external binaries. Writing new cipher algorithms is not planned: only audited implementations, pinned and tested against official vectors.
- Syncthing as an optional plugin of the desktop clients only (Windows, Linux), never required: a single device needs no synchronisation (the vault is local), and cloud backup copies are a separate, recommended option. The plugin is offered when a second device is added (for example the phone), and then pairing is a QR code. Android keeps using Syncthing-Fork as a separate app. Ship Syncthing (MPL-2.0, compatible with GPL-3.0) managed through its REST API. Needs an ADR (size, background process, update policy).
- Setting up a new device from a seed phrase: an encrypted bundle with the settings and the synchronisation pairing (never the master password), protected with a key derived from the phrase, kept in the synchronised folder or shown as a QR code. Needs an ADR.
- Backup copies to the cloud: first to any folder the system exposes (Drive, Dropbox, Mega and others through their own apps, with no extra SDK), later WebDAV and S3-compatible storage; no proprietary SDKs with telemetry. Needs an ADR.
- Keep kotpass strong: offer to help maintain it upstream, add Jazzer fuzzing and the keepass-rs comparison (already listed above), and re-evaluate alternatives yearly (ADR-0005).

## Discarded

- Rust core with UniFFI: KDBX writing in keepass-rs is experimental and UniFFI adds an NDK toolchain; re-evaluated before 1.0 ([ARCHITECTURE](docs/ARCHITECTURE.md#multiplatform-strategy)).
- `MANAGE_EXTERNAL_STORAGE` permission: not needed with SAF and not allowed on Google Play for this use.
- Depending on Syncthing's REST API: requires an API key and Syncthing may not be installed.
