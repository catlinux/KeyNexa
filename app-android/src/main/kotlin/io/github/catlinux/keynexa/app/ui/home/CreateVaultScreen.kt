package io.github.catlinux.keynexa.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.vault.Credentials
import java.io.IOException

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ScreenPadding = 16.dp
private val BlockGap = 24.dp
private val ItemGap = 8.dp
private val TouchTarget = 48.dp
private val StrengthBarHeight = 6.dp

/** A master password shorter than this cannot create a vault. */
private const val MIN_PASSWORD_LENGTH = 8

/** From this length on the warning goes away; a longer password is never blocked. */
private const val GOOD_PASSWORD_LENGTH = 12

/** A key file can be any file the user can reach. */
private val KeyFileTypes = arrayOf("*/*")

/** What the user typed into the password fields. Secrets live here only while the screen is on. */
private class PasswordFields {
    var password by mutableStateOf("")
    var repeated by mutableStateOf("")
    var revealed by mutableStateOf(false)

    /** True once the confirmation is filled in and does not match. */
    val mismatch: Boolean get() = repeated.isNotEmpty() && repeated != password

    /** True when both fields hold the same password, which is required to create the vault. */
    val confirmed: Boolean get() = password.isNotEmpty() && repeated == password

    fun clear() {
        password = ""
        repeated = ""
        revealed = false
    }
}

/** The key file the user picked, if any, and whether the last attempt to read one failed. */
private class KeyFileChoice {
    var name by mutableStateOf<String?>(null)
    var bytes by mutableStateOf<ByteArray?>(null)
    var failed by mutableStateOf(false)

    fun picked(
        pickedName: String,
        pickedBytes: ByteArray,
    ) {
        name = pickedName
        bytes = pickedBytes
        failed = false
    }

    fun markFailed() {
        failed = true
    }

    fun clear() {
        name = null
        bytes = null
        failed = false
    }
}

/**
 * The form for a new vault: a name, the master password twice, an optional key file, and the reminder that a lost
 * master password cannot be recovered.
 *
 * It is a stateless screen: it builds the [Credentials] and hands them, with their ownership, to [onCreate]. Nothing
 * secret goes to saved state, a log or a copy, and the fields are emptied on the way out.
 */
@Composable
fun CreateVaultScreen(
    onCreate: (name: String, credentials: Credentials) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val passwordFields = remember { PasswordFields() }
    val keyFile = remember { KeyFileChoice() }
    var name by remember { mutableStateOf("") }
    val canCreate =
        name.isNotBlank() && passwordFields.password.length >= MIN_PASSWORD_LENGTH && passwordFields.confirmed
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            CreateHeader(onBack = onBack)
            VaultNameField(value = name, onValueChange = { name = it })
            PasswordSection(passwordFields = passwordFields)
            KeyFilePicker(choice = keyFile)
            RecoveryWarning()
            Spacer(modifier = Modifier.height(BlockGap))
            Button(
                onClick = {
                    // Credentials take ownership of the array: from here the callback wipes it, not this screen.
                    val secret = passwordFields.password.toCharArray()
                    onCreate(name.trim(), Credentials.of(password = secret, keyFile = keyFile.bytes))
                    name = ""
                    passwordFields.clear()
                    keyFile.clear()
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) {
                Text(text = stringResource(R.string.create_action))
            }
        }
    }
}

/** The title and the way back. */
@Composable
private fun CreateHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.create_back),
            )
        }
        Text(
            text = stringResource(R.string.create_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = ItemGap),
        )
    }
}

@Composable
private fun VaultNameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(R.string.create_name_label)) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** The master password, the confirmation and the switch that shows or hides them. */
@Composable
private fun PasswordSection(
    passwordFields: PasswordFields,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        PasswordField(
            value = passwordFields.password,
            label = stringResource(R.string.create_password_label),
            revealed = passwordFields.revealed,
            onValueChange = { passwordFields.password = it },
        )
        PasswordField(
            value = passwordFields.repeated,
            label = stringResource(R.string.create_repeat_label),
            revealed = passwordFields.revealed,
            onValueChange = { passwordFields.repeated = it },
        )
        RevealSwitch(
            revealed = passwordFields.revealed,
            onRevealedChange = { passwordFields.revealed = it },
        )
        PasswordStrength(length = passwordFields.password.length, mismatch = passwordFields.mismatch)
    }
}

/** A password field: hidden by default, without autocorrect, and never kept beyond this screen. */
@Composable
private fun PasswordField(
    value: String,
    label: String,
    revealed: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Shows or hides both password fields; the switch itself carries the description of what it does. */
@Composable
private fun RevealSwitch(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.create_show_password)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = revealed,
            onCheckedChange = onRevealedChange,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

/**
 * How long the master password is: a bar that grows with it and a word that says what that means. A password below
 * the minimum blocks the Create button; a merely weak one only warns, as the design system asks.
 */
@Composable
private fun PasswordStrength(
    length: Int,
    mismatch: Boolean,
    modifier: Modifier = Modifier,
) {
    val acceptable = length >= GOOD_PASSWORD_LENGTH && !mismatch
    val filled = (length.toFloat() / GOOD_PASSWORD_LENGTH).coerceIn(0f, 1f)
    val color = if (acceptable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    val message =
        when {
            length < MIN_PASSWORD_LENGTH -> stringResource(R.string.create_password_min, MIN_PASSWORD_LENGTH)
            mismatch -> stringResource(R.string.create_password_mismatch)
            length < GOOD_PASSWORD_LENGTH -> stringResource(R.string.create_password_weak, GOOD_PASSWORD_LENGTH)
            else -> stringResource(R.string.create_password_ok)
        }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
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
            style = MaterialTheme.typography.bodyMedium,
            color = if (acceptable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * The optional key file: choose one, see its name, or drop it again. The picker reads the file only when it is
 * chosen; the bytes are never logged and never kept after the vault is created, and a file that cannot be read is
 * reported instead of being ignored.
 */
@Composable
private fun KeyFilePicker(
    choice: KeyFileChoice,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chooser =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val bytes = readKeyFile(context, uri)
                if (bytes == null) {
                    choice.markFailed()
                } else {
                    choice.picked(uri.lastPathSegment ?: uri.toString(), bytes)
                }
            }
        }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(
            text = stringResource(R.string.create_key_file_label),
            style = MaterialTheme.typography.titleMedium,
        )
        if (choice.name == null) {
            OutlinedButton(
                onClick = { chooser.launch(KeyFileTypes) },
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) {
                Text(text = stringResource(R.string.create_key_file_choose))
            }
            if (choice.failed) {
                Text(
                    text = stringResource(R.string.create_key_file_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.create_key_file_chosen, choice.name.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { choice.clear() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.create_key_file_remove),
                    )
                }
            }
        }
    }
}

/** The warning that must never be missed: what is lost when the master password is lost. */
@Composable
private fun RecoveryWarning(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = stringResource(R.string.create_recovery_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(ScreenPadding),
        )
    }
}

/** Reads the whole key file the user picked; a file that cannot be read leaves the picker as it was. */
private fun readKeyFile(
    context: Context,
    uri: Uri,
): ByteArray? =
    try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (failure: IOException) {
        null
    }
