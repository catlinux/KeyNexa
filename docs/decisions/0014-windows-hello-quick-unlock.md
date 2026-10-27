# ADR-0014: Quick unlock with Windows Hello on the desktop

[English](0014-windows-hello-quick-unlock.md) | [Español](../es/decisions/0014-windows-hello-quick-unlock.md)

- **Status:** Accepted (spike and hand test with the real PIN passed on 2026-10-19; implemented in 1.9.2–1.10.0)
- **Date:** 2026-10-19
- **Extends:** [ADR-0006](0006-biometric-unlock.md) (biometric unlock on Android), [ADR-0012](0012-windows-desktop-client.md) (Windows desktop client)
- **Implements:** threat T28 of the [threat model](../THREAT_MODEL.md)

## Context

The desktop locks itself often, and that is how it should be: after inactivity (5 minutes by default), on Win+L and on suspend (T23). Each time, the user types the long master password again, and that pushes people towards short ones. The owner asked for a quick unlock with the Windows Hello PIN, with these conditions (agreed on 2026-10-19):

- The master password is never written to disk, in any form.
- Only for unlocking again while the app keeps running. The first unlock after starting the app always asks for the master password, and closing the app forgets everything.
- It expires after 8 hours (choices 1, 4, 8 and 24 h).
- The option is hidden when the machine has no Windows Hello (PIN, face or fingerprint).

What it protects against: someone at the unlocked Windows session after KeyNexa has locked itself, for example an unattended PC, or a dump of the process memory taken while KeyNexa is locked. Neither should open the vault without the Hello gesture. What it does not protect against: malware running as the same user while the vault is open (T22, T23). That threat is out of scope already.

Constraints:

- Keeping the secret in memory with nothing around it is not enough: while locked, a memory dump would read it.
- Plain DPAPI is rejected: any process of the same user can open it without the PIN.
- KeyNexa writes a new master seed and KDF salt on every save, so only something taken before the key derivation stays valid (as in ADR-0006).
- No cryptography of our own, and no native binary of our own if JNA is enough (JNA is already a dependency of `platform/windows`).

## Options

1. **Keep the master password or the derived key in memory with nothing around it.** Simple, but a dump of the locked process reads it, and the "PIN" would be a check that no key enforces. Rejected.
2. **A key of the Microsoft Passport key storage provider through NCrypt** (`NCryptCreatePersistedKey` on `Microsoft Passport Key Storage Provider`, `NCryptDecrypt` to unwrap). Pure JNA. However, a key created this way does not ask for the PIN on every use unless an **undocumented** property (`NgcCacheType`) is set. Its only known source is a string inside `cryptngc.dll`, and a Microsoft engineer answered in Q&A that per-use authentication is not offered there. If Windows ignores that property, the unwrap succeeds **without a PIN** and nothing tells us. It fails open. Rejected.
3. **A Windows Hello key credential (`Windows.Security.Credentials.KeyCredentialManager`)**: a documented API, the same one Windows Hello for websites uses. Its key is created in the Hello container (TPM-backed when there is a TPM) and is not exportable. Every `RequestSignAsync` shows the Hello prompt. RSA PKCS#1 v1.5 signatures are deterministic, so signing a random challenge gives the same bytes each time, and a wrapping key can be derived from them. Other open-source password managers use this method. It is a WinRT API, so from the JVM it is called through JNA on its COM ABI (`RoGetActivationFactory`, the interface tables, waiting on `IAsyncOperation`). This is more work than option 2, but if something fails, nothing opens. That is the safe way to fail.
4. **`UserConsentVerifier` alone** (asks for the PIN and returns yes or no). The secret would not depend on the answer: patching one branch in memory skips it. Rejected.

## Decision

Option 3, opt-in, with the secret only in memory.

1. **Hello key:** one key credential per Windows user, named `KeyNexa.QuickUnlock`. It is opened with `OpenAsync`, or created with `RequestCreateAsync` when it does not exist. KeyNexa never reads anything from it except signatures. `IsSupportedAsync` decides whether the option is shown.
2. **What is protected:** the vault's composite-key components (`KdbxEngine.hashedForm`: the SHA-256 of the password and the processed key-file key), never the password itself, as in ADR-0006. The key derivation still runs on every unlock.
3. **Arming** (only after a successful unlock with the master password, with the option on):
   1. Generate a random 32-byte challenge with `SecureRandom`.
   2. `RequestSignAsync(challenge)`: Windows asks for the PIN **once**, right after the password unlock. If the user cancels, quick unlock stays off for this session, without asking again.
   3. Derive the wrapping key: HKDF-SHA256 (RFC 5869, built from the JCA's `HmacSHA256` and checked against the RFC test vectors) over the signature, with the challenge as salt and `KeyNexa quick unlock v1` as info. Wipe the signature.
   4. Encrypt the components with AES-256-GCM (JCA, random 12-byte IV). The vault location and the arming time go in as associated data. Wipe the wrapping key and the plaintext components.
   5. Keep **in memory only**: the challenge, IV, ciphertext, vault location and arming time. Nothing goes to disk, the logs or the clipboard.
4. **Unlock** (unlock screen of the same vault, armed and not expired): offer "Unlock with Windows Hello" and start it once on its own when the screen appears. Sign the stored challenge, derive the key, decrypt, wipe the key, open through the normal path with `Credentials.ofHashes` (key derivation included), and wipe the components. The master password field stays available below.
5. **Disarm** (wipe everything held in memory) when: the app closes; the time limit passes (checked when the unlock screen is shown and once a minute); the user turns the option off; another vault is unlocked; the stored components give `WrongCredentials` (the password changed on another device); decryption fails; or Hello reports an error other than a cancel (key removed, Hello turned off, PIN reset). A **cancel** keeps quick unlock armed and leaves the password field. Hello limits PIN attempts itself (TPM lockout), so KeyNexa adds no counter of its own.
6. **Settings:** "Unlock with Windows Hello" is off by default and shown only when Hello is supported. "Ask for the master password again after" offers 1, 4, 8 and 24 h, default 8. The arming time counts, not the last use: unlocking with the PIN does not extend it.
7. **Prompt in front:** the Hello dialog must appear over the KeyNexa window. The spike checks whether a window handle is needed (`IKeyCredentialManagerInterop` or a foreground hand-off). Without one, Hello cannot be used.
8. **Code layout:** the WinRT/JNA calls go in `platform/windows` (`WindowsHello`: supported, sign challenge). The pure policy goes in `platform/desktop`: arming, expiry, HKDF and AES-GCM, and disarming, behind a `HelloSigner` interface, so it can be tested with a fake signer. The wiring goes in `apps/desktop`.

## Consequences

- With KeyNexa locked and the session open, getting in needs the PIN, face or fingerprint of the Windows user, or the master password. What the quick unlock keeps in memory is only ciphertext, a challenge and an IV. A dump of the process can still hold leftovers of the master password typed with the system keyboard, which the JVM cannot wipe (accepted risk A8 of the [security review](../SECURITY_REVIEW.md)).
- Anyone who knows the Windows PIN can open the vault until the time limit passes. The setting says so in a line. The option is off by default, and its limit is never longer than 24 h.
- Malware running as the user can ask for a signature too. Windows will show the Hello prompt, and a user who enters the PIN by mistake gives it the secret. This falls under T22 and T23 (a compromised session is out of scope).
- A key file is not needed for a quick unlock, because its processed key is part of the components.
- The first unlock after starting the app, and the first one after the time limit, always ask for the master password, so the user keeps practising it.
- Calling WinRT through JNA is fragile code: it gets its own tests, and the spike can show that it is too fragile. In that case, the fallback (a small signed native helper, or giving up the feature) goes back to the owner. Option 2 is not used.
- It needs a test by hand with the owner's real PIN (and without Hello set up, to see the option hidden). Automated tests cover only the policy, with a fake signer.
