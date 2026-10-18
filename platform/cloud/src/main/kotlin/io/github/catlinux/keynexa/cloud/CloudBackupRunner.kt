package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.BackupError
import io.github.catlinux.keynexa.sync.BackupOptions
import io.github.catlinux.keynexa.sync.BackupResult
import io.github.catlinux.keynexa.sync.BackupState
import io.github.catlinux.keynexa.sync.BackupUploader
import io.github.catlinux.keynexa.sync.CloudBackend
import java.io.IOException
import java.time.Clock

/**
 * The backup engine without any Android dependency, so it can be tested on the JVM.
 *
 * [run] does nothing (and answers null) when there is no valid setup or the consent is not accepted. A failure to
 * read the vault bytes is recorded as an [BackupError.Unavailable] and answered as a result, never thrown.
 */
class CloudBackupRunner(
    private val loadSetup: () -> S3Setup?,
    private val consentAccepted: () -> Boolean,
    private val state: BackupState,
    private val backendFactory: (S3Setup) -> CloudBackend,
    private val clock: Clock = Clock.systemUTC(),
    private val prefix: String = "KeyNexa/",
) {
    fun run(
        vaultId: String,
        vaultName: String,
        read: () -> ByteArray,
        force: Boolean,
    ): BackupResult? {
        val setup = loadSetup() ?: return null
        if (!consentAccepted()) return null
        val bytes =
            try {
                read()
            } catch (failure: IOException) {
                state.recordError(vaultId, BackupError.Unavailable, clock.instant())
                return BackupResult.Failed(BackupError.Unavailable)
            }
        val uploader =
            BackupUploader(
                backend = backendFactory(setup),
                state = state,
                options = BackupOptions(keep = setup.keep, prefix = prefix),
                clock = clock,
            )
        return if (force) {
            uploader.backupNow(vaultId, vaultName, bytes)
        } else {
            uploader.backupIfDue(vaultId, vaultName, bytes)
        }
    }
}
