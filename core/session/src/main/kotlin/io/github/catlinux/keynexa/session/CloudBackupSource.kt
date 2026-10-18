package io.github.catlinux.keynexa.session

/**
 * The open vault, as the cloud settings need it: the vault and a way to read its encrypted bytes.
 *
 * It carries nothing of the cloud itself, so the common settings screen can pass it to either flavor.
 */
data class CloudBackupSource(
    val vault: KnownVault,
    val read: () -> ByteArray,
)
