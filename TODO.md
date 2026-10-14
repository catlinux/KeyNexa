# To do

[English](TODO.md) | [Español](docs/es/TODO.md)

Detailed steps: see the [roadmap](docs/ROADMAP.md).

## High priority (phase C — project foundation)

- [ ] Biometric unlock: the ADR-0006 matrix is checked on a Pixel 10 Pro (enable, unlock, new fingerprint invalidates, password due after the chosen days, password changed on another device). Still open: phones without StrongBox and API 26-29.

- [ ] Differential test of KeyNexa-written vaults against keepass-rs, and Jazzer fuzzing (ADR-0005; the mutation fuzzing and the keepassxc-cli comparison already run).
- [ ] Measure kotpass Argon2 on a real phone once the app opens a vault; decide on argon2kt (see `docs/BENCHMARKS.md`).
- [ ] Approve the rest of the identity proposed in `docs/DESIGN.md` (palette, font, manual light/dark switch); the mark is decided.

- [ ] Contribute the kotpass fixes upstream: four patches are ready in `.agents/plans/kotpass-upstream/`; publishing needs the maintainer's approval and tests inside kotpass.

## Medium priority (phase D — secure MVP)

- [ ] Cloud as an always-on hub: let a vault live in WebDAV or S3-compatible storage through a remote `StoragePort` with conditional writes (ETag / If-Match), so the same read-merge-write pipeline works for devices that are never on at the same time. Needs an ADR.
- [ ] Cheaper look for outside changes: compare size and modification time first and read the whole file only when they differ; look less often after minutes without activity. Measure the battery cost on a real phone before and after.
- [ ] Fully reliable clipboard clearing (Android only lets the app in front change the clipboard).
- [ ] Ask for inclusion in F-Droid and IzzyOnDroid (E2.4): the draft metadata and the steps are in `.agents/plans/store-inclusion/`; the requests are filed by hand (Izzy's form asks about AI use and needs honest answers).

## Low priority (later)

- [ ] Update the Android Gradle plugin, Gradle and Kotlin (stable releases only) to clear the findings of the informational build-tooling scan in CI (104 on 2026-10-14, all in build tools, none in the APK).
- [ ] Windows and Linux clients, once the Android app is functional (they reuse the `core-*` modules).
- [ ] Autofill service and credential provider (passkeys).
- [ ] TOTP.
- [ ] Desktop clients (Windows, Linux).

## Under evaluation (decision pending)

- Showing changes not yet uploaded requires the Syncthing add-on (KeyNexa does not use its REST API).
- Built-in synchronisation that does not depend on Syncthing (for example a desktop client acting as the relay), keeping Syncthing as an option.
- Bringing the cryptographic primitives fully inside the project (they are already vendored with kotpass) instead of depending on external binaries. Writing new cipher algorithms is not planned: only audited implementations, pinned and tested against official vectors.
- Syncthing as an optional plugin on every platform, never required: a single device needs no synchronisation (the vault is local; cloud backup copies are a separate, recommended option). Reason: when synchronisation is a separate app it is easy to forget to start it, and then nothing syncs; with the plugin, opening KeyNexa starts it, shows the state ("synchronised a few seconds ago") and prevents setup mistakes such as sharing the wrong folder. Offered when a second device is added, paired with a QR code. Modes: built-in (recommended) / use an external Syncthing / none. On Android it needs a foreground service that stays alive until the pending upload finishes after the app is left. Ship Syncthing (MPL-2.0, compatible with GPL-3.0) managed through its REST API (local only, random key). Needs an ADR (size per architecture, background process rules, update policy, coexistence with Syncthing-Fork). Cheap interim step to check: ask an installed Syncthing-Fork to start when KeyNexa opens.
- Setting up a new device from a seed phrase: an encrypted bundle with the settings and the synchronisation pairing (never the master password), protected with a key derived from the phrase, kept in the synchronised folder or shown as a QR code. Needs an ADR.
- Backup copies to the cloud: first to any folder the system exposes (Drive, Dropbox, Mega and others through their own apps, with no extra SDK), later WebDAV and S3-compatible storage; no proprietary SDKs with telemetry. The app must strongly recommend it (first-run prompt and a persistent reminder until a copy exists, never forced): a lost or broken phone with no copy means losing every password. Needs an ADR.
- Keep kotpass strong: offer to help maintain it upstream, add Jazzer fuzzing and the keepass-rs comparison (already listed above), and re-evaluate alternatives yearly (ADR-0005).

## Discarded

- Rust core with UniFFI: KDBX writing in keepass-rs is experimental and UniFFI adds an NDK toolchain; re-evaluated before 1.0 ([ARCHITECTURE](docs/ARCHITECTURE.md#multiplatform-strategy)).
- `MANAGE_EXTERNAL_STORAGE` permission: not needed with SAF and not allowed on Google Play for this use.
- Depending on Syncthing's REST API: requires an API key and Syncthing may not be installed.
