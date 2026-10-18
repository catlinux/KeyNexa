package io.github.catlinux.keynexa.desktop.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The single-instance lock: the same JVM cannot take it twice, and it can be taken again after release. */
class InstanceLockTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aSecondLockOnTheSameFileFails() {
        val file = folder.newFile("instance.lock").toPath()
        val first = InstanceLock(file)
        val second = InstanceLock(file)

        assertTrue(first.acquired)
        assertFalse(second.acquired)

        second.close()
        first.close()
    }

    @Test
    fun theLockCanBeTakenAgainAfterItIsReleased() {
        val file = folder.newFile("instance.lock").toPath()
        val first = InstanceLock(file)
        assertTrue(first.acquired)
        first.close()

        val second = InstanceLock(file)

        assertTrue(second.acquired)
        second.close()
    }
}
