package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.platform.SecretBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** The cloud folder: credentials, consent, reminder and backup state. */
class DesktopCloudStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theCredentialsSurviveTheRoundTripAndClear() {
        val stores = stores()
        val values = setupValues()

        stores.credentials.save(values)
        assertEquals(values, stores.credentials.load())

        stores.credentials.clear()
        assertNull(stores.credentials.load())
    }

    @Test
    fun theCredentialsAreSealedByTheBox() {
        val stores = stores()
        stores.credentials.save(setupValues())

        val bytes = Files.readAllBytes(stores.path.resolve("credentials.bin"))
        assertTrue(String(bytes, Charsets.UTF_8).startsWith("sealed:"))

        val other = DesktopCloudStores(dirs(), OtherBox())
        other.credentials.save(setupValues())
        assertNull(other.credentials.load())
    }

    @Test
    fun theConsentIsAcceptedAndRevoked() {
        val stores = stores()

        assertFalse(stores.consent.accepted())

        stores.consent.accept()
        assertTrue(stores.consent.accepted())

        stores.consent.revoke()
        assertFalse(stores.consent.accepted())
    }

    @Test
    fun anOlderConsentVersionIsNotAccepted() {
        val stores = stores()
        Files.createDirectories(stores.path)
        Files.write(stores.path.resolve("consent"), "0".toByteArray())

        assertFalse(stores.consent.accepted())
    }

    @Test
    fun theReminderIsSnoozedUntilAMoment() {
        val stores = stores()

        assertNull(stores.reminder.snoozedUntil())

        stores.reminder.snooze(UNTIL)
        assertEquals(UNTIL, stores.reminder.snoozedUntil())
    }

    @Test
    fun garbageInTheReminderIsIgnored() {
        val stores = stores()
        Files.createDirectories(stores.path)
        Files.write(stores.path.resolve("reminder"), "not a moment".toByteArray())

        assertNull(stores.reminder.snoozedUntil())
    }

    @Test
    fun theBackupStateIsKeptInTheCloudFolder() {
        val stores = stores()

        stores.backupState.recordSuccess("vault", UNTIL)
        assertTrue(stores.hasAnyBackupRecord())

        assertTrue(Files.isRegularFile(stores.path.resolve("backup-state.properties")))
    }

    private fun stores(): DesktopCloudStores = DesktopCloudStores(dirs(), TestBox())

    private fun dirs(): DesktopDirs =
        object : DesktopDirs {
            override val data: Path = folder.newFolder().toPath()
        }

    private fun setupValues(): Map<String, String> =
        S3Setup(
            endpoint = "https://s3.example.org",
            region = "eu-west-1",
            bucket = "keynexa",
            pathStyle = true,
            accessKeyId = "access",
            secretAccessKey = "secret",
        ).toValues()

    private fun DesktopCloudStores.hasAnyBackupRecord(): Boolean = backupState.hasAnySuccess()

    private companion object {
        val UNTIL: Instant = Instant.parse("2026-10-18T20:00:00Z")
    }
}

/** A [SecretBox] for the tests: it marks what it seals and opens only its own marks. */
private class TestBox : SecretBox {
    override fun seal(plain: ByteArray): ByteArray = MARKER + plain

    override fun open(sealed: ByteArray): ByteArray? =
        if (sealed.size >= MARKER.size && sealed.copyOfRange(0, MARKER.size).contentEquals(MARKER)) {
            sealed.copyOfRange(MARKER.size, sealed.size)
        } else {
            null
        }

    private companion object {
        val MARKER = "sealed:".toByteArray()
    }
}

/** A [SecretBox] that marks what it seals differently, so nothing the other one wrote can be opened. */
private class OtherBox : SecretBox {
    override fun seal(plain: ByteArray): ByteArray = OTHER_MARKER + plain

    override fun open(sealed: ByteArray): ByteArray? = null

    private companion object {
        val OTHER_MARKER = "other:".toByteArray()
    }
}
