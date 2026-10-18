package io.github.catlinux.keynexa.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** When the discreet cloud reminder is shown and how long "Later" hides it. */
class DesktopCloudReminderTest {
    @Test
    fun showsWhileNothingIsConfiguredOrNothingIsCopied() {
        assertTrue(reminderVisible(ReminderState(configured = false, hasBackup = false, snoozedUntil = null), NOW))
        assertTrue(reminderVisible(ReminderState(configured = true, hasBackup = false, snoozedUntil = null), NOW))
        assertTrue(reminderVisible(ReminderState(configured = false, hasBackup = true, snoozedUntil = null), NOW))
    }

    @Test
    fun hidesOnceThereIsAConfiguredCopyWithNoReasonToAsk() {
        assertFalse(reminderVisible(ReminderState(configured = true, hasBackup = true, snoozedUntil = null), NOW))
    }

    @Test
    fun hidesUntilTheSnoozeIsOver() {
        val state = ReminderState(configured = false, hasBackup = false, snoozedUntil = NOW.plusSeconds(60))

        assertFalse(reminderVisible(state, NOW))
        assertTrue(reminderVisible(state, NOW.plusSeconds(61)))
    }

    @Test
    fun laterHidesItForAWeek() {
        assertEquals(NOW.plus(Duration.ofDays(7)), snoozeUntil(NOW))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-18T20:00:00Z")
    }
}
