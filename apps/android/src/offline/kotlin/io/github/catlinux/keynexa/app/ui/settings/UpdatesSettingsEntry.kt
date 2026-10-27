package io.github.catlinux.keynexa.app.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R

private val NotePadding = 16.dp

/**
 * The offline variant cannot reach the network, so it cannot look for updates: it says how to get them instead
 * (ADR-0015).
 */
@Composable
fun UpdatesSettingsEntry() {
    SettingsCard(titleRes = R.string.settings_section_updates) {
        Text(text = stringResource(R.string.settings_updates_offline), modifier = Modifier.padding(NotePadding))
    }
}
