package io.github.catlinux.keynexa.app.ui.home

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.ui.brand.BrandMark
import io.github.catlinux.keynexa.ui.brand.HexBadge

/** Spacing and touch targets of the design grid (docs/DESIGN.md): 4 dp steps, targets of at least 48 dp. */
private val ScreenPadding = 20.dp
private val BlockGap = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp
private val HeaderMarkSize = 40.dp
private val EmptyMarkSize = 72.dp

/** The card that holds the vaults, and the rows inside it. */
private val CardCorner = 16.dp
private val CardBorderWidth = 1.dp
private const val CARD_BORDER_ALPHA = 0.4f
private const val DIVIDER_ALPHA = 0.2f
private val RowPaddingStart = 16.dp
private val RowPaddingEnd = 8.dp
private val RowPaddingVertical = 12.dp
private val RowGap = 12.dp
private val TextGap = 2.dp
private val BadgeSize = 40.dp

/** The two actions in the thumb zone. */
private val ActionHeight = 54.dp
private val ActionCorner = 14.dp

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
    onBringVault: (Uri) -> Unit = {},
) {
    var askedToRemove by remember { mutableStateOf<KnownVault?>(null) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            HomeHeader(onOpenSettings = onOpenSettings)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (vaults.isEmpty()) {
                    EmptyVaults()
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ItemGap),
                    ) {
                        SectionLabel(textRes = R.string.home_title)
                        VaultCard(
                            vaults = vaults,
                            onSelect = onSelect,
                            onRemove = { askedToRemove = it },
                        )
                    }
                }
            }
            CloudReminder(onOpenSettings = onOpenSettings)
            UpdatePrompt()
            HomeActions(
                onOpenExisting = onOpenExisting,
                onCreateNew = onCreateNew,
                onBringVault = onBringVault,
                isKnownVault = { uri -> vaults.any { it.location.value == uri } },
            )
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

/** The brand and the way to the settings. */
@Composable
private fun HomeHeader(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = BlockGap, bottom = ItemGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        BrandMark(size = HeaderMarkSize)
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.home_settings),
            )
        }
    }
}

/** The word above the list of vaults. */
@Composable
private fun SectionLabel(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(textRes).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(vertical = ItemGap),
    )
}

/** The known vaults in one card, with a separator between rows. */
@Composable
private fun VaultCard(
    vaults: List<KnownVault>,
    onSelect: (KnownVault) -> Unit,
    onRemove: (KnownVault) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CardBorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column {
            vaults.forEachIndexed { index, vault ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
                }
                VaultRow(
                    vault = vault,
                    onSelect = { onSelect(vault) },
                    onRemove = { onRemove(vault) },
                )
            }
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
                .clickable(role = Role.Button, onClick = onSelect)
                .padding(
                    start = RowPaddingStart,
                    end = RowPaddingEnd,
                    top = RowPaddingVertical,
                    bottom = RowPaddingVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        HexBadge(
            text = initialOf(vault.name),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = BadgeSize,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TextGap),
        ) {
            Text(
                text = vault.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = locationLabel(vault),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        BrandMark(size = EmptyMarkSize)
        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = BlockGap),
        )
    }
}

/** The two ways to add a vault, in the lower half of the screen and a thumb away. */
@Composable
private fun HomeActions(
    onOpenExisting: () -> Unit,
    onCreateNew: () -> Unit,
    onBringVault: (Uri) -> Unit,
    isKnownVault: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = BlockGap, bottom = BlockGap),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Button(
            onClick = onOpenExisting,
            shape = RoundedCornerShape(ActionCorner),
            modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight),
        ) {
            Text(text = stringResource(R.string.home_open_existing))
        }
        OutlinedButton(
            onClick = onCreateNew,
            shape = RoundedCornerShape(ActionCorner),
            modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight),
        ) {
            Text(text = stringResource(R.string.home_create_new))
        }
        CloudBringVault(isKnownVault = isKnownVault, onSaved = onBringVault)
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

/** The initial shown on a vault's hexagon avatar. */
private fun initialOf(name: String): String {
    val trimmed = name.trim()
    return if (trimmed.isEmpty()) "?" else trimmed.substring(0, 1).uppercase()
}

/** Where a vault lives, said with the last part of its location. */
private fun locationLabel(vault: KnownVault): String {
    val decoded = Uri.decode(vault.location.value.substringAfterLast('/'))
    return decoded.ifBlank { vault.name }
}
