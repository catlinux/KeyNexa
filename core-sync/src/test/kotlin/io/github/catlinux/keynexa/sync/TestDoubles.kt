package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Cipher
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.FileMetadata
import io.github.catlinux.keynexa.vault.FormatVersion
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.KdbxEngine
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.SiblingFile
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultFormat
import io.github.catlinux.keynexa.vault.VaultLocation
import io.github.catlinux.keynexa.vault.VaultMeta
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.IdentityHashMap
import java.util.Random
import java.util.UUID

/** A minimal vault of the domain, enough for the save pipeline. */
internal fun testVault(name: String = "vault"): Vault =
    Vault(
        format = VaultFormat(FormatVersion(VERSION_MAJOR, VERSION_MINOR), Cipher.Aes256, Kdf.AesKdf(AES_ROUNDS)),
        meta = VaultMeta(name = name),
        root =
            Group(
                id = UUID.randomUUID(),
                name = "Root",
                entries = listOf(Entry(id = UUID.randomUUID(), fields = mapOf(Entry.TITLE to FieldValue(name)))),
            ),
    )

private const val VERSION_MAJOR = 4
private const val VERSION_MINOR = 1
private const val AES_ROUNDS = 1_000L

/**
 * A KDBX engine with no cryptography but with the property the pipeline relies on: every encode produces different
 * bytes even for an equal vault, because it writes a fresh random seed. Decode returns the very same [Vault]
 * instance that was encoded, through an in-memory id registry.
 *
 * Layout: 12-byte KDBX signature and version, 32-byte seed, credentials id, vault id, a payload with the vault
 * description, and a CRC32 of everything before it at the end.
 */
internal class FakeKdbxEngine(
    seed: Long = DEFAULT_SEED,
) : KdbxEngine {
    /** When true, [inspect] reports KDBX 3.1 and damaged data looks like a wrong key, as it does in 3.x. */
    var kdbx3: Boolean = false

    /** When true, [encode] fails the way a real engine would when it cannot write the file. */
    var failEncode: Boolean = false

    var encodes: Int = 0
        private set

    var decodes: Int = 0
        private set

    private val random = Random(seed)
    private val idsByVault = IdentityHashMap<Vault, Int>()
    private val vaultsById = HashMap<Int, Vault>()
    private var nextId = 0

    fun resetCounters() {
        encodes = 0
        decodes = 0
    }

    override fun encode(
        vault: Vault,
        credentials: Credentials,
    ): ByteArray {
        encodes++
        if (failEncode) throw VaultException.UnsupportedFormat("fake engine cannot encode")

        val body = ByteArrayOutputStream()
        body.write(signature())
        body.write(randomBytes(SEED_SIZE))
        body.write(longBytes(credentialsId(credentials)))
        body.write(intBytes(idOf(vault)))
        val description = vault.toString().toByteArray()
        // Padded so that a saved file has a realistic KDBX size: fraction cuts of it stay meaningful.
        val payload = description + ByteArray(maxOf(0, MIN_PAYLOAD_SIZE - description.size))
        body.write(intBytes(payload.size))
        body.write(payload)

        val withoutCrc = body.toByteArray()
        return withoutCrc + intBytes(crcOf(withoutCrc, withoutCrc.size))
    }

    override fun decode(
        data: ByteArray,
        credentials: Credentials,
    ): Vault {
        decodes++
        verify(data)
        if (credentialsIdIn(data) != credentialsId(credentials)) {
            throw VaultException.WrongCredentials("fake engine: wrong credentials")
        }
        val id = readInt(data, VAULT_ID_OFFSET)
        return vaultsById[id] ?: throw VaultException.Corrupted("fake engine: unknown vault")
    }

    override fun inspect(data: ByteArray): VaultFormat {
        // Only the unencrypted header is read: a file whose body is damaged but whose header is intact still
        // reports its version, which is what lets a caller tell a damaged 3.x file from a wrong key.
        verifyHeader(data)
        return format()
    }

    private fun verifyHeader(data: ByteArray) {
        if (data.size < SIGNATURE_SIZE || !data.copyOf(SIGNATURE_SIZE).contentEquals(signature())) {
            throw VaultException.NotAVault("fake engine: not a vault")
        }
        if (data.size < MIN_SIZE) throw VaultException.Corrupted("fake engine: truncated")
    }

    private fun verify(data: ByteArray) {
        verifyHeader(data)
        if (crcOf(data, data.size - Int.SIZE_BYTES) != readInt(data, data.size - Int.SIZE_BYTES)) throw damaged()
    }

    private fun damaged(): VaultException =
        if (kdbx3) {
            VaultException.WrongCredentials("fake engine: 3.x damage looks like a wrong key")
        } else {
            VaultException.Corrupted("fake engine: damaged")
        }

    private fun format(): VaultFormat =
        VaultFormat(
            version = FormatVersion(if (kdbx3) KDBX_3 else VERSION_MAJOR, VERSION_MINOR),
            cipher = Cipher.Aes256,
            kdf = Kdf.AesKdf(AES_ROUNDS),
        )

    private fun idOf(vault: Vault): Int {
        idsByVault[vault]?.let { return it }
        val id = ++nextId
        idsByVault[vault] = id
        vaultsById[id] = vault
        return id
    }

    override fun hashedForm(credentials: Credentials): Credentials {
        if (credentials.isPreHashed) {
            return Credentials.ofHashes(
                credentials.passwordHashCopy(),
                credentials.keyHashCopy(),
            )
        }
        val password = credentials.passwordCopy()
        val keyFile = credentials.keyFileCopy()
        return Credentials.ofHashes(
            password?.let { sha256(utf8(it)) },
            keyFile?.let(::sha256),
        )
    }

    private fun credentialsId(credentials: Credentials): Long {
        val hashed = hashedForm(credentials)
        val passwordHash = hashed.passwordHashCopy()
        val keyHash = hashed.keyHashCopy()
        hashed.wipe()
        val digest = MessageDigest.getInstance("SHA-256")
        passwordHash?.let(digest::update)
        keyHash?.let(digest::update)
        return ByteBuffer.wrap(digest.digest()).long
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun utf8(chars: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
        return ByteArray(buffer.remaining()).also(buffer::get)
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun signature(): ByteArray =
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
            VERSION_MAJOR.toByte(),
            0x00,
            VERSION_MINOR.toByte(),
        )

    private fun credentialsIdIn(data: ByteArray): Long = ByteBuffer.wrap(data, CREDENTIALS_OFFSET, Long.SIZE_BYTES).long

    private fun crcOf(
        data: ByteArray,
        length: Int,
    ): Int {
        val crc = java.util.zip.CRC32()
        crc.update(data, 0, length)
        return crc.value.toInt()
    }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()

    private fun longBytes(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()

    private fun readInt(
        data: ByteArray,
        offset: Int,
    ): Int = ByteBuffer.wrap(data, offset, Int.SIZE_BYTES).int

    private companion object {
        const val DEFAULT_SEED = 20261013L
        const val SEED_SIZE = 32
        const val SIGNATURE_SIZE = 12
        const val MIN_PAYLOAD_SIZE = 256
        const val CREDENTIALS_OFFSET = SIGNATURE_SIZE + SEED_SIZE
        const val VAULT_ID_OFFSET = CREDENTIALS_OFFSET + Long.SIZE_BYTES
        const val MIN_SIZE = VAULT_ID_OFFSET + Int.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES
        const val KDBX_3 = 3
    }
}

/** In-memory storage with programmable failures and a history of the operations it ran. */
internal class FakeStorage(
    initial: Map<VaultLocation, ByteArray> = emptyMap(),
) : StoragePort {
    val files: MutableMap<VaultLocation, ByteArray> = LinkedHashMap(initial)
    val operations: MutableList<String> = mutableListOf()

    /** The read/write call number that fails with [IOException], counted from 1. */
    var failReadAt: Int? = null
    var failWriteAt: Int? = null

    /** A read or a delete of this file fails with [IOException], whatever the call number. */
    var failReadFor: VaultLocation? = null
    var failDeleteFor: VaultLocation? = null

    /** A write that succeeds but stores nothing: a provider that lies. */
    var silentWrite: Boolean = false

    /** Overwrites from byte 0 but keeps the previous tail when it was longer. Does not throw. */
    var writeWithoutTruncate: Boolean = false

    /** Each write stores only this many leading bytes and then fails: the process died mid-write. */
    var cutWriteTo: Int? = null

    /** Each write stores only this fraction of the leading bytes and then fails. */
    var cutWriteFraction: Double? = null

    private val reads: MutableMap<Int, (VaultLocation) -> Unit> = HashMap()
    private val writesAfter: MutableMap<Int, (VaultLocation) -> Unit> = HashMap()
    private var readCount = 0
    private var writeCount = 0

    /** Runs [action] just before the [number]-th read, counted from 1. */
    fun beforeRead(
        number: Int,
        action: (VaultLocation) -> Unit,
    ) {
        reads[number] = action
    }

    /** Runs [action] just after the [number]-th write, counted from 1. */
    fun afterWrite(
        number: Int,
        action: (VaultLocation) -> Unit,
    ) {
        writesAfter[number] = action
    }

    /** Restarts the counters and the history, so a test can hook the n-th call of the save itself. */
    fun forgetOperations() {
        operations.clear()
        readCount = 0
        writeCount = 0
    }

    override fun read(location: VaultLocation): ByteArray {
        readCount++
        operations += "read#$readCount"
        reads[readCount]?.invoke(location)
        if (readCount == failReadAt || location == failReadFor) throw IOException("injected read failure")
        return files[location]?.copyOf() ?: throw IOException("no file at $location")
    }

    override fun metadata(location: VaultLocation): FileMetadata = FileMetadata(files[location]?.size?.toLong(), null)

    override fun listSiblings(location: VaultLocation): List<SiblingFile> =
        files.keys
            .filter { it != location }
            .map { SiblingFile(it, name(it), FileMetadata(files[it]?.size?.toLong(), null)) }

    override fun name(location: VaultLocation): String = location.value.substringAfterLast('/')

    override fun delete(location: VaultLocation) {
        if (location == failDeleteFor) throw IOException("injected delete failure")
        files.remove(location) ?: throw IOException("no file at $location")
    }

    override fun write(
        location: VaultLocation,
        data: ByteArray,
    ) {
        writeCount++
        operations += "write#$writeCount"
        if (writeCount == failWriteAt) throw IOException("injected write failure")
        if (silentWrite) {
            writesAfter[writeCount]?.invoke(location)
            return
        }

        val cut = cutWriteTo ?: cutWriteFraction?.let { (data.size * it).toInt() }
        if (cut != null) {
            files[location] = data.copyOf(minOf(cut, data.size))
            throw IOException("injected cut write")
        }
        files[location] = if (writeWithoutTruncate) mergeWithOldTail(location, data) else data.copyOf()
        writesAfter[writeCount]?.invoke(location)
    }

    private fun mergeWithOldTail(
        location: VaultLocation,
        bytes: ByteArray,
    ): ByteArray {
        val old = files[location] ?: return bytes.copyOf()
        if (bytes.size >= old.size) return bytes.copyOf()
        return bytes + old.copyOfRange(bytes.size, old.size)
    }
}

internal class InMemoryBackupStore(
    private val failOnPut: Boolean = false,
    private val failOnRead: Boolean = false,
) : BackupStore {
    private val refs = LinkedHashMap<String, BackupRef>()
    private val contents = HashMap<String, ByteArray>()
    private var next = 0

    override fun put(
        location: VaultLocation,
        kind: BackupKind,
        bytes: ByteArray,
        createdAt: Instant,
    ): BackupRef {
        if (failOnPut) throw IOException("injected backup failure")
        val ref = BackupRef("backup-${next++}", location, kind, createdAt, Fingerprint.of(bytes))
        refs[ref.id] = ref
        contents[ref.id] = bytes.copyOf()
        return ref
    }

    override fun list(location: VaultLocation): List<BackupRef> =
        refs.values.filter { it.location == location }.sortedByDescending { it.createdAt }

    override fun read(ref: BackupRef): ByteArray {
        if (failOnRead) throw IOException("injected backup read failure")
        return contents[ref.id]?.copyOf() ?: throw IOException("no backup ${ref.id}")
    }

    override fun delete(ref: BackupRef) {
        refs.remove(ref.id)
        contents.remove(ref.id)
    }

    fun stored(kind: BackupKind): List<BackupRef> = refs.values.filter { it.kind == kind }

    fun bytesOf(ref: BackupRef): ByteArray? = contents[ref.id]

    /** Replaces a copy's bytes so that they no longer match its fingerprint. */
    fun corrupt(ref: BackupRef) {
        contents[ref.id] = byteArrayOf(1, 2, 3)
    }
}

internal class InMemorySaveJournal(
    private val failOnBegin: Boolean = false,
    private val failOnPending: Boolean = false,
) : SaveJournal {
    private val entries = HashMap<VaultLocation, PendingSave>()

    override fun begin(pending: PendingSave) {
        if (failOnBegin) throw IOException("injected journal failure")
        entries[pending.location] = pending
    }

    override fun pending(location: VaultLocation): PendingSave? {
        if (failOnPending) throw IOException("injected journal read failure")
        return entries[location]
    }

    override fun clear(location: VaultLocation) {
        entries.remove(location)
    }

    fun isPending(location: VaultLocation): Boolean = entries.containsKey(location)

    /** Records a save as in progress, for tests that start from a crashed process. */
    fun record(pending: PendingSave) {
        entries[pending.location] = pending
    }
}

/** Merges with a fixed answer and counts the calls. */
internal class FakeVaultMerger(
    private val answer: (Vault, Vault) -> MergeResult,
) : VaultMerger {
    var calls: Int = 0
        private set

    override fun merge(
        ours: Vault,
        theirs: Vault,
    ): MergeResult {
        calls++
        return answer(ours, theirs)
    }
}

/** Records the waits without sleeping. */
internal class RecordingSleeper : Sleeper {
    val sleeps: MutableList<Long> = mutableListOf()

    override fun sleep(millis: Long) {
        sleeps += millis
    }
}

/** A clock the tests move forward by hand, so backup timestamps are ordered. */
internal class FixedClock(
    private var now: Instant,
) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/**
 * One in-memory world of the save channel: the storage, the private copies, the journal and the pipeline over them.
 * A [SaveRecovery] over the same three objects is what a restarted process sees.
 */
internal class SyncWorld(
    val storage: FakeStorage = FakeStorage(),
    val backups: InMemoryBackupStore = InMemoryBackupStore(),
    val journal: InMemorySaveJournal = InMemorySaveJournal(),
    merger: VaultMerger? = null,
    options: SaveOptions = SaveOptions(),
) {
    val engine = FakeKdbxEngine()
    val saver = SafeSaver(storage, engine, backups, journal, merger, options)
    val recovery = SaveRecovery(storage, engine, backups, journal)

    fun file(location: VaultLocation): ByteArray = storage.read(location)

    /** The vault the file holds, as this world's engine registered it. */
    fun decodeFile(
        location: VaultLocation,
        credentials: Credentials,
    ): Vault = engine.decode(storage.read(location), credentials)

    fun loaded(
        location: VaultLocation,
        vault: Vault,
    ): LoadedVault = LoadedVault(location, vault, Fingerprint.of(storage.read(location)))

    fun withFile(
        location: VaultLocation,
        bytes: ByteArray,
    ): SyncWorld {
        storage.files[location] = bytes
        return this
    }
}
