package io.github.catlinux.keynexa.ui.fields

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/**
 * An editable text field for a user name that offers the names already used in the vault as a dropdown.
 *
 * The menu never takes the focus: it is anchored to an editable field of type [MenuAnchorType.PrimaryEditable], so
 * the keyboard stays with the field while the user types. Choosing an item fills the field; losing the focus closes
 * the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserNameField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var canClose by remember { mutableStateOf(false) }
    val matches = filterSuggestions(suggestions, value)
    val open = expanded && matches.isNotEmpty()

    // A popup that opened by itself must not count as the field losing the focus, so a focus loss only closes the
    // menu once it has had a frame to open.
    LaunchedEffect(open) {
        canClose = false
        if (open) {
            withFrameNanos { }
            canClose = true
        }
    }

    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed ->
                onValueChange(typed)
                expanded = filterSuggestions(suggestions, typed).isNotEmpty()
            },
            label = label,
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .onFocusChanged { focus ->
                        if (focus.isFocused) {
                            expanded = matches.isNotEmpty()
                        } else if (canClose) {
                            expanded = false
                        }
                    },
        )
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        expanded = false
                        onValueChange(suggestion)
                    },
                )
            }
        }
    }
}
