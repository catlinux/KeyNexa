package io.github.catlinux.keynexa.update

import java.time.Duration
import java.time.Instant

/**
 * When the next update check is due: at most one check a day (ADR-0015), with the first one always due.
 */
object CheckSchedule {
    private const val DEFAULT_INTERVAL_DAYS = 1L
    private val DEFAULT_INTERVAL: Duration = Duration.ofDays(DEFAULT_INTERVAL_DAYS)

    /**
     * True when a check should run now: there was no previous check ([last] is null), [interval] has passed
     * since it, or it lies in the future because the system clock moved back.
     */
    fun isDue(
        last: Instant?,
        now: Instant,
        interval: Duration = DEFAULT_INTERVAL,
    ): Boolean {
        if (last == null) return true
        if (last.isAfter(now)) return true
        return !now.isBefore(last.plus(interval))
    }
}
