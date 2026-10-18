package io.github.catlinux.keynexa.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppModuleTest {
    @Test
    fun exposesModuleName() {
        assertEquals("apps/android", AppModule.NAME)
    }
}
