package io.github.catlinux.keynexa.platform

/** Seals and opens a small blob of bytes; [open] answers null when the blob cannot be opened. */
interface SecretBox {
    fun seal(plain: ByteArray): ByteArray

    /** The plain bytes, or null when the key is different, the blob was altered or its format is invalid. */
    fun open(sealed: ByteArray): ByteArray?
}
