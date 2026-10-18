package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_history
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_history_back
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_untitled
import io.github.catlinux.keynexa.vault.Entry
import org.jetbrains.compose.resources.stringResource
import java.time.ZoneId
import java.util.Locale

private val HistoryGap = 6.dp
private const val DIVIDER_ALPHA = 0.25f

/**
 * The previous versions of the entry, newest first, with the way back to the entry itself. Clicking one shows it
 * read-only in the same panel.
 */
@Composable
internal fun EntryHistorySection(
    entry: Entry,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HistoryGap)) {
        Text(
            text = stringResource(Res.string.entry_detail_history, entry.history.size),
            style = MaterialTheme.typography.titleMedium,
        )
        val zone = ZoneId.systemDefault()
        val locale = Locale.getDefault()
        entry.history.indices.reversed().forEach { index ->
            val version = entry.history[index]
            val date = shortDate(version.times.lastModified, zone, locale)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = { view.showVersion(index) })
                        .padding(vertical = HistoryGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HistoryGap),
            ) {
                Text(
                    text = version.title.ifEmpty { stringResource(Res.string.entry_detail_untitled) },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (date.isNotEmpty()) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
        }
    }
}

/** The way back from a previous version to the entry it belongs to. */
@Composable
internal fun HistoryBackButton(
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = { view.closeDetailMode() }, modifier = modifier) {
        Text(text = stringResource(Res.string.entry_detail_history_back))
    }
}
