package io.github.catlinux.keynexa.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The clipboard format Windows uses for text: UTF-16 with a terminating NUL. */
private const val CF_UNICODETEXT = 13

/** The formats that keep a copied secret out of the clipboard history and out of the cloud clipboard. */
private val SENSITIVE_FORMATS =
    arrayOf(
        "ExcludeClipboardContentFromMonitorProcessing",
        "CanIncludeInClipboardHistory",
        "CanUploadToCloudClipboard",
    )

/** A stop for the read loop, so a block without a terminating NUL cannot hang a test. */
private const val MAX_CHARS = 4_096

/**
 * [WindowsClipboard] against the real clipboard of the machine: what it puts there, the marks that keep a secret out
 * of the clipboard history, and when it clears. The tests are short and leave the clipboard empty.
 */
class WindowsClipboardTest {
    @After
    fun leaveTheClipboardEmpty() {
        if (!onWindows()) return
        if (!raw.OpenClipboard(null)) return
        try {
            raw.EmptyClipboard()
        } finally {
            raw.CloseClipboard()
        }
    }

    @Test
    fun copiesTheTextWhereItCanBeReadBack() {
        assumeWindows()
        val clipboard = WindowsClipboard()

        clipboard.copy("a secret".toCharArray(), sensitive = true)

        assertEquals("a secret", readText())
    }

    @Test
    fun marksASecretAsSensitive() {
        assumeWindows()
        val clipboard = WindowsClipboard()

        clipboard.copy("a secret".toCharArray(), sensitive = true)

        SENSITIVE_FORMATS.forEach { name ->
            assertTrue("$name is missing", raw.IsClipboardFormatAvailable(raw.RegisterClipboardFormat(name)))
        }
    }

    @Test
    fun leavesAPlainCopyUnmarked() {
        assumeWindows()
        val clipboard = WindowsClipboard()

        clipboard.copy("a note".toCharArray(), sensitive = false)

        SENSITIVE_FORMATS.forEach { name ->
            assertFalse("$name should not be there", raw.IsClipboardFormatAvailable(raw.RegisterClipboardFormat(name)))
        }
    }

    @Test
    fun clearsWhatItPutThere() {
        assumeWindows()
        val clipboard = WindowsClipboard()
        clipboard.copy("a secret".toCharArray(), sensitive = true)

        clipboard.clearIfOurs()

        assertFalse(raw.IsClipboardFormatAvailable(CF_UNICODETEXT))
        assertNull(readText())
    }

    @Test
    fun leavesACopySomeoneElseMadeAlone() {
        assumeWindows()
        val ours = WindowsClipboard()
        ours.copy("a secret".toCharArray(), sensitive = true)
        val someoneElse = WindowsClipboard()
        someoneElse.copy("a note".toCharArray(), sensitive = false)

        ours.clearIfOurs()

        assertEquals("a note", readText())
    }

    /** What the clipboard holds as text, read straight from CF_UNICODETEXT; null when it holds none. */
    private fun readText(): String? {
        if (!raw.OpenClipboard(null)) return null
        try {
            val block = raw.GetClipboardData(CF_UNICODETEXT) ?: return null
            return readBlock(block)
        } finally {
            raw.CloseClipboard()
        }
    }

    /** The text inside a clipboard block, up to its terminating NUL. */
    private fun readBlock(block: Pointer): String? {
        val locked = memory.GlobalLock(block) ?: return null
        try {
            val text = StringBuilder()
            var offset = 0L
            while (text.length < MAX_CHARS && locked.getChar(offset) != Char(0)) {
                text.append(locked.getChar(offset))
                offset += Native.WCHAR_SIZE
            }
            return text.toString()
        } finally {
            memory.GlobalUnlock(block)
        }
    }

    private fun assumeWindows() {
        assumeTrue(onWindows())
    }

    private fun onWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

    private companion object {
        val raw: TestClipboard by lazy { TestClipboard.INSTANCE }
        val memory: TestMemory by lazy { TestMemory.INSTANCE }
    }
}

/** The few clipboard calls a test needs, declared here so it reads the real clipboard on its own. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private interface TestClipboard : StdCallLibrary {
    fun OpenClipboard(owner: Pointer?): Boolean

    fun CloseClipboard(): Boolean

    fun EmptyClipboard(): Boolean

    fun GetClipboardData(format: Int): Pointer?

    fun IsClipboardFormatAvailable(format: Int): Boolean

    fun RegisterClipboardFormat(name: String): Int

    companion object {
        val INSTANCE: TestClipboard = Native.load("user32", TestClipboard::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/** The kernel32 memory calls a test needs. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private interface TestMemory : StdCallLibrary {
    fun GlobalLock(handle: Pointer): Pointer?

    fun GlobalUnlock(handle: Pointer): Boolean

    companion object {
        val INSTANCE: TestMemory = Native.load("kernel32", TestMemory::class.java)
    }
}
