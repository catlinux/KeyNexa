package io.github.catlinux.keynexa.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.cloud.CloudBackupService
import io.github.catlinux.keynexa.app.cloud.CloudReminderStore
import io.github.catlinux.keynexa.app.cloud.CloudStores
import io.github.catlinux.keynexa.cloud.ReminderPolicy
import io.github.catlinux.keynexa.cloud.S3Setup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

private val ReminderGap = 8.dp
private val ReminderTouchTarget = 48.dp
private const val SNOOZE_DAYS = 7L

/**
 * A small, never-modal card that invites the user to keep a cloud copy while none exists or none is configured.
 *
 * It reads the credentials, the backup state and the snooze mark off the composition thread and refreshes when the
 * backup service publishes a new status. The offline variant ships an empty function with the same signature.
 */
@Composable
fun CloudReminder(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serviceStatus by CloudBackupService.status.collectAsState()
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(serviceStatus) {
        show = withContext(Dispatchers.IO) { shouldShowReminder(context) }
    }
    if (!show) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(ReminderGap),
            verticalArrangement = Arrangement.spacedBy(ReminderGap),
        ) {
            Text(
                text = stringResource(R.string.cloud_reminder_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ReminderGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.heightIn(min = ReminderTouchTarget),
                ) {
                    Text(text = stringResource(R.string.cloud_reminder_configure))
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                CloudReminderStore(context).snooze(Instant.now().plus(Duration.ofDays(SNOOZE_DAYS)))
                            }
                            show = false
                        }
                    },
                    modifier = Modifier.heightIn(min = ReminderTouchTarget),
                ) {
                    Text(text = stringResource(R.string.cloud_reminder_later))
                }
            }
        }
    }
}

private fun shouldShowReminder(context: android.content.Context): Boolean {
    val configured = CloudStores.credentials(context).load()?.let(S3Setup::fromValues) != null
    val hasBackup = CloudBackupService.hasAnyBackup(context)
    return ReminderPolicy.shouldShow(
        configured = configured,
        hasBackup = hasBackup,
        snoozedUntil = CloudReminderStore(context).snoozedUntil(),
        now = Instant.now(),
    )
}
