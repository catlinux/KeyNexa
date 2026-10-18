# Credits

[English](CREDITS.md) | [Español](docs/es/CREDITS.md)

## Original project

- **Author and copyright holder:** CatLinux
- **Project:** KeyNexa
- **Repository:** https://github.com/catlinux/KeyNexa
- **License:** GPL-3.0-only with the additional terms in [NOTICE](NOTICE)
- **Logo and icon:** CatLinux, made with the help of ChatGPT (OpenAI); source in `design/icon/`. Under NOTICE section 3 the name, logo and icon are not licensed to modified versions.

## Third-party material

The project skeleton includes the following components, each under its own license:

- **Kotlin standard library and Gradle plugins** — JetBrains, Apache-2.0.
- **AndroidX Core, AndroidX Activity, AndroidX Biometric (and its AndroidX dependencies), Jetpack Compose (UI and Material 3)** — The Android Open Source Project, Apache-2.0.
- **Compose Multiplatform 1.8.2** — JetBrains s.r.o. and the Compose Multiplatform contributors, Apache-2.0. Origin: https://github.com/JetBrains/compose-multiplatform. Used by the shared interface module `ui/shared` (theme, brand mark, keypad); on Android it maps to the Jetpack Compose libraries above, plus its small `ui-backhandler` module.
- **JNA 5.19.1 (Java Native Access)** — Timothy Wall and the JNA contributors, dual-licensed LGPL-2.1-or-later or Apache-2.0; used under Apache-2.0. Origin: https://github.com/java-native-access/jna. Used by `platform/windows` to call Windows DPAPI for the desktop client's secrets.
- **Java runtime (Eclipse Temurin JDK 17, OpenJDK) 17.0.18** — Eclipse Adoptium and the OpenJDK contributors, GPL-2.0 with the Classpath Exception. Origin: https://adoptium.net/. Bundled unmodified in the Windows MSI and ZIP with its `legal/` notices (including the third-party code inside the JDK).
- **Skiko 0.9.4.2** — JetBrains s.r.o., Apache-2.0. Origin: https://github.com/JetBrains/skiko. Graphics layer of the Windows client; its native library is built from Skia and ships ICU data (licenses to verify, see [Dependencies and licenses](docs/DEPENDENCIES_AND_LICENSES.md)).
- **JUnit 4** — JUnit team, EPL-1.0 (unit tests only; not distributed with the app).
- **Jazzer 0.30.0** — Code Intelligence GmbH and the Jazzer contributors, Apache-2.0. Origin: https://github.com/CodeIntelligenceTesting/jazzer. Coverage-guided fuzzing of the KDBX decoder in the `fuzz` CI workflow; downloaded as a CLI with a fixed SHA-256, not a Gradle dependency and not distributed with the app.
- **keepass-rs 0.15.2 and the `tools/kdbx-dump-rs` tool** — Stefan Seemayer and the keepass-rs contributors, MIT; the tool also uses serde, serde_json and sha2 (RustCrypto), MIT OR Apache-2.0. Origins: https://github.com/sseemayer/keepass-rs, https://github.com/serde-rs/serde, https://github.com/RustCrypto/hashes. Used by the differential test in the `differential` CI job; the Rust dependencies are pinned in `tools/kdbx-dump-rs/Cargo.lock`, and neither the tool nor they are distributed with the app.
- **kotpass 0.13.0** — Denis T. (copyright 2021), MIT. Origin: https://github.com/keemobile/kotpass, commit `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`. Files: `third_party/kotpass/` (its `LICENSE.txt` is kept; local changes are listed in its README). KDBX engine.
- **Okio 3.15.0** — Square, Inc., Apache-2.0. Origin: https://github.com/square/okio. Runtime dependency of kotpass, not vendored.
- **Kotlin XML Builder** — Jason Blackwell and contributors, Apache-2.0. Origin: https://github.com/redundent/kotlin-xml-builder. Bundled inside kotpass: `third_party/kotpass/src/main/kotlin/org/redundent/kotlin/xml/` (with its `LICENSE.txt`).
- **Apache Commons Lang (parts)** — The Apache Software Foundation, Apache-2.0. Origin: https://github.com/apache/commons-lang. Bundled inside kotpass: `third_party/kotpass/src/main/java/org/apache/commons/lang3/` (with its `LICENSE.txt` and `NOTICE.txt`).
- **Cryptographic engines ported from Bouncy Castle** — The Legion of the Bouncy Castle Inc. (copyright 2000-2021), Bouncy Castle License (MIT text). Origin: https://github.com/bcgit/bc-java. Bundled inside kotpass, each file keeping its license header, under `third_party/kotpass/src/main/kotlin/app/keemobile/kotpass/cryptography/`: `ByteUtils.kt`, `engines/Argon2Engine.kt`, `engines/Blake2bDigest.kt`, `engines/ChaCha7539Engine.kt`, `engines/ChaChaEngine.kt`, `engines/Salsa20Engine.kt`, `engines/TwofishEngine.kt` and `padding/PKCS7Padding.kt`.
- **Manrope (font)** — The Manrope Project Authors (copyright 2018), designed by Mikhail Sharanda, SIL Open Font License 1.1. Origin: https://github.com/googlefonts/manrope, taken from https://github.com/google/fonts (`ofl/manrope/Manrope[wght].ttf`, commit `b31870aff700ab7a1d74fa0c6887d95beb9e0037`, SHA-256 `3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6`). Files: `ui/shared/src/commonRes/font/manrope.ttf` (unmodified variable font; its copyright and license notice are embedded in the font) and `third_party/manrope/OFL.txt` (license text). Interface font of the app.

Planned components and their verified licenses: [Dependencies and licenses](docs/DEPENDENCIES_AND_LICENSES.md).

## Inspiration

KeyNexa reads and writes the KeePass KDBX format, created by Dominik Reichl for [KeePass](https://keepass.info/). Other KeePass clients (KeePassXC, KeePassDX, Keepass2Android) were studied as references for behaviour only; no code, text, icons or visual identity from them is copied.
