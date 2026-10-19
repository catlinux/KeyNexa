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
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_history_back
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_untitled
import io.github.catlinux.keynexa.vault.Entry
import org.jetbrains.compose.resources.stringResource
import java.time.ZoneId
import java.util.Locale

private val HistoryRowPaddingHorizontal = 14.dp
private val HistoryRowPaddingVertical = 10.dp
private val HistoryGap = 6.dp
private const val DIVIDER_ALPHA = 0.25f

/**
 * The previous versions of the entry, newest first, as the rows of the history card; the card supplies the title and
 * the edges. Clicking one shows it read-only in the same panel.
 */
@Composable
internal fun EntryHistorySection(
    entry: Entry,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    Column(modifier = modifier.fillMaxWidth()) {
        entry.history.indices.reversed().forEachIndexed { position, index ->
            if (position > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
            }
            val version = entry.history[index]
            val date = shortDate(version.times.lastModified, zone, locale)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = { view.showVersion(index) })
                        .padding(
                            horizontal = HistoryRowPaddingHorizontal,
                            vertical = HistoryRowPaddingVertical,
                        ),
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
