# ADR-0008: Cloud copies of the vault: optional wizard, `cloud` build variant, providers

[English](0008-cloud-backup.md) | [Español](../es/decisions/0008-cloud-backup.md)

- **Status:** Accepted (2026-10-15, by the project owner)
- **Date:** 2026-10-15
- **Extends:** [ADR-0004](0004-storage-sync-and-safe-save.md); the later "cloud as an always-on hub" step needs its own record

## Context

Today a vault lives in a folder that Syncthing shares. A lost or broken phone with no copy elsewhere loses every password, and two devices that are never on at the same time cannot meet. The owner wants an optional, strongly recommended cloud copy built into KeyNexa, free to use, with an assistant that explains what each service needs, and later the same service as a meeting point for synchronisation.

Constraints:

- The vault file is already encrypted (KDBX); the cloud only ever receives ciphertext.
- The app declares no `INTERNET` permission today and says so. Android does not let an app turn that permission on and off at run time.
- No proprietary SDKs, no telemetry, no new dependency without a recorded reason and license ([AGENTS.md](../../AGENTS.md)); no cryptography of our own.
- Signing in to Google, Dropbox or Microsoft needs the project to register KeyNexa with each of them once; the result is a public client identifier. That is a manual step for the project owner, not something the assistant can do.

## Options

1. **A folder exposed by the system picker (SAF).** Simple, no network code, and it stays available; but provider support is uneven and there is nothing to guide the user.
2. **WebDAV** (Koofr, Nextcloud, own server). Simple protocol; few free providers remain.
3. **S3-compatible storage** (Backblaze B2, Cloudflare R2, MinIO on a VPS, AWS). No project registration, versioning and retention on the server; the user must create an account, a bucket and keys, so the assistant has to guide them.
4. **OAuth with the provider's REST API** (Google Drive, Dropbox, OneDrive). Easiest for the end user (one sign-in); needs the registrations above, and the rules of each provider decide the effort.
5. **Syncthing as an optional plugin.** Already under evaluation (TODO); independent of this record.

## Decision

1. **Two build variants of the same app.** `offline` (the current build, no `INTERNET`, the one for F-Droid and for anyone who wants no network) and `cloud` (declares `INTERNET`, includes the cloud code). CI builds both and checks that the `offline` manifest has no `INTERNET`. How both are published, and whether one can be installed over the other, is decided when the variants are built.
2. **A new module for network code**, outside the `core-*` modules, with its dependencies recorded as usual. It starts with the platform HTTP client behind a small interface; a library is added only if that proves insufficient.
3. **One interface for every service**, `CloudBackend`: put, get, list and delete by name, returning the provider's entity tag or revision, and a capability flag for conditional writes ("only if unchanged"). The first use is **backups only**: copies named `<vault>-<UTC date and time>.kdbx` under a KeyNexa-owned folder or prefix, kept up to a chosen number (default 10, only copies KeyNexa made are ever rotated), uploaded in the background after a successful save at most once a day, plus a "Copy now" button. A failed upload shows a quiet notice and never blocks saving.
4. **An assistant.** It appears only in the `cloud` variant and only when the user chooses it: (a) what is sent, where and why; (b) choose the service in the order Google Drive, Dropbox, OneDrive, S3; (c) what that service needs, with links to create the account or key; (d) connect and test; (e) first copy; (f) reminder while no copy exists, never forced. Services not implemented yet are not shown.
5. **Consent and clarity.** A screen states, before anything is configured, that only the encrypted vault file is sent, only to the chosen service, and only for copies and later synchronisation; that the master password and keys never leave the device; and how to disconnect. "Disconnect" deletes the local credentials and, where the provider allows, revokes the grant.
6. **Order of implementation.** S3 first (no registration, validates the interface and the assistant), then Dropbox, OneDrive and finally Google Drive. The assistant lists them as in point 4.
7. **OAuth providers** use the authorization-code flow with PKCE in the system browser, no client secret in the app, and the narrowest scope the provider has (an app-private folder, never the whole drive). The client identifiers are public and live in the code; how to register each is written in `docs/CLOUD_SETUP.md` when the provider is added.
8. **Credentials** (S3 keys, OAuth tokens) are kept encrypted with a non-exportable Android Keystore key, in storage excluded from backup, and are never logged; requests are never logged either, because they carry signatures or tokens. TLS certificate validation stays at the system default.
9. **Synchronisation through the cloud** (remote `StoragePort`) is a later step with its own ADR. It is only offered for services whose conditional writes have been tested; for the others the cloud stays a backup.

## Consequences

- The `cloud` variant has a larger attack surface than `offline`. The threat model gains cloud account compromise, credential theft and metadata exposure (name, size and times of the file); the main mitigations are the strong master password the user already needs, a key file, and the narrow scopes.
- Old copies can hold old passwords (the history of entries is inside the file): the assistant says so, and the copy count is limited by default.
- The README and the store listings must say that only the `offline` variant has no network access.
- A privacy statement (`docs/PRIVACY.md`, both languages) is required before publishing the `cloud` variant, and some providers ask for it to register the app.
- The project owner must register the app with each OAuth provider before its backend ships, and keep those registrations alive.
- Services that offer a free tier change it from time to time: the documentation lists them as information, never as a promise.

## Open points (to verify before they are promised)

- Google ties an Android client to the package name and the signing certificate. F-Droid signs with its own key, so Google sign-in may not work in that build; and it is not known whether KeyNexa must pass Google's verification.
- Whether Microsoft requires an Azure account to register the app, and what Dropbox's free app-folder limits are.
- Which providers honour conditional writes reliably (needed for point 9).
- Current free storage and its conditions, per provider.
