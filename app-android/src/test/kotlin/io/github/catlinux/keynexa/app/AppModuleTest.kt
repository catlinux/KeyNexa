package io.github.catlinux.keynexa.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppModuleTest {
    @Test
    fun exposesModuleName() {
        assertEquals("app-android", AppModule.NAME)
    }
}
