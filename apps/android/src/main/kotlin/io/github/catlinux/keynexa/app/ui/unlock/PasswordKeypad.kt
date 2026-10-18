package io.github.catlinux.keynexa.app.ui.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R

/** Spacing and shape of the keypad (docs/DESIGN.md). */
private val KeyGap = 4.dp
private val KeyHeight = 48.dp
private val KeyCorner = 8.dp
private val KeyShape = RoundedCornerShape(KeyCorner)

/** The relative width of each kind of key, so every row fills the width. */
private const val CHARACTER_WEIGHT = 1f
private const val WIDE_WEIGHT = 1.5f
private const val SPACE_WEIGHT = 4f

/** The space bar is a character key, but a wide one. */
private const val SPACE = ' '

/** The glyphs the special keys show, drawn as text so no icon or drawable is needed. */
private const val SHIFT_GLYPH = "⇧"
private const val BACKSPACE_GLYPH = "⌫"
private const val LETTERS_LABEL = "ABC"
private const val SYMBOLS_LABEL = "?123"
private const val ACCENTS_LABEL = "áé"

/**
 * The keypad drawn by the app for a master password: it reads [KeypadLayout] and applies presses to [buffer].
 *
 * It never reads the characters of [buffer], shows no preview of the pressed key, and gives only a short haptic as
 * feedback. [KeypadState] is not observable, so a local revision counter is bumped on every press to recompose.
 */
@Suppress("LongParameterList")
@Composable
fun PasswordKeypad(
    state: KeypadState,
    buffer: PasswordBuffer,
    enabled: Boolean,
    onChanged: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var revision by remember { mutableIntStateOf(0) }
    val rows = remember(state, revision) { KeypadLayout.rows(state.page, state.shift) }
    val onPress: (KeypadKey) -> Unit = { key ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val done = state.press(key, buffer)
        revision++
        onChanged()
        if (done) onDone()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KeyGap),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KeyGap),
            ) {
                row.forEach { key ->
                    KeypadKeyButton(
                        key = key,
                        shift = state.shift,
                        enabled = enabled,
                        onPress = onPress,
                        modifier = Modifier.weight(keyWeight(key)),
                    )
                }
            }
        }
    }
}

/** One key: a button of at least 48 dp, with the case and the description its kind asks for. */
@Composable
private fun KeypadKeyButton(
    key: KeypadKey,
    shift: ShiftState,
    enabled: Boolean,
    onPress: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = keypadKeyLabel(key)
    val description = keypadKeyDescription(key, shift)
    val activeShift = key == KeypadKey.Shift && shift != ShiftState.Off
    val container =
        when {
            activeShift -> MaterialTheme.colorScheme.primary
            key is KeypadKey.Character -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
    val content =
        when {
            activeShift -> MaterialTheme.colorScheme.onPrimary
            key is KeypadKey.Character -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSecondaryContainer
        }
    Box(
        modifier =
            modifier
                .heightIn(min = KeyHeight)
                .clip(KeyShape)
                .background(container)
                .clickable(enabled = enabled, role = Role.Button, onClick = { onPress(key) })
                .semantics(mergeDescendants = true) {
                    if (description != null) contentDescription = description
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** The glyph or word a key shows. */
@Composable
private fun keypadKeyLabel(key: KeypadKey): String =
    when (key) {
        is KeypadKey.Character -> if (key.char == SPACE) stringResource(R.string.keypad_space) else key.char.toString()
        KeypadKey.Shift -> SHIFT_GLYPH
        KeypadKey.Backspace -> BACKSPACE_GLYPH
        is KeypadKey.Page ->
            when (key.target) {
                KeypadPage.Letters -> LETTERS_LABEL
                KeypadPage.Symbols -> SYMBOLS_LABEL
                KeypadPage.Accents -> ACCENTS_LABEL
            }
        KeypadKey.Done -> stringResource(R.string.keypad_done)
    }

/** The spoken description of a key; a character key keeps its own character as its text. */
@Composable
private fun keypadKeyDescription(
    key: KeypadKey,
    shift: ShiftState,
): String? =
    when (key) {
        is KeypadKey.Character -> null
        KeypadKey.Shift ->
            stringResource(
                when (shift) {
                    ShiftState.Off -> R.string.keypad_shift
                    ShiftState.Once -> R.string.keypad_shift_once
                    ShiftState.Locked -> R.string.keypad_shift_locked
                },
            )
        KeypadKey.Backspace -> stringResource(R.string.keypad_backspace)
        is KeypadKey.Page ->
            stringResource(
                when (key.target) {
                    KeypadPage.Letters -> R.string.keypad_letters
                    KeypadPage.Symbols -> R.string.keypad_symbols
                    KeypadPage.Accents -> R.string.keypad_accents
                },
            )
        KeypadKey.Done -> stringResource(R.string.keypad_done)
    }

/** The relative width of a key: the space bar is wide and the special keys a little wider than a character. */
private fun keyWeight(key: KeypadKey): Float =
    when {
        key is KeypadKey.Character && key.char == SPACE -> SPACE_WEIGHT
        key is KeypadKey.Character -> CHARACTER_WEIGHT
        else -> WIDE_WEIGHT
    }
