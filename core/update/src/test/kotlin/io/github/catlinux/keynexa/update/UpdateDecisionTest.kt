package io.github.catlinux.keynexa.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class UpdateDecisionTest {
    private val sha = "a".repeat(64)
    private val now = Instant.parse("2026-10-27T12:00:00Z")

    private fun manifest(version: String): UpdateManifest =
        UpdateManifest(
            SemVer.parse(version)!!,
            LocalDate.of(2026, 10, 27),
            listOf(ManifestFile("KeyNexa.msi", 1024L, sha)),
        )

    @Test
    fun offersANewerVersion() {
        val newer = manifest("1.2.4")
        assertSame(newer, UpdateDecision.offer(SemVer.parse("1.2.3")!!, newer))
    }

    @Test
    fun doesNotOfferAnEqualVersion() {
        assertNull(UpdateDecision.offer(SemVer.parse("1.2.3")!!, manifest("1.2.3")))
    }

    @Test
    fun doesNotOfferAnOlderVersion() {
        assertNull(UpdateDecision.offer(SemVer.parse("1.2.3")!!, manifest("1.2.2")))
    }

    @Test
    fun comparesMinorVersionsNumerically() {
        val newer = manifest("1.10.0")
        assertSame(newer, UpdateDecision.offer(SemVer.parse("1.9.9")!!, newer))
        assertNull(UpdateDecision.offer(SemVer.parse("1.10.0")!!, manifest("1.9.9")))
    }

    @Test
    fun checksAtOnceWithoutAPreviousCheck() {
        assertTrue(CheckSchedule.isDue(null, now))
    }

    @Test
    fun waitsUntilTheIntervalHasPassed() {
        assertFalse(CheckSchedule.isDue(now.minus(Duration.ofHours(23)), now))
    }

    @Test
    fun checksWhenExactlyOneDayHasPassed() {
        assertTrue(CheckSchedule.isDue(now.minus(Duration.ofDays(1)), now))
    }

    @Test
    fun checksWhenTheLastCheckLiesInTheFuture() {
        assertTrue(CheckSchedule.isDue(now.plus(Duration.ofDays(2)), now))
    }

    @Test
    fun honoursACustomInterval() {
        assertTrue(CheckSchedule.isDue(now.minus(Duration.ofHours(6)), now, Duration.ofHours(6)))
        assertFalse(CheckSchedule.isDue(now.minus(Duration.ofHours(5)), now, Duration.ofHours(6)))
    }
}
