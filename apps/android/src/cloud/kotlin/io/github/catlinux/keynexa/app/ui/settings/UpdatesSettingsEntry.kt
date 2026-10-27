package io.github.catlinux.keynexa.app.ui.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.updates.AndroidUpdates
import io.github.catlinux.keynexa.app.updates.UpdatePhase
import io.github.catlinux.keynexa.update.UpdateChoice
import kotlinx.coroutines.launch

private val ButtonPadding = 16.dp
private val ButtonMinHeight = 48.dp

/**
 * The updates section of the settings (ADR-0015): a switch for looking for new versions and a button to look right
 * now, with what it found. The offline variant shows a note instead, because it cannot reach the network.
 */
@Composable
fun UpdatesSettingsEntry() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phase by AndroidUpdates.phase.collectAsState()
    var choice by remember { mutableStateOf(AndroidUpdates.choice(context)) }
    val on = choice == UpdateChoice.On

    SettingsCard(titleRes = R.string.settings_section_updates) {
        SwitchSetting(
            label = stringResource(R.string.settings_updates_check),
            checked = on,
            onCheckedChange = { value ->
                val next = if (value) UpdateChoice.On else UpdateChoice.Off
                AndroidUpdates.setChoice(context, next)
                choice = next
            },
            description = stringResource(R.string.settings_updates_help),
        )
        if (on) {
            OutlinedButton(
                onClick = { scope.launch { AndroidUpdates.check(context, asked = true) } },
                enabled = phase != UpdatePhase.Checking,
                modifier = Modifier.padding(horizontal = ButtonPadding).heightIn(min = ButtonMinHeight),
            ) {
                Text(text = stringResource(R.string.settings_updates_now))
            }
            statusText(phase)?.let { Text(text = it, modifier = Modifier.padding(ButtonPadding)) }
        }
    }
}

/** What the last look for updates found, in a line; null when there is nothing to say. */
@Composable
private fun statusText(phase: UpdatePhase): String? =
    when (phase) {
        UpdatePhase.Checking -> stringResource(R.string.settings_updates_checking)
        UpdatePhase.UpToDate -> stringResource(R.string.settings_updates_current)
        is UpdatePhase.Offered ->
            stringResource(
                R.string.settings_updates_available,
                phase.offer.manifest.version
                    .toString(),
            )
        is UpdatePhase.Failed ->
            stringResource(
                if (phase.reason == "network") {
                    R.string.settings_updates_failed_network
                } else {
                    R.string.settings_updates_failed_checks
                },
            )
        else -> null
    }
