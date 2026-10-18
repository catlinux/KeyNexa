package io.github.catlinux.keynexa.vault

import java.security.SecureRandom
import java.util.Random

/** What kind of password to build: how long, which character classes, and whether look-alike characters are out. */
data class Options(
    val length: Int = DEFAULT_LENGTH,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
) {
    init {
        require(length in MIN_LENGTH..MAX_LENGTH) {
            "A password must be between $MIN_LENGTH and $MAX_LENGTH characters long."
        }
        require(lower || upper || digits || symbols) { "At least one character class must be enabled." }
    }

    companion object {
        /** Shorter passwords are not worth generating. */
        const val MIN_LENGTH = 8

        /** Long enough for anything, short enough to be typed or pasted everywhere. */
        const val MAX_LENGTH = 128

        const val DEFAULT_LENGTH = 20
    }
}

/**
 * Builds random passwords with the app's rules: one character of every class the caller enables, picked without
 * modulo bias and then shuffled, with look-alike characters left out on request.
 *
 * The source of randomness is injected. The default is [SecureRandom]; a seeded [Random] makes tests reproducible,
 * which is why the parameter is a plain [Random] rather than the secure implementation.
 *
 * The generated array belongs to the caller, which should overwrite it as soon as it is not needed any more.
 */
class PasswordGenerator(
    private val random: Random = SecureRandom(),
) {
    /** A password of `options.length` characters. */
    fun generate(options: Options): CharArray {
        val pools = poolsOf(options)
        val password = CharArray(options.length)
        var written = 0
        for (pool in pools) password[written++] = pool[random.nextInt(pool.length)]
        val alphabet = pools.joinToString("")
        while (written < password.size) password[written++] = alphabet[random.nextInt(alphabet.length)]
        shuffle(password)
        return password
    }

    // Fisher-Yates, from the end, so no position is favoured.
    private fun shuffle(password: CharArray) {
        for (index in password.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val swapped = password[index]
            password[index] = password[other]
            password[other] = swapped
        }
    }

    /** The characters of every enabled class, in the order the classes are declared. */
    private fun poolsOf(options: Options): List<String> =
        listOfNotNull(
            if (options.lower) pool(LOWER_CASE, options) else null,
            if (options.upper) pool(UPPER_CASE, options) else null,
            if (options.digits) pool(DIGITS, options) else null,
            if (options.symbols) pool(SYMBOLS, options) else null,
        )

    /** One class, without its look-alike characters when the caller asked to leave them out. */
    private fun pool(
        characters: String,
        options: Options,
    ): String = if (options.excludeAmbiguous) characters.filterNot { it in LOOK_ALIKES } else characters

    private companion object {
        const val LOWER_CASE = "abcdefghijklmnopqrstuvwxyz"
        const val UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/|~"

        /** Zero and one, the letters that look like them, and the vertical bar. */
        const val LOOK_ALIKES = "0Oo1lI|"
    }
}
