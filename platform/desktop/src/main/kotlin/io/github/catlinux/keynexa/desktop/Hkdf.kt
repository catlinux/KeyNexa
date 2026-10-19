package io.github.catlinux.keynexa.desktop

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 (RFC 5869), the only hand-built cryptography here and only on top of the JCA's `HmacSHA256`.
 *
 * The quick unlock derives its wrapping key from the Windows Hello signature, which is not a uniform random secret,
 * so the signature is the input keying material and the challenge is the salt.
 */
internal object Hkdf {
    private const val ALGORITHM = "HmacSHA256"
    private const val HASH_SIZE = 32
    private const val MAX_BLOCKS = 255
    private const val MAX_LENGTH = MAX_BLOCKS * HASH_SIZE

    /** The first [length] bytes of the RFC 5869 output for [ikm], [salt] and [info]; an empty salt means 32 zeroes. */
    fun sha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length in 1..MAX_LENGTH) { "Invalid HKDF length" }
        // An all-zero salt is what the RFC prescribes for an absent one.
        val zeroSalt = if (salt.isEmpty()) ByteArray(HASH_SIZE) else null
        val prk = ByteArray(HASH_SIZE)
        val previous = ByteArray(HASH_SIZE)
        try {
            extract(zeroSalt ?: salt, ikm, prk)
            val output = ByteArray(length)
            expand(prk, info, output, previous)
            return output
        } finally {
            zeroSalt?.fill(0)
            prk.fill(0)
            previous.fill(0)
        }
    }

    /** RFC 5869 section 2.2: PRK = HMAC-Hash(salt, IKM). */
    private fun extract(
        salt: ByteArray,
        ikm: ByteArray,
        prk: ByteArray,
    ) {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(salt, ALGORITHM))
        val extracted = mac.doFinal(ikm)
        try {
            extracted.copyInto(prk)
        } finally {
            extracted.fill(0)
        }
    }

    /** RFC 5869 section 2.3: T(i) = HMAC-Hash(PRK, T(i-1) | info | i), copied into [output] until it is full. */
    private fun expand(
        prk: ByteArray,
        info: ByteArray,
        output: ByteArray,
        previous: ByteArray,
    ) {
        var offset = 0
        var counter = 1
        while (offset < output.size) {
            val mac = Mac.getInstance(ALGORITHM)
            mac.init(SecretKeySpec(prk, ALGORITHM))
            // T(0) is empty, so the first block has no previous block in front of it.
            if (counter > 1) mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            val block = mac.doFinal()
            try {
                val take = minOf(block.size, output.size - offset)
                block.copyInto(output, offset, 0, take)
                offset += take
                block.copyInto(previous)
            } finally {
                block.fill(0)
            }
            counter++
        }
    }
}
