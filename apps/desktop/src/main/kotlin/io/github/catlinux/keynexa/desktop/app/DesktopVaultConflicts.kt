package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_delete_copies
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_enter_other_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_keep_copies
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_merged_before
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_merged_now
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_other_password_rejected
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_password_cancel
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_password_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_password_merge
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_password_show
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_password_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_problem_damaged
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_problem_other_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_problem_other_vault
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_problem_unreadable
import io.github.catlinux.keynexa.desktop.app.generated.resources.conflict_problems_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_hide
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_show
import io.github.catlinux.keynexa.session.ConflictReport
import io.github.catlinux.keynexa.sync.CopyProblem
import io.github.catlinux.keynexa.vault.Credentials
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val BannerPadding = 12.dp
private val BannerGap = 8.dp
private val BannerShape = RoundedCornerShape(12.dp)
private val BannerTouch = 44.dp

/** What the conflict banner shows for one report. */
internal data class ConflictNotice(
    /** Copies that were merged in just now. */
    val mergedNow: Int,
    /** Copies merged in an earlier session that are still next to the vault. */
    val mergedEarlier: Int,
    /** Copies that may be deleted once the user agrees. */
    val deletable: Int,
    /** The copies that could not be merged, each with its reason. */
    val problems: List<CopyProblem>,
    /** Copies waiting for another password. */
    val otherPasswordCopies: Int,
    /** True when the last password tried did not open them. */
    val rejected: Boolean,
)

/** What the banner shows for [report], or null when there is nothing to tell and nothing to offer. */
internal fun conflictNotice(report: ConflictReport?): ConflictNotice? {
    if (report == null || report.isEmpty) return null
    return ConflictNotice(
        mergedNow = report.mergedNow,
        mergedEarlier = if (report.mergedNow > 0) 0 else report.deletable.size,
        deletable = report.deletable.size,
        problems = report.failed,
        otherPasswordCopies = report.otherPasswordCopies.size,
        rejected = report.otherPasswordRejected,
    )
}

/**
 * The notice about the conflict copies found next to the vault: what was merged, what could not be, the copies waiting
 * for another password, and the way to delete the copies that are no longer needed or to leave them alone.
 */
@Composable
internal fun ConflictBanner(
    report: ConflictReport,
    onDeleteCopies: () -> Unit,
    onEnterPassword: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notice = conflictNotice(report) ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BannerShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(BannerPadding),
            verticalArrangement = Arrangement.spacedBy(BannerGap),
        ) {
            Text(
                text = mergedLine(notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (notice.problems.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.conflict_problems_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                notice.problems.forEach { problem ->
                    Text(
                        text = stringResource(problemMessage(problem)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            if (notice.rejected) {
                Text(
                    text = stringResource(Res.string.conflict_other_password_rejected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BannerGap)) {
                if (notice.deletable > 0) {
                    TextButton(onClick = onDeleteCopies, modifier = Modifier.heightIn(min = BannerTouch)) {
                        Text(text = stringResource(Res.string.conflict_delete_copies))
                    }
                }
                if (notice.otherPasswordCopies > 0) {
                    TextButton(onClick = onEnterPassword, modifier = Modifier.heightIn(min = BannerTouch)) {
                        Text(text = stringResource(Res.string.conflict_enter_other_password))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = BannerTouch)) {
                    Text(text = stringResource(Res.string.conflict_keep_copies))
                }
            }
        }
    }
}

/** The line that says how many copies were merged, now or in an earlier session. */
@Composable
private fun mergedLine(notice: ConflictNotice): String =
    if (notice.mergedNow > 0) {
        pluralStringResource(Res.plurals.conflict_merged_now, notice.mergedNow, notice.mergedNow)
    } else {
        pluralStringResource(Res.plurals.conflict_merged_before, notice.mergedEarlier, notice.mergedEarlier)
    }

/** The message of one reason a copy could not be merged. */
@Composable
private fun problemMessage(problem: CopyProblem): StringResource =
    when (problem) {
        CopyProblem.OtherPassword -> Res.string.conflict_problem_other_password
        CopyProblem.Damaged -> Res.string.conflict_problem_damaged
        CopyProblem.OtherVault -> Res.string.conflict_problem_other_vault
        CopyProblem.Unreadable -> Res.string.conflict_problem_unreadable
    }

/**
 * The dialog that asks for the password of the copies written with another one. The typed password becomes a
 * [Credentials] that the session takes ownership of; the copy the dialog holds is wiped when it closes.
 */
@Composable
internal fun OtherPasswordDialog(
    onMerge: (Credentials) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { password = "" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(Res.string.conflict_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BannerGap)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(text = stringResource(Res.string.conflict_password_label)) },
                    singleLine = true,
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { revealed = !revealed }) {
                    val text = if (revealed) Res.string.entry_detail_hide else Res.string.entry_detail_show
                    Text(text = stringResource(text))
                }
                Text(
                    text = stringResource(Res.string.conflict_password_show),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chars = password.toCharArray()
                    password = ""
                    onMerge(Credentials.of(password = chars))
                },
                enabled = password.isNotEmpty(),
            ) {
                Text(text = stringResource(Res.string.conflict_password_merge))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.conflict_password_cancel))
            }
        },
    )
}
