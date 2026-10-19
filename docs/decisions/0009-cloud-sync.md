# ADR-0009: Synchronisation through the cloud: local-first, conditional writes

[English](0009-cloud-sync.md) | [Español](../es/decisions/0009-cloud-sync.md)

- **Status:** Proposed
- **Date:** 2026-10-15
- **Extends:** [ADR-0004](0004-storage-sync-and-safe-save.md) and [ADR-0008](0008-cloud-backup.md), whose decision 9 left this step for its own record

## Context

[ADR-0008](0008-cloud-backup.md) added optional cloud **copies**: write-only snapshots, rotated, never read back by the app. The owner also wants the same service to act as a meeting point, so two devices that are never on at the same time (a phone and a desktop) end up with the same vault without Syncthing.

What the project already has:

- A safe-save pipeline in `core/sync` (private backup, fingerprint re-check, merge by UUID and modification times with history, write, read-back) and a merge engine that never overwrites what it cannot merge.
- A `CloudBackend` interface (put, get, list, delete by name) with a capability flag for conditional writes, and an S3 client that today reports `conditionalWrites = false`.
- Encrypted credentials, a consent screen and a `cloud` build variant.

What a shared remote file needs that a local file does not: two devices can both read version A, both edit, and both upload. Without a "only if unchanged" write the second upload silently destroys the first.

## Options

1. **The cloud as the vault's own `StoragePort`.** The vault location is a remote object; every read and write goes to the network. Simple model, but the vault is unusable offline, every unlock waits for the network, and a failed upload leaves no local copy to fall back on.
2. **Local-first with the cloud as a hub (this record).** The vault stays where it is today (a SAF file). A sync step downloads the remote object, merges it with the local file using the existing merge engine, writes the merged result locally through the existing safe-save pipeline, and uploads it only if the remote object has not changed since it was read. Offline use is unaffected.
3. **A server of our own.** Rejected in [ADR-0008](0008-cloud-backup.md): no servers, no accounts.
4. **Keep Syncthing as the only synchronisation.** Stays available and recommended for people who already use it; it does not serve devices that are never online together.

## Decision

1. **Option 2: local-first.** The local file remains the source of truth for the user. The cloud holds one extra object per vault, `KeyNexa/sync/<vault id>.kdbx`, separate from the rotated copies of ADR-0008 and never rotated or deleted by the app (except by an explicit "stop syncing and delete" action).
2. **One reconcile step**, used by every trigger: read the remote object and its entity tag; if it differs from the last one this device saw, merge it into the local vault with the existing merge engine (a failed merge never overwrites, as today); save locally through the safe-save pipeline; then upload the result with a conditional write against the entity tag that was read. If the service answers "changed meanwhile" (a precondition failure), repeat from the read, a few times at most, then report it quietly and keep the local state.
3. **A service is offered for synchronisation only if its conditional write has been tested** against the real API (an upload with a stale tag must fail and change nothing). For the others, including any S3-compatible service that does not honour it, the cloud stays a backup, as ADR-0008 decided. `CloudCapabilities.conditionalWrites` becomes true per backend only after that test, and the user interface says which services can synchronise.
4. **Triggers, first version:** after a successful local save (like the daily copy of ADR-0008, but without the once-a-day limit), when the app comes to the foreground with an open vault, and a "Sync now" button. No background service and no periodic polling in this version; the battery cost is measured first (see the TODO on cheaper change detection).
5. **First connection of a second device:** the remote object is downloaded and merged into the local vault; if the user has no local vault yet, they choose a folder (SAF) to keep it in. If the two vaults have different master passwords the merge cannot read the other one: the existing flow for conflict copies with another password (F1) is reused, never a silent overwrite.
6. **Deletions** travel as KDBX's own deleted-objects list, as in the merge engine today; the history of entries is kept. Nothing new is invented for them.
7. **The remote object is the same encrypted `.kdbx`**: the cloud sees only ciphertext, exactly as in ADR-0008. The same consent screen, credentials store and "Disconnect" apply; synchronisation is a separate switch inside the cloud assistant, off by default, with its own explanation (what it does, that old versions stay in the entries' history, that deleting the vault on one device deletes it everywhere after the next sync).
8. **Failures never block saving or unlocking**: a failed or refused sync shows a quiet state ("last synchronised…", "could not sync: …") and leaves the local vault exactly as it was.

## Consequences

- Devices that are never on at the same time can converge through any service whose conditional write passes the test; the others keep working as backups.
- One more object per vault in the user's cloud account, and more traffic than backups (each save and each foreground uploads or downloads the whole file; a vault is small).
- Two devices editing the same entry offline still produce a merge by modification times, as with Syncthing: the later edit wins and the earlier one stays in the entry's history.
- The remote object is a single point of divergence: if a service returns a stale read after a write (weak consistency), a device can briefly work from an old version; the conditional write is what keeps it from overwriting a newer one.
- The threat model must add: an attacker with the cloud account can delete or roll back the synchronised object (the app keeps its local file and private backups, and the user can restore from the rotated copies), and can see its size, name and times.
- The reconcile step is plain `core/sync` logic over `CloudBackend`; it needs a fake backend with injectable preconditions to test races on the JVM before any real service is used.

## Implementation design (2026-10-19)

How the decision above is built, for the S3-compatible services KeyNexa already supports. It refines points 3 and 5.

1. **The test of point 3 runs against the user's own bucket**, not once per provider: S3-compatible services differ, and the same endpoint can change. When the user turns synchronisation on, a probe writes a temporary object under `KeyNexa/sync/` and checks that a create-only upload (`If-None-Match: *`) over an existing object is refused, that an upload with the current entity tag (`If-Match`) is accepted, that one with a stale tag is refused, and that each refused upload left the content unchanged; then it deletes the object. Only a passing probe turns the switch on; it is stored with the setup and dropped when the service changes. Cloudflare R2 documents `If-Match` and `If-None-Match` on `PutObject`, evaluated atomically; Backblaze B2 documents neither, and is offered for synchronisation only if the probe passes on it.
2. **The remote object is always merged before anything is uploaded.** Nothing is uploaded over the remote object without first decrypting it with the open vault's credentials and merging, even when this device believes it is the only writer: another program may have replaced the local file with an older one, and only the merge (with its deleted-objects list) keeps that from rolling the others back. The one shortcut is "nothing to do": when the remote entity tag is the last one this device saw and the local file is the one it last synchronised, nothing is read or written.
3. **Upload only what the remote object lacks.** After the merge, the result is saved locally only if it differs from the local vault, and uploaded only if it differs from the remote vault (the same equality the conflict copies already use). This is the convergence rule that keeps two devices from bouncing files.
4. **The vault must be open and idle.** The merge runs on the session's own thread with the open vault's credentials; while a save is running, or with the vault locked, the sync is postponed (not an error) and the next trigger runs it. A save during a sync asks for one more run afterwards.
5. **The synchronisation id** (`KeyNexa.SyncId`, 32 lowercase hexadecimal digits from a secure random generator) is added the first time a vault is synchronised. Before creating a new one, the device looks at the objects already under `KeyNexa/sync/` (at most 10) and adopts one only if it decrypts with the open vault's credentials **and** has the same root group UUID: a copy of the same vault made earlier (by Syncthing or by hand) then joins the existing object instead of creating a second one. A vault with a malformed id is not synchronised. An object whose id inside differs from its name is never merged.
6. **Another master password** (point 5 and the last open point): in this version, a remote object that does not decrypt with the open vault's credentials is reported ("the cloud copy uses another master password") and nothing is written on either side. Merging it with the other password is left for later.
7. **A device without a local copy** downloads an object from `KeyNexa/sync/` into a file the user chooses, adds it to the known vaults and unlocks it as usual.
8. **Errors without content:** the state kept per synchronisation id (last entity tag seen, fingerprint of the local file last synchronised, last success, last error) holds no secret and no vault content.

## Open points (to verify before they are promised)

- Which services honour a conditional write reliably: Dropbox (`update` mode with a `rev`), OneDrive (`If-Match` with the entity tag, answered with 412), Google Drive (not found in the documentation), and each S3-compatible provider with `If-Match` on an upload. For S3-compatible services the per-bucket probe of the implementation design answers it; Cloudflare R2 documents both headers ([R2 S3 API compatibility](https://developers.cloudflare.com/r2/api/s3/api/)), Backblaze B2's [`PutObject`](https://www.backblaze.com/apidocs/s3-put-object) lists neither, and a third party reports that B2 rejects `If-None-Match` ([Proxmox patch, July 2025](https://lists.proxmox.com/pipermail/pbs-devel/2025-July/014234.html)). See `.agents/plans/cloud-backup/oauth-providers.RESEARCH.md` for the other providers.
- ~~How the vault id is chosen so two devices agree on it~~ Settled by [ADR-0013](0013-device-sync.md) (2026-10-18): a random synchronisation id stored in the vault's own custom data (`KeyNexa.SyncId`), shared by cloud and device synchronisation. The desktop client ([ADR-0012](0012-windows-desktop-client.md)) uses this record unchanged.
- Whether a background sync is worth its battery cost, and with which Android mechanism, once the foreground version has been measured.
- What happens on two devices with different master passwords after one changed it: the first version only reports it (implementation design, point 6); merging with the other password through the F1 flow is still open.
