package io.github.catlinux.keynexa.kdbx

import app.keemobile.kotpass.errors.CryptoError
import app.keemobile.kotpass.errors.FormatError
import app.keemobile.kotpass.errors.KeyfileError
import io.github.catlinux.keynexa.vault.VaultException
import java.io.IOException

private const val MESSAGE_NOT_A_VAULT = "The data is not a KDBX file."
private const val MESSAGE_UNSUPPORTED_FORMAT = "This KDBX file uses a format that cannot be written."
private const val MESSAGE_WRONG_CREDENTIALS = "The credentials do not open this vault."
private const val MESSAGE_INVALID_KEY_FILE = "The key file is not valid."
private const val MESSAGE_CORRUPTED = "The vault file is damaged or incomplete."

/** Translates a failure from the KDBX library into the engine's own error type, keeping the original cause. */
internal fun Throwable.toVaultException(): VaultException =
    when (this) {
        is VaultException -> this
        is FormatError.UnknownFormat -> VaultException.NotAVault(MESSAGE_NOT_A_VAULT, this)
        is FormatError.UnsupportedVersion -> VaultException.UnsupportedFormat(MESSAGE_UNSUPPORTED_FORMAT, this)
        is FormatError.InvalidHeader -> invalidHeaderFailure()
        is FormatError -> VaultException.Corrupted(MESSAGE_CORRUPTED, this)
        is CryptoError.InvalidKey -> VaultException.WrongCredentials(MESSAGE_WRONG_CREDENTIALS, this)
        is KeyfileError -> VaultException.InvalidKeyFile(MESSAGE_INVALID_KEY_FILE, this)
        is IOException -> VaultException.Corrupted(MESSAGE_CORRUPTED, this)
        else -> VaultException.Corrupted(MESSAGE_CORRUPTED, this)
    }

// A missing cipher is a format we cannot handle; every other header problem means the file is damaged.
private fun FormatError.InvalidHeader.invalidHeaderFailure(): VaultException =
    if (message.contains("Unsupported cipher")) {
        VaultException.UnsupportedFormat(MESSAGE_UNSUPPORTED_FORMAT, this)
    } else {
        VaultException.Corrupted(MESSAGE_CORRUPTED, this)
    }
