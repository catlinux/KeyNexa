package io.github.catlinux.keynexa.app.nav

import androidx.compose.runtime.mutableStateListOf
import io.github.catlinux.keynexa.app.vault.KnownVault
import java.util.UUID

/** The screens of the app. A screen carries only ids, never vault content or secrets. */
sealed interface Screen {
    data object Home : Screen

    data class Unlock(
        val vault: KnownVault,
    ) : Screen

    /** The entries and subgroups of a group; `null` is the root group. */
    data class Group(
        val groupId: UUID?,
    ) : Screen

    data class Search(
        val inGroup: UUID?,
    ) : Screen

    data class EntryDetail(
        val entryId: UUID,
    ) : Screen

    /** Edits an entry, or creates one in [groupId] when [entryId] is null. */
    data class EntryEdit(
        val entryId: UUID?,
        val groupId: UUID?,
    ) : Screen

    data object CreateVault : Screen

    data object RecycleBin : Screen
}

/** A back stack in memory. The bottom screen is always [Screen.Home]. */
class Navigator {
    private val stack = mutableStateListOf<Screen>(Screen.Home)

    val current: Screen get() = stack.last()

    fun push(screen: Screen) {
        stack.add(screen)
    }

    /** Goes back one screen; returns false when already on [Screen.Home]. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Drops everything above [Screen.Home], for example after locking. */
    fun reset() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
