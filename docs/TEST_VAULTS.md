# Test vault corpus

[English](TEST_VAULTS.md) | [Español](es/TEST_VAULTS.md)

The fictitious vaults in `testdata/` feed the compatibility tests (AC1, AC2). They are produced by real KeePass programs on purpose: KeyNexa must open files it did not write itself. They hold only made-up data; **every password here is public**.

## How they are generated

| Script | Program | Run with |
| --- | --- | --- |
| [`tools/testdata/New-KeePass2Vaults.ps1`](../tools/testdata/New-KeePass2Vaults.ps1) | KeePass 2.61 (its own KeePassLib, loaded from PowerShell) | `powershell -File tools\testdata\New-KeePass2Vaults.ps1` |
| [`tools/testdata/new-keepassxc-vaults.sh`](../tools/testdata/new-keepassxc-vaults.sh) | KeePassXC 2.7.12 (`keepassxc-cli`) | `bash tools/testdata/new-keepassxc-vaults.sh` |
| [`tools/testdata/inspect_kdbx.py`](../tools/testdata/inspect_kdbx.py) | none (reads headers) | `python -I tools/testdata/inspect_kdbx.py testdata/*.kdbx` |

Requirements: KeePass 2.x (`winget install DominikReichl.KeePass`) and KeePassXC (`winget install KeePassXCTeam.KeePassXC`). Random content (the 64 KiB attachment) is seeded, so reruns give equivalent but not byte-identical files (KeePass randomises UUIDs, salts and timestamps). Do not regenerate the corpus without a reason; tests rely on the committed files.

`keepassxc-cli` 2.7.12 cannot choose the cipher, the KDF or the format version, and it cannot set tags, custom fields or TOTP. That is why KeePassXC contributes only the command-line vaults below plus the three saved from its interface (last section).

## Content of the "rich" vaults

Groups `Internet` (with `Shopping`), `Work` and a recycle bin with one deleted entry; entries with user, password, URL and notes; one entry with non-ASCII text (`Ñandú café 日本語 😀`); custom fields (one protected) and two attachments (a text file and 64 KiB of random bytes) on the entry `Server`; an entry with four versions in its history (`Changing password`, current password `v4-password`); standard icons, one custom icon and an expired entry (KeePass 2 vaults); public custom data (KeePass 2 vaults except the KDBX 3.1 one). The `*-tags` file adds entry and group tags, which makes it KDBX 4.1.

## Files

Master password of every vault: `test-password-1`, unless noted. Argon2: 64 MiB, 10 iterations, 2 threads. AES-KDF: 600000 rounds (KeePassXC chooses its own).

| File | Written by | Format | Cipher | KDF |
| --- | --- | --- | --- | --- |
| `rich-aes-argon2d.kdbx` | KeePass 2 | 4.0 | AES-256 | Argon2d |
| `rich-aes-argon2id.kdbx` | KeePass 2 | 4.0 | AES-256 | Argon2id |
| `rich-chacha20-argon2d.kdbx` | KeePass 2 | 4.0 | ChaCha20 | Argon2d |
| `rich-chacha20-argon2id.kdbx` | KeePass 2 | 4.0 | ChaCha20 | Argon2id |
| `rich-aes-aeskdf.kdbx` | KeePass 2 | 4.0 | AES-256 | AES-KDF |
| `rich-chacha20-aeskdf.kdbx` | KeePass 2 | 4.0 | ChaCha20 | AES-KDF |
| `rich-kdbx31-aes-aeskdf.kdbx` | KeePass 2 | **3.1** | AES-256 | AES-KDF |
| `rich-aes-argon2d-tags.kdbx` | KeePass 2 | **4.1** (tags) | AES-256 | Argon2d |
| `kpxc-aeskdf.kdbx` | KeePassXC | 4.1 | AES-256 | AES-KDF (about 8.3 million rounds) |
| `kpxc-twofish-argon2d.kdbx` | KeePassXC (interface) | 4.1 | Twofish-256 | Argon2d |
| `kpxc-chacha20-argon2id.kdbx` | KeePassXC (interface) | 4.1 | ChaCha20 | Argon2id |
| `kpxc-argon2d-tags-totp.kdbx` | KeePassXC (interface) | 4.1 | AES-256 | Argon2d; tag `personal` and TOTP (`otp` field, made-up secret `JBSWY3DPEHPK3PXP`) on `Internet/Example mail` |
| `kpxc-empty.kdbx` | KeePassXC | 3.1 | AES-256 | AES-KDF; no entries |
| `kpxc-keyfile-password.kdbx` + `kpxc-keyfile.keyx` | KeePassXC | 4.1 | AES-256 | AES-KDF; password **and** key file |
| `keyfile-only.kdbx` + `keyfile-only.key` | KeePass 2 | 4.0 | AES-256 | AES-KDF; key file only, no password |
| `keyfile-password-kp2.kdbx` (uses `keyfile-only.key`) | KeePass 2 | 4.0 | AES-256 | Argon2id; password and key file |
| `unicode-password.kdbx` | KeePass 2 | 4.0 | AES-256 | Argon2id; password `pässwörd-日本語-😀` |
| `argon2-1gib.kdbx` | KeePass 2 | 4.0 | AES-256 | Argon2id, **1024 MiB**, 2 iterations; for memory and time measurements |

Every file was opened with `keepassxc-cli` as an independent check, and its header was read with `inspect_kdbx.py`.

## The three KeePassXC vaults saved from its interface

`keepassxc-cli` cannot choose Twofish, change the KDF or set tags and TOTP, so `kpxc-twofish-argon2d.kdbx`, `kpxc-chacha20-argon2id.kdbx` and `kpxc-argon2d-tags-totp.kdbx` were saved from the KeePassXC window, starting from a copy of `kpxc-aeskdf.kdbx` and changing *Database settings → Security → Encryption options* (Argon2: 64 MiB, 10 iterations, 2 threads). To redo one by hand, repeat that and, for the last one, add the tag in the entry editor and use *Entries → TOTP → Set up TOTP*. These three are not produced by a script.
