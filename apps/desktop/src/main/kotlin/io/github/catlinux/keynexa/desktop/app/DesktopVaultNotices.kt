package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_changes_kept
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_recovered_discard
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_recovered_restore
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_recovered_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_retry
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.sync.RecoveryOutcome
import io.github.catlinux.keynexa.sync.SaveFailure
import org.jetbrains.compose.resources.stringResource

private val StripPadding = 12.dp
private val StripGap = 8.dp
private val StripShape = RoundedCornerShape(12.dp)
private val StripTouchTarget = 44.dp

/**
 * The strips under the top bar, over the whole window: what could not be saved with the way to try again, and the
 * offer to bring back the changes of a session that did not finish saving. Nothing is drawn when there is neither.
 */
@Composable
internal fun VaultNotices(
    state: SessionState.Open,
    onRetrySave: () -> Unit,
    onRestoreUnsaved: () -> Unit,
    onDiscardUnsaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val problem = state.saveProblem
    val recovered = hasRecoveredUnsaved(state.recovered)
    if (problem == null && !recovered) return
    Column(
        modifier = modifier.fillMaxWidth().padding(StripPadding),
        verticalArrangement = Arrangement.spacedBy(StripGap),
    ) {
        if (problem != null) SaveProblemPanel(problem = problem, onRetry = onRetrySave)
        if (recovered) RecoveredPanel(onRestore = onRestoreUnsaved, onDiscard = onDiscardUnsaved)
    }
}

/** True when recovery found changes that can still be restored or discarded. */
private fun hasRecoveredUnsaved(recovered: RecoveryOutcome?): Boolean =
    recovered is RecoveryOutcome.SaveHadNotStarted ||
        recovered is RecoveryOutcome.ChangedElsewhere ||
        recovered is RecoveryOutcome.Restored ||
        recovered is RecoveryOutcome.UnsavedCopyKept

/** What could not be saved, why, and the reassurance that nothing has been lost. */
@Composable
private fun SaveProblemPanel(
    problem: SaveFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StripShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(StripPadding),
            verticalArrangement = Arrangement.spacedBy(StripGap),
        ) {
            Text(
                text = stringResource(Res.string.save_problem_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(saveProblemMessage(problem)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(Res.string.save_changes_kept),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.heightIn(min = StripTouchTarget),
            ) {
                Text(text = stringResource(Res.string.save_retry))
            }
        }
    }
}

/** The offer to bring back the changes of a session that did not finish saving. */
@Composable
private fun RecoveredPanel(
    onRestore: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StripShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(StripPadding),
            verticalArrangement = Arrangement.spacedBy(StripGap),
        ) {
            Text(
                text = stringResource(Res.string.save_recovered_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(StripGap)) {
                TextButton(onClick = onRestore) {
                    Text(text = stringResource(Res.string.save_recovered_restore))
                }
                TextButton(onClick = onDiscard) {
                    Text(text = stringResource(Res.string.save_recovered_discard))
                }
            }
        }
    }
}
