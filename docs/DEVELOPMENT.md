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
| `./gradlew test` | Runs the unit tests of every module |
| `./gradlew :app-android:assembleDebug` | Builds the debug APK at `app-android/build/outputs/apk/debug/app-android-debug.apk` |

These commands were verified locally on Windows with JDK 17 (Temurin) and Android SDK 36.

## Module layout

Dependencies point downwards only; the `core-*` modules are pure Kotlin/JVM and do not import Android. See [Architecture](ARCHITECTURE.md).

## Quality tooling (planned in phase C)

- Formatting and style: ktlint (or Spotless with ktlint).
- Static analysis: Android Lint (with warnings as errors for security and i18n checks) and detekt.
- Tests: JUnit for `core-*` modules on the JVM; Compose UI and instrumented tests for `app-android`.
- Dependency security: Gradle dependency verification, plus a vulnerability scanner in CI.
- License report generated in CI and compared with [Dependencies and licenses](DEPENDENCIES_AND_LICENSES.md).

## Test data

- Test vaults live in `testdata/` and contain only fictitious entries; their passwords are documented next to them.
- Never commit real vaults, real passwords, key files you use, or signing keys.

## Continuous integration and releases (planned)

- GitHub Actions: build, tests, lint and license/vulnerability checks on every push and pull request.
- Release workflow: triggered by a `vX.Y.Z` tag; builds and signs the APK with secrets stored in the repository settings, publishes it with checksums as a GitHub release (pre-release before 1.0).
