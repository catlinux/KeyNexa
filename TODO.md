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
- [ ] Ask for inclusion in F-Droid and IzzyOnDroid (E2.4; parked on 2026-10-15: the first IzzyOnDroid request on GitLab was rejected for not following the request template, so it has to be redone by hand with the template): the draft metadata and the steps are in `.agents/plans/store-inclusion/`; the requests are filed by hand (Izzy's form asks about AI use and needs honest answers).

## Open follow-ups and checks (2026-10-15)

- [ ] **Try on the phone what is only covered by tests:** the sync status dialog and the "file has not changed" notice (0.12.0), restoring unsaved changes after the app is killed mid-save (0.13.0), locking in the background (0.10.3), clearing an entry's history (0.15.0), and the cloud assistant with a real S3 account (H6).
- [ ] **Cloud copies** ([ADR-0008](docs/decisions/0008-cloud-backup.md), accepted on 2026-10-15): the S3 flow works with a real Backblaze B2 account (2026-10-15). Next: ease the form (offer the bucket list instead of typing it, if the service allows listing with a bucket-limited key), guides and a real test for Cloudflare R2 and Amazon S3, and a way to disconnect one service and choose another; then Dropbox, OneDrive and Google Drive once the app is registered with each; the user guide `docs/CLOUD_SETUP.md` is verified only for Backblaze B2. Plan: `.agents/plans/cloud-backup/`.
- [ ] **kotpass pull requests #43 to #46** ([keemobile/kotpass](https://github.com/keemobile/kotpass/pulls)): the red CI was diagnosed and handled on 2026-10-15 (#43: test fixture fixed and pushed as `1955d4a`; #44 and #46: only the Codecov token step, which forks cannot pass, explained in a comment; #45: network timeout, PR reopened) and the maintainer's question on #46 was answered. Waiting only for the maintainer: his reply on #46 and, if it asks for it, approval to run the workflow on #43. Notes: `.agents/plans/kotpass-upstream/`.
- [ ] **Check the release workflow with the build variants:** `release.yml` now signs the `offline` APK from its new path and has not run since; the first tag after 0.10.3 will prove it. Nothing after 0.10.3 has been released.
- [ ] Remove the stale vault entries left in the phone's list (two "demo" vaults and the "prova-ac4" duplicates).

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
- Backup copies to the cloud: first to any folder the system exposes (Drive, Dropbox, Mega and others through their own apps, with no extra SDK), later WebDAV and S3-compatible storage; no proprietary SDKs with telemetry. The app must strongly recommend it (first-run prompt and a persistent reminder until a copy exists, never forced): a lost or broken phone with no copy means losing every password. Needs an ADR. Now proposed in [ADR-0008](docs/decisions/0008-cloud-backup.md); the step plan is in `.agents/plans/cloud-backup/`.
- Keep kotpass strong: offer to help maintain it upstream, add Jazzer fuzzing and the keepass-rs comparison (already listed above), and re-evaluate alternatives yearly (ADR-0005).

## Discarded

- Rust core with UniFFI: KDBX writing in keepass-rs is experimental and UniFFI adds an NDK toolchain; re-evaluated before 1.0 ([ARCHITECTURE](docs/ARCHITECTURE.md#multiplatform-strategy)).
- `MANAGE_EXTERNAL_STORAGE` permission: not needed with SAF and not allowed on Google Play for this use.
- Depending on Syncthing's REST API: requires an API key and Syncthing may not be installed.
