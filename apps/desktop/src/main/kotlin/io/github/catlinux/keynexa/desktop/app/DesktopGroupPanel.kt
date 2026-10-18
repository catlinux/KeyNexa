package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_collapse
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_entries_count
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_expand
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_new_group
import io.github.catlinux.keynexa.session.findGroup
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.util.UUID

/** The compact rows of the tree, how far a level is indented and how wide the fold control is. */
private val GroupRowHeight = 36.dp
private val RowIndent = 14.dp
private val RowPaddingStart = 8.dp
private val RowPaddingEnd = 10.dp
private val RowGap = 6.dp
private val FoldWidth = 14.dp
private val PanelGap = 6.dp
private const val DIVIDER_ALPHA = 0.3f

/** The marks that say whether a group shows its subgroups; there are no icons on the desktop yet. */
private const val FOLDED = "▸"
private const val UNFOLDED = "▾"

/**
 * The left panel: the tree of groups, one compact row each, with the number of entries below every group. The
 * recycle bin is listed at the bottom, set apart from the tree, and shows its entries like any other group.
 */
@Composable
internal fun GroupPanel(
    vault: Vault,
    view: DesktopVaultState,
    modifier: Modifier = Modifier,
) {
    val recycleBin = vault.meta.recycleBinGroup
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(vertical = PanelGap),
    ) {
        GroupBranch(group = vault.root, depth = 0, vault = vault, recycleBin = recycleBin, view = view)
        val bin = recycleBin?.let { vault.root.findGroup(it) }
        if (bin != null) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = PanelGap),
                color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA),
            )
            GroupBranch(group = bin, depth = 0, vault = vault, recycleBin = recycleBin, view = view)
        }
        TextButton(
            onClick = { view.ask(VaultPrompt.NewGroup(view.selectedGroup ?: vault.root.id)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = RowPaddingStart),
        ) {
            Text(text = stringResource(Res.string.group_new_group))
        }
    }
}

/** One group and, unless it is folded, the groups below it. */
@Composable
private fun GroupBranch(
    group: Group,
    depth: Int,
    vault: Vault,
    recycleBin: UUID?,
    view: DesktopVaultState,
) {
    GroupRow(group = group, depth = depth, vault = vault, view = view)
    if (group.id in view.collapsed) return
    treeChildren(group, recycleBin).forEach { child -> GroupBranch(child, depth + 1, vault, recycleBin, view) }
}

/** One row of the tree: the fold control, the name and how many entries the group holds. */
@Composable
private fun GroupRow(
    group: Group,
    depth: Int,
    vault: Vault,
    view: DesktopVaultState,
) {
    NodeContextMenu(actions = groupActions(vault, group.id), onAction = { runNodeAction(it, group.id, view) }) {
        GroupRowContent(group = group, depth = depth, view = view)
    }
}

/** What a group row shows. */
@Composable
private fun GroupRowContent(
    group: Group,
    depth: Int,
    view: DesktopVaultState,
) {
    val selected = view.selectedGroup == group.id
    val count = entryCount(group)
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(GroupRowHeight)
                .background(background)
                .clickable(role = Role.Button, onClick = { view.selectGroup(group) })
                .padding(start = RowPaddingStart + RowIndent * depth, end = RowPaddingEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        FoldControl(
            hasChildren = group.groups.isNotEmpty(),
            collapsed = group.id in view.collapsed,
            onClick = { view.toggleGroup(group.id) },
        )
        Text(
            text = group.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Text(
                text = pluralStringResource(Res.plurals.group_entries_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The fold control of a group; a group without subgroups keeps the room but has nothing to fold. */
@Composable
private fun FoldControl(
    hasChildren: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    if (!hasChildren) {
        Spacer(modifier = Modifier.width(FoldWidth))
        return
    }
    val description = stringResource(if (collapsed) Res.string.group_expand else Res.string.group_collapse)
    Text(
        text = if (collapsed) FOLDED else UNFOLDED,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .width(FoldWidth)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
    )
}
