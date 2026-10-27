# ADR-0015: Automatic updates for the Android and Windows apps

[English](0015-automatic-updates.md) | [Español](../es/decisions/0015-automatic-updates.md)

- **Status:** Proposed (the owner confirms the three points under *Open for the owner* before step 1 starts)
- **Date:** 2026-10-27
- **Extends:** [ADR-0012](0012-windows-desktop-client.md) (Windows desktop client); the release workflow of [DEVELOPMENT](../DEVELOPMENT.md)
- **Implements:** threat T29 of the [threat model](../THREAT_MODEL.md)

## Context

KeyNexa is published only as GitHub releases: two Android APKs (`offline` and `cloud`), signed with the project key, and a Windows MSI and ZIP, not code-signed (decision D9 is open). Users update by hand, so most of them will run old versions, security fixes included. The owner asked for automatic updates on both platforms (2026-10-20).

A password manager's update channel is the most attractive target it has: whoever can push an update runs code next to every vault. So the design starts from what an attacker could do, not from convenience.

Constraints:

- The `offline` build has no `INTERNET` permission and keeps it that way (T15).
- No KeyNexa servers, no accounts, no telemetry: the only party contacted is GitHub, which already hosts the files.
- Android never allows a silent install outside a store: the system installer always asks the user.
- The Windows MSI is per user and, since 1.11.5, upgrades in the folder where it is.
- An update never touches the vaults or the data folder.

## Threats

1. **A malicious release** published through a stolen GitHub account, a compromised workflow or a stolen signing secret.
2. **Tampering in transit** or a hostile network.
3. **Downgrade**: offering an older version with a known flaw.
4. **Swapping the downloaded file** between the check and the install.
5. **Privacy**: who learns that this machine runs KeyNexa, and which version.

## Options

1. **Notify only:** check the releases and show "version X is available" with a link. Safe and simple, but the user still downloads and installs by hand, and checks nothing.
2. **Download and check the published SHA-256.** The hash comes from the same release as the file, so it only proves the transfer, which HTTPS already does. Against threat 1 it adds nothing.
3. **Download and check a signature made with a KeyNexa update key whose public half is built into the app.** The release workflow signs a small manifest (version and SHA-256 of every file) with Ed25519; the app checks the signature, then the hash, then installs. A stolen GitHub account alone can no longer push an update: it also needs the private key. Ed25519 is in the JCA from Java 15, so the desktop needs no new dependency.
4. **Rely on the platform signature.** On Android the system already refuses an update not signed with the installed app's key, which is the same key the release uses. On Windows there is nothing equivalent until the MSI is code-signed (D9).

## Decision

Option 3 on Windows, option 4 on Android, and option 1 for the `offline` build, all opt-in.

1. **The check:** one anonymous HTTPS `GET` to `api.github.com/repos/catlinux/KeyNexa/releases`, with a `User-Agent` of `KeyNexa/<version>` and nothing else, at start-up and at most once a day. Pre-releases are included while every release is a pre-release (all of them are today). Only versions newer than the running one are offered: a downgrade is never proposed, whatever the server says.
2. **Asking:** the app shows the new version and its changelog entry and installs only after the user agrees, every time. There is no silent install.
3. **Windows:**
   1. The publish job of the release workflow writes `keynexa-update.json` (version, and the name, size and SHA-256 of every file) and signs it with the Ed25519 update key into `keynexa-update.json.sig`. The private key lives in a repository secret available only to that job, which already runs no Gradle and no third-party code (the same rule as the APK key).
   2. The app downloads the manifest and its signature, checks the signature against the public key it carries, checks that the version is newer, downloads the MSI into `%LOCALAPPDATA%\KeyNexa\updates`, checks the size and the SHA-256 against the manifest, locks the vault, starts `msiexec /i <file>` with the normal installer interface, and closes itself. Any failed check deletes the file and offers the release page instead.
   3. The hash is checked again right before `msiexec` starts, and the folder belongs to the user, which narrows threat 4 to code already running as that user (T23, out of scope).
4. **Android (`cloud` build only):** the app downloads the APK of its own build into its private cache, reads the signing certificate of the downloaded file and of the installed app, and hands the file to the system installer only when they are equal. The system checks the signature again. It needs the `REQUEST_INSTALL_PACKAGES` permission, added to the `cloud` build only. A build signed with another key (F-Droid's, for example) finds no update to offer, because the GitHub APKs could not install over it.
5. **The `offline` build:** no network, no check. Its *About* screen says how to update (the releases page, or an app that follows GitHub releases), and the stores will update it once KeyNexa is in F-Droid or IzzyOnDroid.
6. **Settings:** "Look for updates" is **off** until the user answers a one-time question on the first start after the update that brings the feature. It can be turned off at any time.
7. **Privacy:** [PRIVACY](../PRIVACY.md) says what GitHub sees (the IP address, the time and the app version in the `User-Agent`) and that nothing else is sent.

### Open for the owner

1. The update key: an Ed25519 key generated by the owner on a machine of theirs and stored as a repository secret (recommended, same model as the APK key), or kept offline with every release signed by hand (stronger, but releases stop being automatic).
2. The default: a one-time question (recommended), or on by default in the `cloud` build and on Windows.
3. Windows: install from the app as in point 3 (recommended), or notify only (option 1) until D9 is decided.

## Consequences

- A stolen GitHub account alone cannot deliver an update to Windows users: it also needs the update key. If the key leaks, the release workflow and the app need a new key; a key list in the app that allows rotation is left for later.
- On Android the trust stays where it is today: the APK signing key.
- The `cloud` build gains `REQUEST_INSTALL_PACKAGES`, and the app contacts GitHub, which the user agreed to; the `offline` build gains nothing.
- The release workflow gains a manifest and its signature; older versions ignore them.
- New code that downloads, verifies and launches an installer is security-critical: every check gets a test with a bad signature, a bad hash, an older version and a truncated file, and the full path is tested by hand on both platforms before it ships.
