package io.github.catlinux.keynexa.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.cloud.CloudConsentStore
import io.github.catlinux.keynexa.app.cloud.CloudStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CloudPadding = 16.dp
private val CloudGap = 8.dp
private val CloudTouchTarget = 48.dp

/**
 * The cloud copies entry of the settings screen: consent first, then a state with a way to disconnect.
 *
 * The consent mark is read and written off the composition thread. Only the cloud variant has this code; the
 * offline variant ships an empty function with the same signature.
 */
@Suppress("LongMethod")
@Composable
fun CloudSettingsEntry() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showing by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf<Boolean?>(null) }
    var confirmingDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accepted = withContext(Dispatchers.IO) { CloudConsentStore(context).accepted() }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CloudTouchTarget)
                .clickable { showing = true }
                .padding(vertical = CloudGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.cloud_settings_entry), style = MaterialTheme.typography.bodyLarge)
    }

    if (showing) {
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
                    Text(
                        text = stringResource(R.string.cloud_consent_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    when (accepted) {
                        null -> Unit
                        false ->
                            ConsentBody(
                                onAccept = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { CloudConsentStore(context).accept() }
                                        accepted = true
                                    }
                                },
                                onDecline = { showing = false },
                            )
                        true ->
                            ConfiguredBody(
                                onDisconnect = { confirmingDisconnect = true },
                                onClose = { showing = false },
                            )
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
                            accepted = false
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

/** What is sent, where, why and how to stop, with the two consent buttons. */
@Composable
private fun ConsentBody(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val paragraphs =
        listOf(
            R.string.cloud_consent_sent,
            R.string.cloud_consent_where,
            R.string.cloud_consent_why,
            R.string.cloud_consent_no_servers,
            R.string.cloud_consent_old_copies,
            R.string.cloud_consent_stop,
            R.string.cloud_consent_offline,
        )
    paragraphs.forEach { paragraph ->
        Text(text = stringResource(paragraph), style = MaterialTheme.typography.bodyMedium)
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

/** The accepted state: no service is configured yet, and the way to disconnect and delete everything. */
@Composable
private fun ConfiguredBody(
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    Text(text = stringResource(R.string.cloud_status_no_service), style = MaterialTheme.typography.bodyMedium)
    Button(
        onClick = onDisconnect,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_disconnect))
    }
    OutlinedButton(
        onClick = onClose,
        modifier = Modifier.fillMaxWidth().heightIn(min = CloudTouchTarget),
    ) {
        Text(text = stringResource(R.string.cloud_close))
    }
}
