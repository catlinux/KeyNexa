package io.github.catlinux.keynexa.app.ui.group

import androidx.compose.runtime.Composable

/**
 * The offline variant has no cloud synchronisation: this empty function keeps the group menu free of conditionals
 * while the offline APK carries no cloud code.
 */
@Suppress("EmptyFunctionBlock", "UnusedParameter")
@Composable
fun CloudSyncNowMenuItem(onSyncNow: () -> Unit) {
    // Nothing to show: the offline variant never talks to a network.
}
