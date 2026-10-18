package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

/** The TOTP core: the RFC 6238 vectors, the two entry formats, and everything that must answer null. */
class TotpTest {
    @Test
    fun theRfc6238VectorsMatch() {
        val sha1 = TotpConfig(ascii(SHA1_SECRET), TotpAlgorithm.SHA1, digits = 8)
        val sha256 = TotpConfig(ascii(SHA256_SECRET), TotpAlgorithm.SHA256, digits = 8)
        val sha512 = TotpConfig(ascii(SHA512_SECRET), TotpAlgorithm.SHA512, digits = 8)

        assertEquals("94287082", sha1.codeAt(Instant.ofEpochSecond(59)).code)
        assertEquals("46119246", sha256.codeAt(Instant.ofEpochSecond(59)).code)
        assertEquals("90693936", sha512.codeAt(Instant.ofEpochSecond(59)).code)

        assertEquals("07081804", sha1.codeAt(Instant.ofEpochSecond(1_111_111_109)).code)
        assertEquals("68084774", sha256.codeAt(Instant.ofEpochSecond(1_111_111_109)).code)
        assertEquals("25091201", sha512.codeAt(Instant.ofEpochSecond(1_111_111_109)).code)

        assertEquals("14050471", sha1.codeAt(Instant.ofEpochSecond(1_111_111_111)).code)
        assertEquals("67062674", sha256.codeAt(Instant.ofEpochSecond(1_111_111_111)).code)
        assertEquals("99943326", sha512.codeAt(Instant.ofEpochSecond(1_111_111_111)).code)

        assertEquals("89005924", sha1.codeAt(Instant.ofEpochSecond(1_234_567_890)).code)
        assertEquals("91819424", sha256.codeAt(Instant.ofEpochSecond(1_234_567_890)).code)
        assertEquals("93441116", sha512.codeAt(Instant.ofEpochSecond(1_234_567_890)).code)

        assertEquals("69279037", sha1.codeAt(Instant.ofEpochSecond(2_000_000_000)).code)
        assertEquals("90698825", sha256.codeAt(Instant.ofEpochSecond(2_000_000_000)).code)
        assertEquals("38618901", sha512.codeAt(Instant.ofEpochSecond(2_000_000_000)).code)

        assertEquals("65353130", sha1.codeAt(Instant.ofEpochSecond(20_000_000_000)).code)
        assertEquals("77737706", sha256.codeAt(Instant.ofEpochSecond(20_000_000_000)).code)
        assertEquals("47863826", sha512.codeAt(Instant.ofEpochSecond(20_000_000_000)).code)
    }

    @Test
    fun theSecondsRemainingStayWithinThePeriod() {
        val config = TotpConfig(ascii(SHA1_SECRET), digits = 6)

        assertEquals(1, config.codeAt(Instant.ofEpochSecond(59)).secondsRemaining)
        assertEquals(30, config.codeAt(Instant.ofEpochSecond(60)).secondsRemaining)
        assertEquals(30, config.codeAt(Instant.ofEpochSecond(0)).secondsRemaining)
    }

    @Test
    fun aKeePassXcUriIsReadWithItsParameters() {
        val entry =
            entry("otp" to FULL_URI)

        val config = requireNotNull(entry.totp())

        assertEquals(TotpAlgorithm.SHA256, config.algorithm)
        assertEquals(8, config.digits)
        assertEquals(60, config.period)
        assertEquals("Example", config.issuer)
    }

    @Test
    fun aKeePassXcUriWithoutParametersUsesTheDefaults() {
        val config = requireNotNull(entry("otp" to PLAIN_URI).totp())

        assertEquals(TotpAlgorithm.SHA1, config.algorithm)
        assertEquals(6, config.digits)
        assertEquals(30, config.period)
        assertNull(config.issuer)
    }

    @Test
    fun theSecretIsReadIgnoringCaseSpacesHyphensAndPadding() {
        val padded = requireNotNull(entry("otp" to "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP").totp())
        val spaced = requireNotNull(entry("otp" to "otpauth://totp/x?secret=jbsw-y3dp%20ehpk3pxp").totp())
        val withPadding = requireNotNull(entry("otp" to "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP====").totp())

        assertEquals(padded.secret.toList(), spaced.secret.toList())
        assertEquals(padded.secret.toList(), withPadding.secret.toList())
    }

    @Test
    fun aPercentEncodedLabelDoesNotBreakTheUri() {
        val config = requireNotNull(entry("otp" to ENCODED_LABEL_URI).totp())

        assertEquals(6, config.digits)
    }

    @Test
    fun theKeeTrayTotpFieldsAreRead() {
        val withSettings = entry("TOTP Seed" to "JBSWY3DPEHPK3PXP", "TOTP Settings" to "60;8")
        val withoutSettings = entry("TOTP Seed" to "JBSWY3DPEHPK3PXP")

        val configured = requireNotNull(withSettings.totp())
        assertEquals(60, configured.period)
        assertEquals(8, configured.digits)
        assertEquals(TotpAlgorithm.SHA1, configured.algorithm)

        val defaults = requireNotNull(withoutSettings.totp())
        assertEquals(30, defaults.period)
        assertEquals(6, defaults.digits)
    }

    @Test
    fun theOtpUriHasPriorityOverTheKeeTrayFields() {
        val entry =
            entry(
                "otp" to "steam://open/1234",
                "TOTP Seed" to "JBSWY3DPEHPK3PXP",
            )

        assertNull(entry.totp())
    }

    @Test
    fun entriesWithoutAUsableConfigurationAnswerNull() {
        assertNull(entry().totp())
        assertNull(entry("otp" to "steam://open/1234").totp())
        assertNull(entry("otp" to "otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1").totp())
        assertNull(entry("otp" to "otpauth://totp/x?secret=").totp())
        assertNull(entry("otp" to "otpauth://totp/x?secret=!!!!!!!!").totp())
        assertNull(entry("otp" to "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&issuer=%zz").totp())
        assertNull(entry("otp" to "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=5").totp())
        assertNull(entry("otp" to "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=9").totp())
        assertNull(entry("otp" to "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&period=0").totp())
        assertNull(entry("otp" to "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&algorithm=MD5").totp())
        assertNull(entry("TOTP Seed" to "not base32!").totp())
        assertNull(entry("TOTP Seed" to "JBSWY3DPEHPK3PXP", "TOTP Settings" to "0;6").totp())
        assertNull(entry("TOTP Seed" to "JBSWY3DPEHPK3PXP", "TOTP Settings" to "30;9").totp())
        assertNull(entry("TOTP Seed" to "JBSWY3DPEHPK3PXP", "TOTP Settings" to "abc;6").totp())
    }

    @Test
    fun nothingSecretIsPrinted() {
        val config = TotpConfig(ascii(SHA1_SECRET), issuer = "Example")
        val code = config.codeAt(Instant.ofEpochSecond(59))

        assertTrue(config.toString().contains("secret=redacted"))
        assertFalse(config.toString().contains("1234567890"))
        assertTrue(code.toString().contains("code=redacted"))
        assertFalse(code.toString().contains(code.code))
    }

    @Test
    fun configurationsWithTheSameValuesAreEqual() {
        val one = TotpConfig(ascii(SHA1_SECRET), TotpAlgorithm.SHA1, 6, 30, "Example")
        val other = TotpConfig(ascii(SHA1_SECRET), TotpAlgorithm.SHA1, 6, 30, "Example")

        assertEquals(one, other)
        assertEquals(one.hashCode(), other.hashCode())
        assertNotNull(one)
    }

    private fun entry(vararg fields: Pair<String, String>): Entry =
        Entry(
            id = UUID.randomUUID(),
            fields = fields.associate { (name, value) -> name to FieldValue(value) },
        )

    private fun ascii(text: String): ByteArray = text.toByteArray(StandardCharsets.US_ASCII)

    private companion object {
        const val SHA1_SECRET = "12345678901234567890"
        const val SHA256_SECRET = "12345678901234567890123456789012"
        const val SHA512_SECRET = "1234567890123456789012345678901234567890123456789012345678901234"
        const val PLAIN_URI = "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP"
        const val FULL_URI = PLAIN_URI + "&issuer=Example&period=60&digits=8&algorithm=SHA256"
        const val ENCODED_LABEL_URI = "otpauth://totp/Example%3Aalice%40example.com?secret=JBSWY3DPEHPK3PXP"
    }
}
