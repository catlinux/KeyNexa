package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Attachment
import io.github.catlinux.keynexa.vault.Binary
import io.github.catlinux.keynexa.vault.BinaryId
import io.github.catlinux.keynexa.vault.Cipher
import io.github.catlinux.keynexa.vault.DeletedObject
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.FormatVersion
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Icon
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.Times
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultFormat
import io.github.catlinux.keynexa.vault.VaultMeta
import io.github.catlinux.keynexa.vault.XmlElement
import java.time.Instant
import java.util.UUID

/**
 * Fixtures for the merge tests: a tiny construction DSL, deterministic ids and helpers to edit, move and delete
 * objects the way a password manager does.
 *
 * An object's id comes from its path (`uuid("<parent path>/<name>")`, the root's from its own name), so two vaults
 * written with the same names are the same vault seen from two devices. A group and an entry inside one parent must
 * not share a name. Times are always explicit: [at] is a second offset from [T0] and the fixtures never leave a
 * modification time out.
 */

internal const val ROOT = "Root"
internal const val TITLE = Entry.TITLE
internal const val NOTES = Entry.NOTES
internal const val PASSWORD = Entry.PASSWORD
internal const val USER_NAME = Entry.USER_NAME
internal const val URL = Entry.URL

private const val FOLDER_ICON = 48
private const val AES_ROUNDS = 1_000L

/** The fixtures' clock starts here; every time is written as a second offset from it. */
internal val T0: Instant = Instant.parse("2026-01-01T00:00:00Z")

internal fun at(seconds: Int): Instant = T0.plusSeconds(seconds.toLong())

internal fun uuid(name: String): UUID = UUID.nameUUIDFromBytes(name.toByteArray())

internal fun testFormat(): VaultFormat = VaultFormat(FormatVersion(4, 1), Cipher.Aes256, Kdf.AesKdf(AES_ROUNDS))

/** An attachment id for the fixtures. The merge only compares these ids, never the contents. */
internal fun binaryId(name: String): BinaryId {
    val hex = uuid(name).toString().replace("-", "")
    return BinaryId(hex.repeat(2).take(64))
}

internal fun binary(name: String): Binary = Binary(name.toByteArray())

internal fun attachment(
    name: String,
    binaryName: String = name,
): Attachment = Attachment(name, binaryId(binaryName))

internal fun binaries(vararg names: String): Map<BinaryId, Binary> = names.associate { binaryId(it) to binary(it) }

/** Builds a vault whose root group is named [root]. */
internal fun vault(
    root: String = ROOT,
    lastModified: Instant = T0,
    block: GroupScope.() -> Unit = {},
): Vault {
    val scope = GroupScope(root, root, lastModified)
    scope.block()
    return Vault(testFormat(), VaultMeta(name = root), scope.build())
}

/** Nested builder for one group: groups and entries inside it, with the field values each one needs. */
internal class GroupScope(
    private val path: String,
    private val name: String,
    private val lastModified: Instant,
    private val locationChanged: Instant? = null,
    private val notes: String = "",
) {
    private val groups = mutableListOf<Group>()
    private val entries = mutableListOf<Entry>()

    fun group(
        name: String,
        lastModified: Instant,
        locationChanged: Instant? = null,
        block: GroupScope.() -> Unit = {},
    ) {
        val child = GroupScope("$path/$name", name, lastModified, locationChanged)
        child.block()
        groups += child.build()
    }

    fun entry(
        name: String,
        lastModified: Instant,
        block: EntryScope.() -> Unit = {},
    ) {
        val scope = EntryScope()
        scope.block()
        entries += entryOf("$path/$name", name, lastModified, scope)
    }

    internal fun build(): Group =
        Group(
            id = uuid(path),
            name = name,
            notes = notes,
            icon = Icon(FOLDER_ICON),
            times = Times(created = lastModified, lastModified = lastModified, locationChanged = locationChanged),
            groups = groups.toList(),
            entries = entries.toList(),
        )
}

/** The fields of one entry; whatever is left at its default is left out of the entry. */
internal class EntryScope {
    var title: String? = null
    var userName: String = ""
    var password: String = ""
    var url: String = ""
    var notes: String = ""
    var tags: List<String> = emptyList()
    var attachments: List<Attachment> = emptyList()
    var history: List<Entry> = emptyList()
    val extra: MutableMap<String, FieldValue> = LinkedHashMap()
}

internal fun entryOf(
    idName: String,
    name: String,
    lastModified: Instant,
    scope: EntryScope,
): Entry {
    val fields = LinkedHashMap<String, FieldValue>()
    fields[TITLE] = FieldValue(scope.title ?: name)
    if (scope.userName.isNotEmpty()) fields[USER_NAME] = FieldValue(scope.userName)
    if (scope.password.isNotEmpty()) fields[PASSWORD] = FieldValue(scope.password, protected = true)
    if (scope.url.isNotEmpty()) fields[URL] = FieldValue(scope.url)
    if (scope.notes.isNotEmpty()) fields[NOTES] = FieldValue(scope.notes)
    fields.putAll(scope.extra)
    return Entry(
        id = uuid(idName),
        times = Times(created = lastModified, lastModified = lastModified),
        fields = fields,
        tags = scope.tags,
        attachments = scope.attachments,
        history = scope.history,
    )
}

/** A brand-new entry, as one side would add it. */
internal fun newEntry(
    name: String,
    at: Instant,
    attachedBinary: String? = null,
): Entry =
    Entry(
        id = uuid(name),
        times = Times(created = at, lastModified = at),
        fields = mapOf(TITLE to FieldValue(name), NOTES to FieldValue(CONTENT)),
        attachments = attachedBinary?.let { listOf(attachment(it)) }.orEmpty(),
    )

/** A brand-new group, as one side would add it. */
internal fun newGroup(
    name: String,
    at: Instant,
): Group = Group(id = uuid(name), name = name, icon = Icon(FOLDER_ICON), times = Times(created = at, lastModified = at))

internal const val CONTENT = "content"

/** Merges two vaults that the fixtures make mergeable; a change of root would be a fixture mistake. */
internal fun mergeVaults(
    ours: Vault,
    theirs: Vault,
): Vault =
    when (val result = KeePassMerger().merge(ours, theirs)) {
        is MergeResult.Merged -> result.vault
        is MergeResult.Unmergeable -> throw AssertionError("unexpected Unmergeable: ${result.reason}")
    }

internal fun Vault.findGroup(id: UUID): Group? = root.findGroup(id)

internal fun Group.findGroup(id: UUID): Group? {
    if (this.id == id) return this
    return groups.firstNotNullOfOrNull { it.findGroup(id) }
}

internal fun Vault.findEntry(id: UUID): Entry? = root.findEntry(id)

internal fun Group.findEntry(id: UUID): Entry? {
    entries.firstOrNull { it.id == id }?.let { return it }
    return groups.firstNotNullOfOrNull { it.findEntry(id) }
}

/** The group that holds an entry, or null when no group does. */
internal fun Vault.parentOfEntry(entryId: UUID): UUID? = root.parentOfEntry(entryId)

internal fun Group.parentOfEntry(entryId: UUID): UUID? {
    if (entries.any { it.id == entryId }) return id
    return groups.firstNotNullOfOrNull { it.parentOfEntry(entryId) }
}

/** The group that holds a group, or null for the root. */
internal fun Vault.parentOfGroup(groupId: UUID): UUID? = root.parentOfGroup(groupId)

internal fun Group.parentOfGroup(groupId: UUID): UUID? {
    if (groups.any { it.id == groupId }) return id
    return groups.firstNotNullOfOrNull { it.parentOfGroup(groupId) }
}

/** Every group id in the tree, the root included. */
internal fun Group.groupIds(): Set<UUID> =
    buildSet {
        add(id)
        groups.forEach { addAll(it.groupIds()) }
    }

/** Every entry id in the tree. History versions share their entry's id, so they add nothing here. */
internal fun Group.entryIds(): Set<UUID> =
    buildSet {
        entries.forEach { add(it.id) }
        groups.forEach { addAll(it.entryIds()) }
    }

/** Every group and entry id in this group, the group itself included. */
internal fun Group.allIds(): Set<UUID> =
    buildSet {
        add(id)
        entries.forEach { add(it.id) }
        groups.forEach { addAll(it.allIds()) }
    }

internal fun Vault.replaceEntry(entry: Entry): Vault = copy(root = root.replaceEntry(entry))

internal fun Group.replaceEntry(entry: Entry): Group =
    copy(
        groups = groups.map { it.replaceEntry(entry) },
        entries = entries.map { if (it.id == entry.id) entry else it },
    )

/** Replaces the group with [id], and everything inside it, with [transform]'s result. */
internal fun Vault.mapGroup(
    id: UUID,
    transform: (Group) -> Group,
): Vault = copy(root = root.mapGroup(id, transform))

internal fun Group.mapGroup(
    id: UUID,
    transform: (Group) -> Group,
): Group = if (this.id == id) transform(this) else copy(groups = groups.map { it.mapGroup(id, transform) })

/** Edits a group's own properties (name, notes, icon); its place in the tree is untouched. */
internal fun Vault.editGroup(
    id: UUID,
    at: Instant,
    transform: (Group) -> Group = { it },
): Vault {
    val group = requireNotNull(findGroup(id)) { "no group $id" }
    return mapGroup(id) { transform(it).copy(times = it.times.copy(lastModified = at)) }
}

/** Edits an entry: [transform] changes its fields and its modification time becomes [at]. */
internal fun Vault.editEntry(
    id: UUID,
    at: Instant,
    transform: (Entry) -> Entry = { it },
): Vault {
    val entry = requireNotNull(findEntry(id)) { "no entry $id" }
    return replaceEntry(transform(entry).copy(times = entry.times.copy(lastModified = at)))
}

/** Pushes the entry's current version into its history and then edits it, as a manager does on every change. */
internal fun Vault.newVersion(
    id: UUID,
    at: Instant,
    transform: (Entry) -> Entry = { it },
): Vault {
    val entry = requireNotNull(findEntry(id)) { "no entry $id" }
    val versioned = entry.copy(history = entry.history + entry.copy(history = emptyList()))
    return replaceEntry(transform(versioned).copy(times = versioned.times.copy(lastModified = at)))
}

internal fun Vault.removeEntry(id: UUID): Vault = copy(root = root.removeEntry(id))

internal fun Group.removeEntry(id: UUID): Group =
    copy(
        groups = groups.map { it.removeEntry(id) },
        entries = entries.filterNot { it.id == id },
    )

internal fun Vault.addEntry(
    group: UUID,
    entry: Entry,
): Vault = copy(root = root.addEntry(group, entry))

internal fun Group.addEntry(
    groupId: UUID,
    entry: Entry,
): Group =
    if (id == groupId) copy(entries = entries + entry) else copy(groups = groups.map { it.addEntry(groupId, entry) })

/** Removes a group and everything inside it. */
internal fun Vault.removeGroup(id: UUID): Vault = copy(root = root.removeGroup(id))

internal fun Group.removeGroup(id: UUID): Group =
    copy(groups = groups.filterNot { it.id == id }.map { it.removeGroup(id) })

internal fun Vault.addGroup(
    parent: UUID,
    group: Group,
): Vault = copy(root = root.addGroup(parent, group))

internal fun Group.addGroup(
    parentId: UUID,
    group: Group,
): Group =
    if (id == parentId) copy(groups = groups + group) else copy(groups = groups.map { it.addGroup(parentId, group) })

/** Moves an entry to another group, recording when it was put there. */
internal fun Vault.moveEntry(
    id: UUID,
    to: UUID,
    at: Instant,
): Vault {
    val moved =
        requireNotNull(findEntry(id)) { "no entry $id" }
            .let { it.copy(times = it.times.copy(locationChanged = at)) }
    return removeEntry(id).addEntry(to, moved)
}

/** Moves a group, with everything inside, to another group, recording when it was put there. */
internal fun Vault.moveGroup(
    id: UUID,
    to: UUID,
    at: Instant,
): Vault {
    val moved =
        requireNotNull(findGroup(id)) { "no group $id" }
            .let { it.copy(times = it.times.copy(locationChanged = at)) }
    return removeGroup(id).addGroup(to, moved)
}

/** Removes an entry and records its deletion, as a manager does when the user deletes it. */
internal fun Vault.deleteEntry(
    id: UUID,
    at: Instant,
): Vault = removeEntry(id).let { it.copy(deletedObjects = it.deletedObjects + DeletedObject(id, at)) }

/** Removes a group with everything inside and records the deletion of each object, as a manager does. */
internal fun Vault.deleteGroup(
    id: UUID,
    at: Instant,
): Vault {
    val records = requireNotNull(findGroup(id)) { "no group $id" }.allIds().map { DeletedObject(it, at) }
    return removeGroup(id).let { it.copy(deletedObjects = it.deletedObjects + records) }
}

internal fun Vault.withMeta(meta: VaultMeta): Vault = copy(meta = meta)

internal fun Vault.withDeleted(records: List<DeletedObject>): Vault = copy(deletedObjects = records)

internal fun Vault.withBinaries(binaries: Map<BinaryId, Binary>): Vault = copy(binaries = binaries)

internal fun Vault.withUnknown(elements: List<XmlElement>): Vault = copy(unknownElements = elements)

internal fun Vault.withUnknownRoot(elements: List<XmlElement>): Vault = copy(unknownRootElements = elements)

/** Renames the root group or moves its modification time, for fixtures where the root itself is the object. */
internal fun Vault.withRoot(
    name: String = root.name,
    lastModified: Instant = root.times.lastModified ?: T0,
    notes: String = root.notes,
): Vault =
    copy(
        root =
            root.copy(
                name = name,
                notes = notes,
                times = root.times.copy(lastModified = lastModified, created = root.times.created ?: lastModified),
            ),
    )

/** Sets one field of an entry, keeping whether it is protected. */
internal fun Entry.withField(
    name: String,
    value: String,
): Entry = copy(fields = fields + (name to FieldValue(value, protected = fields[name]?.protected ?: false)))

internal fun Entry.withTag(tag: String): Entry = copy(tags = tags + tag)

internal fun Entry.withAttachment(name: String): Entry = copy(attachments = attachments + attachment(name))
