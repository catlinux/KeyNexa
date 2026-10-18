package io.github.catlinux.keynexa.vault

/**
 * Reads and writes the KDBX byte format. The only implementation lives in `core/kdbx`; everything else depends
 * on this interface, so the engine can be replaced (ADR-0005) without touching the rest of the app.
 *
 * Implementations must never log or write to temporary files any password, key or decrypted content, and their
 * exception messages must not contain them either.
 */
interface KdbxEngine {
    /**
     * Reads the unencrypted header only. Cheap: no key derivation. Use it to show the file's format or to decide
     * whether a file that just changed is complete before asking for credentials.
     *
     * @throws VaultException.NotAVault if the data does not start like a KDBX file.
     * @throws VaultException.UnsupportedFormat if the version, cipher or key derivation is not supported.
     * @throws VaultException.Corrupted if the header is cut short or inconsistent.
     */
    fun inspect(data: ByteArray): VaultFormat

    /**
     * Decrypts and parses [data].
     *
     * Argon2 key derivation can take seconds and a lot of memory: call it off the main thread.
     *
     * @throws VaultException.NotAVault if the data does not start like a KDBX file.
     * @throws VaultException.UnsupportedFormat if the version, cipher or key derivation is not supported.
     * @throws VaultException.WrongCredentials if the credentials do not open the file. In KDBX 3.x a damaged or cut
     *   file is indistinguishable from a wrong key, so a caller whose credentials already opened this vault must
     *   treat the failure as possible damage.
     * @throws VaultException.Corrupted if the file is damaged or cut short (for example a partial synchronisation
     *   transfer). The caller must not overwrite such a file. In KDBX 4 this is reliable: the key is proven first.
     */
    fun decode(
        data: ByteArray,
        credentials: Credentials,
    ): Vault

    /**
     * The same credentials in their pre-hashed form ([Credentials.ofHashes]): the components KDBX hashes before the
     * key derivation. They open the vault without the password or the raw key file. The caller wipes the result.
     *
     * @throws VaultException.InvalidKeyFile if the key file cannot be parsed.
     */
    fun hashedForm(credentials: Credentials): Credentials

    /**
     * Serialises and encrypts [vault] with the format it carries ([Vault.format]), with fresh salts and seeds.
     *
     * @throws VaultException.UnsupportedFormat if [Vault.format] cannot be written.
     */
    fun encode(
        vault: Vault,
        credentials: Credentials,
    ): ByteArray
}
