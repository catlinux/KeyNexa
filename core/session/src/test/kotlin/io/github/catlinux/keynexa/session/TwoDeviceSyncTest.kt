package io.github.catlinux.keynexa.session

import io.github.catlinux.keynexa.kdbx.KotpassKdbxEngine
import io.github.catlinux.keynexa.platform.FileBackupStore
import io.github.catlinux.keynexa.platform.FileSaveJournal
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudCapabilities
import io.github.catlinux.keynexa.sync.CloudContent
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.sync.CloudPreconditionFailed
import io.github.catlinux.keynexa.sync.CloudSyncer
import io.github.catlinux.keynexa.sync.InMemoryCloudSyncState
import io.github.catlinux.keynexa.sync.KeePassMerger
import io.github.catlinux.keynexa.sync.SafeSaver
import io.github.catlinux.keynexa.sync.SaveRecovery
import io.github.catlinux.keynexa.sync.SyncIds
import io.github.catlinux.keynexa.sync.SyncResult
import io.github.catlinux.keynexa.sync.VaultReader
import io.github.catlinux.keynexa.vault.Argon2Variant
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.FileMetadata
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.NewVault
import io.github.catlinux.keynexa.vault.SiblingFile
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.VaultEditor
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.Executor

/**
 * Two devices with their own copy of one vault, each a real [VaultSession] on the real KDBX engine, synchronising
 * through a cloud store that honours conditional writes (ADR-0009). Everything but the network transport is real.
 */
class TwoDeviceSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val engine = KotpassKdbxEngine()
    private val cheapKdf = Kdf.Argon2(Argon2Variant.Argon2id, 1024L * 1024L, 1L, 1, 19)
    private val cloud = ConditionalCloud()

    private fun credentials() = Credentials.of(password = "test-password-1".toCharArray())

    private inner class Device(
        name: String,
    ) {
        val location = VaultLocation("memory://$name.kdbx")
        val known = KnownVault(name, location)
        val files = HashMap<VaultLocation, ByteArray>()
        var writes = 0
        val state = InMemoryCloudSyncState()

        val storage =
            object : StoragePort {
                override fun read(location: VaultLocation): ByteArray =
                    files[location]?.copyOf() ?: throw IOException("missing")

                override fun metadata(location: VaultLocation) = FileMetadata(files[location]?.size?.toLong(), null)

                override fun lastModified(location: VaultLocation): Long? = null

                override fun listSiblings(location: VaultLocation): List<SiblingFile> = emptyList()

                override fun name(location: VaultLocation): String = location.value.substringAfterLast('/')

                override fun delete(location: VaultLocation) {
                    files.remove(location)
                }

                override fun write(
                    location: VaultLocation,
                    data: ByteArray,
                ) {
                    writes++
                    files[location] = data.copyOf()
                }
            }

        private val backups = FileBackupStore(folder.newFolder())
        private val journal = FileSaveJournal(folder.newFolder())
        val session =
            VaultSession(
                storage = storage,
                engine = engine,
                readers = VaultReaders(VaultReader(storage, engine, retryDelaysMillis = emptyList())),
                recovery = SaveRecovery(storage, engine, backups, journal),
                saver = SafeSaver(storage, engine, backups, journal, merger = KeePassMerger()),
                backups = backups,
                vaultMerger = KeePassMerger(),
                executor = Executor { it.run() },
            )
        private val local = SessionSyncLocal(session, storage)

        fun create() = session.create(known, NewVault().create("Test", cheapKdf), credentials())

        fun unlockFile(bytes: ByteArray) {
            files[location] = bytes.copyOf()
            session.unlock(known, credentials())
        }

        fun add(title: String) {
            val root = (session.state.value as SessionState.Open).current.root.id
            session.apply { VaultEditor().addEntry(it, root, title) }
        }

        fun titles(): Set<String> =
            (session.state.value as SessionState.Open)
                .current.root.entries
                .map { it.title }
                .toSet()

        fun histories(): Int =
            (session.state.value as SessionState.Open)
                .current.root.entries
                .sumOf { it.history.size }

        fun sync(): SyncResult = CloudSyncer(cloud, state).sync(session.syncId(), local)
    }

    @Test
    fun twoDevicesEditingApartConvergeAndThenAreQuiet() {
        val a = Device("a")
        a.create()
        a.add("First")
        val id = SyncIds.newId()
        assertTrue(a.session.assignSyncId(id))

        assertEquals(SyncResult.Uploaded, a.sync())
        val b = Device("b")
        b.unlockFile(cloud.bytesOf(SyncIds.objectName(id)))
        assertEquals(id, b.session.syncId())
        assertEquals(setOf("First"), b.titles())

        a.add("FromA")
        b.add("FromB")
        assertEquals(SyncResult.Exchanged, a.sync())
        assertEquals(SyncResult.Exchanged, b.sync())
        assertEquals(SyncResult.Downloaded, a.sync())

        val expected = setOf("First", "FromA", "FromB")
        assertEquals(expected, a.titles())
        assertEquals(expected, b.titles())
        assertEquals(0, a.histories())
        assertEquals(0, b.histories())

        val writesA = a.writes
        val writesB = b.writes
        val puts = cloud.puts
        assertEquals(SyncResult.UpToDate, a.sync())
        assertEquals(SyncResult.UpToDate, b.sync())
        assertEquals(writesA, a.writes)
        assertEquals(writesB, b.writes)
        assertEquals(puts, cloud.puts)
    }

    @Test
    fun aRoundWithNothingNewWritesNothingOnEitherSide() {
        val a = Device("a")
        a.create()
        val id = SyncIds.newId()
        assertTrue(a.session.assignSyncId(id))
        assertEquals(SyncResult.Uploaded, a.sync())
        val b = Device("b")
        b.unlockFile(cloud.bytesOf(SyncIds.objectName(id)))

        // Both synchronise repeatedly without any edit: the local files and the cloud object never change again.
        val writesBefore = a.writes + b.writes
        val putsBefore = cloud.puts
        repeat(3) {
            b.sync()
            a.sync()
        }

        assertEquals(writesBefore, a.writes + b.writes)
        assertEquals(putsBefore, cloud.puts)
    }
}

/** A cloud store with entity tags and conditional writes, as ADR-0009 requires of a service. */
private class ConditionalCloud : CloudBackend {
    override val capabilities = CloudCapabilities(conditionalWrites = true)
    private val objects = LinkedHashMap<String, ByteArray>()
    private val etags = HashMap<String, String>()
    private var counter = 0
    var puts = 0
        private set

    fun bytesOf(name: String): ByteArray = objects.getValue(name).copyOf()

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
        ifNoneMatch: Boolean,
    ): CloudObject {
        if (ifNoneMatch && objects.containsKey(name)) throw CloudPreconditionFailed("exists")
        if (ifMatch != null && etags[name] != ifMatch) throw CloudPreconditionFailed("stale")
        puts++
        objects[name] = content.copyOf()
        val etag = "etag-${++counter}"
        etags[name] = etag
        return CloudObject(name, content.size.toLong(), null, etag)
    }

    override fun head(name: String): CloudObject? =
        objects[name]?.let { CloudObject(name, it.size.toLong(), null, etags[name]) }

    override fun fetch(name: String): CloudContent? = objects[name]?.let { CloudContent(it.copyOf(), etags[name]) }

    override fun get(name: String): ByteArray = objects.getValue(name).copyOf()

    override fun list(prefix: String): List<CloudObject> =
        objects.keys.filter { it.startsWith(prefix) }.mapNotNull { head(it) }

    override fun delete(name: String) {
        objects.remove(name)
        etags.remove(name)
    }
}
