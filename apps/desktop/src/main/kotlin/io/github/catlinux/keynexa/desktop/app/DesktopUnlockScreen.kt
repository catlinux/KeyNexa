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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.app_name
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_hide
import io.github.catlinux.keynexa.desktop.app.generated.resources.entry_detail_show
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_action
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_back_to_list
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_choose
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_chosen
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_failed
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_key_file_remove
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_opening
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_password_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_show_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_use_keypad
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_use_system_keyboard
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.session.SessionState
import io.github.catlinux.keynexa.session.UnlockError
import io.github.catlinux.keynexa.ui.brand.BrandMark
import io.github.catlinux.keynexa.ui.brand.HexBadge
import io.github.catlinux.keynexa.ui.keypad.PasswordBuffer
import io.github.catlinux.keynexa.ui.keypad.PasswordKeypad
import io.github.catlinux.keynexa.vault.Credentials
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Spacing and shapes of the design grid (docs/DESIGN.md), as on Android. */
private val ScreenPadding = 20.dp
private val BlockGap = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp
private val FieldShape = RoundedCornerShape(14.dp)
private val CardShape = RoundedCornerShape(16.dp)
private val MarkSize = 96.dp
private val BadgeSize = 40.dp
private val PrimaryHeight = 54.dp
private val SecondaryHeight = 52.dp
private val ProgressSize = 36.dp
private val ContentWidth = 480.dp
private const val CARD_BORDER_ALPHA = 0.4f

/** The mark that stands for every hidden character. */
private const val MASK = "•"

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
    modifier: Modifier = Modifier,
    onEdited: () -> Unit = {},
) {
    val password = remember { DesktopPassword() }
    val keyFile = remember { KeyFileHolder() }
    DisposableEffect(Unit) {
        onDispose {
            password.clear()
            keyFile.clear()
        }
    }
    val loading = state is SessionState.Unlocking
    val failure = (state as? SessionState.Failed)?.error
    val enabled = (password.length > 0 || keyFile.chosen) && !loading
    val submit: () -> Unit = {
        if (enabled) {
            onUnlock(Credentials.of(password = password.take(), keyFile = keyFile.copyOut()))
        }
    }
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
                PasswordField(password = password, enabled = !loading, onSubmit = submit, onEdited = onEdited)
                PasswordInputSwitch(password = password, enabled = !loading)
                if (password.useKeypad && !loading) {
                    PasswordKeypad(
                        state = password.keypad,
                        buffer = password.buffer,
                        enabled = true,
                        onChanged = { password.keypadChanged() },
                        onDone = submit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (failure != null) ErrorPanel(error = failure)
                KeyFilePicker(parent = parent, holder = keyFile, enabled = !loading, onEdited = onEdited)
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

/** The master password, with the button that shows it and the Enter key that unlocks. */
@Composable
private fun PasswordField(
    password: DesktopPassword,
    enabled: Boolean,
    onSubmit: () -> Unit,
    onEdited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.unlock_password_label)
    val description = stringResource(Res.string.unlock_show_password)
    if (password.useKeypad) {
        OutlinedTextField(
            value = if (password.revealed) revealedText(password.buffer) else MASK.repeat(password.bufferLength),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            singleLine = true,
            enabled = enabled,
            shape = FieldShape,
            trailingIcon = { RevealButton(password = password, enabled = enabled, description = description) },
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            value = password.text,
            onValueChange = {
                password.text = it
                onEdited()
            },
            label = { Text(text = label) },
            singleLine = true,
            enabled = enabled,
            shape = FieldShape,
            visualTransformation = if (password.revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailingIcon = { RevealButton(password = password, enabled = enabled, description = description) },
            modifier = modifier.fillMaxWidth().onPreviewKeyEvent { event -> submitOnEnter(event, onSubmit) },
        )
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

/** The text button that switches between the KeyNexa keypad and the system keyboard. */
@Composable
private fun PasswordInputSwitch(
    password: DesktopPassword,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = { password.switchInput() },
        enabled = enabled,
        modifier = modifier.heightIn(min = TouchTarget),
    ) {
        val text = if (password.useKeypad) Res.string.unlock_use_system_keyboard else Res.string.unlock_use_keypad
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

/**
 * The optional key file: choose one, see its name, or drop it again. The file is read only when it is chosen; the
 * bytes are never logged, and a file that cannot be read is reported instead of being ignored.
 */
@Composable
private fun KeyFilePicker(
    parent: Frame,
    holder: KeyFileHolder,
    enabled: Boolean,
    onEdited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chooseTitle = stringResource(Res.string.unlock_key_file_choose)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(
            text = stringResource(Res.string.unlock_key_file_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (holder.name == null) {
            OutlinedButton(
                onClick = {
                    chooseKeyFile(parent, chooseTitle)?.let { file ->
                        val bytes = readKeyFile(file)
                        if (bytes == null) holder.markFailed() else holder.pick(file.name, bytes)
                        onEdited()
                    }
                },
                enabled = enabled,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) {
                Text(text = chooseTitle)
            }
            if (holder.failed) {
                Text(
                    text = stringResource(Res.string.unlock_key_file_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.unlock_key_file_chosen, holder.name.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        holder.clear()
                        onEdited()
                    },
                    enabled = enabled,
                ) {
                    Text(text = stringResource(Res.string.unlock_key_file_remove))
                }
            }
        }
    }
}

/** Asks for a key file with the native dialog; null when the user cancels. */
private fun chooseKeyFile(
    parent: Frame,
    title: String,
): File? {
    val dialog = FileDialog(parent, title, FileDialog.LOAD)
    dialog.isVisible = true
    val directory = dialog.directory
    val name = dialog.file
    return if (directory != null && name != null) File(directory, name) else null
}

/** Runs [onSubmit] and eats the event when it is the Enter key, so the password field never keeps a line break. */
private fun submitOnEnter(
    event: KeyEvent,
    onSubmit: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) return false
    onSubmit()
    return true
}

/** Reads the whole key file the user picked; a file that cannot be read leaves the picker as it was. */
private fun readKeyFile(file: File): ByteArray? =
    try {
        Files.readAllBytes(file.toPath())
    } catch (failure: IOException) {
        null
    }

/** The typed characters as text, through a copy that is wiped at once. */
private fun revealedText(buffer: PasswordBuffer): String {
    val chars = buffer.copyOut()
    return try {
        String(chars)
    } finally {
        chars.fill(Char(0))
    }
}

/** The initial shown on the vault's hexagon avatar. */
private fun initialOf(name: String): String {
    val trimmed = name.trim()
    return if (trimmed.isEmpty()) "?" else trimmed.take(1).uppercase()
}
