package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.vault.Credentials
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Outcome of arming the quick unlock. */
sealed interface ArmResult {
    /** The components are held encrypted until [QuickUnlock.disarm] or the time limit. */
    data object Armed : ArmResult

    /** The user dismissed the Hello prompt; nothing is armed. */
    data object Cancelled : ArmResult

    /** Hello is unavailable or reported an error; [reason] never carries secrets. */
    data class Failed(
        val reason: String,
    ) : ArmResult
}

/** Outcome of opening with the quick unlock. */
sealed interface OpenResult {
    /** The vault components, encrypted in memory until now. The caller owns the credentials and must wipe them. */
    class Opened(
        val credentials: Credentials,
    ) : OpenResult

    /** The user dismissed the Hello prompt; quick unlock stays armed for a retry. */
    data object Cancelled : OpenResult

    /** Hello failed or the held state could not be used; [reason] never carries secrets. */
    data class Failed(
        val reason: String,
    ) : OpenResult
}

/**
 * The pure quick-unlock policy of ADR-0014: it arms the composite-key components of a vault behind a Windows Hello
 * signature, checks the time limit, and opens or disarms. It never touches the disk, the logs or the clipboard.
 *
 * The wrapping key is HKDF-SHA256 over the Hello signature, with the challenge as salt. The components are sealed with
 * AES-256-GCM, and the vault location and arming time go in as associated data. While armed, memory holds only the
 * challenge, the IV, the ciphertext, the location and the arming time.
 *
 * [signer] blocks on the Hello prompt, so every method here may block and must be called off the UI thread. All of them
 * are synchronized: an arming, an opening and a disarming never interleave.
 */
class QuickUnlock(
    private val signer: HelloSigner,
    private val clock: () -> Long,
    private val random: SecureRandom = SecureRandom(),
) {
    private var challenge: ByteArray? = null
    private var iv: ByteArray? = null
    private var ciphertext: ByteArray? = null
    private var location: String? = null
    private var armedAt: Long = 0
    private var hours: Int = 0

    /**
     * Encrypts the hashed components of [components] for [location] and keeps them in memory for [hours].
     *
     * The credentials must come from the vault engine's hashed form; raw secrets are refused. A failed or cancelled
     * arming leaves nothing armed.
     */
    @Synchronized
    fun arm(
        location: String,
        components: Credentials,
        hours: Int,
    ): ArmResult {
        require(hours > 0) { "Invalid quick-unlock hours" }
        disarm()
        val passwordHash = components.passwordHashCopy()
        val keyHash = components.keyHashCopy()
        if (passwordHash == null && keyHash == null) return ArmResult.Failed(MISSING_COMPONENTS)
        val newChallenge = ByteArray(CHALLENGE_SIZE).also { random.nextBytes(it) }
        var signature: ByteArray? = null
        var key: ByteArray? = null
        var plaintext: ByteArray? = null
        var newIv: ByteArray? = null
        var sealed: ByteArray? = null
        var kept = false
        try {
            when (val result = signer.sign(newChallenge)) {
                HelloResult.Cancelled -> return ArmResult.Cancelled
                is HelloResult.Failed -> return ArmResult.Failed(result.reason)
                is HelloResult.Signed -> {
                    val signed = result.bytes
                    signature = signed
                    val derived = Hkdf.sha256(signed, newChallenge, INFO_TEXT.toByteArray(UTF_8), KEY_SIZE)
                    key = derived
                    val packed = pack(passwordHash, keyHash)
                    plaintext = packed
                    val armedTime = clock()
                    val heldIv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
                    newIv = heldIv
                    val heldCiphertext = encrypt(derived, heldIv, packed, associatedData(location, armedTime))
                    sealed = heldCiphertext
                    challenge = newChallenge
                    iv = heldIv
                    ciphertext = heldCiphertext
                    this.location = location
                    armedAt = armedTime
                    this.hours = hours
                    kept = true
                    return ArmResult.Armed
                }
            }
        } finally {
            if (!kept) {
                newChallenge.fill(0)
                newIv?.fill(0)
                sealed?.fill(0)
            }
            signature?.fill(0)
            key?.fill(0)
            plaintext?.fill(0)
            passwordHash?.fill(0)
            keyHash?.fill(0)
        }
    }

    /**
     * True when [location] is armed and the time limit has not passed. The limit is exact: at the millisecond it ends,
     * the quick unlock is no longer valid and is disarmed. Another location answers false without disarming.
     */
    @Synchronized
    fun isArmedFor(location: String): Boolean {
        if (challenge == null) return false
        if (clock() >= armedAt + hours * MILLIS_PER_HOUR) {
            disarm()
            return false
        }
        return this.location == location
    }

    /**
     * Signs the held challenge and returns the components for [location].
     *
     * A cancel keeps everything armed so the user can retry; a Hello failure, an expired or foreign location, a bad
     * tag or missing components disarm the quick unlock. A successful open does not extend the arming time.
     */
    @Synchronized
    fun open(location: String): OpenResult {
        val heldChallenge = challenge ?: return OpenResult.Failed(NOT_ARMED)
        if (!isArmedFor(location)) {
            disarm()
            return OpenResult.Failed(NOT_ARMED)
        }
        val heldIv = iv ?: return OpenResult.Failed(NOT_ARMED)
        val heldCiphertext = ciphertext ?: return OpenResult.Failed(NOT_ARMED)
        val snapshot =
            HeldSnapshot(
                challenge = heldChallenge.copyOf(),
                iv = heldIv.copyOf(),
                ciphertext = heldCiphertext.copyOf(),
                location = location,
                armedAt = armedAt,
            )
        var signature: ByteArray? = null
        try {
            return when (val result = signer.sign(snapshot.challenge)) {
                HelloResult.Cancelled -> OpenResult.Cancelled
                is HelloResult.Failed -> {
                    disarm()
                    OpenResult.Failed(result.reason)
                }
                is HelloResult.Signed -> {
                    signature = result.bytes
                    openSigned(result.bytes, snapshot)
                }
            }
        } finally {
            signature?.fill(0)
            snapshot.challenge.fill(0)
            snapshot.iv.fill(0)
            snapshot.ciphertext.fill(0)
        }
    }

    /** Wipes the challenge, the IV and the ciphertext and forgets everything held. */
    @Synchronized
    fun disarm() {
        challenge?.fill(0)
        iv?.fill(0)
        ciphertext?.fill(0)
        challenge = null
        iv = null
        ciphertext = null
        location = null
        armedAt = 0
        hours = 0
    }

    /**
     * Test-only: the live arrays this object holds, so a test can tamper with the ciphertext and check that [disarm]
     * zeroes them. Never use it outside tests.
     */
    @Synchronized
    internal fun heldStateForTest(): HeldState? {
        val heldChallenge = challenge ?: return null
        val heldIv = iv ?: return null
        val heldCiphertext = ciphertext ?: return null
        return HeldState(heldChallenge, heldIv, heldCiphertext)
    }

    /** Test-only view of the arrays [QuickUnlock] holds while armed. */
    internal class HeldState(
        val challenge: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    /** Derives the key from [signature] and decrypts the snapshot; every failure disarms. */
    private fun openSigned(
        signature: ByteArray,
        snapshot: HeldSnapshot,
    ): OpenResult {
        var key: ByteArray? = null
        var plaintext: ByteArray? = null
        try {
            val derived = Hkdf.sha256(signature, snapshot.challenge, INFO_TEXT.toByteArray(UTF_8), KEY_SIZE)
            key = derived
            val decrypted =
                decrypt(derived, snapshot.iv, snapshot.ciphertext, associatedData(snapshot.location, snapshot.armedAt))
            if (decrypted == null) {
                disarm()
                return OpenResult.Failed(DECRYPTION_FAILED)
            }
            plaintext = decrypted
            val (passwordHash, keyHash) = unpack(decrypted)
            if (passwordHash == null && keyHash == null) {
                disarm()
                return OpenResult.Failed(MISSING_COMPONENTS)
            }
            return OpenResult.Opened(Credentials.ofHashes(passwordHash, keyHash))
        } finally {
            key?.fill(0)
            plaintext?.fill(0)
        }
    }
}

/** The state copied out under the lock for an open, so the signer and the decryption work on stable arrays. */
private class HeldSnapshot(
    val challenge: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val location: String,
    val armedAt: Long,
)

private const val CHALLENGE_SIZE = 32
private const val KEY_SIZE = 32
private const val IV_SIZE = 12
private const val TAG_BITS = 128
private const val HASH_SIZE = 32
private const val FLAG_PASSWORD = 1
private const val FLAG_KEY_FILE = 2
private const val BYTE_MASK = 0xFF
private const val MILLIS_PER_HOUR = 3_600_000L
private const val KEY_ALGORITHM = "AES"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val INFO_TEXT = "KeyNexa quick unlock v1"
private const val NOT_ARMED = "not armed"
private const val MISSING_COMPONENTS = "missing components"
private const val DECRYPTION_FAILED = "decryption failed"

/** The plaintext layout: one flags byte, then the password hash and the key-file hash when their flag is set. */
private fun pack(
    passwordHash: ByteArray?,
    keyHash: ByteArray?,
): ByteArray {
    var flags = 0
    if (passwordHash != null) flags = flags or FLAG_PASSWORD
    if (keyHash != null) flags = flags or FLAG_KEY_FILE
    val packed = ByteArray(1 + (passwordHash?.size ?: 0) + (keyHash?.size ?: 0))
    packed[0] = flags.toByte()
    var offset = 1
    if (passwordHash != null) {
        passwordHash.copyInto(packed, offset)
        offset += passwordHash.size
    }
    if (keyHash != null) keyHash.copyInto(packed, offset)
    return packed
}

private fun unpack(plaintext: ByteArray): Pair<ByteArray?, ByteArray?> {
    if (plaintext.isEmpty()) return null to null
    val flags = plaintext[0].toInt() and BYTE_MASK
    var offset = 1
    val passwordHash =
        if (flags and FLAG_PASSWORD != 0) {
            unpackComponent(plaintext, offset)
        } else {
            null
        }
    if (passwordHash != null) offset += HASH_SIZE
    val keyHash =
        if (flags and FLAG_KEY_FILE != 0) {
            unpackComponent(plaintext, offset)
        } else {
            null
        }
    return passwordHash to keyHash
}

/** A 32-byte component, or null when the plaintext is too short to hold one. */
private fun unpackComponent(
    plaintext: ByteArray,
    offset: Int,
): ByteArray? =
    if (offset + HASH_SIZE <= plaintext.size) {
        plaintext.copyOfRange(offset, offset + HASH_SIZE)
    } else {
        null
    }

private fun encrypt(
    key: ByteArray,
    iv: ByteArray,
    plaintext: ByteArray,
    associatedData: ByteArray,
): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
    cipher.updateAAD(associatedData)
    return cipher.doFinal(plaintext)
}

/** The decrypted plaintext, or null when the tag does not verify or the parameters are unusable. */
@Suppress("SwallowedException")
private fun decrypt(
    key: ByteArray,
    iv: ByteArray,
    ciphertext: ByteArray,
    associatedData: ByteArray,
): ByteArray? =
    try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        cipher.doFinal(ciphertext)
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

/** The vault location and the arming time, so a ciphertext cannot move to another vault or time. */
private fun associatedData(
    location: String,
    armedAt: Long,
): ByteArray {
    val locationBytes = location.toByteArray(UTF_8)
    val buffer = ByteBuffer.allocate(locationBytes.size + Long.SIZE_BYTES)
    buffer.put(locationBytes)
    buffer.putLong(armedAt)
    return buffer.array()
}
