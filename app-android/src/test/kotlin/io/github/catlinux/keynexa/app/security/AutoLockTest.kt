package io.github.catlinux.keynexa.app.security

import io.github.catlinux.keynexa.app.settings.SettingsValues
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoLockTest {
    private var clock = 0L
    private var locks = 0
    private var settings = SettingsValues()
    private val timer = FakeLockTimer()

    private fun autoLock() = AutoLock(settings = { settings }, now = { clock }, timer = timer, lock = { locks++ })

    @Test
    fun locksWhenAwayLongerThanTheDelay() {
        settings = SettingsValues(autoLockSeconds = 60)
        val lock = autoLock()
        lock.onBackground()
        clock = 61_000
        lock.onForeground()
        assertEquals(1, locks)
    }

    @Test
    fun timerLocksWhileTheAppRemainsInTheBackground() {
        settings = SettingsValues(autoLockSeconds = 60)
        autoLock().onBackground()
        assertEquals(60_000L, timer.startedDelay)
        timer.fire()
        assertEquals(1, locks)
    }

    @Test
    fun returningToForegroundCancelsTheTimer() {
        settings = SettingsValues(autoLockSeconds = 60)
        val lock = autoLock()
        lock.onBackground()
        clock = 59_000
        lock.onForeground()
        timer.fire()
        assertEquals(0, locks)
        assertEquals(1, timer.cancellations)
    }

    @Test
    fun staysOpenWhenBackBeforeTheDelay() {
        settings = SettingsValues(autoLockSeconds = 60)
        val lock = autoLock()
        lock.onBackground()
        clock = 59_000
        lock.onForeground()
        assertEquals(0, locks)
    }

    @Test
    fun locksAtOnceWhenTheChoiceIsImmediately() {
        settings = SettingsValues(autoLockSeconds = SettingsValues.LOCK_IMMEDIATELY)
        autoLock().onBackground()
        assertEquals(1, locks)
        assertEquals(null, timer.startedDelay)
    }

    @Test
    fun neverLocksWhenTheChoiceIsNever() {
        settings = SettingsValues(autoLockSeconds = SettingsValues.NEVER_LOCK)
        val lock = autoLock()
        lock.onBackground()
        clock = 10_000_000
        lock.onForeground()
        assertEquals(0, locks)
        assertEquals(null, timer.startedDelay)
    }

    @Test
    fun foregroundWithoutBackgroundDoesNothing() {
        autoLock().onForeground()
        assertEquals(0, locks)
    }

    @Test
    fun screenOffLocksOnlyWhenEnabled() {
        val lock = autoLock()
        lock.onScreenOff()
        assertEquals(1, locks)
        settings = SettingsValues(lockOnScreenOff = false)
        lock.onScreenOff()
        assertEquals(1, locks)
    }

    private class FakeLockTimer : LockTimer {
        var startedDelay: Long? = null
        var cancellations = 0
        private var action: (() -> Unit)? = null

        override fun start(
            delayMillis: Long,
            action: () -> Unit,
        ) {
            startedDelay = delayMillis
            this.action = action
        }

        override fun cancel() {
            cancellations++
            action = null
        }

        fun fire() {
            action?.invoke()
        }
    }
}
