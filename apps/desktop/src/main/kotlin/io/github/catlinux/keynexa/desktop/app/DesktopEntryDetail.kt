@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.detail_coming_soon
import io.github.catlinux.keynexa.desktop.app.generated.resources.detail_empty
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_attachments
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_copy
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_copy_field
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_delete
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_edit
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_hide
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_hide_field
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_modified
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_notes
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_open
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_open_field
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_show
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_show_field
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_totp
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_totp_expires
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_totp_urgent
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_untitled
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_url
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_user_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.tooltip_shortcut
import io.github.catlinux.keynexa.session.pathToEntry
import io.github.catlinux.keynexa.ui.brand.HexBadge
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.totp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The room the detail takes and the rhythm of its lines. */
private val DetailPadding = 16.dp
private val DetailGap = 14.dp
private val ItemGap = 6.dp
private val BadgeSize = 40.dp
private val PanelWidth = 440.dp
private const val PATH_SEPARATOR = " › "

/** How often the one-time code and its countdown are worked out again. */
private const val TOTP_REFRESH_MILLIS = 1_000L

/** From this many seconds left the countdown is shown as urgent. */
private const val URGENT_SECONDS = 5

/** The room the detail panel takes in the window. */
internal val DetailPanelWidth = PanelWidth

/**
 * The right panel: the selected entry, read-only for now. Its user name, password, one-time code, address, custom
 * fields and notes each have a copy button, secrets stay hidden until they are asked for, and Edit and Delete are
 * shown but wait for the step that can save changes.
 */
@Composable
internal fun EntryDetailPanel(
    vault: Vault,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val entry = vault.entry(view.selectedEntry)
    if (entry == null) {
        EmptyDetail(modifier = modifier)
        return
    }
    var revealed by remember(entry.id) { mutableStateOf(false) }
    val path = remember(vault, entry.id) { vault.root.pathToEntry(entry.id).joinToString(PATH_SEPARATOR) }
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .focusRequester(view.detailPanel)
                .focusable()
                .padding(DetailPadding),
        verticalArrangement = Arrangement.spacedBy(DetailGap),
    ) {
        DetailHeader(entry = entry, group = path)
        HorizontalDivider()
        DetailBody(entry = entry, view = view, revealed = revealed, onReveal = { revealed = !revealed })
    }
}

/** The fields of the entry, in the order the detail shows them. */
@Composable
private fun DetailBody(
    entry: Entry,
    view: DesktopVaultState,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DetailGap)) {
        DetailField(
            label = stringResource(Res.string.entry_detail_user_name),
            value = entry.userName,
            view = view,
            shortcut = CTRL_B,
        )
        DetailField(
            label = stringResource(Res.string.entry_detail_password),
            value = entry.passwordValue(),
            view = view,
            hidden = !revealed,
            sensitive = true,
            shortcut = CTRL_C,
            onReveal = onReveal,
        )
        TotpField(entry = entry, view = view, revealed = revealed, onReveal = onReveal)
        DetailField(
            label = stringResource(Res.string.entry_detail_url),
            value = entry.url,
            view = view,
            shortcut = CTRL_U,
            onOpen = if (isWebAddress(entry.url)) ({ view.openUrl(entry.url) }) else null,
        )
        customFields(entry).forEach { (name, value) ->
            DetailField(
                label = name,
                value = value.value,
                view = view,
                hidden = value.protected && !revealed,
                sensitive = value.protected,
                onReveal = if (value.protected) onReveal else null,
            )
        }
        NotesField(entry = entry, view = view)
        AttachmentsField(entry = entry)
        DetailActions()
    }
}

/** The head of the detail: the avatar, the title, the group it lives in and when it last changed. */
@Composable
private fun DetailHeader(
    entry: Entry,
    group: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DetailGap),
    ) {
        HexBadge(
            text = initialOf(entry.title),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = BadgeSize,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
            Text(
                text = entry.title.ifEmpty { stringResource(Res.string.entry_detail_untitled) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (group.isNotEmpty()) {
                Text(
                    text = group,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val modified = shortDate(entry.times.lastModified, ZoneId.systemDefault(), Locale.getDefault())
            if (modified.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.entry_detail_modified, modified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One field of the entry: its label, its value and the buttons that copy, reveal or open it. */
@Suppress("LongParameterList")
@Composable
private fun DetailField(
    label: String,
    value: String,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    sensitive: Boolean = false,
    shortcut: String? = null,
    onReveal: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        FieldLabel(text = label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = if (hidden) MASK.repeat(value.length) else value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (hidden) null else FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotEmpty()) {
                CopyButton(
                    label = label,
                    shortcut = shortcut,
                    onClick = { view.copy(value, label, sensitive = sensitive) },
                )
                if (onReveal != null) {
                    RevealButton(label = label, revealed = !hidden, onClick = onReveal)
                }
                if (onOpen != null) {
                    OpenButton(label = label, shortcut = shortcut, onClick = onOpen)
                }
            }
        }
    }
}

/** The one-time code of the entry, with its countdown, refreshed every second; nothing when it has none. */
@Composable
private fun TotpField(
    entry: Entry,
    view: DesktopVaultState,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = remember(entry.id) { entry.totp() } ?: return
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(entry.id) {
        while (true) {
            now = Instant.now()
            delay(TOTP_REFRESH_MILLIS)
        }
    }
    val code = config.codeAt(now)
    val urgent = code.secondsRemaining <= URGENT_SECONDS
    val label = stringResource(Res.string.entry_detail_totp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        FieldLabel(text = label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = if (revealed) formatTotpCode(code.code) else MASK.repeat(code.code.length),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CopyButton(label = label, shortcut = null, onClick = { view.copy(code.code, label, sensitive = true) })
            RevealButton(label = label, revealed = revealed, onClick = onReveal)
        }
        Text(
            text =
                if (urgent) {
                    stringResource(Res.string.entry_detail_totp_urgent)
                } else {
                    stringResource(Res.string.entry_detail_totp_expires, code.secondsRemaining)
                },
            style = MaterialTheme.typography.bodySmall,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The notes of the entry, as text, with a copy button. */
@Composable
private fun NotesField(
    entry: Entry,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val notes = entry.fields[Entry.NOTES]?.value.orEmpty()
    val label = stringResource(Res.string.entry_detail_notes)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        FieldLabel(text = label)
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (notes.isNotEmpty()) {
                CopyButton(label = label, shortcut = null, onClick = { view.copy(notes, label, sensitive = true) })
            }
        }
    }
}

/** The names of the attachments; opening one comes with the step that can read them. */
@Composable
private fun AttachmentsField(
    entry: Entry,
    modifier: Modifier = Modifier,
) {
    if (entry.attachments.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        FieldLabel(text = stringResource(Res.string.entry_detail_attachments))
        entry.attachments.forEach { attachment ->
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Edit and Delete, shown but waiting for the step that can save changes. */
@Composable
private fun DetailActions(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        DisabledAction(text = stringResource(Res.string.entry_detail_edit))
        DisabledAction(text = stringResource(Res.string.entry_detail_delete))
    }
}

/** A button of an action that still has no screen behind it. */
@Composable
private fun DisabledAction(
    text: String,
    modifier: Modifier = Modifier,
) {
    ShortcutTooltip(text = stringResource(Res.string.detail_coming_soon), modifier = modifier) {
        TextButton(onClick = {}, enabled = false) {
            Text(text = text)
        }
    }
}

/** The label above a field. */
@Composable
private fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Copies the value of a field. */
@Composable
private fun CopyButton(
    label: String,
    shortcut: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.entry_detail_copy_field, label)
    ShortcutTooltip(text = withShortcut(description, shortcut), modifier = modifier) {
        TextButton(onClick = onClick) {
            Text(text = stringResource(Res.string.entry_detail_copy))
        }
    }
}

/** Shows or hides the value of a field. */
@Composable
private fun RevealButton(
    label: String,
    revealed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        if (revealed) {
            stringResource(Res.string.entry_detail_hide_field, label)
        } else {
            stringResource(Res.string.entry_detail_show_field, label)
        }
    ShortcutTooltip(text = description, modifier = modifier) {
        TextButton(onClick = onClick) {
            val text = if (revealed) Res.string.entry_detail_hide else Res.string.entry_detail_show
            Text(text = stringResource(text))
        }
    }
}

/** Opens the address of a field in the system browser. */
@Composable
private fun OpenButton(
    label: String,
    shortcut: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.entry_detail_open_field, label)
    ShortcutTooltip(text = withShortcut(description, shortcut), modifier = modifier) {
        TextButton(onClick = onClick) {
            Text(text = stringResource(Res.string.entry_detail_open))
        }
    }
}

/** The description of an action with the shortcut that does the same, when there is one. */
@Composable
private fun withShortcut(
    description: String,
    shortcut: String?,
): String = if (shortcut == null) description else stringResource(Res.string.tooltip_shortcut, description, shortcut)

/** What the detail shows while no entry is selected. */
@Composable
private fun EmptyDetail(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxHeight().fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.detail_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(DetailPadding),
        )
    }
}
