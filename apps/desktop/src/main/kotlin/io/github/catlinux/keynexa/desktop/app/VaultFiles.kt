package io.github.catlinux.keynexa.desktop.app

/** The extension of a KDBX vault file: the name shown for it and the file dialog filter both use it. */
internal const val VAULT_EXTENSION = ".kdbx"

/** The name to show for a vault file: its file name without the `.kdbx` extension. */
internal fun vaultNameOf(fileName: String): String = fileName.removeSuffix(VAULT_EXTENSION).ifBlank { fileName }
