package io.github.catlinux.keynexa.app.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.catlinux.keynexa.vault.Credentials
import java.security.GeneralSecurityException
import javax.crypto.Cipher

/** Why the biometric path ended without opening the vault. */
enum class BiometricProblem {
    /** New biometrics were enrolled or the screen lock was removed: the key is gone. */
    Invalidated,

    /** The stored data could not be used (missing key, corrupted blob). */
    Unusable,

    /** The master password is due again. */
    PasswordDue,

    /** The user cancelled or the prompt failed; the password can be typed. */
    Cancelled,
}

/** Texts of the system prompt. */
class BiometricTexts(
    val title: String,
    val negative: String,
)

/**
 * Biometric unlock of one vault (ADR-0006). What is wrapped are the composite-key components (the hash of the
 * password and of the key file), never the password; the key derivation still runs on each unlock. The decrypted
 * components exist only inside [unlock] and are handed over as [Credentials], which the session wipes. A failed
 * biometric step always ends in the password prompt: the reason is reported as a [BiometricProblem], not rethrown.
 */
@Suppress("TooManyFunctions", "SwallowedException")
class BiometricUnlock(
    private val context: Context,
    private val keys: BiometricKeys,
    private val store: BiometricStore,
    private val passwordDays: () -> Int,
) {
    /** The device has strong biometrics enrolled. */
    fun isSupported(): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun isEnrolled(location: String): Boolean = store.has(BiometricStore.aliasFor(location))

    /** The unlock button may be offered: enrolled, supported, and the password not due. */
    fun canUnlock(location: String): Boolean {
        val alias = BiometricStore.aliasFor(location)
        return isSupported() && store.has(alias) && !store.passwordDue(alias, passwordDays())
    }

    /** Offer to enable it after a password unlock: supported, not enabled, and not declined before. */
    fun shouldOffer(location: String): Boolean {
        val alias = BiometricStore.aliasFor(location)
        return isSupported() && !store.has(alias) && !store.declined(alias)
    }

    fun decline(location: String) = store.markDeclined(BiometricStore.aliasFor(location))

    /** Deletes the key and the stored data; the password is needed from then on. */
    fun remove(location: String) {
        val alias = BiometricStore.aliasFor(location)
        runCatching { keys.delete(alias) }
        store.delete(alias)
    }

    /** Records that the master password was typed (and opened the vault) just now. */
    fun passwordTyped(location: String) {
        val alias = BiometricStore.aliasFor(location)
        if (store.has(alias)) store.markPasswordTyped(alias)
    }

    /**
     * Enables it for the vault whose open credentials are [hashed] (the caller wipes them). Asks for the biometric,
     * then stores the encrypted components. [onDone] receives true on success.
     */
    fun enroll(
        activity: FragmentActivity,
        location: String,
        texts: BiometricTexts,
        hashed: Credentials,
        onDone: (Boolean) -> Unit,
    ) {
        val alias = BiometricStore.aliasFor(location)
        val cipher =
            try {
                keys.encryptCipher(alias)
            } catch (failure: GeneralSecurityException) {
                onDone(false)
                return
            }
        prompt(activity, texts, cipher) { authed ->
            if (authed == null) {
                runCatching { keys.delete(alias) }
                onDone(false)
            } else {
                onDone(storeComponents(alias, authed, hashed))
            }
        }
    }

    /** Asks for the biometric and returns the credentials, or the reason there are none. */
    fun unlock(
        activity: FragmentActivity,
        location: String,
        texts: BiometricTexts,
        onResult: (Credentials?, BiometricProblem?) -> Unit,
    ) {
        val alias = BiometricStore.aliasFor(location)
        val blob = store.load(alias)
        when {
            blob == null -> {
                remove(location)
                onResult(null, BiometricProblem.Unusable)
            }
            store.passwordDue(alias, passwordDays()) -> onResult(null, BiometricProblem.PasswordDue)
            else -> {
                val cipher = decryptCipherOrNull(location, alias, blob, onResult) ?: return
                prompt(activity, texts, cipher) { authed ->
                    if (authed == null) {
                        onResult(null, BiometricProblem.Cancelled)
                    } else {
                        val credentials = openComponents(authed, blob)
                        if (credentials == null) {
                            remove(location)
                            onResult(null, BiometricProblem.Unusable)
                        } else {
                            onResult(credentials, null)
                        }
                    }
                }
            }
        }
    }

    // On failure the key and blob are deleted and the problem is reported; the caller stops.
    private fun decryptCipherOrNull(
        location: String,
        alias: String,
        blob: BiometricBlob,
        onResult: (Credentials?, BiometricProblem?) -> Unit,
    ): Cipher? =
        try {
            keys.decryptCipher(alias, blob.iv)
        } catch (failure: KeyPermanentlyInvalidatedException) {
            remove(location)
            onResult(null, BiometricProblem.Invalidated)
            null
        } catch (failure: GeneralSecurityException) {
            remove(location)
            onResult(null, BiometricProblem.Unusable)
            null
        }

    private fun storeComponents(
        alias: String,
        cipher: Cipher,
        hashed: Credentials,
    ): Boolean {
        val plain = BiometricComponents.encode(hashed.passwordHashCopy(), hashed.keyHashCopy())
        return try {
            val encrypted = cipher.doFinal(plain)
            store.save(alias, BiometricBlob(cipher.iv, encrypted))
            store.markPasswordTyped(alias)
            true
        } catch (failure: GeneralSecurityException) {
            runCatching { keys.delete(alias) }
            false
        } finally {
            plain.fill(0)
        }
    }

    private fun openComponents(
        cipher: Cipher,
        blob: BiometricBlob,
    ): Credentials? {
        val plain =
            try {
                cipher.doFinal(blob.cipherText)
            } catch (failure: GeneralSecurityException) {
                return null
            }
        try {
            return BiometricComponents.decode(plain)
        } finally {
            plain.fill(0)
        }
    }

    private fun prompt(
        activity: FragmentActivity,
        texts: BiometricTexts,
        cipher: Cipher,
        onResult: (Cipher?) -> Unit,
    ) {
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(result.cryptoObject?.cipher)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    onResult(null)
                }
            }
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(texts.title)
                .setNegativeButtonText(texts.negative)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}

/** Plain form of the components that get encrypted: a flags byte, then the 32-byte hashes that are present. */
internal object BiometricComponents {
    private const val HAS_PASSWORD = 1
    private const val HAS_KEY = 2
    private const val HASH_SIZE = 32

    fun encode(
        passwordHash: ByteArray?,
        keyHash: ByteArray?,
    ): ByteArray {
        val flags = (if (passwordHash != null) HAS_PASSWORD else 0) or (if (keyHash != null) HAS_KEY else 0)
        val result = byteArrayOf(flags.toByte()) + (passwordHash ?: ByteArray(0)) + (keyHash ?: ByteArray(0))
        passwordHash?.fill(0)
        keyHash?.fill(0)
        return result
    }

    /** The credentials in [plain], or null when it is malformed. The caller wipes [plain]. */
    fun decode(plain: ByteArray): Credentials? {
        val flags = plain.firstOrNull()?.toInt() ?: return null
        val hasPassword = flags and HAS_PASSWORD != 0
        val hasKey = flags and HAS_KEY != 0
        val expected = 1 + (if (hasPassword) HASH_SIZE else 0) + (if (hasKey) HASH_SIZE else 0)
        if ((!hasPassword && !hasKey) || plain.size != expected) return null
        val password = if (hasPassword) plain.copyOfRange(1, 1 + HASH_SIZE) else null
        val keyStart = if (hasPassword) 1 + HASH_SIZE else 1
        val key = if (hasKey) plain.copyOfRange(keyStart, keyStart + HASH_SIZE) else null
        return Credentials.ofHashes(password, key)
    }
}
