package io.github.catlinux.keynexa.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

class FileCheckTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val content = "KeyNexa test payload".toByteArray(Charsets.UTF_8)
    private val sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))

    @Test
    fun matchesWhenSizeAndHashAgree() {
        assertTrue(FileCheck.matches(write(), content.size.toLong(), sha256))
    }

    @Test
    fun rejectsADifferentSize() {
        assertFalse(FileCheck.matches(write(), content.size + 1L, sha256))
    }

    @Test
    fun rejectsADifferentHash() {
        assertFalse(FileCheck.matches(write(), content.size.toLong(), "0".repeat(64)))
    }

    @Test
    fun rejectsAMissingFile() {
        assertFalse(FileCheck.matches(folder.root.toPath().resolve("missing.msi"), 1L, sha256))
    }

    private fun write(): Path {
        val path = folder.newFile("KeyNexa.msi").toPath()
        Files.write(path, content)
        return path
    }
}
