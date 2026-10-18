package io.github.catlinux.keynexa.desktop.app

import java.io.File

/** The extension of a KDBX vault file: the name shown for it and the file dialog filter both use it. */
internal const val VAULT_EXTENSION = ".kdbx"

/** The name to show for a vault file: its file name without the `.kdbx` extension. */
internal fun vaultNameOf(fileName: String): String = fileName.removeSuffix(VAULT_EXTENSION).ifBlank { fileName }

/** The file name a new vault is saved as: [name] with the `.kdbx` extension, unless it already ends with it. */
internal fun vaultFileName(name: String): String {
    val trimmed = name.trim()
    return if (trimmed.endsWith(VAULT_EXTENSION, ignoreCase = true)) trimmed else trimmed + VAULT_EXTENSION
}

/** True when a file already sits at [file]; a new vault never overwrites one. */
internal fun vaultFileExists(file: File): Boolean = file.exists()
