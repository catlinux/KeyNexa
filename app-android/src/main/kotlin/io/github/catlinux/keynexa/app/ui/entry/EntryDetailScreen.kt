@file:Suppress("MaxLineLength", "TooManyFunctions")

package io.github.catlinux.keynexa.app.ui.entry

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
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
private val ScreenPadding = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp

/** What separates the names of the groups an entry lives in. */
private const val PATH_SEPARATOR = " / "

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
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            EntryHeader(title = entry.title, path = path, onBack = onBack)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    DetailFields(
                        entry = entry,
                        revealed = revealed,
                        onReveal = { revealed = it },
                        onCopy = onCopy,
                        onOpenUrl = onOpenUrl,
                    )
                    EntryMetadata(
                        entry = entry,
                        vaultBinaries = vaultBinaries,
                        onSaveAttachment = { attachment ->
                            attachmentToSave = attachment
                            saveLauncher.launch(attachment.name)
                        },
                        onClearHistory = { askingToClearHistory = true },
                    )
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

/** The title of the entry, the groups it lives in, and the way back. */
@Composable
private fun EntryHeader(
    title: String,
    path: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.entry_detail_back),
                )
            }
            Text(
                text = title.ifEmpty { stringResource(R.string.entry_detail_untitled) },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (path.isNotEmpty()) {
            Text(
                text = path.joinToString(PATH_SEPARATOR),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The standard fields and the custom ones, with the actions each of them allows. */
@Suppress("LongMethod")
@Composable
private fun DetailFields(
    entry: Entry,
    revealed: String?,
    onReveal: (String?) -> Unit,
    onCopy: (label: String, value: String, sensitive: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        val userNameLabel = stringResource(R.string.entry_detail_user_name)
        entry.fields[Entry.USER_NAME]?.let { field ->
            FieldRow(label = userNameLabel, value = field.value) {
                CopyButton(
                    label = userNameLabel,
                    onCopy = { onCopy(userNameLabel, field.value, false) },
                )
            }
        }
        val passwordLabel = stringResource(R.string.entry_detail_password)
        entry.fields[Entry.PASSWORD]?.let { field ->
            SecretRow(
                label = passwordLabel,
                value = field.value,
                revealed = revealed == passwordLabel,
                onReveal = { shown -> onReveal(if (shown) passwordLabel else null) },
                onCopy = { onCopy(passwordLabel, field.value, true) },
            )
        }
        val totpLabel = stringResource(R.string.entry_detail_totp)
        remember(entry) { entry.totp() }?.let { config ->
            TotpRow(
                label = totpLabel,
                config = config,
                revealed = revealed == totpLabel,
                onReveal = { shown -> onReveal(if (shown) totpLabel else null) },
                onCopy = { code -> onCopy(totpLabel, code, true) },
            )
        }
        val urlLabel = stringResource(R.string.entry_detail_url)
        entry.fields[Entry.URL]?.let { field ->
            val openDescription = stringResource(R.string.entry_detail_open_field, urlLabel)
            FieldRow(label = urlLabel, value = field.value) {
                TextButton(
                    onClick = { onOpenUrl(field.value) },
                    modifier = Modifier.semantics { contentDescription = openDescription },
                ) {
                    Text(text = stringResource(R.string.entry_detail_open))
                }
                CopyButton(
                    label = urlLabel,
                    onCopy = { onCopy(urlLabel, field.value, false) },
                )
            }
        }
        val notesLabel = stringResource(R.string.entry_detail_notes)
        entry.fields[Entry.NOTES]?.let { field -> FieldRow(label = notesLabel, value = field.value) }
        entry.fields.filterKeys { it !in STANDARD_FIELDS }.forEach { (name, field) ->
            if (field.protected) {
                SecretRow(
                    label = name,
                    value = field.value,
                    revealed = revealed == name,
                    onReveal = { shown -> onReveal(if (shown) name else null) },
                    onCopy = { onCopy(name, field.value, true) },
                )
            } else {
                FieldRow(label = name, value = field.value) {
                    CopyButton(
                        label = name,
                        onCopy = { onCopy(name, field.value, false) },
                    )
                }
            }
        }
    }
}

/** A label above its value, with the actions that belong to it. */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = ItemGap),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(ItemGap)) { actions() }
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
        TextButton(onClick = { onReveal(!revealed) }) {
            Text(text = stringResource(if (revealed) R.string.entry_detail_hide else R.string.entry_detail_show))
        }
        CopyButton(label = label, onCopy = onCopy)
    }
}

/**
 * A one-time code: hidden by default, refreshed every second, with the seconds it still has. The code never goes
 * into a description, a log or a toast; copying it marks the clip as sensitive like any other secret.
 */
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
        modifier = Modifier.fillMaxWidth().padding(vertical = ItemGap),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(text = displayed, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(ItemGap)) {
            TextButton(onClick = { onReveal(!revealed) }) {
                Text(text = stringResource(if (revealed) R.string.entry_detail_hide else R.string.entry_detail_show))
            }
            CopyButton(label = label, onCopy = { onCopy(totp.code) })
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            LinearProgressIndicator(
                progress = { totp.secondsRemaining.toFloat() / totp.period },
                modifier = Modifier.weight(1f).semantics { contentDescription = expiresDescription },
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.entry_detail_totp_expires, totp.secondsRemaining),
                style = MaterialTheme.typography.bodySmall,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The copy action of a field; its description names the field and never its value. */
@Composable
private fun CopyButton(
    label: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.entry_detail_copy_field, label)
    TextButton(
        onClick = onCopy,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Text(text = stringResource(R.string.entry_detail_copy))
    }
}

/** What is known about the entry itself: attachments, when it changed and how many older versions it keeps. */
@Composable
private fun EntryMetadata(
    entry: Entry,
    vaultBinaries: Map<BinaryId, Binary>,
    onSaveAttachment: (Attachment) -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        if (entry.attachments.isNotEmpty()) {
            Text(
                text = stringResource(R.string.entry_detail_attachments),
                style = MaterialTheme.typography.titleMedium,
            )
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

/** The two things a user does with an entry: change it, or send it to the recycle bin. */
@Composable
private fun EntryActions(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Button(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.entry_detail_edit))
        }
        OutlinedButton(
            onClick = onDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.entry_detail_delete))
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

/** The moment, written the way the device's locale writes dates. */
private fun formatMoment(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(instant)
