package io.github.catlinux.keynexa.platform

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.IOException

/**
 * The grants behind a vault location. Vaults are added through a folder grant: the files are listed and created
 * inside it, so the location survives Syncthing replacing the file (Android revokes the grant on a single file in
 * that case). When the grant is lost anyway, [isReachable] turns false and the UI offers to reconnect the vault.
 */
class SafVaultAccess(
    context: Context,
    private val storage: StoragePort,
) {
    private val resolver = context.applicationContext.contentResolver

    /** Keeps the grant of a folder returned by `ACTION_OPEN_DOCUMENT_TREE` across restarts. */
    fun persistFolder(tree: Uri) = persist(tree)

    /** Keeps the grant of a file returned by `ACTION_OPEN_DOCUMENT` or `ACTION_CREATE_DOCUMENT`. */
    fun persistFile(document: Uri): VaultLocation {
        persist(document)
        return VaultLocation(document.toString())
    }

    /** A vault file found inside a granted folder. */
    data class VaultFile(
        val name: String,
        val location: VaultLocation,
    )

    /** The `.kdbx` files directly inside the granted folder [tree]; empty when it cannot be listed. */
    fun listVaultFiles(tree: Uri): List<VaultFile> {
        val folderId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val projection =
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val found = mutableListOf<VaultFile>()
        try {
            resolver.query(children, projection, null, null, null)?.use { collect(tree, it, found) }
        } catch (ignored: SecurityException) {
            return emptyList()
        } catch (ignored: IllegalArgumentException) {
            return emptyList()
        }
        return found.sortedBy { it.name.lowercase() }
    }

    private fun collect(
        tree: Uri,
        cursor: Cursor,
        into: MutableList<VaultFile>,
    ) {
        while (cursor.moveToNext()) {
            val name = cursor.getString(1) ?: continue
            if (name.endsWith(VAULT_EXTENSION, ignoreCase = true)) {
                val document = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                into += VaultFile(name, VaultLocation(document.toString()))
            }
        }
    }

    /**
     * Creates an empty file called [fileName] inside the granted folder [tree]. Because the grant belongs to the
     * folder, the location stays valid when a sync tool later replaces the file. Null when the folder refuses.
     */
    fun createInFolder(
        tree: Uri,
        fileName: String,
    ): VaultLocation? {
        val parent =
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val created =
            try {
                DocumentsContract.createDocument(resolver, parent, VAULT_MIME_TYPE, fileName)
            } catch (ignored: SecurityException) {
                null
            } catch (ignored: IllegalArgumentException) {
                null
            } catch (ignored: java.io.FileNotFoundException) {
                null
            }
        return created?.let { VaultLocation(it.toString()) }
    }

    /** Whether the vault can still be read with the current grants; false means "reconnect the file". */
    fun isReachable(location: VaultLocation): Boolean =
        try {
            storage.metadata(location)
            true
        } catch (ignored: IOException) {
            false
        }

    /** Releases a grant that is no longer needed, for example after the user removes the vault from the app. */
    fun release(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, GRANT_FLAGS)
        } catch (ignored: SecurityException) {
            // Already gone: nothing to release.
        }
    }

    private fun persist(uri: Uri) {
        resolver.takePersistableUriPermission(uri, GRANT_FLAGS)
    }

    private companion object {
        const val VAULT_EXTENSION = ".kdbx"
        const val VAULT_MIME_TYPE = "application/octet-stream"
        const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
