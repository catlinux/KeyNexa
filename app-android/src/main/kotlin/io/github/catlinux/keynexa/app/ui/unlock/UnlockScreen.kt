@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.app.ui.unlock

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.ui.common.BrandMark
import io.github.catlinux.keynexa.app.ui.common.HexBadge
import io.github.catlinux.keynexa.app.vault.KnownVault
import io.github.catlinux.keynexa.app.vault.SessionState
import io.github.catlinux.keynexa.app.vault.UnlockError
import io.github.catlinux.keynexa.vault.Credentials
import java.io.IOException

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ScreenPadding = 24.dp
private val ItemGap = 8.dp
private val BlockGap = 18.dp
private val PanelPadding = 12.dp
private val TouchTarget = 48.dp
private val PrimaryHeight = 56.dp
private val SecondaryHeight = 52.dp
private val ProgressSize = 40.dp
private val MarkSize = 96.dp
private val BadgeSize = 40.dp
private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(14.dp)

/** A key file can be any file the user can reach. */
private val KeyFileTypes = arrayOf("*/*")

/** Overwrites a password array that is no longer needed. */
private val PASSWORD_FILL = Char(0)

/** What the user typed or picked on this screen. Secrets live here only while the screen is on. */
private class UnlockForm {
    var password by mutableStateOf("")
    var revealed by mutableStateOf(false)
    var keyFileName by mutableStateOf<String?>(null)
    var keyFileBytes by mutableStateOf<ByteArray?>(null)
    var keyFileFailed by mutableStateOf(false)

    /** A vault needs a password, a key file or both. */
    val ready: Boolean get() = password.isNotEmpty() || keyFileBytes != null

    fun pickKeyFile(
        name: String,
        bytes: ByteArray,
    ) {
        keyFileName = name
        keyFileBytes = bytes
        keyFileFailed = false
    }

    fun markKeyFileFailed() {
        keyFileFailed = true
    }

    fun clearKeyFile() {
        keyFileBytes?.fill(KEY_FILE_FILL)
        keyFileName = null
        keyFileBytes = null
        keyFileFailed = false
    }
}

/** Byte a cleared key file is filled with. */
private val KEY_FILE_FILL = 0.toByte()

/**
 * Opens one vault: the master password, an optional key file, the loading state of the key derivation (Argon2 can
 * take seconds), and a plain-language message when the file cannot be opened.
 *
 * A stateless screen: it builds the [Credentials] and hands them, with their ownership, to [onUnlock]. Passwords
 * and key file bytes are never logged, and no secret goes into state that outlives the screen. The layout follows
 * design direction A: the brand on top and the vault, the form and the actions in the lower half.
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
fun UnlockScreen(
    vault: KnownVault,
    state: SessionState,
    onUnlock: (Credentials) -> Unit,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    biometric: BiometricUi = BiometricUi(),
) {
    val form = remember { UnlockForm() }
    LaunchedEffect(vault.location) {
        if (biometric.available) biometric.onUnlock()
    }
    DisposableEffect(Unit) {
        onDispose { form.clearKeyFile() }
    }
    val loading = state is SessionState.Unlocking
    val failure = (state as? SessionState.Failed)?.error
    val enabled = form.ready && !loading
    val submit: () -> Unit = {
        if (enabled) {
            val typed = form.password.toCharArray()
            val password = if (typed.isEmpty()) null else typed.copyOf()
            onUnlock(Credentials.of(password = password, keyFile = form.keyFileBytes?.copyOf()))
            typed.fill(PASSWORD_FILL)
            form.password = ""
        }
    }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandHeader()
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(BlockGap)) {
                    VaultCard(vault = vault, onBack = onBack)
                    if (loading) ProgressPanel()
                    PasswordField(form = form, enabled = !loading, onSubmit = submit)
                    if (biometric.notice != null) NoticePanel(text = stringResource(biometric.notice))
                    if (failure != null) ErrorPanel(error = failure, onReconnect = onReconnect)
                    KeyFilePicker(form = form, enabled = !loading)
                    Button(
                        onClick = submit,
                        enabled = enabled,
                        shape = FieldShape,
                        modifier = Modifier.fillMaxWidth().height(PrimaryHeight),
                    ) {
                        Text(text = stringResource(R.string.unlock_action))
                    }
                    BiometricButtons(biometric = biometric, loading = loading)
                }
            }
        }
    }
}

/** The brand at the top: the mark and the name. */
@Composable
private fun BrandHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandMark(size = MarkSize)
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** The vault being opened: its avatar, its name, the file when it is known, and the way to choose another. */
@Composable
private fun VaultCard(
    vault: KnownVault,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fileName = remember(vault.location) { fileNameOf(vault) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
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
                if (fileName != null) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.unlock_change))
            }
        }
    }
}

/** The initial shown on the vault's hexagon avatar. */
private fun initialOf(name: String): String {
    val trimmed = name.trim()
    return if (trimmed.isEmpty()) "?" else trimmed.take(1).uppercase()
}

/** The file part of the vault location, when it looks like a file name. */
private fun fileNameOf(vault: KnownVault): String? {
    val candidate = vault.location.value.substringAfterLast('/')
    return candidate.takeIf { it.isNotBlank() && it != vault.location.value && '.' in it }
}

/** The master password, with the button that reveals it, and the keyboard action that submits the form. */
@Composable
private fun PasswordField(
    form: UnlockForm,
    enabled: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.unlock_show_password)
    OutlinedTextField(
        value = form.password,
        onValueChange = { form.password = it },
        label = { Text(text = stringResource(R.string.unlock_password_label)) },
        singleLine = true,
        enabled = enabled,
        shape = FieldShape,
        visualTransformation = if (form.revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        trailingIcon = {
            TextButton(
                onClick = { form.revealed = !form.revealed },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Text(
                    text =
                        stringResource(
                            if (form.revealed) R.string.entry_detail_hide else R.string.entry_detail_show,
                        ),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Unlock with biometrics, and the way to stop using it for this vault. */
@Composable
private fun BiometricButtons(
    biometric: BiometricUi,
    loading: Boolean,
) {
    if (biometric.available) {
        OutlinedButton(
            onClick = biometric.onUnlock,
            enabled = !loading,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth().height(SecondaryHeight),
        ) {
            Icon(
                imageVector = Icons.Filled.Face,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = stringResource(R.string.biometric_unlock_action))
        }
    }
    if (biometric.enrolled) {
        TextButton(
            onClick = biometric.onForget,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        ) {
            Text(text = stringResource(R.string.biometric_forget))
        }
    }
}

/** What the unlock screen needs for biometric unlock; the defaults show nothing. */
class BiometricUi(
    /** The button is offered and the system prompt appears when the screen opens. */
    val available: Boolean = false,
    /** Biometric unlock is enabled for this vault, even if the password is due. */
    val enrolled: Boolean = false,
    /** Why the password is needed instead, as a string resource. */
    val notice: Int? = null,
    val onUnlock: () -> Unit = {},
    val onForget: () -> Unit = {},
)

/** An explanation that is not an error. */
@Composable
private fun NoticePanel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = CardShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(PanelPadding),
        )
    }
}

/** What the screen shows while the vault opens; key derivation can take seconds. */
@Composable
private fun ProgressPanel(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.unlock_opening)
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = PanelPadding),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier =
                Modifier
                    .size(ProgressSize)
                    .semantics { contentDescription = description },
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** What went wrong, in plain language, and the next step when there is one. */
@Composable
private fun ErrorPanel(
    error: UnlockError,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive },
        shape = CardShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(PanelPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = stringResource(errorMessage(error)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (error == UnlockError.Unreachable) {
                TextButton(
                    onClick = onReconnect,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.heightIn(min = TouchTarget),
                ) {
                    Text(text = stringResource(R.string.unlock_reconnect))
                }
            }
        }
    }
}

/** The plain-language message of each way an unlock can fail. */
private fun errorMessage(error: UnlockError): Int =
    when (error) {
        UnlockError.WrongCredentials -> R.string.unlock_error_wrong_credentials
        UnlockError.InvalidKeyFile -> R.string.unlock_error_invalid_key_file
        UnlockError.NotAVault -> R.string.unlock_error_not_a_vault
        UnlockError.UnsupportedFormat -> R.string.unlock_error_unsupported_format
        UnlockError.Damaged -> R.string.unlock_error_damaged
        UnlockError.Unreachable -> R.string.unlock_error_unreachable
        UnlockError.NotEnoughMemory -> R.string.unlock_error_not_enough_memory
    }

/**
 * The optional key file: choose one, see its name, or drop it again. The picker reads the file only when it is
 * chosen; the bytes are never logged, and a file that cannot be read is reported instead of being ignored.
 */
@Composable
private fun KeyFilePicker(
    form: UnlockForm,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chooser =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val bytes = readKeyFile(context, uri)
                if (bytes == null) {
                    form.markKeyFileFailed()
                } else {
                    form.pickKeyFile(uri.lastPathSegment ?: uri.toString(), bytes)
                }
            }
        }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Text(
            text = stringResource(R.string.unlock_key_file_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (form.keyFileName == null) {
            OutlinedButton(
                onClick = { chooser.launch(KeyFileTypes) },
                enabled = enabled,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            ) {
                Text(text = stringResource(R.string.unlock_key_file_choose))
            }
            if (form.keyFileFailed) {
                Text(
                    text = stringResource(R.string.unlock_key_file_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.unlock_key_file_chosen, form.keyFileName.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { form.clearKeyFile() }, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.unlock_key_file_remove),
                    )
                }
            }
        }
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
