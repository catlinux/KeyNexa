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

Template for new records: `docs/decisions/NNNN-short-title.md` and its translation `docs/es/decisions/NNNN-short-title.md`, with the sections *Context*, *Options*, *Decision*, *Consequences*.
