package io.github.catlinux.keynexa.desktop.app

/**
 * The "at most once a minute" rule of the synchronisation triggers: [mark] records that a round just happened, and
 * [due] answers whether enough time has passed and records it. The clock is injected, so the rule can be tested.
 */
internal class SyncTriggerGate(
    private val intervalMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var last: Long? = null

    /** Records a trigger at this moment, so [due] stays false for the whole interval. */
    fun mark() {
        last = now()
    }

    /** True when the interval has passed since the last trigger; it records this moment when it answers true. */
    fun due(): Boolean {
        val previous = last
        if (previous != null && now() - previous < intervalMillis) return false
        last = now()
        return true
    }
}
