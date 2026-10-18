package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.VaultMeta
import java.time.Instant
import java.util.UUID

internal fun mergeEntries(
    sides: Sides,
    alive: Set<UUID>,
    limits: HistoryLimits,
): Map<UUID, Entry> =
    alive.associateWith { id ->
        val mine = sides.ours.entries[id]
        val other = sides.theirs.entries[id]
        when {
            mine == null -> requireNotNull(other)
            other == null -> mine
            else -> mergeEntry(mine, other, limits)
        }
    }

/** The newer version wins; every other version either side knew becomes history, oldest first. */
internal fun mergeEntry(
    ours: Entry,
    theirs: Entry,
    limits: HistoryLimits,
): Entry {
    val winner = if (modified(theirs.times) > modified(ours.times)) theirs else ours
    val loser = if (winner === ours) theirs else ours
    val history = ArrayList<Entry>()
    for (version in (ours.history + theirs.history + loser).map { it.copy(history = emptyList()) }) {
        if (!sameVersion(version, winner) && history.none { sameVersion(it, version) }) history += version
    }
    history.sortBy { modified(it.times) }
    return winner.copy(history = trimHistory(history, limits))
}

/** Drops the oldest versions until the history fits the vault's limits. */
internal fun trimHistory(
    oldestFirst: List<Entry>,
    limits: HistoryLimits,
): List<Entry> {
    var history = oldestFirst
    if (limits.maxItems >= 0 && history.size > limits.maxItems) history = history.takeLast(limits.maxItems)
    if (limits.maxSizeBytes >= 0) {
        while (history.isNotEmpty() && history.sumOf(::approximateSize) > limits.maxSizeBytes) history = history.drop(1)
    }
    return history
}

// Strings are counted as UTF-16, as KeePass does; attachments are left out because their contents are shared and
// stored once.
private fun approximateSize(entry: Entry): Long =
    entry.fields.entries.sumOf { (name, value) -> (name.length + value.value.length) * UTF16_BYTES } +
        entry.tags.sumOf { it.length * UTF16_BYTES }

private const val UTF16_BYTES = 2L

/** The same version of an entry: same modification time and same content, ignoring history. */
private fun sameVersion(
    a: Entry,
    b: Entry,
): Boolean = a.times.lastModified == b.times.lastModified && a.copy(history = b.history) == b

/** Merges the vault's metadata field by field, each by its own change time. */
internal fun mergeMeta(
    ours: VaultMeta,
    theirs: VaultMeta,
): VaultMeta {
    fun theirsNewer(
        mine: Instant?,
        other: Instant?,
    ) = (other ?: Instant.MIN) > (mine ?: Instant.MIN)

    var meta = if (theirsNewer(ours.settingsChanged, theirs.settingsChanged)) settingsFrom(theirs, ours) else ours
    if (theirsNewer(ours.nameChanged, theirs.nameChanged)) {
        meta =
            meta.copy(name = theirs.name, nameChanged = theirs.nameChanged)
    }
    if (theirsNewer(ours.descriptionChanged, theirs.descriptionChanged)) {
        meta = meta.copy(description = theirs.description, descriptionChanged = theirs.descriptionChanged)
    }
    if (theirsNewer(ours.defaultUserChanged, theirs.defaultUserChanged)) {
        meta = meta.copy(defaultUser = theirs.defaultUser, defaultUserChanged = theirs.defaultUserChanged)
    }
    if (theirsNewer(ours.recycleBinChanged, theirs.recycleBinChanged)) {
        meta =
            meta.copy(
                recycleBinEnabled = theirs.recycleBinEnabled,
                recycleBinGroup = theirs.recycleBinGroup,
                recycleBinChanged = theirs.recycleBinChanged,
            )
    }
    if (theirsNewer(ours.entryTemplatesGroupChanged, theirs.entryTemplatesGroupChanged)) {
        meta =
            meta.copy(
                entryTemplatesGroup = theirs.entryTemplatesGroup,
                entryTemplatesGroupChanged = theirs.entryTemplatesGroupChanged,
            )
    }
    if (theirsNewer(ours.masterKeyChanged, theirs.masterKeyChanged)) {
        meta =
            meta.copy(masterKeyChanged = theirs.masterKeyChanged)
    }
    return meta.copy(
        customIcons =
            theirs.customIcons +
                ours.customIcons.filter { (id, icon) ->
                    !theirsNewer(icon.lastModified, theirs.customIcons[id]?.lastModified)
                },
        customData =
            theirs.customData +
                ours.customData.filter { (key, value) ->
                    !theirsNewer(value.lastModified, theirs.customData[key]?.lastModified)
                },
    )
}

// The general settings travel together, governed by settingsChanged; the fields with their own times are left.
private fun settingsFrom(
    newer: VaultMeta,
    base: VaultMeta,
): VaultMeta =
    base.copy(
        settingsChanged = newer.settingsChanged,
        color = newer.color,
        maintenanceHistoryDays = newer.maintenanceHistoryDays,
        masterKeyChangeRecommendedDays = newer.masterKeyChangeRecommendedDays,
        masterKeyChangeForcedDays = newer.masterKeyChangeForcedDays,
        historyMaxItems = newer.historyMaxItems,
        historyMaxSizeBytes = newer.historyMaxSizeBytes,
        protectedFields = newer.protectedFields,
    )
