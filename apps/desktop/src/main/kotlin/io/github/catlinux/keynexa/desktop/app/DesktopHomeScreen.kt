package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.app_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_settings
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_bring_from_cloud
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_cancel
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_create_new
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_empty_message
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_open_existing
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_remove
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_remove_confirm
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_remove_dialog_message
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_remove_dialog_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_title
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.ui.brand.BrandMark
import io.github.catlinux.keynexa.ui.brand.HexBadge
import org.jetbrains.compose.resources.stringResource

/** Spacing and touch targets of the design grid (docs/DESIGN.md), as on Android. */
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

/** The action in the lower half, and the width the whole screen keeps on a wide window. */
private val ActionHeight = 54.dp
private val ActionCorner = 14.dp
private val ContentWidth = 480.dp

/**
 * The vaults this desktop app knows about, with the look of the Android home screen: the brand, the list in a card
 * and the actions to add one. Clicking a vault reports it through [onOpen]; unlocking it is a later screen.
 */
@Suppress("LongParameterList")
@Composable
fun DesktopHomeScreen(
    vaults: List<KnownVault>,
    onOpenExisting: () -> Unit,
    onCreateNew: () -> Unit,
    onSettings: () -> Unit,
    onRemove: (KnownVault) -> Unit,
    onOpen: (KnownVault) -> Unit,
    onBringFromCloud: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    reminder: (@Composable () -> Unit)? = null,
) {
    var askedToRemove by remember { mutableStateOf<KnownVault?>(null) }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = ContentWidth).fillMaxHeight().padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            HomeHeader()
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (vaults.isEmpty()) {
                    EmptyVaults()
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ItemGap),
                    ) {
                        SectionLabel(text = stringResource(Res.string.home_title))
                        VaultCard(
                            vaults = vaults,
                            onSelect = onOpen,
                            onRemove = { askedToRemove = it },
                        )
                    }
                }
            }
            reminder?.invoke()
            HomeActions(
                onOpenExisting = onOpenExisting,
                onCreateNew = onCreateNew,
                onBringFromCloud = onBringFromCloud,
                onSettings = onSettings,
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

/** The brand and the app name. */
@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = BlockGap, bottom = ItemGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        BrandMark(size = HeaderMarkSize)
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The word above the list of vaults. */
@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
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

/** One vault: clicking it opens it (later), and the button asks to take it off the list. */
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
                text = vault.location.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onRemove) {
            Text(text = stringResource(Res.string.home_remove))
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
            text = stringResource(Res.string.home_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = BlockGap),
        )
    }
}

/** The ways to add a vault, in the lower half of the window: open one that exists, or create a new one. */
@Composable
private fun HomeActions(
    onOpenExisting: () -> Unit,
    onCreateNew: () -> Unit,
    onBringFromCloud: (() -> Unit)?,
    onSettings: () -> Unit,
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
            Text(text = stringResource(Res.string.home_open_existing))
        }
        OutlinedButton(
            onClick = onCreateNew,
            shape = RoundedCornerShape(ActionCorner),
            modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight),
        ) {
            Text(text = stringResource(Res.string.home_create_new))
        }
        if (onBringFromCloud != null) {
            OutlinedButton(
                onClick = onBringFromCloud,
                shape = RoundedCornerShape(ActionCorner),
                modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight),
            ) {
                Text(text = stringResource(Res.string.home_bring_from_cloud))
            }
        }
        OutlinedButton(
            onClick = onSettings,
            shape = RoundedCornerShape(ActionCorner),
            modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight),
        ) {
            Text(text = stringResource(Res.string.group_settings))
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
        title = { Text(text = stringResource(Res.string.home_remove_dialog_title)) },
        text = { Text(text = stringResource(Res.string.home_remove_dialog_message, vault.name)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.home_cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = stringResource(Res.string.home_remove_confirm))
            }
        },
    )
}
