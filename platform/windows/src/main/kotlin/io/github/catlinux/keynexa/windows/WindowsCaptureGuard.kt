package io.github.catlinux.keynexa.windows

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import io.github.catlinux.keynexa.desktop.CaptureGuard
import java.awt.Window

/** SetWindowDisplayAffinity, which jna-platform does not expose. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private interface DisplayAffinity32 : StdCallLibrary {
    fun SetWindowDisplayAffinity(
        hwnd: WinDef.HWND,
        affinity: Int,
    ): Boolean

    companion object {
        val INSTANCE: DisplayAffinity32 = Native.load("user32", DisplayAffinity32::class.java)
    }
}

/**
 * [CaptureGuard] over SetWindowDisplayAffinity, so the window stays out of screen captures.
 *
 * WDA_EXCLUDEFROMCAPTURE needs Windows 10 version 2004 or newer; on older versions Windows treats it as WDA_MONITOR,
 * which hides the window from capture APIs but not from a camera pointed at the screen. A failed call is answered
 * with false and never thrown, and the window has to be displayable before it can be asked.
 */
class WindowsCaptureGuard : CaptureGuard {
    override fun apply(
        window: Window,
        allowScreenshots: Boolean,
    ): Boolean {
        val handle = Native.getComponentPointer(window) ?: return false
        val affinity = if (allowScreenshots) WDA_NONE else WDA_EXCLUDEFROMCAPTURE
        return affinity32.SetWindowDisplayAffinity(WinDef.HWND(handle), affinity)
    }

    private companion object {
        const val WDA_NONE = 0x0
        const val WDA_EXCLUDEFROMCAPTURE = 0x11

        val affinity32 = DisplayAffinity32.INSTANCE
    }
}
