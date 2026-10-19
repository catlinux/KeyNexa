@file:Suppress("TooManyFunctions")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.DesktopSettings
import io.github.catlinux.keynexa.desktop.Theme
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.group_settings
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_allow_screenshots
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_allow_screenshots_help
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_auto_lock
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_back
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_clipboard_delay
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_clipboard_note
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_lock_on_suspend
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_lock_on_windows_lock
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_minutes
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud_configure
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud_configured
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud_sync
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_cloud_unconfigured
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_local
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_local_note
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_sync
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_mode_sync_note
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_never
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_no_lock_warning
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_password_keypad
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_password_keypad_help
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_seconds
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_section_appearance
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_section_clipboard
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_section_modes
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_section_security
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_theme
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_theme_dark
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_theme_light
import io.github.catlinux.keynexa.desktop.app.generated.resources.settings_theme_system
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.Instant

private val ScreenPadding = 20.dp
private val BlockGap = 16.dp
private val ItemGap = 8.dp
private val TouchTarget = 44.dp
private val CardShape = RoundedCornerShape(16.dp)
private val ContentWidth = 560.dp

/** What the label of a choice of minutes or seconds says. */
internal sealed interface ChoiceLabel {
    /** The value that means the feature never acts by itself. */
    data object Never : ChoiceLabel

    /** Any other value, with the amount it stands for. */
    data class Amount(
        val value: Int,
    ) : ChoiceLabel
}

/** The label of [value]: never when it is 0 or less, and its amount otherwise. */
internal fun choiceLabel(value: Int): ChoiceLabel = if (value <= 0) ChoiceLabel.Never else ChoiceLabel.Amount(value)

/**
 * What the user chose for the look, the locking, the clipboard and the modes. Every change is handed to [onUpdate],
 * which stores it and publishes it, so the rest of the app follows at once.
 */
@Suppress("LongParameterList")
@Composable
internal fun DesktopSettingsScreen(
    settings: DesktopSettings,
    onUpdate: ((DesktopSettings) -> DesktopSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    cloudConfigured: Boolean = false,
    onOpenCloud: (() -> Unit)? = null,
    cloudSyncState: CloudSyncCardState = CloudSyncCardState(),
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = BlockGap),
        verticalArrangement = Arrangement.spacedBy(BlockGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = ContentWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = stringResource(Res.string.group_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text(text = stringResource(Res.string.settings_back))
            }
        }
        AppearanceSection(settings = settings, onUpdate = onUpdate)
        SecuritySection(settings = settings, onUpdate = onUpdate)
        ClipboardSection(settings = settings, onUpdate = onUpdate)
        ModesSection(cloudConfigured = cloudConfigured, cloudSyncState = cloudSyncState, onOpenCloud = onOpenCloud)
    }
}

/** The theme. */
@Composable
private fun AppearanceSection(
    settings: DesktopSettings,
    onUpdate: ((DesktopSettings) -> DesktopSettings) -> Unit,
) {
    SettingsCard(title = stringResource(Res.string.settings_section_appearance)) {
        SectionLabel(text = stringResource(Res.string.settings_theme))
        Theme.entries.forEach { theme ->
            ChoiceRow(
                label = stringResource(themeLabel(theme)),
                selected = settings.theme == theme,
                onSelect = { onUpdate { it.copy(theme = theme) } },
            )
        }
    }
}

/** The locking and the privacy of the screen and the passwords. */
@Composable
private fun SecuritySection(
    settings: DesktopSettings,
    onUpdate: ((DesktopSettings) -> DesktopSettings) -> Unit,
) {
    SettingsCard(title = stringResource(Res.string.settings_section_security)) {
        SectionLabel(text = stringResource(Res.string.settings_auto_lock))
        DesktopSettings.LOCK_AFTER_IDLE_CHOICES.forEach { minutes ->
            ChoiceRow(
                label = amountLabel(choiceLabel(minutes), inMinutes = true),
                selected = settings.lockAfterIdleMinutes == minutes,
                onSelect = { onUpdate { it.copy(lockAfterIdleMinutes = minutes) } },
            )
        }
        if (settings.lockAfterIdleMinutes <= 0) {
            HelpText(text = stringResource(Res.string.settings_no_lock_warning))
        }
        SwitchRow(
            label = stringResource(Res.string.settings_lock_on_windows_lock),
            checked = settings.lockOnWindowsLock,
            onCheckedChange = { value -> onUpdate { it.copy(lockOnWindowsLock = value) } },
        )
        SwitchRow(
            label = stringResource(Res.string.settings_lock_on_suspend),
            checked = settings.lockOnSuspend,
            onCheckedChange = { value -> onUpdate { it.copy(lockOnSuspend = value) } },
        )
        SwitchRow(
            label = stringResource(Res.string.settings_allow_screenshots),
            help = stringResource(Res.string.settings_allow_screenshots_help),
            checked = settings.allowScreenshots,
            onCheckedChange = { value -> onUpdate { it.copy(allowScreenshots = value) } },
        )
        SwitchRow(
            label = stringResource(Res.string.settings_password_keypad),
            help = stringResource(Res.string.settings_password_keypad_help),
            checked = settings.useKeypad,
            onCheckedChange = { value -> onUpdate { it.copy(useKeypad = value) } },
        )
    }
}

/** How long a copied secret stays on the clipboard. */
@Composable
private fun ClipboardSection(
    settings: DesktopSettings,
    onUpdate: ((DesktopSettings) -> DesktopSettings) -> Unit,
) {
    SettingsCard(title = stringResource(Res.string.settings_section_clipboard)) {
        SectionLabel(text = stringResource(Res.string.settings_clipboard_delay))
        DesktopSettings.CLIPBOARD_CLEAR_CHOICES.forEach { seconds ->
            ChoiceRow(
                label = amountLabel(choiceLabel(seconds), inMinutes = false),
                selected = settings.clipboardClearSeconds == seconds,
                onSelect = { onUpdate { it.copy(clipboardClearSeconds = seconds) } },
            )
        }
        HelpText(text = stringResource(Res.string.settings_clipboard_note))
    }
}

/** The three modes, of which only the local one works for now. */
@Composable
private fun ModesSection(
    cloudConfigured: Boolean,
    cloudSyncState: CloudSyncCardState,
    onOpenCloud: (() -> Unit)?,
) {
    SettingsCard(title = stringResource(Res.string.settings_section_modes)) {
        ModeCard(
            title = stringResource(Res.string.settings_mode_local),
            note = stringResource(Res.string.settings_mode_local_note),
            action = null,
            enabled = false,
        )
        ModeCard(
            title = stringResource(Res.string.settings_mode_cloud),
            note =
                stringResource(
                    if (cloudConfigured) {
                        Res.string.settings_mode_cloud_configured
                    } else {
                        Res.string.settings_mode_cloud_unconfigured
                    },
                ),
            action = stringResource(Res.string.settings_mode_cloud_configure),
            enabled = onOpenCloud != null,
            onAction = onOpenCloud,
        )
        ModeCard(
            title = stringResource(Res.string.settings_mode_cloud_sync),
            note =
                syncNoteText(
                    syncCardNote(
                        cloudSyncState.enabled,
                        cloudSyncState.hasVault,
                        cloudSyncState.lastSuccess,
                        cloudSyncState.lastError,
                        Instant.now(),
                    ),
                ),
            action = stringResource(Res.string.settings_mode_cloud_configure),
            enabled = onOpenCloud != null,
            onAction = onOpenCloud,
        )
        ModeCard(
            title = stringResource(Res.string.settings_mode_sync),
            note = stringResource(Res.string.settings_mode_sync_note),
            action = null,
            enabled = false,
        )
    }
}

/** A card that holds one section of the settings. */
@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = ContentWidth),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(BlockGap),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
            content()
        }
    }
}

/** The name of a group of choices inside a card. */
@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A line of help under a setting. */
@Composable
private fun HelpText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** One of several choices, picked with a radio button. */
@Composable
private fun ChoiceRow(
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
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A setting that is on or off. */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    help: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
        if (help != null) HelpText(text = help)
    }
}

/** One of the three modes: its name, what it does and, when it has one, its button. */
@Composable
@Suppress("LongParameterList")
private fun ModeCard(
    title: String,
    note: String,
    action: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(BlockGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItemGap)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (action != null) {
                Button(
                    onClick = { onAction?.invoke() },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = TouchTarget),
                ) {
                    Text(text = action)
                }
            }
        }
    }
}

/** The label of an amount of minutes or, when [inMinutes] is false, of seconds. */
@Composable
private fun amountLabel(
    label: ChoiceLabel,
    inMinutes: Boolean,
): String =
    when (label) {
        ChoiceLabel.Never -> stringResource(Res.string.settings_never)
        is ChoiceLabel.Amount -> {
            val unit = if (inMinutes) Res.plurals.settings_minutes else Res.plurals.settings_seconds
            pluralStringResource(unit, label.value, label.value)
        }
    }

/** The label of one theme. */
@Composable
private fun themeLabel(theme: Theme): org.jetbrains.compose.resources.StringResource =
    when (theme) {
        Theme.System -> Res.string.settings_theme_system
        Theme.Light -> Res.string.settings_theme_light
        Theme.Dark -> Res.string.settings_theme_dark
    }

private const val DIVIDER_ALPHA = 0.3f
