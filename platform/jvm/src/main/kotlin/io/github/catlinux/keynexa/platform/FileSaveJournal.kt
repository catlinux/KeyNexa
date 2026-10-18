package io.github.catlinux.keynexa.platform

import io.github.catlinux.keynexa.sync.BackupKind
import io.github.catlinux.keynexa.sync.BackupRef
import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.PendingSave
import io.github.catlinux.keynexa.sync.SaveJournal
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.Properties

/**
 * [SaveJournal] with one small file per vault in [directory] (app-private, outside Android backup). It holds
 * fingerprints and backup references only, never vault content. An unreadable entry raises [IOException] instead of
 * reading as "nothing pending", so recovery treats the file as uncertain.
 */
class FileSaveJournal(
    private val directory: File,
) : SaveJournal {
    override fun begin(pending: PendingSave) {
        val properties = Properties()
        properties.setProperty(LOCATION, pending.location.value)
        properties.setProperty(PREVIOUS, pending.previous.hex)
        properties.setProperty(EXPECTED, pending.expected.hex)
        properties.setProperty(STARTED, pending.startedAt.toString())
        put(properties, PREVIOUS_COPY, pending.previousCopy)
        put(properties, OUTGOING_COPY, pending.outgoingCopy)
        val output = ByteArrayOutputStream()
        properties.store(output, null)
        writeAtomically(fileOf(pending.location), output.toByteArray())
    }

    override fun pending(location: VaultLocation): PendingSave? {
        val file = fileOf(location)
        if (!file.exists()) return null
        val properties = Properties()
        file.inputStream().use(properties::load)
        return try {
            PendingSave(
                location = location,
                previous = Fingerprint(required(properties, PREVIOUS)),
                expected = Fingerprint(required(properties, EXPECTED)),
                previousCopy = copy(properties, PREVIOUS_COPY, location),
                outgoingCopy = copy(properties, OUTGOING_COPY, location),
                startedAt = Instant.parse(required(properties, STARTED)),
            )
        } catch (failure: IllegalArgumentException) {
            throw IOException("unreadable save journal", failure)
        } catch (failure: java.time.DateTimeException) {
            throw IOException("unreadable save journal", failure)
        }
    }

    override fun clear(location: VaultLocation) {
        val file = fileOf(location)
        if (file.exists() && !file.delete()) throw IOException("cannot clear the save journal")
    }

    private fun fileOf(location: VaultLocation) = File(directory, "${locationKey(location)}.journal")

    private fun put(
        properties: Properties,
        prefix: String,
        ref: BackupRef,
    ) {
        properties.setProperty("$prefix.id", ref.id)
        properties.setProperty("$prefix.kind", ref.kind.name)
        properties.setProperty("$prefix.createdAt", ref.createdAt.toString())
        properties.setProperty("$prefix.fingerprint", ref.fingerprint.hex)
    }

    private fun copy(
        properties: Properties,
        prefix: String,
        location: VaultLocation,
    ) = BackupRef(
        id = required(properties, "$prefix.id"),
        location = location,
        kind = BackupKind.valueOf(required(properties, "$prefix.kind")),
        createdAt = Instant.parse(required(properties, "$prefix.createdAt")),
        fingerprint = Fingerprint(required(properties, "$prefix.fingerprint")),
    )

    private fun required(
        properties: Properties,
        key: String,
    ): String = properties.getProperty(key) ?: throw IllegalArgumentException("missing $key")

    private companion object {
        const val LOCATION = "location"
        const val PREVIOUS = "previous"
        const val EXPECTED = "expected"
        const val STARTED = "startedAt"
        const val PREVIOUS_COPY = "previousCopy"
        const val OUTGOING_COPY = "outgoingCopy"
    }
}
