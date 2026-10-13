# Architecture

[English](ARCHITECTURE.md) | [Español](es/ARCHITECTURE.md)

> Status: **proposed** (v0.1.0). No code exists yet. Decisions are recorded in [decisions/](decisions/README.md).

## Goals and constraints

1. Security and data integrity first; never lose a user's changes silently.
2. Full KDBX interoperability with other KeePass clients.
3. No custom cryptography: only established, tested implementations.
4. Android first; Windows and Linux later without rewriting the core.
5. Small, explicit, testable security-sensitive code.
6. Every dependency compatible with GPL-3.0 and justified (see [Dependencies and licenses](DEPENDENCIES_AND_LICENSES.md)).

## Technology choices

| Area | Choice | Why | Decision |
| --- | --- | --- | --- |
| Language | Kotlin (JVM target) | Native Android language; the same code runs on desktop JVM | [ADR-0003](decisions/0003-platform-and-modules.md) |
| KDBX engine | [kotpass](https://github.com/keemobile/kotpass) (MIT), vendored and reinforced, wrapped behind our own interface | Only researched JVM library covering KDBX 3.1/4.x with AES, ChaCha20, Twofish, AES-KDF, Argon2d and Argon2id; one runtime dependency (okio) | [ADR-0002](decisions/0002-kdbx-engine.md), [ADR-0005](decisions/0005-reinforced-kotpass.md) |
| Android UI | Jetpack Compose + Material 3 (own theme) | Current Android standard; declarative and testable | [ADR-0003](decisions/0003-platform-and-modules.md) |
| Desktop UI (future) | Compose Multiplatform for Desktop (JVM) | Reuses the JVM core modules unchanged | [ADR-0003](decisions/0003-platform-and-modules.md) |
| File access (Android) | Storage Access Framework (SAF), no "All files access" permission | Least privilege; Google Play policy | [ADR-0004](decisions/0004-storage-sync-and-safe-save.md) |
| Biometric unlock | `androidx.biometric` + Android Keystore | Platform-backed key protection | [THREAT_MODEL](THREAT_MODEL.md) |
| Build | Gradle (Kotlin DSL) with version catalog | Standard; reproducible dependency declarations | [DEVELOPMENT](DEVELOPMENT.md) |

## Module layout (planned)

Dependencies point downwards only. Core modules are pure Kotlin/JVM, with no Android imports, so they can be unit-tested on the JVM and reused by a desktop client.

```text
app-android          Compose UI, navigation, Android services (autofill, biometric, clipboard)
  │
platform-android     SAF storage adapter, Keystore, lifecycle-based auto-lock
  │
core-sync            change detection, merge, safe-save pipeline (uses StoragePort)
  │
core-vault           domain model, use cases, search, StoragePort / KdbxEngine interfaces
  │
core-kdbx            adapter implementing KdbxEngine on top of kotpass (the only module that sees kotpass)
```

- **`core-vault`** defines the domain (vault, group, entry, field, attachment, history) and the ports: `KdbxEngine` (decode/encode a KDBX byte stream with credentials) and `StoragePort` (read, write, metadata, list siblings of a vault file).
- **`core-kdbx`** is the only module that depends on kotpass. Replacing or forking the engine affects only this module.
- **`core-sync`** contains the logic that protects data: content fingerprints, external-change detection, the merge and the safe-save pipeline. It is deliberately independent of Android so it can be tested exhaustively with fake storage.
- **`platform-android`** implements `StoragePort` with SAF and wraps the Android Keystore.
- **`app-android`** is the UI and the Android services.

A future `app-desktop` would reuse `core-*` and add a `platform-desktop` with a plain-file `StoragePort` and OS-specific secret storage.

## Data flow: opening and saving

1. The user picks a vault with SAF. KeyNexa keeps a persistable URI permission, preferring a **folder** grant (`ACTION_OPEN_DOCUMENT_TREE`) so the vault survives being replaced by Syncthing. See [ADR-0004](decisions/0004-storage-sync-and-safe-save.md).
2. Bytes are read into memory and fingerprinted (SHA-256 of the encrypted file). The encrypted bytes are never written to logs.
3. `KdbxEngine` decrypts with the composite key. A file whose header or HMAC blocks do not verify is rejected (it may be a partial Syncthing transfer): KeyNexa retries after a short delay and never writes over it.
4. On save, the safe-save pipeline (below) runs.

### Safe-save pipeline

1. Serialize the vault to a new encrypted byte array in memory.
2. Re-read the current file and compare its fingerprint with the one loaded. If it changed, decrypt it and **merge** it into the in-memory vault, then serialize again.
3. Keep a copy of the previous encrypted file in app-private storage (rotating backups, excluded from Android backup).
4. Write with mode `"rwt"`; if the provider does not support it, fall back to `"rw"` and truncate explicitly.
5. Read the file back and verify that its fingerprint matches the bytes written and that it decrypts.
6. If any step fails, restore from the private backup when possible and tell the user clearly. Never report success without step 5.

### Merge

KeyNexa's merge follows the semantics of KeePass 2.x synchronization as also implemented by KeePassXC, re-implemented from the documented behaviour, not copied:

- Objects are matched by UUID.
- For an entry modified on both sides, the version with the newer last-modification time becomes current and the other is kept in its history.
- Histories are merged without deleting items, then trimmed to the vault's history limits.
- Moves follow the newer location-changed time.
- Deletions apply only through the vault's deleted-objects list, and only if the object was not modified after the deletion.

If the files cannot be merged (for example, different master keys), KeyNexa does not write: it keeps both versions and asks the user.

## Multiplatform strategy

| Option | Verdict |
| --- | --- |
| **Kotlin/JVM core + Compose (Android) + Compose Multiplatform Desktop** | **Chosen.** One language, one KDBX engine, all-permissive dependency chain verified. |
| Rust core (keepass-rs) + UniFFI bindings | Rejected for now. keepass-rs (MIT) marks KDBX writing as experimental, with open data-loss issues on attachments and history (October 2026); UniFFI adds an NDK toolchain per platform. To be re-evaluated before 1.0 ([ADR-0005](decisions/0005-reinforced-kotpass.md)). |
| Kotlin Multiplatform (KMP) shared core | Not possible today without rewriting the engine: no KMP KDBX library was found; kotpass is JVM-only. Desktop JVM makes it unnecessary. |
| Flutter / React Native | Rejected: weaker access to Android security APIs (Keystore, autofill, credential provider) and no mature KDBX library in those ecosystems with verified licensing. |

Desktop clients will add their own secret storage (Windows DPAPI or Credential Manager, Linux Secret Service). That is a security design of its own and will get a separate ADR.

## Internationalization

- Android string resources (`values/` for `en-US` as default, `values-es/` for Spanish). No user-visible text hard-coded in Kotlin.
- UI must be checked with long strings (Spanish is typically longer) and with the pseudo-locales `en-XA`/`ar-XB`.
