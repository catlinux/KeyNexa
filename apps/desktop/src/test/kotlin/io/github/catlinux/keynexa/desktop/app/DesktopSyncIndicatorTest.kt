package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.desktop.CloudSyncStatus
import io.github.catlinux.keynexa.sync.SyncError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** What the top bar's synchronisation line says for each state of the queue. */
class DesktopSyncIndicatorTest {
    private val now = Instant.parse("2026-10-19T12:00:00Z")

    private fun status(
        lastSuccess: Instant? = null,
        lastError: SyncError? = null,
        lastErrorAt: Instant? = null,
        running: Boolean = false,
    ) = CloudSyncStatus(lastSuccess, lastError, lastErrorAt, running)

    @Test
    fun saysNothingWithoutAState() {
        assertEquals(SyncIndicator.Hidden, syncIndicator(status(), now))
    }

    @Test
    fun saysItIsRunningWhileARoundRuns() {
        assertEquals(SyncIndicator.Running, syncIndicator(status(running = true), now))
    }

    @Test
    fun saysWhenItLastSucceeded() {
        val success = now.minusSeconds(120)
        assertEquals(SyncIndicator.Synced(AgoText.Minutes(2)), syncIndicator(status(lastSuccess = success), now))
    }

    @Test
    fun runningBeatsAStoredError() {
        val state =
            status(
                lastSuccess = now.minusSeconds(600),
                lastError = SyncError.Unavailable,
                lastErrorAt = now.minusSeconds(300),
                running = true,
            )
        assertEquals(SyncIndicator.Running, syncIndicator(state, now))
    }

    @Test
    fun saysAnErrorWithNoSuccess() {
        val state = status(lastError = SyncError.Unavailable, lastErrorAt = now.minusSeconds(60))
        assertEquals(SyncIndicator.Failed(SyncError.Unavailable), syncIndicator(state, now))
    }

    @Test
    fun anErrorNewerThanTheSuccessWins() {
        val state =
            status(
                lastSuccess = now.minusSeconds(600),
                lastError = SyncError.Unavailable,
                lastErrorAt = now.minusSeconds(300),
            )
        assertEquals(SyncIndicator.Failed(SyncError.Unavailable), syncIndicator(state, now))
    }

    @Test
    fun aSuccessNewerThanTheErrorWins() {
        val state =
            status(
                lastSuccess = now.minusSeconds(60),
                lastError = SyncError.Unavailable,
                lastErrorAt = now.minusSeconds(300),
            )
        assertEquals(SyncIndicator.Synced(AgoText.Minutes(1)), syncIndicator(state, now))
    }
}
