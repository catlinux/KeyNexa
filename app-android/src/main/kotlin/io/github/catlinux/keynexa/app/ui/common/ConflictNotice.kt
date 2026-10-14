package io.github.catlinux.keynexa.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    /** True when copies are waiting for another password, so the user can type it. */
    val canEnterOtherPassword: Boolean = false,
    /** True when the password typed for those copies was rejected. */
    val otherPasswordRejected: Boolean = false,
)

/**
 * The notice about the Syncthing conflict copies of the open vault: what was merged, the offer to delete the copies
 * and the copies that could not be merged. A card and not a dialog: the vault stays readable behind it.
 *
 * The caller owns the answers: this holds no state, never touches the vault and never deletes anything by itself.
 * Copies are only ever deleted after the user asks for it, and a password typed for a copy is handed back and not
 * kept here.
 */
@Composable
fun ConflictNoticeBar(
    notice: ConflictNotice,
    onDeleteCopies: () -> Unit,
    onMergeOtherPassword: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var askingForPassword by remember { mutableStateOf(false) }
    val troubles = notice.problems.isNotEmpty()
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = if (troubles) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (troubles) scheme.onErrorContainer else scheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(PanelPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            NoticeMessages(notice)
            NoticeActions(
                notice = notice,
                onDeleteCopies = onDeleteCopies,
                onEnterPassword = { askingForPassword = true },
                onDismiss = onDismiss,
            )
        }
    }
    if (askingForPassword) {
        OtherPasswordDialog(
            onMerge = { password ->
                onMergeOtherPassword(password)
                askingForPassword = false
            },
            onDismiss = { askingForPassword = false },
        )
    }
}

/** What the notice says: the offer, why the other copies were left alone, and a rejected password. */
@Composable
private fun NoticeMessages(notice: ConflictNotice) {
    if (notice.mergedNow > 0 || notice.deletable > 0) {
        Text(
            text = offerText(notice),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (notice.problems.isNotEmpty()) {
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
    if (notice.otherPasswordRejected) {
        Text(
            text = stringResource(R.string.conflict_other_password_rejected),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** What the user can answer: delete the merged copies, type the missing password, or keep everything. */
@Composable
private fun NoticeActions(
    notice: ConflictNotice,
    onDeleteCopies: () -> Unit,
    onEnterPassword: () -> Unit,
    onDismiss: () -> Unit,
) {
    val contentColor = LocalContentColor.current
    Row(horizontalArrangement = Arrangement.spacedBy(ItemGap)) {
        if (notice.mergedNow > 0 || notice.deletable > 0) {
            NoticeButton(R.string.conflict_delete_copies, onDeleteCopies, contentColor)
        }
        if (notice.canEnterOtherPassword) {
            NoticeButton(R.string.conflict_enter_other_password, onEnterPassword, contentColor)
        }
        NoticeButton(R.string.conflict_keep_copies, onDismiss, contentColor)
    }
}

@Composable
private fun NoticeButton(
    text: Int,
    onClick: () -> Unit,
    contentColor: Color,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        modifier = Modifier.heightIn(min = TouchTarget),
    ) {
        Text(text = stringResource(text))
    }
}

/**
 * Asks for the password of a copy written with another password. A single field, hidden unless the user reveals it;
 * it never keeps the password after the dialog closes.
 */
@Composable
private fun OtherPasswordDialog(
    onMerge: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    val description = stringResource(R.string.conflict_password_show)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.conflict_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(text = stringResource(R.string.conflict_password_label)) },
                    singleLine = true,
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { onMerge(password) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ItemGap),
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = revealed,
                        onCheckedChange = { revealed = it },
                        modifier = Modifier.semantics { contentDescription = description },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMerge(password) }, enabled = password.isNotEmpty()) {
                Text(text = stringResource(R.string.conflict_password_merge))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.conflict_password_cancel))
            }
        },
    )
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
