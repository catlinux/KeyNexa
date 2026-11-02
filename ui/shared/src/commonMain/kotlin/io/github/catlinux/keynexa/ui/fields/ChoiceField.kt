package io.github.catlinux.keynexa.ui.fields

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * A field whose value is one of [options] (a key and the text shown for it), chosen from a menu under the field.
 * The text cannot be typed into: the whole field opens the menu.
 */
@Composable
fun <K> ChoiceField(
    selected: K,
    options: List<Pair<K, String>>,
    onSelect: (K) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = label,
            trailingIcon = { Text(text = if (open) "▴" else "▾", style = MaterialTheme.typography.titleMedium) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.matchParentSize().clickable(role = Role.DropdownList) { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text = text) },
                    onClick = {
                        open = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}
