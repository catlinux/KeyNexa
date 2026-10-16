package io.github.catlinux.keynexa.vault

/**
 * Why a vault could not be read or written. The distinction matters to the callers: a [Corrupted] file may be a
 * transfer still in progress and is retried, never overwritten; [WrongCredentials] asks the user again.
 * Messages never contain passwords, keys or vault content.
 */
sealed class VaultException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** The data is not a KDBX file (wrong signature). */
    class NotAVault(
        message: String,
        cause: Throwable? = null,
    ) : VaultException(message, cause)

    /** A valid KDBX file that uses a version, cipher or key derivation this engine cannot handle. */
    class UnsupportedFormat(
        message: String,
        cause: Throwable? = null,
    ) : VaultException(message, cause)

    /** The credentials do not open the file. */
    class WrongCredentials(
        message: String,
        cause: Throwable? = null,
    ) : VaultException(message, cause)

    /** The key file is malformed. */
    class InvalidKeyFile(
        message: String,
        cause: Throwable? = null,
    ) : VaultException(message, cause)

    /** The file is damaged, truncated or fails an integrity check. */
    class Corrupted(
        message: String,
        cause: Throwable? = null,
    ) : VaultException(message, cause)

    /** The key derivation (for example Argon2) asks for more memory than the device can give. */
    class NotEnoughMemory(
        message: String,
        cause: Throwable? = null,
    ) : VaultException(message, cause)
}
