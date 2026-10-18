# ADR-0002: KDBX engine

[English](0002-kdbx-engine.md) | [Español](../es/decisions/0002-kdbx-engine.md)

- **Status:** Accepted
- **Date:** 2026-10-13

## Context

KeyNexa must read and write KDBX 3.1, 4.0 and 4.1 without implementing its own cryptography, with interoperability with KeePassXC and other clients. The comparison of candidates is in [Dependencies and licenses](../DEPENDENCIES_AND_LICENSES.md).

## Options

1. **kotpass** (MIT, JVM): full cipher/KDF coverage, one runtime dependency, sample-file tests; single maintainer, no release since 2025-09-28.
2. **KeePassJava2** (Apache-2.0): active, but no Twofish, Argon2d only, new breaking 3.x API and recent attachment-integrity bugs.
3. **keepass-rs** (MIT, Rust): active, but writing is experimental and would require a Rust/UniFFI toolchain.
4. **Own KDBX implementation** on Bouncy Castle: full control, but large, risky and against the "no custom crypto code" principle.

## Decision

Use **kotpass**, isolated in the `core/kdbx` module behind KeyNexa's `KdbxEngine` interface.

## Consequences

- Only `core/kdbx` depends on kotpass; replacing or forking it does not affect the rest.
- Phase C/D must prove, with automated tests on a fixture corpus created by KeePassXC and KeePass 2.x:
  - round-trip preservation of groups, entries, custom fields, attachments, history, icons, custom data and deleted objects;
  - behaviour with unknown XML elements (probably dropped; if so, document it or contribute a fix);
  - Argon2 performance on a low-end device with large memory parameters; if unacceptable, plug in a native KDF through kotpass's `BaseKdfProvider` (argon2kt, MIT).
- If the maintainer stays inactive and a fix is needed, KeyNexa forks kotpass (MIT allows it) keeping its notices.
- KeePassJava2 remains the fallback candidate.
