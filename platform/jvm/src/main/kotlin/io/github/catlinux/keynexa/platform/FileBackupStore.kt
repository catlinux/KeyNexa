package io.github.catlinux.keynexa.platform

import io.github.catlinux.keynexa.sync.BackupKind
import io.github.catlinux.keynexa.sync.BackupRef
import io.github.catlinux.keynexa.sync.BackupStore
import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

/**
 * [BackupStore] over plain files in [directory], which must be app-private and outside Android backup
 * (`Context.noBackupFilesDir`). Stores only the encrypted vault bytes it is given.
 *
 * The file name carries everything the reference needs, so listing never opens a copy:
 * `<location hash>.<kind>.<seconds>.<nanos>.<fingerprint>.bak`.
 */
class FileBackupStore(
    private val directory: File,
) : BackupStore {
    override fun put(
        location: VaultLocation,
        kind: BackupKind,
        bytes: ByteArray,
        createdAt: Instant,
    ): BackupRef {
        val fingerprint = Fingerprint.of(bytes)
        val ref = BackupRef(nameOf(location, kind, createdAt, fingerprint), location, kind, createdAt, fingerprint)
        writeAtomically(File(directory, ref.id), bytes)
        return ref
    }

    override fun list(location: VaultLocation): List<BackupRef> {
        val prefix = "${locationKey(location)}."
        val names = directory.list() ?: return emptyList()
        return names
            .filter { it.startsWith(prefix) && it.endsWith(SUFFIX) }
            .mapNotNull { parse(it, location) }
            .sortedByDescending { it.createdAt }
    }

    override fun read(ref: BackupRef): ByteArray = File(directory, ref.id).readBytes()

    override fun delete(ref: BackupRef) {
        val file = File(directory, ref.id)
        if (file.exists() && !file.delete()) throw IOException("cannot delete backup ${ref.id}")
    }

    private fun nameOf(
        location: VaultLocation,
        kind: BackupKind,
        createdAt: Instant,
        fingerprint: Fingerprint,
    ) = "${locationKey(location)}.${kind.name}.${createdAt.epochSecond}.${createdAt.nano}.${fingerprint.hex}$SUFFIX"

    private fun parse(
        name: String,
        location: VaultLocation,
    ): BackupRef? {
        val parts = name.removeSuffix(SUFFIX).split('.')
        if (parts.size != PARTS) return null
        val kind = BackupKind.entries.firstOrNull { it.name == parts.getOrNull(KIND) }
        val seconds = parts.getOrNull(SECONDS)?.toLongOrNull()
        val nanos = parts.getOrNull(NANOS)?.toLongOrNull()
        if (kind == null || seconds == null || nanos == null) return null
        return BackupRef(name, location, kind, Instant.ofEpochSecond(seconds, nanos), Fingerprint(parts[FINGERPRINT]))
    }

    private companion object {
        const val SUFFIX = ".bak"
        const val PARTS = 5
        const val KIND = 1
        const val SECONDS = 2
        const val NANOS = 3
        const val FINGERPRINT = 4
    }
}

/** A short, file-name-safe key of a vault location; the location itself is a URI that cannot be a file name. */
internal fun locationKey(location: VaultLocation): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(location.value.toByteArray(Charsets.UTF_8))
        .take(KEY_BYTES)
        .joinToString("") { "%02x".format(it) }

private const val KEY_BYTES = 12

/** Writes to a temporary file in the same folder and renames it, so a crash never leaves a half-written file. */
fun writeAtomically(
    target: File,
    bytes: ByteArray,
) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.tmp")
    try {
        java.io.FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } catch (failure: IOException) {
        temporary.delete()
        throw failure
    }
}
