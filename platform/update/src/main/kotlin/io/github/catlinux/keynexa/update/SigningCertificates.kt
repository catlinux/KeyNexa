package io.github.catlinux.keynexa.update

import java.security.MessageDigest

/**
 * The comparison that decides whether a downloaded APK may replace the installed app (ADR-0015).
 *
 * Android checks the signature again when it installs, but KeyNexa refuses first: a package signed by anyone other
 * than the signer of the installed app is never handed to the system installer. Pure on purpose, so it is tested
 * without a device; the Android code only collects the certificate bytes.
 */
object SigningCertificates {
    /**
     * True when [downloaded] and [installed] hold the same signing certificates, compared by SHA-256.
     *
     * An empty side is never equal to anything, so an unsigned package or one whose certificates could not be read
     * does not pass. The order does not matter, and a package with more or fewer signers than the other does not
     * pass either.
     */
    fun sameSigners(
        downloaded: Collection<ByteArray>,
        installed: Collection<ByteArray>,
    ): Boolean {
        if (downloaded.isEmpty() || installed.isEmpty()) return false
        return fingerprints(downloaded) == fingerprints(installed)
    }

    private fun fingerprints(certificates: Collection<ByteArray>): Set<String> =
        certificates
            .map { certificate ->
                MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }
            }.toSet()
}
