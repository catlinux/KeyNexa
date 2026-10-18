package io.github.catlinux.keynexa.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Picks the folder that holds a vault and asks for a read and write grant that survives restarts. A folder grant,
 * unlike one on a single file, is not revoked when a sync tool replaces the file inside it.
 */
class OpenVaultFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(
        context: Context,
        input: Uri?,
    ): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}
