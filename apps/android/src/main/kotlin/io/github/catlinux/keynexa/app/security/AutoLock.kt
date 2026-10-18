package io.github.catlinux.keynexa.app.security

import io.github.catlinux.keynexa.app.settings.SettingsValues

interface LockTimer {
    fun start(
        delayMillis: Long,
        action: () -> Unit,
    )

    fun cancel()
}

/**
 * Decides when the open vault locks by itself: after the app has been in the background for the chosen time, as soon
 * as it is left when the choice is "immediately", and when the screen turns off. It only decides; [lock] does the
 * work. The clock is injected (monotonic milliseconds) so the rules can be tested.
 */
class AutoLock(
    private val settings: () -> SettingsValues,
    private val now: () -> Long,
    private val timer: LockTimer,
    private val lock: () -> Unit,
) {
    private var leftAt: Long? = null

    /** The app is no longer in front (not because of a rotation). */
    fun onBackground() {
        val delay = settings().autoLockSeconds
        if (delay == SettingsValues.LOCK_IMMEDIATELY) {
            timer.cancel()
            leftAt = null
            lock()
        } else {
            leftAt = now()
            if (delay == SettingsValues.NEVER_LOCK) {
                timer.cancel()
            } else {
                timer.start(delay * MILLIS_PER_SECOND) {
                    leftAt = null
                    lock()
                }
            }
        }
    }

    /** The app is in front again: lock if it was away for longer than the chosen time. */
    fun onForeground() {
        timer.cancel()
        val left = leftAt ?: return
        leftAt = null
        val delay = settings().autoLockSeconds
        if (delay != SettingsValues.NEVER_LOCK && now() - left >= delay * MILLIS_PER_SECOND) lock()
    }

    /** The screen went off. */
    fun onScreenOff() {
        if (settings().lockOnScreenOff) lock()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
