package io.github.catlinux.keynexa.update

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** What a check for updates found. */
sealed interface UpdateCheck {
    /** The last check was less than a day ago, so this one did not ask anyone anything. */
    data object NotDue : UpdateCheck

    /** The newest published release is not newer than the running one. */
    data object UpToDate : UpdateCheck

    /** A newer, signed release is available; [installer] is its installer as the manifest lists it. */
    class Available(
        val manifest: UpdateManifest,
        val installer: ManifestFile,
    ) : UpdateCheck

    /** The check could not be completed; [reason] is a short token, never a message from the network. */
    class Failed(
        val reason: String,
    ) : UpdateCheck
}

/** What downloading an offered installer gave. */
sealed interface UpdateDownload {
    /** [path] holds the installer, with the size and SHA-256 the signed manifest lists. */
    class Ready(
        val path: Path,
    ) : UpdateDownload

    /** The installer was not obtained; [reason] is a short token, never a message from the network. */
    class Failed(
        val reason: String,
    ) : UpdateDownload
}

/**
 * Looks for a new release, downloads its installer and checks it, as ADR-0015 describes.
 *
 * The manifest and its signature come from the update channel, and the signature is checked against the key the
 * app carries before anything else is looked at: a stolen GitHub account alone cannot offer an update. Only a
 * newer version is ever offered, the installer is checked against the size and SHA-256 the manifest lists, and the
 * install itself is left to the caller, which asks the user first.
 */
@Suppress("LongParameterList")
class UpdateService(
    private val currentVersion: SemVer,
    private val fetcher: UpdateFetcher,
    private val verifier: ManifestVerifier,
    private val state: UpdateStateStore,
    private val downloads: Path,
    private val installerName: (SemVer) -> String,
    private val clock: () -> Instant = Instant::now,
) {
    /**
     * Looks for a newer release, unless [force] is false and the last check is less than a day old.
     *
     * The check is recorded only once the manifest arrived and its signature verified, so a network problem or a
     * forged manifest is retried on the next start instead of silencing the updater for a day.
     */
    @Suppress("ReturnCount")
    fun check(force: Boolean = false): UpdateCheck {
        if (!force && !CheckSchedule.isDue(state.lastCheck(), clock())) return UpdateCheck.NotDue
        val manifestBytes = fetchOrNull(MANIFEST_URL, MANIFEST_MAX_BYTES) ?: return UpdateCheck.Failed(NETWORK)
        val signature = fetchOrNull(SIGNATURE_URL, SIGNATURE_MAX_BYTES) ?: return UpdateCheck.Failed(NETWORK)
        if (!verifier.verify(manifestBytes, signature)) return UpdateCheck.Failed(SIGNATURE)
        val manifest = UpdateManifest.parse(manifestBytes) ?: return UpdateCheck.Failed(MANIFEST)
        // A correctly signed manifest of an older release is still a rollback: refuse it.
        val seen = state.highestSeen()
        if (seen != null && manifest.version < seen) return UpdateCheck.Failed(ROLLBACK)
        recordCheck(manifest.version)
        val offered = UpdateDecision.offer(currentVersion, manifest) ?: return UpdateCheck.UpToDate
        val installer = offered.file(installerName(offered.version)) ?: return UpdateCheck.Failed(NO_INSTALLER)
        return UpdateCheck.Available(offered, installer)
    }

    /**
     * Downloads the installer [offer] names into the downloads folder and checks its size and SHA-256.
     *
     * Older installers are removed first, so the folder only ever holds the one the user is about to run.
     */
    fun download(offer: UpdateCheck.Available): UpdateDownload {
        val target = downloads.resolve(offer.installer.name)
        return try {
            Files.createDirectories(downloads)
            deleteInstallers()
            fetcher.download(urlOf(offer), target, offer.installer.size)
            if (FileCheck.matches(target, offer.installer.size, offer.installer.sha256)) {
                UpdateDownload.Ready(target)
            } else {
                Files.deleteIfExists(target)
                UpdateDownload.Failed(CHECKSUM)
            }
        } catch (failure: IOException) {
            UpdateDownload.Failed(NETWORK)
        }
    }

    /** True when [path] still has the size and SHA-256 [installer] lists; checked again right before installing. */
    fun verifyAgain(
        path: Path,
        installer: ManifestFile,
    ): Boolean = FileCheck.matches(path, installer.size, installer.sha256)

    /** Removes the installers and partial downloads of earlier runs; best effort, a missing folder is fine. */
    fun cleanup() {
        deleteInstallers()
    }

    private fun urlOf(offer: UpdateCheck.Available): String =
        RELEASE_URL + "v${offer.manifest.version}/" + offer.installer.name

    /** The body at [url], or null when it could not be fetched. The reason never reaches a message. */
    private fun fetchOrNull(
        url: String,
        maxBytes: Int,
    ): ByteArray? =
        try {
            fetcher.fetch(url, maxBytes)
        } catch (failure: IOException) {
            null
        }

    /** A state file that cannot be written only means the next start checks again, so it does not fail the check. */
    private fun recordCheck(version: SemVer) {
        try {
            state.recordCheck(clock())
            state.recordSeen(version)
        } catch (failure: IOException) {
            // Deliberately ignored: the check itself succeeded.
        }
    }

    private fun deleteInstallers() {
        try {
            Files.newDirectoryStream(downloads).use { entries -> entries.forEach(::deleteInstaller) }
        } catch (failure: IOException) {
            // A missing or unreadable folder holds nothing this run can remove.
        }
    }

    private fun deleteInstaller(entry: Path) {
        val name = entry.fileName.toString()
        if (REMOVABLE_SUFFIXES.none(name::endsWith)) return
        try {
            if (Files.isRegularFile(entry)) Files.deleteIfExists(entry)
        } catch (failure: IOException) {
            // Best effort: the download overwrites the .part file and replaces the installer.
        }
    }

    companion object {
        /** The signed manifest of the newest release, on a branch only the release workflow writes (ADR-0015). */
        const val MANIFEST_URL: String =
            "https://raw.githubusercontent.com/catlinux/KeyNexa/update-channel/keynexa-update.txt"

        /** Where the installers of a release are published. */
        const val RELEASE_URL: String = "https://github.com/catlinux/KeyNexa/releases/download/"

        /** The installer of the Windows x64 build, as the release workflow names it. */
        fun windowsInstaller(version: SemVer): String = "KeyNexa-windows-x64-$version.msi"

        /** The APK of the Android build with the cloud flavor, as the release workflow names it. */
        fun androidCloudInstaller(version: SemVer): String = "keynexa-cloud-$version.apk"

        /** What the downloads folder may hold: installers of either platform and the partial files of a download. */
        private val REMOVABLE_SUFFIXES = listOf(".msi", ".apk", ".part")
        private const val SIGNATURE_URL = "$MANIFEST_URL.sig"
        private const val MANIFEST_MAX_BYTES = 16_384
        private const val SIGNATURE_MAX_BYTES = 128
        private const val NETWORK = "network"
        private const val SIGNATURE = "signature"
        private const val MANIFEST = "manifest"
        private const val ROLLBACK = "rollback"
        private const val NO_INSTALLER = "no installer"
        private const val CHECKSUM = "checksum"
    }
}
