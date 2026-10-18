package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_password_min
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_password_mismatch
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_password_ok
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_password_weak
import org.jetbrains.compose.resources.stringResource

/** A password shorter than this is not accepted where one is required. */
internal const val MIN_PASSWORD_LENGTH = 8

/** From this length on the warning goes away; a longer password is never blocked. */
internal const val GOOD_PASSWORD_LENGTH = 12

private val StrengthBarHeight = 6.dp
private val StrengthGap = 6.dp

/**
 * How long a password is: a bar that grows with it and a word that says what that means. A password below the
 * minimum is blocked by the caller; a merely weak one only warns, as the design system asks.
 */
@Composable
internal fun PasswordStrength(
    length: Int,
    mismatch: Boolean,
    modifier: Modifier = Modifier,
) {
    val acceptable = length >= GOOD_PASSWORD_LENGTH && !mismatch
    val filled = (length.toFloat() / GOOD_PASSWORD_LENGTH).coerceIn(0f, 1f)
    val color = if (acceptable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val message =
        when {
            length < MIN_PASSWORD_LENGTH -> stringResource(Res.string.create_password_min, MIN_PASSWORD_LENGTH)
            mismatch -> stringResource(Res.string.create_password_mismatch)
            length < GOOD_PASSWORD_LENGTH -> stringResource(Res.string.create_password_weak, GOOD_PASSWORD_LENGTH)
            else -> stringResource(Res.string.create_password_ok)
        }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StrengthGap),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(StrengthBarHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(filled)
                        .height(StrengthBarHeight)
                        .background(color),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (acceptable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}
