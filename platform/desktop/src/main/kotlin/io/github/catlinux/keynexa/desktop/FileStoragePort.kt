package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.vault.FileMetadata
import io.github.catlinux.keynexa.vault.SiblingFile
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** The mark that says a file is ours and half-written; [listSiblings] never reports one as a conflict copy. */
private const val TEMPORARY_SUFFIX = ".keynexa-tmp"

/**
 * [StoragePort] over the local file system: a location is an absolute path.
 *
 * A write goes to a hidden temporary file in the same folder, is forced to disk and is then moved over the target
 * with an atomic move. When the file system has no atomic move, or another program holds the target open (Windows),
 * the bytes are written in place instead; that is safe because the safe-save pipeline keeps private copies and reads
 * the file back before it trusts the result. The temporary file is removed on every path. Only encrypted KDBX bytes
 * ever pass through here, and nothing is logged.
 *
 * @param move how the finished temporary file is put in place; a test passes a failing one to exercise the fallback.
 */
@Suppress("TooManyFunctions")
class FileStoragePort(
    private val move: (from: Path, to: Path) -> Unit = ::moveAtomically,
) : StoragePort {
    override fun read(location: VaultLocation): ByteArray = Files.readAllBytes(pathOf(location))

    override fun metadata(location: VaultLocation): FileMetadata = metadataOf(pathOf(location))

    override fun lastModified(location: VaultLocation): Long? = metadata(location).lastModified?.toEpochMilli()

    override fun name(location: VaultLocation): String = pathOf(location).fileName?.toString().orEmpty()

    override fun delete(location: VaultLocation) {
        Files.delete(pathOf(location))
    }

    override fun listSiblings(location: VaultLocation): List<SiblingFile> {
        val path = pathOf(location)
        val directory = path.parent ?: return emptyList()
        return try {
            Files.newDirectoryStream(directory).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it != path && !it.isTemporary() }
                    .map { sibling ->
                        SiblingFile(
                            VaultLocation(sibling.toString()),
                            sibling.fileName?.toString().orEmpty(),
                            metadataOf(sibling),
                        )
                    }
            }
        } catch (failure: IOException) {
            emptyList()
        }
    }

    override fun write(
        location: VaultLocation,
        data: ByteArray,
    ) {
        val target = pathOf(location)
        target.parent?.let { Files.createDirectories(it) }
        val temporary = target.resolveSibling(".${target.fileName}$TEMPORARY_SUFFIX")
        try {
            writeForced(temporary, data)
            try {
                move(temporary, target)
            } catch (failure: IOException) {
                writeForced(target, data)
            }
        } finally {
            deleteQuietly(temporary)
        }
    }

    private fun metadataOf(path: Path): FileMetadata {
        if (!Files.exists(path)) return FileMetadata(null, null)
        val size =
            try {
                Files.size(path)
            } catch (failure: IOException) {
                null
            }
        val modified =
            try {
                Files.getLastModifiedTime(path).toInstant()
            } catch (failure: IOException) {
                null
            }
        return FileMetadata(size, modified)
    }

    private fun writeForced(
        path: Path,
        data: ByteArray,
    ) {
        FileChannel
            .open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { channel ->
                channel.write(ByteBuffer.wrap(data))
                channel.force(true)
            }
    }

    private fun deleteQuietly(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (failure: IOException) {
            // The write itself is done; a temporary file that cannot be removed is harmless and is not a sibling.
        }
    }

    private fun pathOf(location: VaultLocation): Path = Path.of(location.value)

    private fun Path.isTemporary(): Boolean = fileName?.toString()?.endsWith(TEMPORARY_SUFFIX) == true
}

private fun moveAtomically(
    from: Path,
    to: Path,
) {
    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
