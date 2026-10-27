# Architecture decision records

[English](README.md) | [Español](../es/decisions/README.md)

Each record explains one important decision: context, options, decision and consequences. A record is never rewritten once accepted; a new record supersedes it.

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-license.md) | License: GPL-3.0-only with section 7 attribution terms | Accepted |
| [0002](0002-kdbx-engine.md) | KDBX engine: kotpass behind an adapter | Accepted |
| [0003](0003-platform-and-modules.md) | Platform: Kotlin/JVM core, Compose UI, modular layout | Accepted |
| [0004](0004-storage-sync-and-safe-save.md) | Storage, Syncthing and safe save | Accepted |
| [0005](0005-reinforced-kotpass.md) | Reinforced kotpass: vendored module, differential tests, fuzzing | Accepted |
| [0006](0006-biometric-unlock.md) | Biometric unlock: Keystore-wrapped composite key, per-use biometrics | Accepted |
| [0007](0007-conflict-copies.md) | Merging Syncthing conflict copies: automatic merge, deletion only with consent | Accepted |
| [0008](0008-cloud-backup.md) | Cloud copies of the vault: optional wizard, `cloud` build variant, providers | Accepted |
| [0009](0009-cloud-sync.md) | Synchronisation through the cloud: local-first, conditional writes | Accepted |
| [0010](0010-in-app-password-keypad.md) | In-app keypad for the master password: on by default, beside the system keyboard | Accepted |
| [0011](0011-repository-layout.md) | Repository layout for several clients and a single product version | Accepted |
| [0012](0012-windows-desktop-client.md) | Windows desktop client | Accepted |
| [0013](0013-device-sync.md) | Synchronisation between devices without Syncthing | Accepted |
| [0014](0014-windows-hello-quick-unlock.md) | Quick unlock with Windows Hello on the desktop: secret only in memory, documented Hello key | Accepted |
| [0015](0015-automatic-updates.md) | Automatic updates: opt-in, signed manifest on Windows, platform signature on Android, none in `offline` | Accepted |

Template for new records: `docs/decisions/NNNN-short-title.md` and its translation `docs/es/decisions/NNNN-short-title.md`, with the sections *Context*, *Options*, *Decision*, *Consequences*.
