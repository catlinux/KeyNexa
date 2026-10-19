package io.github.catlinux.keynexa.app.cloud.ui.settings

import io.github.catlinux.keynexa.cloud.ProbeResult
import io.github.catlinux.keynexa.sync.CloudAuthException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** The probe result, as the outcome the cloud assistant shows next to the switch. */
class CloudSyncSectionTest {
    @Test
    fun aPassingProbeTurnsTheSwitchOn() {
        assertEquals(SyncProbeOutcome.Enabled, syncProbeOutcome(ProbeResult.Supported))
    }

    @Test
    fun aServiceWithoutConditionalWritesIsReportedAsSuch() {
        assertEquals(SyncProbeOutcome.NotSupported, syncProbeOutcome(ProbeResult.NotSupported(2)))
    }

    @Test
    fun rejectedKeysAreNotAuthorized() {
        assertEquals(
            SyncProbeOutcome.NotAuthorized,
            syncProbeOutcome(ProbeResult.Failed(CloudAuthException("rejected"))),
        )
    }

    @Test
    fun anyOtherFailureIsUnavailable() {
        assertEquals(SyncProbeOutcome.Unavailable, syncProbeOutcome(ProbeResult.Failed(IOException("down"))))
    }
}
