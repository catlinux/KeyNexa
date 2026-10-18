@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.vault

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val OTP_FIELD = "otp"
private const val TOTP_SEED_FIELD = "TOTP Seed"
private const val TOTP_SETTINGS_FIELD = "TOTP Settings"

internal const val TOTP_DEFAULT_PERIOD = 30
internal const val TOTP_DEFAULT_DIGITS = 6
private const val MIN_DIGITS = 6
private const val MAX_DIGITS = 8
private const val SETTINGS_SEPARATOR = ';'
private const val PARAMETER_SEPARATOR = '&'
private const val NAME_SEPARATOR = '='
private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
private const val BASE32_BITS = 5
private const val BYTE_BITS = 8
private const val COUNTER_BYTES = 8
private const val BYTE_MASK = 0xFF
private const val LOW_NIBBLE_MASK = 0x0F
private const val SIGN_BIT_MASK = 0x7F
private const val SHIFT_24 = 24
private const val SHIFT_16 = 16
private const val SHIFT_8 = 8
private const val LAST_TRUNCATION_INDEX = 3
private const val DECIMAL_BASE = 10

/** The HMAC hash RFC 6238 allows; the enum name is also the value used in an `otpauth` URI. */
enum class TotpAlgorithm(
    internal val macName: String,
) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512"),
}

/** A TOTP code and how long it stays valid. Its [toString] never prints the code. */
data class TotpCode(
    val code: String,
    val secondsRemaining: Int,
    val period: Int,
) {
    override fun toString(): String = "TotpCode(code=redacted, secondsRemaining=$secondsRemaining, period=$period)"
}

/**
 * The TOTP settings of an entry: the shared secret and how to derive the code. The secret is never printed.
 */
class TotpConfig(
    val secret: ByteArray,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = TOTP_DEFAULT_DIGITS,
    val period: Int = TOTP_DEFAULT_PERIOD,
    val issuer: String? = null,
) {
    override fun toString(): String =
        "TotpConfig(algorithm=$algorithm, digits=$digits, period=$period, issuer=$issuer, secret=redacted)"

    override fun equals(other: Any?): Boolean =
        other is TotpConfig &&
            secret.contentEquals(other.secret) &&
            algorithm == other.algorithm &&
            digits == other.digits &&
            period == other.period &&
            issuer == other.issuer

    override fun hashCode(): Int {
        var result = secret.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + digits
        result = 31 * result + period
        result = 31 * result + (issuer?.hashCode() ?: 0)
        return result
    }

    /** The code for [instant], with leading zeros and the seconds it stays valid (1 to [period]). */
    fun codeAt(instant: Instant): TotpCode {
        val seconds = instant.epochSecond
        val counter = seconds / period
        val hash = hmac(counter)
        val offset = hash.last().toInt() and LOW_NIBBLE_MASK
        val binary =
            ((hash[offset].toInt() and SIGN_BIT_MASK) shl SHIFT_24) or
                ((hash[offset + 1].toInt() and BYTE_MASK) shl SHIFT_16) or
                ((hash[offset + 2].toInt() and BYTE_MASK) shl SHIFT_8) or
                (hash[offset + LAST_TRUNCATION_INDEX].toInt() and BYTE_MASK)
        val code = (binary % decimalModulus(digits)).toString().padStart(digits, '0')
        val elapsed = (seconds % period).toInt()
        val secondsRemaining = if (elapsed == 0) period else period - elapsed
        return TotpCode(code, secondsRemaining, period)
    }

    private fun hmac(counter: Long): ByteArray {
        val mac = Mac.getInstance(algorithm.macName)
        mac.init(SecretKeySpec(secret, algorithm.macName))
        return mac.doFinal(counterBytes(counter))
    }

    private fun counterBytes(counter: Long): ByteArray {
        val bytes = ByteArray(COUNTER_BYTES)
        var value = counter
        for (index in bytes.indices.reversed()) {
            bytes[index] = (value and BYTE_MASK.toLong()).toByte()
            value = value ushr BYTE_BITS
        }
        return bytes
    }

    private fun decimalModulus(digits: Int): Int {
        var modulus = 1
        repeat(digits) { modulus *= DECIMAL_BASE }
        return modulus
    }
}

/**
 * The TOTP configuration of this entry, or null when it has none this code understands.
 *
 * A KeePassXC `otp` field (an `otpauth://totp/...` URI) takes priority; otherwise the KeeTrayTOTP fields
 * (`TOTP Seed` and, optionally, `TOTP Settings`). Anything else (another URI scheme, HOTP, a bad secret or an
 * out-of-range parameter) answers null and never throws.
 */
fun Entry.totp(): TotpConfig? {
    fields[OTP_FIELD]?.value?.let { return parseOtpAuth(it) }
    val seed = fields[TOTP_SEED_FIELD]?.value ?: return null
    return parseKeeTrayTotp(seed, fields[TOTP_SETTINGS_FIELD]?.value)
}

@Suppress("CyclomaticComplexMethod", "ReturnCount", "SwallowedException")
private fun parseOtpAuth(value: String): TotpConfig? {
    val uri =
        try {
            URI(value.trim())
        } catch (failure: URISyntaxException) {
            return null
        }
    if (!uri.scheme.equals(OTPAUTH_SCHEME, ignoreCase = true)) return null
    if (!uri.host.equals(OTPAUTH_TOTP, ignoreCase = true)) return null
    val parameters = queryParameters(uri.rawQuery)
    val secret = parameters[SECRET_PARAMETER]?.let(::base32Decode)?.takeIf { it.isNotEmpty() } ?: return null
    val period =
        parameters[PERIOD_PARAMETER]?.let { text ->
            text.toIntOrNull() ?: return null
        } ?: TOTP_DEFAULT_PERIOD
    if (period <= 0) return null
    val digits =
        parameters[DIGITS_PARAMETER]?.let { text ->
            text.toIntOrNull() ?: return null
        } ?: TOTP_DEFAULT_DIGITS
    if (digits !in MIN_DIGITS..MAX_DIGITS) return null
    val algorithm =
        parameters[ALGORITHM_PARAMETER]?.let { text ->
            parseAlgorithm(text) ?: return null
        } ?: TotpAlgorithm.SHA1
    val issuer = parameters[ISSUER_PARAMETER]?.takeIf { it.isNotBlank() }
    return TotpConfig(secret, algorithm, digits, period, issuer)
}

private fun parseKeeTrayTotp(
    seed: String,
    settings: String?,
): TotpConfig? {
    val secret = base32Decode(seed)?.takeIf { it.isNotEmpty() } ?: return null
    var period = TOTP_DEFAULT_PERIOD
    var digits = TOTP_DEFAULT_DIGITS
    if (!settings.isNullOrBlank()) {
        val parts = settings.split(SETTINGS_SEPARATOR)
        parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            period = text.toIntOrNull() ?: return null
        }
        parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            digits = text.toIntOrNull() ?: return null
        }
        if (period <= 0 || digits !in MIN_DIGITS..MAX_DIGITS) return null
    }
    return TotpConfig(secret, TotpAlgorithm.SHA1, digits, period)
}

private fun queryParameters(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrEmpty()) return emptyMap()
    val result = LinkedHashMap<String, String>()
    rawQuery.split(PARAMETER_SEPARATOR).forEach { part ->
        val separator = part.indexOf(NAME_SEPARATOR)
        if (separator < 0) return@forEach
        val name = decodeParameter(part.substring(0, separator))
        if (name.isEmpty() || result.containsKey(name)) return@forEach
        result[name] = decodeParameter(part.substring(separator + 1))
    }
    return result
}

// A malformed percent escape keeps the text as written: parsing never throws.
@Suppress("SwallowedException")
private fun decodeParameter(value: String): String =
    try {
        URLDecoder.decode(value, StandardCharsets.UTF_8)
    } catch (failure: IllegalArgumentException) {
        value
    }

private fun parseAlgorithm(value: String): TotpAlgorithm? =
    TotpAlgorithm.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

/** RFC 4648 Base32: letters are case-insensitive, spaces and hyphens are ignored, padding is optional. */
private fun base32Decode(text: String): ByteArray? {
    val cleaned = text.filter { !it.isWhitespace() && it != BASE32_SEPARATOR }
    if (cleaned.isEmpty()) return null
    val out = ByteArrayOutputStream()
    var buffer = 0
    var bits = 0
    for (character in cleaned) {
        if (character == BASE32_PADDING) continue
        val index = BASE32_ALPHABET.indexOf(character.uppercaseChar())
        if (index < 0) return null
        buffer = (buffer shl BASE32_BITS) or index
        bits += BASE32_BITS
        if (bits >= BYTE_BITS) {
            bits -= BYTE_BITS
            out.write((buffer shr bits) and BYTE_MASK)
            buffer = buffer and ((1 shl bits) - 1)
        }
    }
    return out.toByteArray()
}

private const val OTPAUTH_SCHEME = "otpauth"
private const val OTPAUTH_TOTP = "totp"
private const val SECRET_PARAMETER = "secret"
private const val PERIOD_PARAMETER = "period"
private const val DIGITS_PARAMETER = "digits"
private const val ALGORITHM_PARAMETER = "algorithm"
private const val ISSUER_PARAMETER = "issuer"
private const val BASE32_SEPARATOR = '-'
private const val BASE32_PADDING = '='
