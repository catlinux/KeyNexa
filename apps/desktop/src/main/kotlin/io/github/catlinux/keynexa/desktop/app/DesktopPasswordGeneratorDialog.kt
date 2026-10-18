package io.github.catlinux.keynexa.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.desktop.app.generated.resources.Res
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate_cancel
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate_digits
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate_exclude_ambiguous
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate_length
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate_lower
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate_symbols
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate_title
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate_upper
import io.github.catlinux.keynexa.desktop.app.generated.resources.edit_generate_use
import io.github.catlinux.keynexa.vault.Options
import io.github.catlinux.keynexa.vault.PasswordGenerator
import org.jetbrains.compose.resources.stringResource

/** The longest password the dialog offers, as on Android. */
private const val MAX_GENERATED_LENGTH = 64

/** The number of stops between the shortest and the longest length. */
private const val LENGTH_STEPS = MAX_GENERATED_LENGTH - Options.MIN_LENGTH - 1

private val DialogGap = 8.dp

/** What the generator dialog is set to; it turns its switches into the options the generator understands. */
private class GeneratorChoices {
    var length by mutableStateOf(Options.DEFAULT_LENGTH)
    var lower by mutableStateOf(true)
    var upper by mutableStateOf(true)
    var digits by mutableStateOf(true)
    var symbols by mutableStateOf(true)
    var excludeAmbiguous by mutableStateOf(false)

    /** The generator needs at least one character class. */
    val anyClass: Boolean get() = lower || upper || digits || symbols

    fun toOptions(): Options =
        Options(
            length = length,
            lower = lower,
            upper = upper,
            digits = digits,
            symbols = symbols,
            excludeAmbiguous = excludeAmbiguous,
        )
}

/**
 * The generator's options, and the button that hands a password to [onUse]. The array belongs to the caller, which
 * wipes it as soon as it has been copied into the field.
 */
@Composable
internal fun GeneratePasswordDialog(
    onUse: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = remember { GeneratorChoices() }
    val generator = remember { PasswordGenerator() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(Res.string.edit_generate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DialogGap)) {
                Text(
                    text = stringResource(Res.string.edit_generate_length, choices.length),
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = choices.length.toFloat(),
                    onValueChange = { choices.length = it.toInt().coerceIn(Options.MIN_LENGTH, MAX_GENERATED_LENGTH) },
                    valueRange = Options.MIN_LENGTH.toFloat()..MAX_GENERATED_LENGTH.toFloat(),
                    steps = LENGTH_STEPS,
                )
                GeneratorOptionSwitches(choices)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUse(generator.generate(choices.toOptions())) },
                enabled = choices.anyClass,
            ) {
                Text(text = stringResource(Res.string.edit_generate_use))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.edit_generate_cancel))
            }
        },
    )
}

/** The character classes the generator may use, and whether look-alike characters are left out. */
@Composable
private fun GeneratorOptionSwitches(
    choices: GeneratorChoices,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DialogGap)) {
        OptionSwitch(
            label = stringResource(Res.string.edit_generate_lower),
            checked = choices.lower,
            onCheckedChange = { choices.lower = it },
        )
        OptionSwitch(
            label = stringResource(Res.string.edit_generate_upper),
            checked = choices.upper,
            onCheckedChange = { choices.upper = it },
        )
        OptionSwitch(
            label = stringResource(Res.string.edit_generate_digits),
            checked = choices.digits,
            onCheckedChange = { choices.digits = it },
        )
        OptionSwitch(
            label = stringResource(Res.string.edit_generate_symbols),
            checked = choices.symbols,
            onCheckedChange = { choices.symbols = it },
        )
        OptionSwitch(
            label = stringResource(Res.string.edit_generate_exclude_ambiguous),
            checked = choices.excludeAmbiguous,
            onCheckedChange = { choices.excludeAmbiguous = it },
        )
    }
}

/** One option of the generator: its name, and a switch that turns it on and off. */
@Composable
private fun OptionSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DialogGap),
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
}
