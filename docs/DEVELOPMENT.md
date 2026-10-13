# Development

[English](DEVELOPMENT.md) | [Español](es/DEVELOPMENT.md)

## Requirements

- JDK 17 (Temurin is what we use locally). The core modules and Android builds use a Java 17 toolchain.
- Android SDK with platform 36 and build-tools 36.0.0. Point Gradle at it with `local.properties` (`sdk.dir=...`, git-ignored) or the `ANDROID_HOME` environment variable.
- No local Gradle installation: the committed wrapper downloads Gradle 8.14.

## Toolchain

| Tool | Version |
| --- | --- |
| JDK | 17 (Temurin) |
| Gradle | 8.14 (wrapper) |
| Android Gradle Plugin | 8.13.1 |
| Kotlin | 2.2.21 |
| Compose BOM | 2025.09.00 (Material 3) |
| Android SDK | compileSdk/targetSdk 36, minSdk 26 |

minSdk 26 gives the autofill framework and `java.time` without desugaring.

## Building and testing

Run these from the repository root. On Windows use `gradlew.bat` instead of `./gradlew`.

| Command | What it does |
| --- | --- |
| `./gradlew build` | Compiles every module, runs the unit tests and lint |
| `./gradlew check` | Runs the quality checks (unit tests, Android Lint, detekt, ktlint) |
| `./gradlew test` | Runs the unit tests of every module |
| `./gradlew :core-kdbx:test` | Runs the KDBX engine tests against the vault corpus in `testdata/` |
| `./gradlew :app-android:assembleDebug` | Builds the debug APK at `app-android/build/outputs/apk/debug/app-android-debug.apk` |

These commands were verified locally on Windows with JDK 17 (Temurin) and Android SDK 36.

### KDBX engine tests

`core-kdbx` tests the engine against the fictitious vaults of `testdata/` ([Test vaults](TEST_VAULTS.md)):

- Corpus (AC1): every vault opens with its credentials, reports the expected format and holds the expected content.
- Errors: wrong password, key file problems, foreign data, and truncated or altered files. A damaged KDBX 4 file is reported as damaged, never as a wrong password.
- Round trip (AC2): decode, encode and decode again give the same vault for every vault and for every cipher and key derivation combination.
- Differential (AC2): the engine's output is exported to XML with `keepassxc-cli` and compared with the export of the original. It is skipped without error when KeePassXC is not installed.
- Argon2 timing, off by default: `KEYNEXA_PERF=1 ./gradlew :core-kdbx:test --tests '*Argon2Performance*' -i` (see [Benchmarks](BENCHMARKS.md)). `KEYNEXA_TEST_HEAP` sets the test heap (default 2g).

## Module layout

Dependencies point downwards only; the `core-*` modules are pure Kotlin/JVM and do not import Android. See [Architecture](ARCHITECTURE.md).

## Quality checks

`./gradlew check` runs all of these and fails on the first problem:

| Command | What it checks |
| --- | --- |
| `./gradlew check` | Every check below (unit tests, Android Lint, detekt, ktlint) |
| `./gradlew test` | Unit tests of every module |
| `./gradlew lint` | Android Lint in `platform-android` and `app-android` |
| `./gradlew detekt` | Kotlin static analysis |
| `./gradlew ktlintCheck` | Kotlin code style |
| `./gradlew ktlintFormat` | Applies ktlint's automatic fixes to the sources |

Configuration:

- **Android Lint**: `config/lint/lint.xml`, shared by both Android modules. The modules set `warningsAsErrors = true` and `abortOnError = true`; `HardcodedText`, `MissingTranslation`, `ExtraTranslation` and the security checks (`SetJavaScriptEnabled`, `TrustAllX509TrustManager`, `WorldReadableFiles`, and the rest) are errors.
- **detekt**: `config/detekt/detekt.yml`, on top of detekt's defaults.
- **ktlint**: `.editorconfig` at the repository root.

### Dependency verification

`gradle/verification-metadata.xml` pins the SHA-256 checksum of every dependency, plugin and tool (including ktlint and detekt) with `verify-metadata=true`. After changing a dependency, the toolchain or the plugin versions, regenerate it with the tasks that resolve the new artifacts, review the diff and commit it:

```sh
./gradlew --write-verification-metadata sha256 check build
```

Do not invent checksums by hand.

## Continuous integration

[GitHub Actions](../.github/workflows/ci.yml) runs on every push to `master` and on every pull request, with `contents: read` and a concurrency group that cancels superseded runs of the same ref. It has five jobs:

| Job | What it does |
| --- | --- |
| `build` | Sets up JDK 17 and Android SDK 36, then runs `./gradlew build check`; on failure it uploads the lint, detekt and test reports as an artifact |
| `docs-i18n` | Runs `bash scripts/check-docs-i18n.sh` |
| `licenses` | Runs `./gradlew generateLicenseReport` and uploads `build/reports/dependency-license` |
| `vulnerabilities` | Scans only the distributed runtime dependencies with OSV-Scanner, from the `gradle.lockfile` files, and fails on known vulnerabilities |
| `build-tooling-vulnerabilities` | Informational (it never fails the build): scans `gradle/verification-metadata.xml`, where the build tools appear; they are not distributed with the app |

Run the same checks locally:

```sh
bash scripts/check-docs-i18n.sh
./gradlew generateLicenseReport
```

`scripts/check-docs-i18n.sh` fails if an English document has no Spanish translation or the other way round, or if a document has no `[English](…) | [Español](…)` language line in its first six lines. Translations live under `docs/es/` mirroring the English paths; the only exception is `README.es.md`, next to `README.md`.

### Dependency locking

Dependency locking is active only for the runtime configurations that ship with the app (`runtimeClasspath` in the JVM modules and `releaseRuntimeClasspath` in the Android ones) and in `STRICT` mode, so the `gradle.lockfile` files under each module contain only distributed dependencies. `./gradlew build check` fails if a lock file is out of date. After changing a runtime dependency, regenerate the locks, review the diff and commit it together with `gradle/verification-metadata.xml`:

```sh
./gradlew build --write-locks
```

## Test data

- Test vaults live in `testdata/` and contain only fictitious entries; their passwords are documented next to them.
- Never commit real vaults, real passwords, key files you use, or signing keys.

## Releases

[`release.yml`](../.github/workflows/release.yml) runs when a tag `vX.Y.Z` is pushed. It stops unless the tag equals `v` + the contents of `VERSION` and `CHANGELOG.md` has an entry for that version. Then it runs `./gradlew build check :app-android:assembleRelease`, aligns the APK with `zipalign`, signs it with `apksigner` using the repository secrets, verifies the signature, and publishes a GitHub release with the APK, its SHA-256 file and the changelog entry as notes. Versions `0.y.z` are published as pre-releases. The keystore is decoded to the runner's temporary directory and removed at the end of the job; the passwords reach `apksigner` through environment variables, never on the command line.

To publish: bump `VERSION` and `CHANGELOG.md`, commit, then create and push the tag (`git tag vX.Y.Z && git push origin vX.Y.Z`). Both the push and the tag need the maintainer's approval.

### Signing key and secrets

Create the keystore once, outside the repository, and keep a backup: if it is lost, installed copies can no longer be updated.

```sh
keytool -genkeypair -v -keystore keynexa-release.jks -alias keynexa   -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 keynexa-release.jks > keynexa-release.jks.base64
```

Then add four secrets under *Settings → Secrets and variables → Actions* of the GitHub repository:

| Secret | Content |
| --- | --- |
| `KEYSTORE_BASE64` | The contents of `keynexa-release.jks.base64` |
| `KEYSTORE_PASSWORD` | The keystore password |
| `KEY_ALIAS` | The key alias (`keynexa`) |
| `KEY_PASSWORD` | The key password |

Delete the `.base64` file afterwards. Never commit the keystore, the passwords or the base64 text.

Verify a downloaded APK with `apksigner verify --print-certs keynexa-X.Y.Z.apk` and `sha256sum -c keynexa-X.Y.Z.apk.sha256`.
