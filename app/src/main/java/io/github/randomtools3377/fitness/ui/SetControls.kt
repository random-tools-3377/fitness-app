package io.github.randomtools3377.fitness.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.Track
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.WeightUnit
import io.github.randomtools3377.fitness.core.WorkoutSet
import io.github.randomtools3377.fitness.core.Workouts
import io.github.randomtools3377.fitness.core.formatAmount
import kotlin.math.roundToInt

/** A number with − and + beside it; tapping the number lets it be typed (0.21.0). */
@Composable
fun Stepper(text: String, label: String, tag: String, onMinus: (() -> Unit)?, onPlus: (() -> Unit)?, onTap: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = { onMinus?.invoke() }, enabled = onMinus != null, modifier = Modifier.testTag("minus:$tag")) { Text("−") }
        Column(
            Modifier.widthIn(min = 44.dp).clickable(enabled = onTap != null) { onTap?.invoke() }.padding(horizontal = 4.dp).testTag("all:$tag"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.testTag("value:$tag"))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
        OutlinedIconButton(onClick = { onPlus?.invoke() }, enabled = onPlus != null, modifier = Modifier.testTag("plus:$tag")) { Text("+") }
    }
}

/** How much − and + change each thing, in the units shown. */
private fun step(t: Track, u: UnitSettings): Double = when (t) {
    Track.WEIGHT -> if (u.weight == WeightUnit.KG) 2.5 else 5.0
    Track.REPS -> 1.0
    Track.SECONDS -> 5.0
    Track.MINUTES -> 1.0
    Track.DISTANCE -> 0.5
}

/** A set's value for [t], in the units shown, or null. */
private fun shown(t: Track, s: WorkoutSet, u: UnitSettings): Double? = when (t) {
    Track.WEIGHT -> s.weightKg?.let { Units.kgToShown(it, u.weight) }
    Track.REPS -> s.reps?.toDouble()
    Track.SECONDS -> s.seconds?.toDouble()
    Track.MINUTES -> s.seconds?.let { it / 60.0 }
    Track.DISTANCE -> s.distanceKm?.let { Units.kmToShown(it, u.distance) }
}

/** [s] with its value for [t] set to [v] (in the units shown), or cleared. */
private fun withValue(t: Track, s: WorkoutSet, v: Double?, u: UnitSettings): WorkoutSet = when (t) {
    Track.WEIGHT -> s.copy(weightKg = v?.let { Units.shownToKg(it, u.weight) })
    Track.REPS -> s.copy(reps = v?.roundToInt())
    Track.SECONDS -> s.copy(seconds = v?.roundToInt())
    Track.MINUTES -> s.copy(seconds = v?.let { (it * 60).roundToInt() })
    Track.DISTANCE -> s.copy(distanceKm = v?.let { Units.shownToKm(it, u.distance) })
}

private fun nice(x: Double): String = formatAmount(Math.round(x * 100) / 100.0)

/**
 * The sets of an exercise as one (0.21.0): how many, and each thing logged, with − and +. A
 * change applies to every set not yet ticked; − and + keep any differences between sets, and a
 * typed number makes them all the same. Reps can be "till failure".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllSetsBar(
    tracks: List<Track>,
    sets: List<WorkoutSet>,
    units: UnitSettings,
    tag: String,
    onCount: (Int) -> Unit,
    onOpen: ((WorkoutSet) -> WorkoutSet) -> Unit,
) {
    var typing by remember { mutableStateOf<Track?>(null) }
    val open = sets.filter { !it.done }.ifEmpty { sets }
    val working = open.filter { !it.warmUp }.ifEmpty { open }
    val failure = working.all { it.toFailure }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Stepper(
            "${sets.size}", if (sets.size == 1) "set" else "sets", "sets:$tag",
            onMinus = if (sets.size > 1 && !sets.last().done) ({ onCount(sets.size - 1) }) else null,
            onPlus = { onCount(sets.size + 1) },
            onTap = null,
        )
        for (t in tracks) {
            val label = trackColumn(t, units)
            if (t == Track.REPS && failure) {
                Stepper("max", "reps", "${t.name.lowercase()}:$tag", null, null, null)
            } else {
                val values = working.map { shown(t, it, units) }
                val known = values.filterNotNull()
                val text = when {
                    known.isEmpty() -> "—"
                    known.size == values.size && known.distinct().size == 1 -> nice(known.first())
                    else -> nice(known.min()) + "–" + nice(known.max())
                }
                val st = step(t, units)
                Stepper(
                    text, label, "${t.name.lowercase()}:$tag",
                    onMinus = if (known.any { it > 0 }) ({ onOpen { s -> if (s.toFailure && t == Track.REPS) s else withValue(t, s, ((shown(t, s, units) ?: 0.0) - st).coerceAtLeast(0.0), units) } }) else null,
                    onPlus = { onOpen { s -> if (s.toFailure && t == Track.REPS) s else withValue(t, s, (shown(t, s, units) ?: 0.0) + st, units) } },
                    onTap = { typing = t },
                )
            }
            if (t == Track.REPS) {
                FilterChip(
                    selected = failure,
                    onClick = { onOpen { s -> if (s.warmUp) s else s.copy(toFailure = !failure, reps = if (!failure) null else s.reps) } },
                    label = { Text("Till failure") },
                    modifier = Modifier.testTag("failure-all:$tag"),
                )
            }
        }
    }
    typing?.let { t ->
        ValueDialog("${trackColumn(t, units).replaceFirstChar { it.uppercase() }} for every set", decimals = t != Track.REPS && t != Track.SECONDS, onSave = { v ->
            onOpen { s -> withValue(t, s, v, units).let { if (t == Track.REPS) it.copy(toFailure = false) else it } }
            typing = null
        }, onDismiss = { typing = null })
    }
}

/** Typing one number (0.21.0); empty clears it. */
@Composable
fun ValueDialog(title: String, decimals: Boolean, onSave: (Double?) -> Unit, onDismiss: () -> Unit, note: String? = null, initial: String = "") {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(text, { text = it }, "Number", "field:value", Modifier.fillMaxWidth(), decimals = decimals)
                if (note != null) Text(note, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Units.parse(text)) }, modifier = Modifier.testTag("save-value")) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The sets of an exercise as small buttons to tick, when they are all the same (0.21.0). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetChips(sets: List<WorkoutSet>, tag: String, onTick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sets.forEachIndexed { j, s ->
            FilterChip(
                selected = s.done,
                onClick = { onTick(j) },
                label = { Text((if (s.done) "✓ " else "") + (if (s.warmUp) "W" else "${j + 1}") + (if (s.done && s.toFailure && s.reps != null) " · ${s.reps}" else "")) },
                modifier = Modifier.testTag("tick:$tag:$j"),
            )
        }
    }
}

/** What a set till failure came to, asked when it's ticked (0.21.0). */
@Composable
fun FailureRepsDialog(names: List<String>, onDone: (List<Int?>) -> Unit, onDismiss: () -> Unit) {
    val texts = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { repeat(names.size) { add("") } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How many reps?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { k, n ->
                    NumberField(texts[k], { texts[k] = it }, n, "field:failure-reps:$k", Modifier.fillMaxWidth(), suffix = "reps", decimals = false)
                }
                Text("Till failure: type what you managed. You can leave it empty.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(texts.map { it.toIntOrNull() }) }, modifier = Modifier.testTag("save-failure-reps")) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Each set" or "Hide each set", to show the sets one by one. */
@Composable
fun EachSetToggle(open: Boolean, tag: String, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.testTag("each-set:$tag")) { Text(if (open) "Hide each set ▴" else "Each set ▾") }
}

/** Whether to show the sets one by one to start with: when they differ, or there's only one. */
fun eachSetAtFirst(sets: List<WorkoutSet>): Boolean = sets.size <= 1 || !Workouts.uniform(sets)
