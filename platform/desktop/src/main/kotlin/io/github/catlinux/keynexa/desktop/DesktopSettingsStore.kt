package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.platform.writeAtomically
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

private const val LOCK_AFTER_IDLE = "lockAfterIdleMinutes"
private const val LOCK_ON_WINDOWS_LOCK = "lockOnWindowsLock"
private const val LOCK_ON_SUSPEND = "lockOnSuspend"
private const val ALLOW_SCREENSHOTS = "allowScreenshots"
private const val CLIPBOARD_CLEAR_SECONDS = "clipboardClearSeconds"
private const val USE_KEYPAD = "useKeypad"
private const val THEME = "theme"

/**
 * The desktop settings, kept as a plain properties file written in UTF-8 and put in place atomically.
 *
 * Nothing secret goes in it. A missing file, a file that cannot be read, and any value that is missing, of the wrong
 * kind or out of range all fall back to the default for that value alone, so one damaged line never loses the rest.
 */
class DesktopSettingsStore(
    private val file: Path,
) {
    /** The stored settings, with the default for every value that is missing or cannot be used. */
    @Suppress("SwallowedException")
    fun load(): DesktopSettings {
        val properties = Properties()
        try {
            Files.newInputStream(file).use { properties.load(InputStreamReader(it, Charsets.UTF_8)) }
        } catch (failure: IOException) {
            return DesktopSettings()
        } catch (failure: IllegalArgumentException) {
            return DesktopSettings()
        }
        return DesktopSettings(
            lockAfterIdleMinutes =
                choice(
                    properties,
                    LOCK_AFTER_IDLE,
                    DesktopSettings.LOCK_AFTER_IDLE_CHOICES,
                    DesktopSettings.DEFAULT_LOCK_AFTER_IDLE,
                ),
            lockOnWindowsLock = flag(properties, LOCK_ON_WINDOWS_LOCK, default = true),
            lockOnSuspend = flag(properties, LOCK_ON_SUSPEND, default = true),
            allowScreenshots = flag(properties, ALLOW_SCREENSHOTS, default = false),
            clipboardClearSeconds =
                choice(
                    properties,
                    CLIPBOARD_CLEAR_SECONDS,
                    DesktopSettings.CLIPBOARD_CLEAR_CHOICES,
                    DesktopSettings.DEFAULT_CLIPBOARD_CLEAR,
                ),
            useKeypad = flag(properties, USE_KEYPAD, default = false),
            theme = theme(properties),
        )
    }

    /** Writes [settings] over the file, leaving the previous one in place if anything goes wrong. */
    fun save(settings: DesktopSettings) {
        val values =
            mapOf(
                LOCK_AFTER_IDLE to settings.lockAfterIdleMinutes.toString(),
                LOCK_ON_WINDOWS_LOCK to settings.lockOnWindowsLock.toString(),
                LOCK_ON_SUSPEND to settings.lockOnSuspend.toString(),
                ALLOW_SCREENSHOTS to settings.allowScreenshots.toString(),
                CLIPBOARD_CLEAR_SECONDS to settings.clipboardClearSeconds.toString(),
                USE_KEYPAD to settings.useKeypad.toString(),
                THEME to settings.theme.name,
            )
        val text =
            buildString {
                values.toSortedMap().forEach { (key, value) ->
                    append(key).append('=').append(value).append('\n')
                }
            }
        writeAtomically(file.toFile(), text.toByteArray(Charsets.UTF_8))
    }

    /** The stored value when it is one of [allowed], and [fallback] otherwise. */
    private fun choice(
        properties: Properties,
        key: String,
        allowed: List<Int>,
        fallback: Int,
    ): Int {
        val value = properties.getProperty(key)?.trim()?.toIntOrNull() ?: return fallback
        return if (value in allowed) value else fallback
    }

    /** The stored flag when it reads as one, and [default] otherwise. */
    private fun flag(
        properties: Properties,
        key: String,
        default: Boolean,
    ): Boolean =
        when (properties.getProperty(key)?.trim()?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
        }

    /** The stored theme when it names one, and the system theme otherwise. */
    private fun theme(properties: Properties): Theme {
        val name = properties.getProperty(THEME)?.trim() ?: return Theme.System
        return Theme.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Theme.System
    }
}
