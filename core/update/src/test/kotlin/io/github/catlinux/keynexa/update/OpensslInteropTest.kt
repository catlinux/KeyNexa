package io.github.catlinux.keynexa.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixture was written and signed by the same shell commands the release workflow runs (`openssl pkeyutl -sign
 * -rawin` with a throwaway Ed25519 key), so this proves the app accepts exactly what the workflow publishes.
 */
class OpensslInteropTest {
    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/openssl/$name")) { name }.use { it.readBytes() }

    private val manifest = resource("keynexa-update.txt")
    private val signature = resource("keynexa-update.txt.sig")
    private val testKey = String(resource("test-public-key.txt"), Charsets.US_ASCII).trim()

    @Test
    fun theWorkflowsManifestParses() {
        val parsed = checkNotNull(UpdateManifest.parse(manifest))
        assertEquals(SemVer(9, 8, 7), parsed.version)
        assertEquals(3, parsed.files.size)
        assertNotNull(parsed.file("KeyNexa-windows-x64-9.8.7.msi"))
    }

    @Test
    fun anOpensslSignatureVerifiesWithTheJca() {
        assertTrue(ManifestVerifier(testKey).verify(manifest, signature))
    }

    @Test
    fun theProductionKeyDoesNotAcceptATestSignature() {
        assertFalse(ManifestVerifier(UpdateKeys.PRODUCTION).verify(manifest, signature))
    }
}
