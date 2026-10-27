package io.github.catlinux.keynexa.update

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the Ed25519 signature of an update manifest.
 *
 * [publicKeyBase64] is the SubjectPublicKeyInfo DER of the key, Base64-encoded (the body of a PEM
 * `PUBLIC KEY`). The key is decoded and built inside [verify], so constructing a verifier never fails.
 */
class ManifestVerifier(
    private val publicKeyBase64: String,
) {
    /**
     * True when [signature] is a valid Ed25519 signature of [manifest] made by the key of this verifier.
     *
     * It returns false on any problem (an invalid Base64 key, a key the JCA rejects, a bad signature) and
     * never throws.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun verify(
        manifest: ByteArray,
        signature: ByteArray,
    ): Boolean {
        if (signature.size != SIGNATURE_LENGTH) return false
        return try {
            val encoded = Base64.getDecoder().decode(publicKeyBase64)
            val key = KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(encoded))
            val verifier = Signature.getInstance(ALGORITHM)
            verifier.initVerify(key)
            verifier.update(manifest)
            verifier.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        private const val ALGORITHM = "Ed25519"
        private const val SIGNATURE_LENGTH = 64
    }
}
