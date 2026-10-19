package io.github.catlinux.keynexa.app.cloud

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Runs one round per key at a time on [executor], with at most one extra round when a request arrives mid-round
 * (ADR-0009 §4). [submit] returns at once; a failure inside a round never ends the worker thread. Kept free of
 * Android so the shared `src/test` can test it in both flavours.
 */
internal class SyncRoundQueue(
    private val executor: Executor,
) {
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val again = ConcurrentHashMap.newKeySet<String>()

    /** True while a round for [key] is running or waiting in the executor. */
    fun isRunning(key: String): Boolean = key in running

    /**
     * Enqueues [round] for [key] and returns at once.
     *
     * A request while a round for the same key is running asks for exactly one more round, never more than one
     * pending.
     */
    fun submit(
        key: String,
        round: () -> Unit,
    ) {
        if (running.add(key)) {
            executor.execute { runKey(key, round) }
        } else {
            again.add(key)
        }
    }

    private fun runKey(
        key: String,
        round: () -> Unit,
    ) {
        try {
            runOnce(round)
            while (again.remove(key)) runOnce(round)
        } finally {
            running.remove(key)
            // A request may have arrived between the loop above and this removal: run the extra round it asked for.
            if (again.remove(key) && running.add(key)) {
                executor.execute { runKey(key, round) }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun runOnce(round: () -> Unit) {
        try {
            round()
        } catch (_: IOException) {
            // A failed round must not end the worker thread.
        } catch (_: RuntimeException) {
            // A failed round must not end the worker thread.
        }
    }
}
