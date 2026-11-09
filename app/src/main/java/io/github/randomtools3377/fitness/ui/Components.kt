package io.github.randomtools3377.fitness.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.EnergyUnit
import io.github.randomtools3377.fitness.core.Nutrients
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Units

/** A text field for a number, with the number keyboard. */
@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimals: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || (decimals && (it == '.' || it == ',')) }) },
        label = { Text(label) },
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.testTag(tag),
    )
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, label: String, tag: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}

/** A row of choices, one of which is picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, tag: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            FilterChip(
                selected = o == selected,
                onClick = { onPick(o) },
                label = { Text(label(o)) },
                modifier = Modifier.testTag(tag(o)),
            )
        }
    }
}

@Composable
fun Section(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmTag: String = "confirm",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag(confirmTag)) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "P 12 g · C 40 g · F 9 g" */
fun macroLine(n: Nutrients): String =
    "Protein ${Units.grams(n.protein)} · Carbs ${Units.grams(n.carbs)} · Fat ${Units.grams(n.fat)}"

/** "Fibre 3 g · Sugar 1 g · Saturates 0.5 g · Salt 0.2 g", with only what is known; "" if nothing is. */
fun extrasLine(n: Nutrients): String = listOfNotNull(
    n.fibre?.let { "Fibre ${io.github.randomtools3377.fitness.core.formatAmount(Math.round(it * 10) / 10.0)} g" },
    n.sugar?.let { "Sugar ${io.github.randomtools3377.fitness.core.formatAmount(Math.round(it * 10) / 10.0)} g" },
    n.satFat?.let { "Saturates ${io.github.randomtools3377.fitness.core.formatAmount(Math.round(it * 10) / 10.0)} g" },
    n.salt?.let { "Salt ${io.github.randomtools3377.fitness.core.formatAmount(Math.round(it * 100) / 100.0)} g" },
).joinToString(" · ")

fun energy(kcal: Double, u: UnitSettings): String = Units.energy(kcal, u.energy)

fun energyUnit(u: UnitSettings): String = u.energy.short

/** A number the user typed in the energy unit in use, as kcal. */
fun typedKcal(text: String, unit: EnergyUnit): Double? = Units.parse(text)?.let { Units.shownToKcal(it, unit) }

@Composable
fun LabelValue(label: String, value: String, tag: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}
