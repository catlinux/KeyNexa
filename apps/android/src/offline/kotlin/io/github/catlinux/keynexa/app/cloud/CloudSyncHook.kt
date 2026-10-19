package io.github.catlinux.keynexa.app.cloud

import android.content.Context
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.sync.SyncLocal

/**
 * The offline variant has no cloud synchronisation: these empty hooks keep the session and the screens free of
 * conditionals while the offline APK carries no cloud code.
 */
object CloudSyncHook {
    @Suppress("UnusedParameter", "EmptyFunctionBlock")
    fun onSaved(
        context: Context,
        vault: KnownVault,
        local: SyncLocal,
    ) {
        // Nothing to do: the offline variant never talks to a network.
    }

    @Suppress("UnusedParameter", "EmptyFunctionBlock")
    fun onTrigger(
        context: Context,
        vault: KnownVault,
        local: SyncLocal,
    ) {
        // Nothing to do: the offline variant never talks to a network.
    }
}
