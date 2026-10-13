package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultModuleTest {
    @Test
    fun exposesModuleName() {
        assertEquals("core-vault", VaultModule.NAME)
    }
}
