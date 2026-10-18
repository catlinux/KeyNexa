package io.github.catlinux.keynexa.windows

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The session and power watch: it starts, runs its own thread and closes cleanly. */
class WindowsSystemEventsTest {
    @Test
    fun startsAndClosesWithinAShortTime() {
        assumeTrue(onWindows())
        val events = WindowsSystemEvents()

        events.listen(onSessionLocked = {}, onSuspend = {})
        Thread.sleep(SHORT_WAIT_MILLIS)
        events.close()
        events.close()

        val named = Thread.getAllStackTraces().keys.filter { it.name == THREAD_NAME }
        assertTrue("the watch thread is still running: $named", named.isEmpty())
    }

    private fun onWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

    private companion object {
        const val SHORT_WAIT_MILLIS = 200L
        const val THREAD_NAME = "keynexa-system-events"
    }
}
