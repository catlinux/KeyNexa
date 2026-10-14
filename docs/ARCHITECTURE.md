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

Implemented in `core-sync` (`SafeSaver`, `VerifiedWriter`, `SaveRecovery`, `VaultReader`).

1. Serialize the vault to a new encrypted byte array in memory and decrypt it once, to prove the new bytes are good.
2. Re-read the current file and compare its fingerprint with the one loaded. If it changed, decrypt it and **merge** it into the in-memory vault, then serialize again (a few rounds at most). Without a merge, nothing is written.
3. Keep two encrypted copies in app-private storage, excluded from Android backup: the previous file (`BeforeSave`, rotated: the newest 10 per vault) and the new bytes (`UnsavedChanges`, kept until they are saved). Record the save in a journal.
4. Check the fingerprint once more, then write with mode `"rwt"`; if the provider does not support it, fall back to `"rw"` and truncate explicitly.
5. Read the file back. Success only if its fingerprint equals that of the new bytes: identical bytes need no second decryption, so a save costs a single key derivation.
6. Otherwise decide what the file holds: unchanged (nothing was written), replaced by another program after our write (left as it is), or torn by our own write. A torn file is restored byte for byte from the `BeforeSave` copy. In every failure the user's changes remain in memory and in the `UnsavedChanges` copy.

**What "never corrupts" means here.** A storage provider cannot replace a file atomically, so a crash in the middle of a write can leave it torn; nothing can prevent that. The guarantee is that a decryptable copy always exists and is put back automatically: on the next start, `SaveRecovery` finds the journal entry, compares the file with the two copies and restores the previous version if the file was torn by our write. Telling a torn file from one written by another program needs no password: every save writes a fresh random master seed into the header, so only a file KeyNexa started writing shares a long prefix with its new bytes.

**Tolerant reading.** A file that fails its integrity checks may be a sync transfer in progress, so `VaultReader` reads it again after 1, 2, 4 and 8 seconds before reporting it as damaged. In KDBX 4 a wrong-key error is reliable and is reported at once (the password may have changed on another device); in KDBX 3.x, where a damaged file and a wrong key look the same, it is retried when the same credentials already opened the vault.

### Merge

KeyNexa's merge follows the semantics of KeePass 2.x synchronization as also implemented by KeePassXC, re-implemented from the documented behaviour, not copied:

- Objects are matched by UUID.
- For an entry modified on both sides, the version with the newer last-modification time becomes current and the other is kept in its history.
- Histories are merged without deleting items, then trimmed to the vault's history limits.
- Moves follow the newer location-changed time.
- Deletions apply only through the vault's deleted-objects list, and only if the object was not modified after the deletion.

Implemented in `core-sync` as `KeePassMerger`, with these further rules: ties favour the user's in-memory version; an object whose new parent group did not survive goes to the nearest surviving ancestor; crossed moves that would form a loop attach the group to the root, so nothing is dropped; metadata merges field by field by its own `*Changed` times; attachments nobody references are dropped; a deletion record for the root group is ignored. The editing code must update an object's last-modification time (and location-changed time when moving it), because the merge decides by those times. Tested with scenarios and with randomly generated pairs of vaults checked against invariants (nothing lost, no loops, no dangling attachments, same result whichever side is "ours").

If the files cannot be merged (for example, different master keys), KeyNexa does not write: it keeps both versions and asks the user.

### Syncthing conflict copies

When two devices change the vault while they cannot see each other, Syncthing keeps one version as the vault file and renames the other to `<name>.sync-conflict-<date>-<time>-<device>.kdbx`. KeyNexa folds those copies into the vault by itself, so their changes are not forgotten until somebody opens the copy by hand ([ADR-0007](decisions/0007-conflict-copies.md)):

- The copies are found among the siblings of the vault file, using the file's real name in storage (not the name shown in the app).
- A per-vault ledger in app-private storage, excluded from backup, holds the SHA-256 fingerprints of the copies already merged, so a known copy is neither decrypted again nor merged twice.
- Each remaining copy is decoded with the open vault's credentials and folded in with `KeePassMerger`; the result goes through the normal save pipeline, and the copies are recorded in the ledger only once that save reports success (a save that fails leaves them out, so the next open tries again).
- Copies that cannot be merged are reported and left alone: written with another password, damaged or still in transfer, belonging to another vault, or unreadable.
- **Nothing is deleted without consent.** After a merge the app says how many copies were merged and offers to delete them, and it deletes only copies in the ledger whose bytes still match the recorded fingerprint, read again just before deleting. Declining keeps them, and the offer comes back on the next open.

It lives in `core-sync` as `ConflictMerger` (with the `MergedCopies` port for the ledger); the Android app wires it in `VaultSession`, stores the ledger with `FileMergedCopies` and shows the notice in the group screen. As with the rest of the vault, only fingerprints and reasons are kept: never content or secrets.

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
