package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Kdf

/** How many times the open vault's key derivation cost another copy of it may ask for; above it, it is not opened. */
private const val MAX_COPY_COST_FACTOR = 16.0

/**
 * Whether a copy's key derivation is within reach of the open vault's. The header that sets it is unencrypted, so a
 * hostile or broken copy (a conflict copy, an object in the cloud) could ask for hours of key derivation on the one
 * thread that also saves. A copy of a vault costs about what the vault costs, so far more is refused.
 *
 * Argon2 work grows with memory times iterations, AES-KDF work with rounds. Different kinds are left to the engine's
 * absolute limits.
 */
fun affordableKdf(
    copy: Kdf,
    open: Kdf,
): Boolean =
    when {
        copy is Kdf.Argon2 && open is Kdf.Argon2 ->
            copy.memoryBytes.toDouble() * copy.iterations <=
                open.memoryBytes.toDouble() * open.iterations * MAX_COPY_COST_FACTOR
        copy is Kdf.AesKdf && open is Kdf.AesKdf -> copy.rounds <= open.rounds * MAX_COPY_COST_FACTOR
        else -> true
    }
