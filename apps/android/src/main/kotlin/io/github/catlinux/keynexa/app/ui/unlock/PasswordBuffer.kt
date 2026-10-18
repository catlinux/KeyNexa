package io.github.catlinux.keynexa.app.ui.unlock

/**
 * The characters of a master password while they are typed, in a mutable [CharArray] that can be wiped.
 *
 * It never turns its content into a [String], [toString] hides it, and every path that drops characters overwrites
 * them with `Char(0)` first.
 */
class PasswordBuffer(
    initialCapacity: Int = DEFAULT_CAPACITY,
) {
    private var chars = CharArray(initialCapacity.coerceAtLeast(MIN_CAPACITY))

    /** How many characters are held. */
    var length: Int = 0
        private set

    /** True when nothing has been typed. */
    fun isEmpty(): Boolean = length == 0

    /** Adds [char] at the end, growing the buffer when it is full. */
    fun append(char: Char) {
        if (length == chars.size) grow()
        chars[length] = char
        length++
    }

    /** Removes the last character; does nothing when the buffer is empty. */
    fun deleteLast() {
        if (length == 0) return
        length--
        chars[length] = Char(0)
    }

    /** Wipes every character and leaves the buffer empty. */
    fun clear() {
        chars.fill(Char(0))
        length = 0
    }

    /** A new array with exactly [length] characters; the caller owns it and wipes it. */
    fun copyOut(): CharArray = chars.copyOf(length)

    /** A constant, so the content can never leak through a log or a crash report. */
    override fun toString(): String = "PasswordBuffer(***)"

    /** Doubles the capacity and wipes the array that is left behind. */
    private fun grow() {
        val grown = CharArray(chars.size * GROWTH_FACTOR)
        chars.copyInto(grown)
        chars.fill(Char(0))
        chars = grown
    }

    private companion object {
        const val DEFAULT_CAPACITY = 32
        const val MIN_CAPACITY = 1
        const val GROWTH_FACTOR = 2
    }
}
