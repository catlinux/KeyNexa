# Security review

[English](SECURITY_REVIEW.md) | [Español](es/SECURITY_REVIEW.md)

Final review before 1.0 (step B3 of the roadmap and the code part of step Z). Done on 2026-10-18 on version 0.33.3; the fixes it led to are in 0.33.4 to 0.33.9.

## Scope and method

- **Code:** every module of the repository: `core-vault`, `core-kdbx`, `core-sync`, `platform-android`, `platform-cloud`, `app-android` (main, `offline` and `cloud` sources), the vendored `third_party/kotpass` (only where it touches untrusted input and the local changes), and the CI and release workflows. About 15,000 lines of KeyNexa code and 10,000 of vendored code.
- **How:** a manual read by risk (secrets in memory and on disk, safe save and recovery, cloud, Android surface, vendored parser, supply chain) and automated searches over the whole code for logging, `http://`, insecure randomness, exported components, temporary or world-readable files and passwords held as `String`. Every fix came with a test, and a test that failed before the fix where that was possible.
- **Device:** a Pixel 10 Pro (Android 17) over adb with the fictitious vault `prova-nube`: unlocking with the keypad and the system keyboard, wiping on background, background lock, cloud copy to Backblaze B2, recovery of a stranded unsaved copy, large text. The connection dropped during the review; the checks still pending are listed below.
- **Who:** an AI model (Claude Opus) working for the owner. It is a documented review, **not an independent external audit**; the roadmap asks for one of the two before 1.0, and a second, independent opinion (another model or a person) is recommended.

## Findings fixed

| # | Severity | Area | Finding | Fixed in |
| --- | --- | --- | --- | --- |
| R1 | Medium | Cloud | Two vaults with the same name shared one cloud folder and one "copies to keep" count, so uploads of one could rotate away every copy of the other. | 0.33.4 |
| R2 | Medium (JVM), low (Android) | Vendored parser | The XML parser bundled with kotpass resolved external entities on the JVM (XXE). A hostile XML key file could read a local file into the key or reach the network. `THREAT_MODEL` T11 already claimed this was disabled; it was not. Android's parser did not load them, but the desktop clients will run on the JVM. | 0.33.7 |
| R3 | Medium | Release | The release job ran the build (Gradle, plugins, dependencies, third-party actions pinned by tag) on the same machine where it later decoded the signing key, and it did not check that the offline APK has no Internet permission, a check the plan assumed. | 0.33.6 (proved by the next tag) |
| R4 | Low to medium | Sync | A conflict copy's unencrypted header could ask for a huge key derivation within the engine's absolute limits; it was decrypted on every open on the thread that also saves. | 0.33.5 |
| R5 | Low | Keypad | With a screen reader on, the KeyNexa keypad would speak every key, while the system keyboard follows Android's setting not to speak passwords. | 0.33.3 |
| R6 | Low | Save | A save that failed cleanly left its private unsaved copy unseen after the app was closed, and copies of failed attempts were never deleted after a later save succeeded. | 0.31.0 |
| R7 | Low | Entry | *Open URL* was offered for any address but only http and https open; a phone without a browser would crash on it. | 0.33.8 |
| R8 | Low | Accessibility | Buttons with a fixed height cut their label with large text (*Unlock with biometrics*). | 0.33.1 |
| R9 | Info | CI | The intermittent failure of the keepass-rs job was Gradle dependency verification of a POM without checksum, not an interoperability difference. | 0.33.9 |

Also hardened during the same days, before this review: touches are ignored under other apps' windows (0.30.5), the unlock screen quietly mentions a keyboard or accessibility service that does not come with the system (0.31.0), and master passwords are typed with the KeyNexa keypad (0.32.0 to 0.33.0; [ADR-0010](decisions/0010-in-app-password-keypad.md)).

## Accepted risks and open points

| # | Severity | Point | Decision |
| --- | --- | --- | --- |
| A1 | Low | The cloud secret key is held as a `String` while in use, and the serialized credentials are not wiped after sealing. | Accepted: they are encrypted at rest with a non-exportable Keystore key; JVM strings cannot be wiped reliably anyway. |
| A2 | Low | The S3 answer check for `<!DOCTYPE` reads the bytes as Latin-1, so a UTF-16 document would pass it. | Accepted: the server is the one the user configured, over HTTPS, and Android's parser does not load external entities; the parser hardening still applies. |
| A3 | Low | The size of an S3 answer is not bounded. | Accepted: same trust as A2. |
| A4 | Info | Cloud object names show the sanitised vault name (T20). | Documented; the user can choose a neutral name. |
| A5 | Info | `ci.yml` and `fuzz.yml` use actions pinned by tag. | Accepted for now: those jobs hold no secrets. Pin them when the tooling is updated. |
| A6 | Low | `BiometricStore.save` is not atomic (from the 0.10.2 review). | Accepted: a broken blob ends in the password prompt and is deleted. |
| A7 | Residual | An accessibility service or a compromised system can still read what is typed or shown (T22). | Out of scope for an app; mitigated by the quiet notice, the key file option and 2FA. |

## Checks still to do on a phone

Pending because the adb connection dropped, or because they need conditions the review could not create. Each is covered by unit tests.

- Killing the app in the middle of a save, then reopening (recovery of the interrupted save).
- The keypad in the "other password" dialog of a conflict copy.
- Installing one build variant over the other.
- That a screen reader switches the master password fields to the system keyboard.
- Biometric unlock on a phone without StrongBox and on API 26–29; Argon2 on a modest phone.
- Clearing an entry's history.

## Verdict

No high-severity finding is open. Before 1.0: run the phone checks above, push the first tag to prove the new release workflow, and get the independent review the roadmap asks for.
