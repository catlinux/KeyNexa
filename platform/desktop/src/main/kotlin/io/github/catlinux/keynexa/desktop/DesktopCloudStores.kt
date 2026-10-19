package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.cloud.CloudCredentialStore
import io.github.catlinux.keynexa.cloud.FileBackupState
import io.github.catlinux.keynexa.cloud.FileSyncState
import io.github.catlinux.keynexa.platform.SecretBox
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException

/** The consent text version; a new text raises it and the user is asked again. */
const val CONSENT_VERSION = 1

private const val CLOUD_DIRECTORY = "cloud"
private const val CREDENTIALS_FILE = "credentials.bin"
private const val CONSENT_FILE = "consent"
private const val REMINDER_FILE = "reminder"
private const val BACKUP_STATE_FILE = "backup-state.properties"
private const val SYNC_STATE_FILE = "sync-state.properties"

/**
 * Where a desktop client keeps what the cloud needs: the credentials, the consent mark, the reminder and the state of
 * the last backup of every vault, all under `data/cloud`. Nothing here is ever logged.
 */
class DesktopCloudStores(
    dirs: DesktopDirs,
    private val box: SecretBox,
) {
    private val directory: File = dirs.data.resolve(CLOUD_DIRECTORY).toFile()

    /** The credentials of the cloud connection, sealed with [box]. */
    val credentials: CloudCredentialStore = CloudCredentialStore(File(directory, CREDENTIALS_FILE), box)

    /** Whether the user accepted the consent text. */
    val consent = DesktopCloudConsent(File(directory, CONSENT_FILE))

    /** Until when the discreet reminder about copying to the cloud was postponed. */
    val reminder = DesktopCloudReminder(File(directory, REMINDER_FILE))

    /** The last success and error of every vault's backup. */
    val backupState: FileBackupState = FileBackupState(File(directory, BACKUP_STATE_FILE))

    /** The last entity tag, fingerprint, success and error of every synchronised vault (ADR-0009 §8). */
    val syncState: FileSyncState = FileSyncState(File(directory, SYNC_STATE_FILE))

    /** The folder everything here lives in, for the screens that show where it is. */
    val path: Path = directory.toPath()
}

/** Remembers, as a version number, that the user accepted the consent text. */
class DesktopCloudConsent(
    private val file: File,
) {
    /** True when the accepted version is the current text's or a newer one. */
    @Suppress("SwallowedException")
    fun accepted(): Boolean =
        try {
            file.isFile && (file.readText().trim().toIntOrNull() ?: 0) >= CONSENT_VERSION
        } catch (failure: IOException) {
            false
        }

    fun accept() {
        file.parentFile?.mkdirs()
        file.writeText(CONSENT_VERSION.toString())
    }

    fun revoke() {
        if (file.exists()) file.delete()
    }
}

/** Remembers until when the discreet cloud-copy reminder was postponed. */
class DesktopCloudReminder(
    private val file: File,
) {
    /** The moment the reminder may come back, or null when it was never postponed. */
    @Suppress("SwallowedException")
    fun snoozedUntil(): Instant? =
        try {
            file.takeIf { it.isFile }?.readText()?.let(Instant::parse)
        } catch (failure: IOException) {
            null
        } catch (failure: DateTimeParseException) {
            null
        }

    /** Postpones the reminder until [until]. */
    fun snooze(until: Instant) {
        file.parentFile?.mkdirs()
        file.writeText(until.toString())
    }
}
