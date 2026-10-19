@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.vault

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Name the recycle bin group gets when the editor has to create it. */
internal const val RECYCLE_BIN_GROUP_NAME = "Recycle Bin"

/** Standard KeePass icon of the recycle bin. */
internal const val RECYCLE_BIN_ICON_ID = 43

private const val NO_GROUP = "There is no group with that id in this vault."
private const val NO_ENTRY = "There is no entry with that id in this vault."
private const val NO_OBJECT = "There is no entry or group with that id in this vault."
private const val ROOT_STAYS = "The root group cannot be moved or deleted."
private const val MOVE_INTO_ITSELF = "A group cannot be moved inside itself or its descendants."

/** Maximum size, in bytes, of a single attachment. */
const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

/** Times of a brand-new object: every one of them is the moment it was created. */
internal fun creationTimes(at: Instant): Times =
    Times(created = at, lastAccessed = at, lastModified = at, locationChanged = at)

/**
 * Edits a [Vault] the way the interface does, with KeePass semantics.
 *
 * Every operation returns a new vault and never changes the one it was given, and the operations that work on an
 * object take the id of an entry or of a group, so a caller does not have to know which it is. The clock and the
 * source of new ids are injected, so callers and tests stay in control of time and identity. Misuse fails with
 * [IllegalArgumentException]: an unknown id, the root group, or a group moved inside its own subtree. Messages never
 * carry field values or any other secret.
 */
@Suppress("TooManyFunctions")
class VaultEditor(
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> UUID = UUID::randomUUID,
) {
    // KDBX stores whole seconds; times kept finer in memory would differ from the same vault read back from its file,
    // and a merge would then take one version for two.
    private fun wholeSecondsNow(): Instant = clock.instant().truncatedTo(ChronoUnit.SECONDS)

    /** Adds an empty group named [name] inside the group [parentId]. */
    fun addGroup(
        vault: Vault,
        parentId: UUID,
        name: String,
    ): Vault {
        val parent = requireGroup(vault, parentId)
        val group = Group(id = newId(), name = name, times = creationTimes(wholeSecondsNow()))
        return vault.copy(root = vault.root.mapGroup(parent.id) { it.copy(groups = it.groups + group) })
    }

    /**
     * Renames the group [groupId] to [name]. Its modification time moves to now, so a merge with another copy of the
     * vault keeps the newer name.
     */
    fun renameGroup(
        vault: Vault,
        groupId: UUID,
        name: String,
    ): Vault {
        val group = requireGroup(vault, groupId)
        if (group.name == name) return vault
        val now = wholeSecondsNow()
        return vault.copy(
            root =
                vault.root.mapGroup(group.id) {
                    it.copy(name = name, times = it.times.copy(lastModified = now))
                },
        )
    }

    /** Adds an entry whose only field is its [title], inside the group [groupId]. */
    fun addEntry(
        vault: Vault,
        groupId: UUID,
        title: String,
    ): Vault {
        val group = requireGroup(vault, groupId)
        val fields = mapOf(Entry.TITLE to FieldValue(title))
        val entry = Entry(id = newId(), times = creationTimes(wholeSecondsNow()), fields = fields)
        return vault.copy(root = vault.root.mapGroup(group.id) { it.copy(entries = it.entries + entry) })
    }

    /**
     * Applies [change] to the contents of an entry (its fields, attachments, tags and other properties).
     *
     * When something really changes, the version that was current is appended to the entry's history, the history is
     * trimmed to the vault's limit, and the modification time becomes now. When [change] leaves the contents as they
     * were, the vault is returned untouched.
     */
    fun editEntry(
        vault: Vault,
        entryId: UUID,
        change: (Entry) -> Entry,
    ): Vault {
        val before = requireNotNull(vault.root.findEntry(entryId)) { NO_ENTRY }
        val changed =
            change(before).copy(
                id = before.id,
                times = before.times,
                history = before.history,
                previousParentGroup = before.previousParentGroup,
            )
        if (changed == before) return vault
        val versions = before.history + before.copy(history = emptyList())
        val limit = vault.meta.historyMaxItems
        val history = if (limit >= 0 && versions.size > limit) versions.takeLast(limit) else versions
        val edited = changed.copy(times = before.times.copy(lastModified = wholeSecondsNow()), history = history)
        val holder = requireNotNull(vault.root.pathTo(entryId).lastOrNull()).id
        val root =
            vault.root.mapGroup(holder) { parent ->
                parent.copy(entries = parent.entries.map { if (it.id == entryId) edited else it })
            }
        return vault.copy(root = root)
    }

    /** Adds (or replaces) an attachment on the entry [entryId]; the binary is keyed by the SHA-256 of its content. */
    fun addAttachment(
        vault: Vault,
        entryId: UUID,
        name: String,
        content: ByteArray,
    ): Vault {
        require(name.isNotBlank()) { "Attachment name must not be blank." }
        require(content.size <= MAX_ATTACHMENT_BYTES) {
            "Attachment is too large (maximum $MAX_ATTACHMENT_BYTES bytes)."
        }
        val id = binaryIdOf(content)
        val edited =
            editEntry(vault, entryId) { entry ->
                val withoutSameName = entry.attachments.filterNot { it.name == name }
                entry.copy(attachments = withoutSameName + Attachment(name, id))
            }
        if (edited.binaries.containsKey(id)) return edited
        return edited.copy(binaries = edited.binaries + (id to Binary(content, protectedInMemory = false)))
    }

    /** Removes the attachment named [name] from the entry and garbage-collects binaries nobody references any more. */
    fun removeAttachment(
        vault: Vault,
        entryId: UUID,
        name: String,
    ): Vault {
        val edited =
            editEntry(vault, entryId) { entry ->
                entry.copy(attachments = entry.attachments.filterNot { it.name == name })
            }
        return dropUnreferencedBinaries(edited)
    }

    /**
     * Stores the contents of attachments that an edit has just referenced by [binaryIdOf], and drops the binaries
     * nobody references any more, so a removed attachment does not linger in the saved file.
     */
    fun storeBinaries(
        vault: Vault,
        added: Collection<ByteArray>,
    ): Vault {
        added.forEach { require(it.size <= MAX_ATTACHMENT_BYTES) { "Attachment is too large." } }
        val stored = added.associate { binaryIdOf(it) to Binary(it, protectedInMemory = false) }
        return dropUnreferencedBinaries(vault.copy(binaries = vault.binaries + stored))
    }

    private fun dropUnreferencedBinaries(vault: Vault): Vault {
        val referenced = referencedBinaryIds(vault.root)
        val filtered = vault.binaries.filterKeys { it in referenced }
        return if (filtered == vault.binaries) vault else vault.copy(binaries = filtered)
    }

    /**
     * Clears the previous versions of the entry [entryId] without creating a new one or touching its modification time.
     */
    fun clearHistory(
        vault: Vault,
        entryId: UUID,
    ): Vault {
        val entry = requireNotNull(vault.root.findEntry(entryId)) { NO_ENTRY }
        if (entry.history.isEmpty()) return vault
        val cleared = entry.copy(history = emptyList())
        val holder = requireNotNull(vault.root.pathTo(entryId).lastOrNull()).id
        val root =
            vault.root.mapGroup(holder) { parent ->
                parent.copy(entries = parent.entries.map { if (it.id == entryId) cleared else it })
            }
        return dropUnreferencedBinaries(vault.copy(root = root))
    }

    /** Moves an entry or a group into the group [targetId], recording when it was put there. */
    fun move(
        vault: Vault,
        objectId: UUID,
        targetId: UUID,
    ): Vault {
        requireObject(vault, objectId)
        val target = requireGroup(vault, targetId)
        val group = vault.root.findGroup(objectId)
        if (group != null) require(target.id !in group.allGroupIds()) { MOVE_INTO_ITSELF }
        val remembered = vault.root.findEntry(objectId)?.previousParentGroup ?: group?.previousParentGroup
        return place(vault, objectId, target.id, wholeSecondsNow(), remembered)
    }

    /**
     * Deletes an entry or a group with everything inside it. While the recycle bin is enabled it is moved there, so
     * it can be restored; otherwise it is erased from the tree, recording in [Vault.deletedObjects] the id of the
     * object and, for a group, those of everything it held. An object already inside the recycle bin is erased.
     */
    fun delete(
        vault: Vault,
        objectId: UUID,
    ): Vault {
        requireObject(vault, objectId)
        val bin = vault.meta.recycleBinGroup?.let { vault.root.findGroup(it) }
        val path = vault.root.pathTo(objectId)
        val insideBin = bin != null && (bin.id == objectId || path.any { it.id == bin.id })
        val at = wholeSecondsNow()
        if (vault.meta.recycleBinEnabled && !insideBin) {
            val destination = withRecycleBin(vault, newId, at)
            return place(destination.vault, objectId, destination.id, at, path.lastOrNull()?.id)
        }
        val erased = vault.root.findGroup(objectId)?.allIds() ?: setOf(objectId)
        return vault.copy(
            root = vault.root.removeObject(objectId),
            deletedObjects = vault.deletedObjects + erased.map { DeletedObject(it, at) },
        )
    }

    /**
     * Restores an entry or a group from the recycle bin: it goes back to the group it was taken from, or to the root
     * when that group is gone, and forgets where it came from.
     */
    fun restore(
        vault: Vault,
        objectId: UUID,
    ): Vault {
        requireObject(vault, objectId)
        val group = vault.root.findGroup(objectId)
        val insideTheObject = group?.allGroupIds().orEmpty()
        val remembered = vault.root.findEntry(objectId)?.previousParentGroup ?: group?.previousParentGroup
        val target = remembered?.takeIf { it !in insideTheObject && vault.root.findGroup(it) != null } ?: vault.root.id
        return place(vault, objectId, target, wholeSecondsNow(), previous = null)
    }

    /** Erases everything inside the recycle bin for good, keeping the bin group itself. */
    fun emptyRecycleBin(vault: Vault): Vault {
        val bin = vault.meta.recycleBinGroup?.let { vault.root.findGroup(it) } ?: return vault
        val objects = bin.allIds()
        if (objects.size == 1) return vault
        val at = wholeSecondsNow()
        val emptied = bin.copy(groups = emptyList(), entries = emptyList())
        val records = objects.filter { it != bin.id }.map { DeletedObject(it, at) }
        return vault.copy(
            root = vault.root.mapGroup(bin.id) { emptied },
            deletedObjects = vault.deletedObjects + records,
        )
    }

    // Takes the object out of the tree and puts it inside the group [target], with a new placement time.
    private fun place(
        vault: Vault,
        objectId: UUID,
        target: UUID,
        at: Instant,
        previous: UUID?,
    ): Vault {
        val without = vault.root.removeObject(objectId)
        val entry = vault.root.findEntry(objectId)
        if (entry != null) {
            val placed = entry.copy(times = entry.times.copy(locationChanged = at), previousParentGroup = previous)
            return vault.copy(root = without.mapGroup(target) { it.copy(entries = it.entries + placed) })
        }
        val group = requireNotNull(vault.root.findGroup(objectId))
        val placed = group.copy(times = group.times.copy(locationChanged = at), previousParentGroup = previous)
        return vault.copy(root = without.mapGroup(target) { it.copy(groups = it.groups + placed) })
    }

    private fun requireGroup(
        vault: Vault,
        groupId: UUID,
    ): Group = requireNotNull(vault.root.findGroup(groupId)) { NO_GROUP }

    private fun requireObject(
        vault: Vault,
        objectId: UUID,
    ) {
        require(objectId != vault.root.id) { ROOT_STAYS }
        require(vault.root.findGroup(objectId) != null || vault.root.findEntry(objectId) != null) { NO_OBJECT }
    }
}

/** The vault with a recycle bin group in place, and the id of that group. */
private class Bin(
    val vault: Vault,
    val id: UUID,
)

private fun withRecycleBin(
    vault: Vault,
    newId: () -> UUID,
    at: Instant,
): Bin {
    val existing = vault.meta.recycleBinGroup?.let { vault.root.findGroup(it) }
    if (existing != null) return Bin(vault, existing.id)
    val bin =
        Group(
            id = newId(),
            name = RECYCLE_BIN_GROUP_NAME,
            icon = Icon(RECYCLE_BIN_ICON_ID),
            times = creationTimes(at),
        )
    val meta = vault.meta.copy(recycleBinEnabled = true, recycleBinGroup = bin.id, recycleBinChanged = at)
    return Bin(vault.copy(meta = meta, root = vault.root.copy(groups = vault.root.groups + bin)), bin.id)
}

/** The group with [id], or null when this subtree does not hold it. */
internal fun Group.findGroup(id: UUID): Group? {
    if (this.id == id) return this
    return groups.firstNotNullOfOrNull { it.findGroup(id) }
}

/** The entry with [id], or null when this subtree does not hold it. */
internal fun Group.findEntry(id: UUID): Entry? {
    entries.firstOrNull { it.id == id }?.let { return it }
    return groups.firstNotNullOfOrNull { it.findEntry(id) }
}

/** The groups from the root down to the one that holds [id] (an entry or a group); empty when it is not here. */
internal fun Group.pathTo(id: UUID): List<Group> {
    val below = holderPath(id) ?: return emptyList()
    return listOf(this) + below
}

// The groups from the child of this group down to the one that holds [id]; empty when this group holds the object.
private fun Group.holderPath(id: UUID): List<Group>? {
    if (entries.any { it.id == id } || groups.any { it.id == id }) return emptyList()
    for (child in groups) child.holderPath(id)?.let { return listOf(child) + it }
    return null
}

/** Every group id in this subtree, the group itself included. */
internal fun Group.allGroupIds(): Set<UUID> =
    buildSet {
        add(id)
        groups.forEach { addAll(it.allGroupIds()) }
    }

/** Every group and entry id in this subtree, the group itself included. */
internal fun Group.allIds(): Set<UUID> =
    buildSet {
        add(id)
        entries.forEach { add(it.id) }
        groups.forEach { addAll(it.allIds()) }
    }

/** This subtree with the group [id] replaced by [transform]'s result. */
internal fun Group.mapGroup(
    id: UUID,
    transform: (Group) -> Group,
): Group = if (this.id == id) transform(this) else copy(groups = groups.map { it.mapGroup(id, transform) })

/** This subtree without the entry or the group [id], and without everything the group held. */
internal fun Group.removeObject(id: UUID): Group =
    copy(
        groups = groups.filterNot { it.id == id }.map { it.removeObject(id) },
        entries = entries.filterNot { it.id == id },
    )

/** Every [BinaryId] referenced by an entry or by any of its history versions anywhere in [root]. */
private fun referencedBinaryIds(root: Group): Set<BinaryId> {
    val result = mutableSetOf<BinaryId>()

    fun walk(group: Group) {
        group.entries.forEach { entry ->
            entry.attachments.forEach { result += it.id }
            entry.history.forEach { version -> version.attachments.forEach { result += it.id } }
        }
        group.groups.forEach(::walk)
    }
    walk(root)
    return result
}

/** The id of an attachment's binary: the lowercase hexadecimal SHA-256 of its content. */
fun binaryIdOf(content: ByteArray): BinaryId {
    val digest = MessageDigest.getInstance("SHA-256").digest(content)
    return BinaryId(digest.joinToString("") { "%02x".format(it) })
}
