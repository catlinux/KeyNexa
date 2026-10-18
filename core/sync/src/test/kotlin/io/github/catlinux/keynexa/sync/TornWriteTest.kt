package io.github.catlinux.keynexa.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tells apart a file KeyNexa itself left half-written from a complete file another program wrote. Two KDBX files
 * agree on the fixed signature and format fields (12 bytes) and then diverge at their random seeds, so the check
 * needs 16 more bytes of agreement ([MIN_LEAD]).
 */
class TornWriteTest {
    private val signature =
        byteArrayOf(
            0x03,
            0xD9.toByte(),
            0xA2.toByte(),
            0x9A.toByte(),
            0x67,
            0xFB.toByte(),
            0x4B,
            0xB5.toByte(),
            0x00,
            0x04,
            0x00,
            0x01,
        )
    private val previous = signature + "previous-version-saved-long-ago-and-longer".toByteArray()
    private val outgoing = signature + "new-version-just-written-by-keynexa".toByteArray()

    @Test
    fun aPrefixOfOurOwnWriteIsOurs() {
        assertTrue(isTornByOwnWrite(outgoing.copyOf(OUR_PREFIX), outgoing, previous, hasCompleteHeader = true))
    }

    @Test
    fun ourWriteFollowedByTheOldTailIsOurs() {
        val file = outgoing + previous.copyOfRange(outgoing.size, previous.size)
        assertTrue(isTornByOwnWrite(file, outgoing, previous, hasCompleteHeader = true))
    }

    @Test
    fun ourPrefixFollowedByTheOldRemainderIsOurs() {
        val file = outgoing.copyOf(OUR_PREFIX) + previous.copyOfRange(OUR_PREFIX, previous.size)
        assertTrue(isTornByOwnWrite(file, outgoing, previous, hasCompleteHeader = true))
    }

    @Test
    fun agreeLessThanTheLeadBeyondTheHeaderIsAnotherProgramsFile() {
        // 8 bytes past the shared header are not enough: this could be any KDBX file.
        val file = outgoing.copyOf(HEADER + 8) + "a-different-tail-from-somewhere-else".toByteArray()
        assertFalse(isTornByOwnWrite(file, outgoing, previous, hasCompleteHeader = true))
    }

    @Test
    fun anotherProgramsFileWithTheSameFixedHeaderIsNotOurs() {
        val file = signature + "a-complete-file-written-by-another-program".toByteArray()
        assertFalse(isTornByOwnWrite(file, outgoing, previous, hasCompleteHeader = true))
    }

    @Test
    fun aFileIdenticalToThePreviousIsNotOurs() {
        assertFalse(isTornByOwnWrite(previous.copyOf(), outgoing, previous, hasCompleteHeader = true))
    }

    @Test
    fun aFileWithoutACompleteHeaderIsAlwaysTorn() {
        // Empty or cut before the header ends: it cannot be another program's vault, whatever its prefix says.
        assertTrue(isTornByOwnWrite(ByteArray(0), outgoing, previous, hasCompleteHeader = false))
        assertTrue(isTornByOwnWrite(previous.copyOf(SHORT_CUT), outgoing, previous, hasCompleteHeader = false))
    }

    private companion object {
        const val SHORT_CUT = 20
        const val HEADER = 12
        const val OUR_PREFIX = 30
    }
}
