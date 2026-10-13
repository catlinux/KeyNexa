package io.github.catlinux.keynexa.kdbx

import org.junit.Assert.assertEquals
import org.junit.Test

class KdbxModuleTest {
    @Test
    fun exposesModuleName() {
        assertEquals("core-kdbx", KdbxModule.NAME)
    }
}
