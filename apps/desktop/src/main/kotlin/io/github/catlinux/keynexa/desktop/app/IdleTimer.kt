package io.github.catlinux.keynexa.desktop.app

/** How many milliseconds a minute has. */
private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Watches how long the window has been idle: [restart] moves the deadline to now, and [isDue] says whether the given
 * number of minutes have passed since it. The clock is injected, so the logic can be tested without waiting.
 */
internal class IdleTimer(
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var lastActivity: Long = now()

    /** Records that the user did something, so the count starts again from now. */
    fun restart() {
        lastActivity = now()
    }

    /** True when [minutes] have passed since the last activity; false when [minutes] is not positive. */
    fun isDue(minutes: Int): Boolean {
        if (minutes <= 0) return false
        return now() - lastActivity >= minutes * MILLIS_PER_MINUTE
    }
}

/**
 * The delay the clipboard timer waits before it clears a copied secret: [seconds] turned into milliseconds, and a
 * value of 0 or less meaning the timer never clears anything by itself, so only locking and leaving do.
 */
internal fun clipboardClearMillis(seconds: Int): Long = if (seconds <= 0) NEVER else seconds * MILLIS_PER_SECOND

/** The delay that means the timer never fires by itself. */
private const val NEVER = Long.MAX_VALUE

/** How many milliseconds a second has. */
private const val MILLIS_PER_SECOND = 1_000L
