@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_untitled
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_lock
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_section_entries
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_section_groups
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_status_saved
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_status_unsaved
import io.github.catlinux.keynexa.desktop.app.generated.resources.open_vault_empty
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_changes_kept
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_recovered_discard
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_recovered_restore
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_recovered_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_retry
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_saving
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.sync.RecoveryOutcome
import io.github.catlinux.keynexa.sync.SaveFailure
import io.github.catlinux.keynexa.vault.Group
import org.jetbrains.compose.resources.stringResource

/** Spacing and shapes of the design grid (docs/DESIGN.md), as on Android. */
private val ScreenPadding = 20.dp
private val BlockGap = 16.dp
private val ItemGap = 8.dp
private val RowGap = 12.dp
private val TextGap = 2.dp
private val PanelPadding = 12.dp
private val CardPaddingHorizontal = 16.dp
private val CardPaddingVertical = 12.dp
private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(14.dp)
private val TouchTarget = 48.dp
private val ContentWidth = 480.dp
private const val CARD_BORDER_ALPHA = 0.4f
private const val DIVIDER_ALPHA = 0.2f

/**
 * The vault that is open, in a minimal form: the vault name with its save status and a Lock button, any save
 * problem or recovered unsaved copy, and the root group's subgroups and entries by name. Navigation and editing
 * come later.
 */
@Suppress("LongParameterList")
@Composable
fun DesktopOpenVaultScreen(
    state: SessionState.Open,
    onLock: () -> Unit,
    onRetrySave: () -> Unit,
    onRestoreUnsaved: () -> Unit,
    onDiscardUnsaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = ContentWidth).fillMaxHeight().padding(horizontal = ScreenPadding),
        ) {
            OpenVaultTopBar(state = state, onLock = onLock)
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(BlockGap),
            ) {
                val problem = state.saveProblem
                if (problem != null) SaveProblemPanel(problem = problem, onRetry = onRetrySave)
                if (hasRecoveredUnsaved(state.recovered)) {
                    RecoveredPanel(onRestore = onRestoreUnsaved, onDiscard = onDiscardUnsaved)
                }
                VaultContent(group = state.current.root)
            }
        }
    }
}

/** True when recovery found changes that can still be restored or discarded. */
private fun hasRecoveredUnsaved(recovered: RecoveryOutcome?): Boolean =
    recovered is RecoveryOutcome.SaveHadNotStarted ||
        recovered is RecoveryOutcome.ChangedElsewhere ||
        recovered is RecoveryOutcome.Restored ||
        recovered is RecoveryOutcome.UnsavedCopyKept

/** The vault name, its save status and the way to lock it again. */
@Composable
private fun OpenVaultTopBar(
    state: SessionState.Open,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = BlockGap, bottom = ItemGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TextGap),
        ) {
            Text(
                text = state.vault.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SaveStatusLine(
                saving = state.saving,
                hasUnsavedChanges = state.hasUnsavedChanges,
                problem = state.saveProblem,
            )
        }
        OutlinedButton(onClick = onLock, shape = FieldShape) {
            Text(text = stringResource(Res.string.group_lock))
        }
    }
}

/** The small line under the vault name: saved, saving, unsaved or waiting for a retry. */
@Composable
private fun SaveStatusLine(
    saving: Boolean,
    hasUnsavedChanges: Boolean,
    problem: SaveFailure?,
    modifier: Modifier = Modifier,
) {
    val text =
        when {
            problem != null -> Res.string.save_problem_title
            saving -> Res.string.save_saving
            hasUnsavedChanges -> Res.string.group_status_unsaved
            else -> Res.string.group_status_saved
        }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** What could not be saved, why, and the reassurance that nothing has been lost. */
@Composable
private fun SaveProblemPanel(
    problem: SaveFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(PanelPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
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
                modifier = Modifier.heightIn(min = TouchTarget),
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
        shape = CardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(PanelPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = stringResource(Res.string.save_recovered_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ItemGap)) {
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

/** The root group's subgroups and entries, by name only. */
@Composable
private fun VaultContent(
    group: Group,
    modifier: Modifier = Modifier,
) {
    if (group.groups.isEmpty() && group.entries.isEmpty()) {
        Text(
            text = stringResource(Res.string.open_vault_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = BlockGap),
        )
        return
    }
    val untitled = stringResource(Res.string.entry_detail_untitled)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BlockGap),
    ) {
        if (group.groups.isNotEmpty()) {
            SectionLabel(text = stringResource(Res.string.group_section_groups))
            NameCard(names = group.groups.map { it.name })
        }
        if (group.entries.isNotEmpty()) {
            SectionLabel(text = stringResource(Res.string.group_section_entries))
            NameCard(names = group.entries.map { it.title.ifEmpty { untitled } })
        }
    }
}

/** The word above a section of the list. */
@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(bottom = ItemGap),
    )
}

/** A card of names, with a separator between rows. */
@Composable
private fun NameCard(
    names: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column {
            names.forEachIndexed { index, name ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CardPaddingHorizontal, vertical = CardPaddingVertical),
                )
            }
        }
    }
}
