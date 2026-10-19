# KeyNexa

[English](README.md) | [Español](README.es.md)

*Your keys. Your vault. Your control.*

A security-first, cross-platform password manager built around established cryptographic libraries, local vaults, and seamless Syncthing synchronization.

> **Project status: Android 1.0 released (2026-10-18); the Windows client is being built (a preview, not published yet; see [Install on Windows](docs/INSTALL_WINDOWS.md)).** You can create or open a KDBX vault, browse, search, add and edit entries (with attachments and clearing an entry's previous versions), use the recycle bin, generate passwords and see an entry's one-time (TOTP) codes; saves go through a safe-save pipeline, and changes made elsewhere (for example with KeePassXC through Syncthing) are merged, conflict copies included. Also: biometric unlock, auto-lock (also in the background), screen protection, the KeyNexa keypad for master passwords, and optional encrypted cloud copies (S3-compatible: Backblaze B2, Cloudflare R2) in the `cloud` build. See the [security review](docs/SECURITY_REVIEW.md).

## What KeyNexa will be

- An Android password manager first, with Windows and Linux clients later.
- **Not planned: macOS and iOS (iPhone/iPad).** The project has no Apple hardware to test on, and a program of this kind cannot be built responsibly with emulators alone.
- Fully compatible with the KeePass **KDBX** format (3.1, 4.0 and 4.1), so your vault also opens in KeePassXC, KeePassDX and other KeePass clients.
- Local-first: your vault is a file you own. No accounts, no KeyNexa servers, no telemetry.
- Designed to live in a folder synchronized by [Syncthing](https://syncthing.net/), detecting external changes and merging conflicts instead of silently losing data.
- Bilingual interface: English (`en-US`) and Spanish (`es-ES`).
- On Windows, an optional quick unlock with Windows Hello (PIN, face or fingerprint) after the app locks itself; off by default, kept only in memory and expiring after 1 to 24 hours ([ADR-0014](docs/decisions/0014-windows-hello-quick-unlock.md)).

See the [roadmap](docs/ROADMAP.md) for what is planned in each phase.

## Documentation

| Document | Content |
| --- | --- |
| [Architecture](docs/ARCHITECTURE.md) | Modules, technology choices, multiplatform strategy |
| [Threat model](docs/THREAT_MODEL.md) | Assets, attackers, threats, mitigations, residual risks |
| [Install on Windows](docs/INSTALL_WINDOWS.md) | MSI and portable ZIP of the Windows client (preview), checks and data folder |
| [Privacy](docs/PRIVACY.md) | What each build does with your data |
| [Dependencies and licenses](docs/DEPENDENCIES_AND_LICENSES.md) | Researched libraries, verified licenses, sources |
| [Development](docs/DEVELOPMENT.md) | Requirements, toolchain and how to build and test |
| [Roadmap](docs/ROADMAP.md) | MVP, acceptance criteria and later phases |
| [Decisions](docs/decisions/README.md) | Architecture decision records (ADR) |
| [Contributing](CONTRIBUTING.md) | Workflow, bilingual documentation rules, versioning |
| [Security policy](SECURITY.md) | How to report vulnerabilities |
| [Changelog](CHANGELOG.md) | Changes per version |
| [To do](TODO.md) | Pending work |

## Requirements, installation and usage

Releases are published on [GitHub Releases](https://github.com/catlinux/KeyNexa/releases) in two builds of the same app, signed with the same key: `keynexa-<version>.apk` (**offline**: no Internet permission at all) and `keynexa-cloud-<version>.apk` (**cloud**: adds the optional encrypted cloud copies, so it asks for the Internet permission; see [Privacy](docs/PRIVACY.md) and the [cloud setup guide](docs/CLOUD_SETUP.md)). Install one of them; installing the other one later keeps your vaults and settings. Download the APK and its `.sha256` file and, in the directory containing both, verify the checksum and the signer (for the cloud build, use `keynexa-cloud-1.0.0.apk`):

```sh
sha256sum -c keynexa-1.0.0.apk.sha256
apksigner verify --print-certs keynexa-1.0.0.apk
```

Compare the SHA-256 certificate fingerprint reported by `apksigner` with `51:ca:da:3c:d0:8c:35:5f:13:67:90:b1:be:83:09:00:8b:b9:a3:1e:e9:44:b4:e6:e3:74:7e:6a:e9:d0:44:9b`. Only the `offline` build is free of the Internet permission; the release workflow checks it before publishing.

To build from source instead, you need JDK 17 and Android SDK 36 (see [Development](docs/DEVELOPMENT.md) for how to point Gradle at the SDK). KeyNexa ships two variants: `offline` (no network access) and `cloud` (declares `INTERNET` for the optional cloud copies; see the [cloud copies setup guide](docs/CLOUD_SETUP.md)). From the repository root:

```sh
./gradlew build
./gradlew test
./gradlew :apps:android:assembleOfflineDebug
./gradlew :apps:android:assembleCloudDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`. The debug APKs are written to `apps/android/build/outputs/apk/offline/debug/keynexa-android-offline-debug.apk` and `apps/android/build/outputs/apk/cloud/debug/keynexa-android-cloud-debug.apk`, and can be installed on a phone for testing (use fictitious vaults only). They are a separate app, *KeyNexa Debug* (`io.github.catlinux.keynexa.debug`), that installs next to a published KeyNexa without touching its vaults or settings. Installing one variant over the other keeps vaults and settings (checked on a phone). To try synchronisation, put the vault in a folder shared with Syncthing and add it to KeyNexa with *Open existing vault*, choosing the folder (not a single file: Android revokes the permission on a single file when Syncthing replaces it).

## Credits and license

**KeyNexa** is developed and maintained by **CatLinux**.
Copyright (C) 2026 CatLinux.

Licensed under the [GNU General Public License v3.0](LICENSE) (`GPL-3.0-only`): you may use, study, modify and share it, including commercially, but any distributed version, modified or not, must remain under GPL-3.0 and include its complete source code.

Under section 7 of the GPL, the [additional terms in NOTICE](NOTICE) also apply: copies and forks must keep this attribution and link to https://github.com/catlinux/KeyNexa, modified versions must be marked as such, and the KeyNexa name and logo are not licensed for forks.

### Third-party material

The project skeleton uses the components below. Each keeps its own license and notices; the full research is in [Dependencies and licenses](docs/DEPENDENCIES_AND_LICENSES.md).

| Component | Author | License | Link |
| --- | --- | --- | --- |
| Kotlin standard library and Gradle plugins | JetBrains | Apache-2.0 | https://kotlinlang.org/ |
| AndroidX Core, AndroidX Activity, Jetpack Compose (UI and Material 3) | The Android Open Source Project | Apache-2.0 | https://developer.android.com/jetpack |
| JUnit 4 (unit tests only; not shipped) | JUnit team | EPL-1.0 | https://junit.org/junit4/ |
| kotpass 0.13.0, vendored in `third_party/kotpass` (KDBX engine) | Denis T. | MIT | https://github.com/keemobile/kotpass |
| Okio 3.15.0 | Square, Inc. | Apache-2.0 | https://github.com/square/okio |
| Kotlin XML Builder, bundled in kotpass | Jason Blackwell and contributors | Apache-2.0 | https://github.com/redundent/kotlin-xml-builder |
| Apache Commons Lang (parts), bundled in kotpass | The Apache Software Foundation | Apache-2.0 | https://github.com/apache/commons-lang |
| Cryptographic engines ported from Bouncy Castle (Argon2, Blake2b, ChaCha, Salsa20, Twofish, PKCS7), bundled in kotpass | The Legion of the Bouncy Castle Inc. | Bouncy Castle License (MIT text) | https://github.com/bcgit/bc-java |

Full credits: [CREDITS.md](CREDITS.md).
