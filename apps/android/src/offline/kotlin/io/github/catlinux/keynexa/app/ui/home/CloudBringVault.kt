package io.github.catlinux.keynexa.app.ui.home

import android.net.Uri
import androidx.compose.runtime.Composable

/**
 * The offline variant has no way to bring a vault from the cloud: this empty function keeps the home screen free of
 * conditionals while the offline APK carries no cloud code.
 */
@Suppress("EmptyFunctionBlock", "UnusedParameter")
@Composable
fun CloudBringVault(
    isKnownVault: (String) -> Boolean,
    onSaved: (Uri) -> Unit,
) {
    // Nothing to show: the offline variant never talks to a network.
}
