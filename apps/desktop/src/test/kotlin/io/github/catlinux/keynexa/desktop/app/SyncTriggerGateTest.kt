package io.github.catlinux.keynexa.desktop.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "at most once a minute" rule of the focus synchronisation trigger. */
class SyncTriggerGateTest {
    private var now = 0L
    private val gate = SyncTriggerGate(MINUTE) { now }

    @Test
    fun theFirstAskIsAlwaysAllowed() {
        assertTrue(gate.due())
    }

    @Test
    fun anAskWithinTheIntervalIsRefused() {
        assertTrue(gate.due())

        now = MINUTE - 1
        assertFalse(gate.due())
    }

    @Test
    fun theAskIsAllowedAgainAfterTheInterval() {
        assertTrue(gate.due())

        now = MINUTE
        assertTrue(gate.due())
    }

    @Test
    fun aMarkedRoundHoldsTheAskBack() {
        gate.mark()

        now = MINUTE - 1
        assertFalse(gate.due())

        now = MINUTE
        assertTrue(gate.due())
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
