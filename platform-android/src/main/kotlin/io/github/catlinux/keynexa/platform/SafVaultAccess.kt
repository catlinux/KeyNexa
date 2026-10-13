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
 * The grants behind a vault location. A folder grant is preferred: the vault is found by name inside it, so it
 * survives Syncthing replacing the file. A single-file grant is revoked when the file is replaced, and then
 * [isReachable] turns false and the UI offers to reconnect the file.
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

    /** The document called [fileName] directly inside the granted folder [tree], or null if it is not there. */
    fun locateInFolder(
        tree: Uri,
        fileName: String,
    ): VaultLocation? {
        val children =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
        val projection =
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val documentId =
            try {
                resolver.query(children, projection, null, null, null)?.use { findId(it, fileName) }
            } catch (ignored: SecurityException) {
                null
            } catch (ignored: IllegalArgumentException) {
                null
            }
        return documentId?.let { VaultLocation(DocumentsContract.buildDocumentUriUsingTree(tree, it).toString()) }
    }

    private fun findId(
        cursor: Cursor,
        fileName: String,
    ): String? {
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == fileName) return cursor.getString(0)
        }
        return null
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
        const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
