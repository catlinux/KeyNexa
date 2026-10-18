@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_changed_during_save
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_credentials_changed
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_encode_failed
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_external_change
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_file_unreadable
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_storage_error
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_unmergeable
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_write_not_verified
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_damaged
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_invalid_key_file
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_not_a_vault
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_not_enough_memory
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_unreachable
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_unsupported_format
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_error_wrong_credentials
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_choose
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_chosen
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_failed
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_remove
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_use_keypad
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_use_system_keyboard
import io.github.catlinux.keynexa.session.UnlockError
import io.github.catlinux.keynexa.sync.SaveFailure
import io.github.catlinux.keynexa.ui.keypad.KeypadState
import io.github.catlinux.keynexa.ui.keypad.PasswordBuffer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** The mark that stands for every hidden character. */
internal const val MASK = "•"

/** The height a control that only holds text keeps, so it stays easy to hit. */
private val TouchTarget = 48.dp

/** The gap between the lines of the key file picker. */
private val KeyFileGap = 8.dp

/** The plain-language message of each way an unlock can fail. */
internal fun unlockErrorMessage(error: UnlockError): StringResource =
    when (error) {
        UnlockError.WrongCredentials -> Res.string.unlock_error_wrong_credentials
        UnlockError.InvalidKeyFile -> Res.string.unlock_error_invalid_key_file
        UnlockError.NotAVault -> Res.string.unlock_error_not_a_vault
        UnlockError.UnsupportedFormat -> Res.string.unlock_error_unsupported_format
        UnlockError.Damaged -> Res.string.unlock_error_damaged
        UnlockError.Unreachable -> Res.string.unlock_error_unreachable
        UnlockError.NotEnoughMemory -> Res.string.unlock_error_not_enough_memory
    }

/** The plain-language reason of each way a save can fail. */
internal fun saveProblemMessage(problem: SaveFailure): StringResource =
    when (problem) {
        SaveFailure.ExternalChangeNeedsMerge -> Res.string.save_problem_external_change
        SaveFailure.Unmergeable -> Res.string.save_problem_unmergeable
        SaveFailure.ChangedDuringSave -> Res.string.save_problem_changed_during_save
        SaveFailure.FileUnreadable -> Res.string.save_problem_file_unreadable
        SaveFailure.CredentialsChanged -> Res.string.save_problem_credentials_changed
        SaveFailure.EncodeFailed -> Res.string.save_problem_encode_failed
        SaveFailure.StorageError -> Res.string.save_problem_storage_error
        SaveFailure.WriteNotVerified -> Res.string.save_problem_write_not_verified
    }

/** Byte a wiped key file is filled with. */
private const val KEY_FILE_FILL: Byte = 0

/** The key file the user picked: its name and its bytes. [clear] wipes the bytes and leaves the holder empty. */
internal class KeyFileHolder {
    var name by mutableStateOf<String?>(null)
        private set
    var bytes by mutableStateOf<ByteArray?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    /** True when a key file is held. */
    val chosen: Boolean get() = bytes != null

    fun pick(
        name: String,
        bytes: ByteArray,
    ) {
        clear()
        this.name = name
        this.bytes = bytes
    }

    fun markFailed() {
        failed = true
    }

    /** The bytes as a new array the caller owns, or null; the holder keeps its own copy to wipe later. */
    fun copyOut(): ByteArray? = bytes?.copyOf()

    fun clear() {
        bytes?.fill(KEY_FILE_FILL)
        bytes = null
        name = null
        failed = false
    }
}

/**
 * The master passwords while they are typed: the system keyboard's text or the keypad's wipeable buffer, one field
 * per password the form asks for. The keypad, whether it is used and whether the characters are shown are decided
 * once for every field; [active] says which field the keypad types into.
 */
internal class DesktopPassword(
    useKeypad: Boolean = false,
    fields: Int = 1,
) {
    var useKeypad by mutableStateOf(useKeypad)
        private set
    var revealed by mutableStateOf(false)
    val keypad = KeypadState()

    /** The field the keypad types into. */
    var active by mutableIntStateOf(0)

    private val inputs = List(fields) { PasswordInput() }

    /** How many characters [field] holds. */
    fun length(field: Int = 0): Int = if (useKeypad) inputs[field].bufferLength else inputs[field].text.length

    /** What [field] shows while the system keyboard is used. */
    fun text(field: Int = 0): String = inputs[field].text

    /** Sets what [field] shows; only the system keyboard writes here. */
    fun setText(
        field: Int,
        value: String,
    ) {
        inputs[field].text = value
    }

    /** The wipeable buffer of [field], which the keypad types into. */
    fun buffer(field: Int = 0): PasswordBuffer = inputs[field].buffer

    /** How many characters the buffer of [field] holds: observable, unlike the buffer itself. */
    fun bufferLength(field: Int = 0): Int = inputs[field].bufferLength

    /** The password of [field] as a new array the caller owns, or null when nothing was typed; every field is wiped. */
    fun take(field: Int = 0): CharArray? {
        val typed = if (useKeypad) inputs[field].buffer.copyOut() else inputs[field].text.toCharArray()
        clear()
        return typed.takeIf { it.isNotEmpty() }
    }

    /** True when [first] and [second] hold the same characters, compared through copies that are wiped at once. */
    fun matches(
        first: Int,
        second: Int,
    ): Boolean {
        if (!useKeypad) return inputs[first].text == inputs[second].text
        if (inputs[first].bufferLength != inputs[second].bufferLength) return false
        val a = inputs[first].buffer.copyOut()
        val b = inputs[second].buffer.copyOut()
        return try {
            a.contentEquals(b)
        } finally {
            a.fill(Char(0))
            b.fill(Char(0))
        }
    }

    fun clear() {
        inputs.forEach { it.clear() }
        revealed = false
    }

    fun switchInput() {
        clear()
        useKeypad = !useKeypad
    }

    /** Records a keypad press in the active field, so the screen recomposes. */
    fun keypadChanged() {
        val input = inputs[active]
        input.bufferLength = input.buffer.length
    }
}

/** One password field: the system keyboard's text, or the keypad's wipeable buffer. */
private class PasswordInput {
    var text by mutableStateOf("")
    val buffer = PasswordBuffer()
    var bufferLength by mutableIntStateOf(0)

    fun clear() {
        buffer.clear()
        bufferLength = 0
        text = ""
    }
}

/**
 * One master password field of [password]. With the keypad it is read-only, shows dots, becomes the field the keypad
 * types into when it takes focus, and shows the characters only while [DesktopPassword.revealed] is on. With the
 * system keyboard it is an ordinary password field without autocorrect, and [onSubmit] makes Enter run it.
 */
@Suppress("LongParameterList")
@Composable
internal fun DesktopPasswordField(
    password: DesktopPassword,
    label: String,
    enabled: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    field: Int = 0,
    onSubmit: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onChanged: () -> Unit = {},
) {
    if (password.useKeypad) {
        OutlinedTextField(
            value = maskedText(password, field),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            singleLine = true,
            enabled = enabled,
            shape = shape,
            trailingIcon = trailingIcon,
            modifier = modifier.fillMaxWidth().focusesField(password, field),
        )
    } else {
        OutlinedTextField(
            value = password.text(field),
            onValueChange = {
                password.setText(field, it)
                onChanged()
            },
            label = { Text(text = label) },
            singleLine = true,
            enabled = enabled,
            shape = shape,
            visualTransformation = if (password.revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next,
                ),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
            trailingIcon = trailingIcon,
            modifier =
                modifier
                    .fillMaxWidth()
                    .focusesField(password, field)
                    .enterSubmits(onSubmit),
        )
    }
}

/** Makes [field] of [password] the one the keypad types into while it holds the focus. */
private fun Modifier.focusesField(
    password: DesktopPassword,
    field: Int,
): Modifier = onFocusChanged { if (it.isFocused) password.active = field }

/** What a field shows: the typed characters when they are shown, one dot for each of them otherwise. */
private fun maskedText(
    password: DesktopPassword,
    field: Int,
): String =
    if (password.revealed) {
        revealedText(password.buffer(field))
    } else {
        MASK.repeat(password.bufferLength(field))
    }

/** Adds Enter as a way to run [onSubmit], or nothing at all when there is no submit action. */
private fun Modifier.enterSubmits(onSubmit: (() -> Unit)?): Modifier =
    if (onSubmit == null) this else onPreviewKeyEvent { event -> submitOnEnter(event, onSubmit) }

/** Runs [onSubmit] and eats the event when it is the Enter key, so the password field never keeps a line break. */
private fun submitOnEnter(
    event: KeyEvent,
    onSubmit: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) return false
    onSubmit()
    return true
}

/** The text button that switches [password] between the KeyNexa keypad and the system keyboard. */
@Composable
internal fun PasswordInputSwitch(
    password: DesktopPassword,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = { password.switchInput() },
        enabled = enabled,
        modifier = modifier.heightIn(min = TouchTarget),
    ) {
        val text = if (password.useKeypad) Res.string.unlock_use_system_keyboard else Res.string.unlock_use_keypad
        Text(text = stringResource(text))
    }
}

/**
 * The optional key file: choose one, see its name, or drop it again. The picker reads the file only when it is
 * chosen; the bytes are never logged, and a file that cannot be read is reported instead of being ignored.
 */
@Suppress("LongParameterList")
@Composable
internal fun DesktopKeyFilePicker(
    parent: Frame,
    holder: KeyFileHolder,
    enabled: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    help: String? = null,
    onEdited: () -> Unit = {},
) {
    val chooseTitle = stringResource(Res.string.unlock_key_file_choose)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KeyFileGap),
    ) {
        Text(
            text = stringResource(Res.string.unlock_key_file_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (help != null) {
            Text(
                text = help,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (holder.name == null) {
            OutlinedButton(
                onClick = {
                    chooseKeyFile(parent, chooseTitle)?.let { file ->
                        val bytes = readKeyFile(file)
                        if (bytes == null) holder.markFailed() else holder.pick(file.name, bytes)
                        onEdited()
                    }
                },
                enabled = enabled,
                shape = shape,
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) {
                Text(text = chooseTitle)
            }
            if (holder.failed) {
                Text(
                    text = stringResource(Res.string.unlock_key_file_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            ChosenKeyFile(holder = holder, enabled = enabled, onEdited = onEdited)
        }
    }
}

/** The key file that is held: its name, and the way to drop it again. */
@Composable
private fun ChosenKeyFile(
    holder: KeyFileHolder,
    enabled: Boolean,
    onEdited: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.unlock_key_file_chosen, holder.name.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                holder.clear()
                onEdited()
            },
            enabled = enabled,
        ) {
            Text(text = stringResource(Res.string.unlock_key_file_remove))
        }
    }
}

/** Asks for a key file with the native dialog; null when the user cancels. */
internal fun chooseKeyFile(
    parent: Frame,
    title: String,
): File? {
    val dialog = FileDialog(parent, title, FileDialog.LOAD)
    dialog.isVisible = true
    val directory = dialog.directory
    val name = dialog.file
    return if (directory != null && name != null) File(directory, name) else null
}

/** Reads the whole key file the user picked; a file that cannot be read leaves the picker as it was. */
internal fun readKeyFile(file: File): ByteArray? =
    try {
        Files.readAllBytes(file.toPath())
    } catch (failure: IOException) {
        null
    }

/** The typed characters as text, through a copy that is wiped at once. */
internal fun revealedText(buffer: PasswordBuffer): String {
    val chars = buffer.copyOut()
    return try {
        String(chars)
    } finally {
        chars.fill(Char(0))
    }
}
