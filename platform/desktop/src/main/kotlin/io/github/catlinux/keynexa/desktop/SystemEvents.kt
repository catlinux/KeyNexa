package io.github.catlinux.keynexa.desktop

/**
 * The system events a desktop client watches for: the session being locked and the machine going to sleep.
 *
 * The callbacks run on the thread the system reports the event on, never on the caller's, so a caller that touches
 * its own state must hop to its own thread first. [close] stops the watch and releases what it used.
 */
interface SystemEvents : AutoCloseable {
    /** Watches for the two events; call this once, and call [close] when the watch is no longer wanted. */
    fun listen(
        onSessionLocked: () -> Unit,
        onSuspend: () -> Unit,
    )

    /** Stops watching and releases what the watch used. */
    override fun close()
}

/** [SystemEvents] for the systems that report none and for tests: it never fires and closes at once. */
object NoSystemEvents : SystemEvents {
    override fun listen(
        onSessionLocked: () -> Unit,
        onSuspend: () -> Unit,
    ) = Unit

    override fun close() = Unit
}
