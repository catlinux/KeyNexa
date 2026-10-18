package io.github.catlinux.keynexa.desktop

/**
 * The system clipboard, as far as KeyNexa uses it.
 *
 * A copied secret is marked, where the system supports it, so it stays out of the clipboard history and out of cloud
 * sync, and [clearIfOurs] empties the clipboard only while it still holds what this object put there, so a later copy
 * by another program is never destroyed. A system that cannot take the text reports it as an [java.io.IOException],
 * whose message never carries the text.
 */
interface SecureClipboard {
    /**
     * Puts [text] on the clipboard; a [sensitive] one is marked to stay out of the clipboard history and out of
     * cloud sync where the system supports it. The caller keeps [text] and wipes it.
     */
    fun copy(
        text: CharArray,
        sensitive: Boolean,
    )

    /** Empties the clipboard when it still holds what this object put there, and does nothing otherwise. */
    fun clearIfOurs()
}
