package io.github.catlinux.keynexa.app.ui.bin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import java.util.UUID

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ScreenPadding = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp

/**
 * One thing waiting in the recycle bin. [detail] is whatever helps the user recognise it, for example the groups it
 * came from, and never holds a secret.
 */
data class BinItem(
    val id: UUID,
    val title: String,
    val isGroup: Boolean,
    val detail: String,
)

/**
 * The recycle bin of the open vault: what is in it, and how to take one item back or get rid of it for good.
 *
 * A stateless screen: it shows the items it is given and reports what the user chose. Nothing is stored and no
 * secret is shown. Deleting for good and emptying the bin ask first, because neither can be undone.
 */
@Suppress("LongParameterList")
@Composable
fun RecycleBinScreen(
    items: List<BinItem>,
    onRestore: (UUID) -> Unit,
    onDeleteForever: (UUID) -> Unit,
    onEmpty: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleting by remember { mutableStateOf<BinItem?>(null) }
    var emptying by remember { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            BinHeader(onBack = onBack)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (items.isEmpty()) {
                    EmptyBin()
                } else {
                    BinList(items = items, onRestore = onRestore, onDelete = { deleting = it })
                }
            }
            OutlinedButton(
                onClick = { emptying = true },
                enabled = items.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) {
                Text(text = stringResource(R.string.bin_empty))
            }
        }
    }
    deleting?.let { item ->
        DeleteForeverDialog(
            item = item,
            onConfirm = {
                onDeleteForever(item.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
    if (emptying) {
        EmptyBinDialog(
            onConfirm = {
                onEmpty()
                emptying = false
            },
            onDismiss = { emptying = false },
        )
    }
}

/** The title of the screen and the way back. */
@Composable
private fun BinHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.bin_back),
            )
        }
        Text(
            text = stringResource(R.string.bin_title),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Everything waiting in the recycle bin, in a list that scrolls. */
@Composable
private fun BinList(
    items: List<BinItem>,
    onRestore: (UUID) -> Unit,
    onDelete: (BinItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        items.forEach { item ->
            BinRow(
                item = item,
                onRestore = { onRestore(item.id) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

/** One item of the bin: what it is, what it was, and what can be done with it. */
@Composable
private fun BinRow(
    item: BinItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val restore = stringResource(R.string.bin_restore_item, item.title)
    val delete = stringResource(R.string.bin_delete_item, item.title)
    Column(
        modifier = modifier.fillMaxWidth().heightIn(min = TouchTarget).padding(vertical = ItemGap),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(if (item.isGroup) R.string.bin_kind_group else R.string.bin_kind_entry),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.detail.isNotEmpty()) {
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ItemGap)) {
            TextButton(
                onClick = onRestore,
                modifier = Modifier.heightIn(min = TouchTarget).semantics { contentDescription = restore },
            ) {
                Text(text = stringResource(R.string.bin_restore))
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = TouchTarget).semantics { contentDescription = delete },
            ) {
                Text(text = stringResource(R.string.bin_delete_forever))
            }
        }
    }
}

/** What the screen says while nothing has been deleted. */
@Composable
private fun EmptyBin(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(ItemGap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.bin_empty_state), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Asks before erasing one item for good. */
@Composable
private fun DeleteForeverDialog(
    item: BinItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.bin_delete_dialog_title, item.title),
        message = stringResource(R.string.bin_delete_dialog_message),
        confirm = stringResource(R.string.bin_delete_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Asks before erasing everything that is left in the bin. */
@Composable
private fun EmptyBinDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.bin_empty_dialog_title),
        message = stringResource(R.string.bin_empty_dialog_message),
        confirm = stringResource(R.string.bin_empty_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** A dialog that asks before something that cannot be undone; the safe answer sits on the right, as the design says. */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.bin_cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = confirm)
            }
        },
    )
}
