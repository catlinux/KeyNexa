package io.github.catlinux.keynexa.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.time.Instant
import java.util.Base64

/** [UpdateService]: the signed manifest, the schedule, the installer checks and the cleanup of old downloads. */
class UpdateServiceTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val keyPair: KeyPair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair()
    private val verifier = ManifestVerifier(Base64.getEncoder().encodeToString(keyPair.public.encoded))

    @Test
    fun aNewerVersionIsOfferedWithItsInstaller() {
        val content = MSI_CONTENT
        val file = msiFile(NEW_VERSION, content)
        val manifest = manifestText(NEW_VERSION, listOf(file))
        val service = service(fake(manifest, sign(manifest)))

        val result = service.check()

        val available = result as UpdateCheck.Available
        assertEquals(SemVer.parse(NEW_VERSION), available.manifest.version)
        assertEquals(file, available.installer)
    }

    @Test
    fun theRunningVersionIsNotOffered() {
        val result = checkVersion(CURRENT_TEXT)

        assertEquals(UpdateCheck.UpToDate, result)
    }

    @Test
    fun anOlderVersionIsNotOffered() {
        val result = checkVersion(OLDER_VERSION)

        assertEquals(UpdateCheck.UpToDate, result)
    }

    @Test
    fun aBadSignatureIsFailedAndTheCheckIsNotRecorded() {
        val state = state()
        state.recordCheck(LAST_WEEK)
        val manifest = manifestText(NEW_VERSION, listOf(msiFile(NEW_VERSION, MSI_CONTENT)))

        val result = service(fake(manifest, ByteArray(SIGNATURE_BYTES)), state).check(force = true)

        assertEquals(SIGNATURE, (result as UpdateCheck.Failed).reason)
        assertEquals(LAST_WEEK, state.lastCheck())
    }

    @Test
    fun aManifestChangedAfterSigningIsFailed() {
        val signed = manifestText(NEW_VERSION, listOf(msiFile(NEW_VERSION, MSI_CONTENT)))
        val signature = sign(signed)
        val changed = manifestText(FORGED_VERSION, listOf(msiFile(FORGED_VERSION, MSI_CONTENT)))

        val result = service(fake(changed, signature)).check()

        assertEquals(SIGNATURE, (result as UpdateCheck.Failed).reason)
    }

    @Test
    fun aSignedManifestOlderThanOneAlreadySeenIsARollback() {
        val state = state()
        val newest = manifestText(HIGHEST_VERSION, listOf(msiFile(HIGHEST_VERSION, MSI_CONTENT)))
        assertTrue(service(fake(newest, sign(newest)), state).check() is UpdateCheck.Available)
        val stale = manifestText(NEW_VERSION, listOf(msiFile(NEW_VERSION, MSI_CONTENT)))

        val result = service(fake(stale, sign(stale)), state).check(force = true)

        assertEquals("rollback", (result as UpdateCheck.Failed).reason)
    }

    @Test
    fun aCheckWithinADayIsNotDueUnlessForced() {
        val state = state()
        state.recordCheck(NOW.minusSeconds(ONE_HOUR_SECONDS))
        val manifest = manifestText(NEW_VERSION, listOf(msiFile(NEW_VERSION, MSI_CONTENT)))
        val fetcher = fake(manifest, sign(manifest))
        val service = service(fetcher, state)

        assertEquals(UpdateCheck.NotDue, service.check())
        assertTrue(service.check(force = true) is UpdateCheck.Available)
    }

    @Test
    fun aManifestWithoutTheInstallerIsFailed() {
        val other = ManifestFile("KeyNexa-windows-x64-$NEW_VERSION.zip", MSI_CONTENT.size.toLong(), sha256(MSI_CONTENT))
        val manifest = manifestText(NEW_VERSION, listOf(other))

        val result = service(fake(manifest, sign(manifest))).check()

        assertEquals(NO_INSTALLER, (result as UpdateCheck.Failed).reason)
    }

    @Test
    fun downloadGivesTheCheckedInstaller() {
        val file = msiFile(NEW_VERSION, MSI_CONTENT)
        val download = service(fake(download = MSI_CONTENT)).download(available(NEW_VERSION, file))

        val ready = download as UpdateDownload.Ready
        assertArrayEquals(MSI_CONTENT, Files.readAllBytes(ready.path))
    }

    @Test
    fun downloadWithAWrongHashIsFailedAndLeavesNoFile() {
        val file = msiFile(NEW_VERSION, MSI_CONTENT)
        val other = "a forged installer".toByteArray()
        assertEquals(MSI_CONTENT.size, other.size)

        val download = service(fake(download = other)).download(available(NEW_VERSION, file))

        assertEquals(CHECKSUM, (download as UpdateDownload.Failed).reason)
        assertTrue(
            Files
                .newDirectoryStream(downloads())
                .use { entries -> entries.none { it.fileName.toString().endsWith(MSI) } },
        )
    }

    @Test
    fun downloadWithAWrongSizeIsFailedAndLeavesNoFile() {
        val file = msiFile(NEW_VERSION, MSI_CONTENT)
        val short = MSI_CONTENT.copyOf(MSI_CONTENT.size - 1)

        val download = service(fake(download = short)).download(available(NEW_VERSION, file))

        assertEquals(CHECKSUM, (download as UpdateDownload.Failed).reason)
        assertFalse(Files.exists(downloads().resolve(file.name)))
    }

    @Test
    fun downloadRemovesTheEarlierInstallers() {
        val directory = downloads()
        val oldInstaller = Files.write(directory.resolve(olderInstallerName()), MSI_CONTENT)
        val oldPart = Files.write(directory.resolve("KeyNexa-windows-x64-1.11.0.msi.part"), MSI_CONTENT)
        val unrelated = Files.write(directory.resolve("notes.txt"), MSI_CONTENT)
        val oldFolder = Files.createDirectory(directory.resolve("folder.msi"))
        val file = msiFile(NEW_VERSION, MSI_CONTENT)

        val download = service(fake(download = MSI_CONTENT)).download(available(NEW_VERSION, file))

        assertTrue(download is UpdateDownload.Ready)
        assertFalse(Files.exists(oldInstaller))
        assertFalse(Files.exists(oldPart))
        assertTrue(Files.exists(unrelated))
        assertTrue(Files.exists(oldFolder))
    }

    @Test
    fun aNetworkErrorIsFailedAndItsReasonCarriesNoUrl() {
        val result = service(fake(failure = IOException("connection reset"))).check(force = true)

        val reason = (result as UpdateCheck.Failed).reason
        assertEquals(NETWORK, reason)
        assertFalse(reason.contains("http"))
        assertFalse(reason.contains("/"))
        assertNotEquals("connection reset", reason)
    }

    private fun checkVersion(version: String): UpdateCheck {
        val manifest = manifestText(version, listOf(msiFile(version, MSI_CONTENT)))
        return service(fake(manifest, sign(manifest))).check()
    }

    private fun service(
        fetcher: UpdateFetcher,
        state: UpdateStateStore = state(),
    ): UpdateService =
        UpdateService(
            currentVersion = CURRENT,
            fetcher = fetcher,
            verifier = verifier,
            state = state,
            downloads = downloads(),
            installerName = UpdateService::windowsInstaller,
            clock = { NOW },
        )

    private fun fake(
        manifest: ByteArray = ByteArray(0),
        signature: ByteArray = ByteArray(0),
        download: ByteArray? = null,
        failure: IOException? = null,
    ): FakeFetcher = FakeFetcher(manifest, signature, download, failure)

    private fun state(): UpdateStateStore = UpdateStateStore(folder.root.toPath().resolve(STATE_FILE))

    private fun downloads(): Path = Files.createDirectories(folder.root.toPath().resolve(DOWNLOADS))

    private fun manifestText(
        version: String,
        files: List<ManifestFile>,
    ): ByteArray =
        buildString {
            append(HEADER).append('\n')
            append("version ").append(version).append('\n')
            append("released ").append(RELEASED).append('\n')
            files.forEach { append("file ${it.name} ${it.size} ${it.sha256}\n") }
        }.toByteArray()

    private fun msiFile(
        version: String,
        content: ByteArray,
    ): ManifestFile = ManifestFile("KeyNexa-windows-x64-$version$MSI", content.size.toLong(), sha256(content))

    private fun available(
        version: String,
        file: ManifestFile,
    ): UpdateCheck.Available {
        val manifest = UpdateManifest.parse(manifestText(version, listOf(file)))!!
        return UpdateCheck.Available(manifest, file)
    }

    private fun olderInstallerName(): String = "KeyNexa-windows-x64-$OLDER_VERSION$MSI"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun sign(text: ByteArray): ByteArray {
        val signature = Signature.getInstance(ALGORITHM)
        signature.initSign(keyPair.private)
        signature.update(text)
        return signature.sign()
    }

    /** Answers the manifest and its signature from memory and writes [download] as the installer when asked. */
    private class FakeFetcher(
        private val manifest: ByteArray,
        private val signature: ByteArray,
        private val download: ByteArray?,
        private val failure: IOException?,
    ) : UpdateFetcher {
        override fun fetch(
            url: String,
            maxBytes: Int,
        ): ByteArray {
            failure?.let { throw it }
            return if (url.endsWith(SIGNATURE_SUFFIX)) signature else manifest
        }

        override fun download(
            url: String,
            target: Path,
            maxBytes: Long,
        ) {
            failure?.let { throw it }
            Files.write(target, download ?: throw IOException("no installer"))
        }
    }

    private companion object {
        const val ALGORITHM = "Ed25519"
        const val SIGNATURE_BYTES = 64
        const val SIGNATURE_SUFFIX = ".sig"
        const val MSI = ".msi"
        const val HEADER = "keynexa-update 1"
        const val RELEASED = "2026-10-27"
        const val STATE_FILE = "update.properties"
        const val DOWNLOADS = "updates"
        const val CURRENT_TEXT = "1.11.9"
        const val NEW_VERSION = "1.12.0"
        const val HIGHEST_VERSION = "1.13.0"
        const val OLDER_VERSION = "1.11.8"
        const val FORGED_VERSION = "1.13.0"
        const val ONE_HOUR_SECONDS = 3_600L
        const val SIGNATURE = "signature"
        const val NO_INSTALLER = "no installer"
        const val CHECKSUM = "checksum"
        const val NETWORK = "network"

        val CURRENT = SemVer(1, 11, 9)
        val NOW: Instant = Instant.parse("2026-10-27T10:00:00Z")
        val LAST_WEEK: Instant = Instant.parse("2026-10-20T10:00:00Z")
        val MSI_CONTENT = "a signed installer".toByteArray()
    }
}
