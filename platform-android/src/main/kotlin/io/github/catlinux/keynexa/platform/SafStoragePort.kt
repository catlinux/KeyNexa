package io.github.catlinux.keynexa.platform

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.catlinux.keynexa.vault.FileMetadata
import io.github.catlinux.keynexa.vault.SiblingFile
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant

/**
 * [StoragePort] over the Storage Access Framework. A location is a document URI, either inside a granted folder
 * (tree) or a single granted file. Every provider failure, including a revoked permission, surfaces as
 * [IOException] because that is the contract the core relies on.
 */
class SafStoragePort(
    context: Context,
) : StoragePort {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun read(location: VaultLocation): ByteArray =
        guarded {
            val stream = resolver.openInputStream(uriOf(location)) ?: throw IOException("provider gave no stream")
            stream.use { it.readBytes() }
        }

    override fun metadata(location: VaultLocation): FileMetadata =
        guarded {
            val projection =
                arrayOf(DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val cursor = resolver.query(uriOf(location), projection, null, null, null)
            cursor?.use {
                if (!it.moveToFirst()) throw FileNotFoundException("document not found")
                FileMetadata(it.longOrNull(0), it.longOrNull(1)?.let(Instant::ofEpochMilli))
            } ?: throw IOException("provider gave no metadata")
        }

    override fun listSiblings(location: VaultLocation): List<SiblingFile> =
        try {
            guarded { siblings(uriOf(location)) }
        } catch (failure: IOException) {
            emptyList()
        }

    override fun write(
        location: VaultLocation,
        data: ByteArray,
    ) {
        guarded {
            val uri = uriOf(location)
            val descriptor = openForWrite(uri)
            descriptor.use {
                FileOutputStream(it.fileDescriptor).use { output ->
                    val channel = output.channel
                    channel.position(0)
                    output.write(data)
                    // Some providers ignore the truncation implied by "rwt", which would leave the tail of a longer
                    // old file behind; cut explicitly at the end of what was written.
                    channel.truncate(data.size.toLong())
                    output.fd.sync()
                }
            }
        }
    }

    private fun openForWrite(uri: Uri) =
        try {
            resolver.openFileDescriptor(uri, "rwt")
        } catch (ignored: FileNotFoundException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        } catch (ignored: UnsupportedOperationException) {
            null
        } ?: resolver.openFileDescriptor(uri, "rw") ?: throw IOException("provider gave no descriptor")

    private fun siblings(uri: Uri): List<SiblingFile> {
        if (!DocumentsContract.isTreeUri(uri)) return emptyList()
        val authority = uri.authority ?: return emptyList()
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val documentId = DocumentsContract.getDocumentId(uri)
        val parentId = parentIdOf(uri, documentId) ?: return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUriOf(authority, treeId), parentId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
        val result = mutableListOf<SiblingFile>()
        resolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                if (id == documentId || name == null) continue
                val child = DocumentsContract.buildDocumentUriUsingTree(treeUriOf(authority, treeId), id)
                val metadata =
                    FileMetadata(
                        cursor.longOrNull(COL_SIZE),
                        cursor.longOrNull(COL_MODIFIED)?.let(Instant::ofEpochMilli),
                    )
                result += SiblingFile(VaultLocation(child.toString()), name, metadata)
            }
        }
        return result
    }

    private fun parentIdOf(
        uri: Uri,
        documentId: String,
    ): String? {
        val path = DocumentsContract.findDocumentPath(resolver, uri)?.path
        path?.dropLast(1)?.lastOrNull()?.let { return it }
        // Providers without findDocumentPath that use "<root>:<relative/path>" ids (the external storage provider).
        return documentId.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
    }

    private fun treeUriOf(
        authority: String,
        treeId: String,
    ): Uri = DocumentsContract.buildTreeDocumentUri(authority, treeId)

    private fun uriOf(location: VaultLocation): Uri = Uri.parse(location.value)

    /** Providers report a missing document, a revoked grant or a dead process in many ways; the core expects one. */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> guarded(block: () -> T): T =
        try {
            block()
        } catch (failure: IOException) {
            throw failure
        } catch (failure: RuntimeException) {
            throw IOException("storage provider failure: ${failure.javaClass.simpleName}", failure)
        }
}

private const val COL_SIZE = 2
private const val COL_MODIFIED = 3

private fun android.database.Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
