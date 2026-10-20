@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_attachment_add
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_attachment_remove
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_attachment_too_large
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_attachments_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_cancel
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_custom_add
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_custom_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_custom_protected
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_custom_remove
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_custom_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_custom_value
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_discard_cancel
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_discard_confirm
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_discard_message
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_discard_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_name_repeated
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_name_standard
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_notes_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_save
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_show_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_title_edit
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_title_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_title_new
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_title_required
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_url_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_user_name_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_user_name_suggestions
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_attachment_missing
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_attachment_save_failed
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_attachment_saved
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_attachment_unencrypted
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_hide
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_save_attachment
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_show
import io.github.catlinux.keynexa.session.mapEntry
import io.github.catlinux.keynexa.ui.fields.UserNameField
import io.github.catlinux.keynexa.vault.Attachment
import io.github.catlinux.keynexa.vault.BinaryId
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.MAX_ATTACHMENT_BYTES
import io.github.catlinux.keynexa.vault.UserNameSuggestions
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultEditor
import io.github.catlinux.keynexa.vault.binaryIdOf
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID

private val FormPadding = 16.dp
private val FormGap = 12.dp
private val ItemGap = 6.dp
private val ActionHeight = 44.dp
private const val NOTES_LINES = 4

/**
 * The form for one entry, in place in the detail panel: the same fields the Android editor shows, the password
 * generator, custom fields, attachments and the history. Ctrl+S saves and Esc leaves; leaving asks first when
 * something was typed. Secrets stay in this form only and are cleared when it goes away.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun EntryEditPanel(
    vault: Vault,
    entry: Entry?,
    isNew: Boolean,
    saving: Boolean,
    view: DesktopVaultState,
    parent: Frame,
    modifier: Modifier = Modifier,
) {
    val form = remember(entry?.id, isNew) { EntryForm(entry?.fields) }
    val attachments =
        remember(entry?.id, isNew) {
            mutableStateListOf<Attachment>().apply { addAll(entry?.attachments.orEmpty()) }
        }
    val newBinaries = remember(entry?.id, isNew) { mutableStateMapOf<BinaryId, ByteArray>() }
    var generating by remember { mutableStateOf(false) }
    var confirmingCancel by remember { mutableStateOf(false) }
    var askingToSave by remember { mutableStateOf<Attachment?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val tooLargeMessage = stringResource(Res.string.edit_attachment_too_large)
    val missingMessage = stringResource(Res.string.entry_detail_attachment_missing)
    val failedMessage = stringResource(Res.string.entry_detail_attachment_save_failed)
    DisposableEffect(Unit) {
        onDispose {
            form.clear()
        }
    }
    val unchanged = form.unchanged && attachments.toList() == entry?.attachments.orEmpty() && newBinaries.isEmpty()
    val leave: () -> Unit = {
        if (unchanged) view.closeDetailMode() else confirmingCancel = true
    }
    val save: () -> Unit = {
        val fields = form.toFields()
        val attachmentList = attachments.toList()
        val binaries = newBinaries.values.toList()
        val groupId = view.selectedGroup ?: vault.root.id
        val saved =
            if (entry == null) {
                view.save { current -> addNewEntry(current, groupId, fields, attachmentList, binaries) }
            } else {
                view.save { current ->
                    val editor = VaultEditor()
                    editor.storeBinaries(
                        editor.editEntry(current, entry.id) { it.copy(fields = fields, attachments = attachmentList) },
                        binaries,
                    )
                }
            }
        if (saved) view.closeDetailMode()
    }
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(FormPadding)
                .onPreviewKeyEvent { event -> formShortcut(event, save, leave) },
        verticalArrangement = Arrangement.spacedBy(FormGap),
    ) {
        Text(
            text = stringResource(if (isNew) Res.string.edit_title_new else Res.string.edit_title_edit),
            style = MaterialTheme.typography.titleLarge,
        )
        BasicFields(
            form = form,
            userNameSuggestions = remember(vault) { UserNameSuggestions.of(vault) },
            onGenerate = { generating = true },
        )
        CustomFieldsSection(form = form)
        AttachmentsSection(
            attachments = attachments,
            saving = saving,
            onRemove = { attachment -> attachments.remove(attachment) },
            onAdd = {
                val result = readAttachment(parent)
                when (result) {
                    AttachmentRead.TooLarge -> notice = tooLargeMessage
                    AttachmentRead.Failed -> Unit
                    is AttachmentRead.Read -> {
                        val id = binaryIdOf(result.content)
                        attachments.removeAll { it.name == result.name }
                        attachments += Attachment(result.name, id)
                        newBinaries[id] = result.content
                        notice = null
                    }
                }
            },
            onSave = { attachment -> askingToSave = attachment },
        )
        if (notice != null) {
            Text(
                text = notice.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FormActions(saveEnabled = !form.blocked && !saving, onSave = save, onCancel = leave)
    }
    if (generating) {
        GeneratePasswordDialog(
            onUse = { generated ->
                try {
                    form.password = String(generated)
                } finally {
                    generated.fill(Char(0))
                }
                generating = false
            },
            onDismiss = { generating = false },
        )
    }
    if (confirmingCancel) {
        DiscardDialog(
            onDiscard = {
                confirmingCancel = false
                view.closeDetailMode()
            },
            onKeepEditing = { confirmingCancel = false },
        )
    }
    askingToSave?.let { attachment ->
        SaveAttachmentDialog(
            attachment = attachment,
            onConfirm = {
                askingToSave = null
                notice = saveAttachmentToDisk(parent, attachment, vault, newBinaries, missingMessage, failedMessage)
            },
            onDismiss = { askingToSave = null },
        )
    }
}

/** True when the form handles the event: Ctrl+S saves it and Esc leaves it. */
private fun formShortcut(
    event: KeyEvent,
    save: () -> Unit,
    leave: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.Escape) {
        leave()
        return true
    }
    if (event.isCtrlPressed && event.key == Key.S) {
        save()
        return true
    }
    return false
}

/** The main fields of the entry, in the order the Android editor shows them. */
@Composable
private fun BasicFields(
    form: EntryForm,
    userNameSuggestions: List<String>,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var titleTouched by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = {
                form.title = it
                titleTouched = true
            },
            label = { Text(text = stringResource(Res.string.edit_title_label)) },
            singleLine = true,
            isError = titleTouched && form.title.isBlank(),
            supportingText =
                if (titleTouched && form.title.isBlank()) {
                    { Text(text = stringResource(Res.string.edit_title_required)) }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth(),
        )
        UserNameField(
            value = form.userName,
            onValueChange = { form.userName = it },
            suggestions = userNameSuggestions,
            label = { Text(text = stringResource(Res.string.edit_user_name_label)) },
            toggleDescription = stringResource(Res.string.edit_user_name_suggestions),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(form = form, onGenerate = onGenerate)
        OutlinedTextField(
            value = form.url,
            onValueChange = { form.url = it },
            label = { Text(text = stringResource(Res.string.edit_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { form.notes = it },
            label = { Text(text = stringResource(Res.string.edit_notes_label)) },
            minLines = NOTES_LINES,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The password, with its show switch, its strength and the way to generate one. */
@Composable
private fun PasswordField(
    form: EntryForm,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        OutlinedTextField(
            value = form.password,
            onValueChange = { form.password = it },
            label = { Text(text = stringResource(Res.string.entry_detail_password)) },
            singleLine = true,
            visualTransformation = if (form.revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            val reveal = if (form.revealed) Res.string.entry_detail_hide else Res.string.entry_detail_show
            TextButton(onClick = { form.revealed = !form.revealed }) {
                Text(text = stringResource(reveal))
            }
            TextButton(onClick = onGenerate) {
                Text(text = stringResource(Res.string.edit_generate))
            }
            Text(
                text = stringResource(Res.string.edit_show_password),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PasswordStrength(length = form.password.length, mismatch = false, master = false)
    }
}

/** The custom fields of the entry: add, rename, value, protection and removal. */
@Composable
private fun CustomFieldsSection(
    form: EntryForm,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        Text(
            text = stringResource(Res.string.edit_custom_title),
            style = MaterialTheme.typography.titleMedium,
        )
        form.custom.forEachIndexed { index, field ->
            CustomFieldRow(
                field = field,
                problem = form.nameProblem(index),
                onRemove = { form.removeCustom(index) },
            )
        }
        TextButton(onClick = { form.addCustom() }) {
            Text(text = stringResource(Res.string.edit_custom_add))
        }
    }
}

/** One custom field being edited, with the reason its name cannot be used when there is one. */
@Composable
private fun CustomFieldRow(
    field: CustomField,
    problem: FieldNameProblem?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            OutlinedTextField(
                value = field.name,
                onValueChange = { field.name = it },
                label = { Text(text = stringResource(Res.string.edit_custom_name)) },
                singleLine = true,
                isError = problem != null,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove) {
                Text(text = stringResource(Res.string.edit_custom_remove))
            }
        }
        OutlinedTextField(
            value = field.value,
            onValueChange = { field.value = it },
            label = { Text(text = stringResource(Res.string.edit_custom_value)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = stringResource(Res.string.edit_custom_protected),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = field.isProtected, onCheckedChange = { field.isProtected = it })
        }
        if (problem != null) {
            val text =
                if (problem == FieldNameProblem.Standard) {
                    Res.string.edit_name_standard
                } else {
                    Res.string.edit_name_repeated
                }
            Text(
                text = stringResource(text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The attachments of the entry: add one, remove one, or save one to a file. */
@Composable
private fun AttachmentsSection(
    attachments: List<Attachment>,
    saving: Boolean,
    onRemove: (Attachment) -> Unit,
    onAdd: () -> Unit,
    onSave: (Attachment) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        Text(
            text = stringResource(Res.string.edit_attachments_title),
            style = MaterialTheme.typography.titleMedium,
        )
        attachments.forEach { attachment ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ItemGap),
            ) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSave(attachment) }, enabled = !saving) {
                    Text(text = stringResource(Res.string.entry_detail_save_attachment))
                }
                TextButton(onClick = { onRemove(attachment) }, enabled = !saving) {
                    Text(text = stringResource(Res.string.edit_attachment_remove))
                }
            }
        }
        TextButton(onClick = onAdd, enabled = !saving) {
            Text(text = stringResource(Res.string.edit_attachment_add))
        }
    }
}

/** The way out of the form: save it, or leave it behind. */
@Composable
private fun FormActions(
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.heightIn(min = ActionHeight),
        ) {
            Text(text = stringResource(Res.string.edit_save))
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.heightIn(min = ActionHeight),
        ) {
            Text(text = stringResource(Res.string.edit_cancel))
        }
    }
}

/** Asks before throwing away what was typed. */
@Composable
private fun DiscardDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        modifier = modifier,
        title = { Text(text = stringResource(Res.string.edit_discard_title)) },
        text = { Text(text = stringResource(Res.string.edit_discard_message)) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(text = stringResource(Res.string.edit_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(text = stringResource(Res.string.edit_discard_cancel))
            }
        },
    )
}

/** Warns that a file saved from the vault is not encrypted, before asking where it goes. */
@Composable
private fun SaveAttachmentDialog(
    attachment: Attachment,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(Res.string.entry_detail_save_attachment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
                Text(text = attachment.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(Res.string.entry_detail_attachment_unencrypted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.entry_detail_attachment_saved))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.edit_cancel))
            }
        },
    )
}

/** What reading an attachment from disk answered. */
private sealed interface AttachmentRead {
    data object TooLarge : AttachmentRead

    data object Failed : AttachmentRead

    data class Read(
        val name: String,
        val content: ByteArray,
    ) : AttachmentRead
}

/** Asks for a file and reads it, up to the size the vault takes; a failure is answered, never logged. */
private fun readAttachment(parent: Frame): AttachmentRead {
    val file = chooseAttachment(parent) ?: return AttachmentRead.Failed
    if (file.length() > MAX_ATTACHMENT_BYTES) return AttachmentRead.TooLarge
    val content =
        try {
            Files.readAllBytes(file.toPath())
        } catch (failure: IOException) {
            return AttachmentRead.Failed
        }
    if (content.size > MAX_ATTACHMENT_BYTES) {
        content.fill(0)
        return AttachmentRead.TooLarge
    }
    return AttachmentRead.Read(file.name, content)
}

/** Asks where an attachment goes and writes it; answers a message when it could not be saved. */
@Suppress("LongParameterList")
private fun saveAttachmentToDisk(
    parent: Frame,
    attachment: Attachment,
    vault: Vault,
    newBinaries: Map<BinaryId, ByteArray>,
    missingMessage: String,
    failedMessage: String,
): String? {
    val content = newBinaries[attachment.id] ?: vault.binaries[attachment.id]?.content
    if (content == null) return missingMessage
    val file = chooseAttachmentTarget(parent, attachment.name) ?: return null
    return try {
        Files.write(file.toPath(), content)
        null
    } catch (failure: IOException) {
        failedMessage
    }
}

/** Asks for a file to attach; null when the user cancels. */
private fun chooseAttachment(parent: Frame): File? = chooseFile(parent, null)

/** Asks where an attachment goes, suggesting its name; null when the user cancels. */
private fun chooseAttachmentTarget(
    parent: Frame,
    suggestedName: String,
): File? = chooseFile(parent, suggestedName)

/** The native file dialog both attachment flows use. */
private fun chooseFile(
    parent: Frame,
    suggestedName: String?,
): File? {
    val dialog = FileDialog(parent, "", if (suggestedName == null) FileDialog.LOAD else FileDialog.SAVE)
    if (suggestedName != null) dialog.file = suggestedName
    dialog.isVisible = true
    val directory = dialog.directory
    val name = dialog.file
    return if (directory != null && name != null) File(directory, name) else null
}

/** Adds an entry with its fields already in place, so it has no empty first version in its history. */
internal fun addNewEntry(
    vault: Vault,
    groupId: UUID,
    fields: Map<String, FieldValue>,
    attachments: List<Attachment>,
    newBinaries: Collection<ByteArray>,
): Vault {
    val id = UUID.randomUUID()
    val editor = VaultEditor(newId = { id })
    val added = editor.addEntry(vault, groupId, fields[Entry.TITLE]?.value.orEmpty())
    return editor.storeBinaries(
        added.copy(root = added.root.mapEntry(id) { it.copy(fields = fields, attachments = attachments) }),
        newBinaries,
    )
}
