package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.BackupError
import io.github.catlinux.keynexa.sync.BackupState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Properties

private const val VAULT_ID_LENGTH = 16
private const val TEMPORARY_SUFFIX = ".tmp"
private const val BYTE_MASK = 0xFF
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0x0F
private const val HEX_DIGITS = "0123456789abcdef"

/** The id of a vault for the backup state: the first [VAULT_ID_LENGTH] hex digits of the SHA-256 of its location. */
fun vaultIdOf(location: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(location.toByteArray(Charsets.UTF_8))
    val builder = StringBuilder(digest.size * 2)
    for (byte in digest) {
        val value = byte.toInt() and BYTE_MASK
        builder.append(HEX_DIGITS[value ushr NIBBLE_BITS]).append(HEX_DIGITS[value and NIBBLE_MASK])
    }
    return builder.toString().take(VAULT_ID_LENGTH)
}

/**
 * The backup state on disk, as a `Properties` text file: no secret ever reaches it.
 *
 * Each vault has `vaultId.lastSuccess`, `vaultId.lastError` and `vaultId.lastErrorAt`. Reads are tolerant: a missing,
 * unreadable or corrupt file means "no state", never an exception. Writes are atomic (a temporary file and a move).
 */
@Suppress("TooManyFunctions")
class FileBackupState(
    private val file: File,
) : BackupState {
    private val successes = HashMap<String, Instant>()
    private val errors = HashMap<String, BackupError>()
    private val errorTimes = HashMap<String, Instant>()
    private var loaded = false

    override fun lastSuccess(vaultId: String): Instant? {
        ensureLoaded()
        return successes[vaultId]
    }

    /** True when any vault has a recorded successful copy. */
    fun hasAnySuccess(): Boolean {
        ensureLoaded()
        return successes.isNotEmpty()
    }

    override fun recordSuccess(
        vaultId: String,
        at: Instant,
    ) {
        ensureLoaded()
        successes[vaultId] = at
        persist()
    }

    override fun lastError(vaultId: String): BackupError? {
        ensureLoaded()
        return errors[vaultId]
    }

    override fun lastErrorAt(vaultId: String): Instant? {
        ensureLoaded()
        return errorTimes[vaultId]
    }

    override fun recordError(
        vaultId: String,
        error: BackupError,
        at: Instant,
    ) {
        ensureLoaded()
        errors[vaultId] = error
        errorTimes[vaultId] = at
        persist()
    }

    override fun clearError(vaultId: String) {
        ensureLoaded()
        errors.remove(vaultId)
        errorTimes.remove(vaultId)
        persist()
    }

    /** Forgets every vault and deletes the state file, so a new connection starts with no old "last copy". */
    fun clearAll() {
        successes.clear()
        errors.clear()
        errorTimes.clear()
        loaded = true
        if (file.exists()) file.delete()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val properties = readProperties() ?: return
        properties.stringPropertyNames().forEach { key ->
            val dot = key.lastIndexOf('.')
            if (dot <= 0) return@forEach
            val vaultId = key.substring(0, dot)
            when (key.substring(dot + 1)) {
                SUCCESS_KEY -> properties.getProperty(key).toInstantOrNull()?.let { successes[vaultId] = it }
                ERROR_KEY -> {
                    val error = BackupError.entries.firstOrNull { it.name == properties.getProperty(key) }
                    if (error != null) errors[vaultId] = error
                }
                ERROR_AT_KEY -> properties.getProperty(key).toInstantOrNull()?.let { errorTimes[vaultId] = it }
            }
        }
    }

    @Suppress("SwallowedException")
    private fun readProperties(): Properties? {
        if (!file.isFile) return null
        return try {
            Properties().apply { load(ByteArrayInputStream(file.readBytes())) }
        } catch (failure: IOException) {
            null
        } catch (failure: IllegalArgumentException) {
            null
        }
    }

    private fun persist() {
        val properties = Properties()
        successes.forEach { (vaultId, at) -> properties.setProperty("$vaultId.$SUCCESS_KEY", at.toString()) }
        errors.forEach { (vaultId, error) -> properties.setProperty("$vaultId.$ERROR_KEY", error.name) }
        errorTimes.forEach { (vaultId, at) -> properties.setProperty("$vaultId.$ERROR_AT_KEY", at.toString()) }
        val out = ByteArrayOutputStream()
        properties.store(out, null)
        writeAtomically(out.toByteArray())
    }

    @Suppress("SwallowedException")
    private fun writeAtomically(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}$TEMPORARY_SUFFIX")
        try {
            temporary.writeBytes(bytes)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (failure: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    @Suppress("SwallowedException")
    private fun String?.toInstantOrNull(): Instant? =
        if (this == null) {
            null
        } else {
            try {
                Instant.parse(this)
            } catch (failure: DateTimeParseException) {
                null
            }
        }

    private companion object {
        const val SUCCESS_KEY = "lastSuccess"
        const val ERROR_KEY = "lastError"
        const val ERROR_AT_KEY = "lastErrorAt"
    }
}
