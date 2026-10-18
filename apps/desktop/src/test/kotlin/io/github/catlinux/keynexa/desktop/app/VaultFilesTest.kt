package io.github.catlinux.keynexa.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** The name shown for a vault file. */
class VaultFilesTest {
    @Test
    fun dropsTheExtension() {
        assertEquals("personal", vaultNameOf("personal.kdbx"))
    }

    @Test
    fun keepsANameWithoutTheExtension() {
        assertEquals("personal", vaultNameOf("personal"))
    }

    @Test
    fun keepsTheNameWhenItIsOnlyTheExtension() {
        assertEquals(".kdbx", vaultNameOf(".kdbx"))
    }
}
