package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.session.KnownVault

/** Why the Windows Hello unlock could not be used, for a line on the unlock screen. */
enum class QuickNotice {
    /** The master password changed elsewhere, so the held components no longer open the vault. */
    PasswordChanged,

    /** Hello failed or the held state could not be used; the quick unlock was turned off. */
    Unavailable,
}

/** Where the unlock that is in progress came from; only a master-password unlock may arm the quick unlock. */
enum class UnlockSource { None, Password, Hello }

/** What the unlock screen needs to offer the Windows Hello unlock. */
class QuickUnlockUi(
    val available: Boolean = false,
    val notice: QuickNotice? = null,
    val onQuickUnlock: (KnownVault) -> Unit = {},
    val onPasswordUnlock: () -> Unit = {},
)
