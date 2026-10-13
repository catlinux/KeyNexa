package io.github.catlinux.keynexa.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The fingerprint is the only thing the pipeline compares, so its exact value and display matter. */
class FingerprintTest {
    @Test
    fun equalsTheSha256OfTheBytes() {
        assertEquals(SHA256_OF_ABC, Fingerprint.of("abc".toByteArray()).hex)
    }

    @Test
    fun equalBytesGiveEqualFingerprintsAndDifferentBytesDoNot() {
        assertEquals(Fingerprint.of(byteArrayOf(1, 2, 3)), Fingerprint.of(byteArrayOf(1, 2, 3)))
        assertNotEquals(Fingerprint.of(byteArrayOf(1, 2, 3)), Fingerprint.of(byteArrayOf(1, 2, 4)))
    }

    @Test
    fun toStringShowsOnlyTheFirstTwelveHexDigits() {
        assertEquals("Fingerprint(ba7816bf8f01…)", Fingerprint.of("abc".toByteArray()).toString())
    }

    private companion object {
        const val SHA256_OF_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }
}
