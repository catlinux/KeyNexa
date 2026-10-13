package io.github.catlinux.keynexa.app.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** What the user can change about protection. The defaults are the safe ones. */
data class SettingsValues(
    /** Seconds in the background before the vault locks; [LOCK_IMMEDIATELY] locks as soon as the app is left. */
    val autoLockSeconds: Int = DEFAULT_AUTO_LOCK_SECONDS,
    val lockOnScreenOff: Boolean = true,
    /** Off by default: the window is marked secure, which blocks screenshots and hides the app in recents. */
    val allowScreenshots: Boolean = false,
    /** Seconds a copied secret stays on the clipboard. */
    val clipboardSeconds: Int = DEFAULT_CLIPBOARD_SECONDS,
) {
    companion object {
        const val LOCK_IMMEDIATELY = 0
        const val NEVER_LOCK = -1
        const val DEFAULT_AUTO_LOCK_SECONDS = 60
        const val DEFAULT_CLIPBOARD_SECONDS = 30

        /** The choices offered for the auto-lock delay, in seconds. */
        val AUTO_LOCK_CHOICES = listOf(LOCK_IMMEDIATELY, 15, 30, 60, 300, 900, NEVER_LOCK)

        /** The choices offered for the clipboard delay, in seconds. */
        val CLIPBOARD_CHOICES = listOf(15, 30, 60, 120)
    }
}

/** The settings, kept in the app's private preferences (excluded from backup). [values] is observable by screens. */
class AppSettings(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var values by mutableStateOf(read())
        private set

    fun update(new: SettingsValues) {
        preferences.edit {
            putInt(AUTO_LOCK, new.autoLockSeconds)
            putBoolean(SCREEN_OFF, new.lockOnScreenOff)
            putBoolean(SCREENSHOTS, new.allowScreenshots)
            putInt(CLIPBOARD, new.clipboardSeconds)
        }
        values = new
    }

    private fun read(): SettingsValues {
        val defaults = SettingsValues()
        return SettingsValues(
            autoLockSeconds = preferences.getInt(AUTO_LOCK, defaults.autoLockSeconds),
            lockOnScreenOff = preferences.getBoolean(SCREEN_OFF, defaults.lockOnScreenOff),
            allowScreenshots = preferences.getBoolean(SCREENSHOTS, defaults.allowScreenshots),
            clipboardSeconds = preferences.getInt(CLIPBOARD, defaults.clipboardSeconds),
        )
    }

    private companion object {
        const val FILE = "settings"
        const val AUTO_LOCK = "auto_lock_seconds"
        const val SCREEN_OFF = "lock_on_screen_off"
        const val SCREENSHOTS = "allow_screenshots"
        const val CLIPBOARD = "clipboard_seconds"
    }
}
