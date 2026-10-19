package io.github.catlinux.keynexa.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val ID = "0123456789abcdef0123456789abcdef"
private val NAME = SyncIds.objectName(ID)
private val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC)

/** The reconciler: the shortcut, the merge-before-upload rule, and how races and errors end up (ADR-0009). */
class CloudSyncerTest {
    @Test
    fun anAbsentObjectIsCreatedFromTheLocalFile() {
        val backend = RacingCloudBackend()
        val state = InMemoryCloudSyncState()
        val local = FakeSyncLocal(setOf("base"))

        val result = CloudSyncer(backend, state, CLOCK).sync(ID, local)

        assertEquals(SyncResult.Uploaded, result)
        assertEquals(setOf("base"), decode(backend.contentOf(NAME)!!))
        assertEquals(1, backend.puts)
        assertNotNull(state.lastEtag(ID))
        assertEquals(Fingerprint.of(encode(setOf("base"))), state.lastLocalFingerprint(ID))
        assertNull(state.lastError(ID))
    }

    @Test
    fun aMissingOrMalformedIdWritesNothing() {
        val backend = RacingCloudBackend()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, InMemoryCloudSyncState(), CLOCK)

        assertEquals(SyncResult.Failed(SyncError.InvalidSyncId), syncer.sync(null, local))
        assertEquals(SyncResult.Failed(SyncError.InvalidSyncId), syncer.sync("not-an-id", local))
        assertEquals(SyncResult.Failed(SyncError.InvalidSyncId), syncer.sync(ID.uppercase(), local))

        assertEquals(0, backend.heads)
        assertEquals(0, backend.puts)
        assertEquals(0, local.absorbed)
    }

    @Test
    fun nothingChangedNeitherFetchesNorPuts() {
        val backend = RacingCloudBackend()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, InMemoryCloudSyncState(), CLOCK)
        assertEquals(SyncResult.Uploaded, syncer.sync(ID, local))
        val heads = backend.heads
        val fetches = backend.fetches
        val puts = backend.puts

        val again = syncer.sync(ID, local)

        assertEquals(SyncResult.UpToDate, again)
        assertEquals(heads + 1, backend.heads)
        assertEquals(fetches, backend.fetches)
        assertEquals(puts, backend.puts)
    }

    @Test
    fun oneRaceRetriesAndConverges() {
        val backend = RacingCloudBackend()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, InMemoryCloudSyncState(), CLOCK)
        syncer.sync(ID, local)
        local.tokens = setOf("base", "a")
        backend.races += { backend.foreignWrite(NAME, encode(setOf("base", "x"))) }

        val result = syncer.sync(ID, local)

        assertEquals(SyncResult.Exchanged, result)
        assertEquals(setOf("base", "a", "x"), local.tokens)
        assertEquals(setOf("base", "a", "x"), decode(backend.contentOf(NAME)!!))
        assertEquals(3, backend.puts)
    }

    @Test
    fun threeRacesEndBusyWithoutTouchingTheRemote() {
        val backend = RacingCloudBackend()
        val state = InMemoryCloudSyncState()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, state, CLOCK)
        syncer.sync(ID, local)
        local.tokens = setOf("base", "a")
        repeat(3) { backend.races += { backend.foreignWrite(NAME, encode(setOf("base", "x"))) } }

        val result = syncer.sync(ID, local)

        assertEquals(SyncResult.Failed(SyncError.Busy), result)
        assertEquals(SyncError.Busy, state.lastError(ID))
        // The remote object still holds the foreign write; the local file kept the merge that could not be uploaded.
        assertEquals(setOf("base", "x"), decode(backend.contentOf(NAME)!!))
        assertEquals(setOf("base", "a", "x"), local.tokens)
    }

    @Test
    fun anotherPasswordAndAnotherVaultWriteNothing() {
        val backend = RacingCloudBackend()
        val state = InMemoryCloudSyncState()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, state, CLOCK)
        syncer.sync(ID, local)
        val stored = backend.contentOf(NAME)!!
        local.tokens = setOf("base", "a")

        local.absorbAnswer = Absorbed.OtherPassword
        assertEquals(SyncResult.Failed(SyncError.OtherPassword), syncer.sync(ID, local))
        assertEquals(SyncError.OtherPassword, state.lastError(ID))
        assertEquals(stored.toList(), backend.contentOf(NAME)!!.toList())

        local.absorbAnswer = Absorbed.NotSameVault
        assertEquals(SyncResult.Failed(SyncError.NotSameVault), syncer.sync(ID, local))
        assertEquals(SyncError.NotSameVault, state.lastError(ID))
        assertEquals(1, backend.puts)
    }

    @Test
    fun aRemoteThatLacksNothingIsNotUploaded() {
        val backend = RacingCloudBackend()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, InMemoryCloudSyncState(), CLOCK)
        syncer.sync(ID, local)
        backend.foreignWrite(NAME, encode(setOf("base", "a")))

        val result = syncer.sync(ID, local)

        assertEquals(SyncResult.Downloaded, result)
        assertEquals(setOf("base", "a"), local.tokens)
        assertEquals(1, backend.puts)
    }

    @Test
    fun postponedRecordsNoError() {
        val backend = RacingCloudBackend()
        val state = InMemoryCloudSyncState()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, state, CLOCK)
        syncer.sync(ID, local)
        val success = state.lastSuccess(ID)
        local.tokens = setOf("base", "a")
        local.absorbAnswer = Absorbed.Postponed

        val result = syncer.sync(ID, local)

        assertEquals(SyncResult.Postponed, result)
        assertNull(state.lastError(ID))
        assertEquals(success, state.lastSuccess(ID))
        assertEquals(1, backend.puts)
    }

    @Test
    fun aCredentialsFailureIsNotAuthorized() {
        val backend = RacingCloudBackend()
        val state = InMemoryCloudSyncState()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, state, CLOCK)
        syncer.sync(ID, local)
        local.tokens = setOf("base", "a")
        backend.failFetch = CloudAuthException("denied")

        val result = syncer.sync(ID, local)

        assertEquals(SyncResult.Failed(SyncError.NotAuthorized), result)
        assertEquals(SyncError.NotAuthorized, state.lastError(ID))
        assertEquals(1, backend.puts)
    }

    @Test
    fun anotherIOExceptionIsUnavailable() {
        val backend = RacingCloudBackend()
        val state = InMemoryCloudSyncState()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, state, CLOCK)
        backend.failHead = IOException("no network")

        val result = syncer.sync(ID, local)

        assertEquals(SyncResult.Failed(SyncError.Unavailable), result)
        assertEquals(SyncError.Unavailable, state.lastError(ID))
        assertEquals(0, backend.puts)
    }

    @Test
    fun aServiceWithoutEntityTagsIsNotSupported() {
        val backend = RacingCloudBackend()
        val state = InMemoryCloudSyncState()
        backend.hideEtags = true

        val result = CloudSyncer(backend, state, CLOCK).sync(ID, FakeSyncLocal(setOf("base")))

        assertEquals(SyncResult.Failed(SyncError.NotSupported), result)
        assertEquals(SyncError.NotSupported, state.lastError(ID))
    }

    @Test
    fun twoDevicesThatEditDifferentThingsConverge() {
        val backend = RacingCloudBackend()
        val stateA = InMemoryCloudSyncState()
        val stateB = InMemoryCloudSyncState()
        val deviceA = FakeSyncLocal(setOf("base"))
        val deviceB = FakeSyncLocal(setOf("base"))
        val syncerA = CloudSyncer(backend, stateA, CLOCK)
        val syncerB = CloudSyncer(backend, stateB, CLOCK)
        assertEquals(SyncResult.Uploaded, syncerA.sync(ID, deviceA))
        assertEquals(SyncResult.Downloaded, syncerB.sync(ID, deviceB))
        deviceA.tokens = setOf("base", "a")
        deviceB.tokens = setOf("base", "b")

        // Round 1: each uploads what the other lacks; round 2: A catches up and B has nothing to do.
        assertEquals(SyncResult.Exchanged, syncerA.sync(ID, deviceA))
        assertEquals(SyncResult.Exchanged, syncerB.sync(ID, deviceB))
        assertEquals(SyncResult.Downloaded, syncerA.sync(ID, deviceA))
        assertEquals(SyncResult.UpToDate, syncerB.sync(ID, deviceB))

        // Round 3: both are the ones this device last synchronised, so nothing is read or written.
        assertEquals(SyncResult.UpToDate, syncerA.sync(ID, deviceA))
        assertEquals(SyncResult.UpToDate, syncerB.sync(ID, deviceB))
        assertEquals(setOf("base", "a", "b"), deviceA.tokens)
        assertEquals(setOf("base", "a", "b"), deviceB.tokens)
        assertEquals(setOf("base", "a", "b"), decode(backend.contentOf(NAME)!!))
    }

    @Test
    fun aRemoteObjectDeletedWhileItsTagWasRememberedIsRecreated() {
        val backend = RacingCloudBackend()
        val state = InMemoryCloudSyncState()
        val local = FakeSyncLocal(setOf("base"))
        val syncer = CloudSyncer(backend, state, CLOCK)
        syncer.sync(ID, local)
        backend.delete(NAME)

        val result = syncer.sync(ID, local)

        assertEquals(SyncResult.Uploaded, result)
        assertEquals(setOf("base"), decode(backend.contentOf(NAME)!!))
        assertEquals(2, backend.puts)
        assertNull(state.lastError(ID))
    }

    @Test
    fun syncIdsGeneratesAndReadsWellFormedIds() {
        val generated = SyncIds.newId()

        assertTrue(SyncIds.isValid(generated))
        assertEquals(generated, SyncIds.read(generated))
        assertNull(SyncIds.read(null))
        assertNull(SyncIds.read("not-an-id"))
        assertFalse(SyncIds.isMalformed(null))
        assertTrue(SyncIds.isMalformed("not-an-id"))
        assertFalse(SyncIds.isMalformed(generated))
        assertEquals("KeyNexa/sync/$generated.kdbx", SyncIds.objectName(generated))
    }

    @Test
    fun syncIdsReadsTheVaultCustomData() {
        val id = SyncIds.newId()

        assertEquals(id, SyncIds.readCustomData(mapOf(SyncIds.KEY to id)))
        assertNull(SyncIds.readCustomData(emptyMap()))
        assertFalse(SyncIds.isMalformedCustomData(mapOf(SyncIds.KEY to id)))
        assertTrue(SyncIds.isMalformedCustomData(mapOf(SyncIds.KEY to "bad")))
        assertFalse(SyncIds.isMalformedCustomData(emptyMap()))
    }
}

/**
 * A vault whose content is a set of tokens, so merging and equality are easy to reason about: merging is the union,
 * and the merged vault differs from the remote exactly when the remote lacks a token.
 */
private class FakeSyncLocal(
    var tokens: Set<String>,
    private val id: String? = ID,
) : SyncLocal {
    /** Overrides what [absorb] answers, for the cases where the merge itself fails. */
    var absorbAnswer: Absorbed? = null

    var absorbed: Int = 0
        private set

    var assigned: String? = null
        private set

    override fun syncId(): String? = id

    override fun currentFile(): SyncFile = SyncFile(encode(tokens))

    override fun absorb(
        bytes: ByteArray,
        expect: Expect,
    ): Absorbed {
        absorbed++
        absorbAnswer?.let { return it }
        val remote = decode(bytes)
        val merged = tokens + remote
        tokens = merged
        return Absorbed.Merged(SyncFile(encode(merged)), remoteLacksLocal = merged != remote)
    }

    override fun assignSyncId(id: String) {
        assigned = id
    }
}

/** A cloud store that changes its entity tag on every write and can inject foreign writes between fetch and put. */
private class RacingCloudBackend : CloudBackend {
    override val capabilities: CloudCapabilities = CloudCapabilities(conditionalWrites = true)

    private val objects = LinkedHashMap<String, ByteArray>()
    private val etags = HashMap<String, String>()
    private var writes = 0

    /** Foreign writes to run right after each fetch, in order; each entry fires once. */
    val races: ArrayDeque<() -> Unit> = ArrayDeque()

    var failHead: IOException? = null
    var failFetch: IOException? = null
    var failPut: IOException? = null

    /** When true the service hides entity tags, as a provider without them would. */
    var hideEtags = false

    var heads: Int = 0
        private set
    var fetches: Int = 0
        private set
    var puts: Int = 0
        private set

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
        ifNoneMatch: Boolean,
    ): CloudObject {
        puts++
        failPut?.let { throw it }
        if (ifNoneMatch && objects.containsKey(name)) throw CloudPreconditionFailed("an object already exists")
        if (ifMatch != null && etags[name] != ifMatch) throw CloudPreconditionFailed("stale entity tag")
        return write(name, content)
    }

    override fun head(name: String): CloudObject? {
        heads++
        failHead?.let { throw it }
        val bytes = objects[name] ?: return null
        return CloudObject(name, bytes.size.toLong(), null, etags[name])
    }

    override fun fetch(name: String): CloudContent? {
        fetches++
        failFetch?.let { throw it }
        val bytes = objects[name] ?: return null
        val content = CloudContent(bytes.copyOf(), etags[name])
        if (races.isNotEmpty()) races.removeFirst().invoke()
        return content
    }

    override fun get(name: String): ByteArray = objects[name]?.copyOf() ?: throw IOException("missing")

    override fun list(prefix: String): List<CloudObject> =
        objects
            .filterKeys { it.startsWith(prefix) }
            .map { (name, bytes) -> CloudObject(name, bytes.size.toLong(), null, etags[name]) }

    override fun delete(name: String) {
        objects.remove(name)
        etags.remove(name)
    }

    /** A foreign device writes [content] directly, without any precondition. */
    fun foreignWrite(
        name: String,
        content: ByteArray,
    ) {
        write(name, content)
    }

    fun contentOf(name: String): ByteArray? = objects[name]?.copyOf()

    private fun write(
        name: String,
        content: ByteArray,
    ): CloudObject {
        objects[name] = content.copyOf()
        val etag = "etag-${++writes}"
        etags[name] = etag
        return CloudObject(name, content.size.toLong(), null, if (hideEtags) null else etag)
    }
}

private fun encode(tokens: Set<String>): ByteArray = tokens.sorted().joinToString(",").toByteArray()

private fun decode(bytes: ByteArray): Set<String> = String(bytes).split(",").filter { it.isNotEmpty() }.toSet()
