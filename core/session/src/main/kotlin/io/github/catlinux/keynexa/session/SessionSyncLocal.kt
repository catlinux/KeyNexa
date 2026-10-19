package io.github.catlinux.keynexa.session

import io.github.catlinux.keynexa.sync.Absorbed
import io.github.catlinux.keynexa.sync.Expect
import io.github.catlinux.keynexa.sync.SyncFile
import io.github.catlinux.keynexa.sync.SyncLocal
import io.github.catlinux.keynexa.vault.StoragePort
import java.io.IOException

/**
 * The open vault as the cloud reconciler sees it (ADR-0009): [VaultSession] behind [SyncLocal].
 *
 * It carries no cloud state and no credentials; the session does the decrypting, merging and saving. [currentFile]
 * reads the file the vault was last saved to, so the reconciler uploads bytes that match what is on disk.
 */
class SessionSyncLocal(
    private val session: VaultSession,
    private val storage: StoragePort,
) : SyncLocal {
    override fun syncId(): String? = session.syncId()

    /**
     * @throws IOException when no vault is open or the file cannot be read; the reconciler reports that as
     *   unavailable and the next trigger tries again.
     */
    override fun currentFile(): SyncFile {
        val location =
            (session.state.value as? SessionState.Open)?.vault?.location
                ?: throw IOException("no vault is open")
        return SyncFile(storage.read(location))
    }

    override fun absorb(
        bytes: ByteArray,
        expect: Expect,
    ): Absorbed = session.absorbRemote(bytes, expect)

    override fun assignSyncId(id: String): Boolean = session.assignSyncId(id)
}
