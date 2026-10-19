package io.github.catlinux.keynexa.app.cloud.ui.settings

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.cloud.AgoText
import io.github.catlinux.keynexa.app.cloud.SyncCardNote
import io.github.catlinux.keynexa.cloud.ProbeResult
import io.github.catlinux.keynexa.sync.CloudAuthException
import io.github.catlinux.keynexa.sync.SyncError

private val SyncTouchTarget = 48.dp
private val SyncItemGap = 8.dp

/** What the conditional-write probe means for the switch of the assistant (ADR-0009 §3). */
internal enum class SyncProbeOutcome {
    /** The service honours conditional writes; synchronisation can be turned on. */
    Enabled,

    /** The service ignored or refused a conditional write; only copies can be used. */
    NotSupported,

    /** The credentials were rejected; the user has to check them. */
    NotAuthorized,

    /** The probe could not finish for another reason, such as the network. */
    Unavailable,
}

/** What the probe answered, as the outcome the assistant shows. */
internal fun syncProbeOutcome(result: ProbeResult): SyncProbeOutcome =
    when (result) {
        ProbeResult.Supported -> SyncProbeOutcome.Enabled
        is ProbeResult.NotSupported -> SyncProbeOutcome.NotSupported
        is ProbeResult.Failed ->
            if (result.error is CloudAuthException) {
                SyncProbeOutcome.NotAuthorized
            } else {
                SyncProbeOutcome.Unavailable
            }
    }

/**
 * The "Synchronisation" section of the assistant's state step: the switch, what it does and what the probe answered.
 * While the probe is running the switch is disabled and a short "checking" line is shown.
 */
@Composable
internal fun CloudSyncSection(
    enabled: Boolean,
    busy: Boolean,
    notice: SyncProbeOutcome?,
    note: SyncCardNote,
    onToggle: (Boolean) -> Unit,
) {
    Text(
        text = stringResource(R.string.cloud_sync_section),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    val label = stringResource(R.string.cloud_sync_switch)
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
        text = stringResource(R.string.cloud_sync_explain),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (enabled) {
        Text(
            text = syncNoteText(note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (busy) {
        Text(
            text = stringResource(R.string.cloud_sync_checking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (notice != null) {
        Text(
            text = noticeText(notice),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (notice == SyncProbeOutcome.Enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
    }
}

/** The note of the synchronisation state line, in words. */
@Composable
internal fun syncNoteText(note: SyncCardNote): String =
    when (note) {
        SyncCardNote.Disabled -> stringResource(R.string.cloud_sync_state_off)
        SyncCardNote.Enabled -> stringResource(R.string.cloud_sync_state_on)
        SyncCardNote.Pending -> stringResource(R.string.cloud_sync_state_pending)
        is SyncCardNote.LastSync -> stringResource(R.string.cloud_sync_state_last, agoLabel(note.ago))
        is SyncCardNote.Failed -> stringResource(R.string.cloud_sync_state_error, syncErrorLabel(note.error))
    }

/** How long ago the last synchronisation was, in words. */
@Composable
internal fun agoLabel(ago: AgoText): String =
    when (ago) {
        AgoText.JustNow -> stringResource(R.string.cloud_sync_ago_now)
        is AgoText.Minutes -> pluralStringResource(R.plurals.cloud_sync_ago_minutes, ago.value.toInt(), ago.value)
        is AgoText.Hours -> pluralStringResource(R.plurals.cloud_sync_ago_hours, ago.value.toInt(), ago.value)
        is AgoText.Days -> pluralStringResource(R.plurals.cloud_sync_ago_days, ago.value.toInt(), ago.value)
    }

/** A failed synchronisation's cause, in a few words for the state line. */
@Composable
internal fun syncErrorLabel(error: SyncError): String =
    stringResource(
        when (error) {
            SyncError.Busy -> R.string.cloud_sync_error_busy
            SyncError.NotAuthorized -> R.string.cloud_sync_error_not_authorized
            SyncError.Unavailable -> R.string.cloud_sync_error_unavailable
            SyncError.NotSupported -> R.string.cloud_sync_error_not_supported
            SyncError.Unreadable -> R.string.cloud_sync_error_unreadable
            SyncError.InvalidSyncId -> R.string.cloud_sync_error_invalid_id
            SyncError.OtherPassword -> R.string.cloud_sync_error_other_password
            SyncError.NotSameVault -> R.string.cloud_sync_error_other_vault
            SyncError.SaveFailed -> R.string.cloud_sync_error_save_failed
        },
    )

/** The message a probe outcome shows, in the words the assistant uses. */
@Composable
private fun noticeText(outcome: SyncProbeOutcome): String =
    stringResource(
        when (outcome) {
            SyncProbeOutcome.Enabled -> R.string.cloud_sync_on
            SyncProbeOutcome.NotSupported -> R.string.cloud_sync_unsupported
            SyncProbeOutcome.NotAuthorized -> R.string.cloud_sync_error_auth
            SyncProbeOutcome.Unavailable -> R.string.cloud_sync_error_network
        },
    )
