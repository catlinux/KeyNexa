package io.github.catlinux.keynexa.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.win32.StdCallLibrary
import io.github.catlinux.keynexa.desktop.SecureClipboard
import java.io.IOException

/** The clipboard format Windows uses for text: UTF-16 with a terminating NUL. */
private const val CF_UNICODETEXT = 13

/** A block the clipboard may move around in memory. */
private const val GMEM_MOVEABLE = 0x0002

/** How many times, and how far apart, opening the clipboard is retried while another program holds it. */
private const val OPEN_ATTEMPTS = 5
private const val OPEN_RETRY_MILLIS = 20L

/** How many bytes the zero DWORD of a marker format takes. */
private const val ZERO_DWORD_BYTES = 4L

/**
 * The formats that tell Windows to keep a copied secret out of the clipboard history and out of the cloud clipboard
 * (see the "Cloud Clipboard and Clipboard History Formats" section of the Win32 clipboard documentation).
 */
private val SENSITIVE_FORMATS =
    arrayOf(
        "ExcludeClipboardContentFromMonitorProcessing",
        "CanIncludeInClipboardHistory",
        "CanUploadToCloudClipboard",
    )

/** The user32 clipboard calls that jna-platform does not expose. The names are the ones user32 exports. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private interface Clipboard32 : StdCallLibrary {
    fun OpenClipboard(owner: Pointer?): Boolean

    fun CloseClipboard(): Boolean

    fun EmptyClipboard(): Boolean

    fun SetClipboardData(
        format: Int,
        data: Pointer,
    ): Pointer?

    fun GetClipboardSequenceNumber(): Int

    companion object {
        val INSTANCE: Clipboard32 = Native.load("user32", Clipboard32::class.java)
    }
}

/** The kernel32 memory calls that jna-platform does not expose. The names are the ones kernel32 exports. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private interface Memory32 : StdCallLibrary {
    fun GlobalAlloc(
        flags: Int,
        bytes: BaseTSD.SIZE_T,
    ): Pointer?

    fun GlobalLock(handle: Pointer): Pointer?

    fun GlobalUnlock(handle: Pointer): Boolean

    companion object {
        val INSTANCE: Memory32 = Native.load("kernel32", Memory32::class.java)
    }
}

/**
 * [SecureClipboard] over the Windows clipboard.
 *
 * The text goes in as CF_UNICODETEXT and, when it is sensitive, three more formats tell Windows to keep it out of the
 * clipboard history and out of the cloud clipboard. [clearIfOurs] empties the clipboard only while its sequence
 * number is still the one this object saw after copying, so a copy made by anyone else is left alone. Only the
 * clipboard's own content passes through here, and nothing is logged.
 */
class WindowsClipboard : SecureClipboard {
    private val clipboard = Clipboard32.INSTANCE
    private val memory = Memory32.INSTANCE

    @Volatile
    private var sequence: Int? = null

    override fun copy(
        text: CharArray,
        sensitive: Boolean,
    ) {
        openOrFail()
        try {
            if (!clipboard.EmptyClipboard()) throw IOException("could not empty the clipboard")
            // The marks go first: if anything fails after them, no secret is left on the clipboard without them.
            if (sensitive) putSensitiveMarks()
            putText(text)
        } finally {
            clipboard.CloseClipboard()
        }
        sequence = clipboard.GetClipboardSequenceNumber()
    }

    override fun clearIfOurs() {
        val ours = sequence ?: return
        sequence = null
        if (clipboard.GetClipboardSequenceNumber() != ours) return
        openOrFail()
        try {
            if (!clipboard.EmptyClipboard()) throw IOException("could not empty the clipboard")
        } finally {
            clipboard.CloseClipboard()
        }
    }

    /** Puts [text] in a moveable block as CF_UNICODETEXT; the system owns that block once this succeeds. */
    private fun putText(text: CharArray) {
        val block = allocate((text.size + 1).toLong() * Native.WCHAR_SIZE)
        val locked = lock(block)
        try {
            locked.write(0, text, 0, text.size)
            locked.setChar(text.size.toLong() * Native.WCHAR_SIZE, Char(0))
        } finally {
            memory.GlobalUnlock(block)
        }
        handOver(CF_UNICODETEXT, block)
    }

    /** Puts the three formats that keep the secret out of the clipboard history and out of the cloud clipboard. */
    private fun putSensitiveMarks() {
        SENSITIVE_FORMATS.forEach { name ->
            val format = User32.INSTANCE.RegisterClipboardFormat(name)
            if (format == 0) throw IOException("could not register a clipboard format")
            val block = allocate(ZERO_DWORD_BYTES)
            lock(block).setInt(0, 0)
            memory.GlobalUnlock(block)
            handOver(format, block)
        }
    }

    /** Gives a block to the clipboard: on success the system owns it, and otherwise it is freed here. */
    private fun handOver(
        format: Int,
        block: Pointer,
    ) {
        if (clipboard.SetClipboardData(format, block) != null) return
        Kernel32.INSTANCE.GlobalFree(block)
        throw IOException("could not put data on the clipboard")
    }

    /** Allocates a moveable block of [bytes], which the clipboard can take over. */
    private fun allocate(bytes: Long): Pointer =
        memory.GlobalAlloc(GMEM_MOVEABLE, BaseTSD.SIZE_T(bytes))
            ?: throw IOException("could not allocate a clipboard block")

    /** Locks [block] so it can be written; when that fails the block is freed and the copy gives up. */
    private fun lock(block: Pointer): Pointer =
        memory.GlobalLock(block)
            ?: run {
                Kernel32.INSTANCE.GlobalFree(block)
                throw IOException("could not lock a clipboard block")
            }

    /** Opens the clipboard, retrying briefly while another program holds it. */
    private fun openOrFail() {
        repeat(OPEN_ATTEMPTS) { attempt ->
            if (clipboard.OpenClipboard(null)) return
            if (attempt < OPEN_ATTEMPTS - 1) sleepBriefly()
        }
        throw IOException("could not open the clipboard")
    }

    private fun sleepBriefly() {
        try {
            Thread.sleep(OPEN_RETRY_MILLIS)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted while waiting for the clipboard")
        }
    }
}
