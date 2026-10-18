package io.github.catlinux.keynexa.platform

import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The ledger of merged conflict copies on disk: one file per vault with one fingerprint per line. */
class FileMergedCopiesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val location = VaultLocation("content://docs/tree/primary%3AVaults/document/primary%3AVaults%2FClaus.kdbx")
    private val other = VaultLocation("content://docs/tree/primary%3AVaults/document/primary%3AVaults%2FOther.kdbx")
    private val first = fingerprint(1)
    private val second = fingerprint(2)

    @Test
    fun whatWasRecordedIsMerged() {
        val ledger = FileMergedCopies(folder.newFolder())

        assertFalse(ledger.isMerged(location, first))

        ledger.record(location, listOf(first, second))

        assertTrue(ledger.isMerged(location, first))
        assertTrue(ledger.isMerged(location, second))
        assertFalse(ledger.isMerged(location, fingerprint(3)))
    }

    @Test
    fun eachVaultKeepsItsOwnList() {
        val ledger = FileMergedCopies(folder.newFolder())

        ledger.record(location, listOf(first))

        assertTrue(ledger.isMerged(location, first))
        assertFalse(ledger.isMerged(other, first))
    }

    @Test
    fun onlyTheNewestTwoHundredFingerprintsAreKept() {
        val directory = folder.newFolder()
        val ledger = FileMergedCopies(directory)

        ledger.record(location, (1..LIMIT + DROPPED).map(::fingerprint))

        assertFalse(ledger.isMerged(location, fingerprint(DROPPED)))
        assertTrue(ledger.isMerged(location, fingerprint(DROPPED + 1)))
        assertTrue(ledger.isMerged(location, fingerprint(LIMIT + DROPPED)))
        assertEquals(LIMIT, linesOf(directory).size)
    }

    @Test
    fun theLedgerSurvivesANewInstance() {
        val directory = folder.newFolder()
        FileMergedCopies(directory).record(location, listOf(first))

        assertTrue(FileMergedCopies(directory).isMerged(location, first))
    }

    @Test
    fun recordingTheSameFingerprintTwiceKeepsItOnce() {
        val directory = folder.newFolder()
        val ledger = FileMergedCopies(directory)

        ledger.record(location, listOf(first))
        ledger.record(location, listOf(first, second))

        assertEquals(listOf(first.hex, second.hex), linesOf(directory))
    }

    private fun fingerprint(index: Int): Fingerprint = Fingerprint("%064x".format(index))

    /** The lines of the only ledger in [directory], which is where the fingerprints must be. */
    private fun linesOf(directory: File): List<String> {
        val file =
            directory
                .listFiles { _, name -> name.endsWith(SUFFIX) }
                ?.single()
                ?: return emptyList()
        return file.readLines().filter { it.isNotBlank() }
    }

    private companion object {
        const val SUFFIX = ".merged"
        const val LIMIT = 200
        const val DROPPED = 5
    }
}
