@file:Suppress("TooManyFunctions", "MaxLineLength")

package io.github.catlinux.keynexa.app.ui.edit

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.vault.Attachment
import io.github.catlinux.keynexa.vault.BinaryId
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.MAX_ATTACHMENT_BYTES
import io.github.catlinux.keynexa.vault.Options
import io.github.catlinux.keynexa.vault.PasswordGenerator
import io.github.catlinux.keynexa.vault.binaryIdOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ScreenPadding = 20.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp

/** The compact bar at the very top. */
private val HeaderPaddingStart = 12.dp
private val HeaderPaddingTop = 20.dp
private val HeaderPaddingBottom = 8.dp

/** One card per group of fields, with a title in capitals. */
private val CardGap = 12.dp
private val FieldGap = 10.dp
private val CardCorner = 16.dp
private val FieldCorner = 14.dp
private val CardBorderWidth = 1.dp
private const val CARD_BORDER_ALPHA = 0.4f
private val CardPaddingHorizontal = 16.dp
private val CardTitlePaddingTop = 14.dp
private val CardTitlePaddingBottom = 4.dp
private val CardPaddingBottom = 14.dp

/** The fixed bar at the bottom. */
private val ActionPaddingTop = 16.dp
private val ActionPaddingBottom = 24.dp
private val ActionHeight = 54.dp
private val ActionCorner = 14.dp

/** The length the generator dialog offers. */
private const val MIN_GENERATED_LENGTH = Options.MIN_LENGTH

private const val MAX_GENERATED_LENGTH = 64

private const val IO_BUFFER_BYTES = 8192

private const val LENGTH_STEPS = MAX_GENERATED_LENGTH - MIN_GENERATED_LENGTH - 1

/** Overwrites a generated password as soon as it has been copied into the field. */
private val FILL = Char(0)

/** The names the KDBX format gives to the fields this form shows in its own way. */
private val STANDARD_FIELDS = setOf(Entry.TITLE, Entry.USER_NAME, Entry.PASSWORD, Entry.URL, Entry.NOTES)

/** Why the name of a custom field cannot be used. */
private enum class NameProblem { Repeated, Standard }

/** One custom field being edited. */
private class CustomField(
    name: String,
    value: String,
    isProtected: Boolean,
) {
    var name by mutableStateOf(name)
    var value by mutableStateOf(value)
    var isProtected by mutableStateOf(isProtected)
}

/**
 * What the form holds right now. Secrets live here only while the screen is on: nothing goes to saved state.
 */
private class EntryForm(
    initial: Map<String, FieldValue>?,
) {
    private val initialFields: Map<String, FieldValue> = initial.orEmpty()

    var title by mutableStateOf(initialFields[Entry.TITLE]?.value.orEmpty())
    var userName by mutableStateOf(initialFields[Entry.USER_NAME]?.value.orEmpty())
    var password by mutableStateOf(initialFields[Entry.PASSWORD]?.value.orEmpty())
    var url by mutableStateOf(initialFields[Entry.URL]?.value.orEmpty())
    var notes by mutableStateOf(initialFields[Entry.NOTES]?.value.orEmpty())
    var revealed by mutableStateOf(false)
    val custom = mutableStateListOf<CustomField>()

    init {
        initialFields.forEach { (name, field) ->
            if (name !in STANDARD_FIELDS) custom += CustomField(name, field.value, field.protected)
        }
    }

    /** True while the form holds exactly what the entry already had. */
    val unchanged: Boolean get() = toFields() == initialFields

    /** True when the entry cannot be saved: no title, or a custom field name that cannot be used. */
    val blocked: Boolean get() = title.isBlank() || custom.indices.any { nameProblem(it) != null }

    /** Why the custom field at [index] cannot be saved, or null when nothing is wrong with it. */
    fun nameProblem(index: Int): NameProblem? {
        val name = custom[index].name.trim()
        return when {
            name.isEmpty() -> null
            name in STANDARD_FIELDS -> NameProblem.Standard
            custom.withIndex().any { (other, field) -> other != index && field.name.trim() == name } ->
                NameProblem.Repeated
            else -> null
        }
    }

    fun addCustom() {
        custom += CustomField(name = "", value = "", isProtected = false)
    }

    /**
     * The fields to save. An empty standard field only survives when the entry already had it, protected fields keep
     * their protection, and the password is protected unless the entry said otherwise (a new one is protected).
     */
    fun toFields(): Map<String, FieldValue> {
        val fields = LinkedHashMap<String, FieldValue>()
        putStandard(fields, Entry.TITLE, title.trim(), protected = null)
        putStandard(fields, Entry.USER_NAME, userName, protected = null)
        putStandard(fields, Entry.PASSWORD, password, protected = passwordProtected)
        putStandard(fields, Entry.URL, url, protected = null)
        putStandard(fields, Entry.NOTES, notes, protected = null)
        custom.forEach { field ->
            val name = field.name.trim()
            if (name.isNotEmpty()) fields[name] = FieldValue(field.value, protected = field.isProtected)
        }
        return fields
    }

    private fun putStandard(
        fields: MutableMap<String, FieldValue>,
        name: String,
        value: String,
        protected: Boolean?,
    ) {
        if (value.isNotEmpty() || name in initialFields) {
            fields[name] = FieldValue(value, protected = protected ?: fieldProtection(name))
        }
    }

    private fun fieldProtection(name: String): Boolean = initialFields[name]?.protected ?: false

    private val passwordProtected: Boolean = initialFields[Entry.PASSWORD]?.protected ?: true
}

/** The attached files: each shows its name, with a button to remove it, plus a button to add one. */
@Composable
private fun AttachmentsSection(
    attachments: List<Attachment>,
    onRemove: (Attachment) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onRemove(attachment) }) {
                    Text(text = stringResource(R.string.edit_attachment_remove))
                }
            }
        }
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.edit_attachment_add))
        }
    }
}

/** Result of trying to read one picked file for attaching. */
private sealed interface ReadResult {
    data object TooLarge : ReadResult

    data object Failed : ReadResult

    data class Success(
        val name: String,
        val bytes: ByteArray,
    ) : ReadResult
}

/** Reads the display name of a picked document in a background-safe way. */
private fun queryDisplayName(
    context: android.content.Context,
    uri: Uri,
): String? =
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(
                    cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME),
                )
            } else {
                null
            }
        }
    } catch (failure: IOException) {
        null
    }

/** Reads the size of a picked document, or null when the provider does not say. */
private fun querySize(
    context: android.content.Context,
    uri: Uri,
): Long? =
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))) {
                cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
            } else {
                null
            }
        }
    } catch (failure: IOException) {
        null
    }

/**
 * Creates or edits one entry.
 *
 * A stateless screen: it shows the fields it is given and reports what the user typed. The password and the
 * protected fields are hidden by default, are never written to saved state and are never logged. [initial] is null
 * while creating an entry.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun EntryEditScreen(
    initial: Map<String, FieldValue>?,
    isNew: Boolean,
    initialAttachments: List<Attachment>,
    onSave: (
        fields: Map<String, FieldValue>,
        attachments: List<Attachment>,
        newBinaries: Map<BinaryId, ByteArray>,
    ) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val form = remember(initial) { EntryForm(initial) }
    val attachments =
        remember {
            androidx.compose.runtime
                .mutableStateListOf<Attachment>()
                .apply { addAll(initialAttachments) }
        }
    val newBinaries = remember { androidx.compose.runtime.mutableStateMapOf<BinaryId, ByteArray>() }
    var generating by remember { mutableStateOf(false) }
    var confirmingCancel by remember { mutableStateOf(false) }
    var attachmentsChanged by remember { mutableStateOf(false) }
    val formUnchanged =
        form.unchanged && !attachmentsChanged && attachments.toList() == initialAttachments
    val requestCancel: () -> Unit = {
        if (formUnchanged) {
            onCancel()
        } else {
            confirmingCancel = true
        }
    }
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val result =
                        withContext(Dispatchers.IO) {
                            val name =
                                queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "attachment"
                            val size = querySize(context, uri)
                            if (size != null && size > MAX_ATTACHMENT_BYTES) {
                                ReadResult.TooLarge
                            } else {
                                val bytes =
                                    try {
                                        context.contentResolver.openInputStream(uri)?.use { input ->
                                            val buffer = ByteArrayOutputStream()
                                            val chunk = ByteArray(IO_BUFFER_BYTES)
                                            var remaining = MAX_ATTACHMENT_BYTES + 1
                                            while (remaining > 0) {
                                                val read = input.read(chunk, 0, minOf(chunk.size, remaining))
                                                if (read < 0) break
                                                buffer.write(chunk, 0, read)
                                                remaining -= read
                                            }
                                            buffer.toByteArray()
                                        }
                                    } catch (failure: IOException) {
                                        null
                                    }
                                if (bytes == null) {
                                    ReadResult.Failed
                                } else if (bytes.size > MAX_ATTACHMENT_BYTES) {
                                    ReadResult.TooLarge
                                } else {
                                    ReadResult.Success(name, bytes)
                                }
                            }
                        }
                    when (result) {
                        ReadResult.TooLarge ->
                            Toast.makeText(context, R.string.edit_attachment_too_large, Toast.LENGTH_SHORT).show()
                        ReadResult.Failed -> Unit
                        is ReadResult.Success -> {
                            val id = binaryIdOf(result.bytes)
                            attachments.removeAll { it.name == result.name }
                            attachments += Attachment(result.name, id)
                            newBinaries[id] = result.bytes
                            attachmentsChanged = true
                        }
                    }
                }
            }
        }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            EditHeader(isNew = isNew, onBack = requestCancel)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = ScreenPadding, vertical = CardGap),
                    verticalArrangement = Arrangement.spacedBy(CardGap),
                ) {
                    EditCard(titleRes = R.string.edit_basic_title) {
                        BasicSection(form = form, onGenerate = { generating = true })
                    }
                    EditCard(titleRes = R.string.edit_custom_title) {
                        CustomSection(form = form)
                    }
                    EditCard(titleRes = R.string.edit_attachments_title) {
                        AttachmentsSection(
                            attachments = attachments,
                            onRemove = { attachment ->
                                attachments -= attachment
                                attachmentsChanged = true
                            },
                            onAdd = { filePicker.launch(arrayOf("*/*")) },
                        )
                    }
                }
            }
            EditActions(
                saveEnabled = !form.blocked,
                onSave = { onSave(form.toFields(), attachments.toList(), newBinaries.toMap()) },
                onCancel = requestCancel,
            )
        }
    }
    if (generating) {
        GenerateDialog(
            onUse = { generated ->
                form.password = generated.concatToString()
                generated.fill(FILL)
                generating = false
            },
            onDismiss = { generating = false },
        )
    }
    if (confirmingCancel) {
        DiscardDialog(
            onDiscard = {
                confirmingCancel = false
                onCancel()
            },
            onDismiss = { confirmingCancel = false },
        )
    }
}

/** The title of the screen and the way back, which cancels the edition. */
@Composable
private fun EditHeader(
    isNew: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = HeaderPaddingStart,
                    end = ScreenPadding,
                    top = HeaderPaddingTop,
                    bottom = HeaderPaddingBottom,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.edit_back),
            )
        }
        Text(
            text =
                stringResource(
                    if (isNew) R.string.edit_title_new else R.string.edit_title_edit,
                ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A group of fields in a card with a title in capitals. */
@Composable
private fun EditCard(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CardBorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column {
            Text(
                text = stringResource(titleRes).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        start = CardPaddingHorizontal,
                        end = CardPaddingHorizontal,
                        top = CardTitlePaddingTop,
                        bottom = CardTitlePaddingBottom,
                    ),
            )
            Column(
                modifier =
                    Modifier.padding(
                        start = CardPaddingHorizontal,
                        end = CardPaddingHorizontal,
                        bottom = CardPaddingBottom,
                    ),
                verticalArrangement = Arrangement.spacedBy(FieldGap),
            ) {
                content()
            }
        }
    }
}

/** The fields every entry has, and the button that fills the password with a generated one. */
@Composable
private fun BasicSection(
    form: EntryForm,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The reminder waits until the user has typed in the field, so a new entry does not open with an error.
    var titleTouched by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        EditTextField(
            label = stringResource(R.string.edit_title_label),
            value = form.title,
            onValueChange = {
                titleTouched = true
                form.title = it
            },
        )
        if (titleTouched && form.title.isBlank()) {
            Text(
                text = stringResource(R.string.edit_title_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        EditTextField(
            label = stringResource(R.string.edit_user_name_label),
            value = form.userName,
            onValueChange = { form.userName = it },
        )
        SecretField(form = form)
        TextButton(onClick = onGenerate) {
            Text(text = stringResource(R.string.edit_generate))
        }
        EditTextField(
            label = stringResource(R.string.edit_url_label),
            value = form.url,
            onValueChange = { form.url = it },
        )
        EditTextField(
            label = stringResource(R.string.edit_notes_label),
            value = form.notes,
            onValueChange = { form.notes = it },
            singleLine = false,
        )
    }
}

/** A labelled text field. */
@Composable
private fun EditTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = singleLine,
        shape = RoundedCornerShape(FieldCorner),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The password, hidden until the user says otherwise. */
@Composable
private fun SecretField(
    form: EntryForm,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        OutlinedTextField(
            value = form.password,
            onValueChange = { form.password = it },
            label = { Text(text = stringResource(R.string.edit_password_label)) },
            singleLine = true,
            shape = RoundedCornerShape(FieldCorner),
            visualTransformation = if (form.revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OptionSwitch(R.string.edit_show_password, form.revealed) { form.revealed = it }
    }
}

/** The custom fields: add one, rename it, give it a value, protect it or drop it. */
@Composable
private fun CustomSection(
    form: EntryForm,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        form.custom.forEachIndexed { index, field ->
            EditTextField(
                label = stringResource(R.string.edit_custom_name),
                value = field.name,
                onValueChange = { field.name = it },
            )
            form.nameProblem(index)?.let { problem ->
                Text(
                    text =
                        stringResource(
                            if (problem == NameProblem.Standard) {
                                R.string.edit_name_standard
                            } else {
                                R.string.edit_name_repeated
                            },
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            EditTextField(
                label = stringResource(R.string.edit_custom_value),
                value = field.value,
                onValueChange = { field.value = it },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OptionSwitch(R.string.edit_custom_protected, field.isProtected) { field.isProtected = it }
                }
                IconButton(onClick = { form.custom.removeAt(index) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.edit_custom_remove),
                    )
                }
            }
        }
        OutlinedButton(
            onClick = { form.addCustom() },
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.edit_custom_add))
        }
    }
}

/** A switch with a visible label that is also its description. */
@Composable
private fun OptionSwitch(
    @StringRes label: Int,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    val description = stringResource(label)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

/** What the generator dialog is set to; it turns its switches into the options the generator understands. */
private class GeneratorChoices {
    var length by mutableStateOf(Options.DEFAULT_LENGTH)
    var lower by mutableStateOf(true)
    var upper by mutableStateOf(true)
    var digits by mutableStateOf(true)
    var symbols by mutableStateOf(true)
    var excludeAmbiguous by mutableStateOf(false)

    /** The generator needs at least one character class. */
    val anyClass: Boolean get() = lower || upper || digits || symbols

    fun toOptions(): Options =
        Options(
            length = length,
            lower = lower,
            upper = upper,
            digits = digits,
            symbols = symbols,
            excludeAmbiguous = excludeAmbiguous,
        )
}

/** The generator's options, and the button that puts a password in the field. */
@Composable
private fun GenerateDialog(
    onUse: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = remember { GeneratorChoices() }
    val generator = remember { PasswordGenerator() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.edit_generate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
                Text(
                    text = stringResource(R.string.edit_generate_length, choices.length),
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = choices.length.toFloat(),
                    onValueChange = {
                        choices.length =
                            it.roundToInt().coerceIn(MIN_GENERATED_LENGTH, MAX_GENERATED_LENGTH)
                    },
                    valueRange = MIN_GENERATED_LENGTH.toFloat()..MAX_GENERATED_LENGTH.toFloat(),
                    steps = LENGTH_STEPS,
                )
                OptionSwitch(R.string.edit_generate_lower, choices.lower) { choices.lower = it }
                OptionSwitch(R.string.edit_generate_upper, choices.upper) { choices.upper = it }
                OptionSwitch(R.string.edit_generate_digits, choices.digits) { choices.digits = it }
                OptionSwitch(R.string.edit_generate_symbols, choices.symbols) { choices.symbols = it }
                OptionSwitch(R.string.edit_generate_exclude_ambiguous, choices.excludeAmbiguous) {
                    choices.excludeAmbiguous = it
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUse(generator.generate(choices.toOptions())) },
                enabled = choices.anyClass,
            ) {
                Text(text = stringResource(R.string.edit_generate_use))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.edit_generate_cancel))
            }
        },
    )
}

/** Asks before losing what was typed. */
@Composable
private fun DiscardDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.edit_discard_title)) },
        text = { Text(text = stringResource(R.string.edit_discard_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.edit_discard_cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscard,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = stringResource(R.string.edit_discard_confirm))
            }
        },
    )
}

/** Saving, which is only possible with a title, and cancelling, which may ask first. */
@Composable
private fun EditActions(
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = ActionPaddingTop,
                    bottom = ActionPaddingBottom,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(ActionCorner),
            modifier = Modifier.weight(1f).height(ActionHeight),
        ) {
            Text(text = stringResource(R.string.edit_cancel))
        }
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            shape = RoundedCornerShape(ActionCorner),
            modifier = Modifier.weight(1f).height(ActionHeight),
        ) {
            Text(text = stringResource(R.string.edit_save))
        }
    }
}
