@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.github.catlinux.keynexa.cloud.ConnectionResult
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.cloud.vaultIdOf
import io.github.catlinux.keynexa.desktop.CloudBackupStatus
import io.github.catlinux.keynexa.desktop.DesktopCloudBackup
import io.github.catlinux.keynexa.desktop.DesktopCloudSync
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_cancel
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_close
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_accept
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_decline
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_heading_sent
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_heading_stop
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_heading_where
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_heading_why
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_no_servers
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_offline
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_old_copies
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_sent
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_stop
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_where
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_consent_why
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_disconnect
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_disconnect_confirm
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_disconnect_message
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_disconnect_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_error_access
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_error_bucket
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_error_endpoint
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_error_region
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_error_secret
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_access_key
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_access_key_help
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_bucket
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_bucket_help
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_endpoint
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_endpoint_help
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_keep
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_keep_option
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_path_style
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_region
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_region_help
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_save
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_secret
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_secret_help
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_show_secret
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_test
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_testing
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_form_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_backblaze
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_browser_note
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_cloudflare
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_ready
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_step_1
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_step_2
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_step_3
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_step_4
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_step_number
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_needs_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_service_s3_description
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_service_s3_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_service_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_settings_entry
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_bucket
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_change_service
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_copy_done
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_copy_now
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_copy_now_needs_vault
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_error_generic
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_error_not_authorized
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_error_unavailable
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_last_copy
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_no_copy
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_status_service
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_test_not_authorized
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_test_ok
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_test_unavailable
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_test_wrong_bucket
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_wizard_back
import io.github.catlinux.keynexa.session.CloudBackupSource
import io.github.catlinux.keynexa.sync.BackupError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val CloudDialogSize = DpSize(640.dp, 720.dp)
private val DialogPadding = 20.dp
private val BlockGap = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 44.dp
private val ContentWidth = 560.dp
private val CardShape = RoundedCornerShape(16.dp)
private const val CARD_BORDER_ALPHA = 0.4f

private const val BACKBLAZE_URL = "https://www.backblaze.com/cloud-storage"
private const val CLOUDFLARE_URL = "https://developers.cloudflare.com/r2/"

/**
 * The cloud assistant, in its own dialog window: consent, the service, what it needs, the S3 form and the state of the
 * configured service.
 *
 * Every read and write (credentials, consent, connection test) runs off the composition thread. The secret key lives
 * only in `remember`, never in saved state, and the form is forgotten when the dialog closes.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun ApplicationScope.DesktopCloudDialog(
    cloud: DesktopCloudBackup,
    cloudSync: DesktopCloudSync,
    source: CloudBackupSource?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val stores = cloud.stores
    val form = remember { SetupForm() }
    var setup by remember { mutableStateOf<S3Setup?>(null) }
    var step by remember { mutableStateOf(CloudStep.Consent) }
    var confirmingDisconnect by remember { mutableStateOf(false) }
    val serviceStatus by cloud.status.collectAsState()
    var status by remember { mutableStateOf<CloudBackupStatus?>(null) }
    var copyMessage by remember { mutableStateOf<CopyMessage?>(null) }
    var awaitingCopy by remember { mutableStateOf(false) }
    var sawCopyRunning by remember { mutableStateOf(false) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncNotice by remember { mutableStateOf<SyncSwitchOutcome?>(null) }

    LaunchedEffect(Unit) {
        val loaded =
            withContext(Dispatchers.IO) {
                val consent = stores.consent.accepted()
                val stored = stores.credentials.load()?.let(S3Setup::fromValues)
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
    LaunchedEffect(serviceStatus, source?.vault, setup) {
        status =
            withContext(Dispatchers.IO) {
                val vault = source?.vault
                if (vault == null) {
                    serviceStatus
                } else {
                    cloud.statusOf(vaultIdOf(vault.location.value))
                }
            }
    }
    // The copy message and the probe notice live only while the status step is on.
    LaunchedEffect(step) {
        if (step != CloudStep.Status) {
            copyMessage = null
            syncNotice = null
        }
    }
    // A "Copy now" is over when the service stops running: show its outcome until the dialog is left.
    LaunchedEffect(serviceStatus.running) {
        if (serviceStatus.running) {
            if (awaitingCopy) sawCopyRunning = true
        } else if (awaitingCopy && sawCopyRunning) {
            awaitingCopy = false
            sawCopyRunning = false
            copyMessage = serviceStatus.lastError?.let { CopyMessage.Failure(it) } ?: CopyMessage.Success
        }
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(size = CloudDialogSize),
        title = stringResource(Res.string.cloud_settings_entry),
        resizable = false,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier =
                            Modifier
                                .widthIn(max = ContentWidth)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(DialogPadding),
                        verticalArrangement = Arrangement.spacedBy(BlockGap),
                    ) {
                        WizardHeader(step = step, onBack = { step = backStep(step) { onDismiss() } })
                        when (step) {
                            CloudStep.Consent ->
                                ConsentBody(
                                    onAccept = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { stores.consent.accept() }
                                            step = CloudStep.Service
                                        }
                                    },
                                    onDecline = onDismiss,
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
                                    onTest = { scope.launch { runTest(form) } },
                                    onSave = {
                                        scope.launch {
                                            save(form, stores) { saved ->
                                                form.reset()
                                                setup = saved
                                                step = CloudStep.Status
                                            }
                                        }
                                    },
                                )
                            CloudStep.Status ->
                                setup?.let { saved ->
                                    StatusBody(
                                        setup = saved,
                                        source = source,
                                        status = status,
                                        copyMessage = copyMessage,
                                        syncBusy = syncBusy,
                                        syncNotice = syncNotice,
                                        onKeep = { newKeep ->
                                            scope.launch { changeKeep(saved, newKeep, stores) { setup = it } }
                                        },
                                        onCopyNow = {
                                            source?.let {
                                                sawCopyRunning = false
                                                awaitingCopy = true
                                                cloud.backupNow(it.vault, it.read)
                                            }
                                        },
                                        onToggleSync = { wanted ->
                                            scope.launch {
                                                if (!wanted) {
                                                    changeSync(saved, false, stores) { setup = it }
                                                    syncNotice = null
                                                } else {
                                                    syncBusy = true
                                                    syncNotice = null
                                                    val outcome = enableSync(saved, stores) { setup = it }
                                                    syncNotice = outcome
                                                    syncBusy = false
                                                }
                                            }
                                        },
                                        onChangeService = {
                                            form.reset()
                                            step = CloudStep.Needs
                                        },
                                        onDisconnect = { confirmingDisconnect = true },
                                        onClose = onDismiss,
                                    )
                                }
                        }
                    }
                }
            }
            if (confirmingDisconnect) {
                AlertDialog(
                    onDismissRequest = { confirmingDisconnect = false },
                    title = { Text(text = stringResource(Res.string.cloud_disconnect_title)) },
                    text = { Text(text = stringResource(Res.string.cloud_disconnect_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmingDisconnect = false
                                scope.launch {
                                    disconnect(cloud, cloudSync)
                                    setup = null
                                    step = CloudStep.Consent
                                    onDismiss()
                                }
                            },
                        ) {
                            Text(text = stringResource(Res.string.cloud_disconnect_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmingDisconnect = false }) {
                            Text(text = stringResource(Res.string.cloud_cancel))
                        }
                    },
                )
            }
        }
    }
}

/** The heading of the current step and the button that goes back or closes the dialog. */
@Composable
private fun WizardHeader(
    step: CloudStep,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(
            text = stepTitle(step),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchTarget)) {
            Text(text = stringResource(Res.string.cloud_wizard_back))
        }
    }
}

/** The title of [step]. */
@Composable
private fun stepTitle(step: CloudStep): String =
    stringResource(
        when (step) {
            CloudStep.Consent -> Res.string.cloud_consent_title
            CloudStep.Service -> Res.string.cloud_service_title
            CloudStep.Needs -> Res.string.cloud_needs_title
            CloudStep.Form -> Res.string.cloud_form_title
            CloudStep.Status -> Res.string.cloud_settings_entry
        },
    )

/** What is sent, where, why and how to stop, with the two consent buttons. */
@Composable
private fun ConsentBody(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    ConsentGroup(heading = Res.string.cloud_consent_heading_sent, body = Res.string.cloud_consent_sent)
    ConsentGroup(heading = Res.string.cloud_consent_heading_where, body = Res.string.cloud_consent_where)
    ConsentGroup(heading = Res.string.cloud_consent_heading_why, body = Res.string.cloud_consent_why)
    ConsentGroup(heading = Res.string.cloud_consent_heading_stop, body = Res.string.cloud_consent_stop)
    listOf(
        Res.string.cloud_consent_no_servers,
        Res.string.cloud_consent_old_copies,
        Res.string.cloud_consent_offline,
    ).forEach { note ->
        Text(
            text = stringResource(note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(
        onClick = onAccept,
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(Res.string.cloud_consent_accept))
    }
    OutlinedButton(
        onClick = onDecline,
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(Res.string.cloud_consent_decline))
    }
}

/** A bold heading with the sentence that belongs to it. */
@Composable
private fun ConsentGroup(
    heading: StringResource,
    body: StringResource,
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
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column(
            modifier = Modifier.padding(BlockGap),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(text = stringResource(Res.string.cloud_service_s3_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(Res.string.cloud_service_s3_description),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The plain checklist for S3, with the two registration pages and the "I have it" button. */
@Composable
private fun NeedsBody(onReady: () -> Unit) {
    val steps =
        listOf(
            Res.string.cloud_needs_step_1,
            Res.string.cloud_needs_step_2,
            Res.string.cloud_needs_step_3,
            Res.string.cloud_needs_step_4,
        )
    steps.forEachIndexed { index, step ->
        Text(
            text = stringResource(Res.string.cloud_needs_step_number, index + 1, stringResource(step)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        text = stringResource(Res.string.cloud_needs_browser_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = { openInBrowser(BACKBLAZE_URL) },
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(Res.string.cloud_needs_backblaze))
    }
    OutlinedButton(
        onClick = { openInBrowser(CLOUDFLARE_URL) },
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(Res.string.cloud_needs_cloudflare))
    }
    Button(
        onClick = onReady,
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(Res.string.cloud_needs_ready))
    }
}

/** The S3 form: fields with help and errors, the connection test and, only after it works, saving. */
@Composable
@Suppress("LongMethod")
private fun SetupFormBody(
    form: SetupForm,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    FormField(
        label = stringResource(Res.string.cloud_form_endpoint),
        value = form.endpoint,
        onValueChange = { form.onEndpointChange(it) },
        help = stringResource(Res.string.cloud_form_endpoint_help),
        error = if (form.errors.endpoint) stringResource(Res.string.cloud_error_endpoint) else null,
    )
    FormField(
        label = stringResource(Res.string.cloud_form_region),
        value = form.region,
        onValueChange = { form.onRegionChange(it) },
        help = stringResource(Res.string.cloud_form_region_help),
        error = if (form.errors.region) stringResource(Res.string.cloud_error_region) else null,
    )
    FormField(
        label = stringResource(Res.string.cloud_form_bucket),
        value = form.bucket,
        onValueChange = { form.bucket = it },
        help = stringResource(Res.string.cloud_form_bucket_help),
        error = if (form.errors.bucket) stringResource(Res.string.cloud_error_bucket) else null,
    )
    FormField(
        label = stringResource(Res.string.cloud_form_access_key),
        value = form.accessKeyId,
        onValueChange = { form.accessKeyId = it },
        help = stringResource(Res.string.cloud_form_access_key_help),
        error = if (form.errors.accessKeyId) stringResource(Res.string.cloud_error_access) else null,
    )
    SecretField(form = form)
    CloudSwitch(
        label = stringResource(Res.string.cloud_form_path_style),
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
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(
            text =
                stringResource(
                    if (form.testing) Res.string.cloud_form_testing else Res.string.cloud_form_test,
                ),
        )
    }
    if (form.testedOk) {
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(Res.string.cloud_form_save))
        }
    }
}

/** One field of the form, with its help under it and its error when it is wrong. */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    help: String,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
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

/** The secret key, hidden until the user asks to see it, with its own switch. */
@Composable
private fun SecretField(form: SetupForm) {
    Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        OutlinedTextField(
            value = form.secretAccessKey,
            onValueChange = { form.secretAccessKey = it },
            label = { Text(text = stringResource(Res.string.cloud_form_secret)) },
            singleLine = true,
            isError = form.errors.secretAccessKey,
            visualTransformation =
                if (form.secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.errors.secretAccessKey) {
            Text(
                text = stringResource(Res.string.cloud_error_secret),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(Res.string.cloud_form_secret_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CloudSwitch(
            label = stringResource(Res.string.cloud_form_show_secret),
            checked = form.secretVisible,
            onCheckedChange = { form.secretVisible = it },
        )
    }
}

/** A setting of the form that is on or off. */
@Composable
private fun CloudSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** How many copies to keep, as a group of radio buttons. */
@Composable
private fun KeepChoices(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(text = stringResource(Res.string.cloud_form_keep), style = MaterialTheme.typography.bodyLarge)
    KEEP_CHOICES.forEach { choice ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTarget)
                    .selectable(selected = selected == choice, role = Role.RadioButton, onClick = { onSelect(choice) }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            RadioButton(selected = selected == choice, onClick = null)
            Text(
                text = stringResource(Res.string.cloud_form_keep_option, choice),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** The state of the configured service: where the copies go, when the last one was, and what to do. */
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun StatusBody(
    setup: S3Setup,
    source: CloudBackupSource?,
    status: CloudBackupStatus?,
    copyMessage: CopyMessage?,
    syncBusy: Boolean,
    syncNotice: SyncSwitchOutcome?,
    onKeep: (Int) -> Unit,
    onCopyNow: () -> Unit,
    onToggleSync: (Boolean) -> Unit,
    onChangeService: () -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    val row = RowStatus(setup.bucket)
    Text(text = stringResource(Res.string.cloud_status_service), style = MaterialTheme.typography.bodyLarge)
    Text(
        text = stringResource(Res.string.cloud_status_bucket, row.bucket.orEmpty()),
        style = MaterialTheme.typography.bodyLarge,
    )
    val lastSuccess = status?.lastSuccess
    Text(
        text =
            if (lastSuccess == null) {
                stringResource(Res.string.cloud_status_no_copy)
            } else {
                stringResource(Res.string.cloud_status_last_copy, lastCopyMoment(lastSuccess))
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
    copyMessage?.let { message ->
        Text(
            text =
                when (message) {
                    CopyMessage.Success -> stringResource(Res.string.cloud_status_copy_done)
                    is CopyMessage.Failure -> errorMessage(message.error)
                },
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (message == CopyMessage.Success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
    }
    KeepChoices(selected = setup.keep, onSelect = onKeep)
    Button(
        onClick = onCopyNow,
        enabled = source != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(Res.string.cloud_status_copy_now))
    }
    if (source == null) {
        Text(
            text = stringResource(Res.string.cloud_status_copy_now_needs_vault),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CloudSyncSection(
        enabled = setup.syncEnabled,
        busy = syncBusy,
        notice = syncNotice,
        onToggle = onToggleSync,
    )
    OutlinedButton(
        onClick = onChangeService,
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(Res.string.cloud_status_change_service))
    }
    OutlinedButton(
        onClick = onDisconnect,
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(Res.string.cloud_disconnect))
    }
    TextButton(
        onClick = onClose,
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(Res.string.cloud_close))
    }
}

/** What a connection test answered, in words. */
@Composable
private fun testMessage(result: ConnectionResult): String =
    stringResource(
        when (result) {
            ConnectionResult.Ok -> Res.string.cloud_test_ok
            ConnectionResult.NotAuthorized -> Res.string.cloud_test_not_authorized
            ConnectionResult.WrongBucketOrEndpoint -> Res.string.cloud_test_wrong_bucket
            ConnectionResult.Unavailable -> Res.string.cloud_test_unavailable
        },
    )

/** What a failed backup means, in words. */
@Composable
private fun errorMessage(error: BackupError): String =
    stringResource(
        when (error) {
            BackupError.NotAuthorized -> Res.string.cloud_status_error_not_authorized
            BackupError.Unavailable -> Res.string.cloud_status_error_unavailable
            BackupError.Rejected, BackupError.Unknown -> Res.string.cloud_status_error_generic
        },
    )

/** The moment of a copy, in the local date and time of the computer. */
private fun lastCopyMoment(instant: Instant): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(instant)

/** Opens [url] in the system browser; only a web address with a host is ever opened. */
private fun openInBrowser(url: String) {
    val uri = browsableUri(url) ?: return
    try {
        Desktop.getDesktop().browse(uri)
    } catch (ignored: IOException) {
        Unit
    } catch (ignored: UnsupportedOperationException) {
        Unit
    }
}
