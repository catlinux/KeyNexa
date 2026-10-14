# Changelog

[English](CHANGELOG.md) | [Español](docs/es/CHANGELOG.md)

All notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.12.0] - 2026-10-14

### Added

- Sync status of the vault file (file modification time, last save by KeyNexa, last change received from another device) with an optional warning after several days without changes. KeyNexa cannot tell what Syncthing has uploaded; the warning is a heuristic.

### Changed

- `StoragePort` gains `lastModified(location)`.

## [0.11.0] - 2026-10-14

### Added

- A Syncthing conflict copy written with another password is no longer only reported: the notice offers to type that password, and the copy is merged into the vault when the save succeeds. The password is used once and wiped, also when it does not open the copy; a rejected password leaves the copy as it was and the notice says so.

## [0.10.3] - 2026-10-14

### Fixed

- The vault now locks after the selected background delay even if the app stays in the background; returning to the app cancels the timer and checks the elapsed time.
- Clipboard clearing checks KeyNexa's marker before removing a copied secret, preserving clipboard content replaced by another app.
- Screen protection is applied before the first frame using the saved screenshot setting.

### Changed

- Documentation now describes the published v0.10.2 pre-release, implemented mitigations and the APK runtime dependencies.
- Added store metadata and phone screenshots (fictitious vault) in `fastlane/metadata/android/` (English and Spanish) for IzzyOnDroid and F-Droid, and the APK no longer embeds the dependency-information block.
- The build now requires the minor and patch version components to stay below 100 so `versionCode` keeps increasing.

## [0.10.2] - 2026-10-14

### Fixed

- The informational scan of the build tooling no longer shows a red cross on every commit: `continue-on-error` moves from the job to its step, so the job ends green and its findings stay visible in the log. It still does not block anything.

## [0.10.1] - 2026-10-14

### Fixed

- "Open an existing vault" closed the app with `IllegalArgumentException: Can only use lower 16 bits for requestCode`: `androidx.biometric` 1.1.0 pulled in `androidx.fragment` 1.2.5, which rejects the request codes of the current Activity Result API. `androidx.fragment` 1.8.5 (Apache-2.0) is now a direct dependency. Bug introduced in 0.9.0.

### Note

- Conflict-copy merging (0.10.0) was checked on a real phone with Syncthing: both edits were merged, they reached the PC, the app offered to delete the copy and it disappeared from the Windows folder after accepting.

## [0.10.0] - 2026-10-14

### Added

- Syncthing conflict copies are merged into the vault automatically (phase D7, ADR-0007): when a vault is opened and when a version written elsewhere is loaded, the `*.sync-conflict-*` copies next to the vault file are decoded with the open vault's credentials and folded in with the merge semantics, and the result goes through the safe-save pipeline. A per-vault ledger of fingerprints in private storage (excluded from backup) avoids decrypting the same copy twice, and a copy is recorded only once the save that merged it reports success. The vault screen then says how many copies were merged and offers to delete them, and only copies that still match the recorded fingerprint are deleted: nothing is deleted without consent, declining keeps the copies and brings the offer back on the next open. A copy written with another password, damaged or still in transfer, belonging to another vault or unreadable is reported with its reason and left untouched.

### Changed

- `StoragePort` gains `name(location)` (the file's real name in storage) and `delete(location)`; the Android implementation uses the document's display name and `DocumentsContract.deleteDocument`.

### Note

- Not checked on a real phone yet: merging on open, the offer to delete the copies and the case of a copy written with another password still need the device test with Syncthing (D7.5).

## [0.9.0] - 2026-10-14

### Added

- Biometric unlock (phase D6, ADR-0006). After a password unlock the app offers to enable it for that vault (enable, not now, or never ask again). It stores, encrypted with a per-vault Android Keystore key that needs a strong biometric on every use (StrongBox when available, invalidated when new biometrics are enrolled or the screen lock is removed), the hash of the password and of the key file, never the password; the key derivation still runs on each unlock. The password is asked again after 7 days without typing it (1 to 30 days in the settings) and whenever the key is invalidated or the stored data no longer opens the vault, with a message that explains why. A button on the unlock screen stops using it for that vault.
- Kotpass gains `Credentials.fromHashes` (local change recorded in its README) and the engine `KdbxEngine.hashedForm`, so a vault opens from the pre-hashed components. Tested against the whole test corpus, including key-file vaults.

### Changed

- `MainActivity` extends `FragmentActivity` (needed by `BiometricPrompt`) and the app depends on `androidx.biometric` 1.1.0 (Apache-2.0).

### Note

- Not checked on a real phone yet: the unit tests cover the storage rules and the component format, but the Keystore and the prompt need the device test matrix (listed in the TODO).

## [0.8.1] - 2026-10-14

### Added

- Settings screen (gear on the home screen): auto-lock delay, lock when the screen turns off, allow screenshots and how long a copied secret stays on the clipboard. Checked on a Pixel 10 Pro: the vault locks after the chosen time in the background, the window cannot be captured while screenshots are off, an outside change appears within seconds, and neither the password nor any entry text shows up in the system log or in the app's files (AC6).

## [0.8.0] - 2026-10-14

### Added

- Protections (phase D5): the open vault locks by itself after a time in the background (60 seconds by default; options from "immediately" to "never"), when the screen turns off, and manually; locking wipes the credentials, drops the vault from memory, clears the clipboard and returns to the home screen. The window is marked secure (no screenshots, blanked in the recent-apps list) unless the setting allows screenshots. The copied-secret delay is a setting (30 seconds by default). Settings are stored privately and are still changed from code only: the settings screen comes next.
- Changes made to the vault file by another program (Syncthing, KeePassXC) are noticed within about 5 seconds while the app is in front, and loaded by themselves when the open vault has no unsaved edits; otherwise they are merged at the next save.

### Changed

- An edit form that is open keeps its fields when the vault is refreshed from disk, and closes if its entry was removed elsewhere.

## [0.7.5] - 2026-10-14

### Changed

- Vendored kotpass: `readHeader` now checks the KDBX signature before parsing the header, so data that is not KDBX is rejected as such.
- TODO: ideas under evaluation recorded (Syncthing built in, device setup from a seed phrase, cloud backup copies, keeping kotpass strong). Four kotpass fixes, with tests, are ready as branches of the project's fork for the upstream maintainer.

## [0.7.4] - 2026-10-14

### Changed

- README and roadmap now describe the real state of the project (what works on Android, what is missing, how to try a debug build with Syncthing).

## [0.7.3] - 2026-10-14

### Fixed

- Saving failed after Syncthing replaced the vault file: Android revokes the grant on a single file in that case (`SecurityException`, found on a Pixel 10 Pro). Vaults are now added and created through a folder grant (`ACTION_OPEN_DOCUMENT_TREE`), which survives the replacement as ADR-0004 foresaw. The home screen asks for the folder that holds the vault, lists the `.kdbx` files in it and creates new vaults inside it.
- Merging a vault edited elsewhere while it is open was checked on a real device: a phone edit and a KeePassXC edit made in between both end up in the file (acceptance criterion AC4).

## [0.7.2] - 2026-10-14

### Fixed

- The new-entry form no longer opens with the "an entry needs a title" error already showing; it appears only after the title has been typed and emptied.

## [0.7.1] - 2026-10-13

### Fixed

- Content no longer hides under the system status bar, navigation bar or keyboard (the menu with the recycle bin was out of reach).
- The clipboard is cleared when KeyNexa is back in front after 30 seconds, and when the vault is locked: Android does not let an app in the background change the clipboard, so a timer alone was not enough.

## [0.7.0] - 2026-10-13

### Added

- Content screens (phase D4.4): browse groups and entries, search (case- and accent-insensitive, never in passwords), entry detail (hidden password shown for 30 seconds at most, copy with a sensitive-clipboard mark cleared after 30 seconds, open web addresses), add and edit entries and custom fields, built-in password generator (`PasswordGenerator`, secure random), recycle bin with restore, permanent delete and empty, and a save-status bar that explains each failure and offers to retry. Every edit is saved at once through the safe-save pipeline.
- Access screens (phase D4.3): home with the known vaults (add, create, remove from the list without touching the file), create-vault form (name, master password twice, optional key file, unrecoverable-password warning) and unlock screen (clear messages for each failure, progress while the key is derived, reconnect-file option), wired in `MainActivity` with the system file pickers and lasting read/write grants.
- App foundation (phase D4.2): `AppContainer` wiring storage, engine, safe saver and private stores; `VaultSession` (unlock, create, edit with automatic safe save, retry, lock that wipes the credentials, crash recovery on opening); the list of known vaults; and screen navigation by state.
- Editing use cases in `core-vault` (phase D4.1): `VaultEditor` (add, edit with history, move, delete to the recycle bin, restore, empty it, permanent deletion recording deleted objects), `VaultSearch` (case- and accent-insensitive, never reads passwords or protected values, leaves out the recycle bin) and `NewVault` (empty KDBX 4.1 vault with ChaCha20 and Argon2id).
- ADR-0006: design of the biometric unlock (phase D6): composite-key components wrapped by a per-vault Android Keystore key bound to strong biometrics on every use, invalidated on biometric changes, with a periodic master-password prompt.

## [0.6.1] - 2026-10-13

### Added

- Android storage (phase D3) in `platform-android`: `SafStoragePort` (Storage Access Framework; writes with `"rwt"`, falls back to `"rw"` and always truncates explicitly, turns any provider failure or revoked grant into `IOException`), `SafVaultAccess` (persistent folder and file grants, locating the vault by name inside a granted folder, `isReachable` for the "reconnect file" flow), and `FileBackupStore` and `FileSaveJournal` (atomic writes under `noBackupFilesDir`; an unreadable journal is an error, never "nothing pending").
- `conflictCopiesOf` in `core-sync`: recognises Syncthing `*.sync-conflict-*` copies next to the vault, newest first, so the UI can offer them for merge.

### Removed

- The placeholder `PlatformModule`.

## [0.6.0] - 2026-10-13

### Added

- Merge (phase D2): `KeePassMerger` in `core-sync`, a two-way merge with KeePass synchronisation semantics written from its documented behaviour: matching by UUID, newer modification wins with the other version kept in history, histories united and trimmed to the vault's limits, location by the newer location-changed time, deletions only through deleted-objects records and never over later changes, metadata field by field. Loops from crossed moves and deletion records for the root group are handled without losing data. `SafeSaver` uses it to fold external changes in before writing.
- 44 merge tests: scenarios, invariants checked on randomly generated vault pairs (no object lost, no loops, no dangling attachments, symmetric results) and end-to-end saves through `SafeSaver` (AC4, unit part).

## [0.5.0] - 2026-10-13

### Added

- Safe-save pipeline and change detection in `core-sync` (phase D1): `SafeSaver` writes only after copying the previous file and the new bytes to private storage and recording the save in a journal, and reports success only after reading the file back with the expected SHA-256 fingerprint; a file torn by its own write is restored byte for byte, and one replaced by another program is left alone. `SaveRecovery` finishes or undoes a save interrupted by a crash, without needing the password. `VaultReader` retries files that may still be in transit. Unmergeable or external changes never overwrite the file: the user's version is kept as a private copy (AC5).
- Ports `BackupStore` and `SaveJournal`, a `write` operation in `StoragePort`, and the `VaultMerger` interface that phase D2 implements.
- Fault-injection tests (53) with fake storage: cut writes, writes that do not truncate, providers that lie, external changes before, during and after the write, and crashes at every point (AC3 and AC5).

### Changed

- detekt allows up to four returns (guard clauses excluded) and treats deliberately translated I/O and vault exceptions as handled.

## [0.4.1] - 2026-10-13

### Changed

- The app icon is now the maintainer's mark (hexagon, shield and keyhole; source in `design/icon/`), traced to vector paths with a themed monochrome variant. The primary and secondary colours follow the icon's navy and sky blue, still at WCAG AA or better.

## [0.4.0] - 2026-10-13

### Added

- Design system proposal (phase C6): `docs/DESIGN.md` with principles, identity, an accessible colour palette for light and dark themes, typography, spacing, components, states and the security messages. The visual identity awaits the maintainer's approval.
- The palette and Material 3 theme in `app-android`, with a unit test that fails the build if any text pair drops below WCAG AA (4.5:1) or any interface edge below 3:1.
- An original adaptive launcher icon (a key with a three-node network) with a themed monochrome variant.

### Changed

- The "missing launcher icon" lint exception is gone now that the app has an icon.

## [0.3.1] - 2026-10-13

### Added

- Mutation fuzzing of the decoder (phase C5b, first part): randomly damaged copies of corpus vaults may only fail with a `VaultException`, within a time limit. Nine thousand mutations ran clean.
- The CI installs KeePassXC so the differential test runs there.

### Security

- The engine refuses key derivation settings beyond sane limits (Argon2 memory above 2 GiB, more than 10000 iterations or 256 lanes, more than 500 million AES-KDF rounds) before doing any work: the parameters sit in the unencrypted header, so a damaged or hostile file could otherwise freeze or crash the app.

## [0.3.0] - 2026-10-13

### Added

- KDBX engine (phase C5). `core-vault` defines the domain model (vault, groups, entries, history, attachments, custom data, unknown XML), the `KdbxEngine` and `StoragePort` interfaces, `Credentials` (wipeable secrets) and `VaultException`. `core-kdbx` implements the engine on kotpass: `KotpassKdbxEngine` with a complete two-way mapping, so a load and a save lose no data, and with failures translated to `VaultException` without secrets in the messages.
- kotpass 0.13.0 (upstream commit `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`) imported as the Gradle module `:kotpass` in `third_party/kotpass/`, with Okio 3.15.0 as its only dependency; credits for kotpass, Okio and the notices bundled in it.
- Acceptance tests AC1 and AC2 against the 18-vault corpus: opening, expected content, errors, round trips for every cipher and key derivation, and a differential comparison with `keepassxc-cli`.
- `docs/BENCHMARKS.md`: Argon2 timings of the pure-Kotlin implementation on a desktop JVM, and the pending phone measurement.

### Changed

- Local fixes in the vendored kotpass, listed in its README: unknown XML elements are kept and written back; a custom icon or custom-data item without a modification time no longer gets a bogus one; a truncated or tampered KDBX 4 file is reported as damaged instead of as a wrong key; the KDBX 3.x stream-start check now actually throws (upstream bug); a public header reader.
- ktlint and detekt share a 120-character line limit.

## [0.2.9] - 2026-10-13

### Fixed

- Dependency verification now also covers the Linux and macOS variants of `aapt2`, which the Linux CI runner needs; the metadata had only the Windows one.

### Added

- `scripts/ci-gradle-error.sh`: the CI publishes the "What went wrong" block of a failed Gradle run as an annotation, readable without signing in.

## [0.2.8] - 2026-10-13

### Added

- Three more test vaults saved from KeePassXC's interface: Twofish with Argon2d, ChaCha20 with Argon2id, and Argon2d with a tag and a TOTP field. The corpus in `testdata/` is now complete (18 vaults).

## [0.2.7] - 2026-10-13

### Fixed

- The CI and release workflows now locate `sdkmanager` under `ANDROID_HOME` instead of relying on the `PATH`, where the runner does not provide it (exit code 127).

## [0.2.6] - 2026-10-13

### Added

- Fictitious test-vault corpus in `testdata/` (15 vaults and 3 key files) written by KeePass 2.61 and KeePassXC 2.7.12: AES-256 and ChaCha20, AES-KDF, Argon2d and Argon2id, KDBX 3.1, 4.0 and 4.1, key files, Unicode password, history, attachments, icons, tags and a 1 GiB Argon2 vault.
- Generator scripts and header inspector in `tools/testdata/`, documented in `docs/TEST_VAULTS.md` together with the three vaults that still need KeePassXC's graphical interface.

## [0.2.5] - 2026-10-13

### Changed

- The CI vulnerability scan now covers only the dependencies distributed with the app, through Gradle dependency locking (the `gradle.lockfile` files of the runtime configurations). Vulnerabilities in build tooling are reported by a new, non-blocking job.

## [0.2.4] - 2026-10-13

### Added

- `docs/TEST_VAULTS.md`: list of fictitious vaults to create with KeePassXC and KeePass 2.x for the compatibility corpus.

### Fixed

- The Android SDK licence step in the CI and release workflows no longer fails with SIGPIPE under `pipefail`.

## [0.2.3] - 2026-10-13

### Added

- Release workflow (`.github/workflows/release.yml`): on a `vX.Y.Z` tag it checks the tag against `VERSION` and `CHANGELOG.md`, builds the release APK, signs it with the repository secrets, and publishes a GitHub release (pre-release before 1.0) with the APK and its SHA-256 file.
- Documentation of how to create the signing keystore and the four repository secrets.

## [0.2.2] - 2026-10-13

### Added

- Continuous integration with GitHub Actions (`.github/workflows/ci.yml`): build and quality checks, bilingual-documentation check, dependency-license report and an OSV vulnerability scan.
- `scripts/check-docs-i18n.sh`, which fails when an English document has no Spanish translation or vice versa, or when a language line is missing.
- The Gradle License Report plugin, wired into `generateLicenseReport` for all subprojects.

## [0.2.1] - 2026-10-13

### Added

- Quality checks wired into `./gradlew check`: ktlint (with `ktlintFormat` to auto-correct), detekt and hardened Android Lint in both Android modules, where `HardcodedText`, `MissingTranslation`, `ExtraTranslation` and the security checks are errors.
- Gradle dependency verification: every dependency, plugin and tool is verified against the SHA-256 checksums in `gradle/verification-metadata.xml`.

## [0.2.0] - 2026-10-13

### Added

- Multi-module Gradle skeleton (phase C1): the Kotlin/JVM 17 modules `core-vault`, `core-kdbx` and `core-sync`, the Android library `platform-android` and the Compose + Material 3 application `app-android`, with a version catalog and a trivial JUnit test per module.
- `app-android` takes its `versionName` from `VERSION` and derives `versionCode` from it; the manifest disables backups and declares no permission, and the interface strings are provided in `en-US` and Spanish only through string resources.
- Verified build, test and packaging instructions in `docs/DEVELOPMENT.md`.

## [0.1.2] - 2026-10-13

### Added

- ADR-0005: kotpass is kept, vendored as a module and reinforced (differential tests against KeePassXC and keepass-rs, fuzzing, XML-preservation fixes, optional native Argon2), after re-evaluating kotpass, KeePassJava2, keepass-rs and kdbxweb.

## [0.1.1] - 2026-10-13

### Changed

- Spanish translations moved to `docs/es/`, mirroring the English structure; only `README.es.md` stays in the root. Language links and the bilingual rules in CONTRIBUTING updated.

## [0.1.0] - 2026-10-13

### Added

- Research and architecture phase (phases A and B): architecture, threat model, dependency and license research, roadmap with MVP acceptance criteria, development plan and architecture decision records 0001–0004.
- License: GPL-3.0-only with additional attribution and trademark terms (section 7) in `NOTICE`; `CREDITS.md`.
- Project documentation in English and Spanish: README, CONTRIBUTING, SECURITY, CHANGELOG, TODO.
- `VERSION` file.
