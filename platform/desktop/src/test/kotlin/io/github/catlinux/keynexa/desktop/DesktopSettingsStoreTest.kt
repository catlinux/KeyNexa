package io.github.catlinux.keynexa.desktop

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/** The desktop settings file: defaults, round trip and what one damaged line does to the rest. */
class DesktopSettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): DesktopSettingsStore = DesktopSettingsStore(file())

    private fun file() = folder.root.toPath().resolve("settings.properties")

    @Test
    fun aMissingFileGivesEveryDefault() {
        assertEquals(DesktopSettings(), store().load())
    }

    @Test
    fun everyValueSurvivesTheRoundTrip() {
        val store = store()
        val settings =
            DesktopSettings(
                lockAfterIdleMinutes = 30,
                lockOnWindowsLock = false,
                lockOnSuspend = false,
                allowScreenshots = true,
                clipboardClearSeconds = 120,
                useKeypad = true,
                theme = Theme.Dark,
            )

        store.save(settings)

        assertEquals(settings, store.load())
    }

    @Test
    fun anUnreadableFileGivesEveryDefault() {
        val file = folder.root.toPath().resolve("settings.properties")
        Files.createDirectories(file.parent)
        Files.write(file, byteArrayOf(0x00, 0x01, 0x02))

        assertEquals(DesktopSettings(), DesktopSettingsStore(file).load())
    }

    @Test
    fun anOutOfRangeValueFallsBackOnItsOwn() {
        val store = store()
        val file = folder.root.toPath().resolve("settings.properties")
        Files.write(
            file,
            listOf(
                "lockAfterIdleMinutes=7",
                "clipboardClearSeconds=45",
                "theme=Purple",
                "useKeypad=true",
            ).joinToString("\n").toByteArray(),
        )

        val loaded = store.load()

        assertEquals(DesktopSettings.DEFAULT_LOCK_AFTER_IDLE, loaded.lockAfterIdleMinutes)
        assertEquals(DesktopSettings.DEFAULT_CLIPBOARD_CLEAR, loaded.clipboardClearSeconds)
        assertEquals(Theme.System, loaded.theme)
        assertEquals(true, loaded.useKeypad)
    }

    @Test
    fun garbageAndUnknownKeysAreIgnored() {
        val file = folder.root.toPath().resolve("settings.properties")
        Files.write(
            file,
            listOf(
                "lockAfterIdleMinutes=many",
                "lockOnWindowsLock=perhaps",
                "somethingElse=1",
                "theme=",
            ).joinToString("\n").toByteArray(),
        )

        val loaded = DesktopSettingsStore(file).load()

        assertEquals(DesktopSettings(), loaded)
    }

    @Test
    fun zeroMeansNeverAndIsKept() {
        val store = store()

        store.save(DesktopSettings(lockAfterIdleMinutes = 0, clipboardClearSeconds = 0))

        val loaded = store.load()
        assertEquals(0, loaded.lockAfterIdleMinutes)
        assertEquals(0, loaded.clipboardClearSeconds)
    }

    @Test
    fun quickUnlockSurvivesTheRoundTrip() {
        val store = store()

        store.save(DesktopSettings(quickUnlock = true, quickUnlockHours = 24))

        val loaded = store.load()
        assertEquals(true, loaded.quickUnlock)
        assertEquals(24, loaded.quickUnlockHours)
    }

    @Test
    fun anInvalidQuickUnlockHoursFallsBackOnItsOwn() {
        val file = folder.root.toPath().resolve("settings.properties")
        Files.write(
            file,
            listOf(
                "quickUnlock=true",
                "quickUnlockHours=7",
            ).joinToString("\n").toByteArray(),
        )

        val loaded = DesktopSettingsStore(file).load()

        assertEquals(true, loaded.quickUnlock)
        assertEquals(DesktopSettings.DEFAULT_QUICK_UNLOCK_HOURS, loaded.quickUnlockHours)
    }

    @Test
    fun anOldFileWithoutTheQuickUnlockKeysGivesTheirDefaults() {
        val file = folder.root.toPath().resolve("settings.properties")
        Files.write(
            file,
            listOf(
                "lockAfterIdleMinutes=30",
                "theme=Dark",
            ).joinToString("\n").toByteArray(),
        )

        val loaded = DesktopSettingsStore(file).load()

        assertEquals(30, loaded.lockAfterIdleMinutes)
        assertEquals(Theme.Dark, loaded.theme)
        assertEquals(false, loaded.quickUnlock)
        assertEquals(DesktopSettings.DEFAULT_QUICK_UNLOCK_HOURS, loaded.quickUnlockHours)
    }
}
