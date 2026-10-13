package io.github.catlinux.keynexa.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformModuleTest {
    @Test
    fun exposesModuleName() {
        assertEquals("platform-android", PlatformModule.NAME)
    }
}
