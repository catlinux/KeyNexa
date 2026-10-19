@file:Suppress("MatchingDeclarationName")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.catlinux.keynexa.desktop.CloudSyncStatus
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_failed
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_synced
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_sync_syncing
import io.github.catlinux.keynexa.sync.SyncError
import org.jetbrains.compose.resources.stringResource
import java.time.Instant

/** The discreet line of the top bar that says what the cloud synchronisation is doing. */
internal sealed interface SyncIndicator {
    /** Nothing to say: synchronisation is off or no round has happened yet. */
    data object Hidden : SyncIndicator

    /** A round is running right now. */
    data object Running : SyncIndicator

    /** The last round left the vault in agreement with the cloud, [ago] time ago. */
    data class Synced(
        val ago: AgoText,
    ) : SyncIndicator

    /** The last round failed with [error]. */
    data class Failed(
        val error: SyncError,
    ) : SyncIndicator
}

/**
 * What [status] means at [now]. A running round wins; then a stored error, because a success clears the error, so an
 * error still there is the newest word unless a strictly newer success came after it.
 */
internal fun syncIndicator(
    status: CloudSyncStatus,
    now: Instant,
): SyncIndicator {
    if (status.running) return SyncIndicator.Running
    val error = prevailingError(status)
    if (error != null) return SyncIndicator.Failed(error)
    val lastSuccess = status.lastSuccess ?: return SyncIndicator.Hidden
    return SyncIndicator.Synced(agoText(now.toEpochMilli() - lastSuccess.toEpochMilli()))
}

/** The error that still stands: one with no newer success, or with no success or error time to compare. */
private fun prevailingError(status: CloudSyncStatus): SyncError? {
    val error = status.lastError ?: return null
    val lastSuccess = status.lastSuccess ?: return error
    val lastErrorAt = status.lastErrorAt ?: return error
    return if (lastSuccess.isAfter(lastErrorAt)) null else error
}

/** The indicator in words, or null when there is nothing to show. */
@Composable
internal fun syncIndicatorText(indicator: SyncIndicator): String? =
    when (indicator) {
        SyncIndicator.Hidden -> null
        SyncIndicator.Running -> stringResource(Res.string.cloud_sync_syncing)
        is SyncIndicator.Synced -> stringResource(Res.string.cloud_sync_synced, agoLabel(indicator.ago))
        is SyncIndicator.Failed -> stringResource(Res.string.cloud_sync_failed, syncErrorLabel(indicator.error))
    }

/** The indicator as a single quiet line, or nothing when it is hidden. */
@Composable
internal fun SyncIndicatorLabel(
    indicator: SyncIndicator,
    modifier: Modifier = Modifier,
) {
    val text = syncIndicatorText(indicator) ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color =
            if (indicator is SyncIndicator.Failed) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = INDICATOR_ALPHA)
            },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = text },
    )
}

/** How quiet the line is against the primary colour of the bar, as [SaveStatusLine] shows it. */
private const val INDICATOR_ALPHA = 0.85f
