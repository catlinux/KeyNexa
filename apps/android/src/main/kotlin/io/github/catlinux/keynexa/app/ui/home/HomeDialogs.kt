package io.github.catlinux.keynexa.app.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.platform.SafVaultAccess

private val TouchTarget = 48.dp
private val RowPadding = 12.dp

/** Asks which of the vault files found in the chosen folder to open. */
@Composable
fun PickVaultDialog(
    files: List<SafVaultAccess.VaultFile>,
    onPick: (SafVaultAccess.VaultFile) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.home_pick_vault_title)) },
        text = {
            Column {
                files.forEach { file ->
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = TouchTarget)
                                .clickable { onPick(file) }
                                .padding(vertical = RowPadding),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.home_cancel)) }
        },
    )
}

/** A short message after a folder choice that could not be used. */
@Composable
fun HomeNoticeDialog(
    @StringRes message: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text = stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.home_notice_ok)) }
        },
    )
}
