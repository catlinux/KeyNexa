package io.github.catlinux.keynexa.windows

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The DPAPI secret box: round trip, the version byte and every way it can refuse a blob. */
class DpapiSecretBoxTest {
    private val box = DpapiSecretBox()

    @Test
    fun roundTrip() {
        assumeWindows()
        val plain = "a secret".toByteArray()

        val sealed = box.seal(plain)

        assertArrayEquals(plain, box.open(sealed))
    }

    @Test
    fun sealedDiffersFromPlain() {
        assumeWindows()
        val plain = "a secret".toByteArray()

        assertFalse(plain.contentEquals(box.seal(plain)))
    }

    @Test
    fun openOfAnAlteredBlobAnswersNull() {
        assumeWindows()
        val sealed = box.seal("a secret".toByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()

        assertNull(box.open(sealed))
    }

    @Test
    fun openOfAWrongVersionAnswersNull() {
        assumeWindows()
        val sealed = box.seal("a secret".toByteArray())
        sealed[0] = 2

        assertNull(box.open(sealed))
    }

    @Test
    fun openOfAnEmptyArrayAnswersNull() {
        assumeWindows()

        assertNull(box.open(ByteArray(0)))
    }

    private fun assumeWindows() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
    }
}
