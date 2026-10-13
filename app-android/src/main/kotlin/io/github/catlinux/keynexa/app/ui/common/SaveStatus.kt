package io.github.catlinux.keynexa.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.sync.SaveFailure

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ItemGap = 8.dp
private val PanelPadding = 12.dp
private val TouchTarget = 48.dp
private val ProgressSize = 20.dp

/** What the save indicator of an open vault needs to know. */
data class SaveStatus(
    /** True while a save is running; Argon2-free writes are quick, but a merge is not. */
    val saving: Boolean,
    /** Why the last save did not finish, or null when there is nothing wrong. */
    val problem: SaveFailure?,
    /** True while the vault in memory differs from the file on disk. */
    val hasUnsavedChanges: Boolean,
)

/**
 * The status of the open vault's save: nothing when all is well, the running save, or a banner that explains a
 * failure and offers to try again. It holds no state and never touches the vault: the caller passes the status and
 * keeps the ownership of the retry.
 */
@Composable
fun SaveStatusBar(
    status: SaveStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val problem = status.problem
    when {
        problem != null -> ProblemBanner(problem = problem, onRetry = onRetry, modifier = modifier)
        status.saving -> SavingLine(modifier = modifier)
    }
}

/** The quiet line that says a save is running. */
@Composable
private fun SavingLine(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.save_saving)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        CircularProgressIndicator(
            modifier =
                Modifier
                    .size(ProgressSize)
                    .semantics { contentDescription = description },
        )
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
    }
}

/** What could not be saved, why, and the reassurance that nothing has been lost. */
@Composable
private fun ProblemBanner(
    problem: SaveFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(PanelPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = stringResource(R.string.save_problem_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(problemMessage(problem)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.save_changes_kept),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.heightIn(min = TouchTarget),
            ) {
                Text(text = stringResource(R.string.save_retry))
            }
        }
    }
}

/** The plain-language reason of each way a save can fail. */
private fun problemMessage(problem: SaveFailure): Int =
    when (problem) {
        SaveFailure.ExternalChangeNeedsMerge -> R.string.save_problem_external_change
        SaveFailure.Unmergeable -> R.string.save_problem_unmergeable
        SaveFailure.ChangedDuringSave -> R.string.save_problem_changed_during_save
        SaveFailure.FileUnreadable -> R.string.save_problem_file_unreadable
        SaveFailure.CredentialsChanged -> R.string.save_problem_credentials_changed
        SaveFailure.EncodeFailed -> R.string.save_problem_encode_failed
        SaveFailure.StorageError -> R.string.save_problem_storage_error
        SaveFailure.WriteNotVerified -> R.string.save_problem_write_not_verified
    }
