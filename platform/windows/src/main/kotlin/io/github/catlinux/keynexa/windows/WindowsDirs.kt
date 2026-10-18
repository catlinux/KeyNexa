package io.github.catlinux.keynexa.windows

import io.github.catlinux.keynexa.desktop.DesktopDirs
import java.nio.file.Path

/**
 * The Windows data folder: `%LOCALAPPDATA%\KeyNexa`, or `%USERPROFILE%\AppData\Local\KeyNexa` when the environment
 * variable is missing or blank. The folder is not created here.
 */
class WindowsDirs(
    env: (String) -> String? = System::getenv,
    home: String = System.getProperty("user.home"),
) : DesktopDirs {
    override val data: Path = dataFolder(env, home)
}

private fun dataFolder(
    env: (String) -> String?,
    home: String,
): Path {
    val localAppData = env(LOCAL_APP_DATA)?.takeIf { it.isNotBlank() }
    return if (localAppData != null) {
        Path.of(localAppData, APP_FOLDER)
    } else {
        Path.of(home, "AppData", "Local", APP_FOLDER)
    }
}

private const val LOCAL_APP_DATA = "LOCALAPPDATA"
private const val APP_FOLDER = "KeyNexa"
