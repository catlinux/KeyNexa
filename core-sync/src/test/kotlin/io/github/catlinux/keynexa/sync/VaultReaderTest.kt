package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads tolerate a file that is still being written: integrity failures are retried, others are not. */
class VaultReaderTest {
    private val location = VaultLocation("vault.kdbx")
    private val credentials = Credentials.of(password = "correct horse battery".toCharArray())

    @Test
    fun aCorrectFileIsOpenedOnTheFirstAttemptWithoutWaiting() {
        val engine = FakeKdbxEngine()
        val vault = testVault("v")
        val bytes = engine.encode(vault, credentials)
        val storage = FakeStorage(mapOf(location to bytes))
        val sleeper = RecordingSleeper()

        val loaded = VaultReader(storage, engine, sleeper).open(location, credentials)

        assertSame(vault, loaded.vault)
        assertEquals(Fingerprint.of(bytes), loaded.fingerprint)
        assertEquals(emptyList<Long>(), sleeper.sleeps)
    }

    @Test
    fun aFileCorruptedTwiceIsOpenedOnTheThirdAttemptAfterTwoWaits() {
        val engine = FakeKdbxEngine()
        val vault = testVault("v")
        val good = engine.encode(vault, credentials)
        val storage = FakeStorage(mapOf(location to damaged(engine.encode(vault, credentials))))
        storage.beforeRead(3) { storage.files[location] = good }
        val sleeper = RecordingSleeper()

        val loaded = VaultReader(storage, engine, sleeper).open(location, credentials)

        assertSame(vault, loaded.vault)
        assertEquals(listOf(1_000L, 2_000L), sleeper.sleeps)
    }

    @Test
    fun aFileThatStaysCorruptedFailsAfterAllFourWaits() {
        val engine = FakeKdbxEngine()
        val storage = FakeStorage(mapOf(location to damaged(engine.encode(testVault("v"), credentials))))
        val sleeper = RecordingSleeper()

        assertThrows(VaultException.Corrupted::class.java) {
            VaultReader(storage, engine, sleeper).open(location, credentials)
        }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), sleeper.sleeps)
    }

    @Test
    fun aTransientStorageFailureIsRetried() {
        val engine = FakeKdbxEngine()
        val vault = testVault("v")
        val storage = FakeStorage(mapOf(location to engine.encode(vault, credentials)))
        storage.failReadAt = 1
        val sleeper = RecordingSleeper()

        val loaded = VaultReader(storage, engine, sleeper).open(location, credentials)

        assertSame(vault, loaded.vault)
        assertEquals(listOf(1_000L), sleeper.sleeps)
    }

    @Test
    fun wrongCredentialsOnKdbx4AreThrownAtOnceEvenWithKnownGoodCredentials() {
        val engine = FakeKdbxEngine()
        val otherKey = Credentials.of(password = "another key".toCharArray())
        val storage = FakeStorage(mapOf(location to engine.encode(testVault("v"), otherKey)))
        val sleeper = RecordingSleeper()

        assertThrows(VaultException.WrongCredentials::class.java) {
            VaultReader(storage, engine, sleeper).open(location, credentials, credentialsKnownGood = true)
        }

        assertEquals(emptyList<Long>(), sleeper.sleeps)
    }

    @Test
    fun wrongCredentialsOnKdbx3WithKnownGoodCredentialsAreRetriedAsDamage() {
        val engine = FakeKdbxEngine().also { it.kdbx3 = true }
        val storage = FakeStorage(mapOf(location to damaged(engine.encode(testVault("v"), credentials))))
        val sleeper = RecordingSleeper()

        assertThrows(VaultException.WrongCredentials::class.java) {
            VaultReader(storage, engine, sleeper).open(location, credentials, credentialsKnownGood = true)
        }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), sleeper.sleeps)
    }

    @Test
    fun wrongCredentialsOnKdbx3WithoutKnownGoodCredentialsAreThrownAtOnce() {
        val engine = FakeKdbxEngine().also { it.kdbx3 = true }
        val storage = FakeStorage(mapOf(location to damaged(engine.encode(testVault("v"), credentials))))
        val sleeper = RecordingSleeper()

        assertThrows(VaultException.WrongCredentials::class.java) {
            VaultReader(storage, engine, sleeper).open(location, credentials)
        }

        assertEquals(emptyList<Long>(), sleeper.sleeps)
    }

    @Test
    fun aFileThatIsNotAVaultIsNotRetried() {
        val engine = FakeKdbxEngine()
        val storage = FakeStorage(mapOf(location to "not a vault at all".toByteArray()))
        val sleeper = RecordingSleeper()

        assertThrows(VaultException.NotAVault::class.java) {
            VaultReader(storage, engine, sleeper).open(location, credentials)
        }

        assertEquals(emptyList<Long>(), sleeper.sleeps)
    }

    @Test
    fun hasChangedDetectsFingerprintChanges() {
        val engine = FakeKdbxEngine()
        val bytes = engine.encode(testVault("v"), credentials)
        val storage = FakeStorage(mapOf(location to bytes))
        val reader = VaultReader(storage, engine, RecordingSleeper())

        assertFalse(reader.hasChanged(location, Fingerprint.of(bytes)))

        storage.files[location] = bytes + byteArrayOf(7)

        assertTrue(reader.hasChanged(location, Fingerprint.of(bytes)))
    }

    /** Breaks the trailing CRC so that the file reads as damaged. */
    private fun damaged(bytes: ByteArray): ByteArray = bytes.also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
}
