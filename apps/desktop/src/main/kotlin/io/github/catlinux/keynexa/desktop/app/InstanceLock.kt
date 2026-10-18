package io.github.catlinux.keynexa.desktop.app

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * One KeyNexa at a time: the first instance holds an exclusive lock on [file] for as long as it runs, and a later
 * one gets [acquired] false and leaves.
 */
class InstanceLock(
    file: Path,
) : AutoCloseable {
    private val channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    private val lock: FileLock? = tryLock()

    /** True when this process holds the lock. */
    val acquired: Boolean get() = lock != null

    /** Releases the lock and closes the channel. */
    override fun close() {
        lock?.release()
        channel.close()
    }

    @Suppress("SwallowedException")
    private fun tryLock(): FileLock? =
        try {
            channel.tryLock()
        } catch (failure: OverlappingFileLockException) {
            null
        } catch (failure: IOException) {
            null
        }
}
