package io.github.catlinux.keynexa.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncModuleTest {
    @Test
    fun exposesModuleName() {
        assertEquals("core-sync", SyncModule.NAME)
    }
}
