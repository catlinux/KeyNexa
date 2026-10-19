package io.github.catlinux.keynexa.app.ui.settings

import androidx.compose.runtime.Composable
import io.github.catlinux.keynexa.session.CloudBackupSource
import io.github.catlinux.keynexa.sync.SyncLocal

/**
 * The offline variant has no cloud entry at all: this empty function keeps the settings screen free of conditionals
 * while the offline APK carries no cloud code.
 */
@Suppress("EmptyFunctionBlock", "UnusedParameter")
@Composable
fun CloudSettingsEntry(
    source: CloudBackupSource?,
    syncLocal: SyncLocal?,
) {
    // Nothing to show: the offline variant never talks to a network.
}
