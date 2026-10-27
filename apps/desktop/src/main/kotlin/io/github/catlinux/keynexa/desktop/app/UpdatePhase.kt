package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.desktop.UpdateCheck
import java.io.File
import java.io.IOException
import java.nio.file.Path

/** What the updater is doing, for the settings screen and the dialogs. */
internal sealed interface UpdatePhase {
    data object Idle : UpdatePhase

    data object Checking : UpdatePhase

    /** A check the user asked for found nothing newer. */
    data object UpToDate : UpdatePhase

    /** A newer, signed version is ready to install; [failed] when a try to install it did not go through. */
    class Offered(
        val offer: UpdateCheck.Available,
        val failed: Boolean = false,
    ) : UpdatePhase

    data object Downloading : UpdatePhase

    /** A check the user asked for failed; [reason] is the service's short token. */
    class Failed(
        val reason: String,
    ) : UpdatePhase
}

/** True when the failure is the connection itself, as opposed to a published update that did not pass the checks. */
internal fun isNetworkFailure(reason: String): Boolean = reason == "network"

/**
 * Starts the installer for the signed, checked MSI at [msi] and returns at once: the app closes next, because the
 * installer replaces the files it is running from. `/passive` shows the progress without asking anything, and the
 * installer finds the folder KeyNexa is already installed in (1.11.7). The path is passed as an argument, never
 * through a shell, and msiexec is the system's own, not whatever the search path finds first.
 */
internal fun launchInstaller(msi: Path): Boolean {
    val windows = System.getenv("SystemRoot")?.takeIf { it.isNotBlank() } ?: return false
    val msiexec = File(windows, "System32\\msiexec.exe")
    if (!msiexec.isFile) return false
    return try {
        ProcessBuilder(msiexec.path, "/i", msi.toAbsolutePath().toString(), "/passive").start()
        true
    } catch (_: IOException) {
        false
    }
}
