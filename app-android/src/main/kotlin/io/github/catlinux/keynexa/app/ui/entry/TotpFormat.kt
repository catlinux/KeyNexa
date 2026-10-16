package io.github.catlinux.keynexa.app.ui.entry

private const val DIGITS_SIX = 6
private const val DIGITS_SEVEN = 7
private const val DIGITS_EIGHT = 8
private const val SMALL_GROUP = 3
private const val LARGE_GROUP = 4

/**
 * Groups a one-time code so it is easier to read: 6 digits as `123 456`, 7 as `123 4567` and 8 as `1234 5678`.
 * Any other length is returned unchanged, and an empty string does not break.
 */
fun formatTotpCode(code: String): String =
    when (code.length) {
        DIGITS_SIX, DIGITS_SEVEN -> "${code.substring(0, SMALL_GROUP)} ${code.substring(SMALL_GROUP)}"
        DIGITS_EIGHT -> "${code.substring(0, LARGE_GROUP)} ${code.substring(LARGE_GROUP)}"
        else -> code
    }
