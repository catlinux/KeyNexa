package io.github.catlinux.keynexa.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Keystore keys of the biometric unlock (ADR-0006): one AES-256-GCM key per vault that can only be used right
 * after a strong biometric authentication, is invalidated when new biometrics are enrolled, and never leaves the
 * secure hardware. The ciphers returned are not yet usable: they go into a `BiometricPrompt.CryptoObject`.
 */
class BiometricKeys(
    private val context: Context,
) {
    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    /** A cipher ready to encrypt, with a brand-new key that replaces any previous one for [alias]. */
    fun encryptCipher(alias: String): Cipher {
        delete(alias)
        val key = createKey(alias)
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /**
     * A cipher ready to decrypt with [iv].
     *
     * @throws android.security.keystore.KeyPermanentlyInvalidatedException if new biometrics were enrolled or the
     *   screen lock was removed.
     * @throws java.security.GeneralSecurityException if the key is missing or unusable.
     */
    fun decryptCipher(
        alias: String,
        iv: ByteArray,
    ): Cipher {
        val key = keyStore.getKey(alias, null) as? SecretKey ?: throw KeyStoreException("no key")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
    }

    fun delete(alias: String) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    // StrongBox when the device has it; a device that advertises it but fails falls back to the TEE.
    @Suppress("SwallowedException")
    private fun createKey(alias: String): SecretKey {
        val strongBox =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        return try {
            generate(alias, strongBox)
        } catch (unavailable: ProviderException) {
            // StrongBoxUnavailableException (API 28+) is a ProviderException; it cannot be named on API 26-27.
            if (!strongBox) throw unavailable
            generate(alias, false)
        }
    }

    private fun generate(
        alias: String,
        strongBox: Boolean,
    ): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    } else {
                        // -1 means authentication for every single use.
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setUnlockedDeviceRequired(true)
                        setIsStrongBoxBacked(strongBox)
                    }
                }.build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
