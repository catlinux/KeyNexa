package io.github.catlinux.keynexa.app.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricComponentsTest {
    @Test
    fun roundTripsPasswordAndKey() {
        val plain = BiometricComponents.encode(ByteArray(32) { 1 }, ByteArray(32) { 2 })
        val credentials = BiometricComponents.decode(plain)!!

        assertTrue(credentials.hasPassword && credentials.hasKeyFile && credentials.isPreHashed)
        assertArrayEquals(ByteArray(32) { 1 }, credentials.passwordHashCopy())
        assertArrayEquals(ByteArray(32) { 2 }, credentials.keyHashCopy())
    }

    @Test
    fun roundTripsOnlyOneComponent() {
        val credentials = BiometricComponents.decode(BiometricComponents.encode(null, ByteArray(32) { 5 }))!!

        assertFalse(credentials.hasPassword)
        assertArrayEquals(ByteArray(32) { 5 }, credentials.keyHashCopy())
    }

    @Test
    fun malformedDataIsRejected() {
        assertNull(BiometricComponents.decode(ByteArray(0)))
        assertNull(BiometricComponents.decode(byteArrayOf(0)))
        assertNull(BiometricComponents.decode(byteArrayOf(1) + ByteArray(31)))
        assertNull(BiometricComponents.decode(byteArrayOf(3) + ByteArray(32)))
    }
}
