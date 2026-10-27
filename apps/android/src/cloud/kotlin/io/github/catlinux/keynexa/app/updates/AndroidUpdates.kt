package io.github.catlinux.keynexa.app.updates

import android.content.Context
import io.github.catlinux.keynexa.update.HttpsUpdateFetcher
import io.github.catlinux.keynexa.update.ManifestVerifier
import io.github.catlinux.keynexa.update.SemVer
import io.github.catlinux.keynexa.update.UpdateCheck
import io.github.catlinux.keynexa.update.UpdateChoice
import io.github.catlinux.keynexa.update.UpdateDownload
import io.github.catlinux.keynexa.update.UpdateKeys
import io.github.catlinux.keynexa.update.UpdateService
import io.github.catlinux.keynexa.update.UpdateStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** What the updater is doing, for the home card and the settings entry. */
sealed interface UpdatePhase {
    data object Idle : UpdatePhase

    data object Checking : UpdatePhase

    /** A check the user asked for found nothing newer. */
    data object UpToDate : UpdatePhase

    /** A newer, signed version is ready; [notice] says why an earlier try to install it did not go through. */
    class Offered(
        val offer: UpdateCheck.Available,
        val notice: Notice = Notice.None,
    ) : UpdatePhase

    data object Downloading : UpdatePhase

    /** The system installer has the APK and is asking the user to confirm. */
    data object Installing : UpdatePhase

    /** A check the user asked for failed; [reason] is the service's short token. */
    class Failed(
        val reason: String,
    ) : UpdatePhase
}

/** What an offer needs to tell the user besides the version. */
enum class Notice { None, InstallFailed, NeedsPermission }

/**
 * The update flow of the cloud build (ADR-0015): look for a newer signed release, and on the user's word download it,
 * check it and hand it to the system installer. The offline build carries none of this.
 *
 * One process-wide state, so the home card and the settings entry always agree. Nothing here runs before the user
 * answered the one-time question, and nothing installs without the user pressing the button.
 */
object AndroidUpdates {
    private const val CHOICE_FILE = "update-choice"
    private const val STATE_FILE = "update-state.properties"
    private const val DOWNLOADS = "updates"
    private const val UNKNOWN_VERSION = 0

    private val mutablePhase = MutableStateFlow<UpdatePhase>(UpdatePhase.Idle)
    val phase: StateFlow<UpdatePhase> = mutablePhase.asStateFlow()

    private var checkedThisRun = false
    private var service: UpdateService? = null
    private var lastOffer: UpdateCheck.Available? = null

    /** The user's answer: not asked yet, no or yes. A missing or unreadable file means "not asked yet". */
    fun choice(context: Context): UpdateChoice =
        try {
            val name = File(context.filesDir, CHOICE_FILE).takeIf { it.isFile }?.readText()?.trim()
            UpdateChoice.entries.firstOrNull { it.name == name } ?: UpdateChoice.Ask
        } catch (_: IOException) {
            UpdateChoice.Ask
        }

    fun setChoice(
        context: Context,
        choice: UpdateChoice,
    ) {
        File(context.filesDir, CHOICE_FILE).writeText(choice.name)
        if (choice != UpdateChoice.On) mutablePhase.value = UpdatePhase.Idle
    }

    /**
     * Looks for a newer release. When [asked] the user pressed the button, so it ignores the once-a-day limit and
     * says what it found; otherwise it runs at most once per start and stays quiet unless there is something to offer.
     */
    suspend fun check(
        context: Context,
        asked: Boolean,
    ) {
        if (!asked && checkedThisRun) return
        checkedThisRun = true
        if (asked) mutablePhase.value = UpdatePhase.Checking
        val found = withContext(Dispatchers.IO) { serviceOf(context).check(force = asked) }
        mutablePhase.value =
            when (found) {
                is UpdateCheck.Available -> UpdatePhase.Offered(found).also { lastOffer = found }
                UpdateCheck.UpToDate -> if (asked) UpdatePhase.UpToDate else UpdatePhase.Idle
                is UpdateCheck.Failed -> if (asked) UpdatePhase.Failed(found.reason) else UpdatePhase.Idle
                UpdateCheck.NotDue -> UpdatePhase.Idle
            }
    }

    /** Hides the offer until the next check; the user can find it again with the button in the settings. */
    fun later() {
        mutablePhase.value = UpdatePhase.Idle
    }

    /**
     * Downloads the offered APK, checks it and hands it to the system installer.
     *
     * The permission to install apps is looked at first, so nothing is downloaded when it is missing: the system
     * screen for it opens and the user comes back to press Install again.
     */
    suspend fun install(
        context: Context,
        offer: UpdateCheck.Available,
    ) {
        mutablePhase.value = UpdatePhase.Downloading
        val outcome = withContext(Dispatchers.IO) { prepare(context, offer) }
        mutablePhase.value =
            when (outcome) {
                Outcome.Handed -> UpdatePhase.Installing
                Outcome.NeedsPermission -> UpdatePhase.Offered(offer, Notice.NeedsPermission)
                Outcome.Failed -> UpdatePhase.Offered(offer, Notice.InstallFailed)
            }
    }

    /** The system installer's answer, from [InstallResultReceiver]: anything but a success brings the offer back. */
    fun installFinished(success: Boolean) {
        if (success) return
        mutablePhase.value = lastOffer?.let { UpdatePhase.Offered(it, Notice.InstallFailed) } ?: UpdatePhase.Idle
    }

    /** The version of the running app, read from the package manager. */
    fun currentVersion(context: Context): SemVer =
        SemVer.parse(ApkInstaller.installedVersionName(context)) ?: SemVer(UNKNOWN_VERSION, 0, 0)

    private fun prepare(
        context: Context,
        offer: UpdateCheck.Available,
    ): Outcome {
        if (!ApkInstaller.mayInstall(context)) {
            ApkInstaller.openInstallPermissionSettings(context)
            return Outcome.NeedsPermission
        }
        val updates = serviceOf(context)
        val download = updates.download(offer) as? UpdateDownload.Ready ?: return Outcome.Failed
        val apk = download.path.toFile()
        val checked =
            updates.verifyAgain(download.path, offer.installer) && ApkInstaller.isOurUpdate(context, apk, offer)
        val handed = checked && ApkInstaller.commit(context, apk)
        if (!handed) apk.delete()
        return if (handed) Outcome.Handed else Outcome.Failed
    }

    @Synchronized
    private fun serviceOf(context: Context): UpdateService {
        service?.let { return it }
        val app = context.applicationContext
        val version = currentVersion(app)
        return UpdateService(
            currentVersion = version,
            fetcher = HttpsUpdateFetcher(userAgentVersion = version.toString()),
            verifier = ManifestVerifier(UpdateKeys.PRODUCTION),
            state = UpdateStateStore(File(app.filesDir, STATE_FILE).toPath()),
            downloads = File(app.cacheDir, DOWNLOADS).toPath(),
            installerName = UpdateService::androidCloudInstaller,
        ).also { service = it }
    }

    private enum class Outcome { Handed, NeedsPermission, Failed }
}
