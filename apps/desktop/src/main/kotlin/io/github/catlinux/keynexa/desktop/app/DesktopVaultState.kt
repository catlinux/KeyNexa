package io.github.catlinux.keynexa.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import io.github.catlinux.keynexa.desktop.ClipboardTimer
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import java.awt.Desktop
import java.io.IOException
import java.util.UUID

/**
 * What the three-panel browser is looking at: the selected group and entry, the search, the order of the list and the
 * last thing that was copied. It also copies to the clipboard and opens web addresses, so a screen never touches the
 * system on its own.
 */
internal class DesktopVaultState(
    private val clipboard: ClipboardTimer,
) {
    /** The group whose entries the centre panel lists. */
    var selectedGroup by mutableStateOf<UUID?>(null)
        private set

    /** The entry the detail panel shows, or null when nothing is selected. */
    var selectedEntry by mutableStateOf<UUID?>(null)
        private set

    /** What the search box holds; while it is not blank the centre panel shows the matches of the whole vault. */
    var query by mutableStateOf("")

    /** True while the search box has the focus, so Ctrl+C keeps copying the text selected there. */
    var searchFocused by mutableStateOf(false)

    /** The order of the centre panel. */
    var sort by mutableStateOf(EntrySort.Title)
        private set

    /** The groups the user has collapsed, so every other group shows its subgroups. */
    var collapsed by mutableStateOf<Set<UUID>>(emptySet())
        private set

    /** The label of the last copied field, shown for a moment in the status bar. */
    var copied by mutableStateOf<String?>(null)

    /** The search box, so the Ctrl+F shortcut can put the focus there. */
    val searchField = FocusRequester()

    /** The detail panel, so Enter in the list can put the focus there. */
    val detailPanel = FocusRequester()

    /** Points the browser at [vault]: the root is selected, nothing else is. */
    fun open(vault: Vault) {
        selectedGroup = vault.root.id
        selectedEntry = null
        query = ""
        sort = EntrySort.Title
        collapsed = emptySet()
        copied = null
    }

    /** Forgets everything, for when the vault is locked and the browser goes away. */
    fun reset() {
        selectedGroup = null
        selectedEntry = null
        query = ""
        copied = null
    }

    fun selectGroup(group: Group) {
        selectedGroup = group.id
        selectedEntry = null
    }

    fun selectEntry(entryId: UUID) {
        selectedEntry = entryId
    }

    fun changeSort(order: EntrySort) {
        sort = order
    }

    fun toggleGroup(groupId: UUID) {
        collapsed = if (groupId in collapsed) collapsed - groupId else collapsed + groupId
    }

    fun focusSearch() {
        searchField.requestFocus()
    }

    fun focusDetail() {
        detailPanel.requestFocus()
    }

    /** Copies [value], marking it when [sensitive], and shows [label] as the confirmation. */
    fun copy(
        value: String,
        label: String,
        sensitive: Boolean,
    ) {
        val text = value.toCharArray()
        try {
            clipboard.copy(text, sensitive)
        } catch (ignored: IOException) {
            return
        } finally {
            text.fill(Char(0))
        }
        copied = label
    }

    /** Opens [url] in the system browser; does nothing when it is not a web address or there is no browser. */
    fun openUrl(url: String) {
        val uri = browsableUri(url) ?: return
        try {
            Desktop.getDesktop().browse(uri)
        } catch (ignored: IOException) {
            Unit
        } catch (ignored: UnsupportedOperationException) {
            Unit
        }
    }
}
