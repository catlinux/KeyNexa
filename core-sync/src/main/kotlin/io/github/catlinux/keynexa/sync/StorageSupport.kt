package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.IOException
import java.time.Instant

internal fun StoragePort.readOrNull(location: VaultLocation): ByteArray? =
    try {
        read(location)
    } catch (failure: IOException) {
        null
    }

/** Writes [bytes] and checks, by reading back, that the file holds exactly them. */
internal fun StoragePort.writeVerified(
    location: VaultLocation,
    bytes: ByteArray,
    fingerprint: Fingerprint = Fingerprint.of(bytes),
): Boolean =
    try {
        write(location, bytes)
        readOrNull(location)?.let(Fingerprint::of) == fingerprint
    } catch (failure: IOException) {
        false
    }

internal fun BackupStore.putOrNull(
    location: VaultLocation,
    kind: BackupKind,
    bytes: ByteArray,
    createdAt: Instant,
): BackupRef? =
    try {
        put(location, kind, bytes, createdAt)
    } catch (failure: IOException) {
        null
    }

/** Reads a copy and returns it only if it still holds the bytes that were stored. */
internal fun BackupStore.readIntactOrNull(ref: BackupRef): ByteArray? =
    try {
        read(ref).takeIf { Fingerprint.of(it) == ref.fingerprint }
    } catch (failure: IOException) {
        null
    }

internal fun BackupStore.deleteQuietly(ref: BackupRef) {
    runCatching { delete(ref) }
}

internal fun SaveJournal.clearQuietly(location: VaultLocation) {
    runCatching { clear(location) }
}
