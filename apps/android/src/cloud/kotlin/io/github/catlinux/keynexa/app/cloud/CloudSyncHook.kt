package io.github.catlinux.keynexa.app.cloud

import android.content.Context
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.sync.SyncLocal

/** The cloud variant connects a completed save and the app's triggers to the background synchronisation queue. */
object CloudSyncHook {
    fun onSaved(
        context: Context,
        vault: KnownVault,
        local: SyncLocal,
    ) {
        CloudSyncService.onSaved(context, vault, local)
    }

    fun onTrigger(
        context: Context,
        vault: KnownVault,
        local: SyncLocal,
    ) {
        CloudSyncService.onTrigger(context, vault, local)
    }
}
