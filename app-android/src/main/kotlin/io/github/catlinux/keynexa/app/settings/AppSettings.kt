package io.github.catlinux.keynexa.app.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** The light/dark choice: follow the system, or force one of the two. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        /** Reads a stored name; a missing or unknown value falls back to [System]. */
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: System
    }
}

/** What the user can change about protection. The defaults are the safe ones. */
data class SettingsValues(
    /** Seconds in the background before the vault locks; [LOCK_IMMEDIATELY] locks as soon as the app is left. */
    val autoLockSeconds: Int = DEFAULT_AUTO_LOCK_SECONDS,
    val lockOnScreenOff: Boolean = true,
    /** Off by default: the window is marked secure, which blocks screenshots and hides the app in recents. */
    val allowScreenshots: Boolean = false,
    /** Seconds a copied secret stays on the clipboard. */
    val clipboardSeconds: Int = DEFAULT_CLIPBOARD_SECONDS,
    /** Days after which the master password is asked again even if biometric unlock is on (ADR-0006). */
    val biometricPasswordDays: Int = DEFAULT_BIOMETRIC_PASSWORD_DAYS,
    val syncWarnDays: Int = DEFAULT_SYNC_WARN_DAYS,
    /** Light or dark: [ThemeMode.System] follows the device by default. */
    val themeMode: ThemeMode = ThemeMode.System,
    /** On by default: master passwords are typed with the KeyNexa keypad, not the system keyboard (ADR-0010). */
    val passwordKeypad: Boolean = true,
) {
    companion object {
        const val LOCK_IMMEDIATELY = 0
        const val NEVER_LOCK = -1
        const val DEFAULT_AUTO_LOCK_SECONDS = 60
        const val DEFAULT_CLIPBOARD_SECONDS = 30
        const val DEFAULT_BIOMETRIC_PASSWORD_DAYS = 7
        const val DEFAULT_SYNC_WARN_DAYS = 0

        /** The choices offered for how long biometric unlock may go without the password, in days. */
        val BIOMETRIC_DAYS_CHOICES = listOf(1, 3, 7, 14, 30)

        /** The choices offered for the sync warning, in days. 0 means never. */
        val SYNC_WARN_CHOICES = listOf(0, 3, 7, 14)

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
            putInt(BIOMETRIC_DAYS, new.biometricPasswordDays)
            putInt(SYNC_WARN, new.syncWarnDays)
            putString(THEME, new.themeMode.name)
            putBoolean(PASSWORD_KEYPAD, new.passwordKeypad)
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
            biometricPasswordDays = preferences.getInt(BIOMETRIC_DAYS, defaults.biometricPasswordDays),
            syncWarnDays = preferences.getInt(SYNC_WARN, defaults.syncWarnDays),
            themeMode = ThemeMode.fromStored(preferences.getString(THEME, null)),
            passwordKeypad = preferences.getBoolean(PASSWORD_KEYPAD, defaults.passwordKeypad),
        )
    }

    private companion object {
        const val FILE = "settings"
        const val AUTO_LOCK = "auto_lock_seconds"
        const val SCREEN_OFF = "lock_on_screen_off"
        const val SCREENSHOTS = "allow_screenshots"
        const val CLIPBOARD = "clipboard_seconds"
        const val BIOMETRIC_DAYS = "biometric_password_days"
        const val SYNC_WARN = "sync_warn_days"
        const val THEME = "theme_mode"
        const val PASSWORD_KEYPAD = "password_keypad"
    }
}
