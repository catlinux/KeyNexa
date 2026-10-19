package io.github.catlinux.keynexa.app.cloud

import io.github.catlinux.keynexa.sync.SyncError
import java.time.Instant

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val NO_MILLIS = 0L

/** How long ago something happened, in the units the state line speaks in. */
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

/** What the synchronisation state line of the cloud assistant says. */
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
 * The note the state line shows for [enabled], [hasVault], [lastSuccess] and [lastError] at [now].
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

/** The state the synchronisation section is shown from, read off the interface thread. */
internal data class SyncCardState(
    val hasVault: Boolean = false,
    val lastSuccess: Instant? = null,
    val lastError: SyncError? = null,
)
