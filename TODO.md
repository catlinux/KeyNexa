# To do

[English](TODO.md) | [Español](docs/es/TODO.md)

Detailed steps: see the [roadmap](docs/ROADMAP.md).

## High priority (phase C — project foundation)

- [ ] **Windows packages, before the first desktop pre-release:** verify the Skia and ICU licenses and notices inside Skiko ([dependencies](docs/DEPENDENCIES_AND_LICENSES.md)); decide D9 (code signing: SmartScreen warns while the files are unsigned); try the MSI on Windows 10 and on a clean machine, and upgrading over an older MSI; check that the `windows` job of `release.yml` passes on a real run (it has only been exercised locally); decide whether 1.x releases stay pre-releases while the desktop client is a preview (the workflow marks them so).
- [ ] Desktop polish seen in the hand test of 2026-10-19: pressing Enter in the "New group" dialog does not create the group (only the button does); the dialogs use Material's default lilac surface instead of a KeyNexa colour.
- [ ] **Desktop manual checks still open:** Win+L and suspend locking, and a real cloud copy to Backblaze B2 or Cloudflare R2 from the desktop assistant (the steps are in the [plan](.agents/plans/escritorio/escritorio.PLAN.md)).

- [ ] **Windows desktop client and KeyNexa's own synchronisation** (owner's priority, decided on 2026-10-18 in [ADR-0011](docs/decisions/0011-repository-layout.md), [ADR-0012](docs/decisions/0012-windows-desktop-client.md) and [ADR-0013](docs/decisions/0013-device-sync.md)): repository reorganised by role, shared theme and session, Windows 10/11 app with the same functions and look as Android, local-only / cloud copies / device sync modes, and synchronisation between devices that replaces Syncthing-Fork. Steps in `.agents/plans/escritorio/escritorio.PLAN.md`. Linux follows in the same desktop module.

- [ ] **A far more attractive README** (requested by the owner on 2026-10-18): screenshots of the main screens in light and dark (taken with a fictitious vault and the screenshot setting turned on only for the moment), a clear hero with the mark and the tagline, badges (release, license, CI), a short feature list with icons, and the install steps for both builds; same structure in `README.es.md`. The screenshots also go to `fastlane/metadata/android/*/images/phoneScreenshots/`.

- [ ] Biometric unlock: the ADR-0006 matrix is checked on a Pixel 10 Pro (enable, unlock, new fingerprint invalidates, password due after the chosen days, password changed on another device). Still open: phones without StrongBox and API 26-29.

- [ ] Measure kotpass Argon2 on a real phone once the app opens a vault; decide on argon2kt (see `docs/BENCHMARKS.md`).
- [ ] Approve the rest of the identity proposed in `docs/DESIGN.md` (palette, font); the mark is decided.

- [ ] Contribute the kotpass fixes upstream: four patches are ready in `.agents/plans/kotpass-upstream/`; publishing needs the maintainer's approval and tests inside kotpass.

## Medium priority (phase D — secure MVP)

- [ ] Cloud as an always-on hub: let a vault live in WebDAV or S3-compatible storage through a remote `StoragePort` with conditional writes (ETag / If-Match), so the same read-merge-write pipeline works for devices that are never on at the same time. Needs an ADR.
- [ ] Sign-in provider on entries ("Sign in with Google" sites): an optional "Sign in with" field (Google, Apple, GitHub…) that links to the entry of the account used, so the entry needs no empty password; the linked account's entry lists the sites that depend on it. Stored as custom fields, so the vault stays standard KDBX. Decision of 2026-10-16: KeyNexa itself never offers "Sign in with Google" (the vault key must come only from what the user holds).
- [ ] Standard "API / token" entry type (requested 2026-10-16): fields for key ID, secret key, endpoint and token value, with the secret protected like a password, so API credentials (for example a Cloudflare R2 token) are not stored as free-form notes. Stored as custom fields to keep the vault standard KDBX.
- [ ] Standard "SSH / server" entry type (requested 2026-10-18): accept `ssh://` and similar schemes in the URL field without complaint, and offer a connection field (for example `ssh stark@192.168.1.150 -p9322`) with a copy button, next to host, port and user. KeePassXC rejects `ssh://` in its URL field, so KeyNexa should not. Stored as custom fields to keep the vault standard KDBX.
- [ ] Extend `docs/VAULT_CONVENTIONS.md` (and its translation) with what was agreed on 2026-10-18: the families (`Mail`, `Banking` with the `neobank` tag, `Crypto`, `Shopping`, `Leisure`, `Servers`, `API`, `AI` for subscription accounts, `Projects`, `Identity`), one entry per account, the title pattern `Service (account)`, the `name: value` format in Notes as a bridge until custom fields exist (never for secrets), and the pending custom fields per family to create once the desktop client is there. Planned for the next session.
- [ ] **Harden against keyloggers and malware** (decided 2026-10-16, refined 2026-10-18; threat T22 in [THREAT_MODEL](docs/THREAT_MODEL.md); an app cannot stop a compromised device, so these only reduce the damage). Done: touches are ignored while another app draws a window over KeyNexa (0.30.5); the unlock screen says quietly, in small grey text, when the keyboard in use or an enabled accessibility service does not come with the system (0.31.0). The key file stays optional (decision of 2026-10-18: losing it loses the vault) and the create screen now explains the benefit and the risk (0.30.6); no warning for vaults without one. Pending: ask for the master password again before sensitive actions, once export or change of key exist (neither exists yet); the KeyNexa keypad ([ADR-0010](docs/decisions/0010-in-app-password-keypad.md)) is used for every master password field since 0.33.0 (unlock, create vault, password of a conflict copy); large text was checked on 2026-10-18; with a screen reader on the system keyboard is used (0.33.3), so no TalkBack test is needed; **still to do:** check the conflict-copy dialog on a phone (final audit). Keep reviewing permissions and the screen-capture and clipboard protections in the final audit. Each item needs its own design and threat check.
- [ ] Cheaper look for outside changes: look less often after minutes without activity. Measure the battery cost on a real phone before and after.
- [ ] Fully reliable clipboard clearing (Android only lets the app in front change the clipboard).
- [ ] Ask for inclusion in F-Droid and IzzyOnDroid (E2.4; parked on 2026-10-15: the first IzzyOnDroid request on GitLab was rejected for not following the request template, so it has to be redone by hand with the template): the draft metadata and the steps are in `.agents/plans/store-inclusion/`; the requests are filed by hand (Izzy's form asks about AI use and needs honest answers).

## Open follow-ups and checks (2026-10-15)

- [ ] **Later, not blocking 1.0 (owner's decision of 2026-10-18; the desktop client comes first):** biometric unlock on a phone without StrongBox and on API 26–29, and Argon2 on a modest phone. Every other phone check is done; see the [security review](docs/SECURITY_REVIEW.md#checks-on-the-phone).
- [ ] **Cloud copies** ([ADR-0008](docs/decisions/0008-cloud-backup.md), accepted on 2026-10-15): the S3 flow works with a real Backblaze B2 account (2026-10-15). Next: ease the form (offer the bucket list instead of typing it, if the service allows listing with a bucket-limited key), Cloudflare R2 is done and verified (2026-10-16); still a guide and a real test for Amazon S3, and a way to disconnect one service and choose another; then Dropbox, OneDrive and Google Drive once the app is registered with each; the user guide `docs/CLOUD_SETUP.md` is verified for Backblaze B2 and Cloudflare R2. Plan: `.agents/plans/cloud-backup/`.
- [ ] **kotpass pull requests #43 to #46** ([keemobile/kotpass](https://github.com/keemobile/kotpass/pulls)): the red CI was diagnosed and handled on 2026-10-15 (#43: test fixture fixed and pushed as `1955d4a`; #44 and #46: only the Codecov token step, which forks cannot pass, explained in a comment; #45: network timeout, PR reopened) and the maintainer's question on #46 was answered. Waiting only for the maintainer: his reply on #46 and, if it asks for it, approval to run the workflow on #43. Notes: `.agents/plans/kotpass-upstream/`.
- [ ] **Why the `differential` CI job sometimes resolves `kotlinx-coroutines-bom` 1.8.0 on the build classpath** while other jobs and Windows never do. The intermittent failure was Gradle dependency verification, not keepass-rs (read from the annotation added in 0.33.2); 0.33.9 adds the checksum checked against Maven Central, which stops the failure. The cause of the varying resolution is still unknown; look at it when moving to Gradle 9.
- [ ] **Check the release workflow with the build variants:** `release.yml` now signs the `offline` APK from its new path and has not run since; the first tag after 0.10.3 will prove it. Nothing after 0.10.3 has been released.

## Low priority (later)

- [ ] Move to the next major lines of the build tools (stable releases only): Gradle 9.x, Android Gradle plugin 9.x (it changes how Kotlin is integrated) and Kotlin 2.3 or 2.4, and with them `compileSdk`/`targetSdk` 37 (Android 17): the CI's lint already reports `OldTargetApi` for 36, made informational in 1.0.0 so it does not block. Also update `actions/cache@v4` in `ci.yml` (Node.js 20 warning). It is a migration, not maintenance: do it in its own step with the CI scan (104 findings on 2026-10-14, all in build tools, none in the APK) as the measure. Patch updates inside the current lines (Gradle 8.14.6, AGP 8.13.2) are done; Kotlin 2.2.21 has no newer patch.
- [ ] Autofill service and credential provider (passkeys).
- [ ] Configure and edit TOTP from the app (scan a QR code or paste an `otpauth` URI) and show the codes in autofill; the entry screen already shows existing TOTP codes read-only.

## Under evaluation (decision pending)

- Showing changes not yet uploaded requires the Syncthing add-on (KeyNexa does not use its REST API).
- Bringing the cryptographic primitives fully inside the project (they are already vendored with kotpass) instead of depending on external binaries. Writing new cipher algorithms is not planned: only audited implementations, pinned and tested against official vectors.
- Setting up a new device from a seed phrase: an encrypted bundle with the settings and the synchronisation pairing (never the master password), protected with a key derived from the phrase, kept in the synchronised folder or shown as a QR code. Needs an ADR.
- Backup copies to the cloud: first to any folder the system exposes (Drive, Dropbox, Mega and others through their own apps, with no extra SDK), later WebDAV and S3-compatible storage; no proprietary SDKs with telemetry. The app must strongly recommend it (first-run prompt and a persistent reminder until a copy exists, never forced): a lost or broken phone with no copy means losing every password. Needs an ADR. Now proposed in [ADR-0008](docs/decisions/0008-cloud-backup.md); the step plan is in `.agents/plans/cloud-backup/`.
- Keep kotpass strong: offer to help maintain it upstream, add coverage-guided fuzzing there and the keepass-rs comparison (already listed above), and re-evaluate alternatives yearly (ADR-0005).

- Automatic updates (idea from the owner, 2026-10-16): how users receive new versions. The `offline` build has no network access, so it cannot check for updates by itself; F-Droid and IzzyOnDroid update the apps they carry, and GitHub releases are manual. To evaluate: rely on the stores (and tools that follow GitHub releases) for the `offline` build; an optional update check that the user turns on, in the `cloud` build only, that reads the public GitHub releases, shows a notice with a link and verifies the published SHA-256 and the signature, without installing anything by itself. Needs an ADR (privacy: what is sent and to whom; signature verification; what the stores allow).

- Application authentication and an own identity provider (idea from the owner, 2026-10-16; to study whether to include them, the exact scope still to be confirmed): (a) KeyNexa as an authenticator app, extending the read-only TOTP already shown to create, scan (QR) and edit codes, and to approve sign-ins; (b) KeyNexa acting as an identity provider of its own ("Sign in with KeyNexa", OpenID Connect/passkeys). (b) would need a server and an account system, which contradicts the local-first design (see the decision on "Sign in with Google" above), so it needs an ADR and a threat model before any work.

## Discarded

- Rust core with UniFFI: KDBX writing in keepass-rs is experimental and UniFFI adds an NDK toolchain; re-evaluated before 1.0 ([ARCHITECTURE](docs/ARCHITECTURE.md#multiplatform-strategy)).
- `MANAGE_EXTERNAL_STORAGE` permission: not needed with SAF and not allowed on Google Play for this use.
- Depending on Syncthing's REST API: requires an API key and Syncthing may not be installed.
- Shipping Syncthing inside KeyNexa as a plugin (decided on 2026-10-18): replaced by KeyNexa's own synchronisation between devices ([ADR-0013](docs/decisions/0013-device-sync.md)), which needs no extra binary, process or foreground service.
