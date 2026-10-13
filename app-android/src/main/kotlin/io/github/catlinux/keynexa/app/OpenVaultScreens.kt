package io.github.catlinux.keynexa.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.github.catlinux.keynexa.app.nav.Screen
import io.github.catlinux.keynexa.app.ui.bin.BinItem
import io.github.catlinux.keynexa.app.ui.bin.RecycleBinScreen
import io.github.catlinux.keynexa.app.ui.common.SaveStatus
import io.github.catlinux.keynexa.app.ui.edit.EntryEditScreen
import io.github.catlinux.keynexa.app.ui.entry.EntryDetailScreen
import io.github.catlinux.keynexa.app.ui.group.GroupScreen
import io.github.catlinux.keynexa.app.ui.search.SearchScreen
import io.github.catlinux.keynexa.app.vault.SessionState
import io.github.catlinux.keynexa.app.vault.findEntry
import io.github.catlinux.keynexa.app.vault.findGroup
import io.github.catlinux.keynexa.app.vault.mapEntry
import io.github.catlinux.keynexa.app.vault.pathToEntry
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultEditor
import io.github.catlinux.keynexa.vault.VaultSearch
import java.util.UUID

private val WEB_SCHEMES = setOf("http", "https")

/** The screens that show an open vault, connected to the session. Every edit goes through [VaultEditor]. */
@Composable
fun OpenVaultScreens(
    container: AppContainer,
    open: SessionState.Open,
) {
    val navigator = container.navigator
    val session = container.session
    val editor = remember { VaultEditor() }
    val vault = open.current
    val back: () -> Unit = { navigator.back() }
    val context = LocalContext.current
    val lock: () -> Unit = {
        ClipboardGuard.clearNow(context)
        session.lock()
        navigator.reset()
    }

    when (val screen = navigator.current) {
        is Screen.Group -> {
            val group = screen.groupId?.let(vault.root::findGroup) ?: vault.root
            val isRoot = group.id == vault.root.id
            GroupScreen(
                group = group,
                isRoot = isRoot,
                hiddenGroupId = vault.meta.recycleBinGroup,
                status = SaveStatus(open.saving, open.saveProblem, open.hasUnsavedChanges),
                onOpenGroup = { navigator.push(Screen.Group(it)) },
                onOpenEntry = { navigator.push(Screen.EntryDetail(it)) },
                onAddEntry = { navigator.push(Screen.EntryEdit(null, group.id)) },
                onAddGroup = { name -> session.apply { editor.addGroup(it, group.id, name) } },
                onSearch = { navigator.push(Screen.Search(group.id.takeUnless { isRoot })) },
                onOpenRecycleBin = { navigator.push(Screen.RecycleBin) },
                onLock = lock,
                onBack = if (isRoot) lock else back,
                onRetrySave = session::retrySave,
            )
        }
        is Screen.Search ->
            SearchScreen(
                search = { query -> VaultSearch().search(vault, query, screen.inGroup) },
                onOpenEntry = { navigator.push(Screen.EntryDetail(it)) },
                onBack = back,
            )
        is Screen.EntryDetail -> EntryDetail(container, vault, screen.entryId, editor)
        is Screen.EntryEdit -> EntryEdit(container, vault, screen, editor)
        Screen.RecycleBin -> RecycleBin(container, vault, editor)
        else -> Unit
    }
}

@Composable
private fun EntryDetail(
    container: AppContainer,
    vault: Vault,
    entryId: UUID,
    editor: VaultEditor,
) {
    val context = LocalContext.current
    val navigator = container.navigator
    val entry = vault.root.findEntry(entryId)
    // An entry that disappeared (for example after a merge) takes its screen with it.
    LaunchedEffect(entry == null) { if (entry == null) navigator.back() }
    if (entry == null) return
    EntryDetailScreen(
        entry = entry,
        path = vault.root.pathToEntry(entryId),
        onEdit = { navigator.push(Screen.EntryEdit(entryId, null)) },
        onDelete = {
            if (container.session.apply { editor.delete(it, entryId) }) navigator.back()
        },
        onCopy = {
            label,
            value,
            sensitive,
            ->
            ClipboardGuard.copy(context, label, value, sensitive, container.settings.values.clipboardSeconds)
        },
        onOpenUrl = { openWebAddress(context, it) },
        onBack = { navigator.back() },
    )
}

@Composable
private fun EntryEdit(
    container: AppContainer,
    vault: Vault,
    screen: Screen.EntryEdit,
    editor: VaultEditor,
) {
    val navigator = container.navigator
    // Taken once: a refresh from disk while the user types must not reset the form.
    val existing = remember(screen) { screen.entryId?.let(vault.root::findEntry) }
    LaunchedEffect(existing == null && screen.entryId != null) {
        if (existing == null && screen.entryId != null) navigator.back()
    }
    if (existing == null && screen.entryId != null) return
    EntryEditScreen(
        initial = existing?.fields,
        isNew = screen.entryId == null,
        onSave = { fields ->
            val saved =
                if (screen.entryId != null) {
                    container.session.apply { v -> editor.editEntry(v, screen.entryId) { it.copy(fields = fields) } }
                } else {
                    addEntry(container, screen.groupId ?: vault.root.id, fields)
                }
            if (saved) navigator.back()
        },
        onCancel = { navigator.back() },
    )
}

// A new entry is created with its fields already in place, so it has no empty first version in its history.
private fun addEntry(
    container: AppContainer,
    groupId: UUID,
    fields: Map<String, FieldValue>,
): Boolean {
    val id = UUID.randomUUID()
    val creator = VaultEditor(newId = { id })
    val title = fields[Entry.TITLE]?.value.orEmpty()
    return container.session.apply { v ->
        val added = creator.addEntry(v, groupId, title)
        added.copy(root = added.root.mapEntry(id) { it.copy(fields = fields) })
    }
}

@Composable
private fun RecycleBin(
    container: AppContainer,
    vault: Vault,
    editor: VaultEditor,
) {
    val navigator = container.navigator
    val session = container.session
    val bin = vault.meta.recycleBinGroup?.let(vault.root::findGroup)
    RecycleBinScreen(
        items = bin?.let(::binItems).orEmpty(),
        onRestore = { id -> session.apply { editor.restore(it, id) } },
        onDeleteForever = { id -> session.apply { editor.delete(it, id) } },
        onEmpty = { session.apply { editor.emptyRecycleBin(it) } },
        onBack = { navigator.back() },
    )
}

private fun binItems(bin: Group): List<BinItem> =
    bin.groups.map { BinItem(it.id, it.name, isGroup = true, detail = (it.entries.size + it.groups.size).toString()) } +
        bin.entries.map { BinItem(it.id, it.title, isGroup = false, detail = it.userName) }

private fun openWebAddress(
    context: Context,
    address: String,
) {
    val uri = address.trim().toUri()
    if (uri.scheme?.lowercase() in WEB_SCHEMES) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
