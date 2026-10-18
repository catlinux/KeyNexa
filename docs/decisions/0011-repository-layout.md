# ADR-0011: Repository layout for several clients and a single product version

[English](0011-repository-layout.md) | [Español](../es/decisions/0011-repository-layout.md)

- **Status:** Accepted
- **Date:** 2026-10-18
- **Extends:** [ADR-0003](0003-platform-and-modules.md)

## Context

The Android app reached 1.0.0 with all modules at the root of the repository (`core-vault`, `core-kdbx`, `core-sync`, `platform-android`, `platform-cloud`, `app-android`). A Windows client comes next and a Linux client after it, both on the JVM (ADR-0003). Keeping every module at the root mixes the clients, and the code the desktop needs from the Android app (the vault session, the private file stores, the theme) lives in Android modules today.

## Options

1. **Separate repositories per client**, with the core published as a library. Every core change needs a release and a version bump in each client; the CI, the docs and the license records are duplicated.
2. **One repository, one Gradle build, modules at the root** (as today). Simple, but the list grows mixed and nothing shows which module belongs to which client.
3. **One repository, one Gradle build, modules grouped by role (this record).** The usual layout of large multi-module Gradle projects: the core changes for every client in one commit, one CI, one place for docs and licenses.
4. **Directories per operating system** (`windows/`, `linux/`). Rejected: Windows and Linux run the same JVM application; splitting them would duplicate the user interface.

## Decision

1. Option 3, with this layout and Gradle project paths that follow the directories:

   ```text
   core/vault       :core:vault       domain model and ports
   core/kdbx        :core:kdbx        the only module that sees kotpass
   core/sync        :core:sync        safe save, merge, conflict copies, cloud reconcile
   core/session     :core:session     vault session (open, edit, save, outside changes), moved out of the Android app
   core/peer        :core:peer        device-to-device synchronisation (ADR-0013)
   platform/jvm     :platform:jvm     private file stores shared by every JVM client (backups, journal, merged-copies ledger)
   platform/android :platform:android SAF, Keystore
   platform/cloud   :platform:cloud   S3-compatible client
   platform/desktop :platform:desktop plain-file storage and the secret-store interface, shared by every desktop system
   platform/windows :platform:windows DPAPI, capture exclusion, Windows data folder (created with the Windows client)
   platform/linux   (reserved)        Secret Service, XDG folders (created only when the Linux client starts)
   ui/shared        :ui:shared        Compose Multiplatform theme, mark, keypad and shared components (Android + desktop)
   apps/android     :apps:android     the Android app
   apps/desktop     :apps:desktop     the desktop app (one code base; packaging per system in apps/desktop/packaging/<system>)
   third_party/kotpass :kotpass       vendored engine (unchanged)
   ```

2. Everything that is not a module stays at the root: `README`, `LICENSE`, `NOTICE`, `CHANGELOG.md`, `TODO.md`, `VERSION`, `docs/`, `testdata/`, `scripts/`, `config/`, `gradle/` and `fastlane/` (F-Droid looks for it there).
3. Modules move with `git mv`, so their history is kept. The move changes no behaviour of the Android app; the application id, signing and APK names stay the same.
4. **A single product version.** One `VERSION` file and one `CHANGELOG.md` for every client. A release tag `vX.Y.Z` publishes every client built from that commit (the two APKs and the Windows packages, each with its SHA-256). While the desktop client is behind the Android app, its files carry `preview` in their names and the release notes say so; the release itself keeps the status of the Android app.
5. Operating-system differences go into one module per system (`platform/windows` now, `platform/linux` later) behind the interfaces of `platform/desktop`, and into the packaging folder of that system, never into a copy of the app. Adding Linux then means adding `platform/linux`, its packaging and a CI job; no existing directory moves and the Android app is not touched. `platform/linux` is not created until the Windows client is finished and the owner authorises it.
6. Apple systems (macOS, iOS) are not planned: the project has no Apple hardware to test on, and a password manager cannot be released on emulators alone.

## Consequences

- Core changes reach every client in one commit, tested together by one CI.
- The move touches the build scripts, the workflows (APK and report paths), the lock and verification files and every path in the docs; it is done in one step of its own and checked by building both APKs and unlocking on the phone.
- A desktop-only fix also bumps the version shared with Android, so an Android release can carry no Android change. The changelog says which client each entry is for.
- The F-Droid build recipe, not yet filed, must point to `apps/android`.
