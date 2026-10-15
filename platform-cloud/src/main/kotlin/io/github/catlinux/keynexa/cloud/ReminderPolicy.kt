package io.github.catlinux.keynexa.cloud

import java.time.Instant

/**
 * When the discreet "keep a cloud copy" reminder should be shown.
 *
 * Pure and deterministic so it can be tested on the JVM: no clock or storage of its own.
 */
object ReminderPolicy {
    /**
     * True when no service is configured yet or no copy exists yet, and the user has not snoozed it until [now]
     * or later.
     */
    fun shouldShow(
        configured: Boolean,
        hasBackup: Boolean,
        snoozedUntil: Instant?,
        now: Instant,
    ): Boolean {
        if (snoozedUntil != null && now.isBefore(snoozedUntil)) return false
        return !configured || !hasBackup
    }
}
