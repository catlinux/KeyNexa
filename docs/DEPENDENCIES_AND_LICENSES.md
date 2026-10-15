# Dependencies and licenses

[English](DEPENDENCIES_AND_LICENSES.md) | [Español](es/DEPENDENCIES_AND_LICENSES.md)

> Research date: **2026-10-13**. Licenses were checked in each project's own `LICENSE` file, build file or Maven Central POM, as cited. Items marked *not verified* could not be confirmed. This is technical research, not legal advice.

## KeyNexa's license and compatibility rule

KeyNexa's own code is licensed under **GPL-3.0-only** with the additional terms of section 7 in [NOTICE](../NOTICE) ([ADR-0001](decisions/0001-license.md)). Every component distributed with the app must be compatible with GPL-3.0:

- Compatible: MIT, BSD, ISC, Apache-2.0, Bouncy Castle License (MIT text), SIL OFL 1.1 (fonts), GPL-3.0, LGPL-3.0.
- Not compatible: GPL-2.0-only, proprietary or "all rights reserved" code, licenses with field-of-use restrictions.
- Each third-party component keeps its own license and notices, listed in [CREDITS.md](../CREDITS.md) when included.

## KDBX libraries evaluated

| Library | License (verified) | Platform | KDBX read/write | Ciphers / KDF | Maintenance (Oct 2026) | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| [kotpass](https://github.com/keemobile/kotpass) (Maven `app.keemobile:kotpass`) | MIT (`LICENSE.txt`, "Copyright (c) 2021 Denis T.") | JVM (not KMP) | 3.x and 4.x, writes 3.1/4.1 | AES, ChaCha20, Twofish; AES-KDF, Argon2d, Argon2id | 0.13.0 (2025-09-28); single maintainer, who calls it "feature complete" (issue #42, 2026-09-03) | **Selected**, vendored and reinforced ([ADR-0005](decisions/0005-reinforced-kotpass.md)) |
| [KeePassJava2](https://github.com/jorabin/KeePassJava2) | Apache-2.0 | JVM (Java 17 for 3.1.0) | 3.1, 4, 4.1 (open issue: 4.1 written back as 4.0, #114) | AES, ChaCha20; AES-KDF, Argon2d only (from source) | 3.1.0 (2026-10-05), very recent breaking API; recent attachment-integrity fixes | Backup candidate |
| [hudeany/kdbx](https://github.com/hudeany/kdbx) | MIT | JVM | 3.x/4.x | AES, ChaCha20; AES-KDF, Argon2d/id | First Maven release 26.1.5 (2026-10-06), 2 stars | Too young |
| [openkeepass](https://github.com/cternes/openkeepass) | Apache-2.0 | JVM | KDBX 3 only | No Argon2/ChaCha20 | Deprecated by its author | Rejected |
| [keepass-rs](https://github.com/sseemayer/keepass-rs) | MIT | Rust | Reads all; writing is behind the `save_kdbx4` feature and described as experimental | Full | Very active (0.15.2, 2026-10-11); open data-loss issues on attachments/history (#360, #375) | Rejected for now ([ARCHITECTURE](ARCHITECTURE.md)) |
| [gokeepasslib](https://github.com/tobischo/gokeepasslib) | MIT | Go | 3.1/4.0/4.1 | — | Active | Rejected: Go toolchain on Android |
| [kdbxweb](https://github.com/keeweb/kdbxweb) | MIT | JavaScript | — | Argon2 provided externally | Last push 2024-12 | Rejected: wrong ecosystem |
| [KeePassDX](https://github.com/Kunzisoft/KeePassDX) | GPL-3.0-or-later | Android app | — | — | Active | Not a published library; native JNI. Used only as a behavioural reference |
| KeePassXC, Keepass2Android, KeePassium | GPL | C++/C#/Swift | — | — | — | Not usable as JVM libraries |

### kotpass details (selected)

- Single runtime dependency: `com.squareup.okio:okio` 3.15.0, Apache-2.0 ([POM](https://repo1.maven.org/maven2/com/squareup/okio/okio/3.15.0/okio-3.15.0.pom)); plus the Kotlin standard library (Apache-2.0).
- Bundled third-party source inside kotpass, each with its license file in the repository: Kotlin XML Builder 1.9.3 (Apache-2.0), Apache Commons Lang 3.17.0 parts (Apache-2.0), and cryptographic engines ported from Bouncy Castle (Argon2, Blake2b, ChaCha, Salsa20, Twofish) carrying the Bouncy Castle License header (MIT text). These notices must be preserved in KeyNexa's credits.
- No native code. Uses `java.time.Instant` (needs API 26+ or desugaring).
- Since 0.13.0 the KDF can be replaced by a native implementation (issue #38).
- Tests: about 62 test files, crypto test vectors and 7 sample `.kdbx` files; no fuzzing found.
- **Gaps to verify in phase C/D:** unknown XML elements are probably not preserved on save (no field in the model; not tested); performance of pure-Kotlin Argon2 on low-end phones with KeePassXC-sized memory parameters.
- Verified commit: `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`.
- Imported into the repository in phase C5 as the Gradle module `:kotpass` (`third_party/kotpass/`): main sources only, upstream license files intact. See its [README](../third_party/kotpass/README.md) for local changes.

## Runtime dependencies in the Android APK

The entries below are the external modules locked for the app's release flavors (`offlineReleaseRuntimeClasspath` and `cloudReleaseRuntimeClasspath`) in [app-android/gradle.lockfile](../app-android/gradle.lockfile), grouped by project. Test-only and build-only dependencies are excluded. The vendored `:kotpass` source module is included in the app but is not an external lockfile coordinate; its bundled source licenses are listed below. BOM entries in the lockfile provide version alignment and are not packaged in the APK.

| Project | Locked modules and versions | License |
| --- | --- | --- |
| AndroidX | androidx.activity: activity-compose, activity-ktx, activity 1.10.1; androidx.annotation: annotation-experimental 1.4.1, annotation-jvm and annotation 1.9.1; androidx.appcompat: appcompat-resources, appcompat 1.2.0; androidx.arch.core: core-common, core-runtime 2.2.0; androidx.autofill: autofill 1.0.0; androidx.biometric: biometric 1.1.0; androidx.collection: collection-jvm, collection-ktx, collection 1.5.0; androidx.concurrent: concurrent-futures 1.1.0; androidx.core: core-ktx, core-viewtree, core 1.18.0; androidx.cursoradapter: cursoradapter 1.0.0; androidx.customview: customview-poolingcontainer, customview 1.0.0; androidx.drawerlayout: drawerlayout 1.0.0; androidx.emoji2: emoji2 1.4.0; androidx.fragment: fragment 1.8.5; androidx.graphics: graphics-path 1.0.1; androidx.interpolator: interpolator 1.0.0; androidx.lifecycle: lifecycle-common-java8, lifecycle-common-jvm, lifecycle-common, lifecycle-livedata-core-ktx, lifecycle-livedata-core, lifecycle-livedata, lifecycle-process, lifecycle-runtime-android, lifecycle-runtime-compose-android, lifecycle-runtime-compose, lifecycle-runtime-ktx-android, lifecycle-runtime-ktx, lifecycle-runtime, lifecycle-viewmodel-android, lifecycle-viewmodel-ktx, lifecycle-viewmodel-savedstate-android, lifecycle-viewmodel-savedstate, lifecycle-viewmodel 2.9.0; androidx.loader: loader 1.0.0; androidx.profileinstaller: profileinstaller 1.4.0; androidx.savedstate: savedstate-android, savedstate-compose-android, savedstate-compose, savedstate-ktx, savedstate 1.3.0; androidx.startup: startup-runtime 1.1.1; androidx.tracing: tracing 1.2.0; androidx.vectordrawable: vectordrawable-animated, vectordrawable 1.1.0; androidx.versionedparcelable: versionedparcelable 1.1.1; androidx.viewpager: viewpager 1.0.0 | Apache-2.0 |
| Jetpack Compose | androidx.compose.animation: animation-android, animation-core-android, animation-core, animation 1.9.1; androidx.compose.foundation: foundation-android, foundation-layout-android, foundation-layout, foundation 1.9.1; androidx.compose.material: material-icons-core-android, material-icons-core 1.7.8; material-ripple-android, material-ripple 1.9.1; androidx.compose.material3: material3-android, material3 1.3.2; androidx.compose.runtime: runtime-android, runtime-annotation-android, runtime-annotation, runtime-saveable-android, runtime-saveable, runtime 1.9.1; androidx.compose.ui: ui-android, ui-geometry-android, ui-geometry, ui-graphics-android, ui-graphics, ui-text-android, ui-text, ui-unit-android, ui-unit, ui-util-android, ui-util, ui 1.9.1; BOM 2025.09.00 | Apache-2.0 |
| Kotlin | `kotlin-stdlib`, `kotlin-stdlib-common` 2.2.21 | Apache-2.0 |
| kotlinx.coroutines | `kotlinx-coroutines-android`, `kotlinx-coroutines-core`, `kotlinx-coroutines-core-jvm` 1.9.0 | Apache-2.0 (documented previously; license for the locked version not re-verified) |
| kotlinx.serialization | `kotlinx-serialization-core`, `kotlinx-serialization-core-jvm` 1.7.3 | To verify |
| kotpass (vendored source) | KDBX engine, version 0.13.0; includes Kotlin XML Builder 1.9.3, parts of Apache Commons Lang 3.17.0 and cryptographic source ported from Bouncy Castle | MIT; bundled XML Builder and Commons Lang Apache-2.0; Bouncy Castle License (MIT text) |
| Okio | `okio`, `okio-jvm` 3.15.0 | Apache-2.0 |
| Guava | `listenablefuture` 1.0 | To verify |
| JetBrains | `annotations` 23.0.0 | To verify |
| JSpecify | `jspecify` 1.0.0 | To verify |

JUnit 4 is test-only and does not appear in the release configurations. Build tools are listed separately below. The lockfile also contains androidx.compose:compose-bom 2025.09.00, kotlinx-coroutines-bom 1.9.0 and kotlinx-serialization-bom 1.7.3; these are alignment metadata, not APK payloads.

### Explicitly avoided

| Component | Reason |
| --- | --- |
| `androidx.security:security-crypto` | Deprecated by Google in favour of the Android Keystore directly |
| `signalapp/argon2` | GPL-3.0 and archived since 2022 |
| UniFFI | Only needed for a Rust core, which is not chosen ([ADR-0005](decisions/0005-reinforced-kotpass.md)); its MPL-2.0 license is GPL-3.0-compatible |
| Any analytics, crash-reporting or ad SDK | Privacy: KeyNexa has no telemetry |

## Build tools

These run only during the build and are not distributed with the app.

| Tool | License (verified) | Source | Notes |
| --- | --- | --- | --- |
| ktlint | MIT | [pinterest/ktlint](https://github.com/pinterest/ktlint) | Kotlin formatting and style, through the ktlint-gradle plugin |
| detekt | Apache-2.0 | [detekt/detekt](https://github.com/detekt/detekt) | Kotlin static analysis |
| Gradle License Report | Apache-2.0 (`LICENSE.txt`) | [jk1/Gradle-License-Report](https://github.com/jk1/Gradle-License-Report) | 3.1.4; the `generateLicenseReport` task |

## Process for adding a dependency

1. Justify the need in the pull request or commit message.
2. Read its `LICENSE` file at the exact version and check its transitive dependencies.
3. Add it to this document (both languages) and to [CREDITS.md](../CREDITS.md) in the same commit.
4. CI runs a license report and a vulnerability scan on every push and pull request.

## Pending legal questions

- Confirm whether KeePassXC-style merge semantics re-implemented from public documentation raise any concern (expected: no, behaviour is not copyrightable; no code is copied).
- Keep the exact Bouncy Castle and Apache notices from kotpass in the app's "About" screen and in CREDITS when the dependency is added.
