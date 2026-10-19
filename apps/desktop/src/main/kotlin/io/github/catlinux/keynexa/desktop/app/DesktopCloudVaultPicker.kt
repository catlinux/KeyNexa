package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.desktop.DesktopCloudBackup
import io.github.catlinux.keynexa.desktop.FileStoragePort
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_close
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_pick_details
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_pick_empty
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_pick_error
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_pick_exists
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_pick_failed
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_pick_gone
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_pick_loading
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_pick_row
import io.github.catlinux.keynexa.desktop.app.generated.resources.cloud_pick_save_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.home_bring_from_cloud
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.sync.SyncIds
import io.github.catlinux.keynexa.vault.VaultLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The prefix of the synchronised objects (ADR-0009 §1). `SyncIds` keeps its own copy private, so the desktop client
 * repeats the same string to be able to list them.
 */
internal const val SYNC_PREFIX = "KeyNexa/sync/"

private val PickerDialogSize = DpSize(640.dp, 640.dp)
private val PickerPadding = 20.dp
private val PickerBlockGap = 16.dp
private val PickerItemGap = 8.dp
private val PickerTouchTarget = 44.dp
private val PickerContentWidth = 560.dp
private val PickerCardShape = RoundedCornerShape(16.dp)
private val PickerCardBorderWidth = 1.dp
private const val PICKER_CARD_BORDER_ALPHA = 0.4f
private val PickerNoticePadding = 12.dp

private const val BYTES_PER_KILOBYTE = 1024L
private const val SHORT_ID_LENGTH = 8

/** One synchronised vault in the service: only what its name and the listing say, never its content. */
internal data class CloudVault(
    val id: String,
    val objectName: String,
    val size: Long,
    val modifiedAt: Instant?,
)

/**
 * The vault [cloudObject] stands for, or null when its name is not exactly `KeyNexa/sync/<id>.kdbx` for a well-formed
 * id. The probe objects (`KeyNexa/sync/.probe-…`), malformed ids and other prefixes are ignored.
 */
internal fun cloudVaultOf(cloudObject: CloudObject): CloudVault? {
    val name = cloudObject.name
    if (!name.startsWith(SYNC_PREFIX) || !name.endsWith(VAULT_EXTENSION)) return null
    val id = name.removePrefix(SYNC_PREFIX).removeSuffix(VAULT_EXTENSION)
    if (!SyncIds.isValid(id) || name != SyncIds.objectName(id)) return null
    return CloudVault(id, name, cloudObject.size, cloudObject.modifiedAt)
}

/** The synchronised vaults among [objects], newest first; a vault with no date goes last, ties by name. */
internal fun cloudVaults(objects: List<CloudObject>): List<CloudVault> =
    objects
        .mapNotNull(::cloudVaultOf)
        .sortedWith(
            compareBy<CloudVault> { it.modifiedAt == null }
                .thenByDescending { it.modifiedAt }
                .thenBy { it.objectName },
        )

/** The first eight characters of a vault id, which is what the picker shows without revealing the whole one. */
internal fun shortVaultId(id: String): String = id.take(SHORT_ID_LENGTH)

/** The name the picker suggests for a downloaded vault: the app's name and the short id. */
internal fun suggestedVaultFileName(id: String): String = "KeyNexa-" + shortVaultId(id) + VAULT_EXTENSION

/** A byte size in the units the picker shows: bytes, kilobytes or megabytes, with a dot as the decimal separator. */
internal fun formatSize(bytes: Long): String =
    when {
        bytes < BYTES_PER_KILOBYTE -> "$bytes B"
        bytes < BYTES_PER_KILOBYTE * BYTES_PER_KILOBYTE ->
            String.format(Locale.ROOT, "%.1f kB", bytes.toDouble() / BYTES_PER_KILOBYTE)
        else ->
            String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / (BYTES_PER_KILOBYTE * BYTES_PER_KILOBYTE))
    }

/** What a download did: the file was written, a file was already there, the object is gone, or it failed. */
internal sealed interface DownloadOutcome {
    /** The bytes were written to the destination. */
    data object Saved : DownloadOutcome

    /** A file already sat at the destination; it was not touched. */
    data object AlreadyExists : DownloadOutcome

    /** The object is no longer in the service. */
    data object Gone : DownloadOutcome

    /** The fetch or the write failed. */
    data class Failed(
        val error: IOException,
    ) : DownloadOutcome
}

/**
 * Downloads [objectName] from [backend] to [destination], off the caller's thread.
 *
 * An existing file is never overwritten and is never even read: it answers [DownloadOutcome.AlreadyExists] before
 * touching the network. The bytes are written exactly as they came, through the same atomic write the rest of the app
 * uses, and are never logged.
 */
internal suspend fun downloadCloudVault(
    backend: CloudBackend,
    objectName: String,
    destination: File,
): DownloadOutcome =
    withContext(Dispatchers.IO) {
        if (vaultFileExists(destination)) return@withContext DownloadOutcome.AlreadyExists
        try {
            val content = backend.fetch(objectName) ?: return@withContext DownloadOutcome.Gone
            FileStoragePort().write(VaultLocation(destination.absolutePath), content.bytes)
            DownloadOutcome.Saved
        } catch (failure: IOException) {
            DownloadOutcome.Failed(failure)
        }
    }

/** What the picker shows after a pick that did not end in a saved file. */
internal enum class PickerNotice { Exists, Gone, Failed }

/**
 * The dialog that lists the vaults synchronised in the service and downloads the one the user picks.
 *
 * It reads the credentials and lists the objects off the composition thread. Picking one asks where to save it with
 * the native dialog and then downloads it; a file that is already there is never overwritten. On success the new
 * known vault is handed to [onPicked] and the dialog closes.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun ApplicationScope.CloudVaultPickerDialog(
    cloud: DesktopCloudBackup,
    parent: Frame,
    onDismiss: () -> Unit,
    onPicked: (KnownVault) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<S3Setup?>(null) }
    var vaults by remember { mutableStateOf(emptyList<CloudVault>()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<PickerNotice?>(null) }
    val saveTitle = stringResource(Res.string.cloud_pick_save_title)

    LaunchedEffect(Unit) {
        val loaded =
            withContext(Dispatchers.IO) {
                val ready =
                    cloud.stores.credentials
                        .load()
                        ?.let(S3Setup::fromValues)
                if (ready == null || !ready.syncEnabled) {
                    null
                } else {
                    runCatching { cloudVaults(ready.backend().list(SYNC_PREFIX)) }
                        .map { ready to it }
                        .getOrNull()
                }
            }
        if (loaded == null) {
            failed = true
        } else {
            setup = loaded.first
            vaults = loaded.second
        }
        loading = false
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(size = PickerDialogSize),
        title = stringResource(Res.string.home_bring_from_cloud),
        resizable = false,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = PickerContentWidth)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(PickerPadding),
                    verticalArrangement = Arrangement.spacedBy(PickerBlockGap),
                ) {
                    Text(
                        text = stringResource(Res.string.home_bring_from_cloud),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val ready = setup
                    if (loading) {
                        PickerLine(Res.string.cloud_pick_loading)
                    } else if (failed || ready == null) {
                        PickerLine(Res.string.cloud_pick_failed)
                    } else if (vaults.isEmpty()) {
                        PickerLine(Res.string.cloud_pick_empty)
                    } else {
                        vaults.forEach { vault ->
                            CloudVaultRow(
                                vault = vault,
                                enabled = !busy,
                                onClick = {
                                    val file = chooseNewVaultFile(parent, saveTitle, suggestedVaultFileName(vault.id))
                                    if (file != null) {
                                        scope.launch {
                                            busy = true
                                            val outcome = downloadCloudVault(ready.backend(), vault.objectName, file)
                                            busy = false
                                            when (outcome) {
                                                DownloadOutcome.Saved ->
                                                    onPicked(
                                                        KnownVault(
                                                            vaultNameOf(file.name),
                                                            VaultLocation(file.absolutePath),
                                                        ),
                                                    )
                                                DownloadOutcome.AlreadyExists -> notice = PickerNotice.Exists
                                                DownloadOutcome.Gone -> notice = PickerNotice.Gone
                                                is DownloadOutcome.Failed -> notice = PickerNotice.Failed
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    notice?.let { shown -> PickerNoticeCard(shown) }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = PickerTouchTarget),
                    ) {
                        Text(text = stringResource(Res.string.cloud_close))
                    }
                }
            }
        }
    }
}

/** One line of information for the picker: what it is loading, that it failed, or that there is nothing. */
@Composable
private fun PickerLine(text: StringResource) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One synchronised vault, as a card the user can click: the short id and, under it, the size and the date. */
@Composable
private fun CloudVaultRow(
    vault: CloudVault,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = PickerCardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border =
            BorderStroke(
                PickerCardBorderWidth,
                MaterialTheme.colorScheme.outline.copy(alpha = PICKER_CARD_BORDER_ALPHA),
            ),
    ) {
        Column(
            modifier = Modifier.padding(PickerBlockGap),
            verticalArrangement = Arrangement.spacedBy(PickerItemGap),
        ) {
            Text(
                text = stringResource(Res.string.cloud_pick_row, shortVaultId(vault.id)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            val modifiedAt = vault.modifiedAt
            Text(
                text =
                    if (modifiedAt == null) {
                        formatSize(vault.size)
                    } else {
                        stringResource(
                            Res.string.cloud_pick_details,
                            formatSize(vault.size),
                            shortDate(modifiedAt, ZoneId.systemDefault(), Locale.getDefault()),
                        )
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The line the picker shows after a pick that did not end in a saved file. */
@Composable
private fun PickerNoticeCard(notice: PickerNotice) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PickerCardShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text =
                stringResource(
                    when (notice) {
                        PickerNotice.Exists -> Res.string.cloud_pick_exists
                        PickerNotice.Gone -> Res.string.cloud_pick_gone
                        PickerNotice.Failed -> Res.string.cloud_pick_error
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(PickerNoticePadding),
        )
    }
}
