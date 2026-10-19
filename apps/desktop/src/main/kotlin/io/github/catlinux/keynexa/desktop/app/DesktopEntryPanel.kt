@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.bin_empty
import io.github.catlinux.keynexa.desktop.app.generated.resources.entries_empty
import io.github.catlinux.keynexa.desktop.app.generated.resources.entries_header
import io.github.catlinux.keynexa.desktop.app.generated.resources.entries_sort_modified
import io.github.catlinux.keynexa.desktop.app.generated.resources.entries_sort_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_untitled
import io.github.catlinux.keynexa.desktop.app.generated.resources.search_field_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.search_no_results
import io.github.catlinux.keynexa.session.findGroup
import io.github.catlinux.keynexa.ui.brand.HexBadge
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** The cards of the list, the line that heads it, and how narrow the panel can be before the date goes. */
private val CardPadding = 12.dp
private val CardGap = 8.dp
private val CardShape = RoundedCornerShape(10.dp)
private val CardBorder = 1.dp
private val SelectedBorder = 1.5.dp
private val HeaderPadding = 16.dp
private val BadgeSize = 32.dp
private val PanelPadding = 16.dp
private val ListPadding = 12.dp
private val NarrowPanelWidth = 420.dp
private const val BORDER_ALPHA = 0.4f
private const val PATH_SEPARATOR = " › "

/**
 * The centre panel: a dense list of the entries of the selected group, or of the matches of the search over the whole
 * vault. Up and Down move the selection and Enter puts the focus on the detail.
 */
@Composable
internal fun EntryListPanel(
    vault: Vault,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val search = remember { VaultSearch() }
    val rows =
        remember(vault, view.selectedGroup, view.query, view.sort) {
            sortedRows(entryRows(vault, view.selectedGroup, view.query, search), view.sort)
        }
    BoxWithConstraints(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= NarrowPanelWidth
        Column(modifier = Modifier.fillMaxSize()) {
            val bin = vault.meta.recycleBinGroup
            if (view.selectedGroup == bin && bin != null) {
                BinActions(vault = vault, binId = bin, view = view)
            }
            EntryListHeader(
                label = listLabel(vault, view),
                count = rows.size,
                sort = view.sort,
                onSort = view::changeSort,
            )
            if (rows.isEmpty()) {
                EmptyEntries(searching = view.query.isNotBlank(), modifier = Modifier.weight(1f))
            } else {
                EntryList(rows = rows, wide = wide, vault = vault, view = view, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** What the recycle bin offers over everything it holds. */
@Composable
private fun BinActions(
    vault: Vault,
    binId: UUID,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val bin = vault.root.findGroup(binId)
    val empty = bin == null || (bin.entries.isEmpty() && bin.groups.isEmpty())
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = CardPadding, vertical = CardPadding),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = { view.ask(VaultPrompt.EmptyBin) }, enabled = !empty) {
            Text(text = stringResource(Res.string.bin_empty))
        }
    }
}

/** What the list is of: the name of the selected group, or "Search" while a search is on. */
@Composable
private fun listLabel(
    vault: Vault,
    view: DesktopVaultState,
): String =
    if (view.query.isNotBlank()) {
        stringResource(Res.string.search_field_label)
    } else {
        view.selectedGroup?.let { vault.root.findGroup(it)?.name }.orEmpty()
    }

/** The line over the cards: which group and how many entries it shows, and the order, which a click flips. */
@Composable
private fun EntryListHeader(
    label: String,
    count: Int,
    sort: EntrySort,
    onSort: (EntrySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = if (sort == EntrySort.Title) EntrySort.Modified else EntrySort.Title
    val sortLabel = if (sort == EntrySort.Title) Res.string.entries_sort_title else Res.string.entries_sort_modified
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = HeaderPadding, vertical = CardGap + CardGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        Text(
            text = pluralStringResource(Res.plurals.entries_header, count, label, count).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(sortLabel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.clickable(role = Role.Button, onClick = { onSort(next) }),
        )
    }
}

/** The rows themselves, with the keyboard that moves through them. */
@Composable
private fun EntryList(
    rows: List<EntryRow>,
    wide: Boolean,
    vault: Vault,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(view.selectedGroup) { focus.requestFocus() }
    LaunchedEffect(rows) {
        view.showRows(rows.map { it.entry.id })
        view.selectEntry(selectionAmong(view.selectedEntry, rows.map { it.entry.id }))
    }
    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event -> moveSelection(event, rows, vault, view, listState, scope) },
        contentPadding = PaddingValues(start = ListPadding, end = ListPadding, bottom = ListPadding),
        verticalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        items(rows, key = { it.entry.id }) { row ->
            EntryListRow(
                row = row,
                wide = wide,
                selected = view.selectedEntry == row.entry.id,
                vault = vault,
                view = view,
            )
        }
    }
}

/** One card of the list: the avatar with the initial, the title with the user name under it, and the date. */
@Composable
private fun EntryListRow(
    row: EntryRow,
    wide: Boolean,
    selected: Boolean,
    vault: Vault,
    view: DesktopVaultState,
) {
    NodeContextMenu(actions = entryActions(vault, row.entry.id), onAction = { runNodeAction(it, row.entry.id, view) }) {
        EntryListRowContent(row = row, wide = wide, selected = selected, view = view)
    }
}

/** What an entry card shows. */
@Composable
private fun EntryListRowContent(
    row: EntryRow,
    wide: Boolean,
    selected: Boolean,
    view: DesktopVaultState,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (selected) colors.secondaryContainer else colors.surface, CardShape)
                .border(
                    if (selected) SelectedBorder else CardBorder,
                    if (selected) colors.primary else colors.outline.copy(alpha = BORDER_ALPHA),
                    CardShape,
                ).clip(CardShape)
                .clickable(role = Role.Button, onClick = { view.selectEntry(row.entry.id) })
                .padding(CardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardPadding),
    ) {
        HexBadge(
            text = initialOf(row.entry.title),
            containerColor = if (selected) colors.primary else colors.primaryContainer,
            contentColor = if (selected) colors.onPrimary else colors.onPrimaryContainer,
            size = BadgeSize,
        )
        TitleCell(row = row, searching = view.query.isNotBlank(), modifier = Modifier.weight(1f))
        if (wide) {
            Text(
                text = shortDate(row.entry.times.lastModified, ZoneId.systemDefault(), Locale.getDefault()),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The title of a card and, under it, the user name; while a search is on, the groups the entry lives under. */
@Composable
private fun TitleCell(
    row: EntryRow,
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    val second = if (searching) row.groupPath.joinToString(PATH_SEPARATOR) else row.entry.userName
    Column(modifier = modifier) {
        Text(
            text = row.entry.title.ifEmpty { stringResource(Res.string.entry_detail_untitled) },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (second.isNotEmpty()) {
            Text(
                text = second,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What the list says when it has nothing to show. */
@Composable
private fun EmptyEntries(
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(if (searching) Res.string.search_no_results else Res.string.entries_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(PanelPadding),
        )
    }
}

/** Moves the selection with the arrows, deletes with the Delete key and puts the focus on the detail with Enter. */
@Suppress("LongParameterList", "ReturnCount")
private fun moveSelection(
    event: KeyEvent,
    rows: List<EntryRow>,
    vault: Vault,
    view: DesktopVaultState,
    listState: LazyListState,
    scope: CoroutineScope,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val current = rows.indexOfFirst { it.entry.id == view.selectedEntry }
    if (event.key == Key.Delete) {
        val entry = rows.getOrNull(current)?.entry ?: return false
        if (!isInsideBin(vault, entry.id)) view.ask(VaultPrompt.MoveToBin(entry.id))
        return true
    }
    val wanted =
        when (event.key) {
            Key.DirectionDown -> current + 1
            Key.DirectionUp -> if (current < 0) rows.lastIndex else current - 1
            Key.Enter -> {
                view.focusDetail()
                return true
            }
            else -> return false
        }
    val index = wanted.coerceIn(0, rows.lastIndex)
    view.selectEntry(rows[index].entry.id)
    scope.launch { listState.animateScrollToItem(index) }
    return true
}
