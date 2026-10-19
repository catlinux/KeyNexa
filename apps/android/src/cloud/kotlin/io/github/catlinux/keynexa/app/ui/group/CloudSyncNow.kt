package io.github.catlinux.keynexa.app.ui.group

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.cloud.CloudStores
import io.github.catlinux.keynexa.app.cloud.CloudSyncService
import io.github.catlinux.keynexa.app.cloud.syncCardNote
import io.github.catlinux.keynexa.app.cloud.ui.settings.syncNoteText
import io.github.catlinux.keynexa.cloud.S3Setup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * The "Sync now" entry of the group menu, with the synchronisation state line under it (ADR-0009 §4).
 *
 * It is shown only when the cloud edition has synchronisation turned on; the offline variant ships an empty function
 * with the same signature. The state is observed so the line refreshes on its own while the menu is open.
 */
@Composable
fun CloudSyncNowMenuItem(onSyncNow: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    val status by CloudSyncService.status.collectAsState()

    LaunchedEffect(Unit) {
        enabled = withContext(Dispatchers.IO) { syncEnabled(context) }
    }
    if (!enabled) return

    DropdownMenuItem(
        onClick = onSyncNow,
        text = {
            Column {
                Text(text = stringResource(R.string.cloud_sync_now))
                Text(
                    text =
                        if (status.running) {
                            stringResource(R.string.cloud_sync_syncing)
                        } else {
                            syncNoteText(
                                syncCardNote(
                                    enabled = true,
                                    hasVault = true,
                                    lastSuccess = status.lastSuccess,
                                    lastError = status.lastError,
                                    now = Instant.now(),
                                ),
                            )
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** True when the credentials are stored and synchronisation is turned on for this service. */
private fun syncEnabled(context: android.content.Context): Boolean {
    val setup = CloudStores.credentials(context).load()?.let(S3Setup::fromValues)
    return setup?.syncEnabled == true
}
