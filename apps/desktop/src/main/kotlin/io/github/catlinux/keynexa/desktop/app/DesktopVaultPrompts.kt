@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.bin_delete_confirm
import io.github.catlinux.keynexa.desktop.app.generated.resources.bin_delete_dialog_message
import io.github.catlinux.keynexa.desktop.app.generated.resources.bin_delete_dialog_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.bin_delete_forever
import io.github.catlinux.keynexa.desktop.app.generated.resources.bin_empty_confirm
import io.github.catlinux.keynexa.desktop.app.generated.resources.bin_empty_dialog_message
import io.github.catlinux.keynexa.desktop.app.generated.resources.bin_empty_dialog_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.bin_restore
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_cancel
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_delete_confirm
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_delete_message
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_delete_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_edit
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_move_to
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_delete_confirm
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_delete_message
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_delete_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_new_group
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_new_group_create
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_new_group_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_new_group_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_rename
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_rename_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.move_to_bin
import io.github.catlinux.keynexa.desktop.app.generated.resources.move_to_empty
import io.github.catlinux.keynexa.desktop.app.generated.resources.move_to_title
import io.github.catlinux.keynexa.session.findEntry
import io.github.catlinux.keynexa.session.findGroup
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultEditor
import org.jetbrains.compose.resources.stringResource
import java.util.UUID

private val PromptGap = 10.dp
private val PromptListHeight = 280.dp
private val PromptIndent = 16.dp
private val PromptTouch = 40.dp

/** The label a menu entry shows for [action]. */
@Composable
internal fun actionLabel(action: NodeAction): String =
    stringResource(
        when (action) {
            NodeAction.NewSubgroup -> Res.string.group_new_group
            NodeAction.Rename -> Res.string.group_rename
            NodeAction.MoveTo -> Res.string.entry_move_to
            NodeAction.MoveToBin -> Res.string.move_to_bin
            NodeAction.Edit -> Res.string.entry_detail_edit
            NodeAction.Restore -> Res.string.bin_restore
            NodeAction.DeleteForever -> Res.string.bin_delete_forever
        },
    )

/** Puts the actions of [actions] behind a right click on [content]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NodeContextMenu(
    actions: List<NodeAction>,
    onAction: (NodeAction) -> Unit,
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty()) {
        content()
        return
    }
    val items = mutableListOf<ContextMenuItem>()
    for (action in actions) {
        items += ContextMenuItem(label = actionLabel(action)) { onAction(action) }
    }
    ContextMenuArea(items = { items }) {
        content()
    }
}

/** Runs what the context menu of [objectId] asked for. */
internal fun runNodeAction(
    action: NodeAction,
    objectId: UUID,
    view: DesktopVaultState,
) {
    when (action) {
        NodeAction.NewSubgroup -> view.ask(VaultPrompt.NewGroup(objectId))
        NodeAction.Rename -> view.ask(VaultPrompt.RenameGroup(objectId))
        NodeAction.MoveTo -> view.ask(VaultPrompt.MoveTo(objectId))
        NodeAction.MoveToBin -> view.ask(VaultPrompt.MoveToBin(objectId))
        NodeAction.Edit -> {
            view.selectEntry(objectId)
            view.startEdit()
        }
        NodeAction.Restore -> view.save { vault -> VaultEditor().restore(vault, objectId) }
        NodeAction.DeleteForever -> view.ask(VaultPrompt.DeleteForever(objectId))
    }
}

/**
 * The question the browser is waiting for: a name for a group, the group an object moves to, or the confirmation of
 * something that cannot be undone. Nothing is drawn when there is no question.
 */
@Composable
internal fun VaultPromptDialogs(
    vault: Vault,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    when (val prompt = view.prompt) {
        null -> Unit
        is VaultPrompt.NewGroup ->
            NameGroupDialog(
                title = stringResource(Res.string.group_new_group_title),
                confirm = stringResource(Res.string.group_new_group_create),
                initial = "",
                onConfirm = { name ->
                    view.dismissPrompt()
                    view.save { current -> VaultEditor().addGroup(current, prompt.parentId, name) }
                },
                onDismiss = view::dismissPrompt,
                modifier = modifier,
            )
        is VaultPrompt.RenameGroup -> {
            val group = vault.root.findGroup(prompt.groupId)
            NameGroupDialog(
                title = stringResource(Res.string.group_rename_title),
                confirm = stringResource(Res.string.group_rename),
                initial = group?.name.orEmpty(),
                onConfirm = { name ->
                    view.dismissPrompt()
                    view.save { current -> VaultEditor().renameGroup(current, prompt.groupId, name) }
                },
                onDismiss = view::dismissPrompt,
                modifier = modifier,
            )
        }
        is VaultPrompt.MoveTo ->
            MoveToDialog(
                groups = moveDestinations(vault, prompt.objectId),
                onConfirm = { target ->
                    view.dismissPrompt()
                    view.save { current -> VaultEditor().move(current, prompt.objectId, target) }
                },
                onDismiss = view::dismissPrompt,
                modifier = modifier,
            )
        else -> ConfirmPrompt(prompt = prompt, vault = vault, view = view, modifier = modifier)
    }
}

/** The questions that only need a yes: moving something to the bin, erasing it, or emptying the bin. */
@Composable
private fun ConfirmPrompt(
    prompt: VaultPrompt,
    vault: Vault,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    when (prompt) {
        is VaultPrompt.MoveToBin -> {
            val group = isGroup(vault, prompt.objectId)
            val title = if (group) Res.string.group_delete_title else Res.string.entry_detail_delete_title
            val message = if (group) Res.string.group_delete_message else Res.string.entry_detail_delete_message
            val confirm = if (group) Res.string.group_delete_confirm else Res.string.entry_detail_delete_confirm
            ConfirmDialog(
                title = stringResource(title),
                message = stringResource(message),
                confirm = stringResource(confirm),
                onConfirm = {
                    view.dismissPrompt()
                    if (view.save { current -> VaultEditor().delete(current, prompt.objectId) }) {
                        view.selectEntry(selectionAfterRemoval(view.visibleRows, prompt.objectId))
                    }
                },
                onDismiss = view::dismissPrompt,
                modifier = modifier,
            )
        }
        is VaultPrompt.DeleteForever ->
            ConfirmDialog(
                title = stringResource(Res.string.bin_delete_dialog_title, nameOf(vault, prompt.objectId)),
                message = stringResource(Res.string.bin_delete_dialog_message),
                confirm = stringResource(Res.string.bin_delete_confirm),
                onConfirm = {
                    view.dismissPrompt()
                    view.save { current -> VaultEditor().delete(current, prompt.objectId) }
                },
                onDismiss = view::dismissPrompt,
                modifier = modifier,
            )
        VaultPrompt.EmptyBin ->
            ConfirmDialog(
                title = stringResource(Res.string.bin_empty_dialog_title),
                message = stringResource(Res.string.bin_empty_dialog_message),
                confirm = stringResource(Res.string.bin_empty_confirm),
                onConfirm = {
                    view.dismissPrompt()
                    view.save { current -> VaultEditor().emptyRecycleBin(current) }
                },
                onDismiss = view::dismissPrompt,
                modifier = modifier,
            )
        else -> Unit
    }
}

/** True when [objectId] is a group of this vault. */
private fun isGroup(
    vault: Vault,
    objectId: UUID,
): Boolean = vault.root.findGroup(objectId) != null

/** The name to show for [objectId]: the title of an entry or the name of a group. */
private fun nameOf(
    vault: Vault,
    objectId: UUID,
): String =
    vault.root.findGroup(objectId)?.name
        ?: vault.root
            .findEntry(objectId)
            ?.title
            .orEmpty()

/** A dialog that asks for the name of a group. */
@Composable
@Suppress("LongParameterList")
private fun NameGroupDialog(
    title: String,
    confirm: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(Res.string.group_new_group_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(text = confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.edit_cancel))
            }
        },
    )
}

/** A dialog that offers the groups an object may move to, in tree order. */
@Composable
private fun MoveToDialog(
    groups: List<Group>,
    onConfirm: (UUID) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by remember { mutableStateOf<UUID?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(Res.string.move_to_title)) },
        text = {
            if (groups.isEmpty()) {
                Text(text = stringResource(Res.string.move_to_empty))
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = PromptListHeight)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PromptGap),
                ) {
                    groups.forEach { group ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = PromptTouch)
                                    .clickable(role = Role.RadioButton, onClick = { chosen = group.id }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = chosen == group.id,
                                onClick = { chosen = group.id },
                            )
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = PromptIndent),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { chosen?.let(onConfirm) }, enabled = chosen != null) {
                Text(text = stringResource(Res.string.entry_move_to))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.edit_cancel))
            }
        },
    )
}

/** A dialog that confirms something which cannot be undone. */
@Composable
@Suppress("LongParameterList")
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.edit_cancel))
            }
        },
    )
}
