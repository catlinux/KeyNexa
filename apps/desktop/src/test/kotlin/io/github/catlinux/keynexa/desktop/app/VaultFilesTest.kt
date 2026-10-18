package io.github.catlinux.keynexa.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** The names a vault file goes by, and whether a new vault may be written there. */
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

    @Test
    fun appendsTheExtensionToANameWithoutIt() {
        assertEquals("personal.kdbx", vaultFileName("personal"))
    }

    @Test
    fun keepsAnExtensionThatIsAlreadyThere() {
        assertEquals("personal.kdbx", vaultFileName("personal.kdbx"))
    }

    @Test
    fun keepsAnExtensionInAnyCase() {
        assertEquals("personal.KDBX", vaultFileName("personal.KDBX"))
    }

    @Test
    fun trimsTheNameBeforeNamingTheFile() {
        assertEquals("personal.kdbx", vaultFileName("  personal  "))
    }

    @Test
    fun seesAFileThatIsAlreadyThere() {
        val file = Files.createTempFile("keynexa-vault", VAULT_EXTENSION).toFile()
        try {
            assertTrue(vaultFileExists(file))
        } finally {
            Files.deleteIfExists(file.toPath())
        }
    }

    @Test
    fun doesNotSeeAFileThatIsNotThere() {
        val folder = Files.createTempDirectory("keynexa-vault")
        try {
            assertFalse(vaultFileExists(folder.resolve("personal$VAULT_EXTENSION").toFile()))
        } finally {
            Files.deleteIfExists(folder)
        }
    }
}
