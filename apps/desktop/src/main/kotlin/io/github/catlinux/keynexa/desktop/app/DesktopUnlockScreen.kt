@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.app_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_hide
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_show
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_action
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_back_to_list
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_opening
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_password_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_show_password
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.session.UnlockError
import io.github.catlinux.keynexa.ui.brand.BrandMark
import io.github.catlinux.keynexa.ui.brand.HexBadge
import io.github.catlinux.keynexa.ui.keypad.PasswordKeypad
import io.github.catlinux.keynexa.vault.Credentials
import org.jetbrains.compose.resources.stringResource
import java.awt.Frame
import java.io.File

/** Spacing and shapes of the design grid (docs/DESIGN.md), as on Android. */
private val ScreenPadding = 20.dp
private val BlockGap = 16.dp
private val ItemGap = 8.dp
private val FieldShape = RoundedCornerShape(14.dp)
private val CardShape = RoundedCornerShape(16.dp)
private val MarkSize = 96.dp
private val BadgeSize = 40.dp
private val PrimaryHeight = 54.dp
private val SecondaryHeight = 52.dp
private val ProgressSize = 36.dp
private val ContentWidth = 480.dp
private const val CARD_BORDER_ALPHA = 0.4f

/**
 * Opens one vault: the master password, an optional key file, the loading state of the key derivation and a
 * plain-language message when the file cannot be opened.
 *
 * A stateless screen: it builds the [Credentials] and hands them, with their ownership, to [onUnlock]. The password
 * and the key file are never logged, and both copies the screen owns are wiped when it leaves. The KeyNexa keypad
 * is available but off by default on the desktop.
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
fun DesktopUnlockScreen(
    parent: Frame,
    vault: KnownVault,
    state: SessionState,
    onUnlock: (Credentials) -> Unit,
    onBack: () -> Unit,
    useKeypad: Boolean = false,
    modifier: Modifier = Modifier,
    onEdited: () -> Unit = {},
) {
    val password = remember { DesktopPassword(useKeypad = useKeypad) }
    val keyFile = remember { KeyFileHolder() }
    DisposableEffect(Unit) {
        onDispose {
            password.clear()
            keyFile.clear()
        }
    }
    val loading = state is SessionState.Unlocking
    val failure = (state as? SessionState.Failed)?.error
    val enabled = (password.length() > 0 || keyFile.chosen) && !loading
    val submit: () -> Unit = {
        if (enabled) {
            onUnlock(Credentials.of(password = password.take(), keyFile = keyFile.copyOut()))
        }
    }
    val revealDescription = stringResource(Res.string.unlock_show_password)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = ContentWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding),
        ) {
            UnlockHeader()
            Column(verticalArrangement = Arrangement.spacedBy(BlockGap)) {
                VaultCard(vault = vault)
                if (loading) ProgressPanel()
                DesktopPasswordField(
                    password = password,
                    label = stringResource(Res.string.unlock_password_label),
                    enabled = !loading,
                    shape = FieldShape,
                    onSubmit = submit,
                    trailingIcon = {
                        RevealButton(password = password, enabled = !loading, description = revealDescription)
                    },
                    onChanged = onEdited,
                )
                PasswordInputSwitch(password = password, enabled = !loading)
                if (password.useKeypad && !loading) {
                    PasswordKeypad(
                        state = password.keypad,
                        buffer = password.buffer(),
                        enabled = true,
                        onChanged = { password.keypadChanged() },
                        onDone = submit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (failure != null) ErrorPanel(error = failure)
                DesktopKeyFilePicker(
                    parent = parent,
                    holder = keyFile,
                    enabled = !loading,
                    shape = FieldShape,
                    onEdited = onEdited,
                )
                Button(
                    onClick = submit,
                    enabled = enabled,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PrimaryHeight),
                ) {
                    Text(text = stringResource(Res.string.unlock_action))
                }
                OutlinedButton(
                    onClick = onBack,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = SecondaryHeight),
                ) {
                    Text(text = stringResource(Res.string.unlock_back_to_list))
                }
            }
        }
    }
}

/** The brand at the top: the mark and the name. */
@Composable
private fun UnlockHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = BlockGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BlockGap),
    ) {
        BrandMark(size = MarkSize)
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** The vault being opened: its avatar, its name and its file. */
@Composable
private fun VaultCard(
    vault: KnownVault,
    modifier: Modifier = Modifier,
) {
    val fileName = File(vault.location.value).name
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HexBadge(
                text = initialOf(vault.name),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                size = BadgeSize,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = vault.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fileName.isNotEmpty()) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Shows or hides the typed master password; the button itself says what it does. */
@Composable
private fun RevealButton(
    password: DesktopPassword,
    enabled: Boolean,
    description: String,
) {
    TextButton(
        onClick = { password.revealed = !password.revealed },
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        val text = if (password.revealed) Res.string.entry_detail_hide else Res.string.entry_detail_show
        Text(text = stringResource(text))
    }
}

/** What the screen shows while the vault opens; key derivation can take seconds. */
@Composable
private fun ProgressPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(ProgressSize))
        Text(
            text = stringResource(Res.string.unlock_opening),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** What went wrong, in plain language. */
@Composable
private fun ErrorPanel(
    error: UnlockError,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = stringResource(unlockErrorMessage(error)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}
