# ADR-0006: Biometric unlock

[English](0006-biometric-unlock.md) | [Español](../es/decisions/0006-biometric-unlock.md)

- **Status:** Accepted
- **Date:** 2026-10-13
- **Implements:** threat T9 of the [threat model](../THREAT_MODEL.md); phase D6 of the roadmap

## Context

Typing a long master password on a phone every time is the main reason people pick weak ones. Biometric unlock must therefore exist, but it must never become the weakest way into the vault. Constraints:

- KeyNexa writes a new master seed and KDF salt on every save, so a derived (post-KDF) key goes stale after each save; only something taken before the key derivation stays valid.
- Android Keystore can bind a key to biometric authentication, per use, and invalidate it when new biometrics are enrolled (`setInvalidatedByBiometricEnrollment`).
- `minSdk` is 26: the per-use biometric binding API (`setUserAuthenticationParameters`) exists only from API 30; on 26-29, a validity of `-1` seconds gives the same per-use behaviour.

## Options

1. **Store the master password encrypted with a Keystore key.** Simple, but the plaintext password exists again after decryption, and it may be reused on other services.
2. **Store the composite-key components** (the SHA-256 of the password and the processed key-file data, which is what KDBX hashes before the key derivation) encrypted with a Keystore key bound to biometrics. The password itself is never stored. The key derivation still runs on each unlock.
3. **Store the post-KDF key.** Fastest unlock, but invalid after every save (new salt), and it skips the protection the key derivation gives against a stolen phone and a leaked Keystore.
4. **Device credential (PIN, pattern) as a fallback.** Weaker than the master password on most devices.

## Decision

Option 2, without device-credential fallback.

1. **One Keystore key per vault:** AES-256-GCM, no padding, alias derived from a hash of the vault's location. Required flags: `setUserAuthenticationRequired(true)`; per-use authentication (`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` on API 30+, `setUserAuthenticationValidityDurationSeconds(-1)` before); `setInvalidatedByBiometricEnrollment(true)`; `setUnlockedDeviceRequired(true)` on API 28+; `setIsStrongBoxBacked(true)` when the device reports `FEATURE_STRONGBOX_KEYSTORE`, falling back to the TEE if key generation fails.
2. **What is wrapped:** the composite-key components, never the password. `core/vault` gains a way to build `Credentials` from those components, and kotpass a small public constructor for pre-hashed credentials (a local change recorded in its README).
3. **Enrolment** only right after a successful password unlock, by explicit opt-in: initialise the cipher for encryption, authenticate with `BiometricPrompt` + `CryptoObject` (`BIOMETRIC_STRONG` only), encrypt, store ciphertext and IV in `noBackupFilesDir`.
4. **Unlock:** initialise the cipher for decryption with the stored IV, authenticate with `BiometricPrompt` + `CryptoObject`, decrypt, open the vault through the normal path (key derivation included), and wipe the decrypted components immediately.
5. **Invalidation:** `KeyPermanentlyInvalidatedException` (new biometrics enrolled, screen lock removed), a `WrongCredentials` result with the stored components (the password changed on another device), or a decryption failure delete the key and the stored blob and fall back to the password, telling the user why.
6. **Periodic password:** the master password is required again after 7 days without typing it (configurable from 1 to 30 days) and after the stored blob is missing or invalid. The timestamp lives next to the blob.
7. **Nothing else uses the decrypted components:** no logs, no copies, no autofill path; they exist only for the duration of one unlock.

## Consequences

- An attacker with the unlocked phone and the owner's finger can open the vault, as with any biometric unlock; an attacker without biometrics needs the master password. A stolen phone with a leaked Keystore still faces the full key derivation, because the post-KDF key is never stored.
- A biometric unlock takes as long as the key derivation (seconds with Argon2 on a phone); the UI shows progress.
- Changing biometrics, removing the screen lock or changing the password elsewhere always ends in a password prompt; the UI must explain this instead of failing silently.
- The design needs a real-device test matrix (with and without StrongBox, API 26-29 and 30+) before release.
