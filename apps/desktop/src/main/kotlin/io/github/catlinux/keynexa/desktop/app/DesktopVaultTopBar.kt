package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_lock
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_settings
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_status_saved
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_status_unsaved
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_problem_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.save_saving
import io.github.catlinux.keynexa.desktop.app.generated.resources.search_clear
import io.github.catlinux.keynexa.desktop.app.generated.resources.search_field_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.tooltip_shortcut
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.sync.SaveFailure
import org.jetbrains.compose.resources.stringResource

/** The band at the top, and the room the vault name takes in it. */
private val TopBarPadding = 10.dp
private val BarPadding = 12.dp
private val BarGap = 10.dp
private val VaultNameWidth = 220.dp
private val ControlHeight = 44.dp
private val ControlShape = RoundedCornerShape(12.dp)
private const val QUIET_ALPHA = 0.85f
private const val BORDER_ALPHA = 0.6f
private const val DISABLED_ALPHA = 0.6f

/** How long the pointer rests on a control before its tooltip appears. */
private const val TOOLTIP_DELAY_MILLIS = 500

/**
 * The band at the top of the browser: the vault name with its save status, the search box over the whole vault, the
 * settings button, which still has no screen, and Lock. Ctrl+F puts the focus in the search box.
 */
@Composable
internal fun VaultTopBar(
    state: SessionState.Open,
    view: DesktopVaultState,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BarPadding, vertical = TopBarPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BarGap),
        ) {
            Column(modifier = Modifier.width(VaultNameWidth)) {
                Text(
                    text = state.vault.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SaveStatusLine(
                    saving = state.saving,
                    hasUnsavedChanges = state.hasUnsavedChanges,
                    problem = state.saveProblem,
                )
            }
            SearchField(view = view, modifier = Modifier.weight(1f))
            SettingsButton()
            LockButton(onLock = onLock)
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
        style = MaterialTheme.typography.bodySmall,
        color =
            if (problem != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = QUIET_ALPHA)
            },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The search box over the whole vault; the list shows the matches while it holds something. */
@Composable
private fun SearchField(
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.search_field_label)
    ShortcutTooltip(text = stringResource(Res.string.tooltip_shortcut, label, CTRL_F), modifier = modifier) {
        OutlinedTextField(
            value = view.query,
            onValueChange = { view.query = it },
            placeholder = { Text(text = label) },
            singleLine = true,
            shape = ControlShape,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            trailingIcon = {
                if (view.query.isNotEmpty()) {
                    TextButton(onClick = { view.query = "" }) {
                        Text(text = stringResource(Res.string.search_clear))
                    }
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ControlHeight)
                    .focusRequester(view.searchField)
                    .onFocusChanged { view.searchFocused = it.isFocused },
        )
    }
}

/** The way to the settings, which still have no screen: it stays readable while it is not available. */
@Composable
private fun SettingsButton(modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.group_settings)
    ShortcutTooltip(text = label, modifier = modifier) {
        OutlinedButton(
            onClick = {},
            enabled = false,
            shape = ControlShape,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = DISABLED_ALPHA),
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = DISABLED_ALPHA)),
            modifier = Modifier.heightIn(min = ControlHeight),
        ) {
            Text(text = label)
        }
    }
}

/** Locking the vault, with the shortcut that does the same. */
@Composable
private fun LockButton(
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.group_lock)
    ShortcutTooltip(text = stringResource(Res.string.tooltip_shortcut, label, CTRL_L), modifier = modifier) {
        OutlinedButton(
            onClick = onLock,
            shape = ControlShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = BORDER_ALPHA)),
            modifier = Modifier.heightIn(min = ControlHeight),
        ) {
            Text(text = label)
        }
    }
}

/** A tooltip that names the action and the shortcut that does the same. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShortcutTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        delayMillis = TOOLTIP_DELAY_MILLIS,
        modifier = modifier,
        content = content,
    )
}
