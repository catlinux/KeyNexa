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
| `./gradlew :app-android:assembleDebug` | Builds the debug APK at `app-android/build/outputs/apk/debug/app-android-debug.apk` |

These commands were verified locally on Windows with JDK 17 (Temurin) and Android SDK 36.

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

Do not invent checksums by hand. CI (build, tests, lint, license report, vulnerability scan) is planned for phase C3; see [Roadmap](ROADMAP.md).

## Test data

- Test vaults live in `testdata/` and contain only fictitious entries; their passwords are documented next to them.
- Never commit real vaults, real passwords, key files you use, or signing keys.

## Continuous integration and releases (planned)

- GitHub Actions: build, tests, lint and license/vulnerability checks on every push and pull request.
- Release workflow: triggered by a `vX.Y.Z` tag; builds and signs the APK with secrets stored in the repository settings, publishes it with checksums as a GitHub release (pre-release before 1.0).
