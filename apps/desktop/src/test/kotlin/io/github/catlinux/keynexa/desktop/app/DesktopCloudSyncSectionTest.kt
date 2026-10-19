package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.cloud.ProbeResult
import io.github.catlinux.keynexa.sync.CloudAuthException
import io.github.catlinux.keynexa.sync.SyncError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** The synchronisation switch and card: how a probe result and a stored state become what the user reads. */
class DesktopCloudSyncSectionTest {
    @Test
    fun aPassingProbeTurnsTheSwitchOn() {
        assertEquals(SyncSwitchOutcome.Enabled, syncSwitchOutcome(ProbeResult.Supported))
    }

    @Test
    fun aServiceWithoutConditionalWritesIsReportedAsSuch() {
        assertEquals(SyncSwitchOutcome.NotSupported, syncSwitchOutcome(ProbeResult.NotSupported(2)))
    }

    @Test
    fun rejectedKeysAreNotAuthorized() {
        assertEquals(
            SyncSwitchOutcome.NotAuthorized,
            syncSwitchOutcome(ProbeResult.Failed(CloudAuthException("rejected"))),
        )
    }

    @Test
    fun anyOtherFailureIsUnavailable() {
        assertEquals(SyncSwitchOutcome.Unavailable, syncSwitchOutcome(ProbeResult.Failed(IOException("down"))))
    }

    @Test
    fun theCardIsOffWhenTheSwitchIsOff() {
        assertEquals(SyncCardNote.Disabled, syncCardNote(false, true, NOW, null, NOW))
    }

    @Test
    fun theCardIsOnWithoutAVault() {
        assertEquals(SyncCardNote.Enabled, syncCardNote(true, false, NOW, null, NOW))
    }

    @Test
    fun theCardIsPendingWhenNothingHasHappenedYet() {
        assertEquals(SyncCardNote.Pending, syncCardNote(true, true, null, null, NOW))
    }

    @Test
    fun theCardShowsHowLongAgoTheLastSyncWas() {
        val lastSuccess = NOW.minusSeconds(2L * 60L)

        assertEquals(SyncCardNote.LastSync(AgoText.Minutes(2)), syncCardNote(true, true, lastSuccess, null, NOW))
    }

    @Test
    fun theCardShowsTheLastErrorEvenAfterASuccess() {
        assertEquals(
            SyncCardNote.Failed(SyncError.OtherPassword),
            syncCardNote(true, true, NOW.minusSeconds(30), SyncError.OtherPassword, NOW),
        )
    }

    @Test
    fun justNowCoversTheFirstMinuteAndAnyFutureMoment() {
        assertEquals(AgoText.JustNow, agoText(0))
        assertEquals(AgoText.JustNow, agoText(59_999))
        assertEquals(AgoText.JustNow, agoText(-1))
    }

    @Test
    fun minutesCoverUpToAnHour() {
        assertEquals(AgoText.Minutes(1), agoText(60_000))
        assertEquals(AgoText.Minutes(59), agoText(59L * 60_000L))
    }

    @Test
    fun hoursCoverUpToADay() {
        assertEquals(AgoText.Hours(1), agoText(3_600_000))
        assertEquals(AgoText.Hours(23), agoText(23L * 3_600_000L))
    }

    @Test
    fun daysCoverTheRest() {
        assertEquals(AgoText.Days(1), agoText(86_400_000))
        assertEquals(AgoText.Days(3), agoText(3L * 86_400_000L))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-19T10:00:00Z")
    }
}
