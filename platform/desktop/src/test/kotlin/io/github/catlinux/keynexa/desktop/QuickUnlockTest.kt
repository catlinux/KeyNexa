package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.vault.Credentials
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** The quick-unlock policy: HKDF vectors, the round trip, the time limit, cancellation and the wipes. */
class QuickUnlockTest {
    private var now = START

    @Test
    fun hkdfMatchesRfc5869Case1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected =
            hex(
                "3cb25f25faacd57a90434f64d0362f2a" +
                    "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                    "34007208d5b887185865",
            )

        assertArrayEquals(expected, Hkdf.sha256(ikm, salt, info, 42))
    }

    @Test
    fun hkdfMatchesRfc5869Case3() {
        val ikm = ByteArray(22) { 0x0b }
        val expected =
            hex(
                "8da4e775a563c18f715f802a063c5a31" +
                    "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                    "9d201395faa4b61a96c8",
            )

        assertArrayEquals(expected, Hkdf.sha256(ikm, ByteArray(0), ByteArray(0), 42))
    }

    @Test
    fun armThenOpenReturnsTheSameHashes() {
        val signer = FakeSigner()
        val policy = quickUnlock(signer)
        val original = credentials()
        val expectedPassword = original.passwordHashCopy()!!
        val expectedKey = original.keyHashCopy()!!
        try {
            assertEquals(ArmResult.Armed, policy.arm(LOCATION, original, HOURS))

            val opened = policy.open(LOCATION)

            assertTrue(opened is OpenResult.Opened)
            val credentials = (opened as OpenResult.Opened).credentials
            try {
                assertArrayEquals(expectedPassword, credentials.passwordHashCopy())
                assertArrayEquals(expectedKey, credentials.keyHashCopy())
            } finally {
                credentials.wipe()
            }
        } finally {
            expectedPassword.fill(0)
            expectedKey.fill(0)
            original.wipe()
        }
    }

    @Test
    fun validOneMillisecondBeforeTheLimitAndNotAtIt() {
        val policy = quickUnlock()
        assertEquals(ArmResult.Armed, policy.arm(LOCATION, credentials(), HOURS))

        now = START + HOURS * HOUR_MILLIS - 1
        assertTrue(policy.isArmedFor(LOCATION))

        now = START + HOURS * HOUR_MILLIS
        assertFalse(policy.isArmedFor(LOCATION))
        assertNull(policy.heldStateForTest())
    }

    @Test
    fun anotherLocationDoesNotOpenAndDisarms() {
        val policy = quickUnlock()
        assertEquals(ArmResult.Armed, policy.arm(LOCATION, credentials(), HOURS))

        assertTrue(policy.open(OTHER_LOCATION) is OpenResult.Failed)

        assertNull(policy.heldStateForTest())
        assertFalse(policy.isArmedFor(LOCATION))
    }

    @Test
    fun cancellingOpenKeepsTheStateArmedForARetry() {
        val signer = FakeSigner()
        val policy = quickUnlock(signer)
        assertEquals(ArmResult.Armed, policy.arm(LOCATION, credentials(), HOURS))

        signer.mode = FakeSigner.Mode.Cancel
        assertEquals(OpenResult.Cancelled, policy.open(LOCATION))
        assertNotNull(policy.heldStateForTest())
        assertTrue(policy.isArmedFor(LOCATION))

        signer.mode = FakeSigner.Mode.Sign
        val opened = policy.open(LOCATION)
        assertTrue(opened is OpenResult.Opened)
        (opened as OpenResult.Opened).credentials.wipe()
    }

    @Test
    fun aSignerFailureInOpenDisarms() {
        val signer = FakeSigner()
        val policy = quickUnlock(signer)
        assertEquals(ArmResult.Armed, policy.arm(LOCATION, credentials(), HOURS))

        signer.mode = FakeSigner.Mode.Fail
        val failed = policy.open(LOCATION)

        assertTrue(failed is OpenResult.Failed)
        assertEquals("hello error", (failed as OpenResult.Failed).reason)
        assertNull(policy.heldStateForTest())
    }

    @Test
    fun cancellingTheSignerInArmLeavesNothingArmed() {
        val signer = FakeSigner().apply { mode = FakeSigner.Mode.Cancel }
        val policy = quickUnlock(signer)

        assertEquals(ArmResult.Cancelled, policy.arm(LOCATION, credentials(), HOURS))

        assertNull(policy.heldStateForTest())
        assertFalse(policy.isArmedFor(LOCATION))
    }

    @Test
    fun aSignerFailureInArmFails() {
        val signer = FakeSigner().apply { mode = FakeSigner.Mode.Fail }
        val policy = quickUnlock(signer)

        val failed = policy.arm(LOCATION, credentials(), HOURS)

        assertTrue(failed is ArmResult.Failed)
        assertEquals("hello error", (failed as ArmResult.Failed).reason)
        assertNull(policy.heldStateForTest())
    }

    @Test
    fun aTamperedCiphertextFailsAndDisarms() {
        val policy = quickUnlock()
        assertEquals(ArmResult.Armed, policy.arm(LOCATION, credentials(), HOURS))
        val held = policy.heldStateForTest()!!
        held.ciphertext[0] = (held.ciphertext[0].toInt() xor FLIP_BIT).toByte()

        assertTrue(policy.open(LOCATION) is OpenResult.Failed)
        assertNull(policy.heldStateForTest())
    }

    @Test
    fun disarmZeroesTheHeldArrays() {
        val policy = quickUnlock()
        assertEquals(ArmResult.Armed, policy.arm(LOCATION, credentials(), HOURS))
        val held = policy.heldStateForTest()!!

        policy.disarm()

        assertTrue(held.challenge.all { it == 0.toByte() })
        assertTrue(held.iv.all { it == 0.toByte() })
        assertTrue(held.ciphertext.all { it == 0.toByte() })
        assertNull(policy.heldStateForTest())
    }

    @Test
    fun aSuccessfulOpenDoesNotExtendTheArmingTime() {
        val policy = quickUnlock()
        assertEquals(ArmResult.Armed, policy.arm(LOCATION, credentials(), HOURS))
        now = START + HOURS * HOUR_MILLIS / 2

        val opened = policy.open(LOCATION)
        assertTrue(opened is OpenResult.Opened)
        (opened as OpenResult.Opened).credentials.wipe()

        now = START + HOURS * HOUR_MILLIS
        assertFalse(policy.isArmedFor(LOCATION))
    }

    @Test
    fun theFakeSignatureIsWipedAfterArm() {
        val signer = FakeSigner()
        val policy = quickUnlock(signer)

        assertEquals(ArmResult.Armed, policy.arm(LOCATION, credentials(), HOURS))

        val signature = requireNotNull(signer.lastSignature)
        assertTrue(signature.all { it == 0.toByte() })
    }

    private fun quickUnlock(signer: HelloSigner = FakeSigner()): QuickUnlock = QuickUnlock(signer, { now })

    private fun credentials(): Credentials =
        Credentials.ofHashes(
            ByteArray(HASH_SIZE) { (it + 1).toByte() },
            ByteArray(HASH_SIZE) { (it + 0x40).toByte() },
        )

    private companion object {
        const val START = 1_000_000L
        const val HOUR_MILLIS = 3_600_000L
        const val HOURS = 8
        const val HASH_SIZE = 32
        const val FLIP_BIT = 0x01
        const val LOCATION = "vaults/main.kdbx"
        const val OTHER_LOCATION = "vaults/other.kdbx"

        fun hex(text: String): ByteArray =
            ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

/** A deterministic stand-in for Windows Hello: SHA-256 of the challenge, or a cancel or a failure on demand. */
private class FakeSigner(
    var mode: Mode = Mode.Sign,
) : HelloSigner {
    enum class Mode {
        Sign,
        Cancel,
        Fail,
    }

    /** The array handed to the policy; it must be all zeroes once the policy has wiped it. */
    var lastSignature: ByteArray? = null
        private set

    override fun sign(challenge: ByteArray): HelloResult =
        when (mode) {
            Mode.Sign -> {
                val signature = MessageDigest.getInstance("SHA-256").digest(challenge)
                lastSignature = signature
                HelloResult.Signed(signature)
            }
            Mode.Cancel -> HelloResult.Cancelled
            Mode.Fail -> HelloResult.Failed("hello error")
        }
}
