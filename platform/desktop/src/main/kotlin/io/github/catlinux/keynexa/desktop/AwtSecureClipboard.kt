package io.github.catlinux.keynexa.desktop

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

/** What the clipboard holds once KeyNexa clears it: nothing that can be read back. */
private const val NOTHING = ""

/**
 * [SecureClipboard] over the AWT system clipboard, for the desktop systems that have no clipboard of their own.
 *
 * It remembers what it put there, as a copy it wipes when it is replaced or dropped, and [clearIfOurs] compares that
 * with what the clipboard holds before emptying it. AWT offers no way to keep a copy out of the clipboard history, so
 * a sensitive text is placed like any other one; a system that does support it brings its own [SecureClipboard].
 * Meant to be used from the client's own thread.
 */
class AwtSecureClipboard : SecureClipboard {
    private val clipboard = Toolkit.getDefaultToolkit().systemClipboard

    @Volatile
    private var ours: CharArray? = null

    override fun copy(
        text: CharArray,
        sensitive: Boolean,
    ) {
        forget()
        ours = text.copyOf()
        clipboard.setContents(StringSelection(String(text)), null)
    }

    override fun clearIfOurs() {
        val remembered = ours ?: return
        val stillOurs = sameText(readText(), remembered)
        forget()
        if (stillOurs) clipboard.setContents(StringSelection(NOTHING), null)
    }

    /** Drops what this object remembers, wiping the copy it kept. */
    private fun forget() {
        ours?.fill(Char(0))
        ours = null
    }

    /** True when [current] holds the same characters as [remembered], compared through a copy that is wiped. */
    private fun sameText(
        current: String?,
        remembered: CharArray,
    ): Boolean {
        if (current == null || current.length != remembered.size) return false
        val chars = current.toCharArray()
        return try {
            chars.contentEquals(remembered)
        } finally {
            chars.fill(Char(0))
        }
    }

    /** What the clipboard holds as text, or null when it holds something this object cannot read. */
    @Suppress("SwallowedException")
    private fun readText(): String? =
        try {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } catch (failure: IllegalStateException) {
            null
        } catch (failure: UnsupportedFlavorException) {
            null
        } catch (failure: IOException) {
            null
        }
}
