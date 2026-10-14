package io.github.catlinux.keynexa.vault

/**
 * The master credentials of a vault: a password, a key file, or both. At least one is required.
 *
 * The secrets are kept as arrays so [wipe] can overwrite them as soon as the vault is open. This does not make
 * the app immune to memory inspection, but it keeps the copies under our control short-lived.
 */
class Credentials private constructor(
    private var password: CharArray?,
    private var keyFile: ByteArray?,
    private var passwordHash: ByteArray? = null,
    private var keyHash: ByteArray? = null,
) : AutoCloseable {
    private var wiped = false

    init {
        require(password != null || keyFile != null || passwordHash != null || keyHash != null) {
            "A vault needs a password, a key file, or both"
        }
    }

    /** True when built from the already-hashed components (see [ofHashes]) instead of the raw secrets. */
    val isPreHashed: Boolean get() = passwordHash != null || keyHash != null

    /** A copy of the SHA-256 of the password, for pre-hashed credentials; the caller wipes it. */
    fun passwordHashCopy(): ByteArray? {
        check(!wiped) { "Credentials already wiped" }
        return passwordHash?.copyOf()
    }

    /** A copy of the processed key-file key, for pre-hashed credentials; the caller wipes it. */
    fun keyHashCopy(): ByteArray? {
        check(!wiped) { "Credentials already wiped" }
        return keyHash?.copyOf()
    }

    val hasPassword: Boolean get() = password != null || passwordHash != null
    val hasKeyFile: Boolean get() = keyFile != null || keyHash != null

    /** A copy of the password; the caller wipes it. Throws if the credentials were already wiped. */
    fun passwordCopy(): CharArray? {
        check(!wiped) { "Credentials already wiped" }
        return password?.copyOf()
    }

    /** A copy of the key file contents; the caller wipes it. Throws if the credentials were already wiped. */
    fun keyFileCopy(): ByteArray? {
        check(!wiped) { "Credentials already wiped" }
        return keyFile?.copyOf()
    }

    /** Overwrites the secrets. The object cannot be used afterwards. */
    fun wipe() {
        password?.fill(WIPE_CHAR)
        keyFile?.fill(0)
        passwordHash?.fill(0)
        keyHash?.fill(0)
        password = null
        keyFile = null
        passwordHash = null
        keyHash = null
        wiped = true
    }

    override fun close() = wipe()

    /** Never prints the secrets. */
    override fun toString(): String = "Credentials(password=$hasPassword, keyFile=$hasKeyFile)"

    companion object {
        private const val WIPE_CHAR = '\u0000'

        /** Takes ownership of the arrays: they are overwritten by [wipe]. */
        fun of(
            password: CharArray? = null,
            keyFile: ByteArray? = null,
        ): Credentials = Credentials(password, keyFile)

        /**
         * Credentials from the components KDBX hashes before the key derivation: the SHA-256 of the password and
         * the processed key-file key, 32 bytes each. Takes ownership of the arrays. They open the same vault as the
         * secrets they came from, without the password existing in memory.
         */
        fun ofHashes(
            passwordHash: ByteArray? = null,
            keyHash: ByteArray? = null,
        ): Credentials {
            require(passwordHash == null || passwordHash.size == HASH_SIZE) { "Invalid password hash size" }
            require(keyHash == null || keyHash.size == HASH_SIZE) { "Invalid key hash size" }
            return Credentials(null, null, passwordHash, keyHash)
        }

        private const val HASH_SIZE = 32
    }
}
