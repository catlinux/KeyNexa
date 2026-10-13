# Development

[English](DEVELOPMENT.md) | [Español](es/DEVELOPMENT.md)

> Status: there is no code yet. Build and test commands will be added here, verified, when the project skeleton exists (phase C).

## Planned toolchain

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 17 (Temurin) | Required by the Android Gradle Plugin |
| Android SDK | compileSdk/targetSdk 36, minSdk 26 | minSdk 26: autofill framework and `java.time` without desugaring |
| Gradle | Wrapper committed in the repository | Kotlin DSL and version catalog (`gradle/libs.versions.toml`) |
| Kotlin | Current stable at the time of phase C | |

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
