package io.github.catlinux.keynexa.app.ui.search

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.vault.VaultSearch
import java.util.UUID

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ScreenPadding = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp

/** What separates the names of the groups a result lives in. */
private const val PATH_SEPARATOR = " / "

/**
 * Searches the open vault: it asks [search] for the matches of what the user types and shows them with the groups
 * they live in. A stateless screen: the caller decides what searching means, nothing is stored, and no entry content
 * travels anywhere but through [onOpenEntry].
 */
@Composable
fun SearchScreen(
    search: (String) -> List<VaultSearch.Result>,
    onOpenEntry: (UUID) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { search(query) }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.search_back),
                    )
                }
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.weight(1f),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    query.isBlank() -> SearchNotice(text = stringResource(R.string.search_prompt))
                    results.isEmpty() -> SearchNotice(text = stringResource(R.string.search_no_results))
                    else -> SearchResults(results = results, onOpenEntry = onOpenEntry)
                }
            }
        }
    }
}

/** The search box: it takes the focus as soon as the screen appears and never autocorrects. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(text = stringResource(R.string.search_field_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

/** What the screen says before the user types, and when nothing matches. */
@Composable
private fun SearchNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(ItemGap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The matches, in a list that scrolls. */
@Composable
private fun SearchResults(
    results: List<VaultSearch.Result>,
    onOpenEntry: (UUID) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        results.forEach { result ->
            ResultRow(result = result, onOpen = { onOpenEntry(result.entry.id) })
        }
    }
}

/** One match: its title, its user name and the groups it lives in. Never a secret. */
@Composable
private fun ResultRow(
    result: VaultSearch.Result,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(vertical = ItemGap),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(
            text = result.entry.title.ifEmpty { stringResource(R.string.search_untitled) },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (result.entry.userName.isNotEmpty()) {
            Text(
                text = result.entry.userName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (result.path.isNotEmpty()) {
            Text(
                text = result.path.joinToString(PATH_SEPARATOR),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
