@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.detail_empty
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_attachments
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_copy_field
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_delete
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_edit
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_hide_field
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_history
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_modified
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_notes
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_open_field
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_password
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
import java.awt.Frame
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The room the detail takes and the rhythm of its cards. */
private val DetailPadding = 16.dp
private val CardGap = 12.dp
private val PanelWidth = 440.dp
private const val PATH_SEPARATOR = " › "

/** What separates the group and the date in the line under the title. */
private const val SUBTITLE_SEPARATOR = " · "

/** The header: the avatar, its text and the actions that change or remove the entry. */
private val BadgeSize = 48.dp
private val HeaderGap = 14.dp
private val HeaderTextGap = 4.dp
private val HeaderActionSize = 48.dp
private val HeaderActionCorner = 12.dp
private val HeaderActionGap = 8.dp
private const val ERROR_BORDER_ALPHA = 0.5f

/** The cards and the rows inside them. */
private val CardCorner = 14.dp
private val CardBorderWidth = 1.dp
private const val CARD_BORDER_ALPHA = 0.4f
private const val DIVIDER_ALPHA = 0.25f
private val RowPaddingHorizontal = 14.dp
private val RowPaddingVertical = 12.dp
private val RowTextGap = 3.dp
private val RowActionGap = 6.dp

/** The card titles and the copy button at their end. */
private val CardTitlePaddingStart = 14.dp
private val CardTitlePaddingEnd = 8.dp
private val CardTitlePaddingTop = 10.dp
private val CardTitlePaddingBottom = 6.dp
private val CardTextPaddingHorizontal = 14.dp
private val CardTextPaddingBottom = 12.dp
private val CardTextGap = 4.dp

/** The square icon buttons of a row: copy filled, reveal and open quiet. */
private val IconButtonSize = 36.dp
private val IconButtonCorner = 10.dp
private val IconSize = 18.dp

/** The chip with the keyboard shortcut of a row. */
private val ShortcutChipCorner = 8.dp
private val ShortcutChipPaddingHorizontal = 6.dp
private val ShortcutChipPaddingVertical = 2.dp
private const val SHORTCUT_BORDER_ALPHA = 0.6f

/** The label above a value: small, bold and spaced, as the mockups show it. */
private val LabelLetterSpacing = 0.4.sp

/** A masked password is read character by character, so the dots are spaced out. */
private val PasswordLetterSpacing = 3.sp
private val TotpCodeSize = 22.sp

/** How often the one-time code and its countdown are worked out again. */
private const val TOTP_REFRESH_MILLIS = 1_000L

/** From this many seconds left the countdown is shown as urgent. */
private const val URGENT_SECONDS = 5

/** The rows the main card of the detail carries, in the order it shows them. */
internal enum class CoreField { UserName, Password, Totp, Url }

/** The rows of the main card: the user name and the password always, the one-time code and the address when set. */
internal fun coreFields(entry: Entry): List<CoreField> =
    buildList {
        add(CoreField.UserName)
        add(CoreField.Password)
        if (entry.totp() != null) add(CoreField.Totp)
        if (entry.url.isNotEmpty()) add(CoreField.Url)
    }

/** The line under the title: the group and the date, whichever of them there is, separated by a middle dot. */
internal fun detailSubtitle(
    group: String,
    modified: String,
): String = listOf(group, modified).filter { it.isNotBlank() }.joinToString(SUBTITLE_SEPARATOR)

/** The room the detail panel takes in the window. */
internal val DetailPanelWidth = PanelWidth

/**
 * The right panel, in whichever state it is in: the entry read-only, the form for a new or an edited entry, or one
 * previous version. A read-only entry shows its user name, password, one-time code, address, custom fields, notes,
 * attachments and history, with a copy button per field and secrets hidden until they are asked for.
 */
@Composable
internal fun EntryDetailPanel(
    vault: Vault,
    view: DesktopVaultState,
    saving: Boolean,
    parent: Frame,
    modifier: Modifier = Modifier,
) {
    val entry = vault.entry(view.selectedEntry)
    when (val mode = view.mode) {
        is DetailMode.Edit ->
            EntryEditPanel(
                vault = vault,
                entry = if (mode.isNew) null else entry,
                isNew = mode.isNew,
                saving = saving,
                view = view,
                parent = parent,
                modifier = modifier,
            )
        is DetailMode.Version ->
            entry?.history?.getOrNull(mode.index)?.let { version ->
                EntryReadView(
                    entry = version,
                    onDelete = null,
                    groupPath = entryPath(vault, entry),
                    view = view,
                    withHistory = false,
                    onEdit = null,
                    modifier = modifier,
                )
            } ?: EmptyDetail(modifier = modifier)
        DetailMode.Read ->
            if (entry == null) {
                EmptyDetail(modifier = modifier)
            } else {
                EntryReadView(
                    entry = entry,
                    onDelete = { view.ask(VaultPrompt.MoveToBin(entry.id)) },
                    groupPath = entryPath(vault, entry),
                    view = view,
                    withHistory = true,
                    onEdit = view::startEdit,
                    modifier = modifier,
                )
            }
    }
}

/** The names of the groups that lead to [entry], root included. */
private fun entryPath(
    vault: Vault,
    entry: Entry,
): String = vault.root.pathToEntry(entry.id).joinToString(PATH_SEPARATOR)

/** One entry, read-only: its header, its cards and, for the selected entry, its history. */
@Suppress("LongParameterList")
@Composable
private fun EntryReadView(
    entry: Entry,
    groupPath: String,
    view: DesktopVaultState,
    withHistory: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var revealed by remember(entry.id) { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .focusRequester(view.detailPanel)
                .focusable()
                .padding(DetailPadding),
        verticalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        if (onEdit == null) {
            HistoryBackButton(view = view)
        }
        DetailHeader(entry = entry, group = groupPath, onEdit = onEdit, onDelete = onDelete)
        FieldsCard(entry = entry, view = view, revealed = revealed, onReveal = { revealed = !revealed })
        CustomFieldsCard(entry = entry, view = view, revealed = revealed, onReveal = { revealed = !revealed })
        NotesCard(entry = entry, view = view)
        AttachmentsCard(entry = entry)
        if (withHistory) {
            HistoryCard(entry = entry, view = view)
        }
    }
}

/** The header of the detail: the avatar, the title, the group and the date, and the actions at its end. */
@Suppress("LongParameterList")
@Composable
private fun DetailHeader(
    entry: Entry,
    group: String,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HeaderGap),
    ) {
        HexBadge(
            text = initialOf(entry.title),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            size = BadgeSize,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HeaderTextGap),
        ) {
            Text(
                text = entry.title.ifEmpty { stringResource(Res.string.entry_detail_untitled) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val modifiedDate = shortDate(entry.times.lastModified, ZoneId.systemDefault(), Locale.getDefault())
            val modified =
                if (modifiedDate.isEmpty()) "" else stringResource(Res.string.entry_detail_modified, modifiedDate)
            val subtitle = detailSubtitle(group, modified)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onEdit != null || onDelete != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HeaderActionGap),
            ) {
                if (onEdit != null) EditButton(onEdit = onEdit)
                if (onDelete != null) DeleteButton(onDelete = onDelete)
            }
        }
    }
}

/** The filled button that opens the form, called also by Ctrl+E. */
@Composable
private fun EditButton(
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.entry_detail_edit)
    ShortcutTooltip(text = stringResource(Res.string.tooltip_shortcut, label, CTRL_E), modifier = modifier) {
        Button(
            onClick = onEdit,
            shape = RoundedCornerShape(HeaderActionCorner),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.height(HeaderActionSize),
        ) {
            Text(text = label)
        }
    }
}

/** The square button that asks to move the entry to the recycle bin. */
@Composable
private fun DeleteButton(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.entry_detail_delete)
    OutlinedButton(
        onClick = onDelete,
        shape = RoundedCornerShape(HeaderActionCorner),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(CardBorderWidth, MaterialTheme.colorScheme.error.copy(alpha = ERROR_BORDER_ALPHA)),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(HeaderActionSize),
    ) {
        Icon(imageVector = TrashIcon, contentDescription = description, modifier = Modifier.size(IconSize))
    }
}

/** The main card: the user name, the password, the one-time code and the address, separated by thin lines. */
@Composable
private fun FieldsCard(
    entry: Entry,
    view: DesktopVaultState,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val userNameLabel = stringResource(Res.string.entry_detail_user_name)
    val passwordLabel = stringResource(Res.string.entry_detail_password)
    val totpLabel = stringResource(Res.string.entry_detail_totp)
    val urlLabel = stringResource(Res.string.entry_detail_url)
    DetailCard(modifier = modifier) {
        coreFields(entry).forEachIndexed { index, field ->
            if (index > 0) RowDivider()
            when (field) {
                CoreField.UserName ->
                    UserNameRow(label = userNameLabel, value = entry.userName, view = view)
                CoreField.Password ->
                    PasswordRow(
                        label = passwordLabel,
                        value = entry.passwordValue(),
                        view = view,
                        revealed = revealed,
                        onReveal = onReveal,
                    )
                CoreField.Totp ->
                    TotpRow(
                        entry = entry,
                        label = totpLabel,
                        view = view,
                        revealed = revealed,
                        onReveal = onReveal,
                    )
                CoreField.Url ->
                    UrlRow(label = urlLabel, value = entry.url, view = view)
            }
        }
    }
}

/** The user name, which Ctrl+B copies. */
@Composable
private fun UserNameRow(
    label: String,
    value: String,
    view: DesktopVaultState,
) {
    DetailRow(
        label = label,
        actions = {
            ShortcutChip(text = CTRL_B)
            if (value.isNotEmpty()) {
                CopyFieldButton(label = label, shortcut = CTRL_B) {
                    view.copy(value, label, sensitive = false)
                }
            }
        },
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The password: dots until it is revealed, and Ctrl+C copies it as a secret. */
@Composable
private fun PasswordRow(
    label: String,
    value: String,
    view: DesktopVaultState,
    revealed: Boolean,
    onReveal: () -> Unit,
) {
    DetailRow(
        label = label,
        actions = {
            ShortcutChip(text = CTRL_C)
            RevealFieldButton(label = label, revealed = revealed, onClick = onReveal)
            if (value.isNotEmpty()) {
                CopyFieldButton(label = label, shortcut = CTRL_C) {
                    view.copy(value, label, sensitive = true)
                }
            }
        },
    ) {
        Text(
            text = if (revealed) value else MASK.repeat(value.length),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (revealed) FontFamily.Monospace else null,
            letterSpacing = if (revealed) 0.sp else PasswordLetterSpacing,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The one-time code, refreshed every second, with the seconds it has left beside it. Nothing is drawn when the entry
 * has no code this app understands.
 */
@Composable
private fun TotpRow(
    entry: Entry,
    label: String,
    view: DesktopVaultState,
    revealed: Boolean,
    onReveal: () -> Unit,
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
    DetailRow(
        label = label,
        actions = {
            RevealFieldButton(label = label, revealed = revealed, onClick = onReveal)
            CopyFieldButton(label = label, shortcut = null) {
                view.copy(code.code, label, sensitive = true)
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RowActionGap),
        ) {
            Text(
                text = if (revealed) formatTotpCode(code.code) else MASK.repeat(code.code.length),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = TotpCodeSize,
                        fontWeight = FontWeight.Bold,
                    ),
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
}

/** The address in the primary colour: Ctrl+U opens it when it is a web address, and it can always be copied. */
@Composable
private fun UrlRow(
    label: String,
    value: String,
    view: DesktopVaultState,
) {
    DetailRow(
        label = label,
        actions = {
            ShortcutChip(text = CTRL_U)
            if (isWebAddress(value)) {
                OpenFieldButton(label = label, shortcut = CTRL_U) { view.openUrl(value) }
            }
            if (value.isNotEmpty()) {
                CopyFieldButton(label = label, shortcut = CTRL_U) {
                    view.copy(value, label, sensitive = false)
                }
            }
        },
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The custom fields, if any, in a card of the same kind: protected ones hidden behind the eye. */
@Composable
private fun CustomFieldsCard(
    entry: Entry,
    view: DesktopVaultState,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fields = customFields(entry)
    if (fields.isEmpty()) return
    DetailCard(modifier = modifier) {
        fields.forEachIndexed { index, (name, field) ->
            if (index > 0) RowDivider()
            DetailRow(
                label = name,
                actions = {
                    if (field.protected) {
                        RevealFieldButton(label = name, revealed = revealed, onClick = onReveal)
                    }
                    if (field.value.isNotEmpty()) {
                        CopyFieldButton(label = name, shortcut = null) {
                            view.copy(field.value, name, sensitive = field.protected)
                        }
                    }
                },
            ) {
                Text(
                    text = if (field.protected && !revealed) MASK.repeat(field.value.length) else field.value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = if (field.protected && revealed) FontFamily.Monospace else null,
                    letterSpacing = if (field.protected && !revealed) PasswordLetterSpacing else 0.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The notes of the entry, in a card of their own, with their copy button at the top right. */
@Composable
private fun NotesCard(
    entry: Entry,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val notes = entry.fields[Entry.NOTES]?.value.orEmpty()
    if (notes.isEmpty()) return
    val label = stringResource(Res.string.entry_detail_notes)
    DetailCard(
        title = label,
        modifier = modifier,
        titleAction = {
            CopyFieldButton(label = label, shortcut = null) {
                view.copy(notes, label, sensitive = true)
            }
        },
    ) {
        Text(
            text = notes,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.padding(
                    start = CardTextPaddingHorizontal,
                    end = CardTextPaddingHorizontal,
                    bottom = CardTextPaddingBottom,
                ),
        )
    }
}

/** The names of the attachments, in a card of their own; opening one comes with the step that can read them. */
@Composable
private fun AttachmentsCard(
    entry: Entry,
    modifier: Modifier = Modifier,
) {
    if (entry.attachments.isEmpty()) return
    DetailCard(title = stringResource(Res.string.entry_detail_attachments), modifier = modifier) {
        Column(
            modifier =
                Modifier.padding(
                    start = CardTextPaddingHorizontal,
                    end = CardTextPaddingHorizontal,
                    bottom = CardTextPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(CardTextGap),
        ) {
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
}

/** The previous versions of the selected entry, in a card of their own. */
@Composable
private fun HistoryCard(
    entry: Entry,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    DetailCard(title = stringResource(Res.string.entry_detail_history, entry.history.size), modifier = modifier) {
        EntryHistorySection(entry = entry, view = view)
    }
}

/** A card of the detail: the surface the rows sit on, with a quiet edge and the corner of the mockups. */
@Composable
private fun DetailCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    titleAction: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CardBorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
        shape = RoundedCornerShape(CardCorner),
    ) {
        Column {
            if (title != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = CardTitlePaddingStart,
                                end = CardTitlePaddingEnd,
                                top = CardTitlePaddingTop,
                                bottom = CardTitlePaddingBottom,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldLabel(text = title, modifier = Modifier.weight(1f))
                    titleAction()
                }
            }
            content()
        }
    }
}

/** One row of a card: the label above its value, and the buttons at its end. */
@Composable
private fun DetailRow(
    label: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    value: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = RowPaddingHorizontal, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowActionGap),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(RowTextGap),
        ) {
            FieldLabel(text = label)
            value()
        }
        actions()
    }
}

/** The small label above a value, in capitals and a quiet colour. */
@Composable
private fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = LabelLetterSpacing,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The shortcut of a row, as a small chip at the end of it. */
@Composable
private fun ShortcutChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ShortcutChipCorner),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CardBorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = SHORTCUT_BORDER_ALPHA)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    horizontal = ShortcutChipPaddingHorizontal,
                    vertical = ShortcutChipPaddingVertical,
                ),
        )
    }
}

/** The thin line between two rows of a card. */
@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
}

/** The filled square button that copies a field; it marks secrets as such before they reach the clipboard. */
@Composable
private fun CopyFieldButton(
    label: String,
    shortcut: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val description = stringResource(Res.string.entry_detail_copy_field, label)
    val shape = RoundedCornerShape(IconButtonCorner)
    ShortcutTooltip(text = withShortcut(description, shortcut), modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .size(IconButtonSize)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primary, shape)
                    .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CopyIcon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

/** The quiet button that shows or hides a secret; its description names the field, never its value. */
@Composable
private fun RevealFieldButton(
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
        Box(
            modifier =
                Modifier
                    .size(IconButtonSize)
                    .clip(RoundedCornerShape(IconButtonCorner))
                    .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (revealed) EyeOffIcon else EyeIcon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

/** The button that opens an address in the system browser. */
@Composable
private fun OpenFieldButton(
    label: String,
    shortcut: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val description = stringResource(Res.string.entry_detail_open_field, label)
    val shape = RoundedCornerShape(IconButtonCorner)
    ShortcutTooltip(text = withShortcut(description, shortcut), modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .size(IconButtonSize)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape)
                    .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = OpenInNewIcon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize),
            )
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
        modifier = modifier.fillMaxHeight().fillMaxWidth().background(MaterialTheme.colorScheme.background),
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
