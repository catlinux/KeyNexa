package io.github.catlinux.keynexa.app.ui.group

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.ui.common.SaveStatus
import io.github.catlinux.keynexa.app.ui.common.SaveStatusBar
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Group
import java.time.Instant
import java.util.UUID

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ScreenPadding = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp

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
)

/**
 * One group of the open vault: its subgroups, its entries, and the ways to search, add, lock or leave.
 *
 * A stateless screen: it shows the data it is given and reports what the user chose. It never reads storage, never
 * touches the vault in memory and never holds a secret. Entries that have expired are marked with a word, not only
 * with a colour.
 */
@Suppress("LongParameterList")
@Composable
fun GroupScreen(
    group: Group,
    isRoot: Boolean,
    hiddenGroupId: UUID?,
    status: SaveStatus,
    onOpenGroup: (UUID) -> Unit,
    onOpenEntry: (UUID) -> Unit,
    onAddEntry: () -> Unit,
    onAddGroup: (name: String) -> Unit,
    onSearch: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onLock: () -> Unit,
    onBack: () -> Unit,
    onRetrySave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigation = GroupNavigation(onOpenGroup = onOpenGroup, onOpenEntry = onOpenEntry, onBack = onBack)
    val commands =
        GroupCommands(
            onAddEntry = onAddEntry,
            onAddGroup = onAddGroup,
            onSearch = onSearch,
            onRecycleBin = onOpenRecycleBin,
            onLock = onLock,
        )
    val emptyMessage = stringResource(if (isRoot) R.string.group_empty_root else R.string.group_empty)
    var askingForName by remember { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            GroupTopBar(name = group.name, navigation = navigation, commands = commands)
            SaveStatusBar(status = status, onRetry = onRetrySave)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                GroupList(
                    subgroups = group.groups.filterNot { it.id == hiddenGroupId },
                    entries = group.entries,
                    emptyMessage = emptyMessage,
                    navigation = navigation,
                    now = Instant.now(),
                )
            }
            GroupActions(commands = commands, onNewGroup = { askingForName = true })
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
}

/** The name of the group, the way back, search, and the actions that do not fit in the list. */
@Composable
private fun GroupTopBar(
    name: String,
    navigation: GroupNavigation,
    commands: GroupCommands,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        IconButton(onClick = navigation.onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.group_back),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = commands.onSearch) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.group_search),
            )
        }
        GroupMenu(commands = commands)
    }
}

/** The overflow menu: the recycle bin and locking the vault. */
@Composable
private fun GroupMenu(
    commands: GroupCommands,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.group_more),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.group_recycle_bin)) },
                onClick = {
                    open = false
                    commands.onRecycleBin()
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

/** The subgroups first, then the entries, in a list that scrolls; or what to do when there is nothing yet. */
@Composable
private fun GroupList(
    subgroups: List<Group>,
    entries: List<Entry>,
    emptyMessage: String,
    navigation: GroupNavigation,
    now: Instant,
) {
    if (subgroups.isEmpty() && entries.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ItemGap),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = emptyMessage, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        subgroups.forEach { group ->
            GroupRow(group = group, onOpen = { navigation.onOpenGroup(group.id) })
        }
        entries.forEach { entry ->
            EntryRow(entry = entry, now = now, onOpen = { navigation.onOpenEntry(entry.id) })
        }
    }
}

/** One subgroup: its name and how much is inside it, said out loud for a screen reader too. */
@Composable
private fun GroupRow(
    group: Group,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = group.groups.size + group.entries.size
    val description = pluralStringResource(R.plurals.group_row_group, count, group.name, count)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}

/** One entry: its title, its user name, and a word when it has expired. Never its password. */
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
    val expired = entry.times.expires && entry.times.expiry?.isBefore(now) == true
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(vertical = ItemGap),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (entry.userName.isNotEmpty()) {
            Text(
                text = entry.userName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expired) {
            Text(
                text = stringResource(R.string.group_expired),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The two things a user adds from here, in the lower half of the screen and a thumb away. */
@Composable
private fun GroupActions(
    commands: GroupCommands,
    onNewGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Button(
            onClick = commands.onAddEntry,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.group_new_entry))
        }
        OutlinedButton(
            onClick = onNewGroup,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.group_new_group))
        }
    }
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
