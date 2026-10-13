package io.github.catlinux.keynexa.kdbx

import app.keemobile.kotpass.cryptography.format.BaseCiphers
import app.keemobile.kotpass.cryptography.format.CipherProvider
import app.keemobile.kotpass.cryptography.format.TwofishCipher
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.header.DatabaseHeader
import app.keemobile.kotpass.database.header.KdfParameters
import app.keemobile.kotpass.database.header.VariantItem
import app.keemobile.kotpass.database.readHeader
import io.github.catlinux.keynexa.vault.Argon2Variant
import io.github.catlinux.keynexa.vault.Cipher
import io.github.catlinux.keynexa.vault.Compression
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.KdbxEngine
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.VariantValue
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultFormat
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

private const val SEED_SIZE = 32
private const val SIGNATURE_SIZE = 8
private const val MESSAGE_NOT_A_VAULT = "The data is not a KDBX file."
private const val MESSAGE_UNSUPPORTED_FORMAT = "This vault format cannot be written."

// First two little-endian words of every KDBX file.
private const val SIGNATURE_BASE = 0x9AA2D903L
private const val SIGNATURE_SECONDARY = 0xB54BFB67L
private const val SECONDARY_OFFSET = 4

/**
 * KDBX engine backed by the vendored kotpass. It is stateless, so one instance can be shared: every call works on
 * its own buffers and keeps no credentials or decrypted content between calls.
 *
 * Twofish is always offered on top of the library defaults: files written by KeePassXC with it must still open.
 */
class KotpassKdbxEngine : KdbxEngine {
    override fun inspect(data: ByteArray): VaultFormat =
        translateFailures {
            requireKdbxSignature(data)
            data.inputStream().use { stream -> KeePassDatabase.readHeader(stream).toVaultFormat() }
        }

    override fun decode(
        data: ByteArray,
        credentials: Credentials,
    ): Vault =
        translateFailures {
            requireKdbxSignature(data)
            val kotpassCredentials = credentials.toKotpass()

            data.inputStream().use { stream ->
                KeePassDatabase.decode(stream, kotpassCredentials, cipherProviders = CIPHERS).toVault()
            }
        }

    override fun encode(
        vault: Vault,
        credentials: Credentials,
    ): ByteArray =
        translateFailures {
            val kotpassCredentials = credentials.toKotpass()
            val database = vault.toKeePassDatabase(kotpassCredentials)

            ByteArrayOutputStream().use { output ->
                database.encode(output, cipherProviders = CIPHERS)
                output.toByteArray()
            }
        }

    private companion object {
        val CIPHERS: List<CipherProvider> = BaseCiphers.entries + TwofishCipher
    }
}

// Checked before the library parses anything, so empty or foreign data is "not a vault" and not "damaged".
private fun requireKdbxSignature(data: ByteArray) {
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    val matches =
        data.size >= SIGNATURE_SIZE &&
            Integer.toUnsignedLong(buffer.getInt(0)) == SIGNATURE_BASE &&
            Integer.toUnsignedLong(buffer.getInt(SECONDARY_OFFSET)) == SIGNATURE_SECONDARY
    if (!matches) throw VaultException.NotAVault(MESSAGE_NOT_A_VAULT)
}

// Every failure that escapes the library means the vault could not be read or written, so it is mapped here.
private inline fun <T> translateFailures(block: () -> T): T =
    try {
        block()
    } catch (expected: Exception) {
        throw expected.toVaultException()
    }

internal fun Cipher.toKotpassCipher(): CipherProvider =
    when (this) {
        Cipher.Aes256 -> BaseCiphers.Aes
        Cipher.ChaCha20 -> BaseCiphers.ChaCha20
        Cipher.Twofish256 -> TwofishCipher
    }

internal fun Compression.toKotpassCompression(): DatabaseHeader.Compression =
    when (this) {
        Compression.None -> DatabaseHeader.Compression.None
        Compression.Gzip -> DatabaseHeader.Compression.GZip
    }

internal fun Kdf.toAesRounds(): ULong =
    when (this) {
        is Kdf.AesKdf -> rounds.toULong()
        is Kdf.Argon2 -> throw VaultException.UnsupportedFormat(MESSAGE_UNSUPPORTED_FORMAT)
    }

internal fun Kdf.toKdfParameters(random: SecureRandom): KdfParameters =
    when (this) {
        is Kdf.AesKdf ->
            KdfParameters.Aes(
                rounds = rounds.toULong(),
                seed = randomByteString(SEED_SIZE, random),
            )
        is Kdf.Argon2 ->
            KdfParameters.Argon2(
                variant =
                    when (variant) {
                        Argon2Variant.Argon2d -> KdfParameters.Argon2.Variant.Argon2d
                        Argon2Variant.Argon2id -> KdfParameters.Argon2.Variant.Argon2id
                    },
                salt = randomByteString(SEED_SIZE, random),
                parallelism = parallelism.toUInt(),
                memory = memoryBytes.toULong(),
                iterations = iterations.toULong(),
                version = version.toUInt(),
                secretKey = null,
                associatedData = null,
            )
    }

internal fun VariantValue.toKotpassVariant(): VariantItem =
    when (this) {
        is VariantValue.Bool -> VariantItem.Bool(value)
        is VariantValue.Int32 -> VariantItem.Int32(value)
        is VariantValue.Int64 -> VariantItem.Int64(value)
        is VariantValue.UInt32 -> VariantItem.UInt32(value.toUInt())
        is VariantValue.UInt64 -> VariantItem.UInt64(value.toULong())
        is VariantValue.Text -> VariantItem.StringUtf8(value)
        is VariantValue.Bytes -> VariantItem.Bytes(value.toByteString())
    }

internal fun randomByteString(
    size: Int,
    random: SecureRandom,
): ByteString = ByteArray(size).also { random.nextBytes(it) }.toByteString()
