package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.FileMetadata
import io.github.catlinux.keynexa.vault.SiblingFile
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictFilesTest {
    private fun sibling(name: String) = SiblingFile(VaultLocation("loc/$name"), name, FileMetadata(null, null))

    private fun names(
        vault: String,
        vararg siblings: String,
    ) = conflictCopiesOf(vault, siblings.map(::sibling)).map { it.name }

    @Test
    fun findsSyncthingConflictCopies() {
        assertEquals(
            listOf("Claus.sync-conflict-20261013-101500-ABCDEFG.kdbx"),
            names(
                "Claus.kdbx",
                "Claus.kdbx",
                "Claus.sync-conflict-20261013-101500-ABCDEFG.kdbx",
                "Other.sync-conflict-20261013-101500-ABCDEFG.kdbx",
                "notes.txt",
            ),
        )
    }

    @Test
    fun newestConflictComesFirst() {
        assertEquals(
            listOf(
                "V.sync-conflict-20261014-080000-BBBBBBB.kdbx",
                "V.sync-conflict-20261013-235959-AAAAAAA.kdbx",
            ),
            names(
                "V.kdbx",
                "V.sync-conflict-20261013-235959-AAAAAAA.kdbx",
                "V.sync-conflict-20261014-080000-BBBBBBB.kdbx",
            ),
        )
    }

    @Test
    fun ignoresLookAlikes() {
        assertEquals(
            emptyList<String>(),
            names(
                "V.kdbx",
                "V.sync-conflict-2026.kdbx",
                "V.sync-conflict-20261013-101500-ABCDEFG.txt",
                "V.sync-conflict-20261013-101500-ABCDEFG.kdbx.bak",
                "V2.sync-conflict-20261013-101500-ABCDEFG.kdbx",
                "V (copy).kdbx",
            ),
        )
    }

    @Test
    fun acceptsConflictWithoutDeviceAndNamesWithoutExtension() {
        assertEquals(
            listOf("V.sync-conflict-20261013-101500.kdbx"),
            names("V.kdbx", "V.sync-conflict-20261013-101500.kdbx"),
        )
        assertEquals(
            listOf("vault.sync-conflict-20261013-101500-ABCDEFG"),
            names("vault", "vault.sync-conflict-20261013-101500-ABCDEFG"),
        )
    }
}
