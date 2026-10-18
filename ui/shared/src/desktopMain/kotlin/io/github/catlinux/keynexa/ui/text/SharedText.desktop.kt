package io.github.catlinux.keynexa.ui.text

import androidx.compose.runtime.Composable
import java.util.Locale

private const val LANGUAGE_SPANISH = "es"

/** The same texts without Compose resources: Spanish on a Spanish system, English otherwise (provisional). */
@Composable
actual fun sharedText(text: SharedText): String =
    if (Locale.getDefault().language == LANGUAGE_SPANISH) spanish(text) else english(text)

private fun english(text: SharedText): String =
    when (text) {
        SharedText.BrandMarkDescription -> "KeyNexa logo"
        SharedText.KeypadSpace -> "space"
        SharedText.KeypadDone -> "Done"
        SharedText.KeypadShift -> "Shift"
        SharedText.KeypadShiftOnce -> "Shift on for one letter"
        SharedText.KeypadShiftLocked -> "Caps lock on"
        SharedText.KeypadBackspace -> "Delete last character"
        SharedText.KeypadLetters -> "Letters"
        SharedText.KeypadSymbols -> "Digits and symbols"
        SharedText.KeypadAccents -> "Accented letters"
    }

private fun spanish(text: SharedText): String =
    when (text) {
        SharedText.BrandMarkDescription -> "Marca de KeyNexa"
        SharedText.KeypadSpace -> "espacio"
        SharedText.KeypadDone -> "Listo"
        SharedText.KeypadShift -> "Mayúscula"
        SharedText.KeypadShiftOnce -> "Mayúscula para una letra"
        SharedText.KeypadShiftLocked -> "Bloqueo de mayúsculas activado"
        SharedText.KeypadBackspace -> "Borrar el último carácter"
        SharedText.KeypadLetters -> "Letras"
        SharedText.KeypadSymbols -> "Cifras y símbolos"
        SharedText.KeypadAccents -> "Letras con acento"
    }
