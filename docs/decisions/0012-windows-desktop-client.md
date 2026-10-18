# ADR-0012: Windows desktop client

[English](0012-windows-desktop-client.md) | [Español](../es/decisions/0012-windows-desktop-client.md)

- **Status:** Accepted
- **Date:** 2026-10-18
- **Extends:** [ADR-0003](0003-platform-and-modules.md), [ADR-0011](0011-repository-layout.md)

## Context

The owner wants a Windows client with the same functions and the same look as Android 1.0: vaults kept only on the computer or also copied to the cloud (Backblaze B2, Cloudflare R2), and synchronisation with other devices without Syncthing-Fork (ADR-0013). Everything must come in one package, with each mode switched on separately.

## Options

1. **Compose Multiplatform for Desktop on the JVM**, as planned in ADR-0003: the core, the cloud client and the vault session run unchanged and the Material 3 theme is shared with Android.
2. **A native Windows UI (WinUI, WPF) over the JVM core**: a second language and a bridge between processes, for no gain in security.
3. **Web technologies (Electron, Tauri)**: a browser engine in a password manager and the core in another runtime.

## Decision

1. **Option 1.** Kotlin, Compose Multiplatform for Desktop, Material 3 with the theme of `ui/shared`. Screens are made for a wide window (groups, entries and detail side by side, keyboard shortcuts) in the same visual direction as Android; the mock-up is approved by the owner before they are built.
2. **Supported systems: Windows 10 22H2 and Windows 11, x64.** Windows 7 and 8.1 are not supported: they no longer receive security updates and current JDKs do not support them. Windows 10 stays supported while the bundled runtime and Compose run on it (checked on a real Windows 10 before each release). Windows on ARM64 is considered later.
3. **Packaging:** the JDK runtime is bundled (jpackage), so nothing has to be installed first. Two forms: an MSI installer **per user** (no administrator rights) and a portable ZIP. No automatic updates in the first version. Code signing is decided before the first stable desktop release; until then Windows SmartScreen will warn on install, and the SHA-256 of every file is published.
4. **One package, three modes**, chosen in the settings and combinable: **local only** (default; the app opens no socket and no connection), **cloud copies** (ADR-0008, the same S3 client and consent text as Android) and **synchronisation with other devices** (ADR-0013). Unlike Android there is no separate offline build: switching a mode on is the user's choice and is explained on screen.
5. **Storage:** vaults are plain files on any local or network drive. The safe-save pipeline of `core/sync` is kept; on top of it, the desktop writes the new **encrypted** bytes to a temporary file in the same folder and replaces the vault with an atomic move, falling back to the in-place write when the file system refuses it. Private data (backups, journal, merged-copies ledger, settings) goes to `%LOCALAPPDATA%\KeyNexa`, never to a roaming profile.
6. **Secrets:** cloud credentials and the device key of ADR-0013 are encrypted with DPAPI for the current user, called through JNA. There is no quick unlock in the first version; Windows Hello will get its own record.
7. **Protections equivalent to Android's:** the window is excluded from screen capture (`SetWindowDisplayAffinity` with `WDA_EXCLUDEFROMCAPTURE`) unless the user allows screenshots; copied secrets are marked so that Windows keeps them out of the clipboard history and the cloud clipboard, and are cleared after the configured time, on lock and on exit; the vault locks after inactivity, when the Windows session is locked and on suspend; a single instance runs at a time; nothing about the vault is logged.
8. **KeyNexa keypad (ADR-0010):** available for master passwords but **off by default** on the desktop, where a physical keyboard is normal and an on-screen keypad only helps against keyloggers.
9. **Linux** follows in the same `apps/desktop` module, with its own packages and the Secret Service in `platform/desktop`, under its own record.

## Consequences

- The installed size includes a Java runtime (tens of megabytes), the price of needing nothing preinstalled.
- New dependencies (Compose Multiplatform, Skiko, JNA, the bundled runtime) are recorded with their licenses before they are added.
- The threat model gains the desktop: other programs running as the same user can read the process memory and the files of `%LOCALAPPDATA%`, and DPAPI does not protect against them; another Windows user cannot read them.
- Without code signing, users must trust the published checksums; that is a barrier to adoption until signing is decided.
