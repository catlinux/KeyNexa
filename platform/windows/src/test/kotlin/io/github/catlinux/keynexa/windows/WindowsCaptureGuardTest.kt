package io.github.catlinux.keynexa.windows

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment

/** The capture guard against a real window: both requests are accepted. */
class WindowsCaptureGuardTest {
    @Test
    fun excludesAndAllowsCaptureAgain() {
        assumeTrue(onWindows())
        assumeTrue(!GraphicsEnvironment.isHeadless())
        val frame = Frame()
        try {
            frame.isUndecorated = true
            frame.setSize(FRAME_SIZE, FRAME_SIZE)
            frame.isVisible = true
            val guard = WindowsCaptureGuard()

            assertTrue(guard.apply(frame, allowScreenshots = false))
            assertTrue(guard.apply(frame, allowScreenshots = true))
        } finally {
            frame.dispose()
        }
    }

    private fun onWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

    private companion object {
        const val FRAME_SIZE = 100
    }
}
