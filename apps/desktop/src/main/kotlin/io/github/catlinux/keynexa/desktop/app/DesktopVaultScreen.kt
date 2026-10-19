package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.clipboard_copied
import io.github.catlinux.keynexa.desktop.app.generated.resources.status_shortcuts
import io.github.catlinux.keynexa.desktop.app.generated.resources.vault_updated_elsewhere
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.vault.Credentials
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import java.awt.Frame

/** The room of the left panel, the rhythm of the status bar and how long a copy is confirmed for. */
private val GroupPanelWidth = 240.dp
private val StatusPadding = 8.dp
private val StatusGap = 12.dp
private const val COPIED_MILLIS = 2_500L
private val BannerPadding = 12.dp
private const val UPDATED_MILLIS = 4_000L
private const val DIVIDER_ALPHA = 0.3f

/**
 * The open vault, as a desktop browser: the tree of groups on the left, the dense list of entries in the middle and
 * the detail of the selected entry on the right, with the search and the actions in the band at the top and the
 * shortcuts at the bottom.
 */
@Suppress("LongParameterList")
@Composable
internal fun DesktopVaultScreen(
    state: SessionState.Open,
    view: DesktopVaultState,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    onRetrySave: () -> Unit,
    onRestoreUnsaved: () -> Unit,
    onDiscardUnsaved: () -> Unit,
    onDeleteConflictCopies: () -> Unit,
    onMergeOtherPassword: (Credentials) -> Unit,
    onDismissConflicts: () -> Unit,
    syncEnabled: Boolean,
    syncIndicator: SyncIndicator,
    onSyncNow: () -> Unit,
    parent: Frame,
    modifier: Modifier = Modifier,
) {
    var askingOtherPassword by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        VaultTopBar(
            state = state,
            view = view,
            onLock = onLock,
            onSettings = onSettings,
            syncEnabled = syncEnabled,
            syncIndicator = syncIndicator,
            onSyncNow = onSyncNow,
        )
        VaultNotices(
            state = state,
            onRetrySave = onRetrySave,
            onRestoreUnsaved = onRestoreUnsaved,
            onDiscardUnsaved = onDiscardUnsaved,
        )
        state.conflicts?.let { report ->
            ConflictBanner(
                report = report,
                onDeleteCopies = onDeleteConflictCopies,
                onEnterPassword = { askingOtherPassword = true },
                onDismiss = onDismissConflicts,
                modifier = Modifier.padding(BannerPadding),
            )
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            GroupPanel(vault = state.current, view = view, modifier = Modifier.width(GroupPanelWidth))
            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
            EntryListPanel(vault = state.current, view = view, modifier = Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
            EntryDetailPanel(
                vault = state.current,
                view = view,
                saving = state.saving,
                parent = parent,
                modifier = Modifier.width(DetailPanelWidth),
            )
        }
        VaultStatusBar(view = view)
    }
    VaultPromptDialogs(vault = state.current, view = view)
    if (askingOtherPassword) {
        OtherPasswordDialog(
            onMerge = { credentials ->
                askingOtherPassword = false
                onMergeOtherPassword(credentials)
            },
            onDismiss = { askingOtherPassword = false },
        )
    }
}

/** The slim bar at the bottom: what the shortcuts do, and what was copied last. */
@Composable
private fun VaultStatusBar(
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = StatusPadding, vertical = StatusPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StatusGap),
        ) {
            if (view.updatedElsewhere) {
                Text(
                    text = stringResource(Res.string.vault_updated_elsewhere),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                LaunchedEffect(Unit) {
                    delay(UPDATED_MILLIS)
                    view.updatedElsewhere = false
                }
            }
            Text(
                text = stringResource(Res.string.status_shortcuts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            view.copied?.let { label ->
                Text(
                    text = stringResource(Res.string.clipboard_copied, label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                LaunchedEffect(label) {
                    delay(COPIED_MILLIS)
                    view.copied = null
                }
            }
        }
    }
}
