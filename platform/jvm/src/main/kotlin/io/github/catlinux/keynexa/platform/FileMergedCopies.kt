package io.github.catlinux.keynexa.platform

import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.MergedCopies
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.File

/**
 * [MergedCopies] over plain files in [directory], which must be app-private and outside Android backup
 * (`Context.noBackupFilesDir`), like [FileBackupStore].
 *
 * One file per vault, `<location key>.merged`, with one fingerprint per line and the newest last. Only fingerprints
 * are written: never vault content or secrets. The ledger is the reason a copy that was already merged is not
 * decrypted again, so it is cheap to read and never needs to grow: once it holds [LIMIT] fingerprints the oldest
 * ones (the first lines) are dropped when a new one arrives.
 */
class FileMergedCopies(
    private val directory: File,
) : MergedCopies {
    override fun isMerged(
        vault: VaultLocation,
        copy: Fingerprint,
    ): Boolean {
        val file = fileOf(vault)
        return file.exists() && copy.hex in fingerprintsOf(file)
    }

    override fun record(
        vault: VaultLocation,
        copies: Collection<Fingerprint>,
    ) {
        val file = fileOf(vault)
        val known = if (file.exists()) fingerprintsOf(file) else emptyList()
        val added = copies.map { it.hex }.filterNot { it in known }
        if (added.isEmpty()) return
        val all = (known + added).distinct().takeLast(LIMIT)
        writeAtomically(file, all.joinToString(separator = "\n", postfix = "\n").toByteArray())
    }

    private fun fingerprintsOf(file: File): List<String> = file.readLines().filter { it.isNotBlank() }

    private fun fileOf(vault: VaultLocation): File = File(directory, "${locationKey(vault)}$SUFFIX")

    private companion object {
        const val SUFFIX = ".merged"

        /** How many fingerprints are kept per vault; older ones go first when a new copy arrives. */
        const val LIMIT = 200
    }
}
