package io.github.catlinux.keynexa.app.cloud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The synchronisation round queue: one round per key, with one more when a request arrives mid-round. */
class SyncRoundQueueTest {
    private val executor = Executors.newSingleThreadExecutor()

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun aSubmitRunsARound() {
        val queue = SyncRoundQueue(executor)
        val rounds = AtomicInteger()
        val done = CountDownLatch(1)

        queue.submit(KEY) {
            rounds.incrementAndGet()
            done.countDown()
        }

        assertTrue(done.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        awaitIdle(queue, KEY)
        assertEquals(1, rounds.get())
    }

    @Test
    fun aRequestWhileARoundRunsAsksForExactlyOneMore() {
        val queue = SyncRoundQueue(executor)
        val rounds = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)

        queue.submit(KEY) {
            rounds.incrementAndGet()
            if (first.compareAndSet(true, false)) {
                started.countDown()
                release.await()
            }
        }
        assertTrue(started.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

        queue.submit(KEY) {}
        release.countDown()
        awaitIdle(queue, KEY)

        assertEquals(2, rounds.get())
    }

    @Test
    fun severalRequestsDuringARoundAreStillOnlyOneExtraRound() {
        val queue = SyncRoundQueue(executor)
        val rounds = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)

        queue.submit(KEY) {
            rounds.incrementAndGet()
            if (first.compareAndSet(true, false)) {
                started.countDown()
                release.await()
            }
        }
        assertTrue(started.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

        queue.submit(KEY) {}
        queue.submit(KEY) {}
        queue.submit(KEY) {}
        release.countDown()
        awaitIdle(queue, KEY)

        assertEquals(2, rounds.get())
    }

    @Test
    fun anotherKeyDoesNotCollide() {
        val queue = SyncRoundQueue(executor)
        val roundsFirst = AtomicInteger()
        val roundsSecond = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)

        queue.submit(FIRST) {
            roundsFirst.incrementAndGet()
            if (first.compareAndSet(true, false)) {
                started.countDown()
                release.await()
            }
        }
        assertTrue(started.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

        queue.submit(SECOND) { roundsSecond.incrementAndGet() }
        release.countDown()
        awaitIdle(queue, FIRST)
        awaitIdle(queue, SECOND)

        assertEquals(1, roundsFirst.get())
        assertEquals(1, roundsSecond.get())
    }

    @Test
    fun anExceptionInARoundDoesNotKillTheThread() {
        val queue = SyncRoundQueue(executor)
        val rounds = AtomicInteger()
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)

        queue.submit(KEY) {
            rounds.incrementAndGet()
            first.countDown()
            throw IOException("boom")
        }
        assertTrue(first.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        awaitIdle(queue, KEY)

        queue.submit(KEY) {
            rounds.incrementAndGet()
            second.countDown()
            error("boom")
        }
        assertTrue(second.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        awaitIdle(queue, KEY)

        val done = CountDownLatch(1)
        queue.submit(KEY) {
            rounds.incrementAndGet()
            done.countDown()
        }
        assertTrue(done.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        awaitIdle(queue, KEY)

        assertEquals(3, rounds.get())
    }

    @Test
    fun isRunningReflectsTheQueue() {
        val queue = SyncRoundQueue(executor)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        assertFalse(queue.isRunning(KEY))
        queue.submit(KEY) {
            started.countDown()
            release.await()
        }
        assertTrue(started.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertTrue(queue.isRunning(KEY))

        release.countDown()
        awaitIdle(queue, KEY)
        assertFalse(queue.isRunning(KEY))
    }

    /** Waits until the queue has finished with [key], so the test does not race the worker thread. */
    private fun awaitIdle(
        queue: SyncRoundQueue,
        key: String,
    ) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (queue.isRunning(key) && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        assertFalse(queue.isRunning(key))
    }

    private companion object {
        const val KEY = "vault-one"
        const val FIRST = "vault-one"
        const val SECOND = "vault-two"
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
    }
}
