package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import io.github.catlinux.keynexa.desktop.app.generated.resources.entries_column_modified
import io.github.catlinux.keynexa.desktop.app.generated.resources.entries_column_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.entries_column_user
import io.github.catlinux.keynexa.desktop.app.generated.resources.entries_column_web
import io.github.catlinux.keynexa.desktop.app.generated.resources.entries_empty
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_untitled
import io.github.catlinux.keynexa.desktop.app.generated.resources.search_no_results
import io.github.catlinux.keynexa.ui.brand.HexBadge
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.time.ZoneId
import java.util.Locale

/** The dense rows, the header that sorts them, and how narrow the panel can be before the web and date columns go. */
private val RowHeight = 44.dp
private val HeaderHeight = 32.dp
private val RowPadding = 10.dp
private val RowGap = 10.dp
private val BadgeSize = 24.dp
private val PanelPadding = 16.dp
private val NarrowPanelWidth = 520.dp
private const val TITLE_WEIGHT = 3f
private const val USER_WEIGHT = 2f
private const val WEB_WEIGHT = 2f
private const val DATE_WEIGHT = 1.5f
private const val DIVIDER_ALPHA = 0.25f
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
    BoxWithConstraints(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        val wide = maxWidth >= NarrowPanelWidth
        Column(modifier = Modifier.fillMaxSize()) {
            EntryListHeader(sort = view.sort, wide = wide, onSort = view::changeSort)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
            if (rows.isEmpty()) {
                EmptyEntries(searching = view.query.isNotBlank(), modifier = Modifier.weight(1f))
            } else {
                EntryList(rows = rows, wide = wide, view = view, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The header of the list: the two columns that can order it are buttons. */
@Composable
private fun EntryListHeader(
    sort: EntrySort,
    wide: Boolean,
    onSort: (EntrySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(HeaderHeight).padding(horizontal = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        SortHeader(
            text = stringResource(Res.string.entries_column_title),
            active = sort == EntrySort.Title,
            onClick = { onSort(EntrySort.Title) },
            modifier = Modifier.weight(TITLE_WEIGHT),
        )
        if (wide) {
            HeaderCell(text = stringResource(Res.string.entries_column_user), modifier = Modifier.weight(USER_WEIGHT))
            HeaderCell(text = stringResource(Res.string.entries_column_web), modifier = Modifier.weight(WEB_WEIGHT))
        }
        SortHeader(
            text = stringResource(Res.string.entries_column_modified),
            active = sort == EntrySort.Modified,
            onClick = { onSort(EntrySort.Modified) },
            modifier = Modifier.weight(DATE_WEIGHT),
        )
    }
}

/** A column header that only names its column. */
@Composable
private fun HeaderCell(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A column header that orders the list, and says so while its order is the one in use. */
@Composable
private fun SortHeader(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (active) "$text $SORT_MARK" else text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** The rows themselves, with the keyboard that moves through them. */
@Composable
private fun EntryList(
    rows: List<EntryRow>,
    wide: Boolean,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(view.selectedGroup) { focus.requestFocus() }
    LaunchedEffect(rows) { view.selectEntry(selectionAmong(view.selectedEntry, rows.map { it.entry.id })) }
    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event -> moveSelection(event, rows, view, listState, scope) },
    ) {
        items(rows, key = { it.entry.id }) { row ->
            EntryListRow(
                row = row,
                wide = wide,
                selected = view.selectedEntry == row.entry.id,
                view = view,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
        }
    }
}

/** One row of the list: the avatar with the initial and title, the user, the web domain and the date. */
@Composable
private fun EntryListRow(
    row: EntryRow,
    wide: Boolean,
    selected: Boolean,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(RowHeight)
                .background(background)
                .clickable(role = Role.Button, onClick = { view.selectEntry(row.entry.id) })
                .padding(horizontal = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        HexBadge(
            text = initialOf(row.entry.title),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = BadgeSize,
        )
        TitleCell(row = row, searching = view.query.isNotBlank(), modifier = Modifier.weight(TITLE_WEIGHT))
        if (wide) {
            Text(
                text = row.entry.userName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(USER_WEIGHT),
            )
            Text(
                text = domainOf(row.entry.url).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(WEB_WEIGHT),
            )
        }
        Text(
            text = shortDate(row.entry.times.lastModified, ZoneId.systemDefault(), Locale.getDefault()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(DATE_WEIGHT),
        )
    }
}

/** The title of a row and, while a search is on, the groups it lives under. */
@Composable
private fun TitleCell(
    row: EntryRow,
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = row.entry.title.ifEmpty { stringResource(Res.string.entry_detail_untitled) },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (searching) {
            Text(
                text = row.groupPath.joinToString(PATH_SEPARATOR),
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

/** Moves the selection with the arrows and puts the focus on the detail with Enter. */
private fun moveSelection(
    event: KeyEvent,
    rows: List<EntryRow>,
    view: DesktopVaultState,
    listState: LazyListState,
    scope: CoroutineScope,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val current = rows.indexOfFirst { it.entry.id == view.selectedEntry }
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

/** The mark that says which column the list is ordered by. */
private const val SORT_MARK = "▾"
