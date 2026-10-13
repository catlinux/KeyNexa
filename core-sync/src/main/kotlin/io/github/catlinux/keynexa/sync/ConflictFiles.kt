package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.SiblingFile

private val CONFLICT_MARKER = Regex("""\.sync-conflict-\d{8}-\d{6}(?:-[A-Za-z0-9]+)?""")

/**
 * The Syncthing conflict copies of the vault called [vaultName] among its [siblings]: files named
 * `<name>.sync-conflict-<date>-<time>-<device>.<ext>` where `<name>` and `<ext>` are those of the vault. They are
 * offered for merge and never deleted. The newest conflict comes first; names sort by date and time.
 */
fun conflictCopiesOf(
    vaultName: String,
    siblings: List<SiblingFile>,
): List<SiblingFile> {
    val dot = vaultName.lastIndexOf('.')
    val base = if (dot > 0) vaultName.substring(0, dot) else vaultName
    val extension = if (dot > 0) vaultName.substring(dot) else ""
    return siblings
        .filter { sibling ->
            val name = sibling.name
            name != vaultName &&
                name.startsWith(base) &&
                name.endsWith(extension) &&
                CONFLICT_MARKER.matches(name.substring(base.length, name.length - extension.length))
        }.sortedByDescending { it.name }
}
