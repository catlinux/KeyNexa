package io.github.catlinux.keynexa.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.catlinux.keynexa.cloud.ConditionalWriteProbe
import io.github.catlinux.keynexa.cloud.ConnectionResult
import io.github.catlinux.keynexa.cloud.ProbeResult
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.cloud.regionAfterEndpointChange
import io.github.catlinux.keynexa.cloud.regionFromEndpoint
import io.github.catlinux.keynexa.cloud.testConnection
import io.github.catlinux.keynexa.desktop.DesktopCloudBackup
import io.github.catlinux.keynexa.desktop.DesktopCloudStores
import io.github.catlinux.keynexa.desktop.DesktopCloudSync
import io.github.catlinux.keynexa.sync.BackupError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** The steps of the cloud assistant, in the order they are shown. */
internal enum class CloudStep { Consent, Service, Needs, Form, Status }

/** The copies-to-keep choices the form offers, in the order they are shown. */
internal val KEEP_CHOICES = listOf(KEEP_SMALL, S3Setup.DEFAULT_KEEP, KEEP_LARGE)

private const val KEEP_SMALL = 5
private const val KEEP_LARGE = 20
private const val HTTPS_PREFIX = "https://"

/**
 * The S3 form being filled, held only while the dialog is open.
 *
 * The secret key lives here and nowhere else: it is never written to saved state, never logged, and the dialog forgets
 * the whole form when it closes. The region is filled in from the endpoint only while it still is what the assistant
 * put there, so a value the user typed is never overwritten.
 */
internal class SetupForm {
    private var endpointState by mutableStateOf("")
    private var regionState by mutableStateOf("")
    private var bucketState by mutableStateOf("")
    private var accessKeyIdState by mutableStateOf("")
    private var secretAccessKeyState by mutableStateOf("")
    private var pathStyleState by mutableStateOf(true)

    /** The endpoint; changing it throws away the last test. */
    var endpoint: String
        get() = endpointState
        set(value) {
            endpointState = value
            invalidateTest()
        }

    /** The region; changing it throws away the last test. */
    var region: String
        get() = regionState
        set(value) {
            regionState = value
            invalidateTest()
        }

    /** The bucket; changing it throws away the last test. */
    var bucket: String
        get() = bucketState
        set(value) {
            bucketState = value
            invalidateTest()
        }

    /** The access key; changing it throws away the last test. */
    var accessKeyId: String
        get() = accessKeyIdState
        set(value) {
            accessKeyIdState = value
            invalidateTest()
        }

    /** The secret key; changing it throws away the last test. */
    var secretAccessKey: String
        get() = secretAccessKeyState
        set(value) {
            secretAccessKeyState = value
            invalidateTest()
        }

    /** The path style; changing it throws away the last test. */
    var pathStyle: Boolean
        get() = pathStyleState
        set(value) {
            pathStyleState = value
            invalidateTest()
        }
    var keep by mutableStateOf(S3Setup.DEFAULT_KEEP)
    var secretVisible by mutableStateOf(false)
    var errors by mutableStateOf(SetupFormErrors())
    var testResult by mutableStateOf<ConnectionResult?>(null)
    var testing by mutableStateOf(false)
    var testedOk by mutableStateOf(false)

    /** The last region the assistant filled in by itself, so it never overwrites what the user typed. */
    private var lastAutoRegion by mutableStateOf<String?>(null)

    /** Forgets the last test, so a change cannot be saved without testing again. */
    private fun invalidateTest() {
        testResult = null
        testedOk = false
    }

    fun onEndpointChange(value: String) {
        endpoint = value
        val result = regionAfterEndpointChange(value, region, lastAutoRegion)
        region = result.region
        lastAutoRegion = result.lastAutoRegion
    }

    /** Reads [value] as the region, which the assistant never overwrites afterwards. */
    fun onRegionChange(value: String) {
        region = value
    }

    /** Forgets everything the form holds, so no secret outlives the dialog. */
    fun reset() {
        endpoint = ""
        region = ""
        bucket = ""
        accessKeyId = ""
        secretAccessKey = ""
        pathStyle = true
        keep = S3Setup.DEFAULT_KEEP
        secretVisible = false
        errors = SetupFormErrors()
        testResult = null
        testing = false
        testedOk = false
        lastAutoRegion = null
    }

    /** The setup this form describes, with the fields trimmed and the region taken from the endpoint if empty. */
    fun toSetup(): S3Setup {
        val trimmedEndpoint = endpoint.trim()
        return S3Setup(
            endpoint = trimmedEndpoint,
            region = region.trim().ifEmpty { regionFromEndpoint(trimmedEndpoint).orEmpty() },
            bucket = bucket.trim(),
            pathStyle = pathStyle,
            accessKeyId = accessKeyId.trim(),
            secretAccessKey = secretAccessKey,
            keep = keep,
        )
    }
}

/** Which fields are wrong, for the message under each one. */
internal data class SetupFormErrors(
    val endpoint: Boolean = false,
    val region: Boolean = false,
    val bucket: Boolean = false,
    val accessKeyId: Boolean = false,
    val secretAccessKey: Boolean = false,
) {
    /** True when any field is wrong. */
    val any: Boolean get() = endpoint || region || bucket || accessKeyId || secretAccessKey
}

/** What the settings row says under its title: the bucket of the configured service, or nothing. */
internal data class RowStatus(
    val bucket: String?,
)

/** The outcome of a "Copy now", kept on the status step until it is left. */
internal sealed interface CopyMessage {
    data object Success : CopyMessage

    data class Failure(
        val error: BackupError,
    ) : CopyMessage
}

/** Which fields of [form] are wrong, for the message under each one. */
internal fun validate(form: SetupForm): SetupFormErrors =
    SetupFormErrors(
        endpoint = !form.endpoint.trim().startsWith(HTTPS_PREFIX),
        // The region is optional when the endpoint says which one it is (Backblaze, Amazon, Cloudflare).
        region = form.region.isBlank() && regionFromEndpoint(form.endpoint.trim()) == null,
        bucket = form.bucket.isBlank(),
        accessKeyId = form.accessKeyId.isBlank(),
        secretAccessKey = form.secretAccessKey.isBlank(),
    )

/**
 * The step a "back" goes to, or the same step after asking to close the dialog when there is nowhere back to go.
 */
internal fun backStep(
    step: CloudStep,
    onClose: () -> Unit,
): CloudStep =
    when (step) {
        CloudStep.Consent, CloudStep.Status -> {
            onClose()
            step
        }
        CloudStep.Service -> CloudStep.Consent
        CloudStep.Needs -> CloudStep.Service
        CloudStep.Form -> CloudStep.Needs
    }

/** Checks the connection of the setup [form] describes, off the caller's thread, and records the outcome. */
internal suspend fun runTest(form: SetupForm) {
    form.errors = validate(form)
    form.testResult = null
    form.testedOk = false
    if (form.errors.any) return
    val candidate = form.toSetup()
    if (S3Setup.fromValues(candidate.toValues()) == null) return
    form.testing = true
    val result =
        withContext(Dispatchers.IO) {
            runCatching { testConnection(candidate.backend()) }.getOrDefault(ConnectionResult.Unavailable)
        }
    form.testing = false
    form.testResult = result
    form.testedOk = result == ConnectionResult.Ok
}

/** Saves the setup [form] describes once its connection test worked, off the caller's thread. */
internal suspend fun save(
    form: SetupForm,
    stores: DesktopCloudStores,
    onSaved: (S3Setup) -> Unit,
) {
    if (!form.testedOk) return
    val candidate = form.toSetup()
    withContext(Dispatchers.IO) { stores.credentials.save(candidate.toValues()) }
    onSaved(candidate)
}

/** Saves [setup] with [newKeep] copies to keep, off the caller's thread. */
internal suspend fun changeKeep(
    setup: S3Setup,
    newKeep: Int,
    stores: DesktopCloudStores,
    onChanged: (S3Setup) -> Unit,
) {
    val updated = setup.copy(keep = newKeep)
    withContext(Dispatchers.IO) { stores.credentials.save(updated.toValues()) }
    onChanged(updated)
}

/** Saves [setup] with synchronisation [sync] (keeping the rest, probe included), off the caller's thread. */
internal suspend fun changeSync(
    setup: S3Setup,
    sync: Boolean,
    stores: DesktopCloudStores,
    onChanged: (S3Setup) -> Unit,
) {
    val updated = setup.copy(sync = sync)
    withContext(Dispatchers.IO) { stores.credentials.save(updated.toValues()) }
    onChanged(updated)
}

/**
 * Runs the conditional-write probe against the user's bucket and, only when it passes, turns synchronisation on,
 * off the caller's thread. The probe never throws; anything unexpected from it is reported as a failure.
 */
internal suspend fun enableSync(
    setup: S3Setup,
    stores: DesktopCloudStores,
    onChanged: (S3Setup) -> Unit,
): SyncSwitchOutcome {
    val result =
        withContext(Dispatchers.IO) {
            try {
                ConditionalWriteProbe(setup.backend()).run()
            } catch (failure: IOException) {
                ProbeResult.Failed(failure)
            }
        }
    val outcome = syncSwitchOutcome(result)
    if (outcome == SyncSwitchOutcome.Enabled) {
        val updated = setup.copy(sync = true, syncProbe = S3Setup.probeKey(setup.endpoint, setup.bucket))
        withContext(Dispatchers.IO) { stores.credentials.save(updated.toValues()) }
        onChanged(updated)
    }
    return outcome
}

/** Deletes the credentials and the consent and forgets every backup and synchronisation, off the caller's thread. */
internal suspend fun disconnect(
    cloud: DesktopCloudBackup,
    cloudSync: DesktopCloudSync,
) {
    withContext(Dispatchers.IO) {
        cloud.stores.credentials.clear()
        cloud.stores.consent.revoke()
        cloud.reset()
        cloudSync.reset()
    }
}
