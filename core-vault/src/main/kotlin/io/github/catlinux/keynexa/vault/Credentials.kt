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
) : AutoCloseable {
    private var wiped = false

    init {
        require(password != null || keyFile != null) { "A vault needs a password, a key file, or both" }
    }

    val hasPassword: Boolean get() = password != null
    val hasKeyFile: Boolean get() = keyFile != null

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
        password = null
        keyFile = null
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
    }
}
