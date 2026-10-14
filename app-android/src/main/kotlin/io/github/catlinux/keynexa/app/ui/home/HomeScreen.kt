package io.github.catlinux.keynexa.app.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.vault.KnownVault

/** Spacing and touch targets of the design grid (docs/DESIGN.md): 4 dp steps, targets of at least 48 dp. */
private val ScreenPadding = 16.dp
private val BlockGap = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp

/**
 * The vaults this app knows about.
 *
 * A stateless screen: it shows [vaults] and reports what the user chose. It never reads storage and never holds a
 * session. Taking a vault off the list is confirmed first, and it never deletes the file.
 *
 * The parameter list is the screen's contract with its caller, so it is kept as it is even though it is long.
 */
@Suppress("LongParameterList")
@Composable
fun HomeScreen(
    vaults: List<KnownVault>,
    onOpenExisting: () -> Unit,
    onCreateNew: () -> Unit,
    onSelect: (KnownVault) -> Unit,
    onRemove: (KnownVault) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var askedToRemove by remember { mutableStateOf<KnownVault?>(null) }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            HomeHeader(onOpenSettings = onOpenSettings)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (vaults.isEmpty()) {
                    EmptyVaults()
                } else {
                    VaultList(
                        vaults = vaults,
                        onSelect = onSelect,
                        onRemove = { askedToRemove = it },
                    )
                }
            }
            HomeActions(onOpenExisting = onOpenExisting, onCreateNew = onCreateNew)
        }
    }
    askedToRemove?.let { vault ->
        RemoveVaultDialog(
            vault = vault,
            onConfirm = {
                onRemove(vault)
                askedToRemove = null
            },
            onDismiss = { askedToRemove = null },
        )
    }
}

/** The title and the way to the settings. */
@Composable
private fun HomeHeader(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.home_settings),
            )
        }
    }
}

/** The known vaults, in a list that scrolls when it grows past the screen. */
@Composable
private fun VaultList(
    vaults: List<KnownVault>,
    onSelect: (KnownVault) -> Unit,
    onRemove: (KnownVault) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        vaults.forEach { vault ->
            VaultRow(
                vault = vault,
                onSelect = { onSelect(vault) },
                onRemove = { onRemove(vault) },
            )
        }
    }
}

/** One vault: tapping it opens it, and the button asks to take it off the list. */
@Composable
private fun VaultRow(
    vault: KnownVault,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .clickable(role = Role.Button, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = vault.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.home_remove),
            )
        }
    }
}

/** What the screen says while the list is empty: what is missing and what to do next. */
@Composable
private fun EmptyVaults(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(BlockGap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** The two ways to add a vault, in the lower half of the screen and a thumb away. */
@Composable
private fun HomeActions(
    onOpenExisting: () -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        OutlinedButton(
            onClick = onOpenExisting,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.home_open_existing))
        }
        Button(
            onClick = onCreateNew,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.home_create_new))
        }
    }
}

/**
 * Asks before taking a vault off the list. The message says what happens and what does not: the file stays where it
 * is. The safe answer sits on the right, as the design system asks.
 */
@Composable
private fun RemoveVaultDialog(
    vault: KnownVault,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.home_remove_dialog_title)) },
        text = { Text(text = stringResource(R.string.home_remove_dialog_message, vault.name)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = stringResource(R.string.home_remove_confirm))
            }
        },
    )
}
