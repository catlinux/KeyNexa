package io.github.catlinux.keynexa.app

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

private const val PERSISTABLE_READ_WRITE =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

/** Picks an existing vault file and asks for a read and write grant that survives restarts. */
class OpenVaultDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(
        context: Context,
        input: Array<String>,
    ): Intent = super.createIntent(context, input).addFlags(PERSISTABLE_READ_WRITE)
}

/** Creates an empty vault file and asks for a read and write grant that survives restarts. */
class CreateVaultDocument : ActivityResultContracts.CreateDocument("application/octet-stream") {
    override fun createIntent(
        context: Context,
        input: String,
    ): Intent = super.createIntent(context, input).addFlags(PERSISTABLE_READ_WRITE)
}
