package io.github.catlinux.keynexa.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.cloud.CloudConsentStore
import io.github.catlinux.keynexa.app.cloud.CloudDownloadOutcome
import io.github.catlinux.keynexa.app.cloud.CloudStores
import io.github.catlinux.keynexa.app.cloud.CloudVault
import io.github.catlinux.keynexa.app.cloud.SYNC_PREFIX
import io.github.catlinux.keynexa.app.cloud.cloudVaults
import io.github.catlinux.keynexa.app.cloud.downloadCloudVault
import io.github.catlinux.keynexa.app.cloud.formatSize
import io.github.catlinux.keynexa.app.cloud.shortVaultId
import io.github.catlinux.keynexa.app.cloud.suggestedVaultFileName
import io.github.catlinux.keynexa.cloud.S3Setup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val BringActionHeight = 54.dp
private val BringActionCorner = 14.dp
private val BringDialogGap = 8.dp
private val BringRowPadding = 12.dp
private val BringRowCorner = 12.dp

/** What the bring dialog says after a pick that did not end in a saved file. */
private enum class BringNotice { Known, Gone, Failed }

/**
 * The way to bring a synchronised vault down to this device (ADR-0009 §7): a home action that lists the objects under
 * `KeyNexa/sync/` and writes the one the user picks into a file they choose with the system picker.
 *
 * It is shown only when the cloud edition has synchronisation turned on; the offline variant ships an empty function
 * with the same signature. An existing file is never overwritten: the pick is refused before anything is written.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun CloudBringVault(
    isKnownVault: (String) -> Boolean,
    onSaved: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<S3Setup?>(null) }
    var loading by remember { mutableStateOf(false) }
    var listFailed by remember { mutableStateOf(false) }
    var vaults by remember { mutableStateOf(emptyList<CloudVault>()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<BringNotice?>(null) }
    var chosen by remember { mutableStateOf<CloudVault?>(null) }
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val vault = chosen
            chosen = null
            val ready = setup
            if (uri == null || vault == null || ready == null) return@rememberLauncherForActivityResult
            if (isKnownVault(uri.toString())) {
                notice = BringNotice.Known
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                busy = true
                val outcome =
                    downloadCloudVault(ready.backend(), vault.objectName) {
                        context.contentResolver.openOutputStream(uri, "wt")
                    }
                busy = false
                when (outcome) {
                    CloudDownloadOutcome.Saved -> {
                        showDialog = false
                        onSaved(uri)
                    }
                    CloudDownloadOutcome.Gone -> notice = BringNotice.Gone
                    is CloudDownloadOutcome.Failed -> notice = BringNotice.Failed
                }
            }
        }

    LaunchedEffect(Unit) {
        available = withContext(Dispatchers.IO) { isAvailable(context) }
    }
    LaunchedEffect(showDialog) {
        if (!showDialog) return@LaunchedEffect
        loading = true
        listFailed = false
        val loaded =
            withContext(Dispatchers.IO) {
                val ready = CloudStores.credentials(context).load()?.let(S3Setup::fromValues)
                if (ready == null || !ready.syncEnabled) {
                    null
                } else {
                    runCatching { cloudVaults(ready.backend().list(SYNC_PREFIX)) }
                        .map { ready to it }
                        .getOrNull()
                }
            }
        if (loaded == null) {
            listFailed = true
        } else {
            setup = loaded.first
            vaults = loaded.second
        }
        loading = false
    }

    if (!available) return

    OutlinedButton(
        onClick = {
            loading = true
            showDialog = true
        },
        shape = RoundedCornerShape(BringActionCorner),
        modifier = Modifier.fillMaxWidth().heightIn(min = BringActionHeight),
    ) {
        Text(text = stringResource(R.string.cloud_bring_action))
    }

    if (showDialog) {
        BringVaultDialog(
            loading = loading,
            listFailed = listFailed,
            vaults = vaults,
            busy = busy,
            notice = notice,
            onPick = { vault ->
                chosen = vault
                notice = null
                saveLauncher.launch(suggestedVaultFileName(vault.id))
            },
            onClose = { showDialog = false },
        )
    }
}

/** True when the credentials are stored, the consent accepted and synchronisation turned on. */
private fun isAvailable(context: android.content.Context): Boolean {
    val setup = CloudStores.credentials(context).load()?.let(S3Setup::fromValues) ?: return false
    return CloudConsentStore(context).accepted() && setup.syncEnabled
}

/** The list of synchronised vaults, with the word for what it is doing and the line after a failed pick. */
@Composable
private fun BringVaultDialog(
    loading: Boolean,
    listFailed: Boolean,
    vaults: List<CloudVault>,
    busy: Boolean,
    notice: BringNotice?,
    onPick: (CloudVault) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = stringResource(R.string.cloud_bring_action),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(BringDialogGap),
            ) {
                when {
                    loading -> BringLine(text = stringResource(R.string.cloud_bring_loading))
                    listFailed -> BringLine(text = stringResource(R.string.cloud_bring_list_failed))
                    vaults.isEmpty() -> BringLine(text = stringResource(R.string.cloud_bring_empty))
                    else ->
                        vaults.forEach { vault ->
                            BringVaultRow(
                                vault = vault,
                                enabled = !busy,
                                onClick = { onPick(vault) },
                            )
                        }
                }
                notice?.let { BringNoticeLine(notice = it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(text = stringResource(R.string.cloud_close))
            }
        },
    )
}

/** One line of information for the dialog: what it is loading, that it failed, or that there is nothing. */
@Composable
private fun BringLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One synchronised vault, as a row the user can click: the short id and, under it, the size and the date. */
@Composable
private fun BringVaultRow(
    vault: CloudVault,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(BringRowCorner),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(BringRowPadding),
            verticalArrangement = Arrangement.spacedBy(BringDialogGap),
        ) {
            Text(
                text = stringResource(R.string.cloud_bring_row, shortVaultId(vault.id)),
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
                            R.string.cloud_bring_details,
                            formatSize(vault.size),
                            shortDate(modifiedAt),
                        )
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The line the dialog shows after a pick that did not end in a saved file. */
@Composable
private fun BringNoticeLine(notice: BringNotice) {
    Text(
        text =
            stringResource(
                when (notice) {
                    BringNotice.Known -> R.string.cloud_bring_known
                    BringNotice.Gone -> R.string.cloud_bring_gone
                    BringNotice.Failed -> R.string.cloud_bring_failed
                },
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

/** A short, locale-aware date in the device's own zone, which is what the row shows next to the size. */
private fun shortDate(instant: Instant): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
