# KeyNexa

[English](README.md) | [Español](README.es.md)

*Your keys. Your vault. Your control.*

A security-first, cross-platform password manager built around established cryptographic libraries, local vaults, and seamless Syncthing synchronization.

> **Project status: early development (0.10.x; v0.10.2 pre-release published).** Android only, for testing. You can already create or open a KDBX vault, browse, search, add and edit entries, use the recycle bin and generate passwords; saves go through a safe-save pipeline and changes made elsewhere (for example with KeePassXC through Syncthing) are merged. **Also working:** auto-lock (including in the background), screen protection, a settings screen, changes made elsewhere being picked up within seconds, and Syncthing conflict copies being merged when the vault is opened, with deletion offered after consent. **Also:** biometric unlock, and the sync status of the vault file with an optional warning after days without changes. **Not done yet:** fully reliable clipboard clearing and the Windows and Linux clients. Do not store real passwords in KeyNexa until a full release says otherwise.

## What KeyNexa will be

- An Android password manager first, with Windows and Linux clients later.
- Fully compatible with the KeePass **KDBX** format (3.1, 4.0 and 4.1), so your vault also opens in KeePassXC, KeePassDX and other KeePass clients.
- Local-first: your vault is a file you own. No accounts, no KeyNexa servers, no telemetry.
- Designed to live in a folder synchronized by [Syncthing](https://syncthing.net/), detecting external changes and merging conflicts instead of silently losing data.
- Bilingual interface: English (`en-US`) and Spanish (`es-ES`).

See the [roadmap](docs/ROADMAP.md) for what is planned in each phase.

## Documentation

| Document | Content |
| --- | --- |
| [Architecture](docs/ARCHITECTURE.md) | Modules, technology choices, multiplatform strategy |
| [Threat model](docs/THREAT_MODEL.md) | Assets, attackers, threats, mitigations, residual risks |
| [Dependencies and licenses](docs/DEPENDENCIES_AND_LICENSES.md) | Researched libraries, verified licenses, sources |
| [Development](docs/DEVELOPMENT.md) | Requirements, toolchain and how to build and test |
| [Roadmap](docs/ROADMAP.md) | MVP, acceptance criteria and later phases |
| [Decisions](docs/decisions/README.md) | Architecture decision records (ADR) |
| [Contributing](CONTRIBUTING.md) | Workflow, bilingual documentation rules, versioning |
| [Security policy](SECURITY.md) | How to report vulnerabilities |
| [Changelog](CHANGELOG.md) | Changes per version |
| [To do](TODO.md) | Pending work |

## Requirements, installation and usage

A pre-release is available from [GitHub Releases](https://github.com/catlinux/KeyNexa/releases). Download `keynexa-0.10.2.apk` and `keynexa-0.10.2.apk.sha256`. In the directory containing both files, verify the APK checksum and signer:

```sh
sha256sum -c keynexa-0.10.2.apk.sha256
apksigner verify --print-certs keynexa-0.10.2.apk
```

Compare the SHA-256 certificate fingerprint reported by `apksigner` with `51:ca:da:3c:d0:8c:35:5f:13:67:90:b1:be:83:09:00:8b:b9:a3:1e:e9:44:b4:e6:e3:74:7e:6a:e9:d0:44:9b`. KeyNexa does not request the Internet permission.

To build from source instead, you need JDK 17 and Android SDK 36 (see [Development](docs/DEVELOPMENT.md) for how to point Gradle at the SDK). From the repository root:

```sh
./gradlew build
./gradlew test
./gradlew :app-android:assembleDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`. The debug APK is written to `app-android/build/outputs/apk/debug/app-android-debug.apk` and can be installed on a phone for testing (use fictitious vaults only). To try synchronisation, put the vault in a folder shared with Syncthing and add it to KeyNexa with *Open existing vault*, choosing the folder (not a single file: Android revokes the permission on a single file when Syncthing replaces it).

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
