package io.github.catlinux.keynexa.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.catlinux.keynexa.ui.R

/** The Android strings of the shared components, from this module's own resources. */
@Composable
actual fun sharedText(text: SharedText): String =
    stringResource(
        when (text) {
            SharedText.BrandMarkDescription -> R.string.brand_mark_description
            SharedText.KeypadSpace -> R.string.keypad_space
            SharedText.KeypadDone -> R.string.keypad_done
            SharedText.KeypadShift -> R.string.keypad_shift
            SharedText.KeypadShiftOnce -> R.string.keypad_shift_once
            SharedText.KeypadShiftLocked -> R.string.keypad_shift_locked
            SharedText.KeypadBackspace -> R.string.keypad_backspace
            SharedText.KeypadLetters -> R.string.keypad_letters
            SharedText.KeypadSymbols -> R.string.keypad_symbols
            SharedText.KeypadAccents -> R.string.keypad_accents
        },
    )
