package io.github.catlinux.keynexa.app.cloud

import io.github.catlinux.keynexa.sync.SyncError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The synchronisation state line: how a stored state becomes the note the cloud assistant shows. */
class SyncStatusTextTest {
    @Test
    fun theNoteIsOffWhenTheSwitchIsOff() {
        assertEquals(SyncCardNote.Disabled, syncCardNote(false, true, NOW, null, NOW))
    }

    @Test
    fun theNoteIsOnWithoutAVault() {
        assertEquals(SyncCardNote.Enabled, syncCardNote(true, false, NOW, null, NOW))
    }

    @Test
    fun theNoteIsPendingWhenNothingHasHappenedYet() {
        assertEquals(SyncCardNote.Pending, syncCardNote(true, true, null, null, NOW))
    }

    @Test
    fun theNoteShowsHowLongAgoTheLastSyncWas() {
        val lastSuccess = NOW.minusSeconds(2L * 60L)

        assertEquals(SyncCardNote.LastSync(AgoText.Minutes(2)), syncCardNote(true, true, lastSuccess, null, NOW))
    }

    @Test
    fun theNoteShowsTheStoredError() {
        assertEquals(
            SyncCardNote.Failed(SyncError.OtherPassword),
            syncCardNote(true, true, null, SyncError.OtherPassword, NOW),
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
