package io.github.catlinux.keynexa.app.ui.edit

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.Options
import io.github.catlinux.keynexa.vault.PasswordGenerator
import kotlin.math.roundToInt

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ScreenPadding = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp

/** The length the generator dialog offers. */
private const val MIN_GENERATED_LENGTH = Options.MIN_LENGTH

private const val MAX_GENERATED_LENGTH = 64

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

/**
 * Creates or edits one entry.
 *
 * A stateless screen: it shows the fields it is given and reports what the user typed. The password and the
 * protected fields are hidden by default, are never written to saved state and are never logged. [initial] is null
 * while creating an entry.
 */
@Composable
fun EntryEditScreen(
    initial: Map<String, FieldValue>?,
    isNew: Boolean,
    onSave: (fields: Map<String, FieldValue>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = remember(initial) { EntryForm(initial) }
    var generating by remember { mutableStateOf(false) }
    var confirmingCancel by remember { mutableStateOf(false) }
    val requestCancel: () -> Unit = {
        if (form.unchanged) {
            onCancel()
        } else {
            confirmingCancel = true
        }
    }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            EditHeader(isNew = isNew, onBack = requestCancel)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    BasicSection(form = form, onGenerate = { generating = true })
                    CustomSection(form = form)
                }
            }
            EditActions(
                saveEnabled = !form.blocked,
                onSave = { onSave(form.toFields()) },
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
        modifier = modifier.fillMaxWidth(),
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The fields every entry has, and the button that fills the password with a generated one. */
@Composable
private fun BasicSection(
    form: EntryForm,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        EditTextField(
            label = stringResource(R.string.edit_title_label),
            value = form.title,
            onValueChange = { form.title = it },
        )
        if (form.title.isBlank()) {
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
        Text(
            text = stringResource(R.string.edit_custom_title),
            style = MaterialTheme.typography.titleMedium,
        )
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.edit_save))
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.edit_cancel))
        }
    }
}
