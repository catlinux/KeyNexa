package io.github.catlinux.keynexa.windows

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

/** The Windows data folder: `%LOCALAPPDATA%\KeyNexa` and its fallback under the user's home. */
class WindowsDirsTest {
    @Test
    fun usesLocalAppDataWhenItIsSet() {
        val dirs =
            WindowsDirs(
                env = { name -> if (name == "LOCALAPPDATA") LOCAL_APP_DATA else null },
                home = HOME,
            )

        assertEquals(Path.of(LOCAL_APP_DATA, "KeyNexa"), dirs.data)
    }

    @Test
    fun fallsBackToTheHomeFolderWhenLocalAppDataIsMissing() {
        val dirs = WindowsDirs(env = { null }, home = HOME)

        assertEquals(Path.of(HOME, "AppData", "Local", "KeyNexa"), dirs.data)
    }

    @Test
    fun fallsBackToTheHomeFolderWhenLocalAppDataIsBlank() {
        val dirs = WindowsDirs(env = { "   " }, home = HOME)

        assertEquals(Path.of(HOME, "AppData", "Local", "KeyNexa"), dirs.data)
    }

    private companion object {
        const val LOCAL_APP_DATA = "C:\\Users\\test\\AppData\\Local"
        const val HOME = "C:\\Users\\test"
    }
}
