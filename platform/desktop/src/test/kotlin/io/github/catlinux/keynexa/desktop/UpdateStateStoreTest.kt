package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.update.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** [UpdateStateStore]: the round trip and every damaged file that must read as "never checked". */
class UpdateStateStoreTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    @Test
    fun missingFileIsNull() {
        assertNull(store().lastCheck())
    }

    @Test
    fun roundTrip() {
        val store = store()

        store.recordCheck(CHECK)

        assertEquals(CHECK, store.lastCheck())
    }

    @Test
    fun aLaterCheckReplacesTheEarlierOne() {
        val store = store()
        store.recordCheck(CHECK)

        store.recordCheck(LATER)

        assertEquals(LATER, store.lastCheck())
    }

    @Test
    fun theHighestSeenVersionOnlyGoesUp() {
        val store = store()
        store.recordSeen(SemVer(1, 12, 0))

        store.recordSeen(SemVer(1, 11, 0))
        assertEquals(SemVer(1, 12, 0), store.highestSeen())

        store.recordSeen(SemVer(2, 0, 0))
        assertEquals(SemVer(2, 0, 0), store.highestSeen())
    }

    @Test
    fun recordingACheckKeepsTheHighestSeen() {
        val store = store()
        store.recordSeen(SemVer(1, 12, 0))

        store.recordCheck(CHECK)

        assertEquals(SemVer(1, 12, 0), store.highestSeen())
        assertEquals(CHECK, store.lastCheck())
    }

    @Test
    fun nothingSeenIsNull() {
        assertNull(store().highestSeen())
    }

    @Test
    fun invalidDateIsNull() {
        Files.writeString(file(), "lastCheck=not-a-date\n")

        assertNull(store().lastCheck())
    }

    @Test
    fun garbageFileIsNull() {
        Files.writeString(file(), "lastCheck=\\uZZZZ\n")

        assertNull(store().lastCheck())
    }

    private fun store(): UpdateStateStore = UpdateStateStore(file())

    private fun file(): Path = folder.root.toPath().resolve(FILE_NAME)

    private companion object {
        const val FILE_NAME = "update.properties"

        val CHECK: Instant = Instant.parse("2026-10-27T09:30:00Z")
        val LATER: Instant = Instant.parse("2026-10-28T09:30:00Z")
    }
}
