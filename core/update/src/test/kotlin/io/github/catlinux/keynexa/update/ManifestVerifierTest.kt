package io.github.catlinux.keynexa.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class ManifestVerifierTest {
    private val sha = "a".repeat(64)
    private val manifest =
        "keynexa-update 1\nversion 1.2.3\nreleased 2026-10-27\nfile KeyNexa.msi 1024 $sha\n"
            .toByteArray(Charsets.UTF_8)

    @Test
    fun acceptsTheSignatureOfItsOwnKey() {
        val keyPair = freshKeyPair()
        assertTrue(verifierFor(keyPair).verify(manifest, sign(keyPair, manifest)))
    }

    @Test
    fun rejectsAModifiedManifest() {
        val keyPair = freshKeyPair()
        val signature = sign(keyPair, manifest)
        val modified = manifest.copyOf().also { it[it.size - 2] = 'b'.code.toByte() }

        assertFalse(verifierFor(keyPair).verify(modified, signature))
    }

    @Test
    fun rejectsASignatureFromAnotherKey() {
        val signingPair = freshKeyPair()
        val otherPair = freshKeyPair()

        assertFalse(verifierFor(otherPair).verify(manifest, sign(signingPair, manifest)))
    }

    @Test
    fun rejectsATruncatedSignature() {
        val keyPair = freshKeyPair()
        val signature = sign(keyPair, manifest).copyOf(63)

        assertFalse(verifierFor(keyPair).verify(manifest, signature))
    }

    @Test
    fun rejectsAnInvalidBase64Key() {
        val keyPair = freshKeyPair()

        assertFalse(ManifestVerifier("not a public key!").verify(manifest, sign(keyPair, manifest)))
        assertFalse(ManifestVerifier("").verify(manifest, sign(keyPair, manifest)))
    }

    @Test
    fun acceptsTheProductionKeyWithoutParsingItYet() {
        val keyPair = freshKeyPair()
        val signature = sign(keyPair, manifest)

        assertFalse(ManifestVerifier(UpdateKeys.PRODUCTION).verify(manifest, signature))
    }

    private fun freshKeyPair(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private fun verifierFor(keyPair: KeyPair): ManifestVerifier =
        ManifestVerifier(Base64.getEncoder().encodeToString(keyPair.public.encoded))

    private fun sign(
        keyPair: KeyPair,
        bytes: ByteArray,
    ): ByteArray =
        Signature.getInstance("Ed25519").run {
            initSign(keyPair.private)
            update(bytes)
            sign()
        }
}
