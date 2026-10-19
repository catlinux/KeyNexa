package io.github.catlinux.keynexa.app.cloud

import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.sync.SyncIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.util.Locale

/**
 * The prefix of the synchronised objects (ADR-0009 §1). `SyncIds` keeps its own copy private, so this repeats the
 * same string to be able to list them. It is shared by both flavours: only the cloud flavour ever lists anything.
 */
internal const val SYNC_PREFIX = "KeyNexa/sync/"

private const val VAULT_EXTENSION = ".kdbx"
private const val BYTES_PER_KILOBYTE = 1024L
private const val SHORT_ID_LENGTH = 8

/** One synchronised vault in the service: only what its name and the listing say, never its content. */
internal data class CloudVault(
    val id: String,
    val objectName: String,
    val size: Long,
    val modifiedAt: Instant?,
)

/**
 * The vault [cloudObject] stands for, or null when its name is not exactly `KeyNexa/sync/<id>.kdbx` for a well-formed
 * id. The probe objects (`KeyNexa/sync/.probe-…`), malformed ids and other prefixes are ignored.
 */
internal fun cloudVaultOf(cloudObject: CloudObject): CloudVault? {
    val name = cloudObject.name
    if (!name.startsWith(SYNC_PREFIX) || !name.endsWith(VAULT_EXTENSION)) return null
    val id = name.removePrefix(SYNC_PREFIX).removeSuffix(VAULT_EXTENSION)
    if (!SyncIds.isValid(id) || name != SyncIds.objectName(id)) return null
    return CloudVault(id, name, cloudObject.size, cloudObject.modifiedAt)
}

/** The synchronised vaults among [objects], newest first; a vault with no date goes last, ties by name. */
internal fun cloudVaults(objects: List<CloudObject>): List<CloudVault> =
    objects
        .mapNotNull(::cloudVaultOf)
        .sortedWith(
            compareBy<CloudVault> { it.modifiedAt == null }
                .thenByDescending { it.modifiedAt }
                .thenBy { it.objectName },
        )

/** The first eight characters of a vault id, which is what the picker shows without revealing the whole one. */
internal fun shortVaultId(id: String): String = id.take(SHORT_ID_LENGTH)

/** The name the picker suggests for a downloaded vault: the app's name and the short id. */
internal fun suggestedVaultFileName(id: String): String = "KeyNexa-" + shortVaultId(id) + VAULT_EXTENSION

/** A byte size in the units the picker shows: bytes, kilobytes or megabytes, with a dot as the decimal separator. */
internal fun formatSize(bytes: Long): String =
    when {
        bytes < BYTES_PER_KILOBYTE -> "$bytes B"
        bytes < BYTES_PER_KILOBYTE * BYTES_PER_KILOBYTE ->
            String.format(Locale.ROOT, "%.1f kB", bytes.toDouble() / BYTES_PER_KILOBYTE)
        else ->
            String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / (BYTES_PER_KILOBYTE * BYTES_PER_KILOBYTE))
    }

/** What a download did: the file was written, the object is gone, or it failed. */
internal sealed interface CloudDownloadOutcome {
    /** The bytes were written to the destination. */
    data object Saved : CloudDownloadOutcome

    /** The object is no longer in the service; nothing was opened or written. */
    data object Gone : CloudDownloadOutcome

    /** The fetch or the write failed. */
    data class Failed(
        val error: IOException,
    ) : CloudDownloadOutcome
}

/**
 * Downloads [objectName] from [backend] through [open], off the caller's thread.
 *
 * When the object is gone nothing is opened and nothing is written. Otherwise the bytes are written exactly as they
 * came, inside `use`, and are never logged. The stream is opened by the caller so a test can pass an in-memory one.
 */
internal suspend fun downloadCloudVault(
    backend: CloudBackend,
    objectName: String,
    open: () -> OutputStream?,
): CloudDownloadOutcome =
    withContext(Dispatchers.IO) {
        try {
            val content = backend.fetch(objectName) ?: return@withContext CloudDownloadOutcome.Gone
            val stream = open() ?: return@withContext CloudDownloadOutcome.Failed(IOException("no output stream"))
            stream.use { it.write(content.bytes) }
            CloudDownloadOutcome.Saved
        } catch (failure: IOException) {
            CloudDownloadOutcome.Failed(failure)
        }
    }
