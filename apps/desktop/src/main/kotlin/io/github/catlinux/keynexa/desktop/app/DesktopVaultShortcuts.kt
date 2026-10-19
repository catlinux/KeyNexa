@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Vault

/** What a keyboard shortcut of the open vault asks for. */
internal enum class VaultShortcut { Search, CopyUserName, CopyPassword, OpenUrl, Lock, NewEntry, EditEntry, Sync }

/** The shortcut labels, as the tooltips and the status bar show them. */
internal const val CTRL_F = "Ctrl+F"
internal const val CTRL_B = "Ctrl+B"
internal const val CTRL_C = "Ctrl+C"
internal const val CTRL_U = "Ctrl+U"
internal const val CTRL_L = "Ctrl+L"
internal const val CTRL_N = "Ctrl+N"
internal const val CTRL_E = "Ctrl+E"
internal const val CTRL_R = "Ctrl+R"

/**
 * The shortcut [key] stands for while Ctrl is held, or null when it is not one of them. Every one of them is also
 * shown in the tooltip of the button it matches and in the status bar.
 */
internal fun shortcutFor(
    ctrl: Boolean,
    key: Key,
): VaultShortcut? {
    if (!ctrl) return null
    return when (key) {
        Key.F -> VaultShortcut.Search
        Key.B -> VaultShortcut.CopyUserName
        Key.C -> VaultShortcut.CopyPassword
        Key.U -> VaultShortcut.OpenUrl
        Key.L -> VaultShortcut.Lock
        Key.N -> VaultShortcut.NewEntry
        Key.E -> VaultShortcut.EditEntry
        Key.R -> VaultShortcut.Sync
        else -> null
    }
}

/** The shortcut [event] asks for, or null; only a key press counts. */
internal fun shortcutOf(event: KeyEvent): VaultShortcut? {
    if (event.type != KeyEventType.KeyDown) return null
    return shortcutFor(event.isCtrlPressed, event.key)
}

/**
 * Runs [shortcut] on the entry the browser has selected, showing [userNameLabel] or [passwordLabel] as the
 * confirmation of a copy. The window only calls this for the events the focused component has not taken, so Ctrl+C
 * inside the search box still copies the text the user selected there.
 */
@Suppress("LongParameterList")
internal fun runShortcut(
    shortcut: VaultShortcut,
    view: DesktopVaultState,
    vault: Vault,
    userNameLabel: String,
    passwordLabel: String,
    onLock: () -> Unit,
    onSyncNow: () -> Unit,
) {
    when (shortcut) {
        VaultShortcut.Search -> view.focusSearch()
        VaultShortcut.Lock -> onLock()
        VaultShortcut.Sync -> onSyncNow()
        VaultShortcut.NewEntry -> view.startNewEntry(vault)
        VaultShortcut.EditEntry -> view.startEdit()
        VaultShortcut.CopyUserName ->
            vault.entry(view.selectedEntry)?.let { view.copy(it.userName, userNameLabel, sensitive = false) }
        VaultShortcut.CopyPassword ->
            if (!view.searchFocused) {
                vault.entry(view.selectedEntry)?.let { view.copy(it.passwordValue(), passwordLabel, sensitive = true) }
            }
        VaultShortcut.OpenUrl -> vault.entry(view.selectedEntry)?.let { view.openUrl(it.url) }
    }
}

/** The password of this entry, which is kept out of the search and never shown unless asked for. */
internal fun Entry.passwordValue(): String = fields[Entry.PASSWORD]?.value.orEmpty()
