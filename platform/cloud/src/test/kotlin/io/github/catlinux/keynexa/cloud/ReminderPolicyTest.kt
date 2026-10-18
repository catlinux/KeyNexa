package io.github.catlinux.keynexa.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The reminder policy: shown until there is a configured service and a copy, and never while snoozed. */
class ReminderPolicyTest {
    @Test
    fun itIsShownWhenNothingIsConfigured() {
        assertTrue(ReminderPolicy.shouldShow(configured = false, hasBackup = false, snoozedUntil = null, now = NOW))
    }

    @Test
    fun itIsNotShownWhenConfiguredAndACopyExists() {
        assertFalse(ReminderPolicy.shouldShow(configured = true, hasBackup = true, snoozedUntil = null, now = NOW))
    }

    @Test
    fun itIsShownWhenConfiguredButNoCopyExists() {
        assertTrue(ReminderPolicy.shouldShow(configured = true, hasBackup = false, snoozedUntil = null, now = NOW))
    }

    @Test
    fun itIsNotShownWhileSnoozed() {
        val until = NOW.plusSeconds(60)
        assertFalse(ReminderPolicy.shouldShow(configured = false, hasBackup = false, snoozedUntil = until, now = NOW))
    }

    @Test
    fun itIsShownAgainOnceTheSnoozeHasPassed() {
        val until = NOW.minusSeconds(60)
        assertTrue(ReminderPolicy.shouldShow(configured = false, hasBackup = false, snoozedUntil = until, now = NOW))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-15T10:20:30Z")
    }
}
