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

The code is identical to the upstream commit apart from the changes below. Changes made in KeyNexa are recorded here, newest first, each with its reason; they stay under the MIT license so they can be offered upstream.

- **Tell damaged files from wrong keys; missing `throw`.** In KDBX 4 only the header HMAC proves the key; a later AES padding failure while decrypting the content (`database/Decoder.kt`) now raises `FormatError.InvalidContent` instead of `CryptoError.InvalidKey`, and an invalid AES-KDF seed (`cryptography/format/AesKdf.kt`) raises `FormatError.InvalidHeader`. Before, a truncated or tampered file was reported as a wrong key, which made a partial synchronisation transfer look like a wrong password. In KDBX 3.x the check of the stream start bytes built `FormatError.InvalidContent` but never threw it (upstream bug); it now throws `CryptoError.InvalidKey`. In 3.x a wrong key and a cut file are still indistinguishable, because that format has no integrity check before decryption.

- **Public header reader, and Okio exposed as `api`.** `database/Decoder.kt` gains `KeePassDatabase.readHeader`, a public function that reuses the internal `DatabaseHeader.readFrom` and checks the signature, so a caller can inspect a file's format without deriving the key. `build.gradle.kts` now declares Okio as `api` instead of `implementation`: the models and the header already expose `okio.ByteString` in their public API, so a consumer could not compile against them otherwise. Needed by KeyNexa's `KotpassKdbxEngine`.

- **Keep unknown XML elements.** `xml/Entry.kt`, `xml/Group.kt`, `xml/Meta.kt` and `xml/DefaultXmlContentParser.kt` silently ignored the elements they did not recognise inside `Meta`, `Group`, `Entry` (history included) and the document root, so rewriting a database dropped whatever newer KeePass/KeePassXC versions had added there. `Entry`, `Group`, `Meta` and `DatabaseContent` now keep them in a new `unknownElements` field (`unknownRootElements` for the children of `Root`) and the parser writes them back. Limitation: a `Protected="True"` value inside an unknown element is not decrypted and re-encrypted with the inner stream, so it may not survive a binary round trip; no upstream element nests protected values inside unknown ones today.

- **Do not write a modification time that was absent.** `xml/CustomIcons.kt` and `xml/CustomData.kt` wrote `LastModificationTime` (year 1, zero ticks) for every custom icon and custom-data item of a KDBX 4.1 file even when the original had none. They now omit the element when the value is null. Found by comparing KeePassXC XML exports of the corpus before and after a round trip.

## Code style

This directory is excluded from ktlint and detekt so that it stays comparable with upstream.
