@file:Suppress("MaxLineLength", "TooManyFunctions")

package io.github.catlinux.keynexa.app.ui.entry

import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.ui.common.HexBadge
import io.github.catlinux.keynexa.vault.Attachment
import io.github.catlinux.keynexa.vault.Binary
import io.github.catlinux.keynexa.vault.BinaryId
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.TotpConfig
import io.github.catlinux.keynexa.vault.totp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ItemGap = 8.dp

/** What separates the names of the groups an entry lives in. */
private const val PATH_SEPARATOR = " › "

/** How long a revealed secret stays on screen before hiding itself again. */
private const val REVEAL_MILLIS = 30_000L

/** The most dots a hidden secret shows, so that the length of a long password is not on display either. */
private const val MAX_DOTS = 12

/** The mark that stands for every hidden character. */
private const val SECRET_DOT = '•'

/** One second, for the clock that keeps a one-time code current. */
private const val SECOND_MILLIS = 1_000L

/** From this many seconds left, the one-time code is shown as running out. */
private const val URGENT_SECONDS = 5

/** The fields this screen shows in its own way; the rest are custom fields. */
private val STANDARD_FIELDS = setOf(Entry.TITLE, Entry.USER_NAME, Entry.PASSWORD, Entry.URL, Entry.NOTES)

/** The compact bar at the very top. */
private val TopBarPaddingHorizontal = 12.dp
private val TopBarPaddingTop = 20.dp
private val TopBarPaddingBottom = 8.dp
private val TopBarPathGap = 4.dp

/** The header with the entry's hexagon avatar. */
private val HeroPaddingHorizontal = 20.dp
private val HeroPaddingTop = 4.dp
private val HeroPaddingBottom = 18.dp
private val HeroGap = 14.dp
private val HeroTextGap = 4.dp
private val BadgeSize = 56.dp

/** The cards that hold the fields and the metadata. */
private val ContentPaddingHorizontal = 20.dp
private val CardGap = 12.dp
private val CardCorner = 18.dp
private val CardBorderWidth = 1.dp
private const val CARD_BORDER_ALPHA = 0.4f
private const val DIVIDER_ALPHA = 0.2f
private val CardPaddingHorizontal = 16.dp
private val CardPaddingVertical = 14.dp
private val CardTitleGap = 6.dp

/** One field row: its label above its value, and the icon buttons at its end. */
private val RowPaddingStart = 16.dp
private val RowPaddingEnd = 8.dp
private val RowPaddingVertical = 14.dp
private val RowLabelGap = 3.dp
private val IconButtonSize = 48.dp
private val IconButtonCorner = 12.dp
private val IconSize = 20.dp
private val StatusGap = 6.dp

/** The fixed bar at the bottom with the two actions. */
private val BottomPaddingHorizontal = 20.dp
private val BottomPaddingTop = 16.dp
private val BottomPaddingBottom = 28.dp
private val BottomGap = 10.dp
private val EditHeight = 54.dp
private val DeleteSize = 54.dp
private val ActionCorner = 14.dp
private const val ERROR_BORDER_ALPHA = 0.5f

/**
 * One entry, with its secrets hidden until the user asks for them.
 *
 * A stateless screen: it shows the entry it is given and reports what the user chose. A revealed secret hides again
 * after [REVEAL_MILLIS] and never survives the screen, no secret value is written to a description, and copying a
 * secret goes through [onCopy] with `sensitive` set.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun EntryDetailScreen(
    entry: Entry,
    path: List<String>,
    vaultBinaries: Map<BinaryId, Binary>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (label: String, value: String, sensitive: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attachmentToSave by remember { mutableStateOf<Attachment?>(null) }
    var askingToClearHistory by remember { mutableStateOf(false) }
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val attachment = attachmentToSave
            attachmentToSave = null
            if (uri != null && attachment != null) {
                val bytes = vaultBinaries[attachment.id]?.content
                if (bytes == null) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.entry_detail_attachment_missing),
                            Toast.LENGTH_SHORT,
                        ).show()
                } else {
                    scope.launch {
                        val saved =
                            withContext(Dispatchers.IO) {
                                try {
                                    val stream =
                                        context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                                    stream.use { it.write(bytes) }
                                    true
                                } catch (failure: IOException) {
                                    false
                                }
                            }
                        Toast
                            .makeText(
                                context,
                                context.getString(
                                    if (saved) {
                                        R.string.entry_detail_attachment_saved
                                    } else {
                                        R.string.entry_detail_attachment_save_failed
                                    },
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }
        }
    var revealed by remember { mutableStateOf<String?>(null) }
    var askingToDelete by remember { mutableStateOf(false) }
    LaunchedEffect(revealed) {
        if (revealed != null) {
            delay(REVEAL_MILLIS)
            revealed = null
        }
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            EntryTopBar(path = path, onBack = onBack)
            EntryHero(entry = entry)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = ContentPaddingHorizontal, vertical = CardGap),
                    verticalArrangement = Arrangement.spacedBy(CardGap),
                ) {
                    FieldsCard(
                        entry = entry,
                        revealed = revealed,
                        onReveal = { revealed = it },
                        onCopy = onCopy,
                        onOpenUrl = onOpenUrl,
                    )
                    NotesCard(notes = entry.fields[Entry.NOTES]?.value)
                    AttachmentsCard(
                        entry = entry,
                        vaultBinaries = vaultBinaries,
                        onSaveAttachment = { attachment ->
                            attachmentToSave = attachment
                            saveLauncher.launch(attachment.name)
                        },
                    )
                    InfoCard(entry = entry, onClearHistory = { askingToClearHistory = true })
                }
            }
            EntryActions(onEdit = onEdit, onDelete = { askingToDelete = true })
        }
    }
    if (askingToDelete) {
        DeleteDialog(
            onConfirm = {
                onDelete()
                askingToDelete = false
            },
            onDismiss = { askingToDelete = false },
        )
    }
    if (askingToClearHistory) {
        ClearHistoryDialog(
            count = entry.history.size,
            onConfirm = {
                onClearHistory()
                askingToClearHistory = false
            },
            onDismiss = { askingToClearHistory = false },
        )
    }
}

/** The compact bar at the top: the way back and the groups the entry lives in. */
@Composable
private fun EntryTopBar(
    path: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = TopBarPaddingHorizontal,
                    end = TopBarPaddingHorizontal,
                    top = TopBarPaddingTop,
                    bottom = TopBarPaddingBottom,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.entry_detail_back),
            )
        }
        if (path.isNotEmpty()) {
            Text(
                text = path.joinToString(PATH_SEPARATOR),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = TopBarPathGap),
            )
        }
    }
}

/** The header: the entry's hexagon avatar, its title and when it was last changed. */
@Composable
private fun EntryHero(
    entry: Entry,
    modifier: Modifier = Modifier,
) {
    val title = entry.title.ifEmpty { stringResource(R.string.entry_detail_untitled) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = HeroPaddingHorizontal,
                    end = HeroPaddingHorizontal,
                    top = HeroPaddingTop,
                    bottom = HeroPaddingBottom,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HeroGap),
    ) {
        HexBadge(
            text = initialsOf(title),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            size = BadgeSize,
        )
        Column(verticalArrangement = Arrangement.spacedBy(HeroTextGap)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.times.lastModified?.let { modified ->
                Text(
                    text = stringResource(R.string.entry_detail_modified, relativeMoment(modified)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A field of the card, described without its value so it can be laid out and unit-tested. */
private data class FieldRowData(
    val label: String,
    val value: String,
    val kind: FieldKind,
)

/** The way a field is shown and the actions it allows. */
private sealed interface FieldKind {
    object Plain : FieldKind

    object Secret : FieldKind

    object Url : FieldKind

    class Totp(
        val config: TotpConfig,
    ) : FieldKind
}

/** The standard fields and the custom ones in one card, as rows separated by a thin line. */
@Suppress("CyclomaticComplexMethod")
@Composable
private fun FieldsCard(
    entry: Entry,
    revealed: String?,
    onReveal: (String?) -> Unit,
    onCopy: (label: String, value: String, sensitive: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val userNameLabel = stringResource(R.string.entry_detail_user_name)
    val passwordLabel = stringResource(R.string.entry_detail_password)
    val totpLabel = stringResource(R.string.entry_detail_totp)
    val urlLabel = stringResource(R.string.entry_detail_url)
    val totpConfig = remember(entry) { entry.totp() }
    val rows =
        buildList<FieldRowData> {
            entry.fields[Entry.USER_NAME]?.let { add(FieldRowData(userNameLabel, it.value, FieldKind.Plain)) }
            entry.fields[Entry.PASSWORD]?.let { add(FieldRowData(passwordLabel, it.value, FieldKind.Secret)) }
            if (totpConfig != null) add(FieldRowData(totpLabel, "", FieldKind.Totp(totpConfig)))
            entry.fields[Entry.URL]?.let { add(FieldRowData(urlLabel, it.value, FieldKind.Url)) }
            entry.fields.forEach { (name, field) ->
                if (name !in STANDARD_FIELDS) {
                    add(FieldRowData(name, field.value, if (field.protected) FieldKind.Secret else FieldKind.Plain))
                }
            }
        }
    if (rows.isEmpty()) return
    EntryCard {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
            }
            when (val kind = row.kind) {
                FieldKind.Plain ->
                    FieldRow(label = row.label, value = row.value) {
                        CopyIconButton(label = row.label, onCopy = { onCopy(row.label, row.value, false) })
                    }
                FieldKind.Secret ->
                    SecretRow(
                        label = row.label,
                        value = row.value,
                        revealed = revealed == row.label,
                        onReveal = { shown -> onReveal(if (shown) row.label else null) },
                        onCopy = { onCopy(row.label, row.value, true) },
                    )
                FieldKind.Url ->
                    FieldRow(label = row.label, value = row.value) {
                        OpenIconButton(label = row.label, onOpen = { onOpenUrl(row.value) })
                        CopyIconButton(label = row.label, onCopy = { onCopy(row.label, row.value, false) })
                    }
                is FieldKind.Totp ->
                    TotpRow(
                        label = row.label,
                        config = kind.config,
                        revealed = revealed == row.label,
                        onReveal = { shown -> onReveal(if (shown) row.label else null) },
                        onCopy = { code -> onCopy(row.label, code, true) },
                    )
            }
        }
    }
}

/** A label above its value, with the icon buttons that belong to it. */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = RowPaddingStart,
                    end = RowPaddingEnd,
                    top = RowPaddingVertical,
                    bottom = RowPaddingVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(RowLabelGap),
        ) {
            FieldLabel(text = label)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
        actions()
    }
}

/** A secret field: dots until the user reveals it, and a copy that says it is sensitive. */
@Composable
private fun SecretRow(
    label: String,
    value: String,
    revealed: Boolean,
    onReveal: (Boolean) -> Unit,
    onCopy: () -> Unit,
) {
    FieldRow(
        label = label,
        value = if (revealed) value else SECRET_DOT.toString().repeat(value.length.coerceAtMost(MAX_DOTS)),
    ) {
        RevealIconButton(revealed = revealed, label = label, onReveal = onReveal)
        CopyIconButton(label = label, onCopy = onCopy)
    }
}

/**
 * A one-time code: hidden by default, refreshed every second, with the seconds it still has. The code never goes
 * into a description, a log or a toast; copying it marks the clip as sensitive like any other secret.
 */
@Suppress("LongMethod")
@Composable
private fun TotpRow(
    label: String,
    config: TotpConfig,
    revealed: Boolean,
    onReveal: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(config) {
        while (true) {
            now = System.currentTimeMillis()
            delay(SECOND_MILLIS - now % SECOND_MILLIS)
        }
    }
    val totp = config.codeAt(Instant.ofEpochMilli(now))
    val displayed =
        if (revealed) {
            formatTotpCode(totp.code)
        } else {
            formatTotpCode(SECRET_DOT.toString().repeat(totp.code.length))
        }
    val urgent = totp.secondsRemaining <= URGENT_SECONDS
    val expiresDescription =
        pluralStringResource(
            R.plurals.entry_detail_totp_expires_description,
            totp.secondsRemaining,
            totp.secondsRemaining,
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = RowPaddingStart,
                    end = RowPaddingEnd,
                    top = RowPaddingVertical,
                    bottom = RowPaddingVertical,
                ),
        verticalArrangement = Arrangement.spacedBy(RowLabelGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RowLabelGap),
            ) {
                FieldLabel(text = label)
                Text(
                    text = displayed,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            RevealIconButton(revealed = revealed, label = label, onReveal = onReveal)
            CopyIconButton(label = label, onCopy = { onCopy(totp.code) })
        }
        LinearProgressIndicator(
            progress = { totp.secondsRemaining.toFloat() / totp.period },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = expiresDescription },
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StatusGap),
        ) {
            Text(
                text = stringResource(R.string.entry_detail_totp_expires, totp.secondsRemaining),
                style = MaterialTheme.typography.bodySmall,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (urgent) {
                Text(
                    text = stringResource(R.string.entry_detail_totp_urgent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The small label above a value, in capitals and a quiet colour. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** An icon button of a field row; its description names the field and never its value. */
@Composable
private fun FieldIconButton(
    painter: Painter,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val container = if (filled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(IconButtonSize)
                .background(container, RoundedCornerShape(IconButtonCorner)),
    ) {
        Icon(
            painter = painter,
            contentDescription = description,
            tint = content,
            modifier = Modifier.size(IconSize),
        )
    }
}

/** The copy action of a field; its description names the field and never its value. */
@Composable
private fun CopyIconButton(
    label: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.entry_detail_copy_field, label)
    FieldIconButton(
        painter = painterResource(R.drawable.ic_content_copy),
        description = description,
        onClick = onCopy,
        modifier = modifier,
    )
}

/** The action that shows or hides a secret; its description names the field, never its value. */
@Composable
private fun RevealIconButton(
    revealed: Boolean,
    label: String,
    onReveal: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        stringResource(
            if (revealed) R.string.entry_detail_hide_field else R.string.entry_detail_show_field,
            label,
        )
    FieldIconButton(
        painter = painterResource(if (revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
        description = description,
        onClick = { onReveal(!revealed) },
        modifier = modifier,
        filled = false,
    )
}

/** The action that opens a web address in the browser. */
@Composable
private fun OpenIconButton(
    label: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.entry_detail_open_field, label)
    FieldIconButton(
        painter = painterResource(R.drawable.ic_open_in_new),
        description = description,
        onClick = onOpen,
        modifier = modifier,
    )
}

/** A card of the screen: the same surface, edge and corner as the others. */
@Composable
private fun EntryCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CardBorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column(content = content)
    }
}

/** The notes of the entry, in a card of their own. */
@Composable
private fun NotesCard(
    notes: String?,
    modifier: Modifier = Modifier,
) {
    if (notes.isNullOrBlank()) return
    EntryCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = CardPaddingHorizontal, vertical = CardPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(CardTitleGap),
        ) {
            FieldLabel(text = stringResource(R.string.entry_detail_notes))
            Text(text = notes, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The attachments of the entry, with the way to save one to a file. */
@Composable
private fun AttachmentsCard(
    entry: Entry,
    vaultBinaries: Map<BinaryId, Binary>,
    onSaveAttachment: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entry.attachments.isEmpty()) return
    val context = LocalContext.current
    EntryCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = CardPaddingHorizontal, vertical = CardPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(CardTitleGap),
        ) {
            FieldLabel(text = stringResource(R.string.entry_detail_attachments))
            entry.attachments.forEach { attachment ->
                val size = vaultBinaries[attachment.id]?.content?.size?.toLong() ?: 0L
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${attachment.name} (${Formatter.formatShortFileSize(context, size)})",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onSaveAttachment(attachment) }) {
                        Text(text = stringResource(R.string.entry_detail_save_attachment))
                    }
                }
            }
            Text(
                text = stringResource(R.string.entry_detail_attachment_unencrypted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** When the entry changed and how many older versions it keeps, in a card of its own. */
@Composable
private fun InfoCard(
    entry: Entry,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EntryCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = CardPaddingHorizontal, vertical = CardPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            entry.times.lastModified?.let { modified ->
                Text(
                    text = stringResource(R.string.entry_detail_last_modified, formatMoment(modified)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ItemGap),
            ) {
                Text(
                    text = stringResource(R.string.entry_detail_history, entry.history.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (entry.history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text(text = stringResource(R.string.entry_detail_clear_history))
                    }
                }
            }
        }
    }
}

/** The fixed bar at the bottom: change the entry, or send it to the recycle bin. */
@Composable
private fun EntryActions(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = BottomPaddingHorizontal,
                    end = BottomPaddingHorizontal,
                    top = BottomPaddingTop,
                    bottom = BottomPaddingBottom,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BottomGap),
    ) {
        Button(
            onClick = onEdit,
            shape = RoundedCornerShape(ActionCorner),
            modifier = Modifier.weight(1f).heightIn(min = EditHeight),
        ) {
            Text(text = stringResource(R.string.entry_detail_edit), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
            onClick = onDelete,
            shape = RoundedCornerShape(ActionCorner),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(CardBorderWidth, MaterialTheme.colorScheme.error.copy(alpha = ERROR_BORDER_ALPHA)),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(DeleteSize),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.entry_detail_delete),
            )
        }
    }
}

/** Asks before deleting, and says what really happens: the entry goes to the recycle bin. */
@Composable
private fun DeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.entry_detail_delete_title)) },
        text = { Text(text = stringResource(R.string.entry_detail_delete_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.entry_detail_delete_cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = stringResource(R.string.entry_detail_delete_confirm))
            }
        },
    )
}

/** Asks before deleting the previous versions of an entry. */
@Composable
private fun ClearHistoryDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.entry_detail_clear_history_title)) },
        text = { Text(text = pluralStringResource(R.plurals.entry_detail_clear_history_message, count, count)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = stringResource(R.string.entry_detail_clear_history_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.entry_detail_clear_history_cancel))
            }
        },
    )
}

/** The moment, said as how long ago it was ("3 days ago"). */
private fun relativeMoment(instant: Instant): String =
    DateUtils
        .getRelativeTimeSpanString(
            instant.toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()

/** The moment, written the way the device's locale writes dates. */
private fun formatMoment(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(instant)

/** The up-to-two initials shown on the entry's hexagon avatar. */
private fun initialsOf(title: String): String {
    val words =
        title
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
    val initials = words.take(2).joinToString("") { it.substring(0, 1).uppercase() }
    return initials.ifEmpty { "?" }
}
