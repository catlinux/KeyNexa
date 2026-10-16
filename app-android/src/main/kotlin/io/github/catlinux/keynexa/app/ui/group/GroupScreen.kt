@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.app.ui.group

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.ui.common.ConflictNotice
import io.github.catlinux.keynexa.app.ui.common.ConflictNoticeBar
import io.github.catlinux.keynexa.app.ui.common.HexBadge
import io.github.catlinux.keynexa.app.ui.common.SaveStatus
import io.github.catlinux.keynexa.app.ui.common.SaveStatusBar
import io.github.catlinux.keynexa.app.ui.theme.LocalDarkTheme
import io.github.catlinux.keynexa.sync.RecoveryOutcome
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.totp
import java.time.Instant
import java.util.UUID

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ItemGap = 8.dp
private val TouchTarget = 48.dp
private val PanelPadding = 12.dp
private val NameGap = 2.dp

/** The rounded band at the top: its padding, corner radius and the controls it holds. */
private val BandPaddingHorizontal = 20.dp
private val BandPaddingTop = 28.dp
private val BandPaddingBottom = 24.dp
private val BandGap = 16.dp
private val BandCorner = 24.dp
private val BandButtonSize = 48.dp
private val BandButtonCorner = 12.dp
private const val BAND_BUTTON_ALPHA = 0.12f
private val StatusIconSize = 16.dp
private val StatusStroke = 2.dp
private val StatusGap = 6.dp
private val SearchHeight = 48.dp
private val SearchCorner = 14.dp
private val SearchPaddingHorizontal = 14.dp
private val SearchGap = 10.dp
private val SearchIconSize = 20.dp

/** The scrolling content below the band: the grid of groups and the card of entries. */
private val ContentPaddingHorizontal = 20.dp
private val ContentPaddingTop = 22.dp
private val ContentPaddingBottom = 96.dp
private val ContentGap = 12.dp
private val GridGap = 10.dp
private val CardCorner = 16.dp
private val CardPadding = 14.dp
private val CardContentGap = 10.dp
private const val CARD_BORDER_ALPHA = 0.4f
private val GroupBadgeSize = 32.dp
private val EntryBadgeSize = 40.dp
private val RowPaddingHorizontal = 14.dp
private val RowPaddingVertical = 12.dp
private val RowGap = 12.dp
private val ChipPaddingHorizontal = 8.dp
private val ChipPaddingVertical = 4.dp
private const val DIVIDER_ALPHA = 0.2f
private val FabPadding = 20.dp

/** The places the rows of a group can take the user. */
private class GroupNavigation(
    val onOpenGroup: (UUID) -> Unit,
    val onOpenEntry: (UUID) -> Unit,
    val onBack: () -> Unit,
)

/** What the user can ask for while in a group. */
private class GroupCommands(
    val onAddEntry: () -> Unit,
    val onAddGroup: (String) -> Unit,
    val onSearch: () -> Unit,
    val onRecycleBin: () -> Unit,
    val onLock: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * The title of the group screen: the vault name at the root (the file's root group is usually just "Root"), the
 * group's own name elsewhere.
 */
internal fun groupScreenTitle(
    groupName: String,
    isRoot: Boolean,
    rootTitle: String?,
): String = if (isRoot && rootTitle != null) rootTitle else groupName

/**
 * One group of the open vault: its subgroups, its entries, and the ways to search, add, lock or leave.
 *
 * A stateless screen: it shows the data it is given and reports what the user chose. It never reads storage, never
 * touches the vault in memory and never holds a secret. Entries that have expired are marked with a word, not only
 * with a colour.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun GroupScreen(
    group: Group,
    isRoot: Boolean,
    hiddenGroupId: UUID?,
    status: SaveStatus,
    conflicts: ConflictNotice?,
    syncWarnDays: Int,
    fileLastModifiedMillis: Long?,
    lastKeyNexaSaveMillis: Long?,
    lastExternalChangeMillis: Long?,
    onOpenGroup: (UUID) -> Unit,
    onOpenEntry: (UUID) -> Unit,
    onAddEntry: () -> Unit,
    onAddGroup: (name: String) -> Unit,
    onSearch: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onLock: () -> Unit,
    onBack: () -> Unit,
    onRetrySave: () -> Unit,
    onDeleteConflictCopies: () -> Unit,
    onMergeOtherPassword: (String) -> Unit,
    onDismissConflicts: () -> Unit,
    modifier: Modifier = Modifier,
    rootTitle: String? = null,
    onReconnect: () -> Unit = {},
    recovered: RecoveryOutcome? = null,
    onRestoreUnsaved: () -> Unit = {},
    onDiscardUnsaved: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    var askingForName by remember { mutableStateOf(false) }
    var showSyncStatus by remember { mutableStateOf(false) }
    var syncWarningDismissed by remember { mutableStateOf(false) }
    val navigation = GroupNavigation(onOpenGroup = onOpenGroup, onOpenEntry = onOpenEntry, onBack = onBack)
    val commands =
        GroupCommands(
            onAddEntry = onAddEntry,
            onAddGroup = onAddGroup,
            onSearch = onSearch,
            onRecycleBin = onOpenRecycleBin,
            onLock = onLock,
            onSettings = onOpenSettings,
        )
    val emptyMessage = stringResource(if (isRoot) R.string.group_empty_root else R.string.group_empty)
    val hasRecoveredUnsaved =
        recovered is RecoveryOutcome.SaveHadNotStarted ||
            recovered is RecoveryOutcome.ChangedElsewhere ||
            recovered is RecoveryOutcome.Restored
    val warningDays =
        if (isRoot && syncWarnDays > 0 && !syncWarningDismissed) {
            syncWarning(fileLastModifiedMillis, System.currentTimeMillis(), syncWarnDays)
        } else {
            null
        }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                GroupHeaderBand(
                    title = groupScreenTitle(group.name, isRoot, rootTitle),
                    isRoot = isRoot,
                    status = status,
                    navigation = navigation,
                    commands = commands,
                    onSyncStatus = { showSyncStatus = true },
                    onNewGroup = { askingForName = true },
                    onSearch = commands.onSearch,
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = ContentPaddingHorizontal,
                                end = ContentPaddingHorizontal,
                                top = ContentGap,
                            ),
                    verticalArrangement = Arrangement.spacedBy(ItemGap),
                ) {
                    SaveStatusBar(status = status, onRetry = onRetrySave, onReconnect = onReconnect)
                    if (conflicts != null) {
                        ConflictNoticeBar(
                            notice = conflicts,
                            onDeleteCopies = onDeleteConflictCopies,
                            onMergeOtherPassword = onMergeOtherPassword,
                            onDismiss = onDismissConflicts,
                        )
                    }
                    if (hasRecoveredUnsaved) {
                        RecoveredPanel(onRestore = onRestoreUnsaved, onDiscard = onDiscardUnsaved)
                    }
                    if (warningDays != null) {
                        SyncWarningPanel(days = warningDays, onDismiss = { syncWarningDismissed = true })
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    GroupContent(
                        subgroups = group.groups.filterNot { it.id == hiddenGroupId },
                        entries = group.entries,
                        emptyMessage = emptyMessage,
                        navigation = navigation,
                        now = Instant.now(),
                    )
                }
            }
            ExtendedFloatingActionButton(
                onClick = commands.onAddEntry,
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                text = { Text(text = stringResource(R.string.group_new_entry)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(FabPadding),
            )
        }
    }
    if (askingForName) {
        NewGroupDialog(
            onCreate = { name ->
                commands.onAddGroup(name)
                askingForName = false
            },
            onDismiss = { askingForName = false },
        )
    }
    if (showSyncStatus) {
        SyncStatusDialog(
            fileLastModifiedMillis = fileLastModifiedMillis,
            lastKeyNexaSaveMillis = lastKeyNexaSaveMillis,
            lastExternalChangeMillis = lastExternalChangeMillis,
            onDismiss = { showSyncStatus = false },
        )
    }
}

/** The coloured band at the top: the group name, its save state, the ways out, and the search field. */
@Suppress("LongParameterList")
@Composable
private fun GroupHeaderBand(
    title: String,
    isRoot: Boolean,
    status: SaveStatus,
    navigation: GroupNavigation,
    commands: GroupCommands,
    onSyncStatus: () -> Unit,
    onNewGroup: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val container = if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val content = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(bottomStart = BandCorner, bottomEnd = BandCorner),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    start = BandPaddingHorizontal,
                    end = BandPaddingHorizontal,
                    top = BandPaddingTop,
                    bottom = BandPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(BandGap),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ItemGap),
            ) {
                if (!isRoot) {
                    BandIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = stringResource(R.string.group_back),
                        onClick = navigation.onBack,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NameGap),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SaveStatusLine(status = status)
                }
                BandIconButton(
                    icon = Icons.Filled.Lock,
                    description = stringResource(R.string.group_lock),
                    onClick = commands.onLock,
                )
                GroupMenu(commands = commands, onSyncStatus = onSyncStatus, onNewGroup = onNewGroup)
            }
            BandSearchField(title = title, onClick = onSearch)
        }
    }
}

/** A round icon button with the translucent background the band uses for its controls. */
@Composable
private fun BandIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(BandButtonSize)
                .bandButtonBackground(),
    ) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(SearchIconSize))
    }
}

/** The translucent background the band's icon buttons share. */
@Composable
private fun Modifier.bandButtonBackground(): Modifier =
    background(
        LocalContentColor.current.copy(alpha = BAND_BUTTON_ALPHA),
        RoundedCornerShape(BandButtonCorner),
    )

/** A field that looks like search but opens the search screen: it only reports the tap. */
@Composable
private fun BandSearchField(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.group_search_in, title)
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(SearchHeight)
                .semantics(mergeDescendants = true) { contentDescription = label },
        shape = RoundedCornerShape(SearchCorner),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SearchPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SearchGap),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(SearchIconSize),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The small line under the title: saved, saving, or changes waiting to be saved. */
@Composable
private fun SaveStatusLine(
    status: SaveStatus,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector?
    val textRes: Int
    when {
        status.problem != null -> {
            icon = Icons.Filled.Warning
            textRes = R.string.group_status_unsaved
        }
        status.saving -> {
            icon = null
            textRes = R.string.save_saving
        }
        status.hasUnsavedChanges -> {
            icon = Icons.Filled.Warning
            textRes = R.string.group_status_unsaved
        }
        else -> {
            icon = Icons.Filled.Check
            textRes = R.string.group_status_saved
        }
    }
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StatusGap),
    ) {
        if (icon == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(StatusIconSize),
                color = LocalContentColor.current,
                strokeWidth = StatusStroke,
            )
        } else {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(StatusIconSize))
        }
        Text(text = stringResource(textRes), style = MaterialTheme.typography.bodySmall)
    }
}

/** The overflow menu: the ways to add a group, see the sync status, empty the bin, change settings or lock. */
@Composable
private fun GroupMenu(
    commands: GroupCommands,
    onSyncStatus: () -> Unit,
    onNewGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        BandIconButton(
            icon = Icons.Default.MoreVert,
            description = stringResource(R.string.group_more),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.group_new_group)) },
                onClick = {
                    open = false
                    onNewGroup()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.group_sync_status_menu)) },
                onClick = {
                    open = false
                    onSyncStatus()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.group_recycle_bin)) },
                onClick = {
                    open = false
                    commands.onRecycleBin()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.group_settings)) },
                onClick = {
                    open = false
                    commands.onSettings()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.group_lock)) },
                onClick = {
                    open = false
                    commands.onLock()
                },
            )
        }
    }
}

/** The subgroups as a two-column grid, then the entries in one card; or what to do when there is nothing yet. */
@Composable
private fun GroupContent(
    subgroups: List<Group>,
    entries: List<Entry>,
    emptyMessage: String,
    navigation: GroupNavigation,
    now: Instant,
) {
    if (subgroups.isEmpty() && entries.isEmpty()) {
        EmptyState(message = emptyMessage)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = ContentPaddingHorizontal,
                end = ContentPaddingHorizontal,
                top = ContentPaddingTop,
                bottom = ContentPaddingBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(ContentGap),
    ) {
        if (subgroups.isNotEmpty()) {
            item { SectionLabel(textRes = R.string.group_section_groups) }
            items(subgroups.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                    row.forEach { group ->
                        GroupCard(
                            group = group,
                            onOpen = { navigation.onOpenGroup(group.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (entries.isNotEmpty()) {
            item { SectionLabel(textRes = R.string.group_section_entries) }
            item {
                EntriesCard(entries = entries, now = now, onOpen = navigation.onOpenEntry)
            }
        }
    }
}

/** The word above a section of the list. */
@Composable
private fun SectionLabel(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(textRes).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** What a group with nothing inside it says. */
@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(ContentPaddingHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One subgroup as a card: its hexagon initial, its name and how much is inside it. */
@Composable
private fun GroupCard(
    group: Group,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = group.groups.size + group.entries.size
    val description = pluralStringResource(R.plurals.group_row_group, count, group.name, count)
    val usePrimary = remember(group.name) { group.name.hashCode() % 2 == 0 }
    Surface(
        onClick = onOpen,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(CardContentGap),
        ) {
            HexBadge(
                text = initialOf(group.name),
                containerColor =
                    if (usePrimary) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                contentColor =
                    if (usePrimary) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                size = GroupBadgeSize,
            )
            Column(verticalArrangement = Arrangement.spacedBy(NameGap)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.group_entries_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Every entry of the group in a single card, with a separator between rows. */
@Composable
private fun EntriesCard(
    entries: List<Entry>,
    now: Instant,
    onOpen: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                EntryRow(entry = entry, now = now, onOpen = { onOpen(entry.id) })
                if (index != entries.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
                }
            }
        }
    }
}

/** One entry: its initials, its title, its user name, a word when it has expired, and a 2FA chip. */
@Composable
private fun EntryRow(
    entry: Entry,
    now: Instant,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = entry.title.ifEmpty { stringResource(R.string.group_entry_untitled) }
    val description =
        if (entry.userName.isEmpty()) {
            stringResource(R.string.group_row_entry, title)
        } else {
            stringResource(R.string.group_row_entry_with_user, title, entry.userName)
        }
    val hasTotp = remember(entry) { entry.totp() != null }
    val expired = entry.times.expires && entry.times.expiry?.isBefore(now) == true
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = RowPaddingHorizontal, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        HexBadge(
            text = initialsOf(title),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            size = EntryBadgeSize,
        )
        EntryLabels(title = title, userName = entry.userName, expired = expired, modifier = Modifier.weight(1f))
        if (hasTotp) {
            TotpChip()
        }
    }
}

/** The title, the user name and the expiry word of an entry. */
@Composable
private fun EntryLabels(
    title: String,
    userName: String,
    expired: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(NameGap),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (userName.isNotEmpty()) {
            Text(
                text = userName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expired) {
            Text(
                text = stringResource(R.string.group_expired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The small tag that marks an entry with a one-time code. */
@Composable
private fun TotpChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.group_chip_2fa),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical),
        )
    }
}

/** The offer to bring back the changes of a session that did not finish saving. */
@Composable
private fun RecoveredPanel(
    onRestore: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(PanelPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = stringResource(R.string.save_recovered_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ItemGap)) {
                TextButton(onClick = onRestore) {
                    Text(text = stringResource(R.string.save_recovered_restore))
                }
                TextButton(onClick = onDiscard) {
                    Text(text = stringResource(R.string.save_recovered_discard))
                }
            }
        }
    }
}

/** The warning that the vault file has not changed for days, which suggests syncing stopped. */
@Composable
private fun SyncWarningPanel(
    days: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(PanelPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = pluralStringResource(R.plurals.group_sync_warning, days, days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.group_sync_warning_dismiss))
            }
        }
    }
}

/** Shows the vault file's synchronisation status: file modification, last KeyNexa save, last external change. */
@Composable
private fun SyncStatusDialog(
    fileLastModifiedMillis: Long?,
    lastKeyNexaSaveMillis: Long?,
    lastExternalChangeMillis: Long?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.group_sync_status_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
                Text(text = stringResource(R.string.group_sync_file_modified, relativeTime(fileLastModifiedMillis)))
                Text(
                    text =
                        stringResource(
                            R.string.group_sync_last_saved,
                            lastKeyNexaSaveMillis?.let { relativeTime(it) }
                                ?: stringResource(R.string.group_sync_never_this_session),
                        ),
                )
                Text(
                    text =
                        stringResource(
                            R.string.group_sync_last_external,
                            lastExternalChangeMillis?.let { relativeTime(it) }
                                ?: stringResource(R.string.group_sync_never_this_session),
                        ),
                )
                Text(
                    text = stringResource(R.string.group_sync_status_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.group_sync_status_close))
            }
        },
    )
}

/** Formats an instant as a relative span such as "2 hours ago". */
@Composable
private fun relativeTime(millis: Long?): String {
    if (millis == null) return stringResource(R.string.group_sync_unknown)
    return DateUtils
        .getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
}

/** Asks for the name of a new group; an empty name cannot be created. */
@Composable
private fun NewGroupDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.group_new_group_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.group_new_group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.group_new_group_cancel))
            }
        },
        dismissButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(text = stringResource(R.string.group_new_group_create))
            }
        },
    )
}

/** The initial shown on a group's hexagon avatar. */
private fun initialOf(name: String): String {
    val trimmed = name.trim()
    return if (trimmed.isEmpty()) "?" else trimmed.substring(0, 1).uppercase()
}

/** The up-to-two initials shown on an entry's hexagon avatar. */
private fun initialsOf(title: String): String {
    val words =
        title
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
    val initials = words.take(2).joinToString("") { it.substring(0, 1).uppercase() }
    return initials.ifEmpty { "?" }
}
