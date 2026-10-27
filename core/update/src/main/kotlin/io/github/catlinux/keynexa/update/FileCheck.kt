package io.github.catlinux.keynexa.update

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Checks a downloaded file against the size and SHA-256 an [UpdateManifest] lists, before installing it.
 */
object FileCheck {
    private const val BLOCK_SIZE = 64 * 1024
    private const val SHA256_ALGORITHM = "SHA-256"

    /**
     * True when [path] is a readable file of exactly [size] bytes whose SHA-256 is [sha256] (lowercase
     * hexadecimal).
     *
     * The size is compared first, so a truncated or padded download fails before it is read, and the file is
     * hashed in blocks to keep memory use constant. Any read error or a missing file gives false.
     */
    fun matches(
        path: Path,
        size: Long,
        sha256: String,
    ): Boolean =
        try {
            Files.size(path) == size && sha256Of(path) == sha256
        } catch (e: IOException) {
            false
        }

    private fun sha256Of(path: Path): String {
        val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(BLOCK_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        // Not java.util.HexFormat: Android only has it from API 34, and the app supports API 26.
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
