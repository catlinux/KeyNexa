package io.github.catlinux.keynexa.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.settings.SettingsValues
import io.github.catlinux.keynexa.app.vault.CloudBackupSource

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ScreenPadding = 16.dp
private val ItemGap = 8.dp
private val BlockGap = 16.dp
private val PanelPadding = 12.dp
private val TouchTarget = 48.dp

private const val SECONDS_PER_MINUTE = 60

/** The one clipboard choice worth saying in minutes. */
private const val CLIPBOARD_TWO_MINUTES = 120

private const val TWO_MINUTES = 2

/**
 * The protection settings: when the vault locks itself, and what the app does with the screen and the clipboard.
 *
 * A stateless screen: it shows [values] and reports every change through [onChange], and it stores nothing itself.
 * Every switch and every option is a row of at least 48 dp whose label is its accessible text.
 */
@Suppress("LongMethod")
@Composable
fun SettingsScreen(
    values: SettingsValues,
    onChange: (SettingsValues) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    cloudSource: CloudBackupSource? = null,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            SettingsHeader(onBack = onBack)
            SectionTitle(text = stringResource(R.string.settings_section_lock))
            Text(text = stringResource(R.string.settings_auto_lock), style = MaterialTheme.typography.bodyLarge)
            SettingsValues.AUTO_LOCK_CHOICES.forEach { seconds ->
                OptionRow(
                    label = autoLockLabel(seconds),
                    selected = values.autoLockSeconds == seconds,
                    onSelect = { onChange(values.copy(autoLockSeconds = seconds)) },
                )
            }
            if (values.autoLockSeconds == SettingsValues.NEVER_LOCK) NeverLockWarning()
            SwitchSetting(
                label = stringResource(R.string.settings_lock_on_screen_off),
                checked = values.lockOnScreenOff,
                onCheckedChange = { onChange(values.copy(lockOnScreenOff = it)) },
            )
            SectionTitle(text = stringResource(R.string.settings_section_privacy))
            SwitchSetting(
                label = stringResource(R.string.settings_allow_screenshots),
                checked = values.allowScreenshots,
                onCheckedChange = { onChange(values.copy(allowScreenshots = it)) },
                description = stringResource(R.string.settings_allow_screenshots_help),
            )
            Text(text = stringResource(R.string.settings_clipboard_delay), style = MaterialTheme.typography.bodyLarge)
            SettingsValues.CLIPBOARD_CHOICES.forEach { seconds ->
                OptionRow(
                    label = clipboardLabel(seconds),
                    selected = values.clipboardSeconds == seconds,
                    onSelect = { onChange(values.copy(clipboardSeconds = seconds)) },
                )
            }
            Text(
                text = stringResource(R.string.settings_clipboard_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BiometricSettings(values = values, onChange = onChange)
            SectionTitle(text = stringResource(R.string.settings_section_sync))
            Text(text = stringResource(R.string.settings_sync_warn), style = MaterialTheme.typography.bodyLarge)
            SettingsValues.SYNC_WARN_CHOICES.forEach { days ->
                OptionRow(
                    label =
                        if (days == 0) {
                            stringResource(R.string.settings_never)
                        } else {
                            pluralStringResource(R.plurals.settings_days, days, days)
                        },
                    selected = values.syncWarnDays == days,
                    onSelect = { onChange(values.copy(syncWarnDays = days)) },
                )
            }
            Text(
                text = stringResource(R.string.settings_sync_warn_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CloudSettingsEntry(cloudSource)
        }
    }
}

/** The title of the screen and the way back. */
@Composable
private fun SettingsHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
            )
        }
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.fillMaxWidth().padding(top = BlockGap),
    )
}

/** One exclusive option: the whole row is tappable and at least 48 dp tall. */
@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .toggleable(value = selected, role = Role.RadioButton, onValueChange = { onSelect() })
                .padding(vertical = ItemGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One setting that is on or off; its label is what a screen reader reads. */
@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = ItemGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** How long biometric unlock may go without the master password. */
@Composable
private fun BiometricSettings(
    values: SettingsValues,
    onChange: (SettingsValues) -> Unit,
) {
    SectionTitle(text = stringResource(R.string.settings_section_biometric))
    Text(text = stringResource(R.string.settings_biometric_days), style = MaterialTheme.typography.bodyLarge)
    SettingsValues.BIOMETRIC_DAYS_CHOICES.forEach { days ->
        OptionRow(
            label = pluralStringResource(R.plurals.settings_days, days, days),
            selected = values.biometricPasswordDays == days,
            onSelect = { onChange(values.copy(biometricPasswordDays = days)) },
        )
    }
    Text(
        text = stringResource(R.string.settings_biometric_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The word the auto-lock delay is shown with: seconds, minutes, "immediately" or "never". */
@Composable
private fun autoLockLabel(seconds: Int): String =
    when {
        seconds == SettingsValues.LOCK_IMMEDIATELY -> stringResource(R.string.settings_immediately)
        seconds == SettingsValues.NEVER_LOCK -> stringResource(R.string.settings_never)
        seconds % SECONDS_PER_MINUTE == 0 -> {
            val minutes = seconds / SECONDS_PER_MINUTE
            pluralStringResource(R.plurals.settings_minutes, minutes, minutes)
        }
        else -> pluralStringResource(R.plurals.settings_seconds, seconds, seconds)
    }

/** The word the clipboard delay is shown with; only the longest choice is worth saying in minutes. */
@Composable
private fun clipboardLabel(seconds: Int): String =
    if (seconds == CLIPBOARD_TWO_MINUTES) {
        pluralStringResource(R.plurals.settings_minutes, TWO_MINUTES, TWO_MINUTES)
    } else {
        pluralStringResource(R.plurals.settings_seconds, seconds, seconds)
    }

/** Says out loud what leaving the vault open indefinitely means. */
@Composable
private fun NeverLockWarning(modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(PanelPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.settings_no_lock_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
