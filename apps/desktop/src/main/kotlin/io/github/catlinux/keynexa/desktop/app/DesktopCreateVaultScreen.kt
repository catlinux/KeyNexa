@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_action
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_file_exists
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_key_file_help
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_name_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_recovery_warning
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_repeat_label
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_show_password
import io.github.catlinux.keynexa.desktop.app.generated.resources.create_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_back_to_list
import io.github.catlinux.keynexa.desktop.app.generated.resources.unlock_password_label
import io.github.catlinux.keynexa.ui.keypad.PasswordKeypad
import io.github.catlinux.keynexa.vault.Credentials
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Spacing and shapes of the design grid (docs/DESIGN.md), as on Android. */
private val ScreenPadding = 20.dp
private val BlockGap = 16.dp
private val ItemGap = 8.dp
private val NoticePadding = 12.dp
private val TouchTarget = 48.dp
private val ActionHeight = 54.dp
private val FieldShape = RoundedCornerShape(14.dp)
private val CardShape = RoundedCornerShape(16.dp)
private val ContentWidth = 480.dp

/** The new master password ([PASSWORD]) and its confirmation ([REPEATED]) are the two fields of the holder. */
private const val PASSWORD = 0
private const val REPEATED = 1

/** True once the confirmation is filled in and does not match. */
private fun DesktopPassword.mismatch(): Boolean = length(REPEATED) > 0 && !matches(PASSWORD, REPEATED)

/** True when both fields hold the same password, which is required to create the vault. */
private fun DesktopPassword.confirmed(): Boolean = length(PASSWORD) > 0 && matches(PASSWORD, REPEATED)

/** What happened when the user asked to create the vault. */
private enum class CreateAttempt { Started, FileExists, Cancelled }

/**
 * The form for a new vault: a name, the master password twice, an optional key file and the reminder that a lost
 * master password cannot be recovered.
 *
 * It is a stateless screen: it asks where the vault goes, builds the [Credentials] and hands them, with their
 * ownership, to [onCreate] along with the file the user chose. Nothing secret is logged, and the password fields and
 * the key file are wiped on the way out.
 */
@Composable
fun DesktopCreateVaultScreen(
    parent: Frame,
    onBack: () -> Unit,
    onCreate: (file: File, name: String, credentials: Credentials) -> Unit,
    modifier: Modifier = Modifier,
) {
    val password = remember { DesktopPassword(fields = 2) }
    val keyFile = remember { KeyFileHolder() }
    DisposableEffect(Unit) {
        onDispose {
            password.clear()
            keyFile.clear()
        }
    }
    var name by remember { mutableStateOf("") }
    var fileExists by remember { mutableStateOf(false) }
    val canCreate = name.isNotBlank() && password.length(PASSWORD) >= MIN_PASSWORD_LENGTH && password.confirmed()
    val saveTitle = stringResource(Res.string.create_action)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = ContentWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding, vertical = BlockGap),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            Text(
                text = stringResource(Res.string.create_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(),
            )
            CreateFields(
                name = name,
                onNameChange = {
                    name = it
                    fileExists = false
                },
                password = password,
                keyFile = keyFile,
                parent = parent,
            )
            if (fileExists) NoticeCard(text = stringResource(Res.string.create_file_exists))
            CreateActions(
                canCreate = canCreate,
                onCreate = {
                    when (startCreating(parent, saveTitle, name.trim(), password, keyFile, onCreate)) {
                        CreateAttempt.Started -> {
                            name = ""
                            fileExists = false
                            keyFile.clear()
                        }
                        CreateAttempt.FileExists -> fileExists = true
                        CreateAttempt.Cancelled -> Unit
                    }
                },
                onBack = onBack,
            )
        }
    }
}

/** The fields of the new vault: its name, the master password twice, the optional key file and the warning. */
@Composable
private fun CreateFields(
    name: String,
    onNameChange: (String) -> Unit,
    password: DesktopPassword,
    keyFile: KeyFileHolder,
    parent: Frame,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BlockGap),
    ) {
        NameField(value = name, onValueChange = onNameChange)
        PasswordSection(password = password)
        DesktopKeyFilePicker(
            parent = parent,
            holder = keyFile,
            enabled = true,
            shape = FieldShape,
            help = stringResource(Res.string.create_key_file_help),
        )
        NoticeCard(text = stringResource(Res.string.create_recovery_warning))
    }
}

/** The name of the new vault. */
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(Res.string.create_name_label)) },
        singleLine = true,
        shape = FieldShape,
        modifier = modifier.fillMaxWidth(),
    )
}

/** The master password, its confirmation and everything around them: showing them, their strength, the keypad. */
@Composable
private fun PasswordSection(
    password: DesktopPassword,
    modifier: Modifier = Modifier,
) {
    val repeatedFocus = remember { FocusRequester() }
    val revealLabel = stringResource(Res.string.create_show_password)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        DesktopPasswordField(
            password = password,
            label = stringResource(Res.string.unlock_password_label),
            enabled = true,
            shape = FieldShape,
            field = PASSWORD,
        )
        DesktopPasswordField(
            password = password,
            label = stringResource(Res.string.create_repeat_label),
            enabled = true,
            shape = FieldShape,
            field = REPEATED,
            modifier = Modifier.focusRequester(repeatedFocus),
        )
        RevealSwitch(
            revealed = password.revealed,
            onRevealedChange = { password.revealed = it },
            label = revealLabel,
        )
        PasswordStrength(length = password.length(PASSWORD), mismatch = password.mismatch())
        PasswordInputSwitch(password = password, enabled = true)
        // Done on the first field moves on to the confirmation, as the system keyboard's Next would.
        if (password.useKeypad) {
            PasswordKeypad(
                state = password.keypad,
                buffer = password.buffer(password.active),
                enabled = true,
                onChanged = { password.keypadChanged() },
                onDone = { if (password.active == PASSWORD) repeatedFocus.requestFocus() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Shows or hides both password fields; the switch itself carries the description of what it does. */
@Composable
private fun RevealSwitch(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = revealed,
            onCheckedChange = onRevealedChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** A card that says something the user must not miss, in the colour of a problem. */
@Composable
private fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(NoticePadding),
        )
    }
}

/** The Create button and the way back to the list of vaults. */
@Composable
private fun CreateActions(
    canCreate: Boolean,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Button(
            onClick = onCreate,
            enabled = canCreate,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight),
        ) {
            Text(text = stringResource(Res.string.create_action))
        }
        OutlinedButton(
            onClick = onBack,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(Res.string.unlock_back_to_list))
        }
    }
}

/**
 * Asks where the new vault goes and starts creating it. The credentials take ownership of the password and of a copy
 * of the key file; when the user cancels or the file is already there, nothing is written and nothing is taken.
 */
@Suppress("LongParameterList")
private fun startCreating(
    parent: Frame,
    title: String,
    name: String,
    password: DesktopPassword,
    keyFile: KeyFileHolder,
    onCreate: (file: File, name: String, credentials: Credentials) -> Unit,
): CreateAttempt {
    val file = chooseNewVaultFile(parent, title, vaultFileName(name)) ?: return CreateAttempt.Cancelled
    if (vaultFileExists(file)) return CreateAttempt.FileExists
    val secret = password.take(PASSWORD)
    onCreate(file, name, Credentials.of(password = secret, keyFile = keyFile.copyOut()))
    return CreateAttempt.Started
}

/** Asks where the new vault goes, with the file name the vault name suggests; null when the user cancels. */
private fun chooseNewVaultFile(
    parent: Frame,
    title: String,
    suggestedName: String,
): File? {
    val dialog = FileDialog(parent, title, FileDialog.SAVE)
    dialog.setFilenameFilter { _, name -> name.endsWith(VAULT_EXTENSION, ignoreCase = true) }
    dialog.file = suggestedName
    dialog.isVisible = true
    val directory = dialog.directory
    val name = dialog.file
    return if (directory != null && name != null) File(directory, vaultFileName(name)) else null
}
