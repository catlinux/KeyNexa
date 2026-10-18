package io.github.catlinux.keynexa.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A delay long enough that the real scheduler never fires while a test runs. */
private const val DELAY_MILLIS = 10_000L

/** The delay a timer clears after when nothing else is asked of it. */
private const val DEFAULT_DELAY_MILLIS = 30_000L

/** The timer that takes a copied secret off the clipboard: when it clears, and when it leaves things alone. */
class ClipboardTimerTest {
    @Test
    fun schedulesTheClearOfASensitiveCopy() {
        val clipboard = FakeClipboard()
        val scheduler = RecordingScheduler()
        val timer = ClipboardTimer(clipboard, clearAfterMillis = DELAY_MILLIS, scheduler = scheduler)

        timer.copy("a secret".toCharArray(), sensitive = true)

        assertEquals(listOf(DELAY_MILLIS), scheduler.delays)
        assertEquals(1, clipboard.copies.get())
        scheduler.runRecorded()
        assertEquals(1, clipboard.clears.get())
        timer.close()
    }

    @Test
    fun schedulesNothingForAPlainCopy() {
        val clipboard = FakeClipboard()
        val scheduler = RecordingScheduler()
        val timer = ClipboardTimer(clipboard, clearAfterMillis = DELAY_MILLIS, scheduler = scheduler)

        timer.copy("a note".toCharArray(), sensitive = false)

        assertEquals(1, clipboard.copies.get())
        assertTrue(scheduler.delays.isEmpty())
        assertTrue(scheduler.queue.isEmpty())
        timer.close()
    }

    @Test
    fun aSecondCopyReplacesThePendingClear() {
        val clipboard = FakeClipboard()
        val scheduler = RecordingScheduler()
        val timer = ClipboardTimer(clipboard, clearAfterMillis = DELAY_MILLIS, scheduler = scheduler)

        timer.copy("a secret".toCharArray(), sensitive = true)
        timer.copy("a note".toCharArray(), sensitive = false)

        assertEquals(2, clipboard.copies.get())
        assertTrue(scheduler.queue.isEmpty())
        timer.close()
    }

    @Test
    fun clearsAtOnceAndForgetsThePendingClear() {
        val clipboard = FakeClipboard()
        val scheduler = RecordingScheduler()
        val timer = ClipboardTimer(clipboard, clearAfterMillis = DELAY_MILLIS, scheduler = scheduler)

        timer.copy("a secret".toCharArray(), sensitive = true)
        timer.clearNow()

        assertEquals(1, clipboard.clears.get())
        assertTrue(scheduler.queue.isEmpty())
        timer.close()
    }

    @Test
    fun theDefaultDelayIsThirtySeconds() {
        val clipboard = FakeClipboard()
        val scheduler = RecordingScheduler()
        val timer = ClipboardTimer(clipboard, scheduler = scheduler)

        timer.copy("a secret".toCharArray(), sensitive = true)

        assertEquals(listOf(DEFAULT_DELAY_MILLIS), scheduler.delays)
        timer.close()
    }

    @Test
    fun closeStopsTheScheduler() {
        val clipboard = FakeClipboard()
        val scheduler = RecordingScheduler()
        val timer = ClipboardTimer(clipboard, clearAfterMillis = DELAY_MILLIS, scheduler = scheduler)

        timer.close()

        assertTrue(scheduler.isShutdown)
    }
}

/** A [SecureClipboard] that only counts what it is asked to do, so a test never touches the real clipboard. */
private class FakeClipboard : SecureClipboard {
    val copies = AtomicInteger()
    val clears = AtomicInteger()

    override fun copy(
        text: CharArray,
        sensitive: Boolean,
    ) {
        copies.incrementAndGet()
    }

    override fun clearIfOurs() {
        clears.incrementAndGet()
    }
}

/**
 * A scheduler that records the delay and the task of every clear, so a test can run one when it wants to instead of
 * waiting for it. It drops a cancelled task at once, which is how a test sees that a clear was replaced.
 */
private class RecordingScheduler :
    ScheduledThreadPoolExecutor(
        1,
        ThreadFactory { task -> Thread(task, "keynexa-clipboard-test").apply { isDaemon = true } },
    ) {
    val delays = mutableListOf<Long>()
    val tasks = mutableListOf<Runnable>()

    init {
        removeOnCancelPolicy = true
    }

    override fun schedule(
        command: Runnable,
        delay: Long,
        unit: TimeUnit,
    ): ScheduledFuture<*> {
        delays += unit.toMillis(delay)
        tasks += command
        return super.schedule(command, delay, unit)
    }

    /** Runs every recorded task, as the scheduler would have done when the delay was over. */
    fun runRecorded() {
        tasks.toList().forEach { it.run() }
    }
}
