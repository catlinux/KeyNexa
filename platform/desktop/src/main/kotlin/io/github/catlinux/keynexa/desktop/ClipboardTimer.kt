package io.github.catlinux.keynexa.desktop

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** How long a copied secret stays on the clipboard before it is cleared. */
private const val CLEAR_AFTER_MILLIS = 30_000L

/** The name of the thread that clears the clipboard, so it is easy to tell apart while debugging. */
private const val TIMER_THREAD = "keynexa-clipboard"

/**
 * Takes a copied secret off the clipboard after [clearAfterMillis], so it does not sit there for the rest of the day.
 *
 * The delay is read again on every copy, so a setting can change it while the app is running.
 *
 * A new copy replaces any pending clear, [clearNow] clears at once (on lock and on exit) and [close] stops the timer
 * for good. Meant to be used from the client's own thread; the clear itself runs on the timer thread.
 */
class ClipboardTimer(
    private val clipboard: SecureClipboard,
    private val clearAfterMillis: () -> Long = { CLEAR_AFTER_MILLIS },
    private val scheduler: ScheduledExecutorService = newScheduler(),
) : AutoCloseable {
    private var pending: ScheduledFuture<*>? = null

    /** Copies [text], marking it when [sensitive], and schedules the clear of a sensitive copy. */
    fun copy(
        text: CharArray,
        sensitive: Boolean,
    ) {
        clipboard.copy(text, sensitive)
        pending?.cancel(false)
        pending =
            if (sensitive) {
                scheduler.schedule({ clipboard.clearIfOurs() }, clearAfterMillis(), TimeUnit.MILLISECONDS)
            } else {
                null
            }
    }

    /** Clears the clipboard now when it still holds what KeyNexa put there, and forgets any pending clear. */
    fun clearNow() {
        pending?.cancel(false)
        pending = null
        clipboard.clearIfOurs()
    }

    /** Stops the timer; the clipboard is left as it is, so call [clearNow] first when the app is leaving. */
    override fun close() {
        pending?.cancel(false)
        pending = null
        scheduler.shutdownNow()
    }

    private companion object {
        fun newScheduler(): ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, TIMER_THREAD).apply { isDaemon = true }
            }
    }
}
