package io.github.catlinux.keynexa.app.cloud

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.catlinux.keynexa.cloud.AesGcmSecretBox
import io.github.catlinux.keynexa.cloud.SecretBox
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * A [SecretBox] whose AES-256-GCM key lives in the Android Keystore and never leaves it.
 *
 * The key is created on first use, with no user authentication required so background uploads can run. If the
 * Keystore cannot be read, [open] answers null and [seal] fails with a plain [IOException] that carries no data.
 */
class KeystoreSecretBox(
    private val alias: String = CLOUD_KEY_ALIAS,
) : SecretBox {
    override fun seal(plain: ByteArray): ByteArray =
        try {
            AesGcmSecretBox(key()).seal(plain)
        } catch (failure: GeneralSecurityException) {
            throw IOException("the cloud credentials could not be encrypted", failure)
        } catch (failure: IOException) {
            throw IOException("the cloud credentials could not be encrypted", failure)
        }

    @Suppress("SwallowedException")
    override fun open(sealed: ByteArray): ByteArray? =
        try {
            keyOrNull()?.let { AesGcmSecretBox(it).open(sealed) }
        } catch (failure: GeneralSecurityException) {
            null
        } catch (failure: IOException) {
            null
        }

    private fun keyOrNull(): SecretKey? {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun key(): SecretKey {
        keyOrNull()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
    }
}
