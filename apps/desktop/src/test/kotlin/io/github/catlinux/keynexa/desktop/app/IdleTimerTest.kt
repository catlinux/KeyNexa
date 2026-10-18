package io.github.catlinux.keynexa.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The idle watch and how a clipboard time becomes a timer delay. */
class IdleTimerTest {
    private var now = 0L
    private val timer = IdleTimer { now }

    @Test
    fun isDueOnceTheMinutesHavePassed() {
        assertFalse(timer.isDue(5))

        now = MINUTE * 4
        assertFalse(timer.isDue(5))

        now = MINUTE * 5
        assertTrue(timer.isDue(5))
    }

    @Test
    fun anyActivityStartsTheCountAgain() {
        now = MINUTE * 4
        timer.restart()

        now = MINUTE * 5
        assertFalse(timer.isDue(5))

        now = MINUTE * 9
        assertTrue(timer.isDue(5))
    }

    @Test
    fun zeroAndNegativeMinutesNeverLock() {
        now = MINUTE * 1_000

        assertFalse(timer.isDue(0))
        assertFalse(timer.isDue(-5))
    }

    @Test
    fun aClipboardTimeBecomesADelay() {
        assertEquals(30_000L, clipboardClearMillis(30))
        assertEquals(10_000L, clipboardClearMillis(10))
        assertEquals(Long.MAX_VALUE, clipboardClearMillis(0))
        assertEquals(Long.MAX_VALUE, clipboardClearMillis(-1))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
