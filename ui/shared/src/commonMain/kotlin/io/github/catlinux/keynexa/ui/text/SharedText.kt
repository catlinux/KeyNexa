package io.github.catlinux.keynexa.ui.text

import androidx.compose.runtime.Composable

/** The texts the shared components show; each target resolves them in its own way. */
enum class SharedText {
    BrandMarkDescription,
    KeypadSpace,
    KeypadDone,
    KeypadShift,
    KeypadShiftOnce,
    KeypadShiftLocked,
    KeypadBackspace,
    KeypadLetters,
    KeypadSymbols,
    KeypadAccents,
}

/** The text of [text] in the language the platform is using. */
@Composable
expect fun sharedText(text: SharedText): String
