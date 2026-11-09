package io.github.randomtools3377.fitness.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.fitness.core.DiaryEntry
import io.github.randomtools3377.fitness.core.Food
import io.github.randomtools3377.fitness.core.Meal
import io.github.randomtools3377.fitness.core.Nutrients
import io.github.randomtools3377.fitness.core.UnitSettings
import io.github.randomtools3377.fitness.core.Units
import io.github.randomtools3377.fitness.core.Water
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** "Today", "Yesterday", "Tomorrow" or "Mon 9 Nov 2026". */
fun dayName(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minus(DatePeriod(days = 1)) -> "Yesterday"
    today.plus(DatePeriod(days = 1)) -> "Tomorrow"
    else -> {
        val wd = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val m = d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "$wd ${d.dayOfMonth} $m ${d.year}"
    }
}

@Composable
fun DiaryScreen(vm: MainViewModel, modifier: Modifier, open: (Route) -> Unit) {
    val day by vm.day.collectAsState()
    val entries by vm.entries.collectAsState()
    val totals by vm.totals.collectAsState()
    val water by vm.water.collectAsState()
    val settings by vm.settings.collectAsState()
    val units by vm.units.collectAsState()
    val burned by vm.dayBurned.collectAsState()
    val dayWorkouts by vm.dayWorkouts.collectAsState()
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var savingMeal by remember { mutableStateOf<Meal?>(null) }
    var dayMenu by remember { mutableStateOf(false) }
    val today = vm.today()

    LazyColumn(modifier.fillMaxSize().testTag("diary-list")) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.day.value = day.minus(DatePeriod(days = 1)) }, modifier = Modifier.testTag("day-prev")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Day before")
                }
                Text(
                    dayName(day, today),
                    Modifier.weight(1f).clickable { vm.day.value = today }.testTag("day-name"),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { vm.day.value = day.plus(DatePeriod(days = 1)) }, modifier = Modifier.testTag("day-next")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Day after")
                }
                IconButton(onClick = { open(Route.Settings) }, modifier = Modifier.testTag("open-settings")) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                Box {
                    IconButton(onClick = { dayMenu = true }, modifier = Modifier.testTag("day-menu")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy a whole day from another day") },
                            onClick = { dayMenu = false; open(Route.Copy(null)) },
                            modifier = Modifier.testTag("copy-day-from"),
                        )
                        if (day != today) {
                            DropdownMenuItem(text = { Text("Back to today") }, onClick = { dayMenu = false; vm.day.value = today })
                        }
                    }
                }
            }
        }

        item { SummaryCard(totals.total, burned, settings.burnedHalf, settings.targetKcal, settings.macros.grams(settings.targetKcal ?: 0), units, open) }

        if (dayWorkouts.isNotEmpty()) {
            item {
                Section("Workouts") {
                    for (w in dayWorkouts) {
                        Row(Modifier.fillMaxWidth().testTag("day-workout:${w.name}"), verticalAlignment = Alignment.CenterVertically) {
                            Text(w.name, Modifier.weight(1f))
                            Text(burnedText(w.burnedKcal * (if (settings.burnedHalf) 0.5 else 1.0), units), color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        item {
            val ml = Water.total(water, day)
            val w = settings.water
            Section("Water") {
                LabelValue(
                    "Drunk",
                    "${Units.volume(ml.toDouble(), units.volume)} of ${Units.volume(w.targetMl.toDouble(), units.volume)}",
                    tag = "water-total",
                )
                LinearProgressIndicator(progress = { (ml.toFloat() / w.targetMl.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.addWater(w.glassMl) }, modifier = Modifier.testTag("add-glass")) {
                        Text("+ Glass ${Units.volume(w.glassMl.toDouble(), units.volume)}")
                    }
                    OutlinedButton(onClick = { vm.addWater(w.bottleMl) }, modifier = Modifier.testTag("add-bottle")) {
                        Text("+ Bottle ${Units.volume(w.bottleMl.toDouble(), units.volume)}")
                    }
                }
                if (water.isNotEmpty()) {
                    TextButton(onClick = { vm.undoWater() }, modifier = Modifier.testTag("undo-water")) { Text("Take back the last one") }
                }
            }
        }

        for (meal in Meal.entries) {
            item(key = meal.name) {
                MealCard(
                    meal = meal,
                    entries = entries.filter { it.meal == meal },
                    total = totals.byMeal.getValue(meal),
                    units = units,
                    add = { open(Route.AddFood(meal)) },
                    copy = { open(Route.Copy(meal)) },
                    save = { savingMeal = meal },
                    edit = { editing = it },
                    eat = { vm.eatPlanned(it) },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { e -> EditEntryDialog(vm, e, units, onDone = { editing = null }) }

    savingMeal?.let { meal ->
        var name by remember(meal) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savingMeal = null },
            title = { Text("Save ${meal.title.lowercase()} as a meal") },
            text = {
                Column {
                    Text("Its foods and amounts are saved together, to add again in one go.")
                    TextInput(name, { name = it }, "Name, like \"Usual breakfast\"", "field:meal-name")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.saveMeal(name, meal); savingMeal = null },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("confirm-save-meal"),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { savingMeal = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryCard(eaten: Nutrients, burned: Double, half: Boolean, targetKcal: Int?, macroTargets: Nutrients, units: UnitSettings, open: (Route) -> Unit) {
    Section("Calories") {
        LabelValue("Eaten", energy(eaten.kcal, units), tag = "day-kcal")
        if (burned > 0) LabelValue(if (half) "Burned in workouts (half)" else "Burned in workouts", energy(burned, units), tag = "day-burned")
        if (targetKcal == null) {
            Text("Set a daily target to see what is left.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { open(Route.Target) }, modifier = Modifier.testTag("set-target")) { Text("Set my target") }
        } else {
            val left = targetKcal + burned - eaten.kcal
            LabelValue("Target", energy(targetKcal.toDouble(), units), tag = "day-target")
            LabelValue(
                if (left >= 0) "Left" else "Over",
                energy(kotlin.math.abs(left), units),
                tag = "day-left",
            )
            LinearProgressIndicator(progress = { (eaten.kcal / (targetKcal + burned)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        HorizontalDivider()
        MacroRow("Protein", eaten.protein, if (targetKcal != null) macroTargets.protein else null)
        MacroRow("Carbs", eaten.carbs, if (targetKcal != null) macroTargets.carbs else null)
        MacroRow("Fat", eaten.fat, if (targetKcal != null) macroTargets.fat else null)
    }
}

@Composable
private fun MacroRow(name: String, eaten: Double, target: Double?) {
    Column {
        LabelValue(name, if (target == null) Units.grams(eaten) else "${Units.grams(eaten)} of ${Units.grams(target)}", tag = "day-${name.lowercase()}")
        if (target != null && target > 0) {
            LinearProgressIndicator(progress = { (eaten / target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<DiaryEntry>,
    total: Nutrients,
    units: UnitSettings,
    add: () -> Unit,
    copy: () -> Unit,
    save: () -> Unit,
    edit: (DiaryEntry) -> Unit,
    eat: (DiaryEntry) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    energy(total.kcal, units),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("meal-kcal:${meal.name}"),
                )
            }
            IconButton(onClick = add, modifier = Modifier.testTag("add:${meal.name}")) {
                Icon(Icons.Filled.Add, contentDescription = "Add to ${meal.title}")
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("meal-menu:${meal.name}")) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy from another day") },
                        onClick = { menu = false; copy() },
                        modifier = Modifier.testTag("copy-meal-from:${meal.name}"),
                    )
                    if (entries.any { !it.planned }) {
                        DropdownMenuItem(
                            text = { Text("Save as a meal") },
                            onClick = { menu = false; save() },
                            modifier = Modifier.testTag("save-meal:${meal.name}"),
                        )
                    }
                }
            }
        }
        for (e in entries.filter { it.planned }) {
            // Planned ahead: doesn't count until ticked as eaten.
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("planned:${e.name}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Planned · ${e.describeAmount()} · ${energy(e.nutrients.kcal, units)}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { eat(e) }, modifier = Modifier.testTag("eat:${e.name}")) { Text("Eaten") }
            }
        }
        for (e in entries.filter { !it.planned }) {
            Row(
                Modifier.fillMaxWidth().clickable { edit(e) }.padding(vertical = 6.dp).testTag("entry:${e.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        e.describeAmount() + if (e.isQuickAdd) "" else " · " + macroLine(e.nutrients),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(energy(e.nutrients.kcal, units), fontWeight = FontWeight.Medium)
            }
        }
        if (entries.any { !it.planned }) {
            Text(macroLine(total), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Changing or removing an entry: its amount, or for a quick add, its calories. */
@Composable
private fun EditEntryDialog(vm: MainViewModel, entry: DiaryEntry, units: UnitSettings, onDone: () -> Unit) {
    var food by remember(entry.id) { mutableStateOf<Food?>(null) }
    var loaded by remember(entry.id) { mutableStateOf(false) }
    LaunchedEffect(entry.id) {
        food = vm.foodOf(entry)
        loaded = true
    }
    if (!loaded) return
    val f = food
    if (entry.isQuickAdd || f == null) {
        QuickAddDialog(
            title = if (entry.isQuickAdd) "Quick add" else entry.name,
            units = units,
            initialKcal = entry.nutrients.kcal,
            initialName = entry.name,
            onConfirm = { kcal, name -> vm.changeQuickAdd(entry, kcal, name); onDone() },
            onDelete = { vm.deleteEntry(entry); onDone() },
            onMove = { m -> vm.moveEntry(entry, m); onDone() },
            current = entry.meal,
            onDismiss = onDone,
        )
    } else {
        AmountDialog(
            food = f,
            units = units,
            initial = entry.amount,
            confirmText = "Save",
            onConfirm = { a -> vm.changeEntry(entry, f, a); onDone() },
            onDelete = { vm.deleteEntry(entry); onDone() },
            onMove = { m -> vm.moveEntry(entry, m); onDone() },
            current = entry.meal,
            onDismiss = onDone,
        )
    }
}
