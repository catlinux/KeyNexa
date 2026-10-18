package io.github.catlinux.keynexa.app.cloud

import android.content.Context
import io.github.catlinux.keynexa.session.KnownVault

/** The cloud variant connects a completed save to the background backup queue. */
object CloudBackupHook {
    fun onSaved(
        context: Context,
        vault: KnownVault,
        read: () -> ByteArray,
    ) {
        CloudBackupService.onSaved(context, vault, read)
    }
}
