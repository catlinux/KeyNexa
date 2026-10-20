package io.github.catlinux.keynexa.ui.fields

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val ListShape = RoundedCornerShape(12.dp)
private val ListMaxHeight = 220.dp
private val ToggleSize = 40.dp
private val RowPadding = 14.dp
private const val ALL_LIMIT = 30

/**
 * An editable text field for a user name that can suggest the names already used in the vault.
 *
 * The list is drawn right under the field, not in a popup, and none of its parts takes the focus: the field keeps the
 * keyboard while the list is open, so the user can type freely. It opens by itself while typing when something
 * matches, and with the arrow it shows every name. Choosing one fills the field; Esc, losing the focus or choosing
 * closes it.
 */
@Suppress("LongParameterList")
@Composable
fun UserNameField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: @Composable () -> Unit,
    toggleDescription: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val shown = if (showAll) suggestions.take(ALL_LIMIT) else filterSuggestions(suggestions, value)
    val listOpen = open && shown.isNotEmpty()

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed ->
                onValueChange(typed)
                showAll = false
                open = filterSuggestions(suggestions, typed).isNotEmpty()
            },
            label = label,
            singleLine = true,
            trailingIcon = {
                if (suggestions.isNotEmpty()) {
                    ArrowButton(
                        open = listOpen,
                        description = toggleDescription,
                        onClick = {
                            field.requestFocus()
                            showAll = true
                            open = !listOpen
                        },
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .onFocusChanged { if (!it.isFocused) open = false }
                    .onPreviewKeyEvent { event ->
                        val close = listOpen && event.type == KeyEventType.KeyDown && event.key == Key.Escape
                        if (close) open = false
                        close
                    },
        )
        if (listOpen) {
            SuggestionList(
                names = shown,
                onPick = { suggestion ->
                    onValueChange(suggestion)
                    showAll = false
                    open = false
                },
            )
        }
    }
}

/** The names under the field. No row takes the focus, so choosing one never moves the keyboard away from the text. */
@Composable
private fun SuggestionList(
    names: List<String>,
    onPick: (String) -> Unit,
) {
    Surface(
        shape = ListShape,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.heightIn(max = ListMaxHeight).verticalScroll(rememberScrollState())) {
            names.forEach { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusProperties { canFocus = false }
                            .clickable { onPick(name) }
                            .padding(RowPadding),
                )
            }
        }
    }
}

/** The small arrow at the end of the field; it never takes the focus from the text. */
@Composable
private fun ArrowButton(
    open: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(ToggleSize)
                .focusProperties { canFocus = false }
                .semantics { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = if (open) "▴" else "▾",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
