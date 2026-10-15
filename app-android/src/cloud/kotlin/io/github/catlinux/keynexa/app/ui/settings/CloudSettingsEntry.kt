@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.app.ui.settings

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.cloud.CloudBackupService
import io.github.catlinux.keynexa.app.cloud.CloudBackupStatus
import io.github.catlinux.keynexa.app.cloud.CloudConsentStore
import io.github.catlinux.keynexa.app.cloud.CloudStores
import io.github.catlinux.keynexa.app.vault.CloudBackupSource
import io.github.catlinux.keynexa.cloud.ConnectionResult
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.cloud.testConnection
import io.github.catlinux.keynexa.cloud.vaultIdOf
import io.github.catlinux.keynexa.sync.BackupError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

private val CloudPadding = 16.dp
private val CloudGap = 8.dp
private val CloudTouchTarget = 48.dp

private const val HTTPS_PREFIX = "https://"
private const val KEEP_SMALL = 5
private const val KEEP_LARGE = 20
private const val BACKBLAZE_URL = "https://www.backblaze.com/cloud-storage"
private const val CLOUDFLARE_URL = "https://developers.cloudflare.com/r2/"
private val KEEP_CHOICES = listOf(KEEP_SMALL, S3Setup.DEFAULT_KEEP, KEEP_LARGE)

/** The steps of the cloud assistant. */
private enum class CloudStep { Consent, Service, Needs, Form, Status }

/**
 * The cloud copies entry of the settings screen: consent, a small S3 assistant and the state of the configured
 * service.
 *
 * Every read and write (credentials, consent, connection test) runs off the composition thread. The secret key lives
 * only in `remember`, never in saved state, and the form is forgotten when the dialog closes. Only the cloud variant
 * has this code; the offline variant ships an empty function with the same signature.
 */
@Suppress("LongMethod")
@Composable
fun CloudSettingsEntry(source: CloudBackupSource?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showing by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<S3Setup?>(null) }
    var step by remember { mutableStateOf(CloudStep.Consent) }
    var confirmingDisconnect by remember { mutableStateOf(false) }
    val serviceStatus by CloudBackupService.status.collectAsState()
    var status by remember { mutableStateOf<CloudBackupStatus?>(null) }
    var rowStatus by remember { mutableStateOf<RowStatus?>(null) }

    LaunchedEffect(showing) {
        if (!showing) return@LaunchedEffect
        val loaded =
            withContext(Dispatchers.IO) {
                val consent = CloudConsentStore(context).accepted()
                val stored = CloudStores.credentials(context).load()?.let(S3Setup::fromValues)
                consent to stored
            }
        setup = loaded.second
        step =
            when {
                !loaded.first -> CloudStep.Consent
                loaded.second != null -> CloudStep.Status
                else -> CloudStep.Service
            }
    }
    LaunchedEffect(showing, serviceStatus, source?.vault, setup) {
        if (!showing) return@LaunchedEffect
        status =
            withContext(Dispatchers.IO) {
                val vault = source?.vault
                if (vault == null) {
                    serviceStatus
                } else {
                    CloudBackupService.statusOf(context, vaultIdOf(vault.location.value))
                }
            }
    }

    LaunchedEffect(setup, serviceStatus) {
        rowStatus =
            withContext(Dispatchers.IO) {
                val bucket = (setup ?: CloudStores.credentials(context).load()?.let(S3Setup::fromValues))?.bucket
                RowStatus(bucket)
            }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CloudTouchTarget)
                .clickable { showing = true }
                .padding(vertical = CloudGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudGap),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CloudGap),
        ) {
            Text(text = stringResource(R.string.cloud_settings_entry), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = rowStatusText(rowStatus),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
    }

    if (showing) {
        val form = remember { SetupForm() }
        Dialog(
            onDismissRequest = { showing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(CloudPadding),
                    verticalArrangement = Arrangement.spacedBy(CloudGap),
                ) {
                    WizardHeader(step = step, onBack = { step = backStep(step) { showing = false } })
                    when (step) {
                        CloudStep.Consent ->
                            ConsentBody(
                                onAccept = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { CloudConsentStore(context).accept() }
                                        step = CloudStep.Service
                                    }
                                },
                                onDecline = { showing = false },
                            )
                        CloudStep.Service -> ServiceBody(onS3 = { step = CloudStep.Needs })
                        CloudStep.Needs ->
                            NeedsBody(
                                onReady = {
                                    form.reset()
                                    step = CloudStep.Form
                                },
                            )
                        CloudStep.Form ->
                            SetupFormBody(
                                form = form,
                                onTest = { runTest(form, scope) },
                                onSave = {
                                    save(
                                        form = form,
                                        scope = scope,
                                        context = context,
                                        onSaved = { saved ->
                                            form.reset()
                                            setup = saved
                                            step = CloudStep.Status
                                        },
                                    )
                                },
                            )
                        CloudStep.Status ->
                            setup?.let { saved ->
                                StatusBody(
                                    setup = saved,
                                    source = source,
                                    status = status,
                                    onKeep = { newKeep -> changeKeep(saved, newKeep, scope, context) { setup = it } },
                                    onCopyNow = {
                                        source?.let { CloudBackupService.backupNow(context, it.vault, it.read) }
                                    },
                                    onChangeService = {
                                        form.reset()
                                        step = CloudStep.Needs
                                    },
                                    onDisconnect = { confirmingDisconnect = true },
                                    onClose = { showing = false },
                                )
                            }
                    }
                }
            }
        }
    }

    if (confirmingDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmingDisconnect = false },
            title = { Text(text = stringResource(R.string.cloud_disconnect_title)) },
            text = { Text(text = stringResource(R.string.cloud_disconnect_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDisconnect = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                CloudStores.credentials(context).clear()
                                CloudConsentStore(context).revoke()
                            }
                            setup = null
                            step = CloudStep.Consent
                            showing = false
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.cloud_disconnect_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDisconnect = false }) {
                    Text(text = stringResource(R.string.cloud_cancel))
                }
            },
        )
    }
}

/** The form being filled, held only while the dialog is composed. */
private class SetupForm {
    var endpoint by mutableStateOf("")
    var region by mutableStateOf("")
    var bucket by mutableStateOf("")
    var accessKeyId by mutableStateOf("")
    var secretAccessKey by mutableStateOf("")
    var pathStyle by mutableStateOf(true)
    var keep by mutableStateOf(S3Setup.DEFAULT_KEEP)
    var secretVisible by mutableStateOf(false)
    var errors by mutableStateOf(SetupFormErrors())
    var testResult by mutableStateOf<ConnectionResult?>(null)
    var testing by mutableStateOf(false)
    var testedOk by mutableStateOf(false)

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
    }

    fun toSetup(): S3Setup =
        S3Setup(
            endpoint = endpoint.trim(),
            region = region.trim(),
            bucket = bucket.trim(),
            pathStyle = pathStyle,
            accessKeyId = accessKeyId.trim(),
            secretAccessKey = secretAccessKey,
            keep = keep,
        )
}

/** Which fields are wrong, for the messages under each one. */
private data class SetupFormErrors(
    val endpoint: Boolean = false,
    val region: Boolean = false,
    val bucket: Boolean = false,
    val accessKeyId: Boolean = false,
    val secretAccessKey: Boolean = false,
) {
    val any: Boolean get() = endpoint || region || bucket || accessKeyId || secretAccessKey
}

/** What the settings row says under its title: the bucket of the configured service, or nothing. */
private data class RowStatus(
    val bucket: String?,
)

@Composable
private fun rowStatusText(status: RowStatus?): String {
    val bucket = status?.bucket
    return if (bucket == null) {
        stringResource(R.string.cloud_settings_entry_status_off)
    } else {
        stringResource(R.string.cloud_settings_entry_status_on, bucket)
    }
}

private fun validate(form: SetupForm): SetupFormErrors =
    SetupFormErrors(
        endpoint = !form.endpoint.trim().startsWith(HTTPS_PREFIX),
        region = form.region.isBlank(),
        bucket = form.bucket.isBlank(),
        accessKeyId = form.accessKeyId.isBlank(),
        secretAccessKey = form.secretAccessKey.isBlank(),
    )

private fun runTest(
    form: SetupForm,
    scope: CoroutineScope,
) {
    form.errors = validate(form)
    form.testResult = null
    form.testedOk = false
    if (form.errors.any) return
    val candidate = form.toSetup()
    if (S3Setup.fromValues(candidate.toValues()) == null) return
    form.testing = true
    scope.launch {
        val result =
            withContext(Dispatchers.IO) {
                runCatching { testConnection(candidate.backend()) }.getOrDefault(ConnectionResult.Unavailable)
            }
        form.testing = false
        form.testResult = result
        form.testedOk = result == ConnectionResult.Ok
    }
}

private fun save(
    form: SetupForm,
    scope: CoroutineScope,
    context: Context,
    onSaved: (S3Setup) -> Unit,
) {
    if (!form.testedOk) return
    val candidate = form.toSetup()
    scope.launch {
        withContext(Dispatchers.IO) { CloudStores.credentials(context).save(candidate.toValues()) }
        onSaved(candidate)
    }
}

private fun changeKeep(
    setup: S3Setup,
    newKeep: Int,
    scope: CoroutineScope,
    context: Context,
    onChanged: (S3Setup) -> Unit,
) {
    val updated = setup.copy(keep = newKeep)
    scope.launch {
        withContext(Dispatchers.IO) { CloudStores.credentials(context).save(updated.toValues()) }
        onChanged(updated)
    }
}

private fun backStep(
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

@Composable
private fun WizardHeader(
    step: CloudStep,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudGap),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cloud_wizard_back),
            )
        }
        Text(text = stepTitle(step), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun stepTitle(step: CloudStep): String =
    stringResource(
        when (step) {
            CloudStep.Consent -> R.string.cloud_consent_title
            CloudStep.Service -> R.string.cloud_service_title
            CloudStep.Needs -> R.string.cloud_needs_title
            CloudStep.Form -> R.string.cloud_form_title
            CloudStep.Status -> R.string.cloud_settings_entry
        },
    )

/** What is sent, where, why and how to stop, with the two consent buttons. */
@Composable
private fun ConsentBody(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    ConsentGroup(heading = R.string.cloud_consent_heading_sent, body = R.string.cloud_consent_sent)
    ConsentGroup(heading = R.string.cloud_consent_heading_where, body = R.string.cloud_consent_where)
    ConsentGroup(heading = R.string.cloud_consent_heading_why, body = R.string.cloud_consent_why)
    ConsentGroup(heading = R.string.cloud_consent_heading_stop, body = R.string.cloud_consent_stop)
    listOf(
        R.string.cloud_consent_no_servers,
        R.string.cloud_consent_old_copies,
        R.string.cloud_consent_offline,
    ).forEach { note ->
        Text(
            text = stringResource(note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(
        onClick = onAccept,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_consent_accept))
    }
    OutlinedButton(
        onClick = onDecline,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_consent_decline))
    }
}

/** A bold heading with the sentence that belongs to it. */
@Composable
private fun ConsentGroup(
    @StringRes heading: Int,
    @StringRes body: Int,
) {
    Text(
        text = stringResource(heading),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium)
}

/** The one implemented service: a card that opens the "what you need" step. */
@Composable
private fun ServiceBody(onS3: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onS3),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(CloudPadding),
            verticalArrangement = Arrangement.spacedBy(CloudGap),
        ) {
            Text(text = stringResource(R.string.cloud_service_s3_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.cloud_service_s3_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The plain checklist for S3, with the two registration pages and the "I have it" button. */
@Composable
private fun NeedsBody(onReady: () -> Unit) {
    val context = LocalContext.current
    val steps =
        listOf(
            R.string.cloud_needs_step_1,
            R.string.cloud_needs_step_2,
            R.string.cloud_needs_step_3,
            R.string.cloud_needs_step_4,
        )
    steps.forEachIndexed { index, step ->
        Text(
            text = stringResource(R.string.cloud_needs_step_number, index + 1, stringResource(step)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        text = stringResource(R.string.cloud_needs_browser_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = { openBrowser(context, BACKBLAZE_URL) },
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_needs_backblaze))
    }
    OutlinedButton(
        onClick = { openBrowser(context, CLOUDFLARE_URL) },
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_needs_cloudflare))
    }
    Button(
        onClick = onReady,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_needs_ready))
    }
}

/** The S3 form: fields with help and errors, the connection test and, only after it works, saving. */
@Composable
private fun SetupFormBody(
    form: SetupForm,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    FormField(
        label = stringResource(R.string.cloud_form_endpoint),
        value = form.endpoint,
        onValueChange = { form.endpoint = it },
        help = stringResource(R.string.cloud_form_endpoint_help),
        error = if (form.errors.endpoint) stringResource(R.string.cloud_error_endpoint) else null,
    )
    FormField(
        label = stringResource(R.string.cloud_form_region),
        value = form.region,
        onValueChange = { form.region = it },
        help = stringResource(R.string.cloud_form_region_help),
        error = if (form.errors.region) stringResource(R.string.cloud_error_region) else null,
    )
    FormField(
        label = stringResource(R.string.cloud_form_bucket),
        value = form.bucket,
        onValueChange = { form.bucket = it },
        help = stringResource(R.string.cloud_form_bucket_help),
        error = if (form.errors.bucket) stringResource(R.string.cloud_error_bucket) else null,
    )
    FormField(
        label = stringResource(R.string.cloud_form_access_key),
        value = form.accessKeyId,
        onValueChange = { form.accessKeyId = it },
        help = stringResource(R.string.cloud_form_access_key_help),
        error = if (form.errors.accessKeyId) stringResource(R.string.cloud_error_access) else null,
    )
    SecretField(form = form)
    CloudSwitch(
        label = stringResource(R.string.cloud_form_path_style),
        checked = form.pathStyle,
        onCheckedChange = { form.pathStyle = it },
    )
    KeepChoices(selected = form.keep, onSelect = { form.keep = it })
    form.testResult?.let { result ->
        val color =
            if (result == ConnectionResult.Ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        Text(text = testMessage(result), style = MaterialTheme.typography.bodyMedium, color = color)
    }
    Button(
        onClick = onTest,
        enabled = !form.testing,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(
            text =
                stringResource(
                    if (form.testing) R.string.cloud_form_testing else R.string.cloud_form_test,
                ),
        )
    }
    if (form.testedOk) {
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
        ) {
            Text(text = stringResource(R.string.cloud_form_save))
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    help: String,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CloudGap)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(text = label) },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Text(
            text = help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SecretField(form: SetupForm) {
    Column(verticalArrangement = Arrangement.spacedBy(CloudGap)) {
        OutlinedTextField(
            value = form.secretAccessKey,
            onValueChange = { form.secretAccessKey = it },
            label = { Text(text = stringResource(R.string.cloud_form_secret)) },
            singleLine = true,
            isError = form.errors.secretAccessKey,
            visualTransformation =
                if (form.secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.errors.secretAccessKey) {
            Text(
                text = stringResource(R.string.cloud_error_secret),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.cloud_form_secret_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CloudSwitch(
            label = stringResource(R.string.cloud_form_show_secret),
            checked = form.secretVisible,
            onCheckedChange = { form.secretVisible = it },
        )
    }
}

@Composable
private fun CloudSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudGap),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun KeepChoices(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(text = stringResource(R.string.cloud_form_keep), style = MaterialTheme.typography.bodyLarge)
    KEEP_CHOICES.forEach { choice ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget).clickable { onSelect(choice) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CloudGap),
        ) {
            RadioButton(selected = selected == choice, onClick = null)
            Text(
                text = stringResource(R.string.cloud_form_keep_option, choice),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** The state of the configured service: where the copies go, when the last one was, and what to do. */
@Suppress("LongMethod")
@Composable
private fun StatusBody(
    setup: S3Setup,
    source: CloudBackupSource?,
    status: CloudBackupStatus?,
    onKeep: (Int) -> Unit,
    onCopyNow: () -> Unit,
    onChangeService: () -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    Text(text = stringResource(R.string.cloud_status_service), style = MaterialTheme.typography.bodyLarge)
    Text(text = stringResource(R.string.cloud_status_bucket, setup.bucket), style = MaterialTheme.typography.bodyLarge)
    val lastSuccess = status?.lastSuccess
    Text(
        text =
            if (lastSuccess == null) {
                stringResource(R.string.cloud_status_no_copy)
            } else {
                stringResource(R.string.cloud_status_last_copy, relativeTime(lastSuccess))
            },
        style = MaterialTheme.typography.bodyMedium,
    )
    status?.lastError?.let { error ->
        Text(
            text = errorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    KeepChoices(selected = setup.keep, onSelect = onKeep)
    Button(
        onClick = onCopyNow,
        enabled = source != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_status_copy_now))
    }
    if (source == null) {
        Text(
            text = stringResource(R.string.cloud_status_copy_now_needs_vault),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(
        onClick = onChangeService,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_status_change_service))
    }
    OutlinedButton(
        onClick = onDisconnect,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_disconnect))
    }
    TextButton(
        onClick = onClose,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_close))
    }
}

@Composable
private fun testMessage(result: ConnectionResult): String =
    stringResource(
        when (result) {
            ConnectionResult.Ok -> R.string.cloud_test_ok
            ConnectionResult.NotAuthorized -> R.string.cloud_test_not_authorized
            ConnectionResult.WrongBucketOrEndpoint -> R.string.cloud_test_wrong_bucket
            ConnectionResult.Unavailable -> R.string.cloud_test_unavailable
        },
    )

@Composable
private fun errorMessage(error: BackupError): String =
    stringResource(
        when (error) {
            BackupError.NotAuthorized -> R.string.cloud_status_error_not_authorized
            BackupError.Unavailable -> R.string.cloud_status_error_unavailable
            BackupError.Rejected, BackupError.Unknown -> R.string.cloud_status_error_generic
        },
    )

@Composable
private fun relativeTime(instant: Instant): String =
    DateUtils
        .getRelativeTimeSpanString(instant.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        .toString()

private fun openBrowser(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
