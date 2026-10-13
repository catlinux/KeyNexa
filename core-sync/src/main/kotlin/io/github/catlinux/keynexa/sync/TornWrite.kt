package io.github.catlinux.keynexa.sync

/**
 * Tells whether [file], which is neither the old version nor the new one, was left torn by KeyNexa's own write
 * or is a complete file written by another program.
 *
 * Every save produces a header with a fresh random master seed, so only a file that KeyNexa started writing can
 * share a long prefix with [outgoing] beyond what it shares with [previous]: two unrelated KDBX files agree on the
 * fixed signature and format fields, then diverge at their random seeds. [MIN_LEAD] bytes of extra agreement make
 * a chance match negligible. No decryption is needed, so the check is fast and does not depend on credentials.
 */
internal fun isTornByOwnWrite(
    file: ByteArray,
    outgoing: ByteArray,
    previous: ByteArray,
): Boolean = commonPrefix(file, outgoing) >= commonPrefix(file, previous) + MIN_LEAD

private const val MIN_LEAD = 16

private fun commonPrefix(
    a: ByteArray,
    b: ByteArray,
): Int {
    val limit = minOf(a.size, b.size)
    var index = 0
    while (index < limit && a[index] == b[index]) index++
    return index
}
