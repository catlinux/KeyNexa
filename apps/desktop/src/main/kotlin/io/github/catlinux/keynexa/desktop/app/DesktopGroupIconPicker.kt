package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_icon_category_devices
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_icon_category_finance
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_icon_category_general
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_icon_category_internet
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_icon_category_security
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_icon_category_work
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_icon_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_icon_option
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val CellSize = 40.dp
private val CellIconSize = 22.dp
private val CellGap = 6.dp
private val CellShape = RoundedCornerShape(8.dp)
private val CellBorder = 1.5.dp

/** The label of a section of the picker. */
private fun categoryLabel(category: GroupIconCategory): StringResource =
    when (category) {
        GroupIconCategory.General -> Res.string.group_icon_category_general
        GroupIconCategory.Security -> Res.string.group_icon_category_security
        GroupIconCategory.Internet -> Res.string.group_icon_category_internet
        GroupIconCategory.Finance -> Res.string.group_icon_category_finance
        GroupIconCategory.Devices -> Res.string.group_icon_category_devices
        GroupIconCategory.Work -> Res.string.group_icon_category_work
    }

/** The section an icon number is in, or the first one when the number is not in the catalogue. */
internal fun categoryOf(iconId: Int): GroupIconCategory =
    GroupIconCatalog.firstOrNull { it.id == iconId }?.category ?: GroupIconCategory.General

/** The catalogue as a picker: one chip per section and the icons of the chosen section as a grid. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupIconPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var category by remember { mutableStateOf(categoryOf(selected)) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CellGap * 2)) {
        Text(
            text = stringResource(Res.string.group_icon_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            GroupIconCategory.entries.forEach { entry ->
                FilterChip(
                    selected = entry == category,
                    onClick = { category = entry },
                    label = { Text(text = stringResource(categoryLabel(entry))) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CellGap),
            verticalArrangement = Arrangement.spacedBy(CellGap),
        ) {
            GroupIconCatalog.filter { it.category == category }.forEach { entry ->
                IconCell(icon = entry, chosen = entry.id == selected, onClick = { onSelect(entry.id) })
            }
        }
    }
}

/** One icon of the grid; the chosen one is outlined and filled. */
@Composable
private fun IconCell(
    icon: GroupIcon,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(Res.string.group_icon_option, icon.id)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(CellSize)
                .background(if (chosen) colors.primaryContainer else colors.surface, CellShape)
                .border(CellBorder, if (chosen) colors.primary else colors.outline.copy(alpha = 0.4f), CellShape)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon.vector,
            contentDescription = null,
            tint = if (chosen) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(CellIconSize),
        )
    }
}
