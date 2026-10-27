package io.github.catlinux.keynexa.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.updates.AndroidUpdates
import io.github.catlinux.keynexa.app.updates.Notice
import io.github.catlinux.keynexa.app.updates.UpdatePhase
import io.github.catlinux.keynexa.update.UpdateChoice
import kotlinx.coroutines.launch

private val PromptGap = 8.dp
private val PromptTouchTarget = 48.dp

/**
 * A small, never-modal card on the home screen for updates (ADR-0015): the one-time question, and a newer version when
 * there is one. It looks for updates once per start, only after the user said yes. The offline variant ships an empty
 * function with the same signature.
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phase by AndroidUpdates.phase.collectAsState()
    var choice by remember { mutableStateOf(AndroidUpdates.choice(context)) }

    LaunchedEffect(choice) {
        if (choice == UpdateChoice.On) AndroidUpdates.check(context, asked = false)
    }
    when {
        choice == UpdateChoice.Ask ->
            PromptCard(
                text = stringResource(R.string.update_question_text),
                title = stringResource(R.string.update_question_title),
                primary = stringResource(R.string.update_question_yes),
                onPrimary = {
                    AndroidUpdates.setChoice(context, UpdateChoice.On)
                    choice = UpdateChoice.On
                },
                secondary = stringResource(R.string.update_question_no),
                onSecondary = {
                    AndroidUpdates.setChoice(context, UpdateChoice.Off)
                    choice = UpdateChoice.Off
                },
            )
        choice == UpdateChoice.On -> {
            when (val current = phase) {
                is UpdatePhase.Offered ->
                    PromptCard(
                        title = stringResource(R.string.update_offer_title),
                        text = offerText(current),
                        primary = stringResource(R.string.update_offer_install),
                        onPrimary = { scope.launch { AndroidUpdates.install(context, current.offer) } },
                        secondary = stringResource(R.string.update_offer_later),
                        onSecondary = AndroidUpdates::later,
                    )
                UpdatePhase.Downloading ->
                    PromptCard(
                        title = stringResource(R.string.update_offer_title),
                        text = stringResource(R.string.update_offer_downloading),
                    )
                UpdatePhase.Installing ->
                    PromptCard(
                        title = stringResource(R.string.update_offer_title),
                        text = stringResource(R.string.update_offer_installing),
                    )
                else -> Unit
            }
        }
    }
}

@Composable
private fun offerText(offered: UpdatePhase.Offered): String {
    val version =
        offered.offer.manifest.version
            .toString()
    val body =
        stringResource(
            R.string.update_offer_text,
            version,
            offered.offer.manifest.released
                .toString(),
        )
    return when (offered.notice) {
        Notice.None -> body
        Notice.InstallFailed -> stringResource(R.string.update_offer_failed) + "\n\n" + body
        Notice.NeedsPermission -> stringResource(R.string.update_offer_needs_permission) + "\n\n" + body
    }
}

/** The card itself: a title, a text and up to two buttons. */
@Composable
private fun PromptCard(
    title: String,
    text: String,
    primary: String? = null,
    onPrimary: () -> Unit = {},
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(PromptGap),
            verticalArrangement = Arrangement.spacedBy(PromptGap),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            if (primary != null || secondary != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PromptGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (primary != null) {
                        Button(onClick = onPrimary, modifier = Modifier.heightIn(min = PromptTouchTarget)) {
                            Text(text = primary)
                        }
                    }
                    if (secondary != null) {
                        TextButton(onClick = onSecondary, modifier = Modifier.heightIn(min = PromptTouchTarget)) {
                            Text(text = secondary)
                        }
                    }
                }
            }
        }
    }
}
