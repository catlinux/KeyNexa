package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NEW_VAULT_NAME = "Personal"
private const val MEBIBYTE = 1024L * 1024L

class NewVaultTest {
    @Test
    fun createsAnEmptyVaultNamedAsAsked() {
        val vault = newVault().create(NEW_VAULT_NAME)

        assertEquals(NEW_VAULT_NAME, vault.meta.name)
        assertEquals(NEW_VAULT_NAME, vault.root.name)
        assertTrue(vault.root.entries.isEmpty())
        assertEquals(listOf(RECYCLE_BIN_GROUP_NAME), vault.root.groups.map { it.name })
        assertTrue(vault.deletedObjects.isEmpty())
    }

    @Test
    fun writesKdbx41WithChaCha20AndArgon2id() {
        val vault = newVault().create(NEW_VAULT_NAME)

        assertEquals(FormatVersion(KDBX_MAJOR, KDBX_MINOR), vault.format.version)
        assertEquals(Cipher.ChaCha20, vault.format.cipher)
        assertEquals(NewVault.defaultKdf(), vault.format.kdf)
        val kdf = vault.format.kdf as Kdf.Argon2
        assertEquals(Argon2Variant.Argon2id, kdf.variant)
        assertEquals(DEFAULT_MEMORY_MIB, kdf.memoryMiB)
        assertEquals(DEFAULT_ITERATIONS, kdf.iterations)
        assertEquals(DEFAULT_PARALLELISM, kdf.parallelism)
        assertEquals(ARGON2_VERSION, kdf.version)
    }

    @Test
    fun enablesTheRecycleBinWithItsGroupInPlace() {
        val vault = newVault().create(NEW_VAULT_NAME)

        val binId = requireNotNull(vault.meta.recycleBinGroup)
        val bin = requireNotNull(vault.root.findGroup(binId))
        assertTrue(vault.meta.recycleBinEnabled)
        assertEquals(EDITED_AT, vault.meta.recycleBinChanged)
        assertEquals(RECYCLE_BIN_GROUP_NAME, bin.name)
        assertEquals(RECYCLE_BIN_ICON_ID, bin.icon.standardId)
        assertEquals(EDITED_AT, bin.times.created)
        assertEquals(vault.root.id, vault.root.parentGroupOf(binId))
    }

    @Test
    fun signsTheFileAsWrittenByKeyNexa() {
        assertEquals("KeyNexa", newVault().create(NEW_VAULT_NAME).meta.generator)
    }

    @Test
    fun letsTheKeyDerivationParametersBeReplaced() {
        val slower =
            Kdf.Argon2(
                variant = Argon2Variant.Argon2d,
                memoryBytes = CUSTOM_MEMORY_MIB * MEBIBYTE,
                iterations = CUSTOM_ITERATIONS,
                parallelism = CUSTOM_PARALLELISM,
                version = ARGON2_VERSION,
            )

        val vault = newVault().create(VAULT_NAME, kdf = slower)

        assertEquals(slower, vault.format.kdf)
    }

    @Test
    fun aBrandNewVaultCanBeEditedAtOnce() {
        val ids = TestIds()
        val clock = TestClock()
        val vault = NewVault(clock, ids).create(NEW_VAULT_NAME)
        val editor = VaultEditor(clock, ids)
        val binId = requireNotNull(vault.meta.recycleBinGroup)

        val withEntry = editor.addEntry(vault, vault.root.id, "First entry")
        val entry = withEntry.root.entries.single()
        val entryId = entry.id
        val deleted = editor.delete(withEntry, entryId)

        // The recycle bin of the new vault is used as it is: no second bin appears.
        assertEquals(binId, deleted.meta.recycleBinGroup)
        assertEquals(binId, deleted.root.parentGroupOf(entryId))
        assertEquals(1, deleted.root.allGroupIds().count { it == binId })
        assertTrue(deleted.deletedObjects.isEmpty())

        val restored = editor.restore(deleted, entryId)
        assertNotNull(restored.root.findEntry(entryId))
        assertEquals(vault.root.id, restored.root.parentGroupOf(entryId))
    }

    private fun newVault(): NewVault = NewVault(TestClock(), TestIds())

    private companion object {
        const val KDBX_MAJOR = 4
        const val KDBX_MINOR = 1
        const val DEFAULT_MEMORY_MIB = 64L
        const val DEFAULT_ITERATIONS = 3L
        const val DEFAULT_PARALLELISM = 2
        const val ARGON2_VERSION = 19
        const val CUSTOM_MEMORY_MIB = 8L
        const val CUSTOM_ITERATIONS = 7L
        const val CUSTOM_PARALLELISM = 1
    }
}
