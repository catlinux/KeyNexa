package io.github.catlinux.keynexa.desktop

import java.awt.Window

/**
 * Keeps the window out of screen captures, on the systems that can do it.
 *
 * It is a best effort: a system that refuses the request, an older one that only hides the window from capture APIs,
 * and a camera pointed at the screen are all beyond what this can do.
 */
interface CaptureGuard {
    /**
     * Asks the system to exclude [window] from captures, or to allow them again when [allowScreenshots]. Answers true
     * when the system accepted the request; a failed call is answered, never thrown.
     */
    fun apply(
        window: Window,
        allowScreenshots: Boolean,
    ): Boolean
}

/** [CaptureGuard] for the systems that cannot exclude a window: it always answers that the request was refused. */
object NoCaptureGuard : CaptureGuard {
    override fun apply(
        window: Window,
        allowScreenshots: Boolean,
    ): Boolean = false
}
