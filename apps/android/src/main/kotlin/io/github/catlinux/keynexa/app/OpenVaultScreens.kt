package io.github.catlinux.keynexa.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.github.catlinux.keynexa.app.nav.Screen
import io.github.catlinux.keynexa.app.ui.bin.BinItem
import io.github.catlinux.keynexa.app.ui.bin.RecycleBinScreen
import io.github.catlinux.keynexa.app.ui.common.ConflictNotice
import io.github.catlinux.keynexa.app.ui.common.SaveStatus
import io.github.catlinux.keynexa.app.ui.edit.EntryEditScreen
import io.github.catlinux.keynexa.app.ui.entry.EntryDetailScreen
import io.github.catlinux.keynexa.app.ui.entry.isWebAddress
import io.github.catlinux.keynexa.app.ui.group.GroupScreen
import io.github.catlinux.keynexa.app.ui.search.SearchScreen
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.session.findEntry
import io.github.catlinux.keynexa.session.findGroup
import io.github.catlinux.keynexa.session.mapEntry
import io.github.catlinux.keynexa.session.pathToEntry
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultEditor
import io.github.catlinux.keynexa.vault.VaultSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** The screens that show an open vault, connected to the session. Every edit goes through [VaultEditor]. */
@Suppress("LongMethod")
@Composable
fun OpenVaultScreens(
    container: AppContainer,
    open: SessionState.Open,
    onReconnect: () -> Unit,
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
            // Storage is slow and may fail (a moved or revoked file): never ask it from the composition thread.
            val fileModified by produceState<Long?>(null, group.id, open.loaded.fingerprint) {
                value =
                    withContext(Dispatchers.IO) {
                        try {
                            container.storage.lastModified(open.vault.location)
                        } catch (unavailable: IOException) {
                            null
                        }
                    }
            }
            GroupScreen(
                group = group,
                isRoot = isRoot,
                rootTitle = open.vault.name,
                hiddenGroupId = vault.meta.recycleBinGroup,
                status = SaveStatus(open.saving, open.saveProblem, open.hasUnsavedChanges),
                recovered = open.recovered,
                syncWarnDays = container.settings.values.syncWarnDays,
                fileLastModifiedMillis = fileModified,
                lastKeyNexaSaveMillis = open.lastKeyNexaSaveMillis,
                lastExternalChangeMillis = open.lastExternalChangeMillis,
                conflicts =
                    open.conflicts?.let {
                        ConflictNotice(
                            mergedNow = it.mergedNow,
                            deletable = it.deletable.size,
                            problems = it.failed.distinct(),
                            canEnterOtherPassword = it.otherPasswordCopies.isNotEmpty(),
                            otherPasswordRejected = it.otherPasswordRejected,
                        )
                    },
                onOpenGroup = { navigator.push(Screen.Group(it)) },
                onOpenEntry = { navigator.push(Screen.EntryDetail(it)) },
                onAddEntry = { navigator.push(Screen.EntryEdit(null, group.id)) },
                onAddGroup = { name -> session.apply { editor.addGroup(it, group.id, name) } },
                onSearch = { navigator.push(Screen.Search(group.id.takeUnless { isRoot })) },
                onOpenRecycleBin = { navigator.push(Screen.RecycleBin) },
                onLock = lock,
                onOpenSettings = { navigator.push(Screen.Settings) },
                onBack = if (isRoot) lock else back,
                onRetrySave = session::retrySave,
                onReconnect = onReconnect,
                onRestoreUnsaved = session::restoreUnsaved,
                onDiscardUnsaved = session::discardUnsaved,
                onDeleteConflictCopies = session::deleteConflictCopies,
                onMergeOtherPassword = { password ->
                    session.mergeOtherPasswordCopies(Credentials.of(password = password))
                },
                onDismissConflicts = session::dismissConflicts,
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
        vaultBinaries = vault.binaries,
        onEdit = { navigator.push(Screen.EntryEdit(entryId, null)) },
        onDelete = {
            if (container.session.apply { editor.delete(it, entryId) }) navigator.back()
        },
        onClearHistory = { container.session.apply { editor.clearHistory(it, entryId) } },
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
        initialAttachments = existing?.attachments.orEmpty(),
        onSave = { fields, attachments, newBinaries ->
            val saved =
                if (screen.entryId != null) {
                    container.session.apply { v ->
                        editor.storeBinaries(
                            editor.editEntry(v, screen.entryId) { it.copy(fields = fields, attachments = attachments) },
                            newBinaries.values,
                        )
                    }
                } else {
                    addEntry(container, screen.groupId ?: vault.root.id, fields, attachments, newBinaries)
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
    attachments: List<io.github.catlinux.keynexa.vault.Attachment> = emptyList(),
    newBinaries: Map<io.github.catlinux.keynexa.vault.BinaryId, ByteArray> = emptyMap(),
): Boolean {
    val id = UUID.randomUUID()
    val creator = VaultEditor(newId = { id })
    val title = fields[Entry.TITLE]?.value.orEmpty()
    return container.session.apply { v ->
        val added = creator.addEntry(v, groupId, title)
        creator.storeBinaries(
            added.copy(root = added.root.mapEntry(id) { it.copy(fields = fields, attachments = attachments) }),
            newBinaries.values,
        )
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
    if (!isWebAddress(address)) return
    try {
        val view = Intent(Intent.ACTION_VIEW, address.trim().toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        // No browser is installed: there is nothing to open it with, and the address can still be copied.
    }
}
