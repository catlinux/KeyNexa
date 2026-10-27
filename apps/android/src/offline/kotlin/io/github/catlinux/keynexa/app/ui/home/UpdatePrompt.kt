package io.github.catlinux.keynexa.app.ui.home

import androidx.compose.runtime.Composable

/**
 * The offline variant never looks for updates: this empty function keeps the home screen free of conditionals while
 * the offline APK carries no network code.
 */
@Suppress("EmptyFunctionBlock")
@Composable
fun UpdatePrompt() {
    // Nothing to show: the offline variant has no network permission.
}
