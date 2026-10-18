package io.github.catlinux.keynexa.desktop

/** The theme a desktop client shows. */
enum class Theme { System, Light, Dark }

/**
 * What a desktop client remembers between runs: when it locks by itself, what locks the vault for it, how long a
 * copied secret stays on the clipboard and how the interface looks. Nothing secret is ever stored here.
 */
data class DesktopSettings(
    /** Minutes without use after which the vault locks; 0 means never. */
    val lockAfterIdleMinutes: Int = DEFAULT_LOCK_AFTER_IDLE,
    /** Lock the vault when the Windows session is locked. */
    val lockOnWindowsLock: Boolean = true,
    /** Lock the vault when the machine is suspended. */
    val lockOnSuspend: Boolean = true,
    /** Let screen captures show the window; by default they do not. */
    val allowScreenshots: Boolean = false,
    /** Seconds a copied secret stays on the clipboard; 0 means never. */
    val clipboardClearSeconds: Int = DEFAULT_CLIPBOARD_CLEAR,
    /** Use the KeyNexa keypad instead of the system keyboard. */
    val useKeypad: Boolean = false,
    val theme: Theme = Theme.System,
) {
    companion object {
        /** The idle minutes a client offers, 0 meaning never. */
        val LOCK_AFTER_IDLE_CHOICES = listOf(1, 5, 15, 30, 0)

        /** The seconds a copied secret may stay, 0 meaning never. */
        val CLIPBOARD_CLEAR_CHOICES = listOf(10, 30, 60, 120, 0)

        const val DEFAULT_LOCK_AFTER_IDLE = 5
        const val DEFAULT_CLIPBOARD_CLEAR = 30
    }
}
