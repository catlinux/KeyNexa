package io.github.catlinux.keynexa.vault

/**
 * Whether two vaults hold the same things, ignoring the order of the entries and of the groups inside a group.
 *
 * Merging the same two vaults from either side gives the same content in a different order, and synchronisation
 * must not take that for a difference: two devices would upload to each other forever.
 */
fun Vault.sameContentAs(other: Vault): Boolean =
    copy(root = root.inIdOrder()) == other.copy(root = other.root.inIdOrder())

private fun Group.inIdOrder(): Group =
    copy(groups = groups.map { it.inIdOrder() }.sortedBy { it.id }, entries = entries.sortedBy { it.id })
