package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.FileMetadata
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Reads tolerate a file that is still being written: integrity failures are retried, others are not. */
class VaultReaderTest {
    private val location = VaultLocation("vault.kdbx")
    private val credentials = Credentials.of(password = "correct horse battery".toCharArray())
    private val at: Instant = Instant.parse("2026-10-16T10:00:00Z")

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

    @Test
    fun equalMetadataAvoidsReadingTheFile() {
        val storage = storageWithMetadata()
        val reader = VaultReader(storage, FakeKdbxEngine(), RecordingSleeper())
        val metadata = storage.metadata(location)
        val readsBefore = readsOf(storage)

        val changed = reader.hasChanged(location, Fingerprint.of(storage.files.getValue(location)), metadata)

        assertFalse(changed)
        assertEquals(readsBefore, readsOf(storage))
    }

    @Test
    fun aDifferentSizeReadsTheFile() {
        val storage = storageWithMetadata()
        val reader = VaultReader(storage, FakeKdbxEngine(), RecordingSleeper())
        val known = Fingerprint.of(storage.files.getValue(location))
        val metadata = storage.metadata(location)
        storage.files[location] = storage.files.getValue(location) + byteArrayOf(7)
        val readsBefore = readsOf(storage)

        assertTrue(reader.hasChanged(location, known, metadata))
        assertTrue(readsOf(storage) > readsBefore)
    }

    @Test
    fun aDifferentModificationTimeReadsTheFile() {
        val storage = storageWithMetadata()
        val reader = VaultReader(storage, FakeKdbxEngine(), RecordingSleeper())
        val known = Fingerprint.of(storage.files.getValue(location))
        val metadata = storage.metadata(location)
        storage.metadataLastModified = at.plusSeconds(5)
        val readsBefore = readsOf(storage)

        // The content is the same here, so the full read answers "unchanged", but it did happen.
        assertFalse(reader.hasChanged(location, known, metadata))
        assertTrue(readsOf(storage) > readsBefore)
    }

    @Test
    fun missingMetadataFallsBackToTheFullRead() {
        val storage = storageWithMetadata()
        val reader = VaultReader(storage, FakeKdbxEngine(), RecordingSleeper())
        val known = Fingerprint.of(storage.files.getValue(location))
        val readsBefore = readsOf(storage)

        assertFalse(reader.hasChanged(location, known, FileMetadata(sizeBytes = null, lastModified = at)))
        assertTrue(readsOf(storage) > readsBefore)
    }

    @Test
    fun aForcedCheckReadsEvenWhenTheMetadataMatches() {
        val storage = storageWithMetadata()
        val reader = VaultReader(storage, FakeKdbxEngine(), RecordingSleeper())
        val metadata = storage.metadata(location)
        val readsBefore = readsOf(storage)

        val changed =
            reader.hasChanged(
                location,
                Fingerprint.of(storage.files.getValue(location)),
                metadata,
                forceFull = true,
            )

        assertFalse(changed)
        assertTrue(readsOf(storage) > readsBefore)
    }

    private fun storageWithMetadata(): FakeStorage {
        val engine = FakeKdbxEngine()
        val bytes = engine.encode(testVault("v"), credentials)
        return FakeStorage(mapOf(location to bytes)).apply { metadataLastModified = at }
    }

    private fun readsOf(storage: FakeStorage): Int = storage.operations.count { it.startsWith("read#") }

    /** Breaks the trailing CRC so that the file reads as damaged. */
    private fun damaged(bytes: ByteArray): ByteArray = bytes.also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
}
