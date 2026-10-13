# Changelog

[English](CHANGELOG.md) | [Español](docs/es/CHANGELOG.md)

All notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.3.1] - 2026-10-13

### Added

- Mutation fuzzing of the decoder (phase C5b, first part): randomly damaged copies of corpus vaults may only fail with a `VaultException`, within a time limit. Nine thousand mutations ran clean.
- The CI installs KeePassXC so the differential test runs there.

### Security

- The engine refuses key derivation settings beyond sane limits (Argon2 memory above 2 GiB, more than 10000 iterations or 256 lanes, more than 500 million AES-KDF rounds) before doing any work: the parameters sit in the unencrypted header, so a damaged or hostile file could otherwise freeze or crash the app.

## [0.3.0] - 2026-10-13

### Added

- KDBX engine (phase C5). `core-vault` defines the domain model (vault, groups, entries, history, attachments, custom data, unknown XML), the `KdbxEngine` and `StoragePort` interfaces, `Credentials` (wipeable secrets) and `VaultException`. `core-kdbx` implements the engine on kotpass: `KotpassKdbxEngine` with a complete two-way mapping, so a load and a save lose no data, and with failures translated to `VaultException` without secrets in the messages.
- kotpass 0.13.0 (upstream commit `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945`) imported as the Gradle module `:kotpass` in `third_party/kotpass/`, with Okio 3.15.0 as its only dependency; credits for kotpass, Okio and the notices bundled in it.
- Acceptance tests AC1 and AC2 against the 18-vault corpus: opening, expected content, errors, round trips for every cipher and key derivation, and a differential comparison with `keepassxc-cli`.
- `docs/BENCHMARKS.md`: Argon2 timings of the pure-Kotlin implementation on a desktop JVM, and the pending phone measurement.

### Changed

- Local fixes in the vendored kotpass, listed in its README: unknown XML elements are kept and written back; a custom icon or custom-data item without a modification time no longer gets a bogus one; a truncated or tampered KDBX 4 file is reported as damaged instead of as a wrong key; the KDBX 3.x stream-start check now actually throws (upstream bug); a public header reader.
- ktlint and detekt share a 120-character line limit.

## [0.2.9] - 2026-10-13

### Fixed

- Dependency verification now also covers the Linux and macOS variants of `aapt2`, which the Linux CI runner needs; the metadata had only the Windows one.

### Added

- `scripts/ci-gradle-error.sh`: the CI publishes the "What went wrong" block of a failed Gradle run as an annotation, readable without signing in.

## [0.2.8] - 2026-10-13

### Added

- Three more test vaults saved from KeePassXC's interface: Twofish with Argon2d, ChaCha20 with Argon2id, and Argon2d with a tag and a TOTP field. The corpus in `testdata/` is now complete (18 vaults).

## [0.2.7] - 2026-10-13

### Fixed

- The CI and release workflows now locate `sdkmanager` under `ANDROID_HOME` instead of relying on the `PATH`, where the runner does not provide it (exit code 127).

## [0.2.6] - 2026-10-13

### Added

- Fictitious test-vault corpus in `testdata/` (15 vaults and 3 key files) written by KeePass 2.61 and KeePassXC 2.7.12: AES-256 and ChaCha20, AES-KDF, Argon2d and Argon2id, KDBX 3.1, 4.0 and 4.1, key files, Unicode password, history, attachments, icons, tags and a 1 GiB Argon2 vault.
- Generator scripts and header inspector in `tools/testdata/`, documented in `docs/TEST_VAULTS.md` together with the three vaults that still need KeePassXC's graphical interface.

## [0.2.5] - 2026-10-13

### Changed

- The CI vulnerability scan now covers only the dependencies distributed with the app, through Gradle dependency locking (the `gradle.lockfile` files of the runtime configurations). Vulnerabilities in build tooling are reported by a new, non-blocking job.

## [0.2.4] - 2026-10-13

### Added

- `docs/TEST_VAULTS.md`: list of fictitious vaults to create with KeePassXC and KeePass 2.x for the compatibility corpus.

### Fixed

- The Android SDK licence step in the CI and release workflows no longer fails with SIGPIPE under `pipefail`.

## [0.2.3] - 2026-10-13

### Added

- Release workflow (`.github/workflows/release.yml`): on a `vX.Y.Z` tag it checks the tag against `VERSION` and `CHANGELOG.md`, builds the release APK, signs it with the repository secrets, and publishes a GitHub release (pre-release before 1.0) with the APK and its SHA-256 file.
- Documentation of how to create the signing keystore and the four repository secrets.

## [0.2.2] - 2026-10-13

### Added

- Continuous integration with GitHub Actions (`.github/workflows/ci.yml`): build and quality checks, bilingual-documentation check, dependency-license report and an OSV vulnerability scan.
- `scripts/check-docs-i18n.sh`, which fails when an English document has no Spanish translation or vice versa, or when a language line is missing.
- The Gradle License Report plugin, wired into `generateLicenseReport` for all subprojects.

## [0.2.1] - 2026-10-13

### Added

- Quality checks wired into `./gradlew check`: ktlint (with `ktlintFormat` to auto-correct), detekt and hardened Android Lint in both Android modules, where `HardcodedText`, `MissingTranslation`, `ExtraTranslation` and the security checks are errors.
- Gradle dependency verification: every dependency, plugin and tool is verified against the SHA-256 checksums in `gradle/verification-metadata.xml`.

## [0.2.0] - 2026-10-13

### Added

- Multi-module Gradle skeleton (phase C1): the Kotlin/JVM 17 modules `core-vault`, `core-kdbx` and `core-sync`, the Android library `platform-android` and the Compose + Material 3 application `app-android`, with a version catalog and a trivial JUnit test per module.
- `app-android` takes its `versionName` from `VERSION` and derives `versionCode` from it; the manifest disables backups and declares no permission, and the interface strings are provided in `en-US` and Spanish only through string resources.
- Verified build, test and packaging instructions in `docs/DEVELOPMENT.md`.

## [0.1.2] - 2026-10-13

### Added

- ADR-0005: kotpass is kept, vendored as a module and reinforced (differential tests against KeePassXC and keepass-rs, fuzzing, XML-preservation fixes, optional native Argon2), after re-evaluating kotpass, KeePassJava2, keepass-rs and kdbxweb.

## [0.1.1] - 2026-10-13

### Changed

- Spanish translations moved to `docs/es/`, mirroring the English structure; only `README.es.md` stays in the root. Language links and the bilingual rules in CONTRIBUTING updated.

## [0.1.0] - 2026-10-13

### Added

- Research and architecture phase (phases A and B): architecture, threat model, dependency and license research, roadmap with MVP acceptance criteria, development plan and architecture decision records 0001–0004.
- License: GPL-3.0-only with additional attribution and trademark terms (section 7) in `NOTICE`; `CREDITS.md`.
- Project documentation in English and Spanish: README, CONTRIBUTING, SECURITY, CHANGELOG, TODO.
- `VERSION` file.
