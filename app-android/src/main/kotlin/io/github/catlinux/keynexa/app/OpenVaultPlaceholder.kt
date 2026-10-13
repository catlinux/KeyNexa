package io.github.catlinux.keynexa.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.vault.SessionState

private val ScreenPadding = 16.dp
private val TouchTarget = 48.dp

/** Stands in for the browsing screens of phase D4.4: shows that the vault opened and lets the user lock it. */
@Composable
fun OpenVaultPlaceholder(
    state: SessionState,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = state as? SessionState.Open
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ScreenPadding),
        ) {
            Text(text = open?.vault?.name.orEmpty(), style = MaterialTheme.typography.headlineMedium)
            Text(
                text =
                    stringResource(
                        R.string.placeholder_open,
                        open
                            ?.current
                            ?.root
                            ?.entries
                            ?.size ?: 0,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onLock, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
                Text(text = stringResource(R.string.placeholder_lock))
            }
        }
    }
}
