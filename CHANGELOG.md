# Changelog

[English](CHANGELOG.md) | [Español](docs/es/CHANGELOG.md)

All notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.9.2] - 2026-10-19

The apps do not change.

### Added

- Internal: access to Windows Hello from the desktop (signing a challenge with the Hello key credential, through JNA on the WinRT ABI), with a manual probe (`:platform:windows:helloProbe`). Step 0 of [ADR-0014](docs/decisions/0014-windows-hello-quick-unlock.md); nothing uses it yet.

## [1.9.1] - 2026-10-19

The apps do not change.

### Added

- Docs: [ADR-0014](docs/decisions/0014-windows-hello-quick-unlock.md) (proposed) designs the quick unlock with Windows Hello on the desktop: a documented Hello key credential (`KeyCredentialManager`) through JNA, the pre-KDF key components kept only in memory and encrypted under a key derived from a Hello signature, a limit of 1 to 24 hours, and the master password always asked for at the first unlock after starting the app. Threat T28 is added to the threat model.

## [1.9.0] - 2026-10-19

The Android app does not change.

### Added

- Desktop: groups can have an icon, chosen from 48 icons in six categories (general, security, internet, finance, devices, work) when creating or editing a group. The icon is stored as the standard KDBX icon number of the group, so it travels with the vault.

### Changed

- Desktop: the window opens about 24 % wider (1360 dp) and the side panels have more room: the group tree fits the icons and the detail panel is wider.
- Desktop: the entry list is now a column of cards (avatar, title and user name, date) under a line that says the group, how many entries it has and the order, which a click flips between name and date. This replaces the dense table.
- Desktop: the title bar and the taskbar show the KeyNexa mark instead of the default Java icon.
- Desktop: the group menu item "Rename" is now "Edit group", and its dialog also changes the icon.

## [1.8.7] - 2026-10-19

The Android app does not change.

### Changed

- Desktop: the entry detail panel now has a clearly defined layout: cards with separated rows, small-caps labels, shortcut chips and square icon buttons.

## [1.8.6] - 2026-10-19

The apps do not change.

### Changed

- Docs: synchronisation through the cloud is verified with Cloudflare R2 between the desktop and the Android cloud edition, in both directions. [ADR-0009](docs/decisions/0009-cloud-sync.md) is now Accepted, threat T27 is marked done, and the cloud guide says what was verified. Backblaze B2 has not been tried.

## [1.8.5] - 2026-10-19

The Android app does not change.

### Fixed

- Desktop: with synchronisation on, the search box in the top bar was squeezed so that "Search" broke onto two lines. The synchronisation state now sits under the vault name and the search box keeps a minimum width. Found by trying the app with two instances and a local S3 service: the switch and its check, bringing a vault from the cloud, saving in one instance and seeing it in the other with Sync now, all worked.

## [1.8.4] - 2026-10-19

Synchronisation is still off until it is turned on in the cloud assistant.

### Fixed

- Synchronisation: two devices no longer upload to each other forever. After merging the same two vaults, each side held the same things in a different order, and the order alone made KeyNexa think the cloud copy lacked something. Found by a new test with two real devices and the real KDBX engine; a quiet round now writes nothing on either side.

## [1.8.3] - 2026-10-19

Synchronisation is still off until it is turned on in the cloud assistant.

### Fixed

- Vaults: new and edited items get times in whole seconds, as the KDBX file stores them; before, the same version read back from the cloud looked different and a merge kept a duplicate of it in the entry's history.
- Synchronisation: a local file replaced by another program (Syncthing, say) before KeyNexa looked at it again is no longer uploaded as it is; the round waits until it has been merged.
- Synchronisation: when the synchronisation id cannot be saved in the vault, nothing is uploaded (before, the file could go up under an id it did not carry, and the other devices would refuse it as another vault); an id already in the vault is never replaced.

## [1.8.2] - 2026-10-19

The desktop app does not change. The offline edition does not change either.

### Added

- Android (cloud edition): Sync now in the group menu and Bring a vault from the cloud on the home screen.

## [1.8.1] - 2026-10-19

The desktop app does not change. The offline edition does not change either.

### Added

- Android (cloud edition): switch to synchronise vaults through the cloud service, with a check that the service supports conditional writes, and the synchronisation state in the cloud settings.

## [1.8.0] - 2026-10-19

The desktop app does not change. The offline edition does not change either.

### Added

- Android (cloud edition): synchronisation queue and triggers (after a save, on unlock, on returning to the app at most once a minute); inactive until the switch arrives in a later version.

## [1.7.3] - 2026-10-19

The Android app does not change.

### Added

- Desktop: Bring a vault from the cloud on the home screen.

## [1.7.2] - 2026-10-19

The Android app does not change.

### Added

- Desktop: synchronisation state in the top bar and a Sync now button (Ctrl+R).

## [1.7.1] - 2026-10-19

The Android app does not change.

### Added

- Desktop: switch to synchronise vaults through the cloud service, with a check that the service supports conditional writes, and a synchronisation card in Settings.

## [1.7.0] - 2026-10-19

The Android app does not change.

### Added

- Desktop: synchronisation queue and triggers (after a save, on unlock, on regaining focus at most once a minute); inactive until the switch arrives in a later version.

## [1.6.10] - 2026-10-19

The apps do not change.

### Fixed

- Internal: after merging a remote copy, the open vault becomes exactly what was written, so the next synchronisation does not see a difference in time resolution alone and rewrite the file again.

## [1.6.9] - 2026-10-19

The apps do not change.

### Changed

- Internal: the open vault session can absorb a remote copy and carry the synchronisation id; not yet wired into the apps.

## [1.6.8] - 2026-10-19

The apps do not change.

### Changed

- Internal: conditional S3 writes (If-Match / If-None-Match), head and fetch, conditional-write probe, sync state and runner; not yet wired into the apps.

## [1.6.7] - 2026-10-19

The apps do not change.

### Changed

- Internal: cloud contract with conditional writes, head and fetch, and the sync reconciler (not yet wired into the apps).

## [1.6.6] - 2026-10-19

The apps do not change.

### Changed

- Docs: [ADR-0009](docs/decisions/0009-cloud-sync.md) gains its implementation design for synchronising through the cloud: a conditional-write probe against the user's own bucket before synchronisation can be turned on (Cloudflare R2 documents the headers; Backblaze B2 does not), the remote copy always merged before anything is uploaded, upload only what the remote copy lacks, adoption of an existing synchronisation id by root group, and a report (never an overwrite) when the cloud copy uses another master password.

## [1.6.5] - 2026-10-19

The Android app does not change.

### Changed

- Desktop: dialogs use KeyNexa's surface colour instead of Material's default lilac (compiled and checked by the tests, not yet seen on screen).

## [1.6.4] - 2026-10-19

The Android app does not change.

### Fixed

- Desktop: pressing Enter in the "New group" and rename dialogs now confirms the name (not yet checked by hand).

### Changed

- Licenses: Skia (BSD-3-Clause) and ICU (Unicode License v3) verified at their sources; the libraries Skia itself bundles are still to be verified.

## [1.6.3] - 2026-10-19

The Android app does not change.

### Fixed

- Desktop cloud copies: an unexpected failure while copying no longer ends the worker thread (the uncaught-exception handler would have locked the vault) and no longer leaves the status as "running".

## [1.6.2] - 2026-10-19

The Android app does not change. Step 9 of the desktop plan: packages and release workflow for Windows.

### Added

- Windows installer (MSI, per user, no administrator rights, installed in `%LOCALAPPDATA%\KeyNexa-app`, apart from the data folder) and portable ZIP, both with the Java runtime inside and the KeyNexa icon; `:apps:desktop:packageMsi` and `:apps:desktop:portableZip`.
- `release.yml`: a `windows` job builds both, installs and uninstalls the MSI as a check, and publishes them with their SHA-256 next to the APKs. While the Windows client is a preview, 1.x releases are marked as pre-releases.
- `docs/INSTALL_WINDOWS.md`: download, checks, SmartScreen warning, installer, portable form and data folder.

### Changed

- Licenses of the Java runtime and Skiko registered in the dependencies and credits documents (the Skia and ICU licenses inside Skiko are still to be verified).
## [1.6.1] - 2026-10-19

The Android app does not change. First hand test of the desktop app, started from the packaged build.

### Fixed

- Desktop: the app did not start (since 1.4.0): the window focus was read outside the window. It is read inside it now.
- Desktop settings: the idle lock choices said "5 seconds" instead of minutes and the clipboard choices "60 minutes" instead of seconds; the unit is explicit now.
- Desktop cloud assistant: the window used the default Material colours instead of KeyNexa's theme.
- Desktop entry editor: an empty entry password showed the "master password" minimum; entries now only get a "weak" hint.

## [1.6.0] - 2026-10-18

The Android app does not change. Step 8 of the desktop plan is complete: settings and modes.

### Added

- Desktop: discreet reminder on the home screen to keep a cloud copy while none is configured or none exists ("Set up" opens the assistant, "Later" hides it for 7 days).

### Fixed

- Cloud assistant (desktop): editing any field after a successful connection test now requires testing again before saving.

## [1.5.3] - 2026-10-18

The Android app does not change. Step 8c, first part of the desktop plan.

### Added

- Desktop: cloud copies assistant (Settings > Copies in the cloud), same steps as on Android: consent, S3 service (Backblaze B2, Cloudflare R2), what you need, form with connection test, state with "Copy now", number of copies to keep and "Disconnect and delete". The credentials are stored sealed with DPAPI.

## [1.5.2] - 2026-10-18

The Android app does not change. Step 8b of the desktop plan.

### Added

- Desktop: settings screen, opened from the top bar and from the home screen: theme, locking (idle time, Windows lock, suspend), screenshots, KeyNexa keypad, clipboard time and the modes (local only; cloud copies and device sync are shown, not yet available). Every change applies at once.

## [1.5.1] - 2026-10-18

The Android app does not change. Step 8a of the desktop plan: the cloud base for the desktop, with no screen yet (patch: internal).

### Added

- Desktop: the cloud backup queue (credentials sealed with DPAPI, consent, reminder and state under the private data folder), connected to every successful save. It does nothing until the cloud is configured and accepted.

### Changed

- The clipboard clearing time is read on every copy, so it can change while the app runs.

## [1.5.0] - 2026-10-18

The Android app does not change. Step 7 of the desktop plan: the desktop protections equal to Android's.

### Added

- Desktop: the vault locks after 5 minutes without keyboard or mouse use in the window, when Windows is locked (Win+L) and when the computer suspends; every lock also clears the clipboard. The window is excluded from screen captures unless screenshots are allowed. The theme, the KeyNexa keypad default and the clipboard time come from the desktop settings. An unexpected error locks the vault and is printed without its message.

### Changed

- Threat model T23 and T24 (desktop) move from planned to implemented, stating that capture exclusion needs Windows 10 2004 or later and does not stop a photo of the screen.

## [1.4.1] - 2026-10-18

The Android app does not change.

### Added

- Desktop platform pieces for the security step (not used by the app yet): the desktop settings file (no secrets; out-of-range values fall back to their default), the Windows session-lock and suspend notifications received through a hidden window as Microsoft documents (no polling), and excluding the window from screen captures with `SetWindowDisplayAffinity`.

## [1.4.0] - 2026-10-18

The Android app does not change. With this version the desktop client reads and edits vaults like the Android app (step 6 of the desktop plan).

### Added

- Desktop: while the window has the focus, the vault file is checked every 5 seconds and a version written elsewhere (for example by Syncthing) is loaded with a short notice; the conflict-copies notice offers the same actions as Android (delete the merged copies, merge copies written with another password, keep them).

## [1.3.3] - 2026-10-18

The Android app does not change.

### Added

- Desktop: right-click menus on groups (new subgroup, rename, move to recycle bin) and entries (edit, move to another group, move to recycle bin), a New group action, the Delete key, and in the recycle bin restore, delete permanently and empty, each with a confirmation.
- Core: `VaultEditor.renameGroup`, which also moves the group's modification time to now, so a merge with another copy of the vault keeps the newer name.

## [1.3.2] - 2026-10-18

The Android app does not change.

### Added

- Desktop: editing and creating entries in place in the detail panel (title, user name, password with the generator and its strength, URL, notes, custom fields with their protection, attachments that can be added, removed or saved to disk after a warning that the saved file is not encrypted, and a read-only view of each previous version), saved through the safe save. Shortcuts Ctrl+N, Ctrl+E, Ctrl+S and Esc.

## [1.3.1] - 2026-10-18

The Android app does not change.

### Fixed

- Desktop, after a manual test of the three panels: the Open button only appears for web addresses (http and https), the copy confirmation reads "Copied: <field>", the disabled Settings button is readable on the top bar, the detail empties when the selected entry is not among the search results, and the search field no longer shows a clipped label.

## [1.3.0] - 2026-10-18

The Android app does not change.

### Added

- Desktop: the open vault in three panels that use the width of a PC screen: a compact tree of groups with the recycle bin apart, a dense list of entries in sortable columns (title, user name, web, modified) and the entry detail on the right (user name, hidden password, one-time code with its countdown, web address that opens in the browser for http and https only, custom fields, notes and attachment names). Search over the whole vault, and the shortcuts Ctrl+F, Ctrl+B, Ctrl+C, Ctrl+U and Ctrl+L shown in tooltips and a status bar. Copies go through the secure clipboard: passwords, protected fields, one-time codes and notes stay out of the Windows clipboard history and cloud clipboard and are cleared after 30 seconds, on lock and on exit. Editing comes in the next step.

## [1.2.1] - 2026-10-18

The Android app does not change.

### Added

- Desktop secure clipboard (not used by the app yet): on Windows a copied secret carries the formats Microsoft documents to keep it out of the clipboard history (Win+V) and the cloud clipboard, and it is cleared after 30 seconds, on lock and on exit, only if the clipboard still holds what KeyNexa put there.

## [1.2.0] - 2026-10-18

The Android app does not change. With this version the desktop skeleton is complete (step 4 of the desktop plan): it opens, creates, unlocks and locks vaults; browsing and editing come next.

### Added

- Desktop: creating a new vault (name, master password with confirmation and strength, optional key file) saved where the user chooses with the native dialog, in KDBX 4.1 with Argon2id like on Android. An existing file is never overwritten, and the password is only taken once the destination is chosen.

## [1.1.2] - 2026-10-18

The Android app does not change.

### Added

- Desktop: unlocking a vault with the master password and/or a key file (the KeyNexa keypad is available and off by default), the same error messages as Android, and a first view of the open vault with its save status, the notice to restore or discard unsaved changes from an interrupted save, and locking with a button or Ctrl+L. The password field is emptied as soon as the password is handed to the session, and the screen wipes its copies of the password and key file when it closes.

## [1.1.1] - 2026-10-18

The Android app does not change.

### Added

- First desktop window (`apps/desktop`, Compose for Desktop, Windows): KeyNexa's look and texts in English and Spanish, one instance at a time, and the home screen with the list of known vaults, opening an existing `.kdbx` with the native file dialog and removing a vault from the list (the file is never deleted). It starts with `.\gradlew.bat :apps:desktop:run`; unlocking and creating vaults come next.

## [1.1.0] - 2026-10-18

The Android app does not change. The desktop client gets its Windows platform layer (the app itself comes next).

### Added

- New module `platform/windows` (`:platform:windows`): the per-user data folder `%LOCALAPPDATA%\KeyNexa` and `DpapiSecretBox`, which seals the desktop client's secrets with Windows DPAPI for the current user ([ADR-0012](docs/decisions/0012-windows-desktop-client.md)). Its DPAPI tests run on Windows and are skipped elsewhere.
- JNA 5.19.1 (Apache-2.0, dual-licensed with LGPL-2.1-or-later) to call DPAPI, recorded in the dependencies and credits.

## [1.0.9] - 2026-10-18

Internal; the Android app does not change.

### Fixed

- CI: the license report failed on POM files of the desktop dependencies of `ui/shared` that had no verified checksum; their SHA-256 sums are added (only additions to `gradle/verification-metadata.xml`).

## [1.0.8] - 2026-10-18

Internal; the Android app does not change.

### Added

- New module `platform/desktop` (`:platform:desktop`), shared by every desktop system: `FileStoragePort` writes the vault to a temporary file in the same folder and moves it into place atomically (writing in place when the system or another program prevents the move, still guarded by the safe-save copies and read-back), the private stores under a per-user data folder, and the list of known vaults in a file.

### Changed

- The `SecretBox` interface moves from `platform/cloud` to `platform/jvm`, so the desktop secret store and the future device key can use it.

## [1.0.7] - 2026-10-18

Internal; the published Android app does not change.

### Changed

- Debug builds are a separate app, *KeyNexa Debug* (`io.github.catlinux.keynexa.debug`), so they install next to a published KeyNexa instead of failing on its signature, and a phone keeps its real vaults and settings while a build is tested.
- Checked on a phone: the reorganisation of 1.0.2–1.0.6 unlocks with the KeyNexa keypad, edits and saves a test vault, and the unlock, home, group, entry and settings screens are pixel-identical to the previous build in light and dark (only the edited entry differs).

## [1.0.6] - 2026-10-18

Internal; the Android app should look and behave the same (to be confirmed on a phone).

### Changed

- New Compose Multiplatform module `ui/shared` (`:ui:shared`, Android and desktop targets): the palette, theme, Manrope typography, brand mark, hexagon and the KeyNexa keypad move out of the Android app with their tests, so the desktop client can reuse them. Their texts move to the module (same English and Spanish strings) and the Manrope font file to `ui/shared/src/commonRes/font/`, shared by both targets.
- Compose Multiplatform 1.8.2 (JetBrains, Apache-2.0) is added for that module. It was chosen because it keeps the Android app on its current Jetpack Compose 1.9.1 and Material 3 1.3.2; in the APK it only adds the small `ui-backhandler` module. Recorded in the dependencies and credits.

## [1.0.5] - 2026-10-18

Internal; the Android app does not change.

### Changed

- New module `core/session` (`:core:session`, package `io.github.catlinux.keynexa.session`): the vault session (`VaultSession` and its states), the vault-tree helpers, the sync warning and the known-vaults list move out of the Android app with their tests, so the desktop client can reuse them. The list of known vaults is now behind a `KnownVaults` interface; Android keeps its implementation over private preferences. `kotlinx-coroutines-core` 1.9.0, already shipped, becomes a direct dependency of the new module.

## [1.0.4] - 2026-10-18

Internal; the Android app does not change.

### Changed

- New module `platform/jvm` (`:platform:jvm`): the private file stores `FileBackupStore`, `FileSaveJournal` and `FileMergedCopies`, plain JVM code, move out of `platform/android` with their tests so the desktop client can reuse them. Packages and behaviour are unchanged.

## [1.0.3] - 2026-10-18

Documentation only; the Android app does not change.

### Changed

- [ADR-0011](docs/decisions/0011-repository-layout.md): the layout is ready for Linux. Operating-system code gets one module per system (`platform/windows` with the Windows client; `platform/linux` reserved and not created until Windows is finished) behind the interfaces of `platform/desktop`, and packaging goes in `apps/desktop/packaging/<system>`, so adding Linux moves no directory.

### Added

- README, roadmap, architecture and ADR-0011: macOS and iOS are not planned, because the project has no Apple hardware to test on and a password manager cannot be released on emulators alone.

## [1.0.2] - 2026-10-18

Internal; the Android app does not change.

### Changed

- The modules move to folders by role, as [ADR-0011](docs/decisions/0011-repository-layout.md) decides: `core/{vault,kdbx,sync}`, `platform/{android,cloud}` and `apps/android` (Gradle paths `:core:vault`, `:apps:android`, …). History is kept (`git mv`). CI, release and fuzz workflows, documentation and credits follow the new paths. The unsigned APKs are now named `keynexa-android-<flavor>-release-unsigned.apk`; the published file names do not change.


## [1.0.1] - 2026-10-18

Documentation only; the Android app does not change.

### Added

- Decisions for the desktop client and KeyNexa's own synchronisation: [ADR-0011](docs/decisions/0011-repository-layout.md) (repository grouped by role, one product version), [ADR-0012](docs/decisions/0012-windows-desktop-client.md) (Windows 10/11 client with Compose Multiplatform, local-only / cloud copies / device sync modes, DPAPI, capture and clipboard protection) and [ADR-0013](docs/decisions/0013-device-sync.md) (synchronisation between paired devices over mutual TLS, without Syncthing).
- Threat model: desktop and synchronisation threats T23–T27 and their residual risks.

### Changed

- [ADR-0009](docs/decisions/0009-cloud-sync.md): the shared vault id is settled (`KeyNexa.SyncId` in the vault's custom data). Architecture, roadmap (new phase F) and to-do list updated; bundling Syncthing as a plugin is discarded.

## [1.0.0] - 2026-10-18

First stable release of the Android app, in two builds signed with the same key: `keynexa-1.0.0.apk` (offline, no Internet permission) and `keynexa-cloud-1.0.0.apk` (adds the optional encrypted cloud copies). Installing one over the other keeps vaults and settings. Since the last published pre-release (0.10.3):

### Added

- Optional encrypted cloud copies of the vault to S3-compatible storage (Backblaze B2 and Cloudflare R2 verified), with an assistant, rotation of old copies and a reminder, in the `cloud` build only ([ADR-0008](docs/decisions/0008-cloud-backup.md), [setup guide](docs/CLOUD_SETUP.md), [privacy](docs/PRIVACY.md)).
- The KeyNexa keypad for master passwords, on by default, so the system keyboard never sees them; the system keyboard is one tap away and is used automatically with a screen reader ([ADR-0010](docs/decisions/0010-in-app-password-keypad.md)).
- A new look (direction A, light and dark, Manrope font) for every screen, and a light/dark/automatic theme setting.
- One-time (TOTP) codes shown read-only, sync status of the vault file with an optional warning, attachments, clearing an entry's history, merging conflict copies written with another password, recovery of changes a failed save could not write.
- Hardening: touches ignored under other apps' windows, a quiet notice about third-party keyboards or accessibility services, conflict copies with absurd key-derivation costs refused, XML parsing without external entities.

### Changed

- Release workflow: build and signing in separate jobs, actions pinned to commits, permission checks on both APKs.

### Fixed

- CI: the build and license steps piped Gradle through `tee` without `pipefail`, so a failed build looked green. Found while preparing 1.0: since the Android Gradle plugin update (0.23.5) the CI build had been failing on two checksums that only Linux downloads (`aapt2` for Linux and the `okio` and `okio-jvm` 3.15.0 POMs). Both are now in `gradle/verification-metadata.xml`, checked against the SHA-256 and SHA-1 published by Google Maven and Maven Central. Lint's `OldTargetApi` (target 36 while the CI SDK knows 37) is informational until the build-tools migration; `ExpiredTargetSdkVersion` still blocks.

### Security

- Final security review documented in [SECURITY_REVIEW](docs/SECURITY_REVIEW.md): nine findings fixed (0.31.0 to 0.33.11), accepted risks listed, no high-severity finding open. It is an AI review, not an independent audit.

## [0.33.15] - 2026-10-18

### Removed

- Test vaults and test folders cleaned from the owner's phone and PC before 1.0 (the fictitious vaults in `testdata/` stay: the tests need them); the stale entries of the phone's vault list are gone, so the TODO item is removed. Documentation only (patch).

## [0.33.14] - 2026-10-18

### Changed

- Releases now publish both builds, signed with the same key: `keynexa-<version>.apk` (offline, no Internet permission) and `keynexa-cloud-<version>.apk` (with the optional cloud copies), each with its SHA-256 file; the workflow fails unless the offline APK lacks the Internet permission and the cloud APK has it (ADR-0008, owner's decision). The cloud release build was checked on a phone (unlock and a cloud copy). README and DEVELOPMENT updated. Patch: release tooling and documentation.

## [0.33.13] - 2026-10-18

### Changed

- The "other password" dialog of conflict copies was checked on the phone, so every phone check of the security review is done except the two the owner postponed. The roadmap marks phases D and E as done and the Android 1.0 as ready, pending its tag. Documentation only (patch).

## [0.33.12] - 2026-10-18

### Changed

- Security review: results of the phone checks (killing the app during a save, one variant over the other, clearing history, screen reader) and the checks still pending. Documentation only (patch).

## [0.33.11] - 2026-10-18

### Fixed

- On Android every vault read as damaged since 0.33.7: the XML hardening also turned off entity expansion, which there breaks the predefined entities (`&amp;` and the like) every vault contains. That setting is removed; external entities are still refused. Found on the phone, not by the JVM tests. Patch.

## [0.33.10] - 2026-10-18

### Added

- `docs/SECURITY_REVIEW.md` (and its translation): the final review before 1.0, with scope, method, the nine findings fixed in 0.31.0 to 0.33.9, the accepted risks and the checks still to do on a phone. `THREAT_MODEL` updated for T11, T16, T17 and T20. Documentation only (patch).

## [0.33.9] - 2026-10-18

### Fixed

- CI: the intermittent failure of the keepass-rs differential job was not an interoperability difference but Gradle dependency verification: that job sometimes resolves the `kotlinx-coroutines-bom` 1.8.0 POM, which had no checksum. Its SHA-256, matching the SHA-256 and SHA-1 published by Maven Central, is now in `gradle/verification-metadata.xml`. Patch: build only.

## [0.33.8] - 2026-10-18

### Fixed

- The entry screen showed *Open URL* for any address, but only http and https open: for `ssh://` and similar the button did nothing. It now appears only for web addresses (the address can always be copied), and a phone without a browser no longer crashes when it is tapped. Found in the final audit. Patch.

## [0.33.7] - 2026-10-18

### Security

- The XML parser bundled with kotpass resolved external entities on the JVM (XXE): a hostile XML key file, which is any file the user picks, could read a local file into the key or reach the network. It now refuses external entities and DTDs on every platform (Android's parser did not load them, but the desktop clients will run on the JVM). Found in the final audit, with a test that failed before the fix. Patch.

### Changed

- The Spanish README of the bundled kotpass lacked the entry for credentials from pre-hashed components; added.

## [0.33.6] - 2026-10-18

### Security

- Release workflow: building and signing are now separate jobs, so nothing the build runs (Gradle, its plugins, the dependencies, third-party actions) shares a machine with the signing key; every action is pinned to a commit; and the release fails if the offline APK asks for the Internet permission, a check the plan expected but the workflow did not have. Found in the final audit. Patch: release tooling only (not run yet; the next tag proves it).

## [0.33.5] - 2026-10-18

### Security

- A Syncthing conflict copy whose unencrypted header asks for more than 16 times the key derivation work of the open vault is no longer decrypted (it is reported as damaged), so a hostile or broken copy cannot keep the thread that also saves the vault busy for hours on every open. The engine's absolute limits still apply to everything. Found in the final audit. Patch.

## [0.33.4] - 2026-10-18

### Fixed

- Cloud copies of two vaults with the same name shared one folder and one "copies to keep" count, so uploads of one could rotate away every copy of the other. Each vault now has its own folder, `KeyNexa/<name>-<8 characters of its id>/`. Copies made before stay where they are and are never rotated or deleted by the app (delete them by hand when they are no longer wanted). Found in the final audit. Patch.

## [0.33.3] - 2026-10-18

### Security

- While a screen reader is on, master passwords are typed with the system keyboard instead of the KeyNexa keypad: the keypad would speak every key aloud, whereas the system keyboard follows the Android setting that keeps passwords from being spoken (ADR-0010 addendum). Patch.

## [0.33.2] - 2026-10-18

### Changed

- CI: when the keepass-rs differential test fails, the test message is published as an annotation that can be read without signing in, to diagnose an intermittent failure seen only in CI. Patch: CI only.

## [0.33.1] - 2026-10-18

### Fixed

- With large text, buttons with a fixed height cut their label (for example *Unlock with biometrics* showed only *Unlock with*): the unlock, home, entry and editor buttons now grow with the font. Patch.

## [0.33.0] - 2026-10-18

### Added

- The KeyNexa keypad also types the new master password and its confirmation when creating a vault (*Done* moves to the confirmation), and the password of a conflict copy written with another password (ADR-0010).

### Changed

- The three master password inputs share one component: typed text is wiped when the screen or dialog is left and when the app goes to the background, the confirmation is compared without making text copies, and the password of a conflict copy reaches the session as an array instead of a `String`.

Minor: new, compatible behaviour.

## [0.32.0] - 2026-10-18

### Added

- The KeyNexa keypad on the unlock screen (ADR-0010): the master password is typed with keys drawn by the app, so the system keyboard never sees it. On by default; a button under the field switches to the system keyboard for that attempt, and a setting turns it off. No preview bubble on the pressed key, only a short vibration; every key has a spoken label.

### Security

- On the unlock screen the typed password is wiped when it is submitted, when the screen is left and when the app goes to the background; with the keypad it is never held as a `String`.

Minor: new, compatible feature.

## [0.31.3] - 2026-10-18

### Added

- Groundwork for the KeyNexa keypad (ADR-0010, step E1): a password buffer that can be wiped and never becomes a `String`, and the fixed keypad layout (letters, digits and symbols, accents), with tests. Not visible yet (patch: internal).

## [0.31.2] - 2026-10-18

### Changed

- ADR-0010 accepted: the KeyNexa keypad is on by default, used only in master password fields, with a fixed layout. Documentation only (patch).

## [0.31.1] - 2026-10-18

### Added

- ADR-0010 (proposed): an optional keypad drawn by KeyNexa for the master password, so the system keyboard never sees it (threat T22). Documentation only (patch).

## [0.31.0] - 2026-10-18

### Added

- The unlock screen says quietly, in small grey text, when the keyboard in use or an enabled accessibility service comes from an installed app rather than the system (threat T22). No app is named and nothing is blocked.

### Fixed

- Unsaved changes are no longer stranded after a save that failed cleanly and the app was then closed: on the next open the private copy is offered (Restore / Discard) when it still adds something, and deleted silently when it adds nothing. Restoring merges it and deletes it only once saved.
- The private copy of a failed save is deleted once a later save succeeds, instead of staying in app storage forever.

Minor: new, compatible behaviour.

## [0.30.6] - 2026-10-18

### Changed

- The create-vault screen explains what the optional key file is for and warns that losing it means losing the vault. Patch: text only.

## [0.30.5] - 2026-10-18

### Security

- Touches are ignored while another app draws a window over KeyNexa (`filterTouchesWhenObscured`), against tapjacking and fake overlays (threat T22). Patch: hardening with no change to the format or to normal use.

## [0.30.4] - 2026-10-18

### Fixed

- The unlock screen showed the vault file as a URL-encoded path (`primary%3Asyncthing-proves%2F…`); it now shows the plain file name. Patch: display only.

### Changed

- Spanish wording: «cubo» (bucket) becomes «contenedor» in the cloud assistant and in the Spanish cloud guide, privacy notice and ADR-0008.

## [0.30.3] - 2026-10-17

### Added

- `docs/VAULT_CONVENTIONS.md`: file name and location advice for a real vault, and the custom field names (`key_id`, `secret_key`, `token`, `endpoint`…) to use for API credentials, so they will match the planned "API / token" entry type. Documentation only (patch).

## [0.30.2] - 2026-10-16

### Changed

- Threat model: new threat T22 (keylogger or malware capturing the master password) with its mitigations and a residual-risk note; the planned hardening is in the TODO.

## [0.30.1] - 2026-10-16

### Changed

- The cloud setup guide now covers Cloudflare R2 step by step, verified with a real account (connection test and copy uploaded).

## [0.30.0] - 2026-10-16

### Changed

- Aligned the remaining screens with the chosen visual direction A: the home screen (a brand header with the settings action, the known vaults in a card of rows with a hexagon avatar and where each one lives, a "Your vaults" section label, an empty state with the brand, and the "Open existing vault" and "Create new vault" buttons in the thumb zone), the settings screen (a header band, one card per section with a title in capitals and rows of the same height separated by a line, and the cloud-copies row fitted into its own card in the cloud build) and the entry editor (the fields in cards with 14 dp corners and a fixed bottom bar with "Cancel" and "Save"). Behaviour is unchanged.

## [0.29.0] - 2026-10-16

### Changed

- Redesigned the entry screen with the chosen visual direction A: a compact top bar with the way back and the group path, a header with the entry's hexagon avatar, title and last-changed line, one card for the fields (a small uppercase label above each value, with 44–48 dp copy/reveal/open icon buttons and the one-time code in a large monospaced figure with its seconds bar), notes, attachments and dates in their own cards, and a fixed bottom bar with a full-width "Edit" button and a square, outlined "Delete" button. Behaviour is unchanged: secrets stay hidden until revealed, and no secret reaches a description, a log or a toast.

## [0.28.0] - 2026-10-16

### Changed

- Redesigned the group screen with the chosen visual direction A: a rounded header band (the vault or group name, a saved/saving/unsaved line, the lock button and the menu), a search field inside the band that opens the search screen, the subgroups as a two-column grid of cards with a hexagon badge and the entry count, the entries in a single card with separators, a hexagon avatar, an expiry word and a "2FA" tag when the entry has a one-time code, and a floating "New entry" button. Behaviour is unchanged.

## [0.27.0] - 2026-10-16

### Changed

- Redesigned the unlock screen with the chosen visual direction A: the KeyNexa brand mark and name on top, and the vault card (with its hexagon avatar, its name and the "Change" action), the master password field with show/hide, the key file picker, the "Unlock" button and the "Use fingerprint" secondary button in the lower half. Behaviour is unchanged. New shared pieces: `HexagonShape`/`HexBadge`, `BrandMark` and `LocalDarkTheme`.

## [0.26.0] - 2026-10-16

### Added

- Visual direction "A, light and sober", chosen by the maintainer, with its dark version; the mockups are in `design/mockups/`.
- The Manrope font (SIL OFL 1.1) as the interface font, bundled as one variable font, with its license in `third_party/manrope/` and in the credits.

## [0.25.2] - 2026-10-16

### Added

- Differential test against keepass-rs (ADR-0005): a small Rust tool in `tools/kdbx-dump-rs` (`keepass = "=0.15.2"`) dumps a vault as canonical JSON, and `KeepassRsDifferentialTest` compares, for every corpus vault, what keepass-rs sees on the original file and on the file KeyNexa rewrote, and the KeyNexa model itself. The test is skipped without `KEEPASS_RS_DUMP` and runs in a new `differential` CI job with Rust 1.99.0; a known-differences list fails if an unlisted difference appears or a listed one stops happening (it is empty: the libraries agree).

## [0.25.1] - 2026-10-16

### Changed

- Looking for outside changes is cheaper: `VaultReader` compares the file's size and modification time (recorded just before the bytes were read) and only reads and hashes the whole file when they differ, on any doubt or on a forced check at least once a minute. The safe-save pipeline still checks the full fingerprint just before writing, so no change is ever missed.

## [0.25.0] - 2026-10-16

### Added

- The entry detail screen now shows the one-time (TOTP) code of an entry that has a valid configuration: a "One-time code" row, hidden by default and revealed with the usual Show/Hide, a sensitive Copy, and a progress bar with the seconds it still has (in the error colour when five seconds or fewer remain). The code refreshes every second without recomposing the whole screen, and it is never written to a description, a log or a toast.

## [0.24.4] - 2026-10-16

### Added

- The TOTP core in `core-vault` (still without an interface): `Entry.totp()` reads the KeePassXC `otp` field (an `otpauth://totp/...` URI) or the KeeTrayTOTP `TOTP Seed`/`TOTP Settings` fields, and `TotpConfig.codeAt(instant)` derives the 6-8 digit code per RFC 6238 (SHA1/SHA256/SHA512, HMAC from `javax.crypto` and RFC 4226 dynamic truncation, with a minimal RFC 4648 Base32 decoder). Invalid configurations answer null and never throw, and no secret or code is ever printed.

## [0.24.3] - 2026-10-16

### Changed

- TODO: automatic updates noted as an idea under evaluation.

## [0.24.2] - 2026-10-16

### Added

- Coverage-guided fuzzing of the KDBX decoder with the Jazzer CLI (0.30.0), without adding a Gradle dependency: a `DecoderFuzzTarget` entry point and a JUnit test that runs it on the fast corpus vaults inside `check`, a `writeFuzzClasspath` Gradle task, and a weekly/manual `fuzz` CI workflow that verifies the Jazzer download's SHA-256 before running it. Jazzer's license is recorded in the dependency, credits and development documents.

## [0.24.1] - 2026-10-16

### Fixed

- Opening or saving a vault whose Argon2 key derivation asks for more memory than the device can give no longer crashes the app with an `OutOfMemoryError`: the KDBX engine turns it into `VaultException.NotEnoughMemory`, and the unlock screen explains it and suggests lowering the vault's Argon2 memory in another program.

## [0.24.0] - 2026-10-16

### Added

- A theme setting (Settings → Appearance) with three choices: Automatic (follow the system, the default), Light and Dark. The choice is saved with the other settings, applied at once (the activity is recreated, without locking the vault) and kept after a restart; an unknown stored value falls back to Automatic.

## [0.23.6] - 2026-10-16

### Changed

- The S3 assistant fills the region field in as the endpoint is typed: when the endpoint changes, the region shows what it can be deduced from (Backblaze, Amazon, Cloudflare R2) unless the user typed it themselves, in which case it is never overwritten. The decision lives in the pure `regionAfterEndpointChange` in `platform-cloud`.

## [0.23.5] - 2026-10-15

### Changed

- Build tools: Gradle 8.14.6 and Android Gradle plugin 8.13.2 (patch updates inside the current lines), with the dependency verification metadata regenerated and reviewed.

## [0.23.4] - 2026-10-15

### Added

- ADR-0009 (proposed): synchronisation through the cloud, local-first, with conditional writes and a per-service test before it is offered.

## [0.23.3] - 2026-10-15

### Added

- Amazon S3 section in the cloud setup guide (not tried with a real account yet).

## [0.23.2] - 2026-10-15

### Changed

- The root group screen now shows the vault name (the one in the vault list) as its title, instead of the file's root group name ("Root" by default in KeePassXC). Subgroups still show their own name, and nothing inside the KDBX changes.

## [0.23.1] - 2026-10-15

### Changed

- TODO: the IzzyOnDroid request was rejected for not following its template; parked.

## [0.23.0] - 2026-10-15

### Added

- The S3 assistant deduces the region from the endpoint for Backblaze B2, Amazon S3 and Cloudflare R2 (`regionFromEndpoint` in `platform-cloud`), so the region field is optional for them and only an error when it cannot be deduced. The help text says so.
- After "Copy now", the state screen shows a short result (the copy was uploaded, or the error) and keeps it until the screen is left.

### Changed

- "Disconnect and delete" also deletes the backup state (`FileBackupState.clearAll()` and `CloudBackupService.reset()`), so a new connection does not show the previous "last copy".
- The cloud setup guide explains that the region is filled in automatically for Backblaze, Amazon and Cloudflare, and the TODO no longer lists these finished items.

## [0.22.1] - 2026-10-15

### Changed

- Cloud setup guide verified with a real Backblaze B2 account: sign-up, bucket, endpoint names, key, first copy and the Downloads folder limit.

## [0.22.0] - 2026-10-15

### Added

- A "Settings" item in the menu of an open vault. Settings could only be opened from the vault list, and going back from a vault locks it, so "Copy now" (cloud variant) could never be enabled.

## [0.21.4] - 2026-10-15

### Changed

- Cloud variant: when Settings is opened with no vault open, the disabled "Copy now" button now says why and what to do.

## [0.21.3] - 2026-10-15

### Fixed

- Cloud variant: "Save and turn on" closed the app on a real phone, because the Android Keystore refuses an initialisation vector chosen by the caller. The cipher now generates it.

## [0.21.2] - 2026-10-15

### Fixed

- Cloud variant: "Test connection" always said it could not connect on a real phone, because Android's XML parser rejects the Apache hardening options used to read S3 answers. The options are now set one by one, and an answer with a DOCTYPE or entities is refused before parsing.

## [0.21.1] - 2026-10-15

### Changed

- Polished the cloud-copy assistant (cloud variant only): the "Test connection" result now appears right above the button, so it is visible without scrolling; the "Copies in the cloud" settings row looks like a tappable row with a trailing arrow and a second line showing the current state; the S3 description no longer says "without a KeyNexa account" and instead says that KeyNexa has no accounts or servers of its own; and the consent screen groups its sentences under the bold headings "What is sent", "Where it goes", "What it is for" and "How to stop using it".

## [0.21.0] - 2026-10-15

### Added

- The cloud-copy assistant (S3 path) in the `cloud` variant (ADR-0008 §4): a step-by-step screen with consent, the service choice, what each provider needs (with links that open the browser), the endpoint/region/bucket/keys form (secret key as a password field, never in saved state), a connection test with clear per-case messages, saving the configuration and a state screen with the last copy, the last error, the copy count (5/10/20), "Copy now", "Change service" and "Disconnect and delete". A discreet, never-modal reminder appears on the home screen until a service is configured and a copy exists, and can be postponed for a week. New bilingual [cloud setup guide](CLOUD_SETUP.md).

### Changed

- `CloudSettingsEntry` now receives the open vault (`CloudBackupSource`) so "Copy now" can upload it; the offline variant keeps empty hooks with the same signature.

## [0.20.0] - 2026-10-15

### Added

- The engine of the cloud copies in the `cloud` variant (ADR-0008), still without a setup screen: `S3Setup` (storable configuration with a redacted `toString`), `FileBackupState` (last success and error per vault, keyed by the SHA-256 of its location, atomic and tolerant of a corrupt file), `testConnection` (lists, uploads a test object and deletes it) and `CloudBackupRunner` in `platform-cloud`; a `VaultSession.afterSave` hook that runs once after every successful save and never lets the hook break the save; and `CloudBackupService` in the cloud flavor, a one-thread queue that uploads the encrypted file after a save (`backupIfDue`), can be forced, coalesces per vault and exposes a `StateFlow` status. The `offline` variant keeps empty hooks with the same signature.

## [0.19.0] - 2026-10-15

### Added

- An encrypted store for the cloud credentials and the consent screen, only in the `cloud` variant (ADR-0008): `SecretBox`/`AesGcmSecretBox` and `CloudCredentialStore` in `platform-cloud` (AES-256-GCM, atomic writes, nothing readable on disk), a `KeystoreSecretBox` whose non-exportable key lives in the Android Keystore, a consent mark in `noBackupFilesDir`, and a "Copies in the cloud" entry in Settings with the consent flow and a "Disconnect and delete" action. The `offline` variant keeps an empty entry and carries no cloud code. New [privacy statement](docs/PRIVACY.md) and cloud threats in the [threat model](docs/THREAT_MODEL.md).

## [0.18.0] - 2026-10-15

### Added

- An S3-compatible cloud client in the new `platform-cloud` module, still unused from the app (ADR-0008): `HttpTransport` (with a `UrlConnectionTransport` over the JDK's `HttpsURLConnection`), `S3Signer` (AWS Signature Version 4 by the `Authorization` header, pure and deterministic, checked against the four official AWS test vectors) and `S3Backend` (put, get, delete and paginated `ListObjectsV2` over a configurable endpoint, path-style or virtual-hosted). It adds no dependency, never logs or puts URLs, headers, keys or bodies in messages, and only allows HTTPS outside tests.

## [0.17.0] - 2026-10-15

### Added

- The internal base of the cloud copies (ADR-0008 §3), still without network code or user interface: `CloudBackend` in `core-sync` (put, get, list and delete of opaque encrypted backups, with a `conditionalWrites` capability and a `CloudAuthException` for credentials failures) and `BackupUploader`, which names a copy `<prefix><vault>/<vault>-<UTC>.kdbx`, uploads at most once a day (`backupNow` ignores the limit), rotates only the copies it created down to the chosen number, and reports a failure as a result value so it can never stop a save.

## [0.16.0] - 2026-10-15

### Added

- Two build flavors, `offline` and `cloud`, under the `distribution` dimension. `offline` is the default and behaves as before: its merged manifest is checked to carry no `INTERNET` permission, even if a library adds one. `cloud` declares `INTERNET` (for the cloud copies still to come) and shows "KeyNexa (cloud)" as its app name; nothing else changes yet. Both share `applicationId`, `versionCode`, `versionName` and signing.

### Changed

- APK paths now carry the flavor: `app-android/build/outputs/apk/<flavor>/<type>/app-android-<flavor>-<type>.apk`. The release workflow builds and checks both flavors but publishes only the `offline` one.
- Dependency locking covers the flavor runtime configurations (`offlineReleaseRuntimeClasspath`, `cloudReleaseRuntimeClasspath`), so the lock files were regenerated.

## [0.15.0] - 2026-10-14

### Added

- Clear an entry's previous versions with `VaultEditor.clearHistory`, which empties the entry's history (without creating a new version or changing its modification time) and garbage-collects attachment binaries only the history referenced. The entry detail screen offers "Clear previous versions" next to "Previous versions: N" with a confirmation dialog; removing an attachment leaves its content in the history until this action is used.

## [0.14.0] - 2026-10-14

### Added

- Add, remove and save to a file the attachments of an entry: `VaultEditor.addAttachment`/`removeAttachment` run through `editEntry` (so a version is kept), replacing an attachment with the same name, garbage-collecting binaries that nothing in the tree or its history references, and rejecting attachments above 10 MB or with a blank name. The entry detail screen shows each attachment's name and readable size with a "Save to…" action that writes the binary to a chosen file (with a clear notice that the saved file is not encrypted), and the edit screen lets you add files (`*/*`) and remove attachments, applied together with the other fields on Save.

## [0.13.1] - 2026-10-14

### Fixed

- The app no longer closes when the vault file disappears or becomes unreachable while the vault is open: the file's modification time (0.12.0) was read on the screen thread and its failure was not caught; it is now read in the background.
- The "Which vault?" list no longer offers Syncthing conflict copies as if they were vaults.

## [0.13.0] - 2026-10-14

### Added

- Reconnect the folder without closing the open vault when a save fails because the grant was lost, and retry the pending save through the normal pipeline (it does not write when the chosen file is another vault).
- Restore unsaved changes from a previous interrupted save after a restart: the notice offers to merge the private copy into the open vault or to discard it, deleting the copy only once the save succeeds.

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
