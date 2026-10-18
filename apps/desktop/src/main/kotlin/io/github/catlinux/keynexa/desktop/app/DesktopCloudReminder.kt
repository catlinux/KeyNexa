package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.cloud.ReminderPolicy
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.desktop.DesktopCloudBackup
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_reminder_configure
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_reminder_later
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_reminder_title
import org.jetbrains.compose.resources.stringResource
import java.time.Instant

private val ReminderPadding = 12.dp
private val ReminderGap = 8.dp
private val ReminderTouch = 44.dp
private val ReminderShape = RoundedCornerShape(12.dp)

/** How long "Later" hides the reminder. */
private const val SNOOZE_DAYS = 7L

/** The state the reminder is shown from, read off the interface thread. */
internal data class ReminderState(
    val configured: Boolean,
    val hasBackup: Boolean,
    val snoozedUntil: Instant?,
)

/** True when the reminder should be shown for [state] at [now]. */
internal fun reminderVisible(
    state: ReminderState,
    now: Instant,
): Boolean =
    ReminderPolicy.shouldShow(
        configured = state.configured,
        hasBackup = state.hasBackup,
        snoozedUntil = state.snoozedUntil,
        now = now,
    )

/** Reads what the reminder needs from the cloud stores; meant to run off the interface thread. */
internal fun reminderState(cloud: DesktopCloudBackup): ReminderState =
    ReminderState(
        configured =
            cloud.stores.consent.accepted() &&
                cloud.stores.credentials
                    .load()
                    ?.let(S3Setup::fromValues) != null,
        hasBackup = cloud.hasAnyBackup(),
        snoozedUntil = cloud.stores.reminder.snoozedUntil(),
    )

/**
 * The discreet card that invites the user to keep a cloud copy while none is configured or none exists. "Set up"
 * opens the cloud assistant and "Later" hides the card for a week.
 */
@Composable
internal fun DesktopCloudReminder(
    onConfigure: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ReminderShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(ReminderPadding),
            verticalArrangement = Arrangement.spacedBy(ReminderGap),
        ) {
            Text(
                text = stringResource(Res.string.cloud_reminder_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ReminderGap)) {
                Button(onClick = onConfigure, modifier = Modifier.heightIn(min = ReminderTouch)) {
                    Text(text = stringResource(Res.string.cloud_reminder_configure))
                }
                TextButton(onClick = onLater, modifier = Modifier.heightIn(min = ReminderTouch)) {
                    Text(text = stringResource(Res.string.cloud_reminder_later))
                }
            }
        }
    }
}

/** The moment "Later" hides the reminder until. */
internal fun snoozeUntil(now: Instant): Instant = now.plusSeconds(SNOOZE_DAYS * SECONDS_PER_DAY)

private const val SECONDS_PER_DAY = 24L * 60L * 60L
