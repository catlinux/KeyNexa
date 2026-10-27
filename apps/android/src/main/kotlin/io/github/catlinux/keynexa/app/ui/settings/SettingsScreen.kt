@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.app.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.app.R
import io.github.catlinux.keynexa.app.settings.SettingsValues
import io.github.catlinux.keynexa.app.settings.ThemeMode
import io.github.catlinux.keynexa.session.CloudBackupSource
import io.github.catlinux.keynexa.sync.SyncLocal
import io.github.catlinux.keynexa.ui.theme.LocalDarkTheme

/** Spacing and touch targets of the design grid (docs/DESIGN.md). */
private val ScreenPadding = 20.dp
private val ItemGap = 8.dp
private val PanelPadding = 12.dp
private val TouchTarget = 48.dp

/** The band at the top, as on the group screen. */
private val BandCorner = 24.dp
private val BandPaddingStart = 12.dp
private val BandPaddingEnd = 20.dp
private val BandPaddingTop = 20.dp
private val BandPaddingBottom = 24.dp

/** One card per section, with a title in capitals and rows of the same height. */
private val CardGap = 12.dp
private val CardCorner = 16.dp
private val CardBorderWidth = 1.dp
private const val CARD_BORDER_ALPHA = 0.4f
private const val DIVIDER_ALPHA = 0.2f
private val CardPaddingHorizontal = 16.dp
private val CardTitlePaddingTop = 14.dp
private val CardTitlePaddingBottom = 4.dp
private val RowPaddingVertical = 12.dp
private val LabelPaddingTop = 12.dp
private val LabelPaddingBottom = 4.dp
private val NotePaddingBottom = 12.dp

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
@Suppress("LongMethod", "LongParameterList")
@Composable
fun SettingsScreen(
    values: SettingsValues,
    onChange: (SettingsValues) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    cloudSource: CloudBackupSource? = null,
    syncLocal: SyncLocal? = null,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(onBack = onBack)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = ScreenPadding, end = ScreenPadding, top = CardGap, bottom = CardGap),
                verticalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                SettingsCard(titleRes = R.string.settings_section_lock) {
                    SettingsLabel(textRes = R.string.settings_auto_lock)
                    SettingsValues.AUTO_LOCK_CHOICES.forEachIndexed { index, seconds ->
                        if (index > 0) SettingsDivider()
                        OptionRow(
                            label = autoLockLabel(seconds),
                            selected = values.autoLockSeconds == seconds,
                            onSelect = { onChange(values.copy(autoLockSeconds = seconds)) },
                        )
                    }
                    if (values.autoLockSeconds == SettingsValues.NEVER_LOCK) {
                        NeverLockWarning(
                            modifier =
                                Modifier.padding(
                                    start = CardPaddingHorizontal,
                                    end = CardPaddingHorizontal,
                                    top = RowPaddingVertical,
                                    bottom = RowPaddingVertical,
                                ),
                        )
                    }
                    SettingsDivider()
                    SwitchSetting(
                        label = stringResource(R.string.settings_lock_on_screen_off),
                        checked = values.lockOnScreenOff,
                        onCheckedChange = { onChange(values.copy(lockOnScreenOff = it)) },
                    )
                }
                SettingsCard(titleRes = R.string.settings_section_privacy) {
                    SwitchSetting(
                        label = stringResource(R.string.settings_allow_screenshots),
                        checked = values.allowScreenshots,
                        onCheckedChange = { onChange(values.copy(allowScreenshots = it)) },
                        description = stringResource(R.string.settings_allow_screenshots_help),
                    )
                    SettingsDivider()
                    SwitchSetting(
                        label = stringResource(R.string.settings_password_keypad),
                        checked = values.passwordKeypad,
                        onCheckedChange = { onChange(values.copy(passwordKeypad = it)) },
                        description = stringResource(R.string.settings_password_keypad_help),
                    )
                    SettingsDivider()
                    SettingsLabel(textRes = R.string.settings_clipboard_delay)
                    SettingsValues.CLIPBOARD_CHOICES.forEachIndexed { index, seconds ->
                        if (index > 0) SettingsDivider()
                        OptionRow(
                            label = clipboardLabel(seconds),
                            selected = values.clipboardSeconds == seconds,
                            onSelect = { onChange(values.copy(clipboardSeconds = seconds)) },
                        )
                    }
                    SettingsNote(textRes = R.string.settings_clipboard_note)
                }
                SettingsCard(titleRes = R.string.settings_section_biometric) {
                    SettingsLabel(textRes = R.string.settings_biometric_days)
                    SettingsValues.BIOMETRIC_DAYS_CHOICES.forEachIndexed { index, days ->
                        if (index > 0) SettingsDivider()
                        OptionRow(
                            label = pluralStringResource(R.plurals.settings_days, days, days),
                            selected = values.biometricPasswordDays == days,
                            onSelect = { onChange(values.copy(biometricPasswordDays = days)) },
                        )
                    }
                    SettingsNote(textRes = R.string.settings_biometric_note)
                }
                SettingsCard(titleRes = R.string.settings_section_sync) {
                    SettingsLabel(textRes = R.string.settings_sync_warn)
                    SettingsValues.SYNC_WARN_CHOICES.forEachIndexed { index, days ->
                        if (index > 0) SettingsDivider()
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
                    SettingsNote(textRes = R.string.settings_sync_warn_note)
                }
                SettingsCard(titleRes = R.string.settings_section_appearance) {
                    SettingsLabel(textRes = R.string.settings_theme)
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) SettingsDivider()
                        OptionRow(
                            label = themeLabel(mode),
                            selected = values.themeMode == mode,
                            onSelect = { onChange(values.copy(themeMode = mode)) },
                        )
                    }
                }
                CloudSettingsEntry(cloudSource, syncLocal)
                UpdatesSettingsEntry()
                SettingsCard(titleRes = R.string.settings_section_about) {
                    Text(
                        text = stringResource(R.string.settings_version, installedVersion()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(CardPaddingHorizontal),
                    )
                }
            }
        }
    }
}

/** The coloured band with the title and the way back. */
@Composable
private fun SettingsHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    val container = if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
    val content = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(bottomStart = BandCorner, bottomEnd = BandCorner),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = BandPaddingStart,
                    end = BandPaddingEnd,
                    top = BandPaddingTop,
                    bottom = BandPaddingBottom,
                ),
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
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A section of the settings: a card with a title in capitals and the rows inside it. */
@Composable
internal fun SettingsCard(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CardBorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column {
            Text(
                text = stringResource(titleRes).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        start = CardPaddingHorizontal,
                        end = CardPaddingHorizontal,
                        top = CardTitlePaddingTop,
                        bottom = CardTitlePaddingBottom,
                    ),
            )
            content()
        }
    }
}

/** The question a group of options answers. */
@Composable
private fun SettingsLabel(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = CardPaddingHorizontal,
                    end = CardPaddingHorizontal,
                    top = LabelPaddingTop,
                    bottom = LabelPaddingBottom,
                ),
    )
}

/** The version name of the installed app, read from the package manager; empty when it cannot be read. */
@Composable
private fun installedVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        try {
            val manager = context.packageManager
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    manager.getPackageInfo(context.packageName, 0)
                }
            info.versionName.orEmpty()
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }
}

/** A quiet explanation under a group of options. */
@Composable
private fun SettingsNote(
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = CardPaddingHorizontal,
                    end = CardPaddingHorizontal,
                    top = ItemGap,
                    bottom = NotePaddingBottom,
                ),
    )
}

/** The thin line between two rows of a card. */
@Composable
private fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA),
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
                .padding(horizontal = CardPaddingHorizontal, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One setting that is on or off; its label is what a screen reader reads. */
@Composable
internal fun SwitchSetting(
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
                .padding(horizontal = CardPaddingHorizontal, vertical = RowPaddingVertical),
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

/** The word each theme choice is shown with. */
@Composable
private fun themeLabel(mode: ThemeMode): String =
    stringResource(
        when (mode) {
            ThemeMode.System -> R.string.settings_theme_system
            ThemeMode.Light -> R.string.settings_theme_light
            ThemeMode.Dark -> R.string.settings_theme_dark
        },
    )

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
