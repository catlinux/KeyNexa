# kotpass (vendored)

[English](README.md) | [Español](README.es.md)

Copy of [kotpass](https://github.com/keemobile/kotpass), a Kotlin/JVM KDBX library by Denis T., licensed under the MIT license (see [LICENSE.txt](LICENSE.txt)). KeyNexa vendors and maintains it as decided in [ADR-0005](../../docs/decisions/0005-reinforced-kotpass.md).

- **Upstream:** https://github.com/keemobile/kotpass
- **Upstream commit:** `4e0bd6b6b13e59d48ebe0bd9c06de3f918de6945` (version 0.13.0, 2025-09-28)
- **Imported:** `kotpass/src/main` only. Not imported: tests and their sample vaults, and the publishing, signing, documentation and update-checking build configuration. The `build.gradle.kts` here belongs to KeyNexa.
- **Dependency:** Okio 3.15.0 (Apache-2.0), from the version catalog.
- **Only `core-kdbx` may depend on this module.**

## Bundled third-party code

Kept untouched with their notices:

| Path | Component | License file |
| --- | --- | --- |
| `src/main/kotlin/org/redundent/kotlin/xml/` | Kotlin XML Builder | `LICENSE.txt` in that directory (Apache-2.0) |
| `src/main/java/org/apache/commons/lang3/` | Apache Commons Lang (parts) | `LICENSE.txt` and `NOTICE.txt` in that directory (Apache-2.0) |
| `src/main/kotlin/app/keemobile/kotpass/cryptography/` (eight files) | Ported from Bouncy Castle | Bouncy Castle License header in each file |

## Local changes

The code is identical to the upstream commit. Changes made in KeyNexa are recorded here, newest first, each with its reason; they stay under the MIT license so they can be offered upstream.

_(none yet)_

## Code style

This directory is excluded from ktlint and detekt so that it stays comparable with upstream.
