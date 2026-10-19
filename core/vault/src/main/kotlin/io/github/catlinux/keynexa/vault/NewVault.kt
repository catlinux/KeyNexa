package io.github.catlinux.keynexa.vault

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Creates a brand-new, empty vault with the defaults KeyNexa writes: KDBX 4.1, ChaCha20 for the file and Argon2id
 * for the key, a root group named like the vault, and the recycle bin enabled with its group already in place.
 */
class NewVault(
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> UUID = UUID::randomUUID,
) {
    // KDBX stores whole seconds; times kept finer in memory would differ from the same vault read back from its file,
    // and a merge would then take one version for two.
    private fun wholeSecondsNow(): Instant = clock.instant().truncatedTo(ChronoUnit.SECONDS)

    /** Creates the vault. [kdf] replaces the default key-derivation parameters with the ones it carries. */
    fun create(
        name: String,
        kdf: Kdf.Argon2 = defaultKdf(),
    ): Vault {
        val now = wholeSecondsNow()
        val rootId = newId()
        val bin =
            Group(
                id = newId(),
                name = RECYCLE_BIN_GROUP_NAME,
                icon = Icon(RECYCLE_BIN_ICON_ID),
                times = creationTimes(now),
            )
        val root = Group(id = rootId, name = name, times = creationTimes(now), groups = listOf(bin))
        return Vault(
            format = VaultFormat(FormatVersion(KDBX_MAJOR, KDBX_MINOR), Cipher.ChaCha20, kdf),
            meta =
                VaultMeta(
                    generator = GENERATOR,
                    name = name,
                    recycleBinEnabled = true,
                    recycleBinGroup = bin.id,
                    recycleBinChanged = now,
                ),
            root = root,
        )
    }

    companion object {
        /** Argon2id with 64 MiB of memory, 3 iterations, parallelism 2 and Argon2 version 19. */
        fun defaultKdf(): Kdf.Argon2 =
            Kdf.Argon2(
                variant = Argon2Variant.Argon2id,
                memoryBytes = DEFAULT_MEMORY_BYTES,
                iterations = DEFAULT_ITERATIONS,
                parallelism = DEFAULT_PARALLELISM,
                version = ARGON2_VERSION,
            )

        private const val GENERATOR = "KeyNexa"
        private const val KDBX_MAJOR = 4
        private const val KDBX_MINOR = 1
        private const val DEFAULT_MEMORY_BYTES = 64L * 1024L * 1024L
        private const val DEFAULT_ITERATIONS = 3L
        private const val DEFAULT_PARALLELISM = 2
        private const val ARGON2_VERSION = 19
    }
}
