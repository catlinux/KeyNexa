# ADR-0003: Platform and modules

[English](0003-platform-and-modules.md) | [Español](../es/decisions/0003-platform-and-modules.md)

- **Status:** Accepted
- **Date:** 2026-10-13

## Context

Android is the first platform; Windows and Linux will follow. The KDBX engine (ADR-0002) is JVM-only. Android security features (Keystore, BiometricPrompt, autofill, credential provider, `FLAG_SECURE`) must be first-class.

## Options

1. Kotlin/JVM core + Jetpack Compose (Android) + Compose Multiplatform Desktop later.
2. Rust core + UniFFI + native UIs per platform.
3. Kotlin Multiplatform core (requires a KMP KDBX engine that does not exist today).
4. Cross-platform UI frameworks (Flutter, React Native).

## Decision

Option 1. Gradle multi-module project: `core/vault`, `core/kdbx`, `core/sync` (pure Kotlin/JVM, no Android imports), `platform/android`, `apps/android`. Details in [ARCHITECTURE](../ARCHITECTURE.md). minSdk 26, compile/target SDK 36.

## Consequences

- Core logic is unit-tested on the JVM, fast and without devices.
- A desktop client reuses the core modules on the JVM; only storage, secret storage and UI are new.
- The core could later move to KMP if a KMP KDBX engine appears; keeping core modules free of JVM-only APIs where cheap makes that easier.
- iOS is not a goal; this choice does not support it without further work.
