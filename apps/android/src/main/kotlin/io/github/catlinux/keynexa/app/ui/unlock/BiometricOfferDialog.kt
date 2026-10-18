package io.github.catlinux.keynexa.app.ui.unlock

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.catlinux.keynexa.app.R

/** Offered once after a password unlock on a device with biometrics: enable it, postpone it, or never ask again. */
@Composable
fun BiometricOfferDialog(
    onEnable: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(text = stringResource(R.string.biometric_offer_title)) },
        text = { Text(text = stringResource(R.string.biometric_offer_message)) },
        confirmButton = {
            Column {
                TextButton(onClick = onEnable) { Text(text = stringResource(R.string.biometric_offer_enable)) }
                TextButton(onClick = onLater) { Text(text = stringResource(R.string.biometric_offer_later)) }
                TextButton(onClick = onNever) { Text(text = stringResource(R.string.biometric_offer_never)) }
            }
        },
    )
}
