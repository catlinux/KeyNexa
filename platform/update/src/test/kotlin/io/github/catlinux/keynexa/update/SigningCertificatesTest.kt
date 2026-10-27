package io.github.catlinux.keynexa.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningCertificatesTest {
    private val ours = byteArrayOf(1, 2, 3)
    private val other = byteArrayOf(9, 9, 9)
    private val second = byteArrayOf(4, 5, 6)

    @Test
    fun theSameCertificateMatches() {
        assertTrue(SigningCertificates.sameSigners(listOf(ours), listOf(ours.copyOf())))
    }

    @Test
    fun aDifferentCertificateDoesNot() {
        assertFalse(SigningCertificates.sameSigners(listOf(other), listOf(ours)))
    }

    @Test
    fun anEmptySideNeverMatches() {
        assertFalse(SigningCertificates.sameSigners(emptyList(), emptyList()))
        assertFalse(SigningCertificates.sameSigners(listOf(ours), emptyList()))
        assertFalse(SigningCertificates.sameSigners(emptyList(), listOf(ours)))
    }

    @Test
    fun theOrderOfSeveralSignersDoesNotMatter() {
        assertTrue(SigningCertificates.sameSigners(listOf(ours, second), listOf(second, ours)))
    }

    @Test
    fun anExtraOrMissingSignerDoesNotMatch() {
        assertFalse(SigningCertificates.sameSigners(listOf(ours, second), listOf(ours)))
        assertFalse(SigningCertificates.sameSigners(listOf(ours), listOf(ours, other)))
    }
}
