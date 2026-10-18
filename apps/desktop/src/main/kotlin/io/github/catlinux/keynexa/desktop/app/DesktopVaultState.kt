package io.github.catlinux.keynexa.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import io.github.catlinux.keynexa.desktop.ClipboardTimer
import io.github.catlinux.keynexa.session.VaultSession
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import java.awt.Desktop
import java.io.IOException
import java.util.UUID

/** What the detail panel shows: the entry read-only, the form, or one previous version. */
internal sealed interface DetailMode {
    /** The selected entry, read-only. */
    data object Read : DetailMode

    /** The form, for the selected entry or for one that is being created. */
    data class Edit(
        val isNew: Boolean,
    ) : DetailMode

    /** One previous version of the selected entry, read-only. */
    data class Version(
        val index: Int,
    ) : DetailMode
}

/** What the browser is asking the user to fill in or to confirm. */
internal sealed interface VaultPrompt {
    /** A name for the new group inside [parentId]. */
    data class NewGroup(
        val parentId: UUID,
    ) : VaultPrompt

    /** A new name for the group [groupId]. */
    data class RenameGroup(
        val groupId: UUID,
    ) : VaultPrompt

    /** The group [objectId] moves to. */
    data class MoveTo(
        val objectId: UUID,
    ) : VaultPrompt

    /** Confirmation that [objectId] goes to the recycle bin. */
    data class MoveToBin(
        val objectId: UUID,
    ) : VaultPrompt

    /** Confirmation that [objectId] is erased for good. */
    data class DeleteForever(
        val objectId: UUID,
    ) : VaultPrompt

    /** Confirmation that everything in the recycle bin is erased. */
    data object EmptyBin : VaultPrompt
}

/**
 * The selection to keep: [selected] itself while it is one of [visible], and nothing when it is not, so the detail
 * goes back to its empty state when a search or a change of group leaves the selected entry out.
 */
internal fun selectionAmong(
    selected: UUID?,
    visible: Collection<UUID>,
): UUID? = selected?.takeIf { it in visible }

/** The group a new entry goes to: the selected one, or the root when the bin or a search is showing. */
internal fun newEntryGroup(
    vault: Vault,
    selectedGroup: UUID?,
    searching: Boolean,
): UUID {
    if (searching || selectedGroup == null || selectedGroup == vault.meta.recycleBinGroup) return vault.root.id
    return selectedGroup
}

/**
 * What the three-panel browser is looking at: the selected group and entry, the search, the order of the list, the
 * detail mode and the last thing that was copied. It also saves through the session, copies to the clipboard and opens
 * web addresses, so a screen never touches the system on its own.
 */
@Suppress("TooManyFunctions")
internal class DesktopVaultState(
    private val clipboard: ClipboardTimer,
    private val session: VaultSession,
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

    /** What the detail panel shows. */
    var mode by mutableStateOf<DetailMode>(DetailMode.Read)
        private set

    /** The question the browser is waiting for an answer to, or null. */
    var prompt by mutableStateOf<VaultPrompt?>(null)
        private set

    /** The entries the centre panel is showing, in order, so a removal can move the selection sensibly. */
    var visibleRows by mutableStateOf<List<UUID>>(emptyList())
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
        mode = DetailMode.Read
        copied = null
    }

    /** Forgets everything, for when the vault is locked and the browser goes away. */
    fun reset() {
        selectedGroup = null
        selectedEntry = null
        query = ""
        mode = DetailMode.Read
        copied = null
    }

    fun selectGroup(group: Group) {
        selectedGroup = group.id
        selectedEntry = null
        mode = DetailMode.Read
    }

    fun selectEntry(entryId: UUID?) {
        selectedEntry = entryId
        mode = DetailMode.Read
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

    /** Opens the form for a new entry, which the panel adds to the group the list is showing. */
    fun startNewEntry(vault: Vault) {
        selectedEntry = null
        selectedGroup = newEntryGroup(vault, selectedGroup, query.isNotBlank())
        mode = DetailMode.Edit(isNew = true)
    }

    /** Opens the form for the selected entry. */
    fun startEdit() {
        if (selectedEntry == null) return
        mode = DetailMode.Edit(isNew = false)
    }

    /** Shows one previous version of the selected entry, read-only. */
    fun showVersion(index: Int) {
        if (selectedEntry == null) return
        mode = DetailMode.Version(index)
    }

    /** Leaves the form or the version view and shows the entry again. */
    fun closeDetailMode() {
        mode = DetailMode.Read
    }

    /** Saves [change] through the session; false while a save is already running, so the form stays open. */
    fun save(change: (Vault) -> Vault): Boolean = session.apply(change)

    /** Asks the user to fill in or confirm something; only one question is open at a time. */
    fun ask(question: VaultPrompt) {
        prompt = question
    }

    /** Answers nothing: the question is dropped and nothing changes. */
    fun dismissPrompt() {
        prompt = null
    }

    /** Records what the centre panel is showing, so a removal knows which entry to select next. */
    fun showRows(ids: List<UUID>) {
        visibleRows = ids
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
