package io.github.catlinux.keynexa.app.ui.unlock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.catlinux.keynexa.app.R

/** Whether master passwords are typed with the KeyNexa keypad (ADR-0010); provided from the settings. */
val LocalPasswordKeypad = compositionLocalOf { true }

/** One password field: the system keyboard's text, or the keypad's wipeable buffer. */
class PasswordInput {
    var text by mutableStateOf("")
    val buffer = PasswordBuffer()

    /** How many characters [buffer] holds: observable, unlike the buffer itself. */
    var bufferLength by mutableIntStateOf(0)

    fun clear() {
        buffer.clear()
        bufferLength = 0
        text = ""
    }
}

/**
 * The master password fields of one screen and how they are typed (ADR-0010): with the KeyNexa keypad, one keypad
 * serves every field and types into [active]; with the system keyboard each field is an ordinary password field.
 * Switching drops what was typed, and nothing typed outlives the screen (see [WipeOnLeave]).
 */
class PasswordEntry(
    useKeypad: Boolean,
    fields: Int = 1,
) {
    var useKeypad by mutableStateOf(useKeypad)
        private set
    val keypad = KeypadState()
    val inputs = List(fields) { PasswordInput() }
    var active by mutableIntStateOf(0)
    var revealed by mutableStateOf(false)

    fun length(field: Int): Int = if (useKeypad) inputs[field].bufferLength else inputs[field].text.length

    /** The password of [field] as a new array the caller owns, or null when empty. Every field is wiped. */
    fun take(field: Int = 0): CharArray? {
        val input = inputs[field]
        val typed = if (useKeypad) input.buffer.copyOut() else input.text.toCharArray()
        clear()
        if (typed.isNotEmpty()) return typed
        return null
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

/** Wipes [entry] when the screen is left and whenever the app goes to the background. */
@Composable
fun WipeOnLeave(entry: PasswordEntry) {
    DisposableEffect(entry) {
        onDispose { entry.clear() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { entry.clear() }
}

/**
 * One master password field of [entry]. With the keypad it is read-only (the system keyboard never opens), shows
 * dots, becomes the field the keypad types into when focused, and shows the characters only while [PasswordEntry.revealed]
 * is on (that transient text is accepted by ADR-0010). With the system keyboard it is a password field without
 * autocorrect.
 */
@Suppress("LongParameterList")
@Composable
fun MasterPasswordField(
    entry: PasswordEntry,
    label: String,
    enabled: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    field: Int = 0,
    onSubmit: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val input = entry.inputs[field]
    if (entry.useKeypad) {
        val length = input.bufferLength
        val shown = if (entry.revealed) revealedText(input.buffer) else MASK.repeat(length)
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            singleLine = true,
            enabled = enabled,
            shape = shape,
            trailingIcon = trailingIcon,
            modifier =
                modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) entry.active = field },
        )
    } else {
        OutlinedTextField(
            value = input.text,
            onValueChange = { input.text = it },
            label = { Text(text = label) },
            singleLine = true,
            enabled = enabled,
            shape = shape,
            visualTransformation = if (entry.revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next,
                ),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
            trailingIcon = trailingIcon,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/** The text button that switches [entry] between the KeyNexa keypad and the system keyboard. */
@Composable
fun PasswordInputSwitch(
    entry: PasswordEntry,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = { entry.switchInput() },
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        val text = if (entry.useKeypad) R.string.unlock_use_system_keyboard else R.string.unlock_use_keypad
        Text(text = stringResource(text))
    }
}

/** The KeyNexa keypad typing into the active field of [entry]; nothing is drawn while the system keyboard is used. */
@Composable
fun PasswordEntryKeypad(
    entry: PasswordEntry,
    enabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!entry.useKeypad) return
    PasswordKeypad(
        state = entry.keypad,
        buffer = entry.inputs[entry.active].buffer,
        enabled = enabled,
        onChanged = { entry.keypadChanged() },
        onDone = onDone,
        modifier = modifier,
    )
}

/** The typed characters as text, through a copy that is wiped at once. */
private fun revealedText(buffer: PasswordBuffer): String {
    val chars = buffer.copyOut()
    return try {
        String(chars)
    } finally {
        chars.fill(Char(0))
    }
}

private const val MASK = "•"
