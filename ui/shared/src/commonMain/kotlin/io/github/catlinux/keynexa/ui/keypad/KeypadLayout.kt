package io.github.catlinux.keynexa.ui.keypad

/** The pages of the keypad. */
enum class KeypadPage { Letters, Symbols, Accents }

/** How the shift key is set. */
enum class ShiftState { Off, Once, Locked }

/** One key of the keypad. */
sealed interface KeypadKey {
    /** A character, already in the case it is shown in. */
    data class Character(
        val char: Char,
    ) : KeypadKey

    /** Cycles the shift state. */
    data object Shift : KeypadKey

    /** Removes the last character. */
    data object Backspace : KeypadKey

    /** Switches to [target] and keeps the shift state. */
    data class Page(
        val target: KeypadPage,
    ) : KeypadKey

    /** Finishes typing. */
    data object Done : KeypadKey
}

/** The fixed layout of the keypad: a letters page, a symbols page and an accents page. */
object KeypadLayout {
    /** The rows of [page], with the characters in the case [shift] asks for. */
    fun rows(
        page: KeypadPage,
        shift: ShiftState,
    ): List<List<KeypadKey>> =
        when (page) {
            KeypadPage.Letters -> lettersRows(shift)
            KeypadPage.Symbols -> symbolsRows()
            KeypadPage.Accents -> accentsRows(shift)
        }

    private fun lettersRows(shift: ShiftState): List<List<KeypadKey>> {
        val upper = shift != ShiftState.Off
        return listOf(
            characters("qwertyuiop", upper),
            characters("asdfghjklñ", upper),
            listOf(KeypadKey.Shift) + characters("zxcvbnm", upper) + KeypadKey.Backspace,
            bottomRow(KeypadPage.Symbols, KeypadPage.Accents),
        )
    }

    private fun symbolsRows(): List<List<KeypadKey>> {
        val rows = characters(DIGITS + SYMBOLS, upper = false).chunked(MAX_ROW).toMutableList()
        appendToLastRow(rows, listOf(KeypadKey.Backspace))
        rows += bottomRow(KeypadPage.Letters, KeypadPage.Accents)
        return rows
    }

    private fun accentsRows(shift: ShiftState): List<List<KeypadKey>> {
        val rows = characters(ACCENTS, shift != ShiftState.Off).chunked(MAX_ROW).toMutableList()
        appendToLastRow(rows, listOf(KeypadKey.Shift, KeypadKey.Backspace))
        rows += bottomRow(KeypadPage.Letters, KeypadPage.Symbols)
        return rows
    }

    private fun characters(
        text: String,
        upper: Boolean,
    ): List<KeypadKey> = text.map { KeypadKey.Character(if (upper) it.uppercaseChar() else it) }

    private fun bottomRow(
        first: KeypadPage,
        second: KeypadPage,
    ): List<KeypadKey> =
        listOf(
            KeypadKey.Page(first),
            KeypadKey.Page(second),
            KeypadKey.Character(' '),
            KeypadKey.Done,
        )

    /** Puts [keys] at the end of the last row when they fit there, or in a row of their own. */
    private fun appendToLastRow(
        rows: MutableList<List<KeypadKey>>,
        keys: List<KeypadKey>,
    ) {
        val last = rows.last()
        if (last.size + keys.size <= MAX_ROW) {
            rows[rows.lastIndex] = last + keys
        } else {
            rows += keys
        }
    }

    private const val MAX_ROW = 10
    private const val DIGITS = "1234567890"
    private const val SYMBOLS = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    private const val ACCENTS = "áéíóúàèìòùäëïöüâêîôûñç"
}

/** The state of the keypad: the page it shows and how shift is set. */
class KeypadState {
    /** The page being shown. */
    var page: KeypadPage = KeypadPage.Letters

    /** How the next character is cased. */
    var shift: ShiftState = ShiftState.Off

    /** Applies [key] to [buffer]; returns true only when the key is [KeypadKey.Done]. */
    fun press(
        key: KeypadKey,
        buffer: PasswordBuffer,
    ): Boolean =
        when (key) {
            is KeypadKey.Character -> {
                buffer.append(key.char)
                if (shift == ShiftState.Once) shift = ShiftState.Off
                false
            }
            KeypadKey.Shift -> {
                shift = nextShift(shift)
                false
            }
            KeypadKey.Backspace -> {
                buffer.deleteLast()
                false
            }
            is KeypadKey.Page -> {
                page = key.target
                false
            }
            KeypadKey.Done -> true
        }

    private fun nextShift(current: ShiftState): ShiftState =
        when (current) {
            ShiftState.Off -> ShiftState.Once
            ShiftState.Once -> ShiftState.Locked
            ShiftState.Locked -> ShiftState.Off
        }
}
