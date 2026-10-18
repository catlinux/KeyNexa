package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** The local file system storage: round trips, truncation, the temporary file and the in-place fallback. */
class FileStoragePortTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val port = FileStoragePort()

    private val root: Path get() = folder.root.toPath()

    private fun location(name: String = VAULT_NAME): VaultLocation = VaultLocation(root.resolve(name).toString())

    @Test
    fun writeAndReadRoundTrip() {
        val location = location()

        port.write(location, byteArrayOf(1, 2, 3))

        assertArrayEquals(byteArrayOf(1, 2, 3), port.read(location))
        assertEquals(VAULT_NAME, port.name(location))
        assertEquals(3L, port.metadata(location).sizeBytes)
    }

    @Test
    fun writeReplacesALongerFileCompletely() {
        val location = location()
        port.write(location, ByteArray(100) { it.toByte() })

        port.write(location, byteArrayOf(9))

        assertArrayEquals(byteArrayOf(9), port.read(location))
        assertEquals(1L, port.metadata(location).sizeBytes)
    }

    @Test
    fun writeLeavesNoTemporaryFile() {
        port.write(location(), byteArrayOf(1))

        assertTrue(temporaryFiles().isEmpty())
    }

    @Test
    fun listSiblingsLeavesOutTheFileAndTemporaryFiles() {
        val location = location()
        port.write(location, byteArrayOf(1))
        port.write(location("other.kdbx"), byteArrayOf(2))
        Files.write(root.resolve(".vault.kdbx.keynexa-tmp"), byteArrayOf(3))

        val siblings = port.listSiblings(location)

        assertEquals(listOf("other.kdbx"), siblings.map { it.name })
    }

    @Test
    fun deleteOfAMissingFileThrows() {
        assertThrows(IOException::class.java) { port.delete(location("missing.kdbx")) }
    }

    @Test
    fun aFailingMoveFallsBackToWritingInPlace() {
        val port = FileStoragePort { _, _ -> throw IOException("no atomic move") }

        port.write(location(), byteArrayOf(7, 8))

        assertArrayEquals(byteArrayOf(7, 8), port.read(location()))
        assertTrue(temporaryFiles().isEmpty())
    }

    private fun temporaryFiles(): List<Path> =
        Files.newDirectoryStream(root).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".keynexa-tmp") }
        }

    private companion object {
        const val VAULT_NAME = "vault.kdbx"
    }
}
