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

## Android and desktop dependencies

The project skeleton (phase C1) already uses Kotlin, AndroidX Core, AndroidX Activity, Compose (UI and Material 3) and JUnit; the remaining rows are still planned.

| Component | License | Source | Notes |
| --- | --- | --- | --- |
| Kotlin stdlib, kotlinx.coroutines | Apache-2.0 | JetBrains | *License of coroutines not re-verified for this document* |
| Jetpack Compose, AndroidX (activity, lifecycle, navigation) | Apache-2.0 | Google Maven POMs | |
| JUnit 4 | EPL-1.0 | [junit-team/junit4](https://github.com/junit-team/junit4) | Unit tests only; not distributed with the app |
| `androidx.biometric` | Apache-2.0 | POM of `biometric-1.1.0` | |
| Compose Multiplatform (desktop, future) | Apache-2.0 | [JetBrains/compose-multiplatform](https://github.com/JetBrains/compose-multiplatform) | Stable 1.12.1 (2026-09-22) |
| Bouncy Castle (only if needed beyond kotpass) | Bouncy Castle License (MIT text) | [bc-java LICENSE.md](https://github.com/bcgit/bc-java) | 1.86 (2026-09-11) |
| argon2kt (optional, native Argon2 on Android) | MIT; bundled reference Argon2 C code is CC0-1.0 or Apache-2.0 | [lambdapioneer/argon2kt](https://github.com/lambdapioneer/argon2kt) | Only if benchmarks justify it; last release 1.6.0 (2024-09-06) |
| Inter or Manrope (font) | SIL OFL 1.1 | [rsms/inter](https://github.com/rsms/inter), [google/fonts](https://github.com/google/fonts/tree/main/ofl/manrope) | Final choice in the design system |
| Material Symbols (icons) | Apache-2.0 (per repository README; *exact Symbols license file not verified*) | [google/material-design-icons](https://github.com/google/material-design-icons) | Alternatives: Lucide (ISC), Phosphor (MIT) |

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
