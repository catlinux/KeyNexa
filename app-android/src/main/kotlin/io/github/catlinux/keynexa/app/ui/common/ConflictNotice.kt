package io.github.catlinux.keynexa.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.sync.CopyProblem

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ItemGap = 8.dp
private val PanelPadding = 12.dp
private val TouchTarget = 48.dp

/** What the conflict-copy notice of an open vault needs to know (ADR-0007). */
data class ConflictNotice(
    /** How many copies were merged into the vault just now; 0 when they were merged on an earlier open. */
    val mergedNow: Int,
    /** How many of the merged copies can still be deleted. */
    val deletable: Int,
    /** Why the copies that could not be merged were left alone, each reason once. */
    val problems: List<CopyProblem>,
)

/**
 * The notice about the Syncthing conflict copies of the open vault: what was merged, the offer to delete the copies
 * and the copies that could not be merged. A card and not a dialog: the vault stays readable behind it.
 *
 * The caller owns the answers: this holds no state, never touches the vault and never deletes anything by itself.
 * Copies are only ever deleted after the user asks for it.
 */
@Composable
fun ConflictNoticeBar(
    notice: ConflictNotice,
    onDeleteCopies: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offer = notice.mergedNow > 0 || notice.deletable > 0
    val troubles = notice.problems.isNotEmpty()
    val scheme = MaterialTheme.colorScheme
    val container = if (troubles) scheme.errorContainer else scheme.secondaryContainer
    val onContainer = if (troubles) scheme.onErrorContainer else scheme.onSecondaryContainer
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = onContainer,
    ) {
        Column(
            modifier = Modifier.padding(PanelPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            if (offer) {
                Text(
                    text = offerText(notice),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (troubles) {
                Text(
                    text = stringResource(R.string.conflict_problems_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                notice.problems.forEach { problem ->
                    Text(
                        text = stringResource(problemMessage(problem)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ItemGap)) {
                if (offer) {
                    TextButton(
                        onClick = onDeleteCopies,
                        colors = ButtonDefaults.textButtonColors(contentColor = onContainer),
                        modifier = Modifier.heightIn(min = TouchTarget),
                    ) {
                        Text(text = stringResource(R.string.conflict_delete_copies))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = onContainer),
                    modifier = Modifier.heightIn(min = TouchTarget),
                ) {
                    Text(text = stringResource(R.string.conflict_keep_copies))
                }
            }
        }
    }
}

/** How many copies were merged, asking whether to delete them. */
@Composable
private fun offerText(notice: ConflictNotice): String =
    if (notice.mergedNow > 0) {
        pluralStringResource(R.plurals.conflict_merged_now, notice.mergedNow, notice.mergedNow)
    } else {
        pluralStringResource(R.plurals.conflict_merged_before, notice.deletable, notice.deletable)
    }

/** The plain-language reason of each way a copy can refuse to be merged. */
private fun problemMessage(problem: CopyProblem): Int =
    when (problem) {
        CopyProblem.OtherPassword -> R.string.conflict_problem_other_password
        CopyProblem.Damaged -> R.string.conflict_problem_damaged
        CopyProblem.OtherVault -> R.string.conflict_problem_other_vault
        CopyProblem.Unreadable -> R.string.conflict_problem_unreadable
    }
