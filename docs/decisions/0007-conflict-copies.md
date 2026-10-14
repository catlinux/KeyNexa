# ADR-0007: Merging Syncthing conflict copies

[English](0007-conflict-copies.md) | [Español](../es/decisions/0007-conflict-copies.md)

- **Status:** Accepted
- **Date:** 2026-10-14
- **Implements:** phase D7 of the roadmap; extends [ADR-0004](0004-storage-sync-and-safe-save.md)

## Context

When two devices change the vault while they cannot see each other, Syncthing keeps one version as the vault file and renames the other to `<name>.sync-conflict-<date>-<time>-<device>.kdbx`. Nothing merges it afterwards: the changes in the copy are invisible until somebody opens it by hand, and with time they are forgotten. The safe-save pipeline already merges concurrent changes with the KeePass semantics (`KeePassMerger`), and entries have random UUIDs, so independent additions never collide.

Constraints: opening a copy costs a full key derivation (seconds with Argon2 on a phone); a copy may have been written with another password (changed on one device only), be damaged, still be in transfer, or belong to another vault; nothing may be deleted without the user's consent.

## Options

1. **Show the copies and let the user merge each one.** Safe but easy to ignore, which is the current problem.
2. **Merge automatically on open, ask before deleting.** The merge only adds information (or applies deletions the other device recorded), previous versions stay in the private backups, and the copies stay on disk until the user agrees.
3. **Merge and delete automatically.** Least friction, but a wrong merge would leave no trace in the synchronised folder.

## Decision

Option 2.

1. **When:** after a vault is opened and after an outside change is loaded, while the open vault has nothing unsaved and no save is running. Copies are found with `conflictCopiesOf` among the siblings of the vault file, using the file's real name from the storage (not the name shown in the app).
2. **What is skipped:** a copy whose fingerprint (SHA-256 of its bytes) is in the per-vault ledger of merged copies. The ledger lives in private storage excluded from backup and holds fingerprints only; it avoids paying a key derivation on every open.
3. **How:** each remaining copy is decoded with the open vault's credentials and folded into the current vault with `KeePassMerger` (ties favour the open vault). The result goes through the normal save pipeline. Copies whose content adds nothing are recorded as merged without saving.
4. **Recorded only after success:** the fingerprints are added to the ledger only once the save reports `Saved` (or when nothing needed saving). A failed save leaves them out, so the next open tries again.
5. **Copies that cannot be merged** are reported and left alone, never deleted: another password (`WrongCredentials`; the notice offers to type that password, and the copy is merged when the save succeeds), damaged or still in transfer (`Corrupted`, tried again next time), another vault (`Unmergeable`), or unreadable.
6. **Deletion only with consent:** after a merge the app says how many copies were merged and offers to delete them. It deletes only copies in the ledger whose bytes still have the recorded fingerprint, read again just before deleting; anything else is kept. Declining keeps the copies, and the offer comes back on the next open.
7. **Nothing secret leaves memory:** the copies are decoded in memory like the vault; only fingerprints are stored.

## Consequences

- Two devices that edit without seeing each other end with the union of their changes after one of them opens the vault, without the user doing anything except confirming the cleanup.
- Each new copy costs one key derivation when it is first found; known copies cost only a read and a hash.
- A copy written with another password is not merged automatically: the app asks for that password, uses it once and wipes it, and merges the copy when the save succeeds.
- `StoragePort` gains `name(location)` and `delete(location)`; the Android implementation uses the document's display name and `DocumentsContract.deleteDocument`.
