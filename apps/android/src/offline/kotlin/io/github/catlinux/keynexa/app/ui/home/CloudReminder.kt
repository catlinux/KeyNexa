package io.github.catlinux.keynexa.app.ui.home

import androidx.compose.runtime.Composable

/**
 * The offline variant has no cloud reminder: this empty function keeps the home screen free of conditionals while
 * the offline APK carries no cloud code.
 */
@Suppress("EmptyFunctionBlock", "UnusedParameter")
@Composable
fun CloudReminder(onOpenSettings: () -> Unit) {
    // Nothing to show: the offline variant never talks to a network.
}
