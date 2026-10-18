package io.github.catlinux.keynexa.app.cloud

import android.content.Context
import io.github.catlinux.keynexa.session.KnownVault

/**
 * The offline variant has no cloud backup: this empty hook keeps the session free of conditionals while the offline
 * APK carries no cloud code.
 */
object CloudBackupHook {
    @Suppress("UnusedParameter", "EmptyFunctionBlock")
    fun onSaved(
        context: Context,
        vault: KnownVault,
        read: () -> ByteArray,
    ) {
        // Nothing to do: the offline variant never uploads anything.
    }
}
