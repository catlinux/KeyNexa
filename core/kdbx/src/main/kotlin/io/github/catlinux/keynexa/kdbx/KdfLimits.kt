package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultFormat

private const val MEBIBYTE = 1024L * 1024L

/** Largest Argon2 memory the engine will allocate. KeePassXC defaults to 64 MiB; 1 GiB is already unusual. */
internal const val MAX_ARGON2_MEMORY_BYTES = 2048L * MEBIBYTE

internal const val MAX_ARGON2_ITERATIONS = 10_000L
internal const val MAX_ARGON2_PARALLELISM = 256

/** KeePass benchmarks reach tens of millions of rounds on a desktop; this leaves ample room above that. */
internal const val MAX_AES_KDF_ROUNDS = 500_000_000L

private const val MESSAGE_KDF_TOO_COSTLY = "The key derivation settings of this file are beyond what this app accepts."

/**
 * The key derivation parameters sit in the unencrypted header, so a damaged or hostile file can ask for hours of
 * work or gigabytes of memory before any integrity check runs. Refusing absurd costs up front turns that into a
 * clear error instead of a frozen or crashed app.
 */
internal fun VaultFormat.requireAcceptableKdfCost() {
    val acceptable =
        when (val settings = kdf) {
            is Kdf.AesKdf -> settings.rounds in 1..MAX_AES_KDF_ROUNDS
            is Kdf.Argon2 ->
                settings.memoryBytes in 1..MAX_ARGON2_MEMORY_BYTES &&
                    settings.iterations in 1..MAX_ARGON2_ITERATIONS &&
                    settings.parallelism in 1..MAX_ARGON2_PARALLELISM
        }
    if (!acceptable) throw VaultException.UnsupportedFormat(MESSAGE_KDF_TOO_COSTLY)
}
