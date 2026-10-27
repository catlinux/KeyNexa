package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.UpdateCheck
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_offer_downloading
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_offer_failed
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_offer_install
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_offer_later
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_offer_text
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_offer_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_question_no
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_question_text
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_question_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.update_question_yes
import org.jetbrains.compose.resources.stringResource

private val DialogGap = 12.dp

/** The one-time question: may the app look for new versions (ADR-0015). The answer is kept in the settings. */
@Composable
internal fun UpdateQuestionDialog(
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(text = stringResource(Res.string.update_question_title)) },
        text = { Text(text = stringResource(Res.string.update_question_text)) },
        confirmButton = { TextButton(onClick = onYes) { Text(text = stringResource(Res.string.update_question_yes)) } },
        dismissButton = { TextButton(onClick = onNo) { Text(text = stringResource(Res.string.update_question_no)) } },
    )
}

/**
 * Tells that a signed newer version is ready and installs it only when the user says so. It is shown while no vault
 * is open, so it never interrupts work, and installing closes the app.
 */
@Composable
internal fun UpdateOfferDialog(
    phase: UpdatePhase,
    onInstall: (UpdateCheck.Available) -> Unit,
    onLater: () -> Unit,
) {
    val offered = phase as? UpdatePhase.Offered
    val downloading = phase is UpdatePhase.Downloading
    if (offered == null && !downloading) return
    val offer = offered?.offer
    AlertDialog(
        onDismissRequest = { if (!downloading) onLater() },
        title = { Text(text = stringResource(Res.string.update_offer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DialogGap)) {
                when {
                    downloading -> {
                        CircularProgressIndicator()
                        Text(text = stringResource(Res.string.update_offer_downloading))
                    }
                    offer != null -> {
                        val version = offer.manifest.version
                        val body = stringResource(Res.string.update_offer_text, version, offer.manifest.released)
                        if (offered.failed) Text(text = stringResource(Res.string.update_offer_failed))
                        Text(text = body)
                    }
                }
            }
        },
        confirmButton = {
            if (offer != null) {
                TextButton(onClick = { onInstall(offer) }) {
                    Text(text = stringResource(Res.string.update_offer_install))
                }
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = onLater) { Text(text = stringResource(Res.string.update_offer_later)) }
            }
        },
    )
}
