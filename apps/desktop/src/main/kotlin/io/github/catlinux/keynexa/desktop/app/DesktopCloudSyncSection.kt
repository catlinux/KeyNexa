package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.cloud.ProbeResult
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.desktop.DesktopCloudBackup
import io.github.catlinux.keynexa.desktop.DesktopCloudSync
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_ago_days
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_ago_hours
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_ago_minutes
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_ago_now
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_checking
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_auth
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_busy
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_invalid_id
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_network
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_not_authorized
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_not_supported
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_other_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_other_vault
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_save_failed
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_unavailable
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_error_unreadable
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_explain
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_on
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_section
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_switch
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_unsupported
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud_sync_error
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud_sync_last
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud_sync_off
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud_sync_on
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud_sync_pending
import io.github.catlinux.keynexa.sync.CloudAuthException
import io.github.catlinux.keynexa.sync.SyncError
import io.github.catlinux.keynexa.sync.SyncLocal
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.Instant

private val SyncTouchTarget = 44.dp
private val SyncItemGap = 8.dp

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val NO_MILLIS = 0L

/** What the conditional-write probe means for the switch of the assistant (ADR-0009 §3). */
internal sealed interface SyncSwitchOutcome {
    /** The service honours conditional writes; synchronisation can be turned on. */
    data object Enabled : SyncSwitchOutcome

    /** The service ignored or refused a conditional write; only copies can be used. */
    data object NotSupported : SyncSwitchOutcome

    /** The credentials were rejected; the user has to check them. */
    data object NotAuthorized : SyncSwitchOutcome

    /** The probe could not finish for another reason, such as the network. */
    data object Unavailable : SyncSwitchOutcome
}

/** What the probe answered, as the outcome the assistant shows. */
internal fun syncSwitchOutcome(result: ProbeResult): SyncSwitchOutcome =
    when (result) {
        ProbeResult.Supported -> SyncSwitchOutcome.Enabled
        is ProbeResult.NotSupported -> SyncSwitchOutcome.NotSupported
        is ProbeResult.Failed ->
            if (result.error is CloudAuthException) {
                SyncSwitchOutcome.NotAuthorized
            } else {
                SyncSwitchOutcome.Unavailable
            }
    }

/** How long ago something happened, in the units the card speaks in. */
internal sealed interface AgoText {
    /** Less than a minute, including a moment in the future. */
    data object JustNow : AgoText

    data class Minutes(
        val value: Long,
    ) : AgoText

    data class Hours(
        val value: Long,
    ) : AgoText

    data class Days(
        val value: Long,
    ) : AgoText
}

/** [millisAgo] as a rounded-down amount of minutes, hours or days; a negative value is "just now". */
internal fun agoText(millisAgo: Long): AgoText =
    when {
        millisAgo < MILLIS_PER_MINUTE -> AgoText.JustNow
        millisAgo < MILLIS_PER_HOUR -> AgoText.Minutes(millisAgo / MILLIS_PER_MINUTE)
        millisAgo < MILLIS_PER_DAY -> AgoText.Hours(millisAgo / MILLIS_PER_HOUR)
        else -> AgoText.Days(millisAgo / MILLIS_PER_DAY)
    }

/** What the synchronisation card of the settings says under its title. */
internal sealed interface SyncCardNote {
    /** The switch is off. */
    data object Disabled : SyncCardNote

    /** The switch is on, but no vault is open to show a state for. */
    data object Enabled : SyncCardNote

    /** The switch is on and the open vault has not synchronised yet. */
    data object Pending : SyncCardNote

    /** The last round left the local file and the remote object in agreement, [ago] time ago. */
    data class LastSync(
        val ago: AgoText,
    ) : SyncCardNote

    /** The last round failed with [error]. */
    data class Failed(
        val error: SyncError,
    ) : SyncCardNote
}

/**
 * The note the card shows for [enabled], [hasVault], [lastSuccess] and [lastError] at [now].
 *
 * A stored error wins over a success, because a success clears the error: an error still there is the newest word.
 */
internal fun syncCardNote(
    enabled: Boolean,
    hasVault: Boolean,
    lastSuccess: Instant?,
    lastError: SyncError?,
    now: Instant,
): SyncCardNote =
    when {
        !enabled -> SyncCardNote.Disabled
        !hasVault -> SyncCardNote.Enabled
        lastError != null -> SyncCardNote.Failed(lastError)
        lastSuccess != null -> {
            val millisAgo = now.toEpochMilli() - lastSuccess.toEpochMilli()
            SyncCardNote.LastSync(agoText(millisAgo.coerceAtLeast(NO_MILLIS)))
        }
        else -> SyncCardNote.Pending
    }

/** The state the synchronisation card is shown from, read off the interface thread. */
internal data class CloudSyncCardState(
    val enabled: Boolean = false,
    val hasVault: Boolean = false,
    val lastSuccess: Instant? = null,
    val lastError: SyncError? = null,
)

/** Reads what the synchronisation card needs from the cloud stores; meant to run off the interface thread. */
internal fun cloudSyncCardState(
    cloud: DesktopCloudBackup,
    cloudSync: DesktopCloudSync,
    local: SyncLocal?,
): CloudSyncCardState {
    val setup =
        cloud.stores.credentials
            .load()
            ?.let(S3Setup::fromValues)
    val status = local?.let(cloudSync::statusOf)
    return CloudSyncCardState(
        enabled = setup?.syncEnabled == true,
        hasVault = local != null,
        lastSuccess = status?.lastSuccess,
        lastError = status?.lastError,
    )
}

/** The message a probe outcome shows, in the words the assistant uses. */
private fun noticeMessage(outcome: SyncSwitchOutcome): StringResource =
    when (outcome) {
        SyncSwitchOutcome.Enabled -> Res.string.cloud_sync_on
        SyncSwitchOutcome.NotSupported -> Res.string.cloud_sync_unsupported
        SyncSwitchOutcome.NotAuthorized -> Res.string.cloud_sync_error_auth
        SyncSwitchOutcome.Unavailable -> Res.string.cloud_sync_error_network
    }

/**
 * The "Synchronisation" section of the assistant's state step: the switch, what it does and what the probe answered.
 * While the probe is running the switch is disabled and a short "checking" line is shown.
 */
@Composable
internal fun CloudSyncSection(
    enabled: Boolean,
    busy: Boolean,
    notice: SyncSwitchOutcome?,
    onToggle: (Boolean) -> Unit,
) {
    Text(
        text = stringResource(Res.string.cloud_sync_section),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    val label = stringResource(Res.string.cloud_sync_switch)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = SyncTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SyncItemGap),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            enabled = !busy,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
    Text(
        text = stringResource(Res.string.cloud_sync_explain),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (busy) {
        Text(
            text = stringResource(Res.string.cloud_sync_checking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (notice != null) {
        Text(
            text = stringResource(noticeMessage(notice)),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (notice == SyncSwitchOutcome.Enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
    }
}

/** The note of the synchronisation card, in words. */
@Composable
internal fun syncNoteText(note: SyncCardNote): String =
    when (note) {
        SyncCardNote.Disabled -> stringResource(Res.string.settings_mode_cloud_sync_off)
        SyncCardNote.Enabled -> stringResource(Res.string.settings_mode_cloud_sync_on)
        SyncCardNote.Pending -> stringResource(Res.string.settings_mode_cloud_sync_pending)
        is SyncCardNote.LastSync -> stringResource(Res.string.settings_mode_cloud_sync_last, agoLabel(note.ago))
        is SyncCardNote.Failed -> stringResource(Res.string.settings_mode_cloud_sync_error, syncErrorLabel(note.error))
    }

/** How long ago the last synchronisation was, in words. */
@Composable
internal fun agoLabel(ago: AgoText): String =
    when (ago) {
        AgoText.JustNow -> stringResource(Res.string.cloud_sync_ago_now)
        is AgoText.Minutes -> pluralStringResource(Res.plurals.cloud_sync_ago_minutes, ago.value.toInt(), ago.value)
        is AgoText.Hours -> pluralStringResource(Res.plurals.cloud_sync_ago_hours, ago.value.toInt(), ago.value)
        is AgoText.Days -> pluralStringResource(Res.plurals.cloud_sync_ago_days, ago.value.toInt(), ago.value)
    }

/** A failed synchronisation's cause, in a few words for the card. */
@Composable
internal fun syncErrorLabel(error: SyncError): String =
    stringResource(
        when (error) {
            SyncError.Busy -> Res.string.cloud_sync_error_busy
            SyncError.NotAuthorized -> Res.string.cloud_sync_error_not_authorized
            SyncError.Unavailable -> Res.string.cloud_sync_error_unavailable
            SyncError.NotSupported -> Res.string.cloud_sync_error_not_supported
            SyncError.Unreadable -> Res.string.cloud_sync_error_unreadable
            SyncError.InvalidSyncId -> Res.string.cloud_sync_error_invalid_id
            SyncError.OtherPassword -> Res.string.cloud_sync_error_other_password
            SyncError.NotSameVault -> Res.string.cloud_sync_error_other_vault
            SyncError.SaveFailed -> Res.string.cloud_sync_error_save_failed
        },
    )
