# ADR-0004: Storage, Syncthing and safe save

[English](0004-storage-sync-and-safe-save.md) | [Español](0004-storage-sync-and-safe-save.es.md)

- **Status:** Accepted
- **Date:** 2026-10-13

## Context

Users will keep vaults in folders synchronized by Syncthing. Findings (sources in parentheses):

- The official Syncthing Android app was discontinued in 2024 and its repository is archived; the maintained fork is Syncthing-Fork, distributed through F-Droid and GitHub ([forum announcement](https://forum.syncthing.net/t/discontinuing-syncthing-android/23002)). KeyNexa cannot assume any Syncthing app is installed.
- Syncthing writes to a temporary file and then replaces the target; conflicts produce `<name>.sync-conflict-<date>-<time>-<device>.<ext>` files ([docs](https://docs.syncthing.net/users/syncing.html)).
- Android security patches from September 2026 revoke an app's URI permission on a file when that file is deleted or renamed, which happens when Syncthing replaces it ([KeePassDX #2573](https://github.com/Kunzisoft/KeePassDX/issues/2573), [Syncthing #10887](https://github.com/syncthing/syncthing/issues/10887)). A folder (tree) grant reportedly survives.
- With SAF, the write mode `"w"` may not truncate on some providers, which corrupts a shorter file; `"wt"`/`"rwt"` are needed ([AOSP javadoc change](https://android.googlesource.com/platform/frameworks/base/+/0ebf92c7d31e%5E%21/)).
- There are no reliable change notifications for files on shared storage; `FileObserver` is not dependable there and Syncthing's REST API needs an API key.
- `MANAGE_EXTERNAL_STORAGE` is not allowed on Google Play for apps where the user picks files.

## Decision

1. **SAF only**, no broad storage permission. Prefer a **folder grant** (`ACTION_OPEN_DOCUMENT_TREE`) and locate the vault by name; single-file grants are supported with a "reconnect file" flow when access is revoked.
2. **Change detection by content fingerprint** (SHA-256 of the encrypted file) when unlocking, when returning to the foreground, periodically while visible, and always immediately before saving. No dependency on Syncthing's API.
3. **Tolerant reading:** a file that fails header/HMAC checks may be a partial transfer; retry with backoff and never write over it.
4. **Safe-save pipeline** (private backup → fingerprint re-check and merge → `"rwt"` write → read-back verification → restore on failure), as described in [ARCHITECTURE](../ARCHITECTURE.md).
5. **Automatic merge** by UUID and modification times, with history preserved; unmergeable cases never overwrite.
6. **Conflict files** (`*.sync-conflict-*`) found next to the vault are offered for merge and never deleted automatically.
7. **Private rotating backups** of the encrypted vault in app storage, excluded from Android backup.

## Consequences

- KeyNexa does not claim real-time or automatic synchronization; it detects changes made by any sync tool.
- The behaviour must be validated on real devices with Syncthing-Fork before it is advertised (acceptance criterion AC4 in the [roadmap](../ROADMAP.md)).
- Folder grants need a slightly longer first-run flow; the UI must explain why.
